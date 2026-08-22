package com.zaba.zcode.core.packageengine

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipInputStream

/**
 * Verifier — verifikasi & ekstraksi wheel aman (SPEC-001 §8, §20 Security Model).
 *
 * - SHA-256: diverifikasi SEBELUM aktivasi (download → verify → extract → smoke → activate)
 * - Path traversal protection saat ekstraksi:
 *     normalize path → reject ../ → reject absolute → extract inside staging
 * - Validasi metadata: *.dist-info/METADATA + RECORD harus ada
 *
 * Wheel dianggap UNTRUSTED INPUT (dari PyPI/Chaquopy) — semua path dicek.
 */
object Verifier {

    const val MAX_WHEEL_BYTES = 512L * 1024 * 1024 // compressed input guard
    const val MAX_EXTRACTED_BYTES = 1024L * 1024 * 1024 // decompression-bomb guard
    const val MAX_WHEEL_ENTRIES = 20_000
    const val MAX_ENTRY_NAME_CHARS = 512

    data class VerifyResult(val ok: Boolean, val error: String?)
    data class WheelMeta(val name: String, val version: String)

    /** Hitung SHA-256 file (streaming — aman untuk wheel besar). */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifySha256(file: File, expectedHex: String): VerifyResult {
        if (expectedHex.isBlank()) return VerifyResult(true, null) // tak ada hash upstream
        val actual = sha256(file)
        return if (actual.equals(expectedHex, ignoreCase = true)) {
            VerifyResult(true, null)
        } else {
            VerifyResult(false, "SHA-256 mismatch: expected $expectedHex, got $actual")
        }
    }

