package com.zaba.zcode.ui.terminal

import androidx.compose.ui.text.AnnotatedString
import com.zaba.zcode.ui.theme.TerminalPalette

/**
 * AnsiLineCache — incremental ANSI parser (SPEC-001 §14: "Incremental ANSI Parser").
 *
 * Parse per-BARIS dan cache hasil AnnotatedString per indeks baris absolut:
 * append hanya mem-parsing baris baru; scroll tidak pernah re-parse ulang.
 * Bukan full-text reparse tiap frame (praktik lama yang dihapus).
 */
class AnsiLineCache(private val palette: TerminalPalette) {

    private val cache = mutableMapOf<Long, AnnotatedString>()
    private var paletteSignature: String = ""

    /** Render satu baris; hasil di-cache. Bila tema berubah, cache di-reset. */
    fun render(lineIndex: Long, text: String): AnnotatedString {
        if (paletteSignature != paletteSignatureOf()) {
            cache.clear()
            paletteSignature = paletteSignatureOf()
        }
        return cache.getOrPut(lineIndex) { parseAnsiToAnnotatedString(text, palette) }
    }

    /** Baris >= fromAbs berubah (buffer trim) → invalidasi agar tidak stale. */
    fun invalidateFrom(fromAbs: Long) {
        cache.keys.removeAll { it >= fromAbs }
    }

    fun clear() = cache.clear()

    private fun paletteSignatureOf(): String =
        "${palette.foreground.value.toLong()}:${palette.ansiColors.joinToString(",") { it.value.toLong().toString() }}"
}
