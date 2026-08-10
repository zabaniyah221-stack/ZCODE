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
    TOKYO_NIGHT,
    SOLARIZED_DARK,
    MONOKAI,
    NORD,
    ONE_DARK,
    GRUVBOX_DARK,
    GITHUB_DARK,
    COBALT2
}

data class TerminalPalette(
    val name: String,
    val foreground: Color,
    val background: Color,
    val ansiColors: List<Color>
)

fun getTerminalPalette(theme: ZcodeThemeType): TerminalPalette {
    return when (theme) {
        ZcodeThemeType.RETRO -> TerminalPalette(
            name = "Phosphor (Retro)",
            foreground = Color(0xFF39FF14),
            background = Color(0xFF000000),
            ansiColors = listOf(
                Color(0xFF000000), Color(0xFFFF4B4B), Color(0xFF39FF14), Color(0xFFFFB000),
                Color(0xFF4B8BFF), Color(0xFFFF4BD8), Color(0xFF4BFFD8), Color(0xFFC8FFC8),
                Color(0xFF4A4A4A), Color(0xFFFF6B6B), Color(0xFF5DFF3B), Color(0xFFFFD03B),
                Color(0xFF7B9BFF), Color(0xFFFF7BDD), Color(0xFF7BFFE1), Color(0xFFE8FFE8)
            )
        )
        ZcodeThemeType.DRACULA -> TerminalPalette(
            name = "Dracula",
            foreground = Color(0xFFF8F8F2),
            background = Color(0xFF000000),
            ansiColors = listOf(
                Color(0xFF21222C), Color(0xFFFF5555), Color(0xFF50FA7B), Color(0xFFF1FA8C),
                Color(0xFFBD93F9), Color(0xFFFF79C6), Color(0xFF8BE9FD), Color(0xFFF8F8F2),
                Color(0xFF6272A4), Color(0xFFFF6E6E), Color(0xFF69FF94), Color(0xFFFFFFA5),
                Color(0xFFD6ACFF), Color(0xFFFF92DF), Color(0xFFA4FFFF), Color(0xFFFFFFFF)
            )
        )
        ZcodeThemeType.TOKYO_NIGHT -> TerminalPalette(
            name = "Tokyo Night",
            foreground = Color(0xFFC0CAF5),
            background = Color(0xFF000000),
            ansiColors = listOf(
                Color(0xFF1D202F), Color(0xFFF7768E), Color(0xFF9ECE6A), Color(0xFFE0AF68),
                Color(0xFF7AA2F7), Color(0xFFBB9AF7), Color(0xFF7DCFFF), Color(0xFFA9B1D6),
                Color(0xFF414868), Color(0xFFFF7A93), Color(0xFFB9F27C), Color(0xFFFF9E64),
                Color(0xFF7DA6FF), Color(0xFFC0CAF5), Color(0xFF0DB9D7), Color(0xFFC0CAF5)
            )
        )
        ZcodeThemeType.SOLARIZED_DARK -> TerminalPalette(
            name = "Solarized Dark",
            foreground = Color(0xFF93A1A1),
            background = Color(0xFF000000),
            ansiColors = listOf(
                Color(0xFF073642), Color(0xFFDC322F), Color(0xFF859900), Color(0xFFB58900),
                Color(0xFF268BD2), Color(0xFFD33682), Color(0xFF2AA198), Color(0xFFEEE8D5),
                Color(0xFF002B36), Color(0xFFCB4B16), Color(0xFF586E75), Color(0xFF657B83),
                Color(0xFF839496), Color(0xFF6C71C4), Color(0xFF93A1A1), Color(0xFFFDF6E3)
            )
        )
        ZcodeThemeType.MONOKAI -> TerminalPalette(
            name = "Monokai",
            foreground = Color(0xFFF8F8F2),
            background = Color(0xFF000000),
            ansiColors = listOf(
                Color(0xFF272822), Color(0xFFF92672), Color(0xFFA6E22E), Color(0xFFF4BF75),
                Color(0xFF66D9EF), Color(0xFFAE81FF), Color(0xFFA1EFE4), Color(0xFFF8F8F2),
                Color(0xFF75715E), Color(0xFFFD971F), Color(0xFFA6E22E), Color(0xFFF4BF75),
                Color(0xFF66D9EF), Color(0xFFAE81FF), Color(0xFFA1EFE4), Color(0xFFF9F8F5)
            )
        )
        ZcodeThemeType.NORD -> TerminalPalette(
            name = "Nord",
            foreground = Color(0xFFD8DEE9),
            background = Color(0xFF000000),
            ansiColors = listOf(
                Color(0xFF2E3440), Color(0xFFBF616A), Color(0xFFA3BE8C), Color(0xFFEBCB8B),
                Color(0xFF81A1C1), Color(0xFFB48EAD), Color(0xFF88C0D0), Color(0xFFE5E9F0),
                Color(0xFF4C566A), Color(0xFFBF616A), Color(0xFFA3BE8C), Color(0xFFEBCB8B),
                Color(0xFF81A1C1), Color(0xFFB48EAD), Color(0xFF8FBCBB), Color(0xFFECEFF4)
            )
        )
        ZcodeThemeType.ONE_DARK -> TerminalPalette(
            name = "One Dark",
            foreground = Color(0xFFABB2BF),
            background = Color(0xFF000000),
            ansiColors = listOf(
                Color(0xFF282C34), Color(0xFFE06C75), Color(0xFF98C379), Color(0xFFD19A66),
                Color(0xFF61AFEF), Color(0xFFC678DD), Color(0xFF56B6C2), Color(0xFFABB2BF),
                Color(0xFF5C6370), Color(0xFFE06C75), Color(0xFF98C379), Color(0xFFD19A66),
                Color(0xFF61AFEF), Color(0xFFC678DD), Color(0xFF56B6C2), Color(0xFFFFFFFF)
            )
        )
        ZcodeThemeType.GRUVBOX_DARK -> TerminalPalette(
            name = "Gruvbox Dark",
            foreground = Color(0xFFEBDBB2),
            background = Color(0xFF000000),
            ansiColors = listOf(
                Color(0xFF282828), Color(0xFFCC241D), Color(0xFF98971A), Color(0xFFD79921),
                Color(0xFF458588), Color(0xFFB16286), Color(0xFF689D6A), Color(0xFFA89984),
                Color(0xFF928374), Color(0xFFFB4934), Color(0xFFB8BB26), Color(0xFFFABD2F),
                Color(0xFF83A598), Color(0xFFD3869B), Color(0xFF8EC07C), Color(0xFFEBDBB2)
            )
        )
        ZcodeThemeType.GITHUB_DARK -> TerminalPalette(
            name = "Github Dark",
            foreground = Color(0xFFC9D1D9),
            background = Color(0xFF000000),
            ansiColors = listOf(
                Color(0xFF161B22), Color(0xFFF85149), Color(0xFF56D364), Color(0xFFE3B341),
                Color(0xFF58A6FF), Color(0xFFBC8CFF), Color(0xFF39C5CF), Color(0xFF8B949E),
                Color(0xFF484F58), Color(0xFFFF7B72), Color(0xFF7EE787), Color(0xFFF2CC60),
                Color(0xFF79C0FF), Color(0xFFD2A8FF), Color(0xFF56D4DD), Color(0xFFC9D1D9)
            )
        )
        ZcodeThemeType.COBALT2 -> TerminalPalette(
            name = "Cobalt2",
            foreground = Color(0xFFFFFFFF),
            background = Color(0xFF000000),
            ansiColors = listOf(
                Color(0xFF000000), Color(0xFFFF000D), Color(0xFF3BFF60), Color(0xFFFFE200),
                Color(0xFF1452FF), Color(0xFFE514FF), Color(0xFF00E1FF), Color(0xFFBBBBBB),
                Color(0xFF555555), Color(0xFFFF000D), Color(0xFF3BFF60), Color(0xFFFFE200),
                Color(0xFF1452FF), Color(0xFFE514FF), Color(0xFF00E1FF), Color(0xFFFFFFFF)
            )
        )
    }
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

private val SolarizedDarkColorScheme = darkColorScheme(
    primary = Color(0xFF2AA198),
    onPrimary = Color(0xFF002B36),
    primaryContainer = Color(0xFF073642),
    background = Color(0xFF002B36),
    surface = Color(0xFF002B36),
    surfaceVariant = Color(0xFF073642),
    error = Color(0xFFDC322F),
    tertiary = Color(0xFFB58900),
    outline = Color(0xFF586E75)
)

private val MonokaiColorScheme = darkColorScheme(
    primary = Color(0xFFF92672),
    onPrimary = Color(0xFF272822),
    primaryContainer = Color(0xFF3E3D32),
    background = Color(0xFF272822),
    surface = Color(0xFF272822),
    surfaceVariant = Color(0xFF3E3D32),
    error = Color(0xFFF92672),
    tertiary = Color(0xFFFD971F),
    outline = Color(0xFF75715E)
)

private val NordColorScheme = darkColorScheme(
    primary = Color(0xFF88C0D0),
    onPrimary = Color(0xFF2E3440),
    primaryContainer = Color(0xFF3B4252),
    background = Color(0xFF2E3440),
    surface = Color(0xFF2E3440),
    surfaceVariant = Color(0xFF3B4252),
    error = Color(0xFFBF616A),
    tertiary = Color(0xFFD08770),
    outline = Color(0xFF4C566A)
)

private val OneDarkColorScheme = darkColorScheme(
    primary = Color(0xFF61AFEF),
    onPrimary = Color(0xFF282C34),
    primaryContainer = Color(0xFF3E4452),
    background = Color(0xFF282C34),
    surface = Color(0xFF282C34),
    surfaceVariant = Color(0xFF3E4452),
    error = Color(0xFFE06C75),
    tertiary = Color(0xFFD19A66),
    outline = Color(0xFF5C6370)
)

private val GruvboxDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFABD2F),
    onPrimary = Color(0xFF282828),
    primaryContainer = Color(0xFF3C3836),
    background = Color(0xFF282828),
    surface = Color(0xFF282828),
    surfaceVariant = Color(0xFF3C3836),
    error = Color(0xFFFB4934),
    tertiary = Color(0xFFFE8019),
    outline = Color(0xFF7C6F64)
)

