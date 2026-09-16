import org.fife.ui.autocomplete.AutoCompletion
import org.fife.ui.autocomplete.BasicCompletion
import org.fife.ui.autocomplete.DefaultCompletionProvider
import org.fife.ui.autocomplete.TemplateCompletion
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice
import org.fife.ui.rsyntaxtextarea.parser.ParseResult
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.Color
import java.awt.Font
import java.io.File
import javax.swing.KeyStroke

/**
 * Editor Python RSTA (migrasi 15 Sep, irisan 4): tema GitHub Dark,
 * autocomplete keyword, dan parser py_compile → squiggle + ikon gutter.
 * Runner/output/drawer tak tersentuh — file ini murni layer editor.
 */

// GitHub Dark, selaras palet ZCODE (Main.kt: BG/SURFACE/ACCENT/TEXT).
private val ED_BG = Color(0x0D, 0x11, 0x17)
private val ED_FG = Color(0xC9, 0xD1, 0xD9)
private val ED_LINE = Color(0x16, 0x1B, 0x22)
private val ED_SEL = Color(0x26, 0x4A, 0x77)
private val C_KEYWORD = Color(0xFF, 0x7B, 0x72)
private val C_STRING = Color(0xA5, 0xD6, 0xFF)
private val C_COMMENT = Color(0x8B, 0x94, 0x9E)
private val C_NUMBER = Color(0x79, 0xC0, 0xFF)
private val C_FUNC = Color(0xD2, 0xA8, 0xFF)
private val C_ANNOT = Color(0xFF, 0xA6, 0x57)

/** Tema programatik (setara Theme XML): aman compile-time, tanpa resource load. */
fun applyGithubDarkTheme(area: RSyntaxTextArea) {
    area.background = ED_BG
    area.foreground = ED_FG
    area.currentLineHighlightColor = ED_LINE
    area.selectionColor = ED_SEL
    area.caretColor = ED_FG
    val s = area.syntaxScheme
    s.getStyle(Token.RESERVED_WORD)?.foreground = C_KEYWORD
    s.getStyle(Token.RESERVED_WORD_2)?.foreground = C_KEYWORD
    s.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE)?.foreground = C_STRING
    s.getStyle(Token.LITERAL_CHAR)?.foreground = C_STRING
    s.getStyle(Token.COMMENT_EOL)?.foreground = C_COMMENT
    s.getStyle(Token.COMMENT_MULTILINE)?.foreground = C_COMMENT
    s.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT)?.foreground = C_NUMBER
    s.getStyle(Token.LITERAL_NUMBER_FLOAT)?.foreground = C_NUMBER
    s.getStyle(Token.FUNCTION)?.foreground = C_FUNC
    s.getStyle(Token.ANNOTATION)?.foreground = C_ANNOT
    area.revalidate()
}

private val PY_KEYWORDS = listOf(
    "False", "None", "True", "and", "as", "assert", "async", "await",
    "break", "class", "continue", "def", "del", "elif", "else", "except",
    "finally", "for", "from", "global", "if", "import", "in", "is",
    "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try",
    "while", "with", "yield", "print", "len", "range", "str", "int",
    "float", "list", "dict", "set", "tuple", "open", "input", "super",
    "self", "__init__", "__name__", "__main__",
)

/** Builtin + stdlib umum (16 Sep): list 40 kata terlalu kecil buat kode
 * beneran (os/sys/np tak match → popup sembunyi → terasa mati). */
private val PY_BUILTINS = listOf(
    "abs", "all", "any", "bool", "bytes", "callable", "chr",
    "dir", "divmod", "enumerate", "eval", "exec", "filter",
    "format", "frozenset", "getattr", "globals", "hasattr",
    "hash", "help", "hex", "id", "isinstance", "issubclass",
    "iter", "locals", "map", "max", "min", "next", "object",
    "oct", "ord", "pow", "repr", "reversed", "round",
    "setattr", "slice", "sorted", "sum", "type", "vars", "zip",
    "os", "sys", "json", "math", "re", "time", "datetime",
    "pathlib", "subprocess", "threading", "collections",
    "itertools", "functools", "typing", "argparse", "logging",
    "random", "string", "io", "shutil", "glob", "pickle",
    "copy", "enum", "hashlib", "unittest",
)

