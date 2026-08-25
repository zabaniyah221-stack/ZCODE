package com.zaba.zcode.core.update

import android.content.Context
import com.zaba.zcode.core.diagnostics.Breadcrumb
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * UpdateChecker — v1.0.22 one-tap update, langkah "CEK" (RFC D2/D3/D6).
 *
 * Sumber kebenaran: `GET /repos/muzape28-blip/ZCODE/releases/latest`
 * (unauthenticated — repo publik; TIDAK ADA credential/PAT di sini, D10).
 * Semantik endpoint (docs.github.com/releases#the-latest-release): hanya
 * non-prerelease & non-draft, diurutkan by created_at (tanggal COMMIT).
 * Konsekuensi yang kita andalkan: draft/prerelease tak pernah bocor; bila
 * rilis dibuat dari commit lama, `latest` bisa mengembalikan versi lama —
 * D3 (compare numerik, tampilkan hanya bila remote > lokal) membuat kasus
 * itu aman: worst case "up to date", never downgrade.
 *
 * Cache 24 jam: auto-check saat app start BUKAN boleh menyentuh jaringan
 * bila cache masih segar; tap user selalu fresh check (dan menyegarkan
 * cache). Persistensi = file JSON atomik, konvensi app (pola
 * TelemetryStore/RunLogStore — stack tidak memakai DataStore aktif).
 */
object UpdateChecker {

    private const val REPO = "muzape28-blip/ZCODE"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val USER_AGENT = "zcode-update-checker/1.0"
    private const val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24)
    private const val CACHE_FILE = ".zcode_update_cache.json"
    private const val ACCEPT_GITHUB = "application/vnd.github+json"

    private val lock = Any()
    private var cacheFile: java.io.File? = null

    /** Tag harus persis `vMAJOR.MINOR.PATCH` — tak ada suffix lain (D2). */
    private val TAG_PATTERN = Regex("""^v(\d+)\.(\d+)\.(\d+)$""")
    private val APK_ASSET_PATTERN = Regex("""^ZCODE-v\d+\.\d+\.\d+\.apk$""")

    sealed interface CheckOutcome {
        data class UpToDate(val localVersion: String) : CheckOutcome
        data class Newer(
            val tag: String,
            val version: String,
            val sha256: String,
            val sizeBytes: Long,
            val downloadUrl: String
        ) : CheckOutcome
        data class Failed(val reasonCode: String, val message: String) : CheckOutcome {
            companion object {
                const val NETWORK = "NETWORK"
                const val RATE_LIMITED = "RATE_LIMITED"
                const val HTTP_ERROR = "HTTP_ERROR"
                const val PARSE = "PARSE"
                const val NO_SHA256_DIGEST = "NO_SHA256_DIGEST"
            }
        }
    }

    /** Parse tag `v1.0.22` → triple; null bila tidak cocok pola (guard D2). */
    fun parseSemVer(tag: String): Triple<Int, Int, Int>? {
        val m = TAG_PATTERN.matchEntire(tag.trim()) ?: return null
        return Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }

    /**
     * Compare versi NUMERIK per-segmen (D3 — bukan string: `1.0.10 > 1.0.9`
     * harus benar; kelas bug string-compare sudah dimakamkan guard v1.0.21).
     * Tag yang tidak cocok pola → null (caller menolak).
     */
    fun compareVersions(a: String, b: String): Int? {
        val pa = parseSemVer(a) ?: return null
        val pb = parseSemVer(b) ?: return null
        return compareTriple(pa, pb)
    }

    private fun compareTriple(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int {
        if (a.first != b.first) return a.first - b.first
        if (a.second != b.second) return a.second - b.second
        return a.third - b.third
    }

    /**
     * Parse respons JSON `releases/latest`. Membalikan null bila: bukan JSON,
     * tag tak valid, tidak ada aset APK sesuai pola, atau aset tanpa
     * `digest` `sha256:` (D1 — tanpa checksum publikasi, integritas tidak
     * bisa diverifikasi; tolak, jangan tebak).
     */
    fun parseLatestResponse(json: String): CheckOutcome.Newer? {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return null
        }
        val tag = root.optString("tag_name", "").trim()
        val semver = parseSemVer(tag) ?: return null
        val version = tag.removePrefix("v")
        val assets = root.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            if (!APK_ASSET_PATTERN.matches(name)) continue
            val rawDigest = asset.optString("digest", "")
            if (!rawDigest.startsWith("sha256:")) return null
            val size = asset.optLong("size", -1L)
            val url = asset.optString("browser_download_url", "")
            if (size <= 0 || url.isEmpty()) return null
            return CheckOutcome.Newer(
                tag = tag,
                version = version,
                sha256 = rawDigest.removePrefix("sha256:").lowercase(),
                sizeBytes = size,
                downloadUrl = url
            )
        }
        return null
    }

    fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        Breadcrumb.log("UPDATE_CHECK_FAIL", "currentVersion: ${e.message}")
        ""
    }

    private fun cachePath(context: Context): java.io.File =
        synchronized(lock) {
            val f = cacheFile ?: java.io.File(context.filesDir, CACHE_FILE).also { cacheFile = it }
            f
        }

    /** Baca cache; null bila tak ada/korup/lebih tua dari TTL. */
    private fun readCache(): CheckOutcome? {
        val f = synchronized(lock) { cacheFile } ?: return null
        if (!f.exists()) return null
        return try {
            val now = System.currentTimeMillis()
            val root = JSONObject(f.readText())
            val ts = root.optLong("ts", 0L)
            if (now - ts > CACHE_TTL_MS) return null
            val kind = root.optString("kind", "")
            when (kind) {
                "up_to_date" -> CheckOutcome.UpToDate(root.optString("local", ""))
                "newer" -> CheckOutcome.Newer(
                    tag = root.optString("tag", ""),
                    version = root.optString("version", ""),
                    sha256 = root.optString("sha256", ""),
                    sizeBytes = root.optLong("size", -1L),
                    downloadUrl = root.optString("url", "")
                )
                else -> null
            }
        } catch (e: Exception) {
            null // korup → abaikan, jalankan fresh check (konvensi TelemetryStore)
        }
    }

    private fun writeCache(context: Context, outcome: CheckOutcome) {
        val f = cachePath(context)
        val root = JSONObject().put("ts", System.currentTimeMillis())
        when (outcome) {
            is CheckOutcome.UpToDate -> root.put("kind", "up_to_date").put("local", outcome.localVersion)
            is CheckOutcome.Newer ->
                root.put("kind", "newer")
                    .put("tag", outcome.tag)
                    .put("version", outcome.version)
                    .put("sha256", outcome.sha256)
                    .put("size", outcome.sizeBytes)
                    .put("url", outcome.downloadUrl)
            is CheckOutcome.Failed -> return // kegagalan TIDAK di-cache
        }
        try {
            // atomik: temp + rename (pola TransactionManager/TelemetryStore)
            val tmp = java.io.File(f.parentFile, f.name + ".tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(f)) {
                tmp.copyTo(f, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            Breadcrumb.log("UPDATE_CHECK_FAIL", "cache write: ${e.message}")
        }
    }

    /**
     * Cek update. [fresh] = tap user (selalu jaringan + segarkan cache);
     * false = auto-check app start (pakai cache bila masih <24 jam, D2).
     * Blocking — panggil dari `Dispatchers.IO`.
     */
    fun checkLatest(context: Context, fresh: Boolean): CheckOutcome {
        Breadcrumb.log("UPDATE_CHECK_BEGIN", if (fresh) "user" else "auto")
        val local = currentVersion(context)
        if (local.isEmpty() || parseSemVer("v$local") == null) {
            val msg = "versi lokal tidak terbaca"
            Breadcrumb.log("UPDATE_CHECK_FAIL", msg)
            return CheckOutcome.Failed(CheckOutcome.Failed.PARSE, msg)
        }
        if (!fresh) {
            val cached = readCache()
            if (cached != null) {
                Breadcrumb.log("UPDATE_CHECK_OK", "cache local=$local")
                return reconcile(cached, local)
            }
        }
        val outcome = fetchAndCompare(context)
        when (outcome) {
            is CheckOutcome.Newer -> Breadcrumb.log("UPDATE_CHECK_NEWER", "${outcome.tag} local=$local")
            is CheckOutcome.UpToDate -> Breadcrumb.log("UPDATE_CHECK_OK", "local=$local")
            is CheckOutcome.Failed -> Breadcrumb.log("UPDATE_CHECK_FAIL", outcome.reasonCode)
        }
        return outcome
    }

    /** Cache menyimpan "fakta remote"; perbandingan vs versi lokal dilakukan ulang di sini. */
    private fun reconcile(cached: CheckOutcome, local: String): CheckOutcome = when (cached) {
        is CheckOutcome.Newer ->
            if (compareVersions(cached.version, local) == 1) cached
            else CheckOutcome.UpToDate(local)
        is CheckOutcome.UpToDate -> CheckOutcome.UpToDate(local)
        is CheckOutcome.Failed -> cached
    }

    private fun fetchAndCompare(context: Context): CheckOutcome {
        val local = currentVersion(context)
        val conn: HttpURLConnection = try {
            (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", ACCEPT_GITHUB)
            }
        } catch (e: Exception) {
            val msg = "koneksi: ${e.message}"
            Breadcrumb.log("UPDATE_CHECK_FAIL", "NETWORK $msg")
            return CheckOutcome.Failed(CheckOutcome.Failed.NETWORK, "Network error")
        }
        try {
            val code = conn.responseCode
            if (code == 403 || code == 429) {
                return CheckOutcome.Failed(CheckOutcome.Failed.RATE_LIMITED, "Rate limited")
            }
            if (code !in 200..299) {
                return CheckOutcome.Failed(CheckOutcome.Failed.HTTP_ERROR, "HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().readText()
            val newer = parseLatestResponse(body) ?: run {
                val reason = if (body.contains("\"digest\"") && !body.contains("sha256:")) {
                    CheckOutcome.Failed.NO_SHA256_DIGEST
                } else {
                    CheckOutcome.Failed.PARSE
                }
                Breadcrumb.log("UPDATE_CHECK_FAIL", "parse $reason")
                return CheckOutcome.Failed(reason, "Invalid release data")
            }
            writeCache(context, newer)
            return if (compareVersions(newer.version, local) == 1) {
                newer
            } else {
                CheckOutcome.UpToDate(local)
            }
        } catch (e: Exception) {
            val msg = "jaringan: ${e.message}"
            Breadcrumb.log("UPDATE_CHECK_FAIL", "NETWORK $msg")
            return CheckOutcome.Failed(CheckOutcome.Failed.NETWORK, "Network error")
        } finally {
            conn.disconnect()
        }
    }
}
