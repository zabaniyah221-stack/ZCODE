package com.zaba.zcode.core.update

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zaba.zcode.MainActivity
import com.zaba.zcode.R
import com.zaba.zcode.core.diagnostics.Breadcrumb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UpdateDownloadService — v1.0.22, owner tunggal unduhan (RFC D11).
 *
 * Keputusan user 2026-08-24: unduhan harus tetap jalan saat user beralih ke
 * app lain (SMS/panggilan telepon) → foreground service, bukan ViewModel.
 *
 * Kontrak target 34 (docs resmi, RFC §6):
 * - manifest mendeklarasikan `foregroundServiceType="dataSync"` + permission
 *   FOREGROUND_SERVICE(_DATA_SYNC); tanpa itu `startForeground()` melempar
 *   MissingForegroundServiceTypeException;
 * - `ServiceCompat.startForeground(this, id, notification, type)` — helper
 *   androidx yang direkomendasikan A14; id ≠ 0; type di-pass eksplisit;
 * - tangkap ForegroundServiceStartNotAllowedException (API 31+): tidak bisa
 *   → gagal jujur, file tak tersentuh, retry setelah interaksi user;
 * - limit 6 jam dataSync HANYA untuk app targeting 15+ (kita target 34 →
 *   tidak ada limit waktu; bila kelak target 35, wajib implementasi
 *   Service.onTimeout — RFC D11).
 *
 * Owner tunggal = service. UI (Activity-scoped UpdateViewModel) subscribe
 * `state` (StateFlow in-process — satu process dengan Activity). Batal dari
 * UI = `requestCancel()` — flag dicek per chunk; parsial DIHAPUS downloader.
 *
 * NOTIFIKASI: channel `update` (low importance; [ensureChannel] idempotent —
 * dipanggil ZcodeApp saat start-up dan di sini sebagai pengaman). Ongoing
 * selama unduh; selesai/gagal → detached (tetap terlihat) lalu stopSelf.
 * Bila POST_NOTIFICATIONS ditolak (API 33+): FGS tetap jalan, notifikasi
 * tetap di Task Manager (bukan drawer) — verified docs; tidak memblokir.
 */
class UpdateDownloadService : Service() {

    /**
     * State unduhan — direct nested di class (BUKAN di companion object):
     * tipe yang hidup di companion TIDAK bisa diakses dari file lain sebagai
     * `UpdateDownloadService.DownloadState` (akses via nama class hanya
     * berlaku untuk fungsi/properti, bukan tipe) → ViewModel tak akan
     * bisa membaca state.
     */
    sealed interface DownloadState {
        object Idle : DownloadState
        data class InProgress(val written: Long, val total: Long) : DownloadState
        data class Done(val file: File, val sha256: String, val bytes: Long) : DownloadState
        data class Failed(val reasonCode: String, val message: String) : DownloadState
        object Cancelled : DownloadState
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val destPath = intent?.getStringExtra(EXTRA_DEST)
        val expectedSize = intent?.getLongExtra(EXTRA_SIZE, -1L) ?: -1L
        if (url == null || destPath == null) {
            Breadcrumb.log("UPDATE_DOWNLOAD_FAIL", "start tanpa parameter")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!downloadActive.compareAndSet(false, true)) {
            // Unduhan lain masih jalan di instance ini — jangan double download.
            Breadcrumb.log("UPDATE_DOWNLOAD_FAIL", "start duplikat diabaikan")
            return START_NOT_STICKY
        }
        ensureChannel(this)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                buildNotification("starting…", null, ongoing = true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
            Breadcrumb.log("UPDATE_FGS_START", "dataSync ${url.take(80)}")
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Breadcrumb.log("UPDATE_DOWNLOAD_FAIL", "FGS_START_NOT_ALLOWED: ${e.message}")
            _state.value = DownloadState.Failed(
                "FGS_DENIED", "System refused to start the download service"
            )
            downloadActive.set(false)
            stopSelf()
            return START_NOT_STICKY
        }

