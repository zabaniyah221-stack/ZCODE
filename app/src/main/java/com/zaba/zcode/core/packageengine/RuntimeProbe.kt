package com.zaba.zcode.core.packageengine

import android.content.Context
import com.zaba.zcode.core.files.Paths
import com.chaquo.python.Python
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * RuntimeProbe — capture runtime eksak (SPEC-001 Phase 0 "Capture exact runtime"):
 *   Chaquopy version, Python version, ABI, Android API, pip version, wheel tags.
 *
 * Memanggil package_runtime.probe di dalam Chaquopy, lalu menyimpan
 * python-env/state/runtime.json. Di desktop (tanpa Chaquopy) memakai nilai host.
 * Snapshot ini menjadi bagian dari compatibility report.
 */
object RuntimeProbe {

    data class RuntimeInfo(
        val pythonVersion: String,
        val pythonFull: String,
        val machine: String,
        val platform: String,
        val abis: List<String>,
        val androidApi: Int?,
        val pipVersion: String,
        val chaquopyVersion: String,
        val supportedTags: List<String>,
        val sitePackages: List<String>,
        val raw: JSONObject
    )

    @Volatile
    private var cached: RuntimeInfo? = null

    fun isChaquopyAvailable(): Boolean = try {
        Class.forName("com.chaquo.python.Python")
        true
    } catch (e: Throwable) {
        false
    }

    fun probe(context: Context, androidApi: Int? = null, force: Boolean = false): RuntimeInfo {
        cached?.let { if (!force) return it }
        val info = if (isChaquopyAvailable()) probeChaquopy(context, androidApi) else probeHost(androidApi)
        persist(context, info)
        cached = info
        return info
    }

    fun cachedInfo(): RuntimeInfo? = cached

    private fun probeChaquopy(context: Context, androidApi: Int?): RuntimeInfo {
        val appContext = context.applicationContext
        // The caller owns the worker dispatcher. Do not spawn a second thread
        // and return a fallback while that thread keeps using Chaquopy unseen.
        val raw = try {
            if (!com.zaba.zcode.core.execution.PythonRuntime.ensureStarted(appContext)) {
                null
            } else {
                val py = Python.getInstance().getModule("package_runtime.probe")
                val json = py.callAttr("probe_runtime_json", androidApi ?: -1).toString()
                JSONObject(json)
            }
        } catch (_: Exception) {
            null
        }
        return raw?.let(::fromJson) ?: probeHost(androidApi)
    }

    private fun probeHost(androidApi: Int?): RuntimeInfo {
        // Desktop/dev: pakai sysconfig lokal sebagai estimasi (bukan klaim Android).
        val raw = JSONObject()
        raw.put("python_version", System.getProperty("java.version") ?: "unknown")
        raw.put("machine", "host")
        raw.put("platform", "desktop")
        raw.put("abis", JSONArray())
        raw.put("supported_tags", JSONArray())
        raw.put("pip_version", "host")
        raw.put("chaquopy_version", "17.0.0")
        return fromJson(raw)
    }

    private fun fromJson(raw: JSONObject): RuntimeInfo {
        fun list(key: String): List<String> {
            val arr = raw.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).map { arr.optString(it) }
        }
        return RuntimeInfo(
            pythonVersion = raw.optString("python_version", "unknown"),
            pythonFull = raw.optString("python_full", raw.optString("python_version", "")),
            machine = raw.optString("machine", ""),
            platform = raw.optString("platform", ""),
            abis = list("abis"),
            androidApi = if (raw.has("android_api") && raw.optInt("android_api", -1) > 0) raw.optInt("android_api") else null,
            pipVersion = raw.optString("pip_version", ""),
            chaquopyVersion = raw.optString("chaquopy_version", "17.0.0"),
            supportedTags = list("supported_tags"),
            sitePackages = list("site_packages"),
            raw = raw
        )
    }

    private fun persist(context: Context, info: RuntimeInfo) {
        try {
            val state = Paths.pythonState(context)
            val f = File(state, "runtime.json")
            val tmp = File(state, "runtime.json.tmp")
            tmp.writeText(info.raw.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(info.raw.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            // gagal menyimpan bukan akhir dunia
        }
    }
}
