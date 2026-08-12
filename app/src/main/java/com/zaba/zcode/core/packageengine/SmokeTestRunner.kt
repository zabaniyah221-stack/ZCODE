package com.zaba.zcode.core.packageengine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * SmokeTestRunner — post-install import/smoke test (SPEC-001 §9).
 *
 * Menjalankan smoke test TERHADAP STAGING (belum aktivasi) via
 * package_runtime.smoke.run_smoke_json — sys.path disuntik sementara,
 * lalu dipulihkan. Jenis: IMPORT, NATIVE_LOAD, BASIC_API, FILE_OUTPUT,
 * OFFLINE_RESTART. Deterministic & time-bounded (timeout per test).
 */
class SmokeTestRunner(private val context: Context) {

    data class SmokeOutcome(
        val ok: Boolean,
        val results: List<JSONObject>,
        val nativeLibs: List<String>,
        val nativeNote: String
    )

    /**
     * @param siblingDirs direktori staging paket LAIN dalam transaksi yang sama.
     *
     * FIX 2026-08-13: tanpa ini smoke test hanya melihat satu paket, sehingga
     * setiap paket berdependensi (52% dari sampel katalog — flask, pandas,
     * requests, httpx, rich, …) pasti gagal dengan ModuleNotFoundError dan
     * memicu rollback seluruh transaksi.
     */
    fun run(
        importName: String,
        stagingDir: String,
        tests: List<JSONObject>?,
        siblingDirs: List<String> = emptyList()
    ): SmokeOutcome {
        val testsJson = tests?.let { arr ->
            val out = JSONArray()
            arr.forEach { out.put(it) }
            out.toString()
        } ?: "[]"
        val siblingsJson = JSONArray().apply { siblingDirs.forEach { put(it) } }.toString()
        val json = PyCall.callJson(
            context,
            "package_runtime.smoke",
            "run_smoke_json",
            importName,
            stagingDir,
            testsJson,
            30,
            siblingsJson
        ) ?: return SmokeOutcome(false, listOf(
            JSONObject().put("test", "setup").put("type", "SETUP").put("ok", false)
                .put("error", "Smoke test runner tidak tersedia (butuh Chaquopy).")
        ), emptyList(), "")

        return try {
            val o = JSONObject(json)
            val results = mutableListOf<JSONObject>()
            o.optJSONArray("results")?.let { arr ->
                for (i in 0 until arr.length()) results.add(arr.getJSONObject(i))
            }
            val libs = mutableListOf<String>()
            o.optJSONObject("native_info")?.optJSONArray("native_libs")?.let { arr ->
                for (i in 0 until arr.length()) libs.add(arr.optString(i))
            }
            SmokeOutcome(
                ok = o.optBoolean("ok", false),
                results = results,
                nativeLibs = libs,
                nativeNote = o.optJSONObject("native_info")?.optString("note", "") ?: ""
            )
        } catch (e: Exception) {
            SmokeOutcome(false, listOf(
                JSONObject().put("test", "parse").put("type", "SETUP").put("ok", false)
                    .put("error", "Hasil smoke test tidak valid: ${e.message}")
            ), emptyList(), "")
        }
    }
}
