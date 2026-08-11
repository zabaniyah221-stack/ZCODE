package com.zaba.zcode.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * ZIcons — ikon vektor polos ZCODE (keputusan redesign 2026-08).
 *
 * Digambar manual dari path 24dp — TANPA dependensi material-icons (APK tetap
 * ramping & offline-first, nol KB tambahan). Warna TIDAK di-hardcode di sini:
 * semua ikon di-tint di tempat pakai (umumnya `onSurface`) → otomatis mengikuti
 * tema aktif (Retro/Dracula/Tokyo Night), seragam di semua merk HP — menghapus
 * inkonsistensi bentuk emoji antar OEM (keputusan "polos + ikut tema" user).
 */
object ZIcons {

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black) // selalu ditimpa tint Icon() di tempat pakai
        ).build()

    /** 🔍 lama → kaca pembesar polos (palette: go-to-line & find). */
    val Search: ImageVector = icon(
        "Search",
        "M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 " +
            "6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5z" +
            "M9.5 14C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"
    )

    /** "+" lama → plus polos (buat file baru). */
    val Add: ImageVector = icon("Add", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")

    /** FAB ▶ polos — segitiga run. */
    val Play: ImageVector = icon("Play", "M8 5v14l11-7z")

    /** Ikon folder topbar — menu file (Open/Save/Save as, audit 2026-08). */
    val Folder: ImageVector = icon(
        "Folder",
        "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8" +
            "c0-1.1-.9-2-2-2h-8l-2-2z"
    )

    /** Floppy disk polos — Save (timpa file asli di device). */
    val Save: ImageVector = icon(
        "Save",
        "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4z" +
            "m-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z"
    )

    /** Folder + plus polos — Save as (file device baru via SAF). */
    val SaveAs: ImageVector = icon(
        "SaveAs",
        "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8" +
            "c0-1.1-.9-2-2-2zm-1 8h-3v3h-2v-3h-3v-2h3V9h2v3h3v2z"
    )
}
