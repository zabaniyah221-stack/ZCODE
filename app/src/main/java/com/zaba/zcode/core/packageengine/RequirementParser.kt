package com.zaba.zcode.core.packageengine

import org.json.JSONArray
import org.json.JSONObject

/**
 * RequirementParser — parser requirement (SPEC-001 §12 Manual Install).
 *
 * Kanonik: `packaging.requirements.Requirement` di Python (package_runtime.requirement).
 * Kotlin melakukan pre-check ringan (anti shell/flag) lalu mendelegasikan parse
 * penuh ke Python agar konsisten dengan PEP 508 (==, >=, <, ~=, extras, marker).
 *
 * Didukung: `requests`, `requests==2.32.3`, `pydantic>=2,<3`, `numpy==1.26.*`,
 * `flask[async]`, baris requirements.txt.
 * Ditolak: perintah shell, flag pip (--trusted-host dll), URL/VCS install.
 */
object RequirementParser {

    data class Requirement(
        val name: String,
        val canonicalName: String,
        val extras: List<String>,
        val specifier: String,
        val marker: String?,
        val raw: String
    )

    /** Regex longgar untuk pre-check (parsing final di Python). */
    private val SAFE_REQ = Regex(
        "^[A-Za-z0-9._-]+(\\[[A-Za-z0-9,._-]*\\])?" +
            "(\\s*(===|==|!=|<=|>=|<|>|~=)\\s*[A-Za-z0-9.*+!\\-]+" +
            "(\\s*,\\s*(===|==|!=|<=|>=|<|>|~=)\\s*[A-Za-z0-9.*+!\\-]+)*)?" +
            "(\\s*;\\s*.+)?$"
    )

    private val FORBIDDEN = listOf(
        "rm ", "curl", "wget", "mkdir", "cd ", "sudo", "&&", "||",
        "`", "\$(", "--trusted-host", "--index-url", "--extra-index-url",
        "--target", "--upgrade", "--force"
    )

    fun preCheck(text: String): String? {
        val t = (text ?: "").trim()
        if (t.isEmpty()) return "Requirement kosong."
        if (t.length > 500) return "Requirement terlalu panjang (maks 500 karakter)."
        if (FORBIDDEN.any { t.contains(it) }) {
            return "Input mengandung pola yang dilarang. Manual Install hanya menerima " +
                "requirement Python (contoh: requests, requests==2.32.3, pydantic>=2,<3), " +
                "bukan perintah shell/opsi pip."
        }
        if (!SAFE_REQ.matches(t)) {
            return "Format requirement tidak dikenali: '$t'. " +
                "Contoh: requests, requests==2.32.3, numpy==1.26.*, flask[async]"
        }
        return null
    }

    /** Parse via Python (kanonik). Panggil dari thread background. */
    fun parse(context: android.content.Context, text: String): Requirement {
        preCheck(text)?.let { throw RequirementException(it) }
        val json = PyCall.callJson(
            context,
            "package_runtime.requirement",
            "parse_requirement_json",
            text
        ) ?: throw RequirementException("Requirement parser tidak tersedia (butuh Chaquopy).")
        try {
            val o = JSONObject(json)
            if (o.optBoolean("ok", false) == false) {
                throw RequirementException(o.optString("error", "Requirement tidak valid."))
            }
            return Requirement(
                name = o.getString("name"),
                canonicalName = o.getString("canonical_name"),
                extras = o.optJSONArray("extras")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                specifier = o.optString("specifier", ""),
                marker = if (o.isNull("marker")) null else o.optString("marker"),
                raw = o.optString("raw", text)
            )
        } catch (e: Exception) {
            if (e is RequirementException) throw e
            throw RequirementException("Hasil parse tidak valid: ${e.message}")
        }
    }

    class RequirementException(message: String) : Exception(message)
}
