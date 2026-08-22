package com.zaba.zcode.core.packageengine

import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifierTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val distInfo = "demo_pkg-1.0.dist-info"

    private fun sha256Record(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return "sha256=" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun wheel(
        mutateRecord: (String) -> String = { it },
        extraZipEntry: Pair<String, ByteArray>? = null,
    ): File {
        val entries = linkedMapOf(
            "demo_pkg/__init__.py" to "VALUE = 1\n".toByteArray(),
            "$distInfo/METADATA" to "Metadata-Version: 2.1\nName: demo-pkg\nVersion: 1.0\n".toByteArray(),
            "$distInfo/WHEEL" to "Wheel-Version: 1.0\nGenerator: test\nRoot-Is-Purelib: true\nTag: py3-none-any\n".toByteArray(),
        )
        val recordPath = "$distInfo/RECORD"
        val record = buildString {
            entries.forEach { (path, bytes) ->
                append(path).append(',').append(sha256Record(bytes)).append(',').append(bytes.size).append('\n')
            }
            append(recordPath).append(",,\n")
        }.let(mutateRecord).toByteArray()
        entries[recordPath] = record
        extraZipEntry?.let { entries[it.first] = it.second }

        val file = temporary.newFile("demo_pkg-1.0-py3-none-any.whl")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun validWheelChecksIdentityWheelVersionAndEveryRecordHash() {
        val extracted = temporary.newFolder("valid")
        val extract = Verifier.extractWheel(wheel(), extracted)
        val (verified, meta) = Verifier.validateWheelMeta(extracted, "demo.pkg", "1.0")

        assertTrue(extract.error.orEmpty(), extract.ok)
        assertTrue(verified.error.orEmpty(), verified.ok)
        assertEquals("demo-pkg", meta?.name)
        assertEquals("1.0", meta?.version)
    }

    @Test
    fun recordHashMismatchFailsClosed() {
        val extracted = temporary.newFolder("bad-hash")
        val file = wheel(
            mutateRecord = { record: String ->
                record.replaceFirst("sha256=", "sha256=broken")
            }
        )
        assertTrue(Verifier.extractWheel(file, extracted).ok)

        val (verified, _) = Verifier.validateWheelMeta(extracted, "demo-pkg", "1.0")

        assertFalse(verified.ok)
        assertTrue(verified.error.orEmpty().contains("hash mismatch"))
    }

    @Test
    fun fileMissingFromRecordIsRejected() {
        val extracted = temporary.newFolder("unlisted")
        val file = wheel(extraZipEntry = "surprise.py" to "bad = True\n".toByteArray())
        assertTrue(Verifier.extractWheel(file, extracted).ok)

        val (verified, _) = Verifier.validateWheelMeta(extracted, "demo-pkg", "1.0")

        assertFalse(verified.ok)
        assertTrue(verified.error.orEmpty().contains("RECORD tidak cocok"))
    }

    @Test
    fun metadataIdentityMustMatchResolvedPlan() {
        val extracted = temporary.newFolder("identity")
        assertTrue(Verifier.extractWheel(wheel(), extracted).ok)

        val (verified, _) = Verifier.validateWheelMeta(extracted, "another-project", "1.0")

        assertFalse(verified.ok)
        assertTrue(verified.error.orEmpty().contains("tidak cocok plan"))
    }

    @Test
    fun traversalEntryIsRejectedBeforeWritingOutsideStaging() {
        val file = temporary.newFile("traversal.whl")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.py"))
            zip.write("escaped = True".toByteArray())
            zip.closeEntry()
        }
        val extracted = temporary.newFolder("traversal-out")

        val result = Verifier.extractWheel(file, extracted)

        assertFalse(result.ok)
        assertFalse(File(extracted.parentFile, "escape.py").exists())
    }
}
