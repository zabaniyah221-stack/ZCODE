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
        val requiresPython: String?,
        /**
         * True untuk pustaka pendukung `chaquopy-*` (OpenBLAS, libjpeg, ...).
         * Paket ini hanya membungkus satu file .so — tidak ada modul Python
         * yang bisa diimpor, jadi uji impor terhadapnya WAJIB dilewati.
         */
        val supportLibrary: Boolean = false
    )

    data class ResolvePlan(
        val ok: Boolean,
        val packages: List<ResolvedPackage>,
        val conflicts: List<Conflict>,
        val unavailable: List<Unavailable>,
        val errorCode: String?,
        val errorStage: String?,
        val humanError: String?,
        val technicalError: String?,
        /** BUG C: modul stdlib — bukan error, tidak perlu dipasang. */
        val stdlib: List<StdlibHit> = emptyList(),
        /**
         * Jejak keputusan resolver yang tidak terlihat dari daftar paket akhir
         * (mis. pustaka pendukung yang gagal diambil). User mendiagnosis dari
         * HP tanpa logcat, jadi ini harus sampai ke layar.
         */
        val notes: List<String> = emptyList()
    )

    data class Conflict(val name: String, val requiredBy: String?, val versionA: String, val versionB: String, val specifier: String)
    data class Unavailable(val name: String, val parent: String?, val reason: String)
    data class StdlibHit(val name: String, val reason: String)

    /**
     * BUG B — FIX 2026-08-13. `org.json.JSONObject.optString()` TIDAK PERNAH
     * mengembalikan null: field yang tidak ada menghasilkan string kosong "".
     *
     * Akibatnya `localPath = p.optString("local_path")` bernilai "" untuk SETIAP
     * paket PyPI, lalu `if (p.localPath != null)` di PackageEngineV2 selalu
     * bernilai true, sehingga `File("").copyTo(...)` dijalankan dan melempar
     * "The source file doesn't exist." — download tidak pernah berjalan sama
     * sekali. Itulah pesan yang dilihat user saat memasang colorama.
     *
     * Helper ini mengembalikan null untuk field yang hilang, JSON null, maupun
     * string kosong/spasi — ketiganya sama-sama berarti "tidak ada nilai".
     */
    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

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
            tested,
            // BUILD #3: ABI + API perangkat dikirim eksplisit supaya Python
            // membangun tag `android_<api>_<abi>` sendiri. Tanpa ini
            // packaging.tags.sys_tags() menghasilkan `linux_armv7l` yang tidak
            // pernah cocok dengan wheel Chaquopy — sebab numpy/pandas/pillow/
            // matplotlib selalu ditolak walau wheel-nya ADA.
            Paths.currentAbi().replace('_', '-'),
            android.os.Build.VERSION.SDK_INT
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
                        // BUG B: optString() -> "" bukan null; pakai helper.
                        url = p.optStringOrNull("url"),
                        localPath = p.optStringOrNull("local_path"),
                        sha256 = p.optStringOrNull("sha256"),
                        size = if (p.has("size") && !p.isNull("size")) p.optLong("size") else null,
                        priority = p.optInt("priority", 0),
                        compatReason = p.optString("compat_reason", ""),
                        requiresDist = strList(p, "requires_dist"),
                        summary = p.optString("summary", ""),
                        requiresPython = p.optStringOrNull("requires_python"),
                        supportLibrary = p.optBoolean("support_library", false)
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
        // BUG C: modul stdlib dilaporkan terpisah — bukan kegagalan.
        val stdlib = mutableListOf<StdlibHit>()
        o.optJSONArray("stdlib")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                stdlib.add(StdlibHit(
                    name = s.optString("name"),
                    reason = s.optString("reason")
                ))
            }
        }
        val notes = strList(obj, "notes")
        return ResolvePlan(true, packages, conflicts, unavailable, null, null, null, null, stdlib, notes)
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