/** Autocomplete: keyword + template blok (dipicu Ctrl+Spasi, popup jinak). */
/** Auto-activation 300ms (paket 15 Sep sore): popup muncul saat ketik. */
fun installPythonCompletion(area: RSyntaxTextArea): AutoCompletion {
    val p = JediCompletionProvider()
    // WAJIB (16 Sep, bukti source CompletionProviderBase:178): flag
    // autoActivateAfterLetters default FALSE → tanpa baris ini timer
    // auto-activation tak pernah restart → ketik tak pernah popup.
    // Manual Ctrl+Spasi lolos karena bypass cek ini (temuan user).
    p.setAutoActivationRules(true, null)
    for (kw in PY_KEYWORDS) p.addCompletion(BasicCompletion(p, kw))
    for (kw in PY_BUILTINS) p.addCompletion(BasicCompletion(p, kw))
    p.addCompletion(TemplateCompletion(p, "def", "def \${name}(\${args}):",
        "def \${name}(\${args}):\n    \${cursor}"))
    p.addCompletion(TemplateCompletion(p, "for", "for x in ...:",
        "for \${x} in \${iter}:\n    \${cursor}"))
    p.addCompletion(TemplateCompletion(p, "if", "if ...:",
        "if \${cond}:\n    \${cursor}"))
    p.addCompletion(TemplateCompletion(p, "while", "while ...:",
        "while \${cond}:\n    \${cursor}"))
    p.addCompletion(TemplateCompletion(p, "class", "class ...:",
        "class \${Name}:\n    def __init__(self):\n        \${cursor}"))
    p.addCompletion(TemplateCompletion(p, "try", "try/except",
        "try:\n    \${cursor}\nexcept \${Exception} as e:\n    pass"))
    val ac = AutoCompletion(p)
    ac.setTriggerKey(KeyStroke.getKeyStroke("ctrl SPACE"))
    ac.setAutoActivationEnabled(true)
    ac.setAutoActivationDelay(300)
    // Popup selalu tampil walau 1 match (16 Sep): default RSTA
    // silent auto-insert saat count==1 → terasa mati. Bukti source 3.3.2.
    ac.setAutoCompleteSingleChoices(false)
    // Popup gelap (paket 15 Sep sore): renderer delegate + selection UIManager.
    try {
        val rend = org.fife.ui.autocomplete.CompletionCellRenderer()
        rend.delegateRenderer.background = ED_BG
        rend.delegateRenderer.foreground = ED_FG
        ac.setListCellRenderer(rend)
    } catch (_: Exception) { }
    ac.install(area)
    return ac
}

/**
 * Cache Jedi async (opsi B, 15 Sep malam): jedi dingin 6.2 dtk / hangat 3.0 dtk
 * di Celeron — subprocess di EDT = freeze tiap ketik. Pola: provider layani
 * cache sinkron (tak pernah blokir), recompute jalan background single-flight.
 * Bukti ukur: /tmp/jt.py 376 items. Hanya analisis, TANPA eksekusi kode user.
 */
