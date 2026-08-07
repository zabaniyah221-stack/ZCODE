package com.zaba.zcode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ZCODE colors — port of zabacode/themes/definitions.py + CSS vars
// Fase 0: Retro default #050806 true-black OLED, text-bright #39FF14, ai #FFB000, err #FF4B4B
// Topbar faded grey #3A4452 is NOT editor bg — editor is true-black

private val RetroBg = Color(0xFF050806)
private val RetroTextBright = Color(0xFF39FF14)
private val RetroAi = Color(0xFFFFB000)
private val RetroErr = Color(0xFFFF4B4B)
private val TopbarFadedGrey = Color(0xFF3A4452) // user request: faded grey topbar
private val BgPanel = Color(0xFF0A100D)
private val BgPanel2 = Color(0xFF0F1712)
private val Border = Color(0xFF1B4D2E)

private val DarkColorScheme = darkColorScheme(
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

@Composable
fun ZcodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Fase 0: only dark, light will be added later
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(), // JetBrains Mono will be added via fontFamily
        content = content
    )
}

// Topbar color accessor for tests
object ZcodeColors {
    val TopbarFadedGreyHex = "#3A4452"
    val EditorOledHex = "#050806"
    val TextBrightHex = "#39FF14"
}
