"""
ZCODE Fase 1 & 2 Strict Tests — honest & aware
Covers: file manager + persistensi, PTY terminal interaktif, pip layer, 3 tema,
WebView Ace asli, Checker, PluginHost (beautifier aman), Command Palette, semua tombol wired.
Catatan: sandbox tanpa JDK/Android SDK, jadi test ini struktural (menjaga kontrak anti-regresi).
Behavioral test di sandbox: bash tools/check.sh + python (lihat test_ace_real).
Run: python -m pytest test_zcode_fase1.py -v
"""
import re
from pathlib import Path

ROOT = Path(__file__).parent
APP = ROOT / "app"
JAVA = APP / "src/main/java/com/zaba/zcode"
UI = JAVA / "ui"
CORE = JAVA / "core"
ASSETS = APP / "src/main/assets"


def read(p): return p.read_text(encoding="utf-8", errors="replace") if p.exists() else ""


# ===================================================================
# File & struktur Fase 1
# ===================================================================

class TestFase1Files:
    def test_index_html_exists(self):
        p = ASSETS / "editor/index.html"
        assert p.exists(), "index.html Ace missing"

    def test_ace_real_not_stub(self):
        # Ace asli 1.44.0 berukuran ~900KB; stub Fase 0 hanya ~300 byte
        ace = ASSETS / "editor/ace/ace.js"
        assert ace.exists() and ace.stat().st_size > 100_000, "ace.js masih stub?"

    def test_ace_version_144(self):
        assert "1.44.0" in read(ASSETS / "editor/ace/ace.js")

    def test_ace_mode_python_real(self):
        mode = ASSETS / "editor/ace/mode-python.js"
        assert mode.exists() and mode.stat().st_size > 1_000, "mode-python.js masih stub?"

    def test_workspace_viewmodel_exists(self):
        p = JAVA / "WorkspaceViewModel.kt"
        assert p.exists()
        txt = read(p)
        for kw in ["createNewFile", "renameFile", "deleteFile", "validateSyntaxDebounced", "untitled_"]:
            assert kw in txt, f"WorkspaceViewModel missing {kw}"

    def test_workspace_persists_tabs(self):
        txt = read(JAVA / "WorkspaceViewModel.kt")
        assert "SharedPreferences" in txt or "persistWorkspaceState" in txt

    def test_checker_exists(self):
        txt = read(CORE / "editor/Checker.kt")
        for kw in ["stripCommentsAndStrings", "checkBrackets", "checkSyntax"]:
            assert kw in txt, f"Checker missing {kw}"

    def test_plugin_host_exists(self):
        txt = read(CORE / "plugins/PluginHost.kt")
        for kw in ["beautify", "optimizeImports"]:
            assert kw in txt, f"PluginHost missing {kw}"

    def test_terminal_screen_exists(self):
        txt = read(UI / "terminal/TerminalScreen.kt")
        for kw in ["sendCtrlC", "FocusRequester", "inputVal", "onBack"]:
            assert kw in txt, f"TerminalScreen missing {kw}"

    def test_pip_screen_exists(self):
        txt = read(UI / "settings/PipScreen.kt")
        assert "pip" in txt.lower()
        assert "isInstalling" in txt
        assert "scrollState" in txt

    def test_about_screen_exists(self):
        txt = read(UI / "settings/AboutScreen.kt")
        assert "github.com/muzape28-blip/ZCODE/issues" in txt

    def test_main_activity_routes(self):
        txt = read(JAVA / "MainActivity.kt")
        for kw in ["output/", "pip", "about", "viewModels"]:
            assert kw in txt, f"MainActivity missing route {kw}"


# ===================================================================
# Editor WebView — anti-regresi F-01/S-27/C-50
# ===================================================================

class TestEditorWebView:
    def test_webview_bridge(self):
        txt = read(UI / "editor/EditorScreen.kt")
        assert "addJavascriptInterface" in txt
        assert "loadUrl" in txt
        assert "onPageFinished" in txt

    def test_escape_js_function(self):
        assert "escapeJavaScriptString" in read(UI / "editor/EditorScreen.kt")

    def test_index_html_bridge(self):
        txt = read(ASSETS / "editor/index.html")
        for kw in ["ace.edit", "onCodeChange", "setCode", "12px", "#050806"]:
            assert kw in txt, f"index.html missing {kw}"

    def test_index_html_plugins(self):
        txt = read(ASSETS / "editor/index.html")
        assert "duplicateRows" in txt and "toggleCommentLines" in txt


# ===================================================================
# ExecutionEngine — guards S-18 + SIGINT asli
# ===================================================================

