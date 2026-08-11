package com.zaba.zcode.core.packageengine

/**
 * PackageStatus — status model package (SPEC-001 §4).
 *
 * Rules:
 * - TESTED      = install + dependency + import + smoke test passed (oleh ZCODE)
 * - COMPATIBLE  = artifact kandidat tampak kompatibel, belum dieksekusi penuh
 * - INCOMPATIBLE= known runtime/platform limitation
 * - UNAVAILABLE = tidak ada artifact yang acceptable
 * - TESTED TIDAK berarti security audited.
 */
enum class PackageStatus(val label: String) {
    BUILT_IN("Built-in"),
    TESTED("Tested"),
    COMPATIBLE("Compatible"),
    EXPERIMENTAL("Experimental"),
    NOT_REVIEWED("Not Reviewed"),
    INCOMPATIBLE("Incompatible"),
    UNAVAILABLE("Unavailable"),
    INSTALLED("Installed"),
    BROKEN("Broken"),
    UPDATE_AVAILABLE("Update Available");

    fun installable(): Boolean = this == TESTED || this == COMPATIBLE || this == EXPERIMENTAL

    companion object {
        fun fromString(s: String?): PackageStatus? {
            if (s == null) return null
            return entries.firstOrNull { it.name == s.trim().uppercase() }
        }
    }
}
