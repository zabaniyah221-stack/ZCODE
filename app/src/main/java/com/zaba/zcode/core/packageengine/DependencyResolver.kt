package com.zaba.zcode.core.packageengine

import android.content.Context
import com.zaba.zcode.core.files.Paths
import org.json.JSONArray
import org.json.JSONObject

/**
 * DependencyResolver — resolusi dependensi + wheel selection (SPEC-001 §5, §12).
 *
 * Logika inti di Python (package_runtime.resolve) memakai packaging:
 * - sumber: local wheel cache → PyPI JSON API → Chaquopy Android wheel index
 * - filter: version constraint, Requires-Python, wheel tag (Python/ABI/platform)
 * - tolak sdist; bila tak ada wheel kompatibel → UNAVAILABLE + alasan
 * - deteksi konflik versi (package sama, versi beda)
 * - marker environment (extras, python_version) dievaluasi
 *
 * Result: plan (daftar package + wheel terpilih), conflicts, unavailable.
 */
class DependencyResolver(private val context: Context) {

    data class ResolvedPackage(
        val canonicalName: String,
        val version: String,
        val source: String,          // local | pypi | chaquopy
        val filename: String,
        val url: String?,
        val localPath: String?,
        val sha256: String?,
        val size: Long?,
        val priority: Int,
        val compatReason: String,
        val requiresDist: List<String>,
        val summary: String,
        val requiresPython: String?
    )

    data class ResolvePlan(
        val ok: Boolean,
        val packages: List<ResolvedPackage>,
        val conflicts: List<Conflict>,
        val unavailable: List<Unavailable>,
        val errorCode: String?,
        val errorStage: String?,
        val humanError: String?,
        val technicalError: String?
    )

    data class Conflict(val name: String, val requiredBy: String?, val versionA: String, val versionB: String, val specifier: String)
    data class Unavailable(val name: String, val parent: String?, val reason: String)

    fun resolve(requirementText: String): ResolvePlan {
        val wheelsDir = Paths.pythonWheels(context).absolutePath
        val tested = loadTestedManifestJson()
        val json = PyCall.callJson(
            context,
            "package_runtime.resolve",
            "resolve_json",
            requirementText,
            wheelsDir,
            null,
            tested
        ) ?: return ResolvePlan(false, emptyList(), emptyList(), emptyList(),
            "ENGINE_UNAVAILABLE", "engine", "Package engine tidak tersedia (butuh Chaquopy runtime).", null)

        return parsePlan(json)
    }

    private fun parsePlan(json: String): ResolvePlan {
        val o = try {
            JSONObject(json)
        } catch (e: Exception) {
            return ResolvePlan(false, emptyList(), emptyList(), emptyList(),
                "METADATA", "resolve", "Hasil resolusi tidak valid.", e.message)
        }
        if (o.optBoolean("ok", false) == false) {
            return ResolvePlan(
                ok = false,
                packages = emptyList(),
                conflicts = emptyList(),
                unavailable = emptyList(),
                errorCode = o.optString("code"),
                errorStage = o.optString("stage"),
                humanError = o.optString("human"),
                technicalError = o.optString("technical")
            )
        }
        val packages = mutableListOf<ResolvedPackage>()
        o.optJSONArray("packages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                packages.add(
                    ResolvedPackage(
                        canonicalName = p.optString("name", p.optString("canonical_name", "")),
                        version = p.optString("version", ""),
                        source = p.optString("source", "pypi"),
                        filename = p.optString("filename", ""),
                        url = p.optString("url"),
                        localPath = p.optString("local_path"),
                        sha256 = if (p.isNull("sha256")) null else p.optString("sha256"),
                        size = if (p.has("size") && !p.isNull("size")) p.optLong("size") else null,
                        priority = p.optInt("priority", 0),
                        compatReason = p.optString("compat_reason", ""),
                        requiresDist = strList(p, "requires_dist"),
                        summary = p.optString("summary", ""),
                        requiresPython = if (p.isNull("requires_python")) null else p.optString("requires_python")
                    )
                )
            }
        }
        val conflicts = mutableListOf<Conflict>()
        o.optJSONArray("conflicts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                conflicts.add(Conflict(
                    name = c.optString("name"),
                    requiredBy = if (c.isNull("required_by")) null else c.optString("required_by"),
                    versionA = c.optString("version_a"),
                    versionB = c.optString("version_b"),
                    specifier = c.optString("specifier")
                ))
            }
        }
        val unavailable = mutableListOf<Unavailable>()
        o.optJSONArray("unavailable")?.let { arr ->
            for (i in 0 until arr.length()) {
                val u = arr.getJSONObject(i)
                unavailable.add(Unavailable(
                    name = u.optString("name"),
                    parent = if (u.isNull("parent")) null else u.optString("parent"),
                    reason = u.optString("reason")
                ))
            }
        }
        return ResolvePlan(true, packages, conflicts, unavailable, null, null, null, null)
    }

    private fun strList(o: JSONObject, key: String): List<String> {
        val arr = o.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    private fun loadTestedManifestJson(): String? {
        return try {
            val text = context.assets.open("package_catalog/tested-manifest.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            text
        } catch (e: Exception) {
            null
        }
    }
}
