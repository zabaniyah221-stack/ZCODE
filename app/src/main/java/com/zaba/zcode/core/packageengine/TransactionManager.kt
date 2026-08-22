package com.zaba.zcode.core.packageengine

import android.content.Context
import android.util.AtomicFile
import com.zaba.zcode.core.diagnostics.Breadcrumb
import com.zaba.zcode.core.files.Paths
import com.zaba.zcode.core.logging.SemanticLog
import com.zaba.zcode.core.logging.SemanticLogKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * TransactionManager — installation transactional (SPEC-001 §3, §6, ADR-004).
 *
 * Alur: create → stage (download/verify/extract di transactions/<tx-id>/) →
 * smoke test (terhadap staging) → activate (atomic-ish: pindah dir + update
 * state/installed.json dengan backup) → rollback (pulihkan bila gagal).
 *
 * Environment: python-env/
 *   site-packages/<normalized>/<version>/   ← versi aktif (di-inject sys.path)
 *   transactions/<tx-id>/site-packages/     ← staging
 *   state/installed.json                    ← sumber kebenaran versi aktif
 *   state/transactions.json                 ← journal transaksi
 *
 * Rule 3 SPEC: tidak ada mutasi langsung environment aktif — semua lewat staging.
 */
class TransactionManager(private val context: Context) {

    data class Transaction(val id: String, val dir: File, val logDir: File) {
        val stagingSitePackages: File get() = File(dir, "site-packages")
        fun logFile(): File = File(logDir, "$id.log")
    }

    data class PlanPackage(
        val canonicalName: String,
        val version: String,
        val source: String,
        val sha256: String?,
        val wheelUrl: String?,
        val wheelLocalPath: String?,
        val filename: String? = null,
        /** Pustaka pendukung native (.so saja) — bukan modul Python. */
        val supportLibrary: Boolean = false
    )

    data class ActivationResult(
        val ok: Boolean,
        val message: String,
        /** canonical package name -> path relative to python-env. */
        val activatedPaths: Map<String, String> = emptyMap(),
        /** True only when the old state and directories remain authoritative. */
        val oldEnvironmentPreserved: Boolean = true,
    )

    private val counter = AtomicLong(0)

    fun create(operation: String): Transaction {
        val id = "tx_${System.currentTimeMillis()}_${counter.incrementAndGet()}_" +
            Integer.toHexString((Math.random() * 0xFFFF).toInt()).padStart(4, '0')
        val dir = File(Paths.pythonTransactions(context), id)
        File(dir, "site-packages").mkdirs()
        journal(id, operation, "CREATED", null, null)
        return Transaction(id, dir, Paths.pythonLogs(context))
    }

    fun appendTxLog(tx: Transaction, line: String) {
        try {
            tx.logFile().appendText(line + "\n")
        } catch (e: Exception) {
            // log transaksi tidak boleh menggagalkan install
        }
    }

