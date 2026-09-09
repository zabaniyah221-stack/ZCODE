package com.zaba.zcode.core.editor

import android.content.Context
import com.zaba.zcode.core.diagnostics.Breadcrumb
import com.zaba.zcode.core.packageengine.PyCall
import org.json.JSONArray
import org.json.JSONObject

/**
 * SpikeLint — jembatan Kotlin untuk Spike Intelligence Engine (v1.0.23,
 * RFC_V1023_SPIKE_INTELLIGENCE §7).
 *
 * Kontrak Python: `zcode_plugins.run_spike_json(payload) -> JSON string`
 * (fail-open by design — tidak pernah melempar untuk payload apa pun).
 * Sisi Kotlin juga fail-open: kegagalan APA PUN mengembalikan null dan
 * jalur editor tetap hidup — Checker tetap bekerja sendirian seperti
 * v1.0.22 bila pack "Editor Intelligence" belum terpasang.
 *
 * Probe ketersediaan (health_check) di-cache per process: tanpa pack,
 * lint tidak pernah memanggil Python dua kali.
 */
object SpikeLint {

    /** Budget file (RFC §4.2): analisis hanya untuk file < 256 KB. */
    const val MAX_CODE_CHARS = 256 * 1024

    data class Result(val problems: List<Problem>, val elapsedMs: Long)

    data class Block(
        val name: String,
        val line: Int,
        val complexity: Int,
        val isHigh: Boolean
    )

    data class ComplexityReport(
        val available: Boolean,
        val maxComplexity: Int,
        val maintainabilityIndex: Double,
        val functionCount: Int,
        val classCount: Int,
        val blocks: List<Block>
    ) {
        companion object {
            fun unavailable() = ComplexityReport(false, 0, 0.0, 0, 0, emptyList())
        }
    }

    /** Cache probe per process (dipanggil dari thread background; @Volatile). */
    @Volatile
    private var pyflakesReady: Boolean? = null

    /** true bila pack Editor Intelligence terpasang (pyflakes importable). */
    fun pyflakesAvailable(context: Context): Boolean {
        pyflakesReady?.let { return it }
        val ready = try {
            val out = PyCall.callJson(
                context.applicationContext,
                "zcode_plugins",
                "run_spike_json",
                JSONObject().put("action", "health_check").toString()
            )
            out != null && JSONObject(out)
                .optJSONObject("health")?.optJSONObject("pyflakes")
                ?.optBoolean("installed", false) == true
        } catch (e: Exception) {
            Breadcrumb.log("SPKE_PROBE_FAIL", e.message ?: "")
            false
        }
        pyflakesReady = ready
        return ready
    }

    /**
     * Lint pyflakes. null = fail-open (engine absen/error/budget) —
     * pemanggil wajib mempertahankan daftar Checker apa adanya.
     * Wajib dipanggil dari thread background (kontrak PyCall).
     */
    fun lint(context: Context, code: String, filename: String): Result? {
        if (code.isEmpty() || code.length > MAX_CODE_CHARS) return null
        val started = System.currentTimeMillis()
        return try {
            val payload = JSONObject()
                .put("action", "lint")
                .put("code", code)
                .put("kwargs", JSONObject().put("filename", filename))
            val out = PyCall.callJson(
                context.applicationContext, "zcode_plugins", "run_spike_json",
                payload.toString()
            ) ?: return null
            val root = JSONObject(out)
            if (!root.optBoolean("ok", false)) return null
            val issues = root.optJSONArray("issues") ?: JSONArray()
            val problems = mutableListOf<Problem>()
            for (i in 0 until issues.length()) {
                val issue = issues.optJSONObject(i) ?: continue
                problems.add(
                    Problem(
                        severity = if (issue.optString("severity") == "error") {
                            Severity.ERROR
                        } else {
                            Severity.WARNING
                        },
                        message = issue.optString("message"),
                        line = issue.optInt("line", 1),
                        column = issue.optInt("column", 0).takeIf { c -> c > 0 },
                        source = "pyflakes"
                    )
                )
            }
            val elapsed = System.currentTimeMillis() - started
            Breadcrumb.log("SPKE_LINT_MS", "$elapsed count=${problems.size}")
            Result(problems, elapsed)
        } catch (e: Exception) {
            Breadcrumb.log("SPKE_LINT_FAIL", e.message ?: "")
            null
        }
    }

    /** Laporan kompleksitas (mccabe) untuk aksi palette. null = fail-open. */
    fun complexity(context: Context, code: String): ComplexityReport? {
        if (code.isEmpty() || code.length > MAX_CODE_CHARS) return null
        return try {
            val payload = JSONObject()
                .put("action", "analyze_complexity")
                .put("code", code)
                .put("kwargs", JSONObject().put("threshold", 7))
            val out = PyCall.callJson(
                context.applicationContext, "zcode_plugins", "run_spike_json",
                payload.toString()
            ) ?: return null
            val root = JSONObject(out)
            if (!root.optBoolean("ok", false)) return null
            val analysis = root.optJSONObject("analysis") ?: return null
            val blocksJson = analysis.optJSONArray("blocks") ?: JSONArray()
            val blocks = mutableListOf<Block>()
            for (i in 0 until blocksJson.length()) {
                val b = blocksJson.optJSONObject(i) ?: continue
                blocks.add(
                    Block(
                        name = b.optString("name"),
                        line = b.optInt("line", 1),
                        complexity = b.optInt("complexity", 1),
                        isHigh = b.optBoolean("is_high_complexity", false)
                    )
                )
            }
            ComplexityReport(
                available = true,
                maxComplexity = analysis.optInt("cyclomatic_complexity", 0),
                maintainabilityIndex = analysis.optDouble("maintainability_index", 0.0),
                functionCount = analysis.optInt("function_count", 0),
                classCount = analysis.optInt("class_count", 0),
                blocks = blocks
            )
        } catch (e: Exception) {
            Breadcrumb.log("SPKE_CX_FAIL", e.message ?: "")
            null
        }
    }
}
