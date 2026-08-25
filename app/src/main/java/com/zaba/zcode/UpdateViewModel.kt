package com.zaba.zcode

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaba.zcode.core.diagnostics.Breadcrumb
import com.zaba.zcode.core.update.UpdateChecker
import com.zaba.zcode.core.update.UpdateDownloader
import com.zaba.zcode.core.update.UpdateDownloadService
import com.zaba.zcode.core.update.UpdateInstaller
import com.zaba.zcode.core.update.UpdateReceipt
import com.zaba.zcode.core.update.UpdateSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * UpdateViewModel — v1.0.22, owner Activity-scoped alur update (RFC D7 + §3).
 *
 * Pola `PackageOperationViewModel`: satu owner per Activity supaya
 * PipScreen-type "screen pergi, kerjaan harus hidup" tidak membuat engine
 * kedua. Beda dengan engine package: owner unduhan TIDAK di sini tapi di
 * [UpdateDownloadService] (D11 — keputusan user: unduhan selamat saat user
 * beralih app; Activity boleh mati, FGS tetap jalan). VM ini hanya:
 *
 * - state machine UI (IDLE → CHECKING → AVAILABLE → DOWNLOADING → VERIFYING
 *   → FLUSHING → READY → PENDING / FAILED-retryable);
 * - orkestrasi langkah yang menyentuh workspace: VERIFY (sha vs digest API)
 *   → FLUSH (`flushSaveSync(verifyAllDrafts=true)`, hook yang sama dengan
 *   MainActivity sebelum process rebirth; gagal = STOP, fail-closed) →
 *   RECEIPT (hanya setelah flush sukses) → INSTALL (dialog system Android);
 * - recovery bila VM dibuat ulang saat unduhan masih hidup (Activity
 *   recreated, process masih hidup): service state dibaca kembali, dan bila
 *   `offer` tak lagi diketahui, verifikasi dijalankan ulang setelah fresh
 *   check — tidak pernah mengasumsikan file di cache = benar.
 *
 * Jaminan loop (RFC §3): tiap state satu aksi utama + satu jalan keluar;
 * gagal selalu retryable; idempotent (cek ulang saat READY tidak mengunduh
 * ulang); tahan restart (receipt + breadcrumb); tutup dialog ≠ batalkan.
 */
