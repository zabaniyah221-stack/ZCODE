package com.zaba.zcode.core.plugins

import com.zaba.zcode.core.editor.Checker

/**
 * PluginHost — plugin transformasi teks (Fase 2).
 *
 * Beautifier (B-10 fix, longest-first):
 * - Operator multi-karakter dilindungi placeholder dulu (`->`, `**`, `//=`, `<<`, ...),
 *   jadi `def f() -> int:` tidak akan pernah rusak menjadi `def f() - > int:`.
 * - String literal & komentar TIDAK pernah disentuh (scanner segmen literal/kode),
 *   jadi `print("a==b")` aman dan `print(' :)')` tidak rusak.
 * - Unary minus/plus/tilde/star (`-1`, `*args`, `~x`) tidak diberi spasi.
 * - `**kwargs` / `f(**d)` / `{**d}` (unpacking) dijaga tanpa spasi setelah `**`.
 * - Spasi antar-segmen: karakter terakhir segmen sebelumnya disambungkan (prev threading),
 *   sehingga `"%d"%x` → `"%d" % x` (tanpa spasi dobel) dan indent baris tetap dipertahankan.
 * - Konservatif: normalisasi spasi operator SAJA — tidak menyentuh titik/komentar/struktur.
 */
object PluginHost {

    // Operator multi-karakter → token placeholder (longest-first, urutan penting: **= sebelum **)
    private val protectOps = listOf(
        "**=" to "\u00A7POWEQ\u00A7",
        "//=" to "\u00A7FLOORDIV_EQ\u00A7",
        "<<=" to "\u00A7SHL_EQ\u00A7",
        ">>=" to "\u00A7SHR_EQ\u00A7",
        "->" to "\u00A7ARROW\u00A7",
        ":=" to "\u00A7WALRUS\u00A7",
        "==" to "\u00A7EQ\u00A7",
        "!=" to "\u00A7NE\u00A7",
        "<=" to "\u00A7LE\u00A7",
        ">=" to "\u00A7GE\u00A7",
        "+=" to "\u00A7ADD_EQ\u00A7",
        "-=" to "\u00A7SUB_EQ\u00A7",
        "*=" to "\u00A7MUL_EQ\u00A7",
        "/=" to "\u00A7DIV_EQ\u00A7",
        "%=" to "\u00A7MOD_EQ\u00A7",
        "&=" to "\u00A7AND_EQ\u00A7",
        "|=" to "\u00A7OR_EQ\u00A7",
        "^=" to "\u00A7XOR_EQ\u00A7",
        "**" to "\u00A7POW\u00A7",
        "//" to "\u00A7FLOORDIV\u00A7",
        "<<" to "\u00A7SHL\u00A7",
        ">>" to "\u00A7SHR\u00A7",
        "..." to "\u00A7ELLIPSIS\u00A7"
    )

    // Pemulihan: operator binary diberi spasi kanonik; ** (pangkat/unpacking) & ... (ellipsis) verbatim
    private val restoreOps = listOf(
        "\u00A7POWEQ\u00A7" to " **= ",
        "\u00A7FLOORDIV_EQ\u00A7" to " //= ",
        "\u00A7SHL_EQ\u00A7" to " <<= ",
        "\u00A7SHR_EQ\u00A7" to " >>= ",
        "\u00A7ARROW\u00A7" to " -> ",
        "\u00A7WALRUS\u00A7" to " := ",
        "\u00A7EQ\u00A7" to " == ",
        "\u00A7NE\u00A7" to " != ",
        "\u00A7LE\u00A7" to " <= ",
        "\u00A7GE\u00A7" to " >= ",
        "\u00A7ADD_EQ\u00A7" to " += ",
        "\u00A7SUB_EQ\u00A7" to " -= ",
        "\u00A7MUL_EQ\u00A7" to " *= ",
        "\u00A7DIV_EQ\u00A7" to " /= ",
        "\u00A7MOD_EQ\u00A7" to " %= ",
        "\u00A7AND_EQ\u00A7" to " &= ",
        "\u00A7OR_EQ\u00A7" to " |= ",
        "\u00A7XOR_EQ\u00A7" to " ^= ",
        "\u00A7FLOORDIV\u00A7" to " // ",
        "\u00A7SHL\u00A7" to " << ",
        "\u00A7SHR\u00A7" to " >> ",
        "\u00A7POW\u00A7" to " ** ", // binary ** diberi spasi, unpacking diperbaiki setelahnya
        "\u00A7ELLIPSIS\u00A7" to "..."
    )

    private val unaryOkAfter = setOf('(', '[', '{', ',', ':', ';', '=', '\u00A7')
    private val singleOps = setOf('+', '-', '*', '/', '%', '&', '|', '^', '<', '>', '=', '~')
    private val noSpaceAfter = setOf(' ', ')', ']', '}', ',', ':', '\n', '\t')

    private class Seg(val text: String, val isLiteral: Boolean)