object JediCache {
    @Volatile var items: List<Pair<String, String>> = emptyList()
    @Volatile private var lastText: String? = null
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)

    fun request(text: String, line: Int, col: Int) {
        if (!running.compareAndSet(false, true)) return
        lastText = text
        Thread({
            try {
                items = queryJedi(text, line, col)
            } catch (_: Exception) { } finally { running.set(false) }
        }, "zcode-jedi").apply { isDaemon = true; start() }
    }

    private fun queryJedi(code: String, line: Int, col: Int): List<Pair<String, String>> {
        var tmp: File? = null
        return try {
            tmp = File.createTempFile("zcode_jedi", ".py")
            tmp.writeText(code)
            val script = "import jedi\n" +
                "s=jedi.Script(path='" + tmp.absolutePath + "')\n" +
                "print(chr(10).join(c.name+'\\t'+c.type for c in " +
                "s.complete(" + line + "," + col + ")))"
            val proc = ProcessBuilder("python3", "-c", script)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            // Background: boleh lama (dingin 6 dtk), UI tak diblokir.
            if (!proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                proc.destroyForcibly(); return emptyList()
            }
            out.lines().mapNotNull { ln ->
                val t = ln.split('\t')
                if (t.size == 2 && t[0].isNotBlank()) t[0] to t[1] else null
            }.take(30)
        } catch (_: Exception) { emptyList() } finally {
            try { tmp?.delete() } catch (_: Exception) { }
        }
    }
}

class JediCompletionProvider : DefaultCompletionProvider() {

    override fun getCompletions(comp: javax.swing.text.JTextComponent):
            MutableList<org.fife.ui.autocomplete.Completion> {
        val base = super.getCompletions(comp).toMutableList()
        try {
            val doc = comp.document
            val caret = comp.caretPosition
            val full = doc.getText(0, doc.length)
            val before = full.substring(0, caret.coerceAtMost(full.length))
            val prefix = before.takeLastWhile { it.isLetterOrDigit() || it == '_' }
            val line = before.count { it == '\n' } + 1
            val col = caret - (before.lastIndexOf('\n') + 1)
            // Picu recompute background untuk ketikan BERIKUTNYA.
            JediCache.request(full, line, col)
            if (prefix.length < 2) return base
            // Layani cache terakhir (sinkron, tanpa blokir).
            val seen = base.mapNotNull {
                (it as? BasicCompletion)?.replacementText }.toMutableSet()
            for ((name, sig) in JediCache.items) {
                if (name in seen || !name.startsWith(prefix)) continue
                seen.add(name)
                base.add(BasicCompletion(this, name, sig, "(jedi) $name"))
            }
        } catch (_: Exception) { }
        return base
    }
}

/**
 * Parser py_compile: dijalankan RSTA di thread background setelah jeda
 * idle — error sintaks jadi squiggle + ikon gutter. Bila python3 hilang,
 * diam-diam tanpa notice (bukan error editor). Baris py_compile 1-based →
 * notice 0-based.
 */
class PythonCompileParser : AbstractParser() {

    override fun parse(doc: RSyntaxDocument, style: String): ParseResult {
        val res = DefaultParseResult(this)
        val code = try {
            doc.getText(0, doc.length)
        } catch (_: Exception) { return res }
        if (code.isBlank()) return res
        var tmp: File? = null
        try {
            tmp = File.createTempFile("zcode_parse", ".py")
            tmp.writeText(code)
            val proc = ProcessBuilder("python3", "-m", "py_compile", tmp.absolutePath)
                .redirectErrorStream(true).start()
            val err = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            // Format: '  File "...", line N' diikuti baris SyntaxError: msg.
            val m = Regex("line (\\d+)").find(err)
            if (m != null) {
                val line1 = m.groupValues[1].toIntOrNull() ?: 1
                val msgLine = err.lines().lastOrNull { it.isNotBlank() } ?: "syntax error"
                val msg = msgLine.trim().take(300)
                // Span EKSPILISIT (fix 15 Sep malam): notice 3-arg (offset-1)
                // dirender ngawur full-width bawah viewport. Beri offset+length
                // = baris penuh agar squiggle tepat di baris salah.
                // Notice 0-based, py_compile 1-based.
                val line0 = (line1 - 1).coerceAtLeast(0)
                res.addNotice(DefaultParserNotice(
                    this, msg, line0, lineStart(doc, line0), lineLen(doc, line0)))
            }
        } catch (_: Exception) {
            // Parser tak boleh meledak: sunyi = tanpa squiggle.
        } finally {
            try { tmp?.delete() } catch (_: Exception) { }
        }
        return res
    }
}

