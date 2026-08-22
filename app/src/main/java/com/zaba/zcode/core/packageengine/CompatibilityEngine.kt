package com.zaba.zcode.core.packageengine

import android.content.Context

/**
 * CompatibilityEngine — analisis kompatibilitas package (SPEC-001 §4, §5, §11).
 *
 * Rule 2 SPEC: jangan bilang "Compatible" hanya karena package ada di PyPI.
 * Pertimbangan: Python version, Android ABI, API level, wheel/native deps.
 * Katalog = knowledge base: INCOMPATIBLE/UNAVAILABLE TETAP tampil + dijelaskan.
 */
class CompatibilityEngine(private val context: Context) {

    data class Analysis(
        val status: PackageStatus,
        val reasons: List<String>,
        val testable: Boolean
    )

    private data class ParsedVersion(
        val release: List<Int>,
        val phase: Int,
        val preRank: Int,
        val phaseNumber: Int,
    )

    /**
     * Conservative comparator for the PEP 440 forms used by the curated
     * catalog: numeric releases plus a/b/rc, .dev, .post and ignored local tags.
     * Unknown forms return null and never produce a false UPDATE_AVAILABLE.
     */
    private fun comparePythonVersions(left: String, right: String): Int? {
        fun parse(raw: String): ParsedVersion? {
            val normalized = raw.trim().lowercase().removePrefix("v")
                .substringBefore('+')
                .replace("-", ".")
            val match = Regex(
                "^(\\d+(?:\\.\\d+)*)(?:(a|b|rc)(\\d*))?(?:\\.?(dev|post)(\\d*))?$"
            ).matchEntire(normalized) ?: return null
            val release = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
            val pre = match.groupValues[2]
            val preNumber = match.groupValues[3].toIntOrNull() ?: 0
            val suffix = match.groupValues[4]
            val suffixNumber = match.groupValues[5].toIntOrNull() ?: 0
            return when {
                suffix == "dev" -> ParsedVersion(release, -2, 0, suffixNumber)
                pre.isNotEmpty() -> ParsedVersion(
                    release,
                    -1,
                    when (pre) { "a" -> 0; "b" -> 1; else -> 2 },
                    preNumber,
                )
                suffix == "post" -> ParsedVersion(release, 1, 0, suffixNumber)
                else -> ParsedVersion(release, 0, 0, 0)
            }
        }
        val a = parse(left) ?: return null
        val b = parse(right) ?: return null
        val width = maxOf(a.release.size, b.release.size)
        for (index in 0 until width) {
            val partA = a.release.getOrElse(index) { 0 }
            val partB = b.release.getOrElse(index) { 0 }
            if (partA != partB) return partA.compareTo(partB)
        }
        if (a.phase != b.phase) return a.phase.compareTo(b.phase)
        if (a.preRank != b.preRank) return a.preRank.compareTo(b.preRank)
        return a.phaseNumber.compareTo(b.phaseNumber)
    }

    fun analyze(
        details: PackageDetails,
        runtime: RuntimeProbe.RuntimeInfo,
        installedVersion: String?
    ): Analysis {
        val reasons = mutableListOf<String>()

        // 1. Status terpasang
        if (installedVersion != null) {
            val tested = details.testedVersion
            val ordering = tested?.let { comparePythonVersions(it, installedVersion) }
            if (ordering != null && ordering > 0) {
                return Analysis(
                    PackageStatus.UPDATE_AVAILABLE,
                    listOf("Terpasang $installedVersion; versi tested ZCODE lebih baru: $tested"),
                    true
                )
            }
            val reason = when {
                ordering == null && tested != null && tested != installedVersion ->
                    "Versi $installedVersion terpasang; ordering terhadap tested $tested tidak dapat dipastikan."
                ordering != null && ordering < 0 ->
                    "Versi $installedVersion terpasang dan lebih baru dari tested ZCODE $tested."
                else -> "Versi $installedVersion terpasang dan aktif."
            }
            return Analysis(PackageStatus.INSTALLED, listOf(reason), false)
        }

        // 2. Status eksplisit dari katalog
        when (details.status) {
            PackageStatus.INCOMPATIBLE -> return Analysis(
                PackageStatus.INCOMPATIBLE,
                listOf("Known limitation: ${details.description.take(160)}") + details.doesNotWork,
                false
            )
            PackageStatus.UNAVAILABLE -> return Analysis(
                PackageStatus.UNAVAILABLE,
                listOf("Belum ada artifact yang acceptable untuk runtime ini."),
                false
            )
            else -> Unit
        }

        // 3. Cek Python version
        if (details.python.isNotEmpty()) {
            val runtimeMinor = runtime.pythonVersion.takeWhile { it.isDigit() || it == '.' }
            if (details.python.none { runtimeMinor.startsWith(it) }) {
                return Analysis(
                    PackageStatus.INCOMPATIBLE,
                    listOf("Membutuhkan Python ${details.python.joinToString("/")}, runtime ZCODE: ${runtime.pythonVersion}"),
                    false
                )
            }
        }

        // 4. Cek ABI (untuk native)
        if (details.type == "native" && details.abis.isNotEmpty()) {
            val overlap = details.abis.any { runtime.abis.contains(it) }
            if (!overlap) {
                return Analysis(
                    PackageStatus.INCOMPATIBLE,
                    listOf(
                        "Wheel native hanya untuk ABI ${details.abis.joinToString(", ")}; " +
                            "device ini: ${runtime.abis.joinToString(", ")}."
                    ),
                    false
                )
            }
            reasons.add("ABI device (${runtime.abis.joinToString(", ")}) didukung.")
        }

        // 5. Status katalog yang bisa diinstall
        return when (details.status) {
            PackageStatus.TESTED -> Analysis(
                PackageStatus.TESTED,
                reasons + listOf("Versi tested ZCODE: ${details.testedVersion ?: "?"}"),
                true
            )
            PackageStatus.COMPATIBLE, PackageStatus.NOT_REVIEWED -> Analysis(
                PackageStatus.COMPATIBLE,
                reasons + listOf("Artifact kandidat tampak kompatibel; belum smoke-tested penuh oleh ZCODE."),
                true
            )
            PackageStatus.EXPERIMENTAL -> Analysis(
                PackageStatus.EXPERIMENTAL,
                reasons + listOf("Belum diverifikasi — install eksperimental dengan risiko."),
                true
            )
            PackageStatus.BUILT_IN -> Analysis(
                PackageStatus.BUILT_IN,
                listOf("Sudah tersedia di runtime (stdlib/bundled)."),
                false
            )
            else -> Analysis(
                PackageStatus.COMPATIBLE,
                reasons + listOf("Belum direview."),
                true
            )
        }
    }
}