    /**
     * Ekstrak wheel ke destDir (staging) dengan proteksi path traversal.
     * onProgress: jumlah file ter-extract (untuk installation console).
     */
    fun extractWheel(wheel: File, destDir: File, onProgress: ((Int) -> Unit)? = null): VerifyResult {
        if (wheel.length() > MAX_WHEEL_BYTES) {
            return VerifyResult(false, "Wheel terlalu besar (>512MB)")
        }
        try {
            destDir.mkdirs()
            val destCanonical = destDir.canonicalPath
            var count = 0
            var extractedBytes = 0L
            val seenEntries = mutableSetOf<String>()
            wheel.inputStream().use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        count++
                        if (count > MAX_WHEEL_ENTRIES) {
                            return VerifyResult(false, "Wheel melebihi batas $MAX_WHEEL_ENTRIES entry")
                        }
                        val rawName = entry.name.replace('\\', '/')
                        if (rawName.length > MAX_ENTRY_NAME_CHARS) {
                            return VerifyResult(false, "Nama entry wheel terlalu panjang")
                        }
                        if (!seenEntries.add(rawName)) {
                            return VerifyResult(false, "Entry wheel duplikat: '$rawName'")
                        }
                        if (!isSafeEntryName(rawName)) {
                            return VerifyResult(false, "Entry wheel tidak aman: '$rawName' (path traversal/absolute)")
                        }
                        val target = File(destDir, rawName)
                        val targetCanonical = target.canonicalPath
                        if (!targetCanonical.startsWith(destCanonical + File.separator)) {
                            return VerifyResult(false, "Entry wheel keluar dari staging: '$rawName'")
                        }
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { out ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = zip.read(buf)
                                    if (n < 0) break
                                    extractedBytes += n
                                    if (extractedBytes > MAX_EXTRACTED_BYTES) {
                                        return VerifyResult(
                                            false,
                                            "Isi wheel melebihi batas ekstraksi ${MAX_EXTRACTED_BYTES / 1024 / 1024}MB",
                                        )
                                    }
                                    out.write(buf, 0, n)
                                }
                            }
                        }
                        zip.closeEntry()
                        if (count % 25 == 0) onProgress?.invoke(count)
                        entry = zip.nextEntry
                    }
                }
            }
            onProgress?.invoke(count)
            return VerifyResult(true, null)
        } catch (e: Exception) {
            return VerifyResult(false, "Ekstraksi wheel gagal: ${e.message}")
        }
    }

    private fun isSafeEntryName(name: String): Boolean {
        if (name.startsWith("/")) return false
        if (Regex("^[A-Za-z]:").containsMatchIn(name)) return false
        val parts = name.split("/")
        return parts.none { it == ".." || it == "." }
    }

    /** Validate metadata identity, WHEEL version, and every RECORD entry. */
    fun validateWheelMeta(
        extractedDir: File,
        expectedName: String? = null,
        expectedVersion: String? = null,
    ): Pair<VerifyResult, WheelMeta?> {
        val distInfos = extractedDir.listFiles()?.filter {
            it.isDirectory && it.name.endsWith(".dist-info")
        } ?: emptyList()
        if (distInfos.size != 1) {
            return VerifyResult(false, "Wheel harus mengandung tepat satu *.dist-info") to null
        }
        val distInfo = distInfos.single()
        val metadata = File(distInfo, "METADATA")
        val wheel = File(distInfo, "WHEEL")
        val record = File(distInfo, "RECORD")
        for (required in listOf(metadata, wheel, record)) {
            if (!required.isFile) {
                return VerifyResult(false, "${required.name} tidak ditemukan di dist-info") to null
            }
            if (required.length() > 4L * 1024 * 1024) {
                return VerifyResult(false, "${required.name} terlalu besar") to null
            }
        }

        val metaText = metadata.readText(Charsets.UTF_8)
        val name = Regex("^Name:\\s*(.+)$", RegexOption.MULTILINE)
            .find(metaText)?.groupValues?.get(1)?.trim()
        val version = Regex("^Version:\\s*(.+)$", RegexOption.MULTILINE)
            .find(metaText)?.groupValues?.get(1)?.trim()
        if (name.isNullOrBlank() || version.isNullOrBlank()) {
            return VerifyResult(false, "METADATA tidak memiliki Name/Version yang valid") to null
        }
        if (expectedName != null && canonicalName(name) != canonicalName(expectedName)) {
            return VerifyResult(false, "Nama METADATA '$name' tidak cocok plan '$expectedName'") to null
        }
        if (expectedVersion != null && version != expectedVersion) {
            return VerifyResult(false, "Versi METADATA '$version' tidak cocok plan '$expectedVersion'") to null
        }

        val wheelVersion = Regex("^Wheel-Version:\\s*(.+)$", RegexOption.MULTILINE)
            .find(wheel.readText(Charsets.UTF_8))?.groupValues?.get(1)?.trim()
            ?: return VerifyResult(false, "WHEEL tidak memiliki Wheel-Version") to null
        val wheelMajor = wheelVersion.substringBefore('.').toIntOrNull()
        if (wheelMajor == null || wheelMajor > 1) {
            return VerifyResult(false, "Wheel-Version $wheelVersion belum didukung") to null
        }

        val recordResult = verifyRecord(extractedDir, record)
        if (!recordResult.ok) return recordResult to null
        return VerifyResult(true, null) to WheelMeta(name, version)
    }

    private fun verifyRecord(extractedDir: File, record: File): VerifyResult {
        val rows = linkedMapOf<String, List<String>>()
        for ((index, line) in record.readLines(Charsets.UTF_8).withIndex()) {
            if (line.isBlank()) continue
            val columns = parseCsvLine(line)
            if (columns.size != 3 || columns[0].isBlank()) {
                return VerifyResult(false, "RECORD baris ${index + 1} tidak valid")
            }
            val path = columns[0].replace('\\', '/')
            if (!isSafeEntryName(path) || rows.put(path, columns) != null) {
                return VerifyResult(false, "RECORD path tidak aman/duplikat: '$path'")
            }
        }

        val root = extractedDir.canonicalFile
        val actualFiles = extractedDir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(extractedDir).invariantSeparatorsPath }
            .toSet()
        val distInfoPath = record.parentFile.relativeTo(extractedDir).invariantSeparatorsPath
        val recordPath = "$distInfoPath/RECORD"
        // PEP 427 requires these signature files to be absent from RECORD rows.
        // ZCODE does not treat their presence as a trust verdict: wheel download
        // SHA-256 remains the trust/integrity anchor verified before extraction.
        val signaturePaths = setOf(
            "$distInfoPath/RECORD.jws",
            "$distInfoPath/RECORD.p7s",
        )
        if (recordPath !in rows) {
            return VerifyResult(false, "RECORD wajib mencatat dirinya sendiri")
        }
        val listedSignatures = rows.keys intersect signaturePaths
        if (listedSignatures.isNotEmpty()) {
            return VerifyResult(false, "Signature wheel tidak boleh dicatat di RECORD: $listedSignatures")
        }
        val unlisted = actualFiles - rows.keys - signaturePaths
        val phantom = rows.keys - actualFiles
        if (unlisted.isNotEmpty() || phantom.isNotEmpty()) {
            return VerifyResult(
                false,
                "RECORD tidak cocok isi wheel; unlisted=${unlisted.take(3)} missing=${phantom.take(3)}",
            )
        }

        for ((path, columns) in rows) {
            val target = File(extractedDir, path).canonicalFile
            if (!target.path.startsWith(root.path + File.separator) || !target.isFile) {
                return VerifyResult(false, "RECORD menunjuk file di luar staging: '$path'")
            }
            val hashField = columns[1]
            val sizeField = columns[2]
            if (path == recordPath && hashField.isBlank() && sizeField.isBlank()) continue
            if (hashField.isBlank() || sizeField.isBlank()) {
                return VerifyResult(false, "RECORD hash/size kosong untuk '$path'")
            }
            val expectedSize = sizeField.toLongOrNull()
                ?: return VerifyResult(false, "RECORD size invalid untuk '$path'")
            if (target.length() != expectedSize) {
                return VerifyResult(false, "RECORD size mismatch untuk '$path'")
            }
            val separator = hashField.indexOf('=')
            if (separator <= 0) return VerifyResult(false, "RECORD hash invalid untuk '$path'")
            val algorithm = hashField.substring(0, separator).lowercase()
            val expected = hashField.substring(separator + 1).trimEnd('=')
            val javaAlgorithm = when (algorithm) {
                "sha256" -> "SHA-256"
                "sha384" -> "SHA-384"
                "sha512" -> "SHA-512"
                else -> return VerifyResult(false, "RECORD memakai hash lemah/tidak didukung: $algorithm")
            }
            val actual = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(digest(target, javaAlgorithm))
            if (actual != expected) {
                return VerifyResult(false, "RECORD hash mismatch untuk '$path'")
            }
        }
        return VerifyResult(true, null)
    }

    private fun digest(file: File, algorithm: String): ByteArray {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }

    /** Minimal RFC 4180 parser sufficient for the three-column RECORD format. */
    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    out += field.toString()
                    field.clear()
                }
                else -> field.append(char)
            }
            index++
        }
        if (quoted) return emptyList()
        out += field.toString()
        return out
    }

    private fun canonicalName(name: String): String =
        name.trim().lowercase().replace(Regex("[-_.]+"), "-")
}
