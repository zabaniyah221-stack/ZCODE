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

    fun analyze(
        details: PackageDetails,
        runtime: RuntimeProbe.RuntimeInfo,
        installedVersion: String?
    ): Analysis {
        val reasons = mutableListOf<String>()

        // 1. Status terpasang
        if (installedVersion != null) {
            val newer = details.testedVersion?.let { tv ->
                tv != installedVersion
            } ?: false
            if (newer) {
                return Analysis(
                    PackageStatus.UPDATE_AVAILABLE,
                    listOf("Terpasang $installedVersion; versi tested ZCODE: ${details.testedVersion}"),
                    true
                )
            }
            return Analysis(
                PackageStatus.INSTALLED,
                listOf("Versi $installedVersion terpasang dan aktif."),
                false
            )
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
