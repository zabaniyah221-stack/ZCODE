package com.zaba.zcode.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// TEST D2 marker
enum class ZcodeThemeType {
    RETRO,
    DRACULA,
    TOKYO_NIGHT
}

@Composable
fun ZcodeTheme(
    themeType: ZcodeThemeType = ZcodeThemeType.RETRO,
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

object ZcodeColors {
    val TopbarFadedGreyHex = "#3A4452"
    val EditorOledHex = "#050806"
    val TextBrightHex = "#39FF14"
}
