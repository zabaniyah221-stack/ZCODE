package com.zaba.zcode.core.execution

/**
 * TerminalBuffer — line-oriented buffer terminal (SPEC-001 §15).
 *
 * Bukan satu String raksasa: simpan baris per-baris dengan line index absolut.
 * - current line: baris terakhir yang sedang ditulis (belum \n)
 * - chunked line storage: daftar baris + startOffset (indeks absolut)
 * - line index: get(absIndex) O(1)
 * - loadHistory(range): visibleRange(absStart, absEnd)
 *
 * In-memory buffer dibatasi (maxLines) — full history TIDAK hilang karena
 * RunLogger menulis lengkap ke disk (SPEC-001 Rule 5: full output ≠ full RAM).
 */
class TerminalBuffer(private val maxLines: Int = 10_000) {

    private val lines = ArrayDeque<String>()
    /** Indeks absolut baris pertama di [lines]. */
    var startOffset: Long = 0L
        private set

    /** Total baris yang pernah masuk (termasuk yang sudah di-trim). */
    var totalLines: Long = 0L
        private set

    /** Baris terkini yang belum diakhiri \n (current line). */
    private var current = StringBuilder()

    val lineCount: Int get() = lines.size

    /** Indeks absolut baris terakhir yang tersedia. */
    fun lastLineIndex(): Long = startOffset + lines.size - 1

    fun append(chunk: String) {
        if (chunk.isEmpty()) return
        var i = 0
        while (i < chunk.length) {
            val nl = chunk.indexOf('\n', i)
            if (nl < 0) {
                current.append(chunk, i, chunk.length)
                return
            }
            current.append(chunk, i, nl)
            lines.addLast(current.toString())
            current = StringBuilder()
            totalLines++
            trimHeadIfNeeded()
            i = nl + 1
        }
    }

    private fun trimHeadIfNeeded() {
        while (lines.size > maxLines) {
            lines.removeFirst()
            startOffset++
        }
    }

    /** Ambil baris berdasarkan indeks absolut; null bila di luar window. */
    fun get(absIndex: Long): String? {
        val rel = (absIndex - startOffset).toInt()
        if (rel < 0 || rel >= lines.size) return null
        return lines[rel]
    }

    /** Ambil baris current yang belum diakhiri newline. */
    fun currentLine(): String = current.toString()

    /** Visible/history window [startAbs, endAbs) — loadHistory(SPEC §15). */
    fun visibleRange(startAbs: Long, endAbs: Long): List<String> {
        val out = mutableListOf<String>()
        var idx = startAbs
        while (idx < endAbs) {
            get(idx)?.let { out.add(it) } ?: break
            idx++
        }
        return out
    }

    fun clear() {
        lines.clear()
        current = StringBuilder()
        startOffset = 0
        totalLines = 0
    }
}
