package com.zaba.zcode.core.packageengine

import android.content.Context
import com.zaba.zcode.core.files.Paths
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
        val filename: String? = null
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
     * Aktivasi staging → environment aktif.
     * Backup installed.json dulu; setiap kegagalan pertengahan → rollback otomatis.
     */
    fun activate(
        tx: Transaction,
        packages: List<PlanPackage>,
        onLog: (String) -> Unit
    ): Pair<Boolean, String> {
        val sitePkgs = Paths.pythonSitePackages(context)
        val stateDir = Paths.pythonState(context)
        val installedFile = File(stateDir, "installed.json")
        val backupFile = File(stateDir, "installed.json.bak")

        // 1. backup state
        try {
            if (installedFile.exists()) {
                installedFile.copyTo(backupFile, overwrite = true)
            } else {
                backupFile.delete()
            }
        } catch (e: Exception) {
            return false to "Backup installed.json gagal: ${e.message}"
        }

        // 2. baca state lama
        val current = try {
            if (installedFile.exists()) JSONObject(installedFile.readText()) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }

        // 3. pindahkan staging per package (track yang sudah dipindah utk rollback)
        val moved = mutableListOf<File>()
        try {
            for (p in packages) {
                val versionDir = File(tx.stagingSitePackages, "${p.canonicalName}/${p.version}")
                if (!versionDir.exists()) {
                    return rollbackActivate(tx, moved, backupFile, installedFile, "Staging tidak lengkap untuk ${p.canonicalName}@${p.version}")
                }
                val target = File(sitePkgs, p.canonicalName)
                if (target.exists()) target.deleteRecursively()
                versionDir.copyRecursively(File(target, p.version), overwrite = true)
                moved.add(target)
                current.put(p.canonicalName, installedEntry(p, "site-packages/${p.canonicalName}/${p.version}"))
                onLog("    activate: ${p.canonicalName}@${p.version}")
            }
        } catch (e: Exception) {
            return rollbackActivate(tx, moved, backupFile, installedFile, "Aktivasi gagal: ${e.message}")
        }

        // 4. tulis state baru (temp + rename)
        try {
            val tmp = File(stateDir, "installed.json.tmp")
            tmp.writeText(current.toString())
            if (!tmp.renameTo(installedFile)) {
                installedFile.writeText(current.toString())
                tmp.delete()
            }
            backupFile.delete()
        } catch (e: Exception) {
            return rollbackActivate(tx, moved, backupFile, installedFile, "Tulis state gagal: ${e.message}")
        }

        // 5. bersihkan staging + jurnal sukses
        tx.dir.deleteRecursively()
        journal(tx.id, "install", "SUCCESS", null, null)
        return true to "OK"
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

    private fun rollbackActivate(
        tx: Transaction,
        moved: List<File>,
        backupFile: File,
        installedFile: File,
        reason: String
    ): Pair<Boolean, String> {
        // hapus yang baru dipindah
        moved.forEach { it.deleteRecursively() }
        // pulihkan state lama
        try {
            if (backupFile.exists()) {
                backupFile.copyTo(installedFile, overwrite = true)
                backupFile.delete()
            } else {
                installedFile.delete()
            }
        } catch (e: Exception) {
            // state bisa rusak — catat via journal
        }
        tx.dir.deleteRecursively()
        journal(tx.id, "install", "ROLLED_BACK", "ACTIVATION", reason)
        return false to reason
    }

    /** Abort sebelum aktivasi: hapus staging, environment tidak tersentuh. */
    fun abort(tx: Transaction, errorCode: String?, reason: String?) {
        tx.dir.deleteRecursively()
        journal(tx.id, "install", "ABORTED", errorCode, reason)
    }

    fun uninstall(canonicalName: String, onLog: (String) -> Unit): Pair<Boolean, String> {
        val sitePkgs = Paths.pythonSitePackages(context)
        val stateDir = Paths.pythonState(context)
        val installedFile = File(stateDir, "installed.json")
        return try {
            if (!installedFile.exists()) return false to "installed.json tidak ada"
            val root = JSONObject(installedFile.readText())
            val meta = root.optJSONObject(canonicalName) ?: return false to "Package '$canonicalName' tidak terpasang"
            val relPath = meta.optString("path")
            val dir = if (relPath.isNotBlank()) File(sitePkgs, relPath.removePrefix("site-packages/")) else File(sitePkgs, canonicalName)
            if (dir.exists()) dir.deleteRecursively()
            root.remove(canonicalName)
            val tmp = File(stateDir, "installed.json.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(installedFile)) {
                installedFile.writeText(root.toString())
                tmp.delete()
            }
            onLog("    uninstall: $canonicalName dihapus")
            TelemetryStore.increment("uninstall_count")
            true to "OK"
        } catch (e: Exception) {
            false to "Uninstall gagal: ${e.message}"
        }
    }
}
