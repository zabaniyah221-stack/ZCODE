package com.zaba.zcode.core.packageengine

import android.content.Context
import com.zaba.zcode.core.files.Paths
import java.io.File

/**
 * InstalledPackages — pembaca ringan `python-env/state/installed.json`
 * (v1.0.19, Gerbong B: jembatan requiresPackage).
 *
 * Dipakai SamplesScreen untuk menjawab "paket X sudah aktif belum?" SEBELUM
 * membuat file sample — tanpa menyeret PackageEngineV2 (yang punya lock
 * operasi & state berat) ke layer UI.
 *
 * Kontrak data sama dengan PackageEngineV2.activeInstalledVersions():
 * key = canonicalName; entri hanya ada bila paket pernah lolos smoke +
 * activate. Best-effort: kegagalan baca = set kosong (dialog akan muncul,
 * user paling buruk diarahkan ke INSTALL MODULES padahal sudah terpasang —
 * lebih aman daripada sample crash).
 */
object InstalledPackages {

    fun activeNames(context: Context): Set<String> = try {
        val stateFile = File(Paths.pythonState(context), "installed.json")
        if (!stateFile.exists()) {
            emptySet()
        } else {
            val obj = org.json.JSONObject(stateFile.readText())
            obj.keys().asSequence().map { it.lowercase() }.toSet()
        }
    } catch (e: Exception) {
        emptySet()
    }

    /** Nama paket dari [required] yang BELUM aktif (canonical, lowercase). */
    fun missingFrom(context: Context, required: List<String>): List<String> {
        if (required.isEmpty()) return emptyList()
        val active = activeNames(context)
        return required.map { it.lowercase() }.filter { it !in active }
    }
}
