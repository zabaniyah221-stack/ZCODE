package com.zaba.zcode.core.files

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Transactional private trash for destructive workspace operations.
 *
 * Files are moved only inside app-private storage on the same filesystem. A clear
 * becomes visible as restorable only after the incoming directory is atomically
 * renamed to [LAST_DIR]. An interrupted incoming operation is rolled back on the
 * next startup instead of being mistaken for a completed deletion.
 *
 * This class deliberately has no Android dependency so its filesystem invariants
 * can be exercised by ordinary JVM tests.
 */
class WorkspaceTrashManager internal constructor(
    private val filesDir: File,
    private val trashRoot: File,
    /** Test seam for deterministic copy/move fault injection. */
    private val moveOverride: ((File, File) -> Unit)? = null,
) {
    data class ClearResult(
        val ok: Boolean,
        val count: Int,
        val message: String,
    )

    data class RestoreResult(
        val ok: Boolean,
        val count: Int,
        val message: String,
        val metadata: String? = null,
        /** Original name -> conflict-safe restored name. */
        val restoredNames: Map<String, String> = emptyMap(),
        internal val copiedFiles: List<File> = emptyList(),
    )

    fun pythonFileCount(): Int = synchronized(LOCK) { workspacePythonFiles().size }

    fun hasRestorableClear(): Boolean = synchronized(LOCK) {
        val last = File(trashRoot, LAST_DIR)
        last.isDirectory && File(last, MANIFEST).isFile
    }

    /**
     * Recover only operations which never reached the atomic commit point.
     * A committed [LAST_DIR] is user-visible trash and must never be restored
     * automatically.
     */
    fun recoverInterruptedClear(): String? = synchronized(LOCK) {
        if (!trashRoot.exists()) return@synchronized null
        var recovered = 0
        var conflicted = 0
        trashRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(INCOMING_PREFIX) }
            ?.forEach { incoming ->
                incoming.listFiles()
                    ?.filter { isWorkspacePythonFile(it) }
                    ?.forEach { source ->
                        val target = uniqueWorkspaceTarget(source.name)
                        try {
                            atomicMove(source, target)
                            recovered++
                            if (target.name != source.name) conflicted++
                        } catch (_: Exception) {
                            // Leave the incoming directory in place. A later startup
                            // can retry; deleting it here would destroy recovery data.
                        }
                    }
                // Metadata belongs to the interrupted transaction. Once every
                // user file is back, it is safe to remove metadata and the shell.
                val stillHasUserFile = incoming.listFiles()?.any { isWorkspacePythonFile(it) } == true
                if (!stillHasUserFile) {
                    File(incoming, MANIFEST).delete()
                    File(incoming, "$MANIFEST.tmp").delete()
                    incoming.delete()
                }
            }
        trashRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(OLD_PREFIX) }
            ?.forEach { old ->
                val last = File(trashRoot, LAST_DIR)
                if (!last.exists()) {
                    runCatching { atomicMove(old, last) }
                } else {
                    old.deleteRecursively()
                }
            }
        when {
            recovered == 0 -> null
            conflicted == 0 -> "$recovered file dipulihkan dari operasi Clear All yang terputus."
            else -> "$recovered file dipulihkan; $conflicted nama bentrok dipulihkan dengan nama baru."
        }
    }

    /**
     * Move every top-level user Python file to a committed private trash set.
     * Existing restorable trash is retained until the new set has committed.
     */
    fun clearAll(metadata: String): ClearResult = synchronized(LOCK) {
        val sources = workspacePythonFiles()
        if (sources.isEmpty()) {
            return@synchronized ClearResult(false, 0, "Tidak ada file .py untuk dipindahkan.")
        }
        if (!trashRoot.exists() && !trashRoot.mkdirs()) {
            return@synchronized ClearResult(false, 0, "Folder pemulihan tidak dapat dibuat.")
        }

        val id = "${System.currentTimeMillis()}-${Thread.currentThread().id}"
        val incoming = File(trashRoot, "$INCOMING_PREFIX$id")
        if (!incoming.mkdir()) {
            return@synchronized ClearResult(false, 0, "Transaksi Clear All tidak dapat dibuat.")
        }

        val moved = mutableListOf<Pair<File, File>>()
        try {
            for (source in sources) {
                val target = File(incoming, source.name)
                atomicMove(source, target)
                moved += source to target
            }
            writeManifestAtomically(incoming, metadata)

            val last = File(trashRoot, LAST_DIR)
            val old = File(trashRoot, "$OLD_PREFIX$id")
            if (last.exists()) atomicMove(last, old)
            try {
                atomicMove(incoming, last)
            } catch (e: Exception) {
                if (old.exists() && !last.exists()) runCatching { atomicMove(old, last) }
                throw e
            }
            // Previous deletion expires only after the new deletion is committed.
            old.deleteRecursively()
            cleanupEmptyTrashRoot()
            ClearResult(true, sources.size, "${sources.size} file dipindahkan ke pemulihan privat.")
        } catch (e: Exception) {
            // If incoming was already committed, leave LAST intact: it contains all
            // user files and is safer than attempting a second destructive move.
            if (incoming.exists()) {
                moved.asReversed().forEach { (original, staged) ->
                    if (staged.exists() && !original.exists()) {
                        runCatching { atomicMove(staged, original) }
                    }
                }
                incoming.deleteRecursively()
            }
            val detail = when (e) {
                is AtomicMoveNotSupportedException -> "filesystem tidak mendukung atomic move"
                else -> e.message ?: e.javaClass.simpleName
            }
            ClearResult(false, 0, "Clear All dibatalkan; file lama dipertahankan ($detail).")
        }
    }

    /**
     * Copy a committed trash set back into the workspace without overwriting any
     * current file. Trash remains intact until [finishRestore] is called, allowing
     * the caller to commit workspace topology/preferences first.
     */
    fun beginRestore(): RestoreResult = synchronized(LOCK) {
        val last = File(trashRoot, LAST_DIR)
        val manifest = File(last, MANIFEST)
        if (!last.isDirectory || !manifest.isFile) {
            return@synchronized RestoreResult(false, 0, "Tidak ada penghapusan yang dapat dipulihkan.")
        }
        val metadata = runCatching { manifest.readText(Charsets.UTF_8) }.getOrElse {
            return@synchronized RestoreResult(false, 0, "Manifest pemulihan tidak dapat dibaca.")
        }
        val sources = last.listFiles()?.filter { isWorkspacePythonFile(it) }?.sortedBy { it.name }.orEmpty()
        if (sources.isEmpty()) {
            return@synchronized RestoreResult(false, 0, "Set pemulihan tidak berisi file .py.")
        }

        val copied = mutableListOf<File>()
        val names = linkedMapOf<String, String>()
        try {
            for (source in sources) {
                val target = uniqueWorkspaceTarget(source.name)
                copyAndVerify(source, target)
                copied += target
                names[source.name] = target.name
            }
            RestoreResult(
                ok = true,
                count = copied.size,
                message = "${copied.size} file dipulihkan tanpa menimpa file saat ini.",
                metadata = metadata,
                restoredNames = names,
                copiedFiles = copied,
            )
        } catch (e: Exception) {
            copied.forEach { it.delete() }
            RestoreResult(false, 0, "Restore dibatalkan; workspace saat ini tidak diubah (${e.message ?: e.javaClass.simpleName}).")
        }
    }

    /** Finalize restore only after the caller durably commits workspace state. */
    fun finishRestore(result: RestoreResult): Boolean = synchronized(LOCK) {
        if (!result.ok) return@synchronized false
        val last = File(trashRoot, LAST_DIR)
        if (!last.isDirectory) return@synchronized false
        val deleted = last.deleteRecursively()
        cleanupEmptyTrashRoot()
        deleted
    }

    /** Remove only files created by [beginRestore]; committed trash stays intact. */
    fun rollbackRestore(result: RestoreResult) = synchronized(LOCK) {
        result.copiedFiles.forEach { file ->
            if (file.parentFile == filesDir && file.isFile) file.delete()
        }
    }

    private fun workspacePythonFiles(): List<File> =
        filesDir.listFiles()
            ?.filter { isWorkspacePythonFile(it) }
            ?.sortedBy { it.name }
            .orEmpty()

    private fun isWorkspacePythonFile(file: File): Boolean =
        file.isFile && file.name.endsWith(".py") &&
            !file.name.startsWith(".") && !file.name.startsWith("_")

    private fun uniqueWorkspaceTarget(originalName: String): File {
        val direct = File(filesDir, originalName)
        if (!direct.exists()) return direct
        val stem = originalName.removeSuffix(".py")
        var index = 2
        while (true) {
            val candidate = File(filesDir, "${stem}_restored_$index.py")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun writeManifestAtomically(dir: File, metadata: String) {
        val tmp = File(dir, "$MANIFEST.tmp")
        FileOutputStream(tmp).use { output ->
            output.write(metadata.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        atomicMove(tmp, File(dir, MANIFEST))
    }

    private fun copyAndVerify(source: File, target: File) {
        if (target.exists()) throw IllegalStateException("target restore sudah ada: ${target.name}")
        source.inputStream().use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        if (source.length() != target.length() || sha256(source) != sha256(target)) {
            target.delete()
            throw IllegalStateException("verifikasi copy gagal untuk ${source.name}")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun atomicMove(source: File, target: File) {
        moveOverride?.let { injected ->
            injected(source, target)
            return
        }
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private fun cleanupEmptyTrashRoot() {
        if (trashRoot.listFiles().isNullOrEmpty()) trashRoot.delete()
    }

    companion object {
        private val LOCK = Any()
        private const val LAST_DIR = "last"
        private const val MANIFEST = "workspace-state.json"
        private const val INCOMING_PREFIX = ".incoming-"
        private const val OLD_PREFIX = ".old-"
    }
}
