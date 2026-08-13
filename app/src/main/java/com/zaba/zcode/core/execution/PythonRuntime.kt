package com.zaba.zcode.core.execution

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.zaba.zcode.core.diagnostics.Breadcrumb

/**
 * PythonRuntime — SATU-SATUNYA pintu memulai interpreter Python (fix 2026-08-12).
 *
 * MASALAH YANG DIPERBAIKI (kandidat force close saat tap Run):
 * Sebelum ini `Python.start(AndroidPlatform(...))` dipanggil dari 6 tempat berbeda
 * (WorkspaceViewModel.preWarmPython, ExecutionEngine x2, PyCall, RuntimeProbe,
 * PluginRunner x2) dengan pola cek-lalu-jalan yang TIDAK sinkron:
 *
 *     if (!Python.isStarted()) { Python.start(...) }   // ← race window di sini
 *
 * `preWarmPython()` berjalan di thread IO saat aplikasi dibuka. Bila user menekan
 * ▶ Run sebelum pre-warm selesai, dua thread bisa sama-sama lolos pengecekan lalu
 * masuk ke `Python.start()` bersamaan. Di sisi Java ini melempar IllegalStateException;
 * di layer native inisialisasi ganda berisiko SIGSEGV — dan SIGSEGV tidak bisa
 * ditangkap CrashReporter, jadi user hanya melihat aplikasi mati tanpa pesan.
 *
 * SOLUSI: satu kunci global. Semua pemanggil WAJIB lewat `ensureStarted()`.
 * Pengecekan dan pemanggilan berada di dalam blok synchronized yang sama, sehingga
 * tidak ada lagi celah antara "cek" dan "jalan".
 *
 * Fungsi ini boleh dipanggil berkali-kali dari thread mana pun; hanya pemanggil
 * pertama yang benar-benar menjalankan start().
 */
object PythonRuntime {

    private val lock = Any()

    @Volatile
    private var startFailure: String? = null

    /** True bila runtime Chaquopy tersedia (Android). False di JVM desktop/CI. */
    fun isAvailable(): Boolean = try {
        Class.forName("com.chaquo.python.Python")
        true
    } catch (e: Throwable) {
        false
    }

    /**
     * Pastikan interpreter Python sudah hidup. Thread-safe, idempoten.
     * @return true bila Python siap dipakai.
     * @throws tidak pernah — kegagalan dicatat & dikembalikan sebagai false.
     */
    fun ensureStarted(context: Context): Boolean {
        if (!isAvailable()) return false
        // Fast path tanpa kunci: mayoritas panggilan terjadi setelah Python hidup.
        if (Python.isStarted()) return true
        synchronized(lock) {
            // Cek ulang DI DALAM kunci — inilah inti perbaikannya.
            if (Python.isStarted()) return true
            startFailure?.let { return false } // sudah pernah gagal, jangan ulangi terus
            return try {
                Breadcrumb.log("PYTHON_START_BEGIN")
                Python.start(AndroidPlatform(context.applicationContext))
                Breadcrumb.log("PYTHON_START_OK")
                true
            } catch (e: IllegalStateException) {
                // Sudah dimulai pihak lain (mis. Chaquopy auto-start) — bukan kegagalan.
                Breadcrumb.log("PYTHON_START_ALREADY", e.message ?: "")
                Python.isStarted()
            } catch (e: Throwable) {
                startFailure = e.message ?: e.javaClass.simpleName
                Breadcrumb.log("PYTHON_START_FAIL", startFailure ?: "")
                false
            }
        }
    }

    /** Pesan kegagalan start terakhir (untuk ditampilkan ke user, bukan diam). */
    fun failureMessage(): String? = startFailure
}