private val GithubDarkColorScheme = darkColorScheme(
    primary = Color(0xFF58A6FF),
    onPrimary = Color(0xFF0D1117),
    primaryContainer = Color(0xFF161B22),
    background = Color(0xFF0D1117),
    surface = Color(0xFF0D1117),
    surfaceVariant = Color(0xFF161B22),
    error = Color(0xFFF85149),
    tertiary = Color(0xFFF0883E),
    outline = Color(0xFF30363D)
)

private val Cobalt2ColorScheme = darkColorScheme(
    primary = Color(0xFFFFC600),
    onPrimary = Color(0xFF193549),
    primaryContainer = Color(0xFF152C3E),
    background = Color(0xFF193549),
    surface = Color(0xFF193549),
    surfaceVariant = Color(0xFF152C3E),
    error = Color(0xFFFF000D),
    tertiary = Color(0xFFFF9D00),
    outline = Color(0xFF005080)
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
        ZcodeThemeType.SOLARIZED_DARK -> SolarizedDarkColorScheme
        ZcodeThemeType.MONOKAI -> MonokaiColorScheme
        ZcodeThemeType.NORD -> NordColorScheme
        ZcodeThemeType.ONE_DARK -> OneDarkColorScheme
        ZcodeThemeType.GRUVBOX_DARK -> GruvboxDarkColorScheme
        ZcodeThemeType.GITHUB_DARK -> GithubDarkColorScheme
        ZcodeThemeType.COBALT2 -> Cobalt2ColorScheme
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
