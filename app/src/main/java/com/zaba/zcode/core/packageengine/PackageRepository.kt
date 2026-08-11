package com.zaba.zcode.core.packageengine

import android.content.Context
import com.zaba.zcode.core.files.Paths
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * PackageRepository — sumber data katalog (SPEC-001 §10, §19).
 *
 * - assets/package_catalog/packages.json   : katalog curated (100 MVP → 300 V1)
 * - assets/package_catalog/stdlib.json     : index stdlib terpisah (~305 nama)
 * - assets/package_catalog/smoke-tests.json : manifest smoke test
 * - python-env/state/installed.json        : status terpasang (baca langsung)
 *
 * "Incompatible packages tetap searchable" (SPEC user story) — repository tidak
 * menyaring INCOMPATIBLE/UNAVAILABLE; UI-lah yang memutuskan action.
 */
class PackageRepository(private val context: Context) {

    private var catalog: List<PackageDetails>? = null
    private var stdlib: List<String>? = null

    fun loadCatalog(): List<PackageDetails> {
        catalog?.let { return it }
        val list = mutableListOf<PackageDetails>()
        try {
            val text = context.assets.open("package_catalog/packages.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                list.add(PackageDetails.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            // katalog tidak ada/corrupt → kosong; UI harus tetap hidup
        }
        catalog = list
        return list
    }

    fun loadStdlib(): List<String> {
        stdlib?.let { return it }
        val list = mutableListOf<String>()
        try {
            val text = context.assets.open("package_catalog/stdlib.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) list.add(arr.getString(i))
        } catch (e: Exception) {
            // kosong
        }
        stdlib = list
        return list
    }

    fun loadSmokeTests(): Map<String, List<JSONObject>> {
        val map = mutableMapOf<String, List<JSONObject>>()
        try {
            val text = context.assets.open("package_catalog/smoke-tests.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(text)
            root.keys().forEach { key ->
                val arr = root.getJSONArray(key)
                map[key] = (0 until arr.length()).map { arr.getJSONObject(it) }
            }
        } catch (e: Exception) {
            // kosong
        }
        return map
    }

    fun search(query: String): List<PackageDetails> {
        val q = query.trim()
        if (q.isEmpty()) return loadCatalog()
        return loadCatalog().filter {
            it.name.contains(q, ignoreCase = true) ||
                it.displayName.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true)
        }
    }

    fun findByCanonicalName(name: String): PackageDetails? {
        val canon = name.lowercase().replace("_", "-")
        return loadCatalog().firstOrNull {
            it.name.lowercase().replace("_", "-") == canon
        }
    }

    fun categories(): List<String> = loadCatalog().map { it.category }.distinct().sorted()

    /** Status terpasang dari state/installed.json (tanpa dependency ke SQLite). */
    fun installedSnapshot(): Map<String, InstalledInfo> {
        val map = mutableMapOf<String, InstalledInfo>()
        try {
            val f = File(Paths.pythonState(context), "installed.json")
            if (!f.exists()) return map
            val root = JSONObject(f.readText())
            root.keys().forEach { key ->
                val o = root.getJSONObject(key)
                map[key] = InstalledInfo(
                    version = o.optString("version"),
                    path = o.optString("path"),
                    installedAt = o.optLong("installed_at", 0L),
                    source = o.optString("source", ""),
                    sha256 = if (o.isNull("sha256")) null else o.optString("sha256")
                )
            }
        } catch (e: Exception) {
            // korup → kosong
        }
        return map
    }

    data class InstalledInfo(
        val version: String,
        val path: String,
        val installedAt: Long,
        val source: String,
        val sha256: String?
    )
}
