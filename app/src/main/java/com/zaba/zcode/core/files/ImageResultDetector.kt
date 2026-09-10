package com.zaba.zcode.core.files

import java.io.File

/**
 * ImageResultDetector — deteksi gambar hasil Run (v1.0.23, preview-PNG).
 *
 * Script Python berjalan dengan cwd = workspace (filesDir — lihat
 * zcode_runner.py + ExecutionEngine.workspaceDirPath), sehingga hasil
 * `plt.savefig("grafik.png")` mendarat di workspace root. Detektor ini
 * memindai file gambar yang mtime-nya >= mulai Run — murah (satu
 * listFiles per run, pasca-exit) dan murni java.io: bisa diuji JVM tanpa
 * Android runtime (pola bagian-murni RFC D9 v1.0.22).
 *
 * Ukuran/kuota: tidak ada; deteksi tidak pernah menghapus/memindahkan file
 * milik user — hanya membaca nama + mtime.
 */
object ImageResultDetector {

    /** Ekstensi yang dikenali (output umum matplotlib/Pillow di HP). */
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp")

    fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    /**
     * Gambar baru di [dir] sejak [sinceMs] (waktu mulai Run), urut waktu —
     * file pertama = hasil pertama. Directory kosong/tak ada = list kosong.
     */
    fun detectNewImages(dir: File, sinceMs: Long): List<File> {
        val files = dir.listFiles() ?: return emptyList()
        return files
            .filter { it.isFile && it.lastModified() >= sinceMs && isImageFile(it.name) }
            .sortedBy { it.lastModified() }
    }
}
