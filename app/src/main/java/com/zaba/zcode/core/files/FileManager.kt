package com.zaba.zcode.core.files

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
        filesDir.listFiles { f -> f.name.endsWith(".py") && !f.name.startsWith(".") && !f.name.startsWith("_") }
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
        val secured = secureFilename(filename) ?: return Result.failure(IllegalArgumentException("Invalid filename"))
        if (content.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) return Result.failure(IllegalArgumentException("Too large"))
        return try {
            File(filesDir, secured).writeText(content, Charsets.UTF_8)
            Result.success(secured)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