/**
 * Offset awal + panjang baris dokumen (helper span notice eksplisit,
 * fix 15 Sep malam). Guard penuh: dokumen bisa berubah saat parser jalan.
 */
private fun lineStart(doc: RSyntaxDocument, line0: Int): Int {
    return try {
        val root = doc.getDefaultRootElement()
        val el = root.getElement(line0.coerceIn(0, (root.elementCount - 1).coerceAtLeast(0)))
        el.startOffset
    } catch (_: Exception) { 0 }
}

private fun lineLen(doc: RSyntaxDocument, line0: Int): Int {
    return try {
        val root = doc.getDefaultRootElement()
        val el = root.getElement(line0.coerceIn(0, (root.elementCount - 1).coerceAtLeast(0)))
        (el.endOffset - el.startOffset).coerceAtLeast(1)
    } catch (_: Exception) { 1 }
}

/**
 * Parser pycodestyle (paket 15 Sep sore): style warning jadi notice
 * WARNING (ikon beda dari ERROR py_compile). Sunyi bila modul hilang.
 */
class PycodestyleParser : AbstractParser() {

    override fun parse(doc: RSyntaxDocument, style: String): ParseResult {
        val res = DefaultParseResult(this)
        val code = try {
            doc.getText(0, doc.length)
        } catch (_: Exception) { return res }
        if (code.isBlank()) return res
        var tmp: File? = null
        try {
            tmp = File.createTempFile("zcode_style", ".py")
            tmp.writeText(code)
            val proc = ProcessBuilder("python3", "-m", "pycodestyle",
                "--format=%(row)d:%(col)d:%(code)s:%(text)s", tmp.absolutePath)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            if (!proc.waitFor(6, java.util.concurrent.TimeUnit.SECONDS)) {
                proc.destroyForcibly(); return res
            }
            for (ln in out.lines().take(50)) {
                val t = ln.split(':', limit = 4)
                if (t.size < 4) continue
                // W391 (blank line at end) = noise saat mengetik — skip.
                // W292 (no newline at end) = SELALU ada saat mengetik +
                // span-nya di ujung dokumen → RSTA gambar full-width di
                // bawah viewport, bukan di bawah baris salah (16 Sep).
                if (t[2] == "W391" || t[2] == "W292") continue
                val row = t[0].toIntOrNull() ?: continue
                val col = t[1].toIntOrNull() ?: 1
                // Span eksplisit (fix 15 Sep malam, sama akar py_compile):
                // dari kolom lapor sampai akhir baris.
                val line0 = (row - 1).coerceAtLeast(0)
                val ls = lineStart(doc, line0)
                val le = ls + lineLen(doc, line0)
                // Clamp (16 Sep): kolom pelapor bisa di ujung/lewat akhir
                // baris → offset liar = squiggle full-width bawah viewport.
                val start = (ls + (col - 1).coerceAtLeast(0))
                    .coerceIn(ls, (le - 1).coerceAtLeast(ls))
                val len = (le - start).coerceAtLeast(1)
                val notice = DefaultParserNotice(
                    this, "${t[2]} ${t[3].trim().take(200)}",
                    line0, start, len)
                notice.level = org.fife.ui.rsyntaxtextarea.parser
                    .ParserNotice.Level.WARNING
                res.addNotice(notice)
            }
        } catch (_: Exception) {
        } finally {
            try { tmp?.delete() } catch (_: Exception) { }
        }
        return res
    }
}

