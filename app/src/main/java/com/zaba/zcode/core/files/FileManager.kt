package com.zaba.zcode.core.files

import com.zaba.zcode.core.diagnostics.Breadcrumb
import java.io.File

/**
 * FileManager — port of zabacode/core/file_manager.py with guards
 * S-20: secure_filename, MAX_FILE_BYTES 512KB, MAX_FILENAME_LEN 128, no traversal, no dotfile
 * E-01: OSError catch for read/delete
 */
object FileManager {
    const val MAX_FILE_BYTES = 512 * 1024
    const val MAX_FILENAME_LEN = 128

    fun secureFilename(name: String): String? {
        if (name.isBlank()) return null
        if (".." in name || "/" in name || "\\" in name || "\u0000" in name) return null
        val trimmed = name.trim()
        if (trimmed.length > MAX_FILENAME_LEN) return null
        if (trimmed.startsWith(".") || trimmed.startsWith("_")) return null
        if (trimmed == ".py" || trimmed.isEmpty()) return null
        if (!Regex("^[A-Za-z0-9][A-Za-z0-9_\\-\\.]*$").matches(trimmed)) return null
        return if (trimmed.endsWith(".py")) trimmed else "$trimmed.py"
    }

    fun listFiles(filesDir: File): List<Map<String, Any>> =
        filesDir.listFiles { f ->
            f.isFile && f.name.endsWith(".py") &&
                !f.name.startsWith(".") && !f.name.startsWith("_")
        }
            ?.sortedBy { it.name }
            ?.map { mapOf("name" to it.name, "size" to it.length()) } ?: emptyList()

    fun readFile(filesDir: File, filename: String): Result<String> {
        val secured = secureFilename(filename) ?: return Result.failure(IllegalArgumentException("Invalid filename"))
        val file = File(filesDir, secured)
        return try {
            if (!file.exists()) return Result.failure(NoSuchFileException(file, reason = "Not found"))
            Result.success(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveFile(filesDir: File, filename: String, content: String): Result<String> {
        val secured = secureFilename(filename) ?: run {
            Breadcrumb.log("FILE_SAVE_REJECT", "nama tidak aman: $filename")
            return Result.failure(IllegalArgumentException("Invalid filename"))
        }
        if (content.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) {
            Breadcrumb.log("FILE_SAVE_REJECT", "$secured terlalu besar")
            return Result.failure(IllegalArgumentException("Too large"))
        }
        return try {
            File(filesDir, secured).writeText(content, Charsets.UTF_8)
            Result.success(secured)
        } catch (e: Exception) {
            // Hanya KEGAGALAN yang dicatat. Autosave berjalan terus-menerus;
            // mencatat setiap simpan sukses akan memenuhi breadcrumb 128KB
            // dalam hitungan menit dan justru MENGHAPUS jejak crash yang
            // sedang dicari.
            Breadcrumb.log("FILE_SAVE_FAIL", "$secured: ${e.message}")
            Result.failure(e)
        }
    }

    /** Hapus file dengan path aman (E-01: OSError di-catch). */
    fun deleteFileIfExists(filesDir: File, filename: String): Boolean {
        val secured = secureFilename(filename) ?: return false
        return try {
            val file = File(filesDir, secured)
            val ada = file.exists()
            val ok = ada && file.delete()
            // Penghapusan itu destruktif dan jarang — selalu dicatat.
            Breadcrumb.log("FILE_DELETE", "$secured ada=$ada ok=$ok")
            ok
        } catch (e: Exception) {
            Breadcrumb.log("FILE_DELETE_FAIL", "$secured: ${e.message}")
            false
        }
    }
}
