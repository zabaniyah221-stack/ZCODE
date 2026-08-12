package com.zaba.zcode.core.packageengine

import android.content.Context
import com.zaba.zcode.core.execution.ExecutionEngine
import com.zaba.zcode.core.diagnostics.Breadcrumb
import com.zaba.zcode.core.files.Paths
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * PackageEngineV2 — Package Runtime Platform (SPEC-001).
 *
 * Satu-satunya pintu install: Library UI dan Manual UI KEDUANYA lewat sini
 * (Rule 7 — jangan duplikasi backend package).
 *
 * Alur install (Rule 1 — no fake success):
 *   parse → resolve → storage guard → download → SHA-256 verify → extract
 *   (path-safe) → metadata validate → smoke test (staging) → atomic activate.
 * Setiap kegagalan: transaction abort / rollback, environment existing utuh,
 * error diklasifikasi per stage (bukan satu kategori INSTALL_FAILED).
 *
 * Bukan god class: delegasi ke RequirementParser, DependencyResolver, Verifier,
 * TransactionManager, SmokeTestRunner, WheelSelector, TelemetryStore, PackageDb.
 */
class PackageEngineV2(private val context: Context) {

    sealed interface Step {
        data class Begin(val label: String) : Step
        data class Log(val text: String) : Step
        data class Finish(val label: String, val ok: Boolean, val detail: String = "") : Step
    }

    data class InstallResult(
        val ok: Boolean,
        val code: String?,
        val stage: String?,
        val humanMessage: String?,
        val technicalMessage: String?,
        val rollbackPerformed: Boolean,
        val installed: List<String>
    )

    private val resolver = DependencyResolver(context)
    private val txManager = TransactionManager(context)
    private val smokeRunner = SmokeTestRunner(context)
    private val repository = PackageRepository(context)
    private val db = PackageDb(context)

    companion object {
        private const val USER_AGENT = "zcode-package-runtime/1.0"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        // Storage margin (keputusan forum): max(1.5 × estimasi, 100 MB)
        private const val MIN_SAFETY_MARGIN_BYTES = 100L * 1024 * 1024
        private const val ESTIMATE_FACTOR = 1.5
    }

    // ------------------------------------------------------------------
    // INSTALL
    // ------------------------------------------------------------------

