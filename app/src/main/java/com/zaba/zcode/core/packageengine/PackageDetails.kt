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
    val sha256: String?
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
                sha256 = if (o.isNull("sha256")) null else o.optString("sha256")
            )
        }
    }
}