    fun journal(id: String, operation: String, state: String, errorCode: String?, errorMessage: String?) {
        try {
            val f = File(Paths.pythonState(context), "transactions.json")
            val arr = if (f.exists()) JSONArray(f.readText()) else JSONArray()
            val o = JSONObject()
            o.put("id", id)
            o.put("operation", operation)
            o.put("state", state)
            o.put("started_at", System.currentTimeMillis())
            if (errorCode != null) o.put("error_code", errorCode)
            if (errorMessage != null) o.put("error_message", errorMessage)
            if (state == "SUCCESS" || state == "ROLLED_BACK" || state == "ABORTED") {
                o.put("completed_at", System.currentTimeMillis())
            }
            arr.put(o)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(arr.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            // jurnal best-effort
        }
    }

    /**
     * Activate verified staging with generation directories.
     *
     * Old active directories are never touched before the atomic installed.json
     * commit. Every install, including same-version reinstall, receives a unique
     * final generation path. Therefore a copy/move/state failure can delete only
     * unreferenced new data while the old state remains executable.
     */
    fun activate(
        tx: Transaction,
        packages: List<PlanPackage>,
        onLog: (String) -> Unit
    ): ActivationResult {
        if (packages.isEmpty()) return ActivationResult(false, "Rencana aktivasi kosong.")
        if (packages.map { it.canonicalName }.distinct().size != packages.size) {
            return ActivationResult(false, "Rencana aktivasi memuat package duplikat.")
        }

        val sitePkgs = Paths.pythonSitePackages(context)
        val installedFile = File(Paths.pythonState(context), "installed.json")
        val current = try {
            recoverAtomicFile(installedFile)
            if (installedFile.exists()) JSONObject(installedFile.readText()) else JSONObject()
        } catch (e: Exception) {
            return ActivationResult(false, "State installed.json lama tidak valid: ${e.message}")
        }

        data class Prepared(
            val plan: PlanPackage,
            val source: File,
            val incoming: File,
            val finalDir: File,
            val relativePath: String,
        )

        val prepared = mutableListOf<Prepared>()
        val generation = tx.id.filter { it.isLetterOrDigit() || it == '_' }.takeLast(48)

        // Phase A: prepare complete incoming trees while every currently active
        // generation remains untouched.
        try {
            for (p in packages) {
                requireSafePackageName(p.canonicalName)
                requireSafePathSegment(p.version, "version")
                val source = File(tx.stagingSitePackages, "${p.canonicalName}/${p.version}")
                if (!source.isDirectory) {
                    throw IllegalStateException("Staging tidak lengkap untuk ${p.canonicalName}@${p.version}")
                }
                normalizePermissions(source)
                val packageRoot = File(sitePkgs, p.canonicalName)
                if (!packageRoot.exists() && !packageRoot.mkdirs()) {
                    throw IllegalStateException("Folder package tidak dapat dibuat: ${p.canonicalName}")
                }
                val finalName = "${p.version}__zcode_$generation"
                val incoming = File(packageRoot, ".incoming-$finalName")
                val finalDir = File(packageRoot, finalName)
                if (incoming.exists() || finalDir.exists()) {
                    throw IllegalStateException("Generation target sudah ada untuk ${p.canonicalName}@${p.version}")
                }
                source.copyRecursively(incoming, overwrite = false)
                verifyCopiedTree(source, incoming)
                prepared += Prepared(
                    p,
                    source,
                    incoming,
                    finalDir,
                    "site-packages/${p.canonicalName}/$finalName",
                )
            }
        } catch (error: Exception) {
            prepared.forEach { runCatching { it.incoming.deleteRecursively() } }
            runCatching { tx.dir.deleteRecursively() }
            val reason = "Aktivasi dibatalkan; environment lama dipertahankan: " +
                (error.message ?: error.javaClass.simpleName)
            journal(tx.id, "install", "ROLLED_BACK", "ACTIVATION", reason)
            return ActivationResult(false, reason, oldEnvironmentPreserved = true)
        }

        val next = JSONObject(current.toString())
        for (item in prepared) {
            next.put(item.plan.canonicalName, installedEntry(item.plan, item.relativePath))
        }

        // Phase B + COMMIT BOUNDARY. The helper may roll back only before the
        // AtomicFile state commit returns. No post-commit callback lives here.
        val committed = ActivationCommitBoundary.promoteAndCommit(
            prepared.map { ActivationCommitBoundary.Generation(it.incoming, it.finalDir) }
        ) {
            writeAtomicFile(installedFile, next.toString().toByteArray(Charsets.UTF_8))
        }
        if (committed.isFailure) {
            runCatching { tx.dir.deleteRecursively() }
            val error = committed.exceptionOrNull()
            val reason = "Aktivasi dibatalkan; environment lama dipertahankan: " +
                (error?.message ?: error?.javaClass?.simpleName ?: "commit gagal")
            journal(tx.id, "install", "ROLLED_BACK", "ACTIVATION", reason)
            return ActivationResult(false, reason, oldEnvironmentPreserved = true)
        }

        // COMMITTED: installed.json now authoritatively points at finalDir.
        // Cleanup/log/journal failures may leave stale storage or missing logs,
        // but must never enter pre-commit rollback or delete active finalDir.
        val postCommitSteps = mutableListOf<Pair<String, () -> Unit>>()
        for (item in prepared) {
            postCommitSteps.add("cleanup ${item.plan.canonicalName}" to {
                val packageRoot = item.finalDir.parentFile
                    ?: throw IllegalStateException("Package root hilang")
                packageRoot.listFiles()?.forEach { candidate ->
                    if (candidate != item.finalDir && !candidate.name.startsWith(".incoming-")) {
                        candidate.deleteRecursively()
                    }
                }
            })
            postCommitSteps.add("log ${item.plan.canonicalName}" to {
                onLog("    activate: ${item.plan.canonicalName}@${item.plan.version}")
            })
        }
        postCommitSteps.add("transaction cleanup" to { tx.dir.deleteRecursively() })
        postCommitSteps.add("journal success" to {
            journal(tx.id, "install", "SUCCESS", null, null)
        })
        val warnings = ActivationCommitBoundary.runBestEffort(postCommitSteps)
        if (warnings.isNotEmpty()) {
            runCatching {
                Breadcrumb.log("PKG_ACTIVATION_POST_COMMIT_WARN", warnings.joinToString(" | "))
            }
        }

        return ActivationResult(
            ok = true,
            message = if (warnings.isEmpty()) "OK" else "OK; post-commit warning: ${warnings.joinToString(" | ")}",
            activatedPaths = prepared.associate { it.plan.canonicalName to it.relativePath },
            oldEnvironmentPreserved = false,
        )
    }

    /**
     * BUG U: pastikan seluruh isi direktori bisa dibaca (dan ELF bisa
     * dieksekusi). Dipanggil sebelum copyRecursively di activate. Gagal
     * per-file tidak fatal — copy yang jadi hakimnya.
     */
    private fun normalizePermissions(root: File) {
        try {
            root.walkTopDown().forEach { f ->
                try {
                    if (!f.canRead()) f.setReadable(true, false)
                    if (f.isFile && !f.name.endsWith(".py") && !f.name.endsWith(".pyc")) {
                        val header = ByteArray(4)
                        val n = f.inputStream().use { it.read(header) }
                        val isElf = n == 4 && header[0] == 0x7F.toByte() &&
                            header[1] == 'E'.code.toByte() &&
                            header[2] == 'L'.code.toByte() &&
                            header[3] == 'F'.code.toByte()
                        if (isElf && !f.canExecute()) f.setExecutable(true, false)
                    }
                } catch (_: Exception) { /* per-file: biarkan copy menghakimi */ }
            }
        } catch (_: Exception) { /* normalisasi bukan alasan menggagalkan activate */ }
    }

    private fun installedEntry(p: PlanPackage, path: String): JSONObject {
        val o = JSONObject()
        o.put("version", p.version)
        o.put("path", path)
        o.put("installed_at", System.currentTimeMillis())
        o.put("source", p.source)
        if (p.sha256 != null) o.put("sha256", p.sha256)
        return o
    }

    private fun requireSafePackageName(name: String) {
        if (!Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$").matches(name)) {
            throw IllegalArgumentException("Nama package tidak aman: $name")
        }
    }

    private fun requireSafePathSegment(value: String, label: String) {
        if (value.isBlank() || value == "." || value == ".." ||
            '/' in value || '\\' in value || value.indexOf(0.toChar()) >= 0
        ) {
            throw IllegalArgumentException("$label path tidak aman")
        }
    }

    /** Compare directories by relative path, type, size, and SHA-256. */
    private fun verifyCopiedTree(source: File, copied: File) {
        fun snapshot(root: File): Map<String, String> = root.walkTopDown()
            .filter { it != root }
            .associate { item ->
                val relative = item.relativeTo(root).invariantSeparatorsPath
                val value = if (item.isDirectory) {
                    "dir"
                } else {
                    "file:${item.length()}:${sha256(item)}"
                }
                relative to value
            }
        if (snapshot(source) != snapshot(copied)) {
            throw IllegalStateException("Verifikasi tree hasil copy gagal")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun writeAtomicFile(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file)
        var output: FileOutputStream? = null
        try {
            output = atomic.startWrite()
            output.write(bytes)
            output.fd.sync()
            atomic.finishWrite(output)
            output = null
        } catch (e: Exception) {
            output?.let { atomic.failWrite(it) }
            throw e
        }
    }

    private fun recoverAtomicFile(file: File) {
        val backup = File(file.path + ".bak")
        if (!file.exists() && !backup.exists()) return
        AtomicFile(file).openRead().use { /* opening restores an interrupted backup */ }
    }

    /** Abort sebelum aktivasi: hapus staging, environment tidak tersentuh. */
    fun abort(tx: Transaction, errorCode: String?, reason: String?) {
        tx.dir.deleteRecursively()
        journal(tx.id, "install", "ABORTED", errorCode, reason)
    }

    fun uninstall(
        canonicalName: String,
        onLog: (SemanticLog) -> Unit
    ): Pair<Boolean, String> {
        val sitePkgs = Paths.pythonSitePackages(context)
        val stateDir = Paths.pythonState(context)
        val installedFile = File(stateDir, "installed.json")
        return try {
            recoverAtomicFile(installedFile)
            if (!installedFile.exists()) return false to "installed.json tidak ada"
            val root = JSONObject(installedFile.readText())
            val meta = root.optJSONObject(canonicalName) ?: return false to "Package '$canonicalName' tidak terpasang"
            val relPath = meta.optString("path")
            val expectedPrefix = "site-packages/$canonicalName/"
            if (!relPath.startsWith(expectedPrefix)) {
                return false to "Path package aktif tidak aman; uninstall dibatalkan"
            }
            val dir = File(Paths.pythonEnvDir(context), relPath).canonicalFile
            val safeRoot = sitePkgs.canonicalFile
            if (dir.parentFile?.parentFile != safeRoot) {
                return false to "Path package aktif keluar dari site-packages; uninstall dibatalkan"
            }

            // Commit removal from the active pointer first. If state write fails,
            // the executable directory remains untouched and uninstall is false.
            root.remove(canonicalName)
            writeAtomicFile(installedFile, root.toString().toByteArray(Charsets.UTF_8))
            val deleted = !dir.exists() || dir.deleteRecursively()
            dir.parentFile?.takeIf { it != sitePkgs && it.listFiles().isNullOrEmpty() }?.delete()
            onLog(SemanticLog(
                if (deleted) "uninstall: $canonicalName dihapus"
                else "uninstall: $canonicalName nonaktif; sisa direktori gagal dibersihkan",
                if (deleted) SemanticLogKind.INFO else SemanticLogKind.WARN
            ))
            true to if (deleted) "OK" else "OK; package nonaktif tetapi cleanup direktori tertunda"
        } catch (e: Exception) {
            false to "Uninstall gagal: ${e.message}"
        }
    }

    companion object {
        /**
         * Complete AtomicFile recovery before Python or UI reads installed.json
         * directly. Returns false only when recovery itself fails.
         */
        fun recoverInstalledState(context: Context): Boolean = try {
            val file = File(Paths.pythonState(context), "installed.json")
            val backup = File(file.path + ".bak")
            if (file.exists() || backup.exists()) {
                AtomicFile(file).openRead().use { /* restore backup if needed */ }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
