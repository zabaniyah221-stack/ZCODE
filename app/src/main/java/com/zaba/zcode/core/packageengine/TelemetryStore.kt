package com.zaba.zcode.core.packageengine

import android.content.Context
import com.zaba.zcode.core.files.Paths
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * TelemetryStore — dashboard metric minimum (SPEC-001 "Gathering Results").
 *
 * Counter + event failure disimpan ke python-env/state/telemetry.json dengan
 * penulisan atomik (temp + rename). Metric:
 *   install_attempts, install_success, install_failure, rollback_count,
 *   smoke_test_failure, native_load_failure, dependency_conflict,
 *   package_not_available, terminal_runs, terminal_interrupts,
 *   terminal_process_failures, terminal_log_bytes, terminal_memory_peak
 * Failure diklasifikasikan per stage (NETWORK, RESOLUTION, COMPATIBILITY, …),
 * BUKAN satu kategori INSTALL_FAILED (SPEC: harus actionable).
 */
object TelemetryStore {
    private val lock = Any()
    private var file: File? = null
    private val counters = mutableMapOf<String, Long>()
    private val failures = ArrayDeque<JSONObject>()
    private const val MAX_FAILURES = 50

    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            file = File(Paths.pythonState(context), "telemetry.json")
            loadLocked()
        }
    }

    private fun loadLocked() {
        val f = file ?: return
        try {
            if (!f.exists()) return
            val root = JSONObject(f.readText())
            val c = root.optJSONObject("counters") ?: return
            c.keys().forEach { key -> counters[key] = c.optLong(key, 0L) }
            val arr = root.optJSONArray("failures")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    failures.addLast(arr.getJSONObject(i))
                }
            }
        } catch (e: Exception) {
            // file korup → mulai kosong (jangan crash)
        }
    }

    fun increment(key: String, n: Long = 1) {
        synchronized(lock) {
            counters[key] = (counters[key] ?: 0L) + n
            saveLocked()
        }
    }

    /** Catat nilai puncak (peak metric: terminal_memory_peak, log bytes, dst). */
    fun recordPeak(key: String, value: Long) {
        synchronized(lock) {
            val cur = counters[key] ?: 0L
            if (value > cur) {
                counters[key] = value
                saveLocked()
            }
        }
    }

    /** Catat kegagalan dengan kode stage yang actionable. */
    fun recordFailure(code: String, stage: String, packageName: String, message: String) {
        synchronized(lock) {
            incrementLocked("install_failure")
            val ev = JSONObject()
            ev.put("ts", System.currentTimeMillis())
            ev.put("code", code)
            ev.put("stage", stage)
            ev.put("package", packageName)
            ev.put("message", message.take(500))
            failures.addLast(ev)
            while (failures.size > MAX_FAILURES) failures.removeFirst()
            saveLocked()
        }
    }

    private fun incrementLocked(key: String, n: Long = 1) {
        counters[key] = (counters[key] ?: 0L) + n
    }

    fun snapshot(): JSONObject {
        synchronized(lock) {
            val root = JSONObject()
            root.put("version", 1)
            val c = JSONObject()
            counters.toSortedMap().forEach { (k, v) -> c.put(k, v) }
            root.put("counters", c)
            val arr = JSONArray()
            failures.forEach { arr.put(it) }
            root.put("failures", arr)
            return root
        }
    }

    private fun saveLocked() {
        val f = file ?: return
        try {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(snapshot().toString())
            if (!tmp.renameTo(f)) {
                // fallback: tulis langsung kalau rename gagal (FS aneh)
                f.writeText(snapshot().toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            // telemetri tidak boleh memblokir install
        }
    }
}
