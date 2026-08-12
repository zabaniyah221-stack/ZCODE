package com.zaba.zcode.core.diagnostics

import android.content.Context
import com.zaba.zcode.core.files.Paths
import java.io.File

/**
 * RunLogStore — daftar & rotasi log run terminal (build #3).
 *
 * KENAPA ADA: `Paths.runLogsDir()` sudah menampung satu file .log per run,
 * tetapi TIDAK ADA yang pernah menghapusnya. Setiap tap ▶ menambah satu file
 * permanen. Di HP 32-bit dengan storage terbatas itu kebocoran yang tumbuh
 * diam-diam — tidak pernah terlihat sampai penyimpanan penuh, dan user tidak
 * punya cara membersihkannya dari dalam aplikasi.
 *
 * Kebijakan: simpan [MAX_RUN_LOGS] run TERBARU, hapus sisanya. Angkanya bukan
 * tebakan bebas — 50 run cukup untuk menelusuri satu sesi debugging panjang,
 * sementara ukurannya tetap wajar (log biasa beberapa KB).
 *
 * Semua operasi dibungkus runCatching: pembersihan log TIDAK BOLEH menjadi
 * sumber crash baru. Kegagalan menghapus satu file lebih baik daripada
 * aplikasi tertutup saat user menekan Run.
 */
object RunLogStore {

    const val MAX_RUN_LOGS = 50

    /** Satu entri log run, sudah siap tampil (tanpa I/O tambahan di UI). */
    data class Entry(
        val file: File,
        val name: String,
        val sizeBytes: Long,
        val modifiedAt: Long
    ) {
        /** Ukuran ringkas untuk layar sempit: "12KB", "1.4MB". */
        val sizeLabel: String
            get() = when {
                sizeBytes >= 1024L * 1024L -> "%.1fMB".format(sizeBytes / 1048576.0)
                sizeBytes >= 1024L -> "${sizeBytes / 1024}KB"
                else -> "${sizeBytes}B"
            }
    }

    /** Daftar log run, TERBARU dulu. Aman dipanggil walau direktori kosong. */
    fun list(context: Context): List<Entry> = runCatching {
        Paths.runLogsDir(context)
            .listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { Entry(it, it.name, it.length(), it.lastModified()) }
            ?: emptyList()
    }.getOrDefault(emptyList())

    /**
     * Hapus log terlama sampai tersisa [MAX_RUN_LOGS]. Mengembalikan jumlah
     * file yang terhapus.
     *
     * Dipanggil saat run BARU dimulai, bukan saat aplikasi dibuka: pada titik
     * itu file lama sudah pasti tidak sedang ditulis, jadi tidak ada risiko
     * menghapus log yang masih aktif.
     */
    fun rotate(context: Context, keep: Int = MAX_RUN_LOGS): Int = runCatching {
        val semua = list(context)
        if (semua.size <= keep) return@runCatching 0
        var terhapus = 0
        semua.drop(keep).forEach { e ->
            if (runCatching { e.file.delete() }.getOrDefault(false)) terhapus++
        }
        if (terhapus > 0) Breadcrumb.log("RUNLOG_ROTATE", "hapus=$terhapus sisa=$keep")
        terhapus
    }.getOrDefault(0)

    /** Total byte seluruh log run — untuk ditampilkan di Diagnostics. */
    fun totalBytes(context: Context): Long = list(context).sumOf { it.sizeBytes }

    /** Hapus SEMUA log run (aksi eksplisit user dari Diagnostics). */
    fun clearAll(context: Context): Int = runCatching {
        var n = 0
        list(context).forEach { if (runCatching { it.file.delete() }.getOrDefault(false)) n++ }
        Breadcrumb.log("RUNLOG_CLEAR", "hapus=$n")
        n
    }.getOrDefault(0)
}
