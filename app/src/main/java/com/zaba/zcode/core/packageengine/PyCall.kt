package com.zaba.zcode.core.packageengine

import android.content.Context
import android.os.Looper
import com.chaquo.python.Python

/**
 * PyCall — helper sinkron untuk fungsi Python package_runtime.
 *
 * PENTING: sinkron di sini berarti worker Python dimiliki thread pemanggil.
 * Caller WAJIB thread background. Versi v1.0.15 membuat Thread internal lalu
 * hanya menunggu CountDownLatch 90 detik: saat await timeout, worker Python
 * tidak dibatalkan dan terus hidup tanpa owner. PackageEngine melepas busyFlag,
 * sehingga resolve berikutnya dapat overlap dan merusak cache global.
 *
 * Timeout operasi panjang bukan tanggung jawab wrapper ini. Network timeout,
 * retry budget, progress, dan cooperative cancellation dimiliki resolver.
 */
object PyCall {

    class PyCallException(message: String) : Exception(message)

    /** Panggil fn di module Python; hasil harus JSON string. */
    fun callJson(
        context: Context,
        module: String,
        fn: String,
        vararg args: Any?
    ): String? {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "PyCall $module.$fn wajib dipanggil dari background thread"
        }
        if (!RuntimeProbe.isChaquopyAvailable()) return null
        val appContext = context.applicationContext
        if (!com.zaba.zcode.core.execution.PythonRuntime.ensureStarted(appContext)) {
            throw PyCallException(
                com.zaba.zcode.core.execution.PythonRuntime.failureMessage()
                    ?: "Python runtime tidak tersedia (butuh Chaquopy)"
            )
        }
        return try {
            val py = Python.getInstance().getModule(module)
            py.callAttr(fn, *args).toString()
        } catch (e: Exception) {
            throw PyCallException("$module.$fn: ${e.message ?: e}")
        }
    }
}