    fun install(
        requirementText: String,
        preResolved: DependencyResolver.ResolvePlan? = null,
        onStep: (Step) -> Unit
    ): InstallResult {
        TelemetryStore.increment("install_attempts")
        var tx: TransactionManager.Transaction? = null
        var rollbackPerformed = false
        var pkgName = requirementText.trim()

        fun fail(
            code: String,
            stage: String,
            human: String,
            technical: String?,
            alreadyRolledBack: Boolean = false
        ): InstallResult {
            TelemetryStore.recordFailure(code, stage, pkgName, human)
            if (tx != null && !alreadyRolledBack) {
                txManager.abort(tx!!, code, human)
                rollbackPerformed = true
            } else if (alreadyRolledBack) {
                rollbackPerformed = true
            }
            if (rollbackPerformed) TelemetryStore.increment("rollback_count")
            onStep(Step.Finish(stage, false, human))
            return InstallResult(false, code, stage, human, technical, rollbackPerformed, emptyList())
        }

        try {
            // 1. Parse requirement
            onStep(Step.Begin("Requirement"))
            val req = try {
                RequirementParser.parse(context, requirementText)
            } catch (e: Exception) {
                return fail("REQUIREMENT", "parse", e.message ?: "Requirement tidak valid.", e.toString())
            }
            pkgName = req.canonicalName
            onStep(Step.Finish("Requirement", true, "${req.name}${if (req.specifier.isNotBlank()) req.specifier else ""}"))
            onStep(Step.Log("  extras: ${req.extras.joinToString(",") { "[$it]" }.ifEmpty { "-" }}"))

            // 2. Resolve dependencies (reuse plan dari analyze bila diberikan)
            onStep(Step.Begin("Resolve"))
            val plan = preResolved ?: resolver.resolve(requirementText)
            if (!plan.ok) {
                return fail(plan.errorCode ?: "RESOLUTION", plan.errorStage ?: "resolve",
                    plan.humanError ?: "Resolusi gagal.", plan.technicalError)
            }
            if (plan.conflicts.isNotEmpty()) {
                TelemetryStore.increment("dependency_conflict")
                val msg = plan.conflicts.joinToString("; ") {
                    "konflik ${it.name}: butuh ${it.versionA} vs ${it.versionB} (${it.specifier})"
                }
                return fail("DEPENDENCY_CONFLICT", "resolve", "Konflik dependensi: $msg", null)
            }
            // BUG C: modul stdlib bukan kegagalan — beri tahu apa adanya.
            if (plan.stdlib.isNotEmpty() && plan.packages.isEmpty()) {
                val msg = plan.stdlib.joinToString(" ") { it.reason }
                onStep(Step.Finish("Resolve", true, "modul bawaan Python"))
                onStep(Step.Log("  ℹ️ $msg"))
                return InstallResult(
                    ok = true,
                    code = null,
                    stage = null,
                    humanMessage = msg,
                    technicalMessage = null,
                    rollbackPerformed = false,
                    installed = emptyList()
                )
            }
            if (plan.unavailable.isNotEmpty()) {
                TelemetryStore.increment("package_not_available")
                val msg = plan.unavailable.joinToString("; ") { "${it.name}: ${it.reason}" }
                return fail("PACKAGE_NOT_AVAILABLE", "resolve", "Tidak tersedia: $msg", null)
            }
            onStep(Step.Finish("Resolve", true, "${plan.packages.size} package dalam plan"))
            // Jejak resolver — memperlihatkan pustaka pendukung yang diambil
            // ATAU yang gagal diambil. Tanpa ini kegagalan native tidak bisa
            // dibedakan dari "peta tidak terbaca" (pelajaran v1.0.8).
            plan.notes.forEach { onStep(Step.Log("  · $it")) }
            plan.packages.forEach {
                onStep(Step.Log("  - ${it.canonicalName}==${it.version} [${it.source}] ${it.filename}"))
            }

            // 3. Storage guard (keputusan forum: 1.5× estimasi atau 100MB)
            val estBytes = plan.packages.sumOf { it.size ?: 0L } + 8L * 1024 * 1024
            val margin = maxOf((estBytes * ESTIMATE_FACTOR).toLong(), MIN_SAFETY_MARGIN_BYTES)
            val free = ExecutionEngine.freeStorageBytes(Paths.pythonEnvDir(context))
            if (free >= 0 && free < estBytes + margin) {
                return fail("STORAGE", "storage",
                    "Storage tidak cukup: butuh ±${(estBytes + margin) / 1024 / 1024}MB (termasuk margin), " +
                        "free ${free / 1024 / 1024}MB.", null)
            }
            onStep(Step.Log("  storage: estimasi ${estBytes / 1024 / 1024}MB + margin ${margin / 1024 / 1024}MB, free ${free / 1024 / 1024}MB"))

            // 4. Transaction
            tx = txManager.create("install")
            onStep(Step.Begin("Transaction"))
            onStep(Step.Log("  tx: ${tx.id}"))
            onStep(Step.Finish("Transaction", true))

            // 5. Download + verify per package
            onStep(Step.Begin("Download"))
            val planPackages = mutableListOf<TransactionManager.PlanPackage>()
            for (p in plan.packages) {
                val wheelFile = File(Paths.pythonWheels(context), p.filename)
                var sha = p.sha256
                if (p.localPath != null) {
                    // sumber lokal: salin ke cache bila perlu
                    val local = File(p.localPath)
                    if (!wheelFile.exists()) local.copyTo(wheelFile, overwrite = true)
                    sha = Verifier.sha256(wheelFile)
                    onStep(Step.Log("  ${p.canonicalName}: salin wheel lokal (sha256=$sha)"))
                } else {
                    val url = p.url ?: return fail("DOWNLOAD", "download",
                        "URL wheel tidak tersedia untuk ${p.canonicalName}", null)
                    val dl = download(url, wheelFile, sha) { msg -> onStep(Step.Log("  ${p.canonicalName}: $msg")) }
                    if (!dl.first) return fail("DOWNLOAD", "download", dl.second, null)
                    sha = sha ?: dl.second
                }
                planPackages.add(
                    TransactionManager.PlanPackage(
                        canonicalName = p.canonicalName,
                        version = p.version,
                        source = p.source,
                        sha256 = sha,
                        wheelUrl = p.url,
                        wheelLocalPath = p.localPath,
                        filename = p.filename,
                        supportLibrary = p.supportLibrary
                    )
                )
            }
            onStep(Step.Finish("Download", true, "${planPackages.size} wheel terdownload & diverifikasi"))

            // 6. Extract (path-safe) + validasi metadata
            onStep(Step.Begin("Extract"))
            for (p in planPackages) {
                val wheelFile = File(Paths.pythonWheels(context), p.filename ?: wheelFilename(p))
                val staging = File(tx.stagingSitePackages, "${p.canonicalName}/${p.version}")
                val res = Verifier.extractWheel(wheelFile, staging) { n -> onStep(Step.Log("  ${p.canonicalName}: $n files...")) }
                if (!res.ok) return fail("EXTRACT", "extract", res.error ?: "Ekstraksi gagal.", null)
                val (metaRes, meta) = Verifier.validateWheelMeta(staging)
                if (!metaRes.ok) return fail("VERIFY", "extract", metaRes.error ?: "Metadata invalid.", null)
                onStep(Step.Log("  ${p.canonicalName}: metadata OK (${meta?.name} ${meta?.version})"))
            }
            onStep(Step.Finish("Extract", true))

            // 7. Smoke test terhadap staging
            //
            // FIX 2026-08-13: seluruh direktori staging transaksi ini dikirim
            // sebagai "saudara". Sebelumnya tiap paket diuji SENDIRIAN, sehingga
            // `import requests` tidak menemukan urllib3 yang ada di folder
            // sebelah — ModuleNotFoundError lalu rollback. Bukan kasus khusus
            // requests: 52% paket populer punya dependensi runtime wajib.
            onStep(Step.Begin("Smoke Test"))
            val allStagingDirs = planPackages.map {
                File(tx.stagingSitePackages, "${it.canonicalName}/${it.version}").absolutePath
            } + activeSitePackagePaths()
            for (p in planPackages) {
                // Pustaka pendukung (chaquopy-openblas, chaquopy-libjpeg, ...)
                // TIDAK punya modul Python untuk diimpor. Menjalankan uji impor
                // terhadapnya akan selalu gagal dan membatalkan seluruh
                // transaksi — termasuk paket utama yang sebenarnya sudah
                // berhasil. Cukup pastikan file .so-nya benar-benar ada.
                if (p.supportLibrary) {
                    val dir = File(tx.stagingSitePackages, "${p.canonicalName}/${p.version}")
                    val adaSo = dir.walkTopDown().any { it.isFile && it.name.contains(".so") }
                    if (!adaSo) {
                        return fail("SMOKE_TEST", "smoke_test",
                            "Pustaka pendukung ${p.canonicalName} tidak memuat file .so apa pun.",
                            "staging=${dir.absolutePath}")
                    }
                    onStep(Step.Log("  ${p.canonicalName}: pustaka pendukung (.so) — uji impor dilewati"))
                    continue
                }
                val details = repository.findByCanonicalName(p.canonicalName)
                val importName = details?.importName ?: p.canonicalName
                val manifestTests = repository.loadSmokeTests()[p.canonicalName]
                val tests = buildSmokeTests(p.canonicalName, importName, details?.type, manifestTests)
                val staging = File(tx.stagingSitePackages, "${p.canonicalName}/${p.version}").absolutePath
                val outcome = smokeRunner.run(importName, staging, tests, allStagingDirs)
                if (!outcome.ok) {
                    TelemetryStore.increment("smoke_test_failure")
                    if (outcome.nativeLibs.isNotEmpty()) TelemetryStore.increment("native_load_failure")
                    val failMsg = outcome.results.firstOrNull { !it.optBoolean("ok") }
                        ?.optString("error") ?: "smoke test gagal"
                    // Sertakan SELURUH hasil + daftar .so yang ditemukan sebagai
                    // pesan teknis. Sebelumnya argumen ini diisi `null`, jadi satu-
                    // satunya jejak adalah humanMessage yang sudah dipangkas —
                    // penyebab asli ImportError (baris "Original error was: ...")
                    // tidak pernah sampai ke mana pun.
                    val teknis = buildString {
                        append("smoke gagal untuk ${p.canonicalName}==${p.version}\n")
                        append("native .so terdeteksi: ${outcome.nativeLibs.size}\n")
                        // NATIVE-LOADER: tanpa baris ini tidak mungkin dibedakan
                        // antara "pustaka pendukung tidak pernah diunduh" dan
                        // "sudah ada tapi gagal dimuat" — dua sebab yang
                        // perbaikannya sama sekali berbeda.
                        append("preload: ${outcome.preloadLog.size} catatan\n")
                        outcome.preloadLog.take(15).forEach { append("  ").append(it).append('\n') }
                        outcome.nativeLibs.take(20).forEach { append("  so: ").append(it).append('\n') }
                        append("hasil test:\n")
                        for (i in 0 until outcome.results.size) {
                            val r = outcome.results[i]
                            append("  [").append(r.optString("type", "?")).append("] ")
                                .append(if (r.optBoolean("ok")) "OK" else "GAGAL").append(' ')
                                .append(r.optString("error", "")).append('\n')
                        }
                    }
                    return fail("SMOKE_TEST", "smoke_test",
                        "Import/smoke test ${p.canonicalName} gagal: $failMsg", teknis)
                }
                onStep(Step.Log("  ${p.canonicalName}: smoke OK (${outcome.nativeLibs.size} .so)"))
            }
            onStep(Step.Finish("Smoke Test", true))

            // 8. Activate (atomic-ish + rollback)
            onStep(Step.Begin("Activate"))
            val (actOk, actMsg) = txManager.activate(tx, planPackages) { m -> onStep(Step.Log(m)) }
            if (!actOk) {
                // activate() sudah melakukan rollback + journal ROLLED_BACK
                return fail("ACTIVATION", "activation", "Aktivasi gagal: $actMsg", null, alreadyRolledBack = true)
            }
            onStep(Step.Finish("Activate", true))

            // 9. Sync SQLite + telemetri sukses
            for (p in planPackages) {
                db.upsertInstalled(p.canonicalName, p.version, "site-packages/${p.canonicalName}/${p.version}", p.source, p.sha256)
            }
            TelemetryStore.increment("install_success")
            TelemetryStore.increment("packages_installed", planPackages.size.toLong())
            val installed = planPackages.map { it.canonicalName }
            return InstallResult(true, null, null, null, null, false, installed)

        } catch (e: Exception) {
            return fail("RUNTIME", "engine", "Kegagalan internal engine: ${e.message}", e.toString())
        }
    }

