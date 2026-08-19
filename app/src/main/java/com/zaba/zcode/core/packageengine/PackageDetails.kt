package com.zaba.zcode.core.packageengine

import org.json.JSONArray
import org.json.JSONObject

/**
 * PackageDetails — metadata package (SPEC-001 §19 Library Metadata Schema).
 * Field wajib tampil di Package Details sesuai urutan SPEC §11.
 */
data class PackageDetails(
    val name: String,
    val displayName: String,
    val importName: String,
    val category: String,
    val type: String,               // "pure" | "native"
    val status: PackageStatus,
    val testedVersion: String?,
    val python: List<String>,
    val abis: List<String>,
    val description: String,
    val useCases: List<String>,
    val works: List<String>,
    val doesNotWork: List<String>,
    val dependencies: List<String>,
    val risks: List<String>,
    val smokeTest: String?,
    val license: String,
    val publisher: String,
    val source: String,
    val sha256: String?,
    // ---- Kurasi kartu Detail v1.0.18 (LIBRARY_KURASI_KONTEN_2026_08_15) ----
    // Semua opsional: entri lama tanpa field ini tetap valid (fallback UI).
    val longDescription: String = "",   // WHAT IS IT — prosa dari sumber resmi
    val whyUse: String = "",            // WHY USE IT
    val example: String = "",           // HOW TO USE — snippet pendek yang jalan di ZCODE
    /** ID SampleEntry untuk tombol "Coba contoh lengkap"; null = belum ada. */
    val sampleId: String? = null,
    val whoMadeIt: String = "",          // WHO MADE IT — asal + lisensi (prosa)
    val sources: List<SourceRef> = emptyList(), // rujukan tap-able per seksi
    val curatedAt: String = ""          // tanggal kurasi (kejujuran konten beku)
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("name", name)
        o.put("displayName", displayName)
        o.put("importName", importName)
        o.put("category", category)
        o.put("type", type)
        o.put("status", status.name)
        o.put("testedVersion", testedVersion ?: JSONObject.NULL)
        o.put("python", JSONArray(python))
        o.put("abis", JSONArray(abis))
        o.put("description", description)
        o.put("useCases", JSONArray(useCases))
        o.put("works", JSONArray(works))
        o.put("doesNotWork", JSONArray(doesNotWork))
        o.put("dependencies", JSONArray(dependencies))
        o.put("risks", JSONArray(risks))
        o.put("smokeTest", smokeTest ?: JSONObject.NULL)
        o.put("license", license)
        o.put("publisher", publisher)
        o.put("source", source)
        o.put("sha256", sha256 ?: JSONObject.NULL)
        if (longDescription.isNotBlank()) o.put("longDescription", longDescription)
        if (whyUse.isNotBlank()) o.put("whyUse", whyUse)
        if (example.isNotBlank()) o.put("example", example)
        if (!sampleId.isNullOrBlank()) o.put("sampleId", sampleId)
        if (whoMadeIt.isNotBlank()) o.put("whoMadeIt", whoMadeIt)
        if (sources.isNotEmpty()) o.put("sources", JSONArray(sources.map { it.toJson() }))
        if (curatedAt.isNotBlank()) o.put("curatedAt", curatedAt)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): PackageDetails {
            fun list(key: String): List<String> {
                val arr = o.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).map { arr.optString(it) }
            }
            return PackageDetails(
                name = o.optString("name", ""),
                displayName = o.optString("displayName", o.optString("name", "")),
                importName = o.optString("importName", o.optString("name", "")),
                category = o.optString("category", "Other"),
                type = o.optString("type", "pure"),
                status = PackageStatus.fromString(o.optString("status")) ?: PackageStatus.NOT_REVIEWED,
                testedVersion = if (o.isNull("testedVersion")) null else o.optString("testedVersion"),
                python = list("python"),
                abis = list("abis"),
                description = o.optString("description", ""),
                useCases = list("useCases"),
                works = list("works"),
                doesNotWork = list("doesNotWork"),
                dependencies = list("dependencies"),
                risks = list("risks"),
                smokeTest = if (o.isNull("smokeTest")) null else o.optString("smokeTest"),
                license = o.optString("license", ""),
                publisher = o.optString("publisher", ""),
                source = o.optString("source", ""),
                sha256 = if (o.isNull("sha256")) null else o.optString("sha256"),
                longDescription = o.optString("longDescription", ""),
                whyUse = o.optString("whyUse", ""),
                example = o.optString("example", ""),
                sampleId = if (o.isNull("sampleId")) null
                    else o.optString("sampleId", "").takeIf { it.isNotBlank() },
                whoMadeIt = o.optString("whoMadeIt", ""),
                sources = run {
                    val arr = o.optJSONArray("sources") ?: return@run emptyList()
                    (0 until arr.length()).mapNotNull { i ->
                        arr.optJSONObject(i)?.let { SourceRef.fromJson(it) }
                    }
                },
                curatedAt = o.optString("curatedAt", "")
            )
        }
    }
}

/**
 * SourceRef — rujukan sumber kurasi yang TAP-ABLE di kartu Detail.
 * `untuk`: seksi yang didukung ("what"/"why"/"how"/"where"/"who"/"learn-id"),
 * `label`: teks pendek yang ditampilkan (domain tanpa https),
 * `url`  : tujuan penuh saat di-tap (Intent.ACTION_VIEW).
 */
data class SourceRef(val untuk: String, val label: String, val url: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("untuk", untuk).put("label", label).put("url", url)

    companion object {
        fun fromJson(o: JSONObject): SourceRef? {
            val url = o.optString("url", "")
            if (url.isBlank()) return null
            return SourceRef(
                untuk = o.optString("untuk", ""),
                label = o.optString("label", url.removePrefix("https://").removePrefix("http://").trimEnd('/')),
                url = url
            )
        }
    }
}
