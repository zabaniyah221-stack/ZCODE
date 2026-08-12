package com.zaba.zcode.core.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CrashReporter — tangkap exception Java yang tidak tertangani (diagnostik 2026-08-12).
 *
 * KENAPA ADA: sebelum ini, crash di thread mana pun langsung mematikan proses
 * tanpa meninggalkan bukti yang bisa dibaca user (tidak ada PC → tidak ada logcat).
 *
 * BATAS JUJUR (penting, jangan dihapus):
 * Handler ini HANYA menangkap Throwable di JVM. Crash NATIVE (SIGSEGV di
 * interpreter Python / libpython) dan pembunuhan oleh OS karena kehabisan memori
 * TIDAK lewat sini. Untuk dua kasus itu, satu-satunya bukti adalah baris terakhir
 * di Breadcrumb. Karena itu keduanya dipakai bersama, bukan salah satu.
 *
 * Lokasi: <filesDir>/logs/diagnostics/crash-<timestamp>.txt (maks 5 file terbaru)
 */
object CrashReporter {
    private const val MAX_FILES = 5
    private val tsFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    @Volatile
    private var dir: File? = null

    @Volatile
    private var appVersion: String = "?"

    fun install(context: Context, versionName: String) {
        try {
            val d = File(context.filesDir, "logs/diagnostics")
            d.mkdirs()
            dir = d
            appVersion = versionName
        } catch (e: Throwable) {
            return
        }

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(thread, throwable)
                Breadcrumb.log("FATAL_JAVA", "${throwable.javaClass.simpleName}: ${throwable.message}")
            } catch (e: Throwable) {
                // jangan pernah crash di dalam crash handler
            }
            // teruskan ke handler bawaan supaya perilaku sistem tetap normal
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(thread: Thread, throwable: Throwable) {
        val d = dir ?: return
        val f = File(d, "crash-${tsFormat.format(Date())}.txt")
        val sb = StringBuilder()
        sb.append("ZCODE crash report\n")
        sb.append("versi   : ").append(appVersion).append('\n')
        sb.append("waktu   : ").append(Date().toString()).append('\n')
        sb.append("thread  : ").append(thread.name).append('\n')
        sb.append("android : ").append(android.os.Build.VERSION.SDK_INT)
            .append(" (").append(android.os.Build.VERSION.RELEASE).append(")\n")
        sb.append("device  : ").append(android.os.Build.MANUFACTURER)
            .append(' ').append(android.os.Build.MODEL).append('\n')
        sb.append("abi     : ").append(android.os.Build.SUPPORTED_ABIS.joinToString(",")).append('\n')
        sb.append("\n--- stack trace ---\n")
        sb.append(stackTraceOf(throwable))
        sb.append("\n--- breadcrumb (40 baris terakhir) ---\n")
        sb.append(Breadcrumb.tail(40))
        sb.append('\n')
        f.writeText(sb.toString())
        prune(d)
    }

    private fun stackTraceOf(t: Throwable): String {
        val sb = StringBuilder()
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 5) {
            if (depth > 0) sb.append("\nCaused by: ")
            sb.append(cur.javaClass.name).append(": ").append(cur.message).append('\n')
            cur.stackTrace.take(40).forEach { sb.append("    at ").append(it.toString()).append('\n') }
            cur = cur.cause
            depth++
        }
        return sb.toString()
    }

    private fun prune(d: File) {
        try {
            val files = d.listFiles { f -> f.name.startsWith("crash-") } ?: return
            if (files.size <= MAX_FILES) return
            files.sortedBy { it.lastModified() }
                .take(files.size - MAX_FILES)
                .forEach { it.delete() }
        } catch (e: Throwable) {
            // abaikan
        }
    }

    /** Laporan crash terakhir (null bila belum pernah crash). */
    fun lastReport(context: Context): String? = try {
        val d = dir ?: File(context.filesDir, "logs/diagnostics")
        d.listFiles { f -> f.name.startsWith("crash-") }
            ?.maxByOrNull { it.lastModified() }
            ?.readText()
    } catch (e: Throwable) {
        null
    }

    fun hasReport(context: Context): Boolean = lastReport(context) != null
}