class TestExecutionFase1:
    def test_start_interactive_session(self):
        assert "startInteractiveSession" in read(CORE / "execution/ExecutionEngine.kt")

    def test_real_sigint(self):
        txt = read(CORE / "execution/ExecutionEngine.kt")
        assert "kill" in txt and "-INT" in txt, "Ctrl+C harus SIGINT asli, bukan SIGKILL"

    def test_pip_process_guard(self):
        txt = read(CORE / "execution/ExecutionEngine.kt")
        assert "startPipProcess" in txt
        assert "isSafePackageName" in txt

    def test_terminal_caps_output(self):
        txt = read(UI / "terminal/TerminalScreen.kt")
        assert "MAX_OUTPUT_CHARS" in txt, "terminal harus cap output (S-18)"

    def test_pip_caps_log(self):
        txt = read(UI / "settings/PipScreen.kt")
        assert "MAX_OUTPUT_CHARS" in txt, "pip log harus di-cap"

    def test_no_textstyle_val_bug(self):
        # Bug dari source referensi: `private val TextStyle = androidx...` tidak valid Kotlin
        txt = read(UI / "settings/PipScreen.kt")
        assert "private val TextStyle" not in txt, "bug TextStyle val di PipScreen"

    def test_exec_has_ketik_langsung_note(self):
        txt = read(CORE / "execution/ExecutionEngine.kt").lower()
        assert "ketik langsung" in txt


# ===================================================================
# Checker / PluginHost — anti-regresi B-10/B-11
# ===================================================================

class TestCheckerPlugin:
    def test_beautify_protects_arrow(self):
        txt = read(CORE / "plugins/PluginHost.kt")
        assert "ARROW" in txt, "beautifier harus protect -> (longest-first, B-10)"

    def test_beautify_protects_ops(self):
        txt = read(CORE / "plugins/PluginHost.kt")
        assert "POW" in txt and "FLOORDIV" in txt, "beautifier harus protect ** dan //"

    def test_beautify_string_safe(self):
        txt = read(CORE / "plugins/PluginHost.kt")
        assert "isLiteral" in txt or "scanSegments" in txt, "beautifier harus pisahkan string/komentar"

    def test_beautify_unary(self):
        txt = read(CORE / "plugins/PluginHost.kt")
        assert "unary" in txt.lower(), "beautifier harus sadar unary minus/star"

    def test_checker_b11_comment_note(self):
        txt = read(CORE / "editor/Checker.kt")
        assert "stripCommentsAndStrings" in txt
        assert "B-11" in txt or "bracket" in txt.lower()


# ===================================================================
# Tema & Workbench — semua tombol wired
# ===================================================================

class TestThemeWorkbenchFase1:
    def test_three_themes(self):
        txt = read(UI / "theme/ZcodeTheme.kt")
        for t in ["RETRO", "DRACULA", "TOKYO_NIGHT"]:
            assert t in txt, f"theme {t} missing"

    def test_theme_takes_type(self):
        assert "themeType" in read(UI / "theme/ZcodeTheme.kt")

    def test_workbench_wired(self):
        txt = read(UI / "workbench/WorkbenchScreen.kt")
        for kw in ["onRun", "fileToRename", "fileToDelete", "showPalette", "combinedClickable", "insertText"]:
            assert kw in txt, f"Workbench missing wiring {kw}"

    def test_workbench_long_press_close(self):
        txt = read(UI / "workbench/WorkbenchScreen.kt")
        assert "onLongClick" in txt and "closeFile" in txt

    def test_workbench_quicktools_wired(self):
        txt = read(UI / "workbench/WorkbenchScreen.kt")
        assert "QuickTools" in txt or "quickTools" in txt.lower()
        assert "evaluateJavascript" in txt

    def test_workbench_dialogs(self):
        txt = read(UI / "workbench/WorkbenchScreen.kt")
        assert txt.count("AlertDialog") >= 3, "rename/delete/clear-all dialog harus ada"


# ===================================================================
# Bebas dari bug pola lama di seluruh kode Kotlin
# ===================================================================

class TestNoKnownBugPatterns:
    def test_no_mailto_anywhere(self):
        for p in (JAVA).rglob("*.kt"):
            assert "mailto" not in read(p).lower(), f"mailto ditemukan di {p}"

    def test_no_destroy_only_ctrlc(self):
        # TerminalScreen tidak boleh hanya andalkan destroyForcibly untuk Ctrl+C
        term = read(UI / "terminal/TerminalScreen.kt")
        assert "sendCtrlC" in term
        engine = read(CORE / "execution/ExecutionEngine.kt")
        assert "sendCtrlC" in engine

    def test_index_html_offline_no_cdn(self):
        txt = read(ASSETS / "editor/index.html").lower()
        for bad in ["cdnjs", "unpkg", "jsdelivr", "http://", "https://"]:
            assert bad not in txt, f"index.html tidak boleh pakai CDN/remote: {bad}"
