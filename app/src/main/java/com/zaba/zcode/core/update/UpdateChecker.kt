package com.zaba.zcode.core.update

import android.content.Context
import com.zaba.zcode.core.diagnostics.Breadcrumb
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
    private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L // 24 jam
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

    /** Satu aset release dari respons API — data murni untuk [selectApkAsset]. */
    data class AssetRef(
        val name: String,
        val sizeBytes: Long,
        val digest: String,
        val downloadUrl: String
    )

    /**
     * Pilih aset APK + validasi integritas (D1/D2) — FUNGSI MURNI (tanpa
     * org.json/Android) supaya teruji di JVM: tag harus valid, nama aset
     * harus cocok pola `ZCODE-vX.Y.Z.apk`, digest WAJIB ber-skema `sha256:`
     * (tanpa checksum publikasi integritas tak bisa diverifikasi — tolak,
     * jangan tebak), size/url harus valid.
     */
    fun selectApkAsset(tag: String, assets: List<AssetRef>): CheckOutcome.Newer? {
        if (parseSemVer(tag) == null) return null
        val version = tag.removePrefix("v")
        for (asset in assets) {
            if (!APK_ASSET_PATTERN.matches(asset.name)) continue
            val rawDigest = asset.digest
            if (!rawDigest.startsWith("sha256:")) return null
            if (asset.sizeBytes <= 0 || asset.downloadUrl.isEmpty()) return null
            return CheckOutcome.Newer(
                tag = tag,
                version = version,
                sha256 = rawDigest.removePrefix("sha256:").lowercase(),
                sizeBytes = asset.sizeBytes,
                downloadUrl = asset.downloadUrl
            )
        }
        return null
    }

    /**
     * Parse respons JSON `releases/latest` — adapter tipis org.json: ekstrak
     * tag + daftar aset lalu delegasikan keputusan ke [selectApkAsset]
     * (logika integritas teruji di JVM, adapter ini diverifikasi device UAT).
     * Membalikan null bila: bukan JSON, tidak ada kunci `assets`, atau
     * [selectApkAsset] menolak.
     */
    fun parseLatestResponse(json: String): CheckOutcome.Newer? {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return null
        }
        val tag = root.optString("tag_name", "").trim()
        val assetsArr = root.optJSONArray("assets") ?: return null
        val assets = mutableListOf<AssetRef>()
        for (i in 0 until assetsArr.length()) {
            val asset = assetsArr.optJSONObject(i) ?: continue
            assets.add(
                AssetRef(
                    name = asset.optString("name", ""),
                    sizeBytes = asset.optLong("size", -1L),
                    digest = asset.optString("digest", ""),
                    downloadUrl = asset.optString("browser_download_url", "")
                )
            )
        }
        return selectApkAsset(tag, assets)
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

    /** Baca cache; null bila tak ada/korup/lebih tua dari TTL.
     *
     * v1.0.23: path WAJIB di-resolve via cachePath(context). Membaca field
     * statis `cacheFile` saja membuat process baru selalu melihat null dan
     * cache tidak pernah terbaca lintas app restart — bug ditemukan lewat
     * telemetri device (0 cache-hit dari 15 auto-check;
     * docs/UAT_UPDATER_CHECKPATH_2026_09_08.md §2). */
    private fun readCache(context: Context): CheckOutcome? {
        val f = cachePath(context)
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
            val cached = readCache(context)
            if (cached != null) {
                Breadcrumb.log("UPDATE_CHECK_OK", "cache local=$local")
                return reconcile(cached, local)
            }
        }
        val outcome = fetchAndCompare(context)
        when (outcome) {
            is CheckOutcome.Newer -> Breadcrumb.log("UPDATE_CHECK_NEWER", "${outcome.tag} local=$local")
            is CheckOutcome.UpToDate -> Breadcrumb.log("UPDATE_CHECK_OK", "local=$local")
            // v1.0.23: Failed tidak di-log di sini - satu owner telemetri
            // kegagalan ada di fetchAndCompare/currentVersion (layer yang
            // memegang detail). Dulu dua layer log -> entri dobel
            // "NETWORK jaringan:..." + "NETWORK" (SKILL 20 #1). Branch tetap
            // wajib: when atas sealed harus exhaustive.
            is CheckOutcome.Failed -> Unit
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
                Breadcrumb.log("UPDATE_CHECK_FAIL", "rate: $code")
                return CheckOutcome.Failed(CheckOutcome.Failed.RATE_LIMITED, "Rate limited")
            }
            if (code !in 200..299) {
                Breadcrumb.log("UPDATE_CHECK_FAIL", "http: $code")
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
