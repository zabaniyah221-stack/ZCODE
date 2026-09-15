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
    "self", "__init__", "__name__", "__main__"
)

/** Autocomplete: keyword + template blok (dipicu Ctrl+Spasi, popup jinak). */
fun installPythonCompletion(area: RSyntaxTextArea): AutoCompletion {
    val p = DefaultCompletionProvider()
    for (kw in PY_KEYWORDS) p.addCompletion(BasicCompletion(p, kw))
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
    ac.install(area)
    return ac
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
                // Default level ERROR + showInEditor true = squiggle + gutter.
                // Notice 0-based (TaskTagParser), py_compile 1-based.
                res.addNotice(DefaultParserNotice(
                    this, msg, (line1 - 1).coerceAtLeast(0)))
            }
        } catch (_: Exception) {
            // Parser tak boleh meledak: sunyi = tanpa squiggle.
        } finally {
            try { tmp?.delete() } catch (_: Exception) { }
        }
        return res
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
    area.text = initialText
    installPythonCompletion(area)
    area.addParser(PythonCompileParser())
    onReady(area)
    return RTextScrollPane(area)
}
