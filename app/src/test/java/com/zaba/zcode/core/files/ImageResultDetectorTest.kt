package com.zaba.zcode.core.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * ImageResultDetectorTest — bagian MURNI preview-PNG (v1.0.23): deteksi
 * file gambar pasca-run. java.io murni -> JVM test tanpa Android runtime
 * (pola UpdateUnitTests). Yang Android-dependent (decode/dialog) dijaga
 * guard lexikal + UAT device.
 */
class ImageResultDetectorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun touch(name: String, mtime: Long): java.io.File =
        tmp.newFile(name).apply { setLastModified(mtime) }

    @Test
    fun detectsOnlyNewImagesInWorkspaceRoot() {
        val dir = tmp.root
        val t0 = 1_000_000L
        touch("grafik.png", t0 + 500)       // hasil run ini
        touch("lama.png", t0 - 100)         // run sebelumnya -> bukan
        touch("data.json", t0 + 900)        // baru tapi bukan gambar
        touch("hasil.JPG", t0 + 700)        // ekstensi besar -> tetap gambar
        val sub = dir.resolve("sub").apply { mkdir() }
        sub.resolve("dalam.png").apply { writeText("x"); setLastModified(t0 + 800) } // subdir -> bukan

        val found = ImageResultDetector.detectNewImages(dir, t0)
        assertEquals(listOf("grafik.png", "hasil.JPG"), found.map { it.name })
    }

    @Test
    fun emptyOrMissingDirIsSafe() {
        assertTrue(ImageResultDetector.detectNewImages(tmp.root.resolve("nodir"), 0L).isEmpty())
    }

    @Test
    fun boundaryIsInclusiveAndSortedByTime() {
        val t0 = 5_000L
        touch("b.png", t0 + 200)
        touch("a.png", t0 + 100)
        touch("c.png", t0)               // tepat == start -> IKUT (ditulis saat run)
        val found = ImageResultDetector.detectNewImages(tmp.root, t0)
        assertEquals(listOf("c.png", "a.png", "b.png"), found.map { it.name })
    }

    @Test
    fun extensionSetIsConservative() {
        for (ok in listOf("x.png", "x.jpg", "x.jpeg", "x.webp", "x.bmp", "X.Png")) {
            assertTrue(ImageResultDetector.isImageFile(ok))
        }
        for (no in listOf("x.gif", "x.txt", "x.svg", "x", "x.py", ".png/")) {
            assertFalse(ImageResultDetector.isImageFile(no))
        }
    }
}