    /**
     * Direktori paket yang SUDAH aktif (python-env/state/installed.json).
     *
     * FIX 2026-08-13: tanpa ini instalasi bertahap tetap gagal. Contoh: user
     * sudah memasang `urllib3`, lalu memasang `requests`. Plan hanya berisi
     * requests (urllib3 dianggap ada), tetapi smoke test tidak melihat urllib3
     * yang sudah aktif dan kembali melempar ModuleNotFoundError.
     *
     * Sengaja "best-effort": kegagalan membaca state tidak boleh menggagalkan
     * instalasi — paling buruk smoke test kembali seketat sebelumnya.
     */
    private fun activeSitePackagePaths(): List<String> = try {
        val stateFile = File(Paths.pythonState(context), "installed.json")
        if (!stateFile.exists()) {
            emptyList()
        } else {
            val root = Paths.pythonEnvDir(context)
            val obj = org.json.JSONObject(stateFile.readText())
            obj.keys().asSequence().mapNotNull { key ->
                val rel = obj.optJSONObject(key)?.optString("path")?.takeIf { it.isNotBlank() }
                rel?.let { File(root, it) }?.takeIf { it.isDirectory }?.absolutePath
            }.toList()
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun buildSmokeTests(
        canonical: String,
        importName: String,
        type: String?,
        manifest: List<org.json.JSONObject>?
    ): List<org.json.JSONObject> {
        val tests = manifest?.toMutableList() ?: mutableListOf(
            org.json.JSONObject().put("name", "import").put("type", "IMPORT").put("target", importName)
        )
        if (type == "native") {
            tests.add(org.json.JSONObject().put("name", "native-load").put("type", "NATIVE_LOAD").put("target", importName))
        }
        return tests
    }

    private fun wheelFilename(p: TransactionManager.PlanPackage): String {
        // filename dari plan disimpan di wheel cache; cari file .whl yang cocok
        val dir = Paths.pythonWheels(context)
        val prefix = p.canonicalName.replace("-", "_") + "-" + p.version
        return dir.listFiles()?.firstOrNull { it.name.startsWith(prefix) && it.name.endsWith(".whl") }?.name
            ?: (prefix + ".whl")
    }

    private fun download(
        url: String,
        dest: File,
        expectedSha256: String?,
        onLog: (String) -> Unit
    ): Pair<Boolean, String> {
        return try {
            dest.parentFile?.mkdirs()
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return false to "HTTP $code saat mengunduh $url"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        written += n
                    }
                }
            }
            conn.disconnect()
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedSha256 != null && !expectedSha256.equals(actual, ignoreCase = true)) {
                dest.delete()
                return false to "SHA-256 mismatch: expected $expectedSha256, got $actual"
            }
            onLog("${dest.name} (${written / 1024} KB) sha256=$actual")
            true to actual
        } catch (e: Exception) {
            dest.delete()
            false to "Download gagal: ${e.message}"
        }
    }

    /**
     * Analyze — Parse + Resolve saja (tanpa download/install).
     * Dipakai Manual Install flow: Parse → Resolve → Compatibility → confirm
     * kalau risky → Install (plan di-reuse supaya tidak double network).
     */
    fun analyze(requirementText: String, onLog: (Step) -> Unit = {}): DependencyResolver.ResolvePlan {
        onLog(Step.Begin("Requirement"))
        val req = RequirementParser.parse(context, requirementText)
        onLog(Step.Finish("Requirement", true, "${req.name}${if (req.specifier.isNotBlank()) req.specifier else ""}"))
        onLog(Step.Begin("Resolve"))
        val plan = resolver.resolve(requirementText)
        onLog(Step.Finish("Resolve", true, "${plan.packages.size} package dalam plan"))
        plan.notes.forEach { onLog(Step.Log("  · $it")) }
        if (plan.notes.isNotEmpty()) {
            Breadcrumb.log("PKG_RESOLVE_NOTES", plan.notes.joinToString(" | "))
        }
        return plan
    }

    /** Risiko install: ada package EXPERIMENTAL/prioritas 4 atau tidak TESTED di katalog. */
    fun riskDescription(plan: DependencyResolver.ResolvePlan, requirementText: String): String? {
        if (!plan.ok) return null
        val risks = mutableListOf<String>()
        for (p in plan.packages) {
            if (p.priority >= 4) {
                risks.add("${p.canonicalName}==${p.version}: wheel ${p.compatReason} (belum diuji ZCODE)")
            } else {
                val details = repository.findByCanonicalName(p.canonicalName)
                if (details != null && details.status != PackageStatus.TESTED) {
                    risks.add("${p.canonicalName}: status katalog '${details.status.label}' (belum TESTED)")
                }
            }
        }
        return if (risks.isEmpty()) null else risks.joinToString("\n")
    }

    // ------------------------------------------------------------------
    // UNINSTALL / SUPPORT / INSTALLED
    // ------------------------------------------------------------------

    fun uninstall(canonicalName: String, onLog: (String) -> Unit): Pair<Boolean, String> {
        val tx = TransactionManager(context)
        val result = tx.uninstall(canonicalName, onLog)
        if (result.first) {
            db.deleteInstalled(canonicalName)
            TelemetryStore.increment("uninstall_count")
        }
        return result
    }

    /**
     * Paket yang tampil di daftar "Terpasang".
     *
     * Pustaka pendukung `chaquopy-*` sengaja DISEMBUNYIKAN: user tidak pernah
     * memintanya, tidak bisa memakainya langsung, dan menghapusnya justru
     * merusak paket lain yang masih membutuhkannya. Menampilkannya hanya
     * menimbulkan pertanyaan "ini apa dan kenapa ada".
     */
    fun listInstalled(): Map<String, String> =
        repository.installedSnapshot()
            .filterKeys { !it.startsWith("chaquopy-") }
            .mapValues { it.value.version }

    /** Termasuk pustaka pendukung — untuk Diagnostics dan perhitungan ukuran. */
    fun listInstalledAll(): Map<String, String> =
        repository.installedSnapshot().mapValues { it.value.version }

    /** Support request (SPEC Phase 3) — disimpan lokal, tanpa backend cloud. */
    fun requestSupport(canonicalName: String, note: String): Pair<Boolean, String> {
        return try {
            val state = Paths.pythonState(context)
            val f = File(state, "support-requests.json")
            val arr = if (f.exists()) JSONArray(f.readText()) else JSONArray()
            val o = JSONObject()
            o.put("ts", System.currentTimeMillis())
            o.put("package", canonicalName)
            o.put("note", note.take(500))
            val runtime = RuntimeProbe.cachedInfo()
            if (runtime != null) {
                o.put("python", runtime.pythonVersion)
                o.put("abis", JSONArray(runtime.abis))
                o.put("platform", runtime.platform)
                o.put("android_api", runtime.androidApi ?: -1)
                o.put("chaquopy", runtime.chaquopyVersion)
            }
            arr.put(o)
            val tmp = File(state, "support-requests.json.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(arr.toString())
                tmp.delete()
            }
            true to "Permintaan support disimpan. ZCODE team akan pakai data ini untuk prioritas wheel/testing."
        } catch (e: Exception) {
            false to "Gagal menyimpan permintaan: ${e.message}"
        }
    }
}
