package com.zaba.zcode.core.packageengine

import android.content.Context
import com.chaquo.python.Python
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * PyCall — helper panggil fungsi Python (package_runtime) dan terima hasil JSON.
 *
 * Kontrak: fungsi Python menerima argumen primitif/JSON-string dan MENGEMBALIKAN
 * JSON string (bukan dict — PyObject.toString() dict = repr, bukan JSON).
 * Semua panggilan berjalan di thread background dengan timeout (metadata/resolve
 * BOLEH punya timeout — yang dilarang hard timeout hanya interactive session).
 */
object PyCall {

    class PyCallException(message: String) : Exception(message)

    /** Panggil fn di module Python; hasil = JSON string. Null → gagal/timeout. */
    fun callJson(
        context: Context,
        module: String,
        fn: String,
        vararg args: Any?
    ): String? {
        if (!RuntimeProbe.isChaquopyAvailable()) return null
        val appContext = context.applicationContext
        val result = AtomicReference<String?>(null)
        val error = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        Thread {
            try {
                if (!com.zaba.zcode.core.execution.PythonRuntime.ensureStarted(appContext)) {
                    // CATATAN: JANGAN memakai `error(...)` di sini — di scope ini ada
                    // variabel lokal bernama `error` (AtomicReference), sehingga
                    // pemanggilan `error(...)` berisiko resolusi ambigu di compiler.
                    // Set nilai langsung; blok finally tetap menjalankan countDown().
                    error.set(
                        com.zaba.zcode.core.execution.PythonRuntime.failureMessage()
                            ?: "Python runtime tidak tersedia (butuh Chaquopy)"
                    )
                    return@Thread
                }
                val py = Python.getInstance().getModule(module)
                val obj = py.callAttr(fn, *args)
                result.set(obj.toString())
            } catch (e: Exception) {
                error.set(e.message ?: e.toString())
            } finally {
                latch.countDown()
            }
        }.start()
        if (!latch.await(90, TimeUnit.SECONDS)) {
            throw PyCallException("Python call timeout: $module.$fn")
        }
        error.get()?.let { throw PyCallException("$module.$fn: $it") }
        return result.get()
    }
}