    /** Scanner seluruh dokumen: pisahkan literal (string/komentar) vs kode. */
    private fun scanSegments(code: String): List<Seg> {
        val out = mutableListOf<Seg>()
        val codeSb = StringBuilder()
        val litSb = StringBuilder()
        var i = 0
        val n = code.length
        var inString: Char? = null
        var inTriple: String? = null

        fun flushCode() {
            if (codeSb.isNotEmpty()) {
                out.add(Seg(codeSb.toString(), false))
                codeSb.clear()
            }
        }

        fun flushLit() {
            if (litSb.isNotEmpty()) {
                flushCode()
                out.add(Seg(litSb.toString(), true))
                litSb.clear()
            }
        }

        while (i < n) {
            val c = code[i]
            when {
                inTriple != null -> {
                    litSb.append(c)
                    if (i + 2 < n && code.substring(i, i + 3) == inTriple) {
                        litSb.append(code[i + 1])
                        litSb.append(code[i + 2])
                        i += 3
                        inTriple = null
                    } else {
                        i++
                    }
                }
                inString != null -> {
                    litSb.append(c)
                    if (c == '\\' && i + 1 < n) {
                        litSb.append(code[i + 1])
                        i += 2
                    } else {
                        if (c == inString) inString = null
                        i++
                    }
                }
                c == '"' || c == '\'' -> {
                    if (i + 2 < n) {
                        val triple = code.substring(i, i + 3)
                        if (triple == "\"\"\"" || triple == "'''") {
                            flushLit()
                            inTriple = triple
                            litSb.append(triple)
                            i += 3
                            continue
                        }
                    }
                    flushLit()
                    inString = c
                    litSb.append(c)
                    i++
                }
                c == '#' -> {
                    flushLit()
                    val end = code.indexOf('\n', i)
                    val commentEnd = if (end == -1) n else end
                    litSb.append(code.substring(i, commentEnd))
                    i = commentEnd
                }
                else -> {
                    flushLit()
                    codeSb.append(c)
                    i++
                }
            }
        }
        flushLit()
        flushCode()
        return out
    }

    private fun lastNonSpace(text: String, default: Char): Char {
        for (j in text.length - 1 downTo 0) {
            val ch = text[j]
            if (ch != ' ' && ch != '\t') return ch
        }
        return default
    }

    private fun spacingPass(text: String, prevBefore: Char): String {
        val sb = StringBuilder()
        var prev = prevBefore
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == ' ' || c == '\t' -> {
                    if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
                    i++
                }
                c in singleOps -> {
                    val unary = (c == '-' || c == '+' || c == '~' || c == '*') &&
                        (prev == ' ' || prev in unaryOkAfter || prev in singleOps)
                    if (!unary) {
                        if (sb.isEmpty() && prev != ' ') sb.append(' ')
                        else if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
                    }
                    sb.append(c)
                    if (!unary) {
                        val next = if (i + 1 < n) text[i + 1] else ' '
                        if (next !in noSpaceAfter) sb.append(' ')
                    }
                    prev = c
                    i++
                }
                else -> {
                    sb.append(c)
                    prev = c
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun beautifyLine(line: String, prevBefore: Char, preserveIndent: Boolean): String {
        val indent = if (preserveIndent) line.takeWhile { it == ' ' || it == '\t' } else ""
        var rest = line.substring(indent.length)
        protectOps.forEach { (op, token) -> rest = rest.replace(op, token) }
        var spaced = spacingPass(rest, prevBefore)
        restoreOps.forEach { (token, op) -> spaced = spaced.replace(token, op) }
        // normalisasi spasi ganda (tanpa memotong spasi yang berarti)
        spaced = spaced.replace(Regex(" +"), " ")
        // unpacking ** : f(**d) / a, **d / {**d} → tanpa spasi setelah **
        spaced = spaced.replace(Regex("\\(\\s*\\*\\*\\s+"), "(**")
        spaced = spaced.replace(Regex(",\\s*\\*\\*\\s+"), ", **")
        spaced = spaced.replace(Regex("\\{\\s*\\*\\*\\s+"), "{**")
        return indent + spaced
    }

    private fun beautifyCodeSegment(text: String, prevBefore: Char, firstIsLineStart: Boolean): String {
        val lines = text.split("\n")
        val out = mutableListOf<String>()
        for ((i, line) in lines.withIndex()) {
            val preserve = (i == 0 && firstIsLineStart) || i > 0
            out.add(beautifyLine(line, if (i == 0) prevBefore else ' ', preserve))
        }
        return out.joinToString("\n")
    }

    fun beautify(code: String): String {
        val segments = scanSegments(code)
        val sb = StringBuilder()
        var prevChar = ' '
        var atLineStart = true
        for (seg in segments) {
            if (seg.isLiteral) {
                sb.append(seg.text)
                prevChar = lastNonSpace(seg.text, prevChar)
                atLineStart = seg.text.endsWith("\n")
            } else {
                sb.append(beautifyCodeSegment(seg.text, prevChar, atLineStart))
                prevChar = lastNonSpace(seg.text, prevChar)
                atLineStart = seg.text.endsWith("\n")
            }
        }
        return sb.toString()
    }

    /** Auto-import library standar bila dipakai tapi belum di-import (deteksi via strip string/komentar). */
    fun optimizeImports(code: String): String {
        val stdLibs = listOf("os", "sys", "math", "json", "time", "random", "datetime")
        val stripped = Checker.stripCommentsAndStrings(code)
        val missingImports = mutableListOf<String>()

        stdLibs.forEach { lib ->
            val used = stripped.contains("$lib.")
            val already = code.contains(Regex("\\bimport\\s+$lib\\b")) ||
                code.contains(Regex("\\bfrom\\s+$lib\\s+import\\b")) ||
                code.contains(Regex("\\bimport\\s+[^\\n]*\\b$lib\\b"))
            if (used && !already) {
                missingImports.add("import $lib")
            }
        }
        if (missingImports.isEmpty()) return code
        return missingImports.joinToString("\n") + "\n" + code
    }
}
