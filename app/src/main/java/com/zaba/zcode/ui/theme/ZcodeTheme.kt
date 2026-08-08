package com.zaba.zcode.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ZCODE themes — port of zabacode/themes/definitions.py + CSS vars.
 * Keputusan tim:
 * - Editor & terminal output SELALU true-black OLED #050806 (independen dari tema).
 * - Tema hanya menyentuh bagian dekoratif: topbar, drawer, dialog, tombol, aksen.
 * - Topbar faded grey #3A4452 dipertahankan sebagai warna referensi (anti-regresi),
 *   tapi dipakai dengan opacity/elevasi halus, bukan blok warna datar (user request: pembatas
 *   visual jangan terlalu sharp).
 */
enum class ZcodeThemeType {
    RETRO,
    DRACULA,
    TOKYO_NIGHT
}

// ---- Retro (default, identitas Zabacode: phosphor green di atas OLED) ----
private val RetroBg = Color(0xFF050806)
private val RetroTextBright = Color(0xFF39FF14)
private val RetroAi = Color(0xFFFFB000)
private val RetroErr = Color(0xFFFF4B4B)
private val TopbarFadedGrey = Color(0xFF3A4452) // user request: faded grey topbar
private val BgPanel = Color(0xFF0A100D)
private val BgPanel2 = Color(0xFF0F1712)
private val Border = Color(0xFF1B4D2E)

// ---- Dracula ----
private val DraculaBg = Color(0xFF282A36)
private val DraculaCurrentLine = Color(0xFF44475A)
private val DraculaForeground = Color(0xFFF8F8F2)
private val DraculaComment = Color(0xFF6272A4)
private val DraculaPurple = Color(0xFFBD93F9)
private val DraculaGreen = Color(0xFF50FA7B)
private val DraculaOrange = Color(0xFFFFB86C)
private val DraculaRed = Color(0xFFFF5555)

// ---- Tokyo Night ----
private val TokyoBg = Color(0xFF1A1B26)
private val TokyoTerminalBg = Color(0xFF16161E)
private val TokyoBlue = Color(0xFF7AA2F7)
private val TokyoGreen = Color(0xFF9ECE6A)
private val TokyoOrange = Color(0xFFFF9E64)
private val TokyoRed = Color(0xFFF7768E)
private val TokyoOutline = Color(0xFF383E5A)

private val RetroColorScheme = darkColorScheme(
    primary = RetroTextBright,
    onPrimary = RetroBg,
    primaryContainer = BgPanel,
    background = RetroBg,
    surface = RetroBg,
    surfaceVariant = BgPanel,
    error = RetroErr,
    tertiary = RetroAi,
    outline = Border
)

private val DraculaColorScheme = darkColorScheme(
    primary = DraculaPurple,
    onPrimary = DraculaBg,
    primaryContainer = DraculaCurrentLine,
    background = DraculaBg,
    surface = DraculaBg,
    surfaceVariant = DraculaCurrentLine,
    error = DraculaRed,
    tertiary = DraculaOrange,
    outline = DraculaComment
)

private val TokyoColorScheme = darkColorScheme(
    primary = TokyoBlue,
    onPrimary = TokyoBg,
    primaryContainer = TokyoTerminalBg,
    background = TokyoBg,
    surface = TokyoBg,
    surfaceVariant = TokyoTerminalBg,
    error = TokyoRed,
    tertiary = TokyoOrange,
    outline = TokyoOutline
)

@Composable
fun ZcodeTheme(
    themeType: ZcodeThemeType = ZcodeThemeType.RETRO,
    content: @Composable () -> Unit
) {
    val colors = when (themeType) {
        ZcodeThemeType.RETRO -> RetroColorScheme
        ZcodeThemeType.DRACULA -> DraculaColorScheme
        ZcodeThemeType.TOKYO_NIGHT -> TokyoColorScheme
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}

/** Warna akses untuk komponen & test (anti-regresi). */
object ZcodeColors {
    val TopbarFadedGreyHex = "#3A4452"
    val EditorOledHex = "#050806"
    val TextBrightHex = "#39FF14"
    val ErrorRedHex = "#FF4B4B"
}