class UpdateViewModel(
    app: Application,
    private val workspace: WorkspaceViewModel
) : AndroidViewModel(app) {

    enum class UiState {
        IDLE, CHECKING, UPTODATE, AVAILABLE, DOWNLOADING,
        VERIFYING, FLUSHING, READY, PENDING, FAILED
    }

    val uiState = mutableStateOf(UiState.IDLE)
    val localVersion = mutableStateOf("")
    val offer = mutableStateOf<UpdateChecker.CheckOutcome.Newer?>(null)
    val progress = mutableStateOf(0L to -1L)
    val failReason = mutableStateOf("")
    val showDialog = mutableStateOf(false)
    val needsInstallPermission = mutableStateOf(false)
    val pendingVersion = mutableStateOf("")

    private val ctx: Context = app.applicationContext
    private val opMutex = Mutex()
    private var lastStage = "check"

    init {
        localVersion.value = UpdateChecker.currentVersion(ctx)
        // Start-up: receipt sudah diproses ZcodeApp (UpdateSession). PENDING
        // = user batal di dialog system pada sesi sebelumnya — APK masih di
        // cache (best-effort; file hilang ditangani di installNow).
        val outcome = UpdateSession.startupOutcome
        if (outcome is UpdateReceipt.Outcome.Pending) {
            uiState.value = UiState.PENDING
            UpdateReceipt.read(ctx)?.to?.let { pendingVersion.value = it }
        } else if (outcome is UpdateReceipt.Outcome.ApkGone) {
            // RFC §3 kasus batas: cache best-effort, file hilang → FAILED
            // jujur dengan reason "Package removed from cache", retryable
            // (retry = cek ulang → AVAILABLE → unduh ulang).
            uiState.value = UiState.FAILED
            failReason.value = "Package removed from cache"
            lastStage = "check"
        }
        // StateFlow service EMIT state sekarang saat collect: Done/InProgress
        // dari sesi sebelumnya (Activity recreated) ditangani di handleDone/
        // collector — recovery di sini, bukan asumsi.
        viewModelScope.launch(Dispatchers.Main.immediate) {
            UpdateDownloadService.state.collect { s ->
                when (s) {
                    is UpdateDownloadService.DownloadState.InProgress -> {
                        progress.value = s.written to s.total
                        if (uiState.value == UiState.IDLE) uiState.value = UiState.DOWNLOADING
                    }
                    is UpdateDownloadService.DownloadState.Done -> handleDone(s)
                    is UpdateDownloadService.DownloadState.Failed -> {
                        if (s.reasonCode == UpdateDownloader.DownloadResult.Failed.CANCELLED) {
                            uiState.value = if (offer.value != null) UiState.AVAILABLE else UiState.IDLE
                        } else {
                            fail("download", humanReason(s.reasonCode, s.message))
                        }
                    }
                    is UpdateDownloadService.DownloadState.Cancelled -> Unit
                    is UpdateDownloadService.DownloadState.Idle -> Unit
                }
            }
        }
        // D2: auto-check sunyi saat app start — HANYA saat IDLE (jangan
        // menimpa PENDING/READY/unduhan berjalan). Cache 24 jam di checker
        // membuat jalur ini tanpa jaringan bila masih segar.
        if (uiState.value == UiState.IDLE) {
            checkNow(fresh = false)
        }
    }

    // ---------- cek ----------

    /** [fresh] false = auto-check (hormati cache 24 jam); true = tap user. */
    fun checkNow(fresh: Boolean = true) {
        viewModelScope.launch(Dispatchers.Main) {
            opMutex.withLock {
                if (uiState.value == UiState.DOWNLOADING || uiState.value == UiState.VERIFYING
                    || uiState.value == UiState.FLUSHING
                ) return@withLock // jangan ganggu operasi unduh/jalan
                uiState.value = UiState.CHECKING
                when (val r = withContext(Dispatchers.IO) {
                    UpdateChecker.checkLatest(ctx, fresh)
                }) {
                    is UpdateChecker.CheckOutcome.UpToDate -> {
                        offer.value = null
                        uiState.value = UiState.UPTODATE
                    }
                    is UpdateChecker.CheckOutcome.Newer -> {
                        offer.value = r
                        uiState.value = UiState.AVAILABLE
                    }
                    is UpdateChecker.CheckOutcome.Failed ->
                        fail("check", humanReason(r.reasonCode, r.message))
                }
            }
        }
    }

    // ---------- unduh ----------

    /** HANYA dari tap user "Download & Update" (D10 — tanpa auto-download). */
    fun startDownload() {
        val n = offer.value ?: return
        viewModelScope.launch(Dispatchers.Main) {
            opMutex.withLock {
                if (uiState.value == UiState.DOWNLOADING) return@withLock
                uiState.value = UiState.DOWNLOADING
                progress.value = 0L to n.sizeBytes
                val dest = UpdateReceipt.apkFile(ctx, n.version)
                // D4: app menjaga cache-nya sendiri — APK versi lain dibuang.
                UpdateDownloader.cleanupOldApks(UpdateReceipt.apksDir(ctx), n.version)
                Breadcrumb.log("UPDATE_DOWNLOAD_BEGIN", n.version)
                UpdateDownloadService.start(ctx, n.downloadUrl, dest, n.sizeBytes)
            }
        }
    }

    fun cancelDownload() {
        Breadcrumb.log("UPDATE_DOWNLOAD_CANCELLED", "ui")
        UpdateDownloadService.requestCancel()
    }

    private fun handleDone(s: UpdateDownloadService.DownloadState.Done) {
        val n = offer.value
        if (n == null) {
            // VM baru (Activity recreated) — expected sha tak diketahui.
            // Fresh check: bila latest masih versi yang sama dengan file
            // → verifikasi lanjut; selain itu file cache dibuang + state
            // mengikuti hasil check. Tidak pernah asumsi file = benar.
            uiState.value = UiState.CHECKING
            viewModelScope.launch(Dispatchers.IO) {
                val r = UpdateChecker.checkLatest(ctx, fresh = true)
                withContext(Dispatchers.Main) {
                    if (r is UpdateChecker.CheckOutcome.Newer &&
                        s.file.name == UpdateReceipt.apkFile(ctx, r.version).name
                    ) {
                        offer.value = r
                        verifyWithOffer(r, s.file, s.sha256)
                    } else {
                        s.file.delete()
                        UpdateDownloadService.reset()
                        when (r) {
                            is UpdateChecker.CheckOutcome.Newer -> {
                                offer.value = r
                                uiState.value = UiState.AVAILABLE
                            }
                            is UpdateChecker.CheckOutcome.UpToDate ->
                                uiState.value = UiState.UPTODATE
                            is UpdateChecker.CheckOutcome.Failed ->
                                fail("check", humanReason(r.reasonCode, r.message))
                        }
                    }
                }
            }
            return
        }
        verifyWithOffer(n, s.file, s.sha256)
    }

    /** VERIFYING + FLUSHING + RECEIPT (RFC D5.2/D5.3 — urutannya kontraktual). */
    private fun verifyWithOffer(
        n: UpdateChecker.CheckOutcome.Newer,
        apk: java.io.File,
        sha256: String
    ) {
        uiState.value = UiState.VERIFYING
        if (!sha256.equals(n.sha256, ignoreCase = true)) {
            apk.delete()
            Breadcrumb.log(
                "UPDATE_VERIFY_FAIL",
                "expected ${n.sha256.take(12)}… got ${sha256.take(12)}… — file dibuang, jangan install"
            )
            fail("download", "Verification failed")
            return
        }
        Breadcrumb.log("UPDATE_VERIFY_OK", "sha256=${sha256.take(12)}…")
        runPrepare(n, apk)
    }

    private fun runPrepare(n: UpdateChecker.CheckOutcome.Newer, apk: java.io.File) {
        // FLUSHING: semua draft workspace di-commit SEBELUM instalasi. Hook
        // yang sama dengan MainActivity.requestRuntimeRestart (fail-closed:
        // false = instalasi TIDAK diluncurkan — draft user tidak boleh
        // ditinggal dalam keadaan tak tersimpan).
        uiState.value = UiState.FLUSHING
        val flushOk = workspace.flushSaveSync(verifyAllDrafts = true)
        if (!flushOk) {
            Breadcrumb.log("UPDATE_FLUSH_FAIL", "pre-install flush gagal")
            fail("flush", "Workspace save failed")
            return
        }
        Breadcrumb.log("UPDATE_FLUSH_OK", "pre-install")
        val fromCode = versionCode()
        // toCode = fromCode + 1 adalah BAWAH aman: kebijakan rilis repo =
        // versionCode monotonik naik satu per rilis; instalasi versi apa pun
        // yang di atasnya tetap terdeteksi (cur >= toCode = INSTALLED).
        UpdateReceipt.write(
            ctx,
            from = localVersion.value,
            fromCode = fromCode,
            to = n.version,
            toCode = fromCode + 1,
            sha256 = n.sha256,
            apkFile = apk,
            flushOk = true
        )
        uiState.value = UiState.READY
    }

    // ---------- install ----------

    /** READY/PENDING → luncurkan dialog system Android (D5.5). */
    fun installNow() {
        val n = offer.value
        val apk = when {
            n != null -> UpdateReceipt.apkFile(ctx, n.version)
            else -> UpdateReceipt.read(ctx)?.apkPath?.let { java.io.File(it) }
        }
        if (apk == null || !apk.exists()) {
            // D5.6: cache best-effort — APK hilang saat akan install.
            Breadcrumb.log("UPDATE_RECEIPT_STALE", "apk hilang saat install")
            if (n != null) {
                uiState.value = UiState.AVAILABLE
            } else {
                uiState.value = UiState.IDLE
            }
            return
        }
        if (!UpdateInstaller.canInstall(ctx)) {
            needsInstallPermission.value = true
            Breadcrumb.log("UPDATE_INSTALL_LAUNCH", "izin REQUEST_INSTALL_PACKAGES belum ada")
            return
        }
        needsInstallPermission.value = false
        UpdateInstaller.launchInstall(ctx, apk)
        // Setelah ini Android yang pegang: user confirm (→ restart →
        // UPDATE_INSTALLED) atau batal (→ restart → UPDATE_PENDING).
        uiState.value = UiState.PENDING
    }

    fun openInstallSettings() {
        UpdateInstaller.openUnknownSourcesSettings(ctx)
    }

    /** Dipanggil Activity saat resumed — user kembali dari halaman pengaturan. */
    fun onResumed() {
        if (needsInstallPermission.value &&
            (uiState.value == UiState.READY || uiState.value == UiState.PENDING)
        ) {
            needsInstallPermission.value = !UpdateInstaller.canInstall(ctx)
        }
    }

    // ---------- dialog & drawer ----------

    /** Tap baris "Cek Update": drawer ditutup, dialog situasional dibuka. */
    fun openDialog() {
        showDialog.value = true
        // IDLE → cek sekarang (D2: tap user = fresh check, tanpa popup).
        if (uiState.value == UiState.IDLE) checkNow(fresh = true)
    }

    fun closeDialog() {
        // Tutup dialog ≠ batalkan: unduhan FGS tetap jalan (D11) — state
        // tidak disentuh; progress tetap terlihat di suffix drawer +
        // notifikasi.
        showDialog.value = false
    }

    /** Retryable dari FAILED (RFC §3 jaminan 2) — sesuai stage terakhir. */
    fun retry() {
        when (lastStage) {
            "check" -> checkNow(fresh = true)
            "download", "verify" -> startDownload()
            "flush" -> {
                val n = offer.value
                val apk = n?.let { UpdateReceipt.apkFile(ctx, it.version) }
                if (n != null && apk != null && apk.exists()) runPrepare(n, apk)
                else startDownload()
            }
            else -> installNow()
        }
    }

    private fun fail(stage: String, reason: String) {
        lastStage = stage
        failReason.value = reason
        uiState.value = UiState.FAILED
    }

    fun suffixText(): String = when (uiState.value) {
        UiState.IDLE -> localVersion.value
        UiState.CHECKING -> "checking…"
        UiState.UPTODATE -> "up to date"
        UiState.AVAILABLE -> offer.value?.version ?: ""
        UiState.DOWNLOADING -> {
            val (w, t) = progress.value
            if (t > 0) String.format("%.1f/%.1f MB", w / 1048576.0, t / 1048576.0)
            else String.format("%.1f MB", w / 1048576.0)
        }
        UiState.VERIFYING -> "verifying…"
        UiState.FLUSHING -> "saving…"
        UiState.READY, UiState.PENDING -> "ready"
        UiState.FAILED -> "failed"
    }

    private fun humanReason(code: String, message: String): String = when (code) {
        "NETWORK" -> "Network"
        "STORAGE" -> "Not enough free space"
        "SIZE_LIMIT" -> "Package too large"
        "RATE_LIMITED" -> "Update service busy — try again later"
        "FGS_DENIED" -> "System blocked the download service — retry"
        "PARSE", "HTTP_ERROR", "NO_SHA256_DIGEST" -> "Update service error"
        else -> message.ifBlank { "Update failed" }
    }

    private fun versionCode(): Int = try {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    } catch (e: Exception) {
        -1
    }

    override fun onCleared() {
        // JANGAN batalkan apa pun: owner unduhan = service (D11). VM boleh
        // mati; FGS + state-nya hidup sampai selesai, dan VM pengganti
        // membaca state service saat init (recovery handleDone).
        super.onCleared()
    }
}
