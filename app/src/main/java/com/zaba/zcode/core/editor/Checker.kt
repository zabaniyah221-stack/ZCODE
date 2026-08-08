package com.zaba.zcode.core.editor

/**
 * Checker — offline lightweight Python syntax validator (Fase 2).
 * Single-pass scanner, tanpa parser berat:
 * - strip komentar (#) & string literal (termasuk triple-quoted, escape, f-string prefix)
 * - cek keseimbangan tanda kurung () [] {} dengan nomor baris
 * - B-11 fix: `print(' :)')` TIDAK boleh dianggap error (string di-strip dulu)
 * - B-19 fix: `async def` aman (tidak ada parsing AST, jadi tidak "invisible")
 * - F-07 fix: tanpa prelude injection — analisis langsung terhadap kode user
 */
object Checker {

    /**
     * Ganti semua komentar & string literal dengan spasi (baris baru dipertahankan
     * agar nomor baris tetap akurat). Karakter di dalam string/komentar tidak ikut
     * dianalisis bracket.
     */
    fun stripCommentsAndStrings(code: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = code.length

        while (i < len) {
            val char = code[i]

            // Triple-quoted string: """ atau '''
            if (i + 2 < len) {
                val triple = code.substring(i, i + 3)
                if (triple == "\"\"\"" || triple == "'''") {
                    sb.append("   ")
                    i += 3
                    while (i < len) {
                        if (i + 2 < len && code.substring(i, i + 3) == triple) {
                            sb.append("   ")
                            i += 3
                            break
                        }
                        val c = code[i]
                        if (c == '\n') sb.append('\n') else sb.append(' ')
                        i++
                    }
                    continue
                }
            }

            // Single-line string: '...' atau "..."
            if (char == '"' || char == '\'') {
                val quote = char
                sb.append(' ')
                i++
                while (i < len) {
                    val c = code[i]
                    if (c == '\\') {
                        sb.append("  ")
                        i += 2
                        continue
                    }
                    if (c == quote) {
                        sb.append(' ')
                        i++
                        break
                    }
                    if (c == '\n') sb.append('\n') else sb.append(' ')
                    i++
                }
                continue
            }

            // Komentar baris
            if (char == '#') {
                while (i < len && code[i] != '\n') {
                    sb.append(' ')
                    i++
                }
                continue
            }

            sb.append(char)
            i++
        }
        return sb.toString()
    }

    /** Deteksi string literal satu baris yang tidak pernah ditutup. */
    fun unterminatedStringLine(code: String): Int? {
        var i = 0
        val len = code.length
        var lineNum = 1

        while (i < len) {
            val char = code[i]

            if (char == '\n') {
                lineNum++
                i++
                continue
            }

            // Triple-quoted dianggap aman (bisa multi-baris & dibiarkan oleh strip)
            if (i + 2 < len) {
                val triple = code.substring(i, i + 3)
                if (triple == "\"\"\"" || triple == "'''") {
                    i += 3
                    while (i < len) {
                        if (i + 2 < len && code.substring(i, i + 3) == triple) {
                            i += 3
                            break
                        }
                        if (code[i] == '\n') lineNum++
                        i++
                    }
                    continue
                }
            }

            if (char == '"' || char == '\'') {
                val quote = char
                i++
                var closed = false
                while (i < len) {
                    val c = code[i]
                    if (c == '\\') { i += 2; continue }
                    if (c == quote) { closed = true; i++; break }
                    if (c == '\n') break // string satu baris menyentuh baris baru tanpa tutup
                    i++
                }
                if (!closed) return lineNum
                continue
            }

            i++
        }
        return null
    }

    /** Cek keseimbangan tanda kurung terhadap kode yang sudah di-strip. */
    fun checkBrackets(code: String): String? {
        val stripped = stripCommentsAndStrings(code)
        val stack = mutableListOf<Pair<Char, Int>>()

        var lineNum = 1
        for (ch in stripped) {
            if (ch == '\n') {
                lineNum++
                continue
            }
            when (ch) {
                '(', '[', '{' -> stack.add(ch to lineNum)
                ')', ']', '}' -> {
                    if (stack.isEmpty()) {
                        return "Unexpected closed bracket '$ch' on line $lineNum"
                    }
                    val (open, openLine) = stack.removeAt(stack.size - 1)
                    val match = when (ch) {
                        ')' -> open == '('
                        ']' -> open == '['
                        else -> open == '{'
                    }
                    if (!match) {
                        return "Mismatched bracket on line $lineNum: '$ch' does not match '$open' on line $openLine"
                    }
                }
            }
        }

        if (stack.isNotEmpty()) {
            val (open, openLine) = stack.last()
            return "Unbalanced brackets: '$open' on line $openLine has no matching closed bracket"
        }
        return null
    }

    /** Entry point diagnostik real-time: error sintaksis atau null. */
    fun checkSyntax(code: String): String? {
        unterminatedStringLine(code)?.let { line ->
            return "Unterminated string literal on line $line"
        }
        return checkBrackets(code)
    }
}
