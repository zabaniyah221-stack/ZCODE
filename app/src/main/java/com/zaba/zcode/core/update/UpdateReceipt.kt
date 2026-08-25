package com.zaba.zcode.core.update

import android.content.Context
import com.zaba.zcode.core.diagnostics.Breadcrumb
import org.json.JSONObject
import java.io.File

/**
 * UpdateReceipt — v1.0.22, state antar-restart (RFC D5.3/D5.6).
 *
 * Masalah yang dipecahkan: setelah app restart (pasca-install atau user
 * batal di dialog system), proses baru harus tahu apa yang terjadi —
 * INSTALLED / PENDING / STALE — tanpa menebak.
 *
 * Kontrak keras (fail-closed, RFC D5.2):
 * 1. Receipt HANYA ditulis SETELAH flush workspace sukses — [write] menolak
 *    dipanggil dengan [flushOk]=false (mesinnya, bukan kebiasaan).
 * 2. Penulisan atomik (temp + rename) — pola TelemetryStore/TransactionManager;
 *    crash di tengah tulis tidak boleh meninggalkan receipt setengah jadi.
 * 3. cacheDir = BEST-EFFORT (docs resmi: sistem boleh hapus saat storage
 *    rendah; user bisa clear cache). Karena itu [processOnStartup] SELALU
 *    cek keberadaan file APK dulu — file hilang = state balik AVAILABLE
 *    (unduh ulang), BUKAN error.
 * 4. Versi lama tidak pernah disentuh: receipt hanya metadata; satu-satunya
 *    operasi destruktif ada di Android saat user confirm install.
 */
object UpdateReceipt {

    private const val STATUS_PENDING = "PENDING_INSTALL"

    data class Parsed(
        val from: String,
        val to: String,
        val fromCode: Int,
        val toCode: Int,
        val sha256: String,
        val apkPath: String,
        val writtenAt: Long,
        val status: String
    )

    sealed interface Outcome {
        object None : Outcome
        /** APK di cache sudah hilang (best-effort) → unduh ulang. */
        object ApkGone : Outcome
        data class Installed(val from: String, val to: String) : Outcome
        /** User belum confirm dialog system — APK siap dipasang ulang. */
        object Pending : Outcome
        data class Stale(val reason: String) : Outcome
    }

    fun apksDir(context: Context): File =
        File(context.cacheDir, "update").apply { mkdirs() }

    fun apkFile(context: Context, version: String): File =
        File(apksDir(context), "ZCODE-v$version.apk")

    private fun receiptFile(context: Context): File = File(apksDir(context), "receipt.json")

    /**
     * Tulis receipt atomik. [flushOk] HARUS hasil `flushSaveSync` — bila
     * false, TIDAK ADA penulisan (fail-closed: instalasi yang meluncurkan
     * saat flush gagal bisa kehilangan draft user).
     */
    fun write(
        context: Context,
        from: String,
        fromCode: Int,
        to: String,
        toCode: Int,
        sha256: String,
        apkFile: File,
        flushOk: Boolean
    ) {
        check(flushOk) {
            "UPDATE_RECEIPT: flush workspace gagal — receipt TIDAK ditulis (fail-closed, RFC D5.2)"
        }
        val f = receiptFile(context)
        val root = JSONObject()
            .put("from", from)
            .put("fromCode", fromCode)
            .put("to", to)
            .put("toCode", toCode)
            .put("sha256", sha256)
            .put("apk", apkFile.absolutePath)
            .put("writtenAt", System.currentTimeMillis())
            .put("status", STATUS_PENDING)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(f)) {
            tmp.copyTo(f, overwrite = true)
            tmp.delete()
        }
        Breadcrumb.log(
            "UPDATE_RECEIPT_WRITTEN",
            "$from→$to sha256=${sha256.take(12)}… apk=${apkFile.name}"
        )
    }

    fun read(context: Context): Parsed? {
        val f = receiptFile(context)
        if (!f.exists()) return null
        return try {
            val root = JSONObject(f.readText())
            Parsed(
                from = root.optString("from", ""),
                to = root.optString("to", ""),
                fromCode = root.optInt("fromCode", -1),
                toCode = root.optInt("toCode", -1),
                sha256 = root.optString("sha256", ""),
                apkPath = root.optString("apk", ""),
                writtenAt = root.optLong("writtenAt", 0L),
                status = root.optString("status", "")
            )
        } catch (e: Exception) {
            // korup → buang (lebih aman daripada menafsirkan setengah jadi)
            f.delete()
            null
        }
    }

    fun delete(context: Context) {
        receiptFile(context).delete()
    }

    private fun currentVersionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    } catch (e: Exception) {
        -1
    }

    /**
     * Proses receipt saat start-up (dipanggil ZcodeApp). Urutan check (RFC
     * D5.6): (1) file APK masih ada? cache best-effort — hilang = ApkGone.
     * (2) bandingkan versionCode sekarang vs receipt:
     *   - sekarang >= toCode → INSTALLED (update terpasang; == berarti
     *     persis update ini, > berarti versi lebih baru sudah di atasnya) —
     *     cleanup;
     *   - sekarang == fromCode → PENDING (user batal di dialog system) —
     *     APK dipertahankan untuk pasang ulang;
     *   - selain itu (receipt lebih tua/di luar jangkauan) → STALE — buang.
     */
    fun processOnStartup(context: Context): Outcome {
        val r = read(context) ?: return Outcome.None
        val apk = File(r.apkPath)
        if (!apk.exists()) {
            // Cache dihapus OS/user (best-effort). Bukan error: state balik
            // AVAILABLE, user tinggal unduh ulang (RFC D5.6 + §3 kasus batas).
            delete(context)
            Breadcrumb.log("UPDATE_RECEIPT_STALE", "apk hilang dari cache ($r.apkPath)")
            return Outcome.ApkGone
        }
        val cur = currentVersionCode(context)
        // `return when` eksplisit: fungsi block body TIDAK otomatis
        // mengembalikan ekspresi terakhir (berbeda expression body).
        return when {
            cur >= r.toCode -> {
                delete(context)
                UpdateDownloader.cleanupOldApks(apk.parentFile, null)
                Breadcrumb.log("UPDATE_INSTALLED", "from ${r.fromCode} to ${r.toCode}")
                Outcome.Installed(r.from, r.to)
            }
            cur == r.fromCode -> {
                Breadcrumb.log("UPDATE_PENDING", "to=${r.toCode} apk tersimpan")
                Outcome.Pending
            }
            else -> {
                delete(context)
                UpdateDownloader.cleanupOldApks(apk.parentFile, null)
                Breadcrumb.log("UPDATE_RECEIPT_STALE", "receipt ${r.from}→${r.to} tak cocok versi $cur")
                Outcome.Stale("receipt ${r.from}→${r.to} tak cocok versi $cur")
            }
        }
    }
}
