package com.zaba.zcode.core.plugins

import android.content.Context
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * PluginRunner — eksekusi plugin transform Python (batch anti-sepi, F3).
 *
 * Dual-backend mengikuti pola ExecutionEngine:
 *  1. Chaquopy in-process (Android): module `zcode_plugins` di app/src/main/python.
 *  2. python3 subprocess (desktop/dev): path skrip via env `ZCODE_PLUGINS_PY`
 *     atau fallback layout repo — kalau tidak ada, graceful error (dev mode).
 *
 * Kontrak: TIDAK PERNAH throw — selalu return PluginResult(ok, code, report).
 * Plugin adalah fitur; kegagalan plugin tidak boleh merobohkan app.
 */
data class PluginResult(val ok: Boolean, val code: String, val report: String)

object PluginRunner {

    private const val TIMEOUT_MS = 15_000L

    /** Deteksi runtime Chaquopy (sama dengan ExecutionEngine). */
    fun isChaquopyAvailable(): Boolean = try {
        Class.forName("com.chaquo.python.Python")
        true
    } catch (e: Throwable) {
        false
    }

    fun run(context: Context?, pluginId: String, code: String): PluginResult = try {
        if (context != null && isChaquopyAvailable()) {
            runChaquopy(context, pluginId, code)
        } else {
            runSubprocess(pluginId, code)
        }
    } catch (e: PyException) {
        PluginResult(false, code, "Python error: ${e.message}")
    } catch (e: Exception) {
        PluginResult(false, code, "Plugin runner error: ${e.message}")
    }

    private fun runChaquopy(context: Context, pluginId: String, code: String): PluginResult {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
        val json = Python.getInstance()
            .getModule("zcode_plugins")
            .callAttr("run_json", pluginId, code)
            .toString()
        return parse(json, code)
    }

    private fun runSubprocess(pluginId: String, code: String): PluginResult {
        val script = System.getenv("ZCODE_PLUGINS_PY")
            ?: listOf(
                "app/src/main/python/zcode_plugins.py",
                "../app/src/main/python/zcode_plugins.py"
            )
                .map { File(it) }
                .firstOrNull { it.isFile }
                ?.absolutePath
        if (script == null) {
            return PluginResult(
                false, code,
                "Plugin Python backend tidak tersedia di mode dev (zcode_plugins.py tidak ditemukan — set ZCODE_PLUGINS_PY)."
            )
        }
        val tmp = File.createTempFile("zcode_plugin_in_", ".py")
        return try {
            tmp.writeText(code)
            val proc = ProcessBuilder("python3", script, pluginId, tmp.absolutePath)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            if (!proc.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly()
                return PluginResult(false, code, "Plugin timeout (> ${TIMEOUT_MS / 1000}s)")
            }
            // Output CLI = 1 baris JSON; ambil baris terakhir biar aman dari noise
            parse(out.trim().lines().lastOrNull() ?: "", code)
        } finally {
            tmp.delete()
        }
    }

    private fun parse(json: String, fallback: String): PluginResult = try {
        val o = JSONObject(json)
        PluginResult(
            o.optBoolean("ok", false),
            o.optString("code", fallback),
            o.optString("report", "")
        )
    } catch (e: Exception) {
        PluginResult(false, fallback, "Plugin mengembalikan output tidak valid")
    }
}