        val dest = File(destPath)
        scope.launch {
            val pre = UpdateDownloader.precheck(expectedSize, dest.parentFile ?: dest)
            if (pre != null) {
                fail(pre.reasonCode, pre.message)
                return@launch
            }
            val result = UpdateDownloader.download(
                url = url,
                dest = dest,
                expectedSizeBytes = expectedSize,
                shouldCancel = { cancelFlag.get() },
                onProgress = { written, total ->
                    _state.value = DownloadState.InProgress(written, total)
                    updateNotification(written, total, expectedSize)
                }
            )
            when (result) {
                is UpdateDownloader.DownloadResult.Ok -> {
                    // VERIFYING dilakukan UpdateViewModel (sha vs digest API, RFC D1)
                    // — service hanya menyerahkan hasil hitung.
                    _state.value = DownloadState.Done(result.file, result.sha256, result.bytes)
                    stopForeground(STOP_FOREGROUND_DETACH)
                    Breadcrumb.log("UPDATE_FGS_STOP", "done")
                    stopSelf()
                }
                is UpdateDownloader.DownloadResult.Failed -> fail(result.reasonCode, result.message)
            }
        }
        return START_NOT_STICKY
    }

    private fun fail(code: String, message: String) {
        Breadcrumb.log("UPDATE_DOWNLOAD_FAIL", "$code: $message")
        _state.value = DownloadState.Failed(code, message)
        // Detach (bukan remove): user tetap melihat "gagal" + bisa retry dari drawer.
        stopForeground(STOP_FOREGROUND_DETACH)
        Breadcrumb.log("UPDATE_FGS_STOP", "failed")
        stopSelf()
    }

    private fun updateNotification(written: Long, total: Long, expectedSize: Long) {
        val t = if (total > 0) total else expectedSize
        val text = if (t > 0) {
            val pct = ((written * 100) / t).toInt()
            String.format("%.1f / %.1f MB (%d%%)", written / 1048576.0, t / 1048576.0, pct)
        } else {
            String.format("%.1f MB", written / 1048576.0)
        }
        val progress: Triple<Int, Int, Boolean>? =
            if (t > 0) Triple(t.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                written.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), false)
            else Triple(0, 0, true)
        val notif = buildNotification(text, progress, ongoing = true)
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, notif)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS ditolak — FGS tetap jalan; notifikasi FGS tetap
            // di Task Manager (bukan drawer). Tidak memblokir (verified docs, D11).
            Breadcrumb.log("UPDATE_DOWNLOAD_FAIL", "notif permission: ${e.message}")
        }
    }

    private fun buildNotification(
        contentText: String,
        progress: Triple<Int, Int, Boolean>?,
        ongoing: Boolean
    ): Notification {
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            this, 0, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update)
            .setContentTitle("ZCODE — downloading update")
            .setContentText(contentText)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
        if (progress != null) {
            n.setProgress(progress.first, progress.second, progress.third)
        }
        return n.build()
    }

    override fun onDestroy() {
        scope.cancel()
        downloadActive.set(false)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "update"
        private const val NOTIF_ID = 42 // RFC D11: id ≠ 0
        private const val EXTRA_URL = "url"
        private const val EXTRA_DEST = "dest"
        private const val EXTRA_SIZE = "size"

        private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
        val state: StateFlow<DownloadState> = _state.asStateFlow()

        private val cancelFlag = AtomicBoolean(false)
        private val downloadActive = AtomicBoolean(false)

        /**
         * Start FGS. [url]/[dest]/[expectedSize] dari hasil cek (D2/D4).
         * Panggil HANYA setelah user tap "Download & Update" (D10: tanpa
         * auto-download; sekaligus aman aturan bg-start FGS target 31+).
         */
        fun start(context: Context, url: String, dest: File, expectedSize: Long) {
            cancelFlag.set(false)
            _state.value = DownloadState.InProgress(0L, expectedSize)
            val intent = Intent(context, UpdateDownloadService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_DEST, dest.absolutePath)
                .putExtra(EXTRA_SIZE, expectedSize)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Batal dari UI: flag dicek per chunk; file parsial dihapus downloader. */
        fun requestCancel() {
            if (!cancelFlag.compareAndSet(false, true)) return
            Breadcrumb.log("UPDATE_DOWNLOAD_CANCELLED", "requested")
            _state.value = DownloadState.Cancelled
        }

        fun reset() {
            _state.value = DownloadState.Idle
        }

        /** Channel low importance — idempotent; dipanggil ZcodeApp + service. */
        fun ensureChannel(context: Context) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "ZCODE Updates",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress download update ZCODE"
            }
            context.getSystemService(android.app.NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
