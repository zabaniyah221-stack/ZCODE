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
        val nativeNote: String,
        /** Jejak NATIVE-LOADER: pustaka pendukung yang dimuat / gagal dimuat. */
        val preloadLog: List<String> = emptyList()
    )

    /**
     * Hasil pemindaian pustaka native yang belum terpenuhi.
     *
     * @param packages paket yang perlu diunduh supaya .so bisa dimuat
     * @param unknown  pustaka yang tidak dikenal peta — WAJIB dilaporkan ke
     *                 pemakai, bukan didiamkan (pelajaran v1.0.8-v1.0.10:
     *                 kegagalan diam-diam menghabiskan tiga siklus rilis)
     * @param sources  {paket: RESMI|PERANGKAT|DUGAAN} — dasar pengetahuan
     */
    data class MissingLibs(
        val packages: List<String>,
        val unknown: List<String>,
        val sources: Map<String, String>,
        val scanned: Int,
        val error: String
    )

    /**
     * Pindai .so di direktori yang diberikan, kembalikan paket yang kurang.
     *
     * Best-effort: bila runtime Python tidak tersedia atau pemindaian gagal,
     * mengembalikan hasil kosong. Instalasi harus tetap berjalan seperti
     * sebelumnya — pemindai ini menambah kemampuan, tidak boleh mengurangi.
     */
    fun scanMissingLibs(dirs: List<String>, api: Int): MissingLibs {
        val kosong = MissingLibs(emptyList(), emptyList(), emptyMap(), 0, "")
        if (dirs.isEmpty()) return kosong
        val dirsJson = JSONArray().apply { dirs.forEach { put(it) } }.toString()
        val json = try {
            PyCall.callJson(
                context,
                "package_runtime.smoke",
                "scan_missing_libs_json",
                dirsJson,
                api
            )
        } catch (e: Exception) {
            return kosong.copy(error = e.message ?: e.toString())
        } ?: return kosong
        return try {
            val o = JSONObject(json)
            fun arr(key: String): List<String> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
            }
            val src = mutableMapOf<String, String>()
            o.optJSONObject("sources")?.let { s ->
                s.keys().forEach { k -> src[k] = s.optString(k, "?") }
            }
            MissingLibs(
                packages = arr("packages"),
                unknown = arr("unknown"),
                sources = src,
                scanned = o.optInt("scanned", 0),
                error = o.optString("error", "")
            )
        } catch (e: Exception) {
            kosong.copy(error = "hasil pindai tidak valid: ${e.message}")
        }
    }

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
                nativeNote = o.optJSONObject("native_info")?.optString("note", "") ?: "",
                preloadLog = mutableListOf<String>().also { pl ->
                    o.optJSONObject("native_info")?.optJSONArray("preload_log")?.let { arr ->
                        for (i in 0 until arr.length()) pl.add(arr.optString(i))
                    }
                }
            )
        } catch (e: Exception) {
            SmokeOutcome(false, listOf(
                JSONObject().put("test", "parse").put("type", "SETUP").put("ok", false)
                    .put("error", "Hasil smoke test tidak valid: ${e.message}")
            ), emptyList(), "")
        }
    }
}