/** Scrollbar Swing rasa output Compose (paket 15 Sep sore): thumb gelap. */
fun styleDarkScrollbars() {
    try {
        javax.swing.UIManager.put("ScrollBar.thumb",
            javax.swing.plaf.ColorUIResource(ED_SEL))
        javax.swing.UIManager.put("ScrollBar.track",
            javax.swing.plaf.ColorUIResource(ED_BG))
        javax.swing.UIManager.put("ScrollBar.width", 12)
        // Popup completion gelap (app tak punya Swing list lain — aman global).
        javax.swing.UIManager.put("List.background",
            javax.swing.plaf.ColorUIResource(ED_BG))
        javax.swing.UIManager.put("List.foreground",
            javax.swing.plaf.ColorUIResource(ED_FG))
        javax.swing.UIManager.put("List.selectionBackground",
            javax.swing.plaf.ColorUIResource(ED_SEL))
        javax.swing.UIManager.put("List.selectionForeground",
            javax.swing.plaf.ColorUIResource(java.awt.Color.WHITE))
    } catch (_: Exception) { }
}

/**
 * ScrollBarUI gelap LAF-independen (paket 15 Sep sore): UIManager.put tak
 * mempan bila LAF bukan Metal — delegate ini selalu menang.
 */
class DarkScrollBarUI : javax.swing.plaf.basic.BasicScrollBarUI() {

    override fun configureScrollBarColors() {
        thumbColor = ED_SEL
        trackColor = ED_BG
    }

    override fun createDecreaseButton(orientation: Int): javax.swing.JButton =
        zeroButton()

    override fun createIncreaseButton(orientation: Int): javax.swing.JButton =
        zeroButton()

    private fun zeroButton() = javax.swing.JButton().apply {
        val z = java.awt.Dimension(0, 0)
        preferredSize = z; minimumSize = z; maximumSize = z
    }
}

/** Pabrik editor: satu tempat wiring tema + completion + parser. */
fun newPythonEditor(
    fontSize: Int,
    initialText: String,
    onReady: (RSyntaxTextArea) -> Unit
): RTextScrollPane {
    val area = RSyntaxTextArea()
    area.syntaxEditingStyle = SyntaxConstants.SYNTAX_STYLE_PYTHON
    area.isCodeFoldingEnabled = true
    area.antiAliasingEnabled = true
    area.font = Font(Font.MONOSPACED, Font.PLAIN, fontSize)
    applyGithubDarkTheme(area)
    // Paket editor 15 Sep sore: tanpa border, wrap kata.
    // markOccurrences MATI (16 Sep, mau user): tanpa highlight blok
    // terang maupun gelap di kata bawah kursor = transparan.
    area.lineWrap = true
    area.wrapStyleWord = true
    area.markOccurrences = false
    area.margin = java.awt.Insets(4, 6, 4, 6)
    area.text = initialText
    installPythonCompletion(area)
    area.addParser(PythonCompileParser())
    area.addParser(PycodestyleParser())
    styleDarkScrollbars()
    onReady(area)
    val pane = RTextScrollPane(area)
    // Anti-blink (temuan 15 Sep): viewport + gutter default terang ikut
    // repaint saat resize. Cat gelap eksplisit.
    // Paket 15 Sep sore: border HILANG total (pane, viewport, gutter).
    pane.background = ED_BG
    pane.viewport.background = ED_BG
    try { pane.border = javax.swing.BorderFactory.createEmptyBorder() } catch (_: Exception) { }
    try { pane.viewport.border = null } catch (_: Exception) { }
    try {
        pane.gutter?.background = ED_BG
        pane.gutter?.borderColor = ED_BG
        pane.gutter?.setBorder(javax.swing.BorderFactory.createEmptyBorder())
    } catch (_: Exception) { }
    // Scrollbar gelap pasti (paket 15 Sep sore): delegate langsung,
    // tak tergantung LAF.
    try {
        pane.verticalScrollBar.ui = DarkScrollBarUI()
        pane.horizontalScrollBar.ui = DarkScrollBarUI()
    } catch (_: Exception) { }
    return pane
}
