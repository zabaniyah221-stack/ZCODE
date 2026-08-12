package com.zaba.zcode.core.diagnostics

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Breadcrumb — jejak langkah aplikasi (diagnostik 2026-08-12).
 *
 * KENAPA ADA: user memakai ZCODE di HP tanpa PC, jadi `adb logcat` TIDAK tersedia.
 * Saat aplikasi force close (khususnya crash native / OOM yang TIDAK melewati
 * UncaughtExceptionHandler), satu-satunya bukti yang tersisa adalah apa yang
 * sudah sempat ditulis ke disk. Baris TERAKHIR di file ini = TKP.
 *
 * Kontrak yang dijaga:
 * - Setiap `log()` menulis + flush SEKETIKA (tanpa buffer tertunda). Kalau proses
 *   mati mendadak, baris terakhir tetap ada di disk.
 * - TIDAK PERNAH melempar exception. Diagnostik tidak boleh jadi sumber crash baru.
 * - File di-rotate saat melewati MAX_BYTES supaya tidak menggerogoti storage.
 *
 * Lokasi: <filesDir>/logs/diagnostics/breadcrumb.log
 */
object Breadcrumb {
    private const val MAX_BYTES = 128 * 1024 // 128KB — cukup untuk ratusan sesi
    private val lock = Any()
    private val tsFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var file: File? = null

    /** Dipanggil sekali dari ZcodeApp.onCreate() — sebelum apa pun yang bisa crash. */
    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            try {
                val dir = File(context.filesDir, "logs/diagnostics")
                dir.mkdirs()
                val f = File(dir, "breadcrumb.log")
                if (f.exists() && f.length() > MAX_BYTES) {
                    // rotate: simpan separuh terakhir supaya riwayat sebelumnya tidak hilang total
                    try {
                        val tail = f.readText().takeLast(MAX_BYTES / 2)
                        f.writeText(tail)
                    } catch (e: Throwable) {
                        f.delete()
                    }
                }
                file = f
            } catch (e: Throwable) {
                // diagnostik gagal != aplikasi gagal
            }
        }
    }

    /**
     * Catat satu langkah. `step` sebaiknya KONSTAN & mudah di-grep (mis. "FAB_TAP"),
     * `detail` opsional untuk konteks (nama file, kode error, dll).
     */
    fun log(step: String, detail: String = "") {
        val f = file ?: return
        synchronized(lock) {
            try {
                FileWriter(f, true).use { w ->
                    w.append(tsFormat.format(Date()))
                        .append(" | ")
                        .append(step)
                    if (detail.isNotEmpty()) {
                        // BATAS 4000, BUKAN 400 (2026-08-13). Pesan ImportError numpy
                        // panjangnya ~735 karakter dan boilerplate-nya di DEPAN:
                        // potongan di 400 mendarat tepat di kata "troubles" dan
                        // membuang baris "Original error was: ..." di posisi 664 —
                        // satu-satunya baris yang menyebut sebab sebenarnya.
                        // Diagnostik yang memotong bukti bukan diagnostik.
                        //
                        // Kalau tetap terlalu panjang, pangkas dari TENGAH: kepala
                        // memberi konteks, EKOR memuat sebab akhirnya.
                        w.append(" | ").append(ringkas(detail).replace('\n', ' '))
                    }
                    w.append('\n')
                    w.flush() // WAJIB: tanpa ini baris terakhir hilang saat crash
                }
            } catch (e: Throwable) {
                // sengaja diam
            }
        }
    }

    private const val MAX_DETAIL = 4000

    /**
     * Pangkas dari TENGAH bila melebihi [MAX_DETAIL].
     *
     * Pesan exception yang panjang hampir selalu menaruh sebab sebenarnya di
     * baris TERAKHIR ("Original error was: ...", "Caused by: ..."). Memotong
     * ekor berarti membuang jawabannya.
     */
    internal fun ringkas(detail: String): String {
        if (detail.length <= MAX_DETAIL) return detail
        val sisa = MAX_DETAIL - 40
        val kepala = sisa * 2 / 3
        val ekor = sisa - kepala
        return detail.take(kepala) +
            " …[${detail.length - sisa} karakter dipangkas]… " +
            detail.takeLast(ekor)
    }

    /** Isi penuh breadcrumb (untuk layar Diagnostik / tombol Salin). */
    fun dump(): String = try {
        file?.takeIf { it.exists() }?.readText() ?: "(breadcrumb kosong)"
    } catch (e: Throwable) {
        "(gagal membaca breadcrumb: ${e.message})"
    }

    /** N baris terakhir — bagian paling penting saat menganalisis crash. */
    fun tail(lines: Int = 40): String = try {
        val all = dump().trimEnd().split('\n')
        if (all.size <= lines) all.joinToString("\n") else all.takeLast(lines).joinToString("\n")
    } catch (e: Throwable) {
        "(gagal membaca breadcrumb)"
    }

    fun clear() {
        synchronized(lock) {
            try {
                file?.writeText("")
            } catch (e: Throwable) {
                // abaikan
            }
        }
    }
}
