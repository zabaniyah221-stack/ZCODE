package com.zaba.zcode.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UpdateUnitTests — v1.0.22 one-tap update (RFC D9): bagian MURNI (tanpa
 * Android runtime) diuji di JVM. Pola rumah: JUnit4 + fixture string;
 * tidak ada Robolectric di stack — fungsi yang butuh Context dilindungi
 * guard lexikal (test_zcode_kotlin_guards.py) + UAT device.
 */
class UpdateUnitTests {

    // ---------- parseSemVer (D2: tag harus persis vMAJOR.MINOR.PATCH) ----------

    @Test
    fun parsesStrictSemanticTag() {
        assertEquals(Triple(1, 0, 22), UpdateChecker.parseSemVer("v1.0.22"))
        assertEquals(Triple(10, 2, 0), UpdateChecker.parseSemVer("v10.2.0"))
    }

    @Test
    fun rejectsAnythingOutsideStrictPattern() {
        assertNull(UpdateChecker.parseSemVer("1.0.22")) // tanpa v
        assertNull(UpdateChecker.parseSemVer("V1.0.22")) // kapital
        assertNull(UpdateChecker.parseSemVer("v1.0")) // pendek
        assertNull(UpdateChecker.parseSemVer("v1.0.22-beta")) // prerekale
        assertNull(UpdateChecker.parseSemVer("v1.0.22.0")) // 4 segmen
        assertNull(UpdateChecker.parseSemVer("latest"))
        assertNull(UpdateChecker.parseSemVer(""))
    }

    // ---------- compareVersions (D3: NUMERIK per-segmen, bukan string) ----------

    @Test
    fun twoDigitPatchIsGreaterThanOneDigit() {
        // Kelas bug string-compare yang sudah dimakamkan guard v1.0.21:
        // sebagai string, "1.0.10" < "1.0.9". Harus numerik.
        assertEquals(1, UpdateChecker.compareVersions("v1.0.10", "v1.0.9"))
        assertEquals(-1, UpdateChecker.compareVersions("v1.0.9", "v1.0.10"))
    }

    @Test
    fun comparesSegmentsLeftToRight() {
        assertEquals(1, UpdateChecker.compareVersions("v1.2.0", "v1.1.9"))
        assertEquals(1, UpdateChecker.compareVersions("v2.0.0", "v1.9.9"))
        assertEquals(0, UpdateChecker.compareVersions("v1.0.21", "v1.0.21"))
        assertEquals(-1, UpdateChecker.compareVersions("v1.0.9", "v1.0.10"))
    }

    @Test
    fun invalidEitherSideIsNull() {
        assertNull(UpdateChecker.compareVersions("v1.0", "v1.0.1"))
        assertNull(UpdateChecker.compareVersions("v1.0.1", "latest"))
    }

    // ---------- selectApkAsset (D1/D2: logika integritas, data murni) ----------
    //
    // Diuji lewat List<AssetRef> BUKAN string JSON: org.json di JVM unit test
    // adalah stub android (mockable jar), jadi semua keputusan integritas
    // difungsikan murni (RFC D9 + pola rumah: bagian Android-dependent
    // dilindungi guard lexikal + UAT device; adapter org.json tipis).

    private val validAssets = listOf(
        UpdateChecker.AssetRef(
            name = "ZCODE-v1.0.22.apk.sha256",
            sizeBytes = 84,
            digest = "sha256:66638dad83d35a85edeafab085380866475dcad11712d14cce5da2422e3ff7ce",
            downloadUrl = "https://example.invalid/notes"
        ),
        UpdateChecker.AssetRef(
            name = "apksigner.txt",
            sizeBytes = 945,
            digest = "sha256:ce75041836574a14a9f9f69c6a90c576adbc05e7bff704fb5b85ad7f0b003ec7",
            downloadUrl = "https://example.invalid/signer"
        ),
        UpdateChecker.AssetRef(
            name = "ZCODE-v1.0.22.apk",
            sizeBytes = 35_000_000,
            digest = "sha256:ABCD1234ef56ABCD1234ef56ABCD1234ef56ABCD1234ef56ABCD1234ef56",
            downloadUrl = "https://example.invalid/apk"
        )
    )

    @Test
    fun selectsApkAssetAndIgnoresOthers() {
        val r = UpdateChecker.selectApkAsset("v1.0.22", validAssets)
        assertTrue(r is UpdateChecker.CheckOutcome.Newer)
        val n = r as UpdateChecker.CheckOutcome.Newer
        assertEquals("v1.0.22", n.tag)
        assertEquals("1.0.22", n.version)
        assertEquals(35_000_000L, n.sizeBytes)
        assertEquals("https://example.invalid/apk", n.downloadUrl)
        // Aset .sha256/txt TIDAK boleh terpilih — hanya ZCODE-vX.apk.
        assertFalse(n.sha256.startsWith("66638dad"))
    }

    @Test
    fun stripsDigestPrefixAndNormalizesCase() {
        val n = UpdateChecker.selectApkAsset("v1.0.22", validAssets) as UpdateChecker.CheckOutcome.Newer
        assertEquals(
            "abcd1234ef56abcd1234ef56abcd1234ef56abcd1234ef56abcd1234ef56",
            n.sha256
        )
    }

    @Test
    fun rejectsWhenApkAssetMissing() {
        val noApk = validAssets.map {
            if (it.name == "ZCODE-v1.0.22.apk") it.copy(name = "ZCODE-v1.0.22.apk.bak") else it
        }
        assertNull(UpdateChecker.selectApkAsset("v1.0.22", noApk))
    }

    @Test
    fun rejectsWhenDigestMissingOrNotSha256() {
        // Tanpa digest sama sekali → tolak (D1: tanpa checksum publikasi,
        // integritas tidak bisa diverifikasi).
        val noDigest = validAssets.map {
            if (it.name == "ZCODE-v1.0.22.apk") it.copy(digest = "") else it
        }
        assertNull(UpdateChecker.selectApkAsset("v1.0.22", noDigest))
        // Skema digest selain sha256: → tolak.
        val wrongScheme = validAssets.map {
            if (it.name == "ZCODE-v1.0.22.apk") it.copy(digest = "md5:ABCD1234") else it
        }
        assertNull(UpdateChecker.selectApkAsset("v1.0.22", wrongScheme))
    }

    @Test
    fun rejectsInvalidTagOrNoAssets() {
        assertNull(UpdateChecker.selectApkAsset("v1.0", validAssets))
        assertNull(UpdateChecker.selectApkAsset("latest", validAssets))
        assertNull(UpdateChecker.selectApkAsset("v1.0.22", emptyList()))
        assertNull(UpdateChecker.selectApkAsset("", validAssets))
    }
}
