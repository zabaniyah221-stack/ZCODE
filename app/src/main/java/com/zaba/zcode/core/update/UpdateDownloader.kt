package com.zaba.zcode.core.update

import com.zaba.zcode.core.diagnostics.Breadcrumb
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * UpdateDownloader — v1.0.22 one-tap update, langkah "UNDUH" (RFC D4/D11).
 *
 * Pola di-clone dari `PackageEngineV2.download()` yang sudah terbukti di
 * device ARMv7 (HttpURLConnection, buffer 64 KB, SHA-256 sambil streaming,
 * progress throttle ≥256 KB — pelajaran "47 detik SENYAP" dan "156 event
 * flood", cancel → hapus parsial). Fungsi FUNGSIONAL MURNI (tanpa Context)
 * agar unit-testable; foreground service (D11) yang memanggil dari
 * Dispatchers.IO dan meneruskan progress ke notifikasi/StateFlow.
 *
 * Integritas (D1, jujur): downloader MENGHITUNG SHA-256 dan MENGEMBALIKANNYA;
 * pembanding terhadap field `digest` respons API dilakukan caller pada state
 * VERIFYING — tanpa checksum publikasi yang cocok, file tidak dianggap
 * siap. Download TIDAK membuktikan signer (tidak ada toolchain di HP);
 * Android yang menjaga identitas signer saat install.
 */
object UpdateDownloader {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val USER_AGENT = "zcode-updater/1.0"
    private const val BUFFER_BYTES = 64 * 1024
    private const val PROGRESS_THROTTLE_BYTES = 256 * 1024L
    /** Guard ukuran (D4): tolak APK di atas 100 MB dari field `size` API. */
    private const val MAX_APK_BYTES = 100L * 1024 * 1024
    /** Guard storage (D4, pola package engine): ruang bebas ≥ 1.5× ukuran. */
    private const val MIN_FREE_BYTES = 100L * 1024 * 1024

    sealed interface DownloadResult {
        data class Ok(val file: File, val sha256: String, val bytes: Long) : DownloadResult
        data class Failed(val reasonCode: String, val message: String) : DownloadResult {
            companion object {
                const val SIZE_LIMIT = "SIZE_LIMIT"
                const val STORAGE = "STORAGE"
                const val NETWORK = "NETWORK"
                const val HTTP_ERROR = "HTTP_ERROR"
                const val CANCELLED = "CANCELLED"
            }
        }
    }

    /**
     * Guard pra-download (D4): ukuran dari field `size` API + ruang storage.
     * Ditolak SEBELUM jaringan dipakai — pesan jujur, retryable.
     */
    fun precheck(expectedSizeBytes: Long, destDir: File): DownloadResult.Failed? {
        if (expectedSizeBytes <= 0) {
            return DownloadResult.Failed(
                DownloadResult.Failed.SIZE_LIMIT, "Package size unknown"
            )
        }
        if (expectedSizeBytes > MAX_APK_BYTES) {
            return DownloadResult.Failed(
                DownloadResult.Failed.SIZE_LIMIT,
                "Package too large (${expectedSizeBytes / 1024 / 1024} MB)"
            )
        }
        destDir.mkdirs()
        val need = maxOf(expectedSizeBytes * 3 / 2, MIN_FREE_BYTES)
        val free = destDir.usableSpace
        if (free < need) {
            return DownloadResult.Failed(
                DownloadResult.Failed.STORAGE,
                "Not enough free space (need ${need / 1024 / 1024} MB, free ${free / 1024 / 1024} MB)"
            )
        }
        return null
    }

    /**
     * Unduh [url] ke [dest] dengan streaming + SHA-256 sambil jalan.
     * [shouldCancel] dicek per chunk (pola cancel package engine);
     * [onProgress] di-throttle ≥256 KB + satu emisi final 100%.
     * Gagal/batal → file parsial DIHAPUS (jangan meracuni cache).
     */
    fun download(
        url: String,
        dest: File,
        expectedSizeBytes: Long,
        shouldCancel: () -> Boolean,
        onProgress: (writtenBytes: Long, totalBytes: Long) -> Unit
    ): DownloadResult {
        dest.parentFile?.mkdirs()
        val conn: HttpURLConnection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = true
            }
        } catch (e: Exception) {
            dest.delete()
            val msg = "koneksi: ${e.message}"
            Breadcrumb.log("UPDATE_DOWNLOAD_FAIL", "NETWORK $msg")
            return DownloadResult.Failed(DownloadResult.Failed.NETWORK, "Network error")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                return DownloadResult.Failed(DownloadResult.Failed.HTTP_ERROR, "HTTP $code")
            }
            val totalBytes = conn.contentLengthLong // -1 bila server tak memberi
            // Defense-in-depth: Content-Length yang tiba-tiba melebihi guard
            // (field `size` API bisa kedaluwarsa bila release baru di-cut).
            if (totalBytes > MAX_APK_BYTES) {
                return DownloadResult.Failed(
                    DownloadResult.Failed.SIZE_LIMIT,
                    "Package too large (${totalBytes / 1024 / 1024} MB)"
                )
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            var lastEmit = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(BUFFER_BYTES)
                    while (true) {
                        if (shouldCancel()) {
                            dest.delete() // file parsial jangan meracuni cache
                            Breadcrumb.log("UPDATE_DOWNLOAD_CANCELLED", "at ${written} B")
                            return DownloadResult.Failed(
                                DownloadResult.Failed.CANCELLED, "Cancelled by user"
                            )
                        }
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        written += n
                        // Throttle ≥256 KB: jangan banjiri Compose/notifikasi
                        // di ARMv7 (pelajaran 156 event), jangan 47 detik senyap.
                        if (written - lastEmit >= PROGRESS_THROTTLE_BYTES || (totalBytes > 0 && written == totalBytes)) {
                            lastEmit = written
                            onProgress(written, totalBytes)
                        }
                    }
                }
            }
            onProgress(written, if (totalBytes > 0) totalBytes else written)
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            Breadcrumb.log("UPDATE_DOWNLOAD_OK", "${written} B sha256=${sha256.take(12)}…")
            return DownloadResult.Ok(dest, sha256, written)
        } catch (e: Exception) {
            dest.delete()
            val msg = "unduh: ${e.message}"
            Breadcrumb.log("UPDATE_DOWNLOAD_FAIL", "NETWORK $msg")
            return DownloadResult.Failed(DownloadResult.Failed.NETWORK, "Network error")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Hapus APK versi lama di [dir] (D4: app menjaga cache-nya sendiri —
     * APK lama tak berguna setelah install; cacheDir juga best-effort, jadi
     * jangan biarkan menumpuk). [keepVersion] dipertahankan (mis. PENDING
     * yang belum ter-install user).
     */
    fun cleanupOldApks(dir: File, keepVersion: String?) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { f ->
            if (!f.name.matches(Regex("""^ZCODE-v\d+\.\d+\.\d+\.apk$"""))) return@forEach
            val v = f.name.removePrefix("ZCODE-v").removeSuffix(".apk")
            if (v == keepVersion) return@forEach
            if (!f.delete()) {
                Breadcrumb.log("UPDATE_DOWNLOAD_FAIL", "cleanup ${f.name} gagal")
            }
        }
    }
}
