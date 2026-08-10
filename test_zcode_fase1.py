"""
ZCODE Fase 1 & 2 Strict Tests — honest & aware
Covers: file manager + persistensi, PTY terminal interaktif, pip layer, 3 tema,
WebView CodeMirror 6 asli (bundle), Checker, PluginHost (beautifier aman), Command Palette, semua tombol wired.
Catatan: sandbox tanpa JDK/Android SDK, jadi test ini struktural (menjaga kontrak anti-regresi).
Behavioral test di sandbox: bash tools/check.sh + python (lihat test_cm6_real_not_stub).
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
BUILD_GRADLE = APP / "build.gradle.kts"


def read(p): return p.read_text(encoding="utf-8", errors="replace") if p.exists() else ""


# ===================================================================
# File & struktur Fase 1
# ===================================================================

class TestFase1Files:
    def test_index_html_exists(self):
        p = ASSETS / "editor/index.html"
        assert p.exists(), "index.html editor missing"

    def test_cm6_real_not_stub(self):
        # Bundle CM6 asli (CM6 + lang-python + search) ~400KB; stub pasti kecil
        bundle = ASSETS / "editor/codemirror.bundle.js"
        assert bundle.exists() and bundle.stat().st_size > 100_000, "codemirror.bundle.js masih stub?"

    def test_cm6_bridge_contract(self):
        # Kontrak bridge identik dengan era Ace + openFind baru (docs/MIGRASI_CM6.md §3)
        txt = read(ASSETS / "editor/codemirror.bundle.js")
        for kw in ["setCode", "getCode", "insertText", "undo", "redo",
                   "duplicateRows", "toggleCommentLines", "openFind", "onEditorReady"]:
            assert kw in txt, f"bundle CM6 kehilangan kontrak bridge {kw}"

    def test_cm6_python_lang(self):
        # 'nonlocal' hanya ada di grammar Lezer-python — bukti lang-python terbundle
        assert "nonlocal" in read(ASSETS / "editor/codemirror.bundle.js")

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
        # Migrasi CM6: index.html kini hanya me-load bundle; implementasi bridge
        # (onCodeChange/setCode) hidup di dalam bundle (test_cm6_bridge_contract).
        txt = read(ASSETS / "editor/index.html")
        for kw in ["codemirror.bundle.js", "12px", "#050806"]:
            assert kw in txt, f"index.html missing {kw}"

    def test_bundle_plugins(self):
        txt = read(ASSETS / "editor/codemirror.bundle.js")
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


# ===================================================================
# Chaquopy — on-device execution (Fase 1 fix: tidak nanggung)
# ===================================================================

class TestChaquopyEmbed:
    def test_root_build_has_chaquopy_plugin(self):
        assert 'com.chaquo.python' in read(ROOT / "build.gradle.kts")

    def test_app_build_applies_chaquopy(self):
        txt = read(BUILD_GRADLE)
        assert 'id("com.chaquo.python")' in txt

    def test_app_build_chaquopy_block(self):
        txt = read(BUILD_GRADLE)
        assert "chaquopy" in txt and 'version = "3.11"' in txt

    def test_settings_has_chaquo_maven(self):
        txt = read(ROOT / "settings.gradle.kts")
        assert "chaquo.com/maven" in txt

    def test_runner_py_exists(self):
        p = APP / "src/main/python/zcode_runner.py"
        assert p.exists(), "zcode_runner.py missing"
        txt = read(p)
        for kw in ["run_script", "BridgeStdin", "runpy.run_path", "KeyboardInterrupt"]:
            assert kw in txt, f"runner missing {kw}"

    def test_pip_py_exists(self):
        p = APP / "src/main/python/zcode_pip.py"
        assert p.exists(), "zcode_pip.py missing"
        assert "install_package" in read(p)

    def test_terminal_bridge_exists(self):
        txt = read(CORE / "execution/TerminalBridge.kt")
        for kw in ["readLine", "isInterrupted", "workspaceDir", "onExit", "interrupt"]:
            assert kw in txt, f"TerminalBridge missing {kw}"

    def test_engine_dual_backend(self):
        txt = read(CORE / "execution/ExecutionEngine.kt")
        assert "isChaquopyAvailable" in txt
        assert "ChaquopySession" in txt and "ProcessSession" in txt
        assert "describeBackend" in txt

    def test_engine_callback_session(self):
        txt = read(CORE / "execution/ExecutionEngine.kt")
        assert "onOutput" in txt and "onExit" in txt

    def test_engine_start_pip_stream(self):
        assert "startPipStream" in read(CORE / "execution/ExecutionEngine.kt")

    def test_engine_workspace_dir(self):
        assert "workspaceDirPath" in read(CORE / "execution/ExecutionEngine.kt")

    def test_vm_sets_workspace_dir(self):
        assert "workspaceDirPath" in read(JAVA / "WorkspaceViewModel.kt")


# ===================================================================
# Fix bug: tab double-trigger (long-press close → re-open) + versi
# ===================================================================

class TestBugFixes:
    def test_tab_single_combined_clickable(self):
        txt = read(UI / "workbench/WorkbenchScreen.kt")
        assert "combinedClickable" in txt
        assert "onLongClick" in txt

    def test_vm_guard_recently_closed(self):
        txt = read(JAVA / "WorkspaceViewModel.kt")
        assert "lastClosed" in txt, "guard anti double-trigger missing"
        assert "400" in txt

    def test_version_bump(self):
        assert "1.0.0" in read(BUILD_GRADLE)
        assert "1.0.0" in read(ROOT / "gradle.properties")
        assert "1.0.0" in read(UI / "settings/AboutScreen.kt")

    def test_beautify_prev_threading(self):
        txt = read(CORE / "plugins/PluginHost.kt")
        assert "lastNonSpace" in txt and "atLineStart" in txt

    def test_terminal_takes_context(self):
        assert "context: Context" in read(UI / "terminal/TerminalScreen.kt")

    def test_pip_takes_context(self):
        assert "context: android.content.Context" in read(UI / "settings/PipScreen.kt")


# ===================================================================
# Batch Anti-Sepi (2026-08) — plugins, search multi-mode, autocomplete,
# snippets, FAB syntax-aware. Lihat docs/PLAN_BATCH_ANTI_SEPI.md.
# ===================================================================

import subprocess

PY_PLUGINS = APP / "src/main/python/zcode_plugins.py"
BUNDLE = ASSETS / "editor/codemirror.bundle.js"


class TestZcodePluginsBackend:
    def test_zcode_plugins_py_exists(self):
        assert PY_PLUGINS.exists() and PY_PLUGINS.stat().st_size > 5_000

    def test_zcode_plugins_provenance_header(self):
        # Kejujuran lisensi (PLAN §2.5): port dari Zabacode GPLv3 wajib tercatat
        assert "PORTED FROM ZABACODE (GPLv3)" in read(PY_PLUGINS)

    def test_zcode_plugins_three_transforms(self):
        txt = read(PY_PLUGINS)
        for cls in ["SmartCommentGenerator", "VariableTypeHintGenerator", "DuplicateLineDetector"]:
            assert cls in txt, f"kelas port {cls} hilang"

    def test_zcode_plugins_run_json_interface(self):
        assert "def run_json" in read(PY_PLUGINS)

    def test_zcode_plugins_behavioral_docstring(self):
        # Behavioral test nyata via interpreter (pola dual-backend desktop)
        import tempfile, json
        src = "def tambah(a, b=1):\n    return a + b\n"
        with tempfile.NamedTemporaryFile("w", suffix=".py", delete=False) as f:
            f.write(src)
            tmp = f.name
        r = subprocess.run(
            ["python3", str(PY_PLUGINS), "docstring_generator", tmp],
            capture_output=True, text=True, timeout=30,
        )
        d = json.loads(r.stdout.strip().splitlines()[-1])
        assert d["ok"] is True
        assert '"""Docstring for tambah.' in d["code"]
        assert "Args:" in d["code"]

    def test_zcode_plugins_behavioral_unknown_plugin(self):
        import tempfile, json
        with tempfile.NamedTemporaryFile("w", suffix=".py", delete=False) as f:
            f.write("x = 1\n")
            tmp = f.name
        r = subprocess.run(
            ["python3", str(PY_PLUGINS), "plugin_palsu", tmp],
            capture_output=True, text=True, timeout=30,
        )
        d = json.loads(r.stdout.strip().splitlines()[-1])
        assert d["ok"] is False  # graceful, tidak crash


class TestPluginKotlin:
    def test_plugin_runner_dual_backend(self):
        txt = read(JAVA / "core/plugins/PluginRunner.kt")
        assert "Chaquopy" in txt and "ZCODE_PLUGINS_PY" in txt
        assert "TIMEOUT_MS" in txt

    def test_todo_extractor_tags(self):
        txt = read(JAVA / "core/plugins/TodoExtractor.kt")
        for tag in ["TODO", "FIXME", "HACK", "XXX"]:
            assert tag in txt

    def test_plugin_registry_ids_lengkap(self):
        txt = read(JAVA / "core/plugins/PluginRegistry.kt")
        for pid in ["beautifier", "optimize_imports", "duplicate_line", "toggle_comment",
                    "docstring_generator", "type_hint_generator", "find_duplicates",
                    "todo_extractor", "snippets", "auto_trim_on_run"]:
            assert f'"{pid}"' in txt, f"plugin id {pid} hilang dari registry"

    def test_snippet_library_empat_template(self):
        txt = read(JAVA / "core/plugins/SnippetLibrary.kt")
        for sid in ["flask_app", "web_scraper", "async_fetch", "rest_api"]:
            assert sid in txt, f"snippet {sid} hilang"

    def test_vm_plugin_state_satu_sumber(self):
        # Anti state-terbelah ala Zabacode: flag plugin hidup di ViewModel/prefs
        txt = read(JAVA / "WorkspaceViewModel.kt")
        assert "pluginFlags" in txt and "plugin_enabled_" in txt


# ===================================================================
# F2.4 — Toggle indikator Python di terminal
# ===================================================================

class TestF24PythonIndicator:
    def test_vm_has_show_python_indicator_state(self):
        """F2.4: WorkspaceViewModel harus punya state showPythonIndicator."""
        txt = read(JAVA / "WorkspaceViewModel.kt")
        assert "showPythonIndicator" in txt, "State showPythonIndicator hilang dari ViewModel"
        assert "mutableStateOf(true)" in txt, "Default harus true (ON)"

    def test_vm_has_set_show_python_indicator(self):
        """F2.4: Harus ada setter yang persist via SharedPreferences."""
        txt = read(JAVA / "WorkspaceViewModel.kt")
        assert "setShowPythonIndicator" in txt, "Setter setShowPythonIndicator hilang"
        assert "show_python_indicator" in txt, "Key SharedPreferences hilang"

    def test_vm_loads_show_python_indicator_from_prefs(self):
        """F2.4: State harus di-load dari SharedPreferences saat init."""
        txt = read(JAVA / "WorkspaceViewModel.kt")
        assert 'prefs.getBoolean("show_python_indicator"' in txt, "Load dari prefs hilang"

    def test_settings_screen_has_python_indicator_toggle(self):
        """F2.4: SettingsScreen harus punya toggle untuk indikator Python."""
        txt = read(UI / "settings/SettingsScreen.kt")
        assert "showPythonIndicator" in txt, "Toggle showPythonIndicator hilang dari SettingsScreen"
        assert "Menyalakan Python" in txt, "Label toggle harus menyebut indikator Python"

    def test_terminal_screen_accepts_show_python_indicator(self):
        """F2.4: TerminalScreen harus terima parameter showPythonIndicator."""
        txt = read(UI / "terminal/TerminalScreen.kt")
        assert "showPythonIndicator" in txt, "Parameter showPythonIndicator hilang dari TerminalScreen"
        assert "Boolean = true" in txt, "Default harus true (ON)"

    def test_terminal_screen_conditional_indicator(self):
        """F2.4: Indikator harus conditional berdasarkan showPythonIndicator."""
        txt = read(UI / "terminal/TerminalScreen.kt")
        # Harus ada kondisi yang menggabungkan startingPython DAN showPythonIndicator
        assert "startingPython && showPythonIndicator" in txt, "Kondisi conditional hilang"

    def test_main_activity_passes_show_python_indicator(self):
        """F2.4: MainActivity harus pass state ke TerminalScreen."""
        txt = read(JAVA / "MainActivity.kt")
        assert "showPythonIndicator = vm.showPythonIndicator" in txt, "MainActivity tidak pass state ke TerminalScreen"


class TestBatchUI:
    def test_drawer_plugins_expandable(self):
        txt = read(UI / "workbench/WorkbenchScreen.kt")
        # Redesign 2026-08 (Fase 3): header "🧩 PLUGINS" → "TOOLS" (polos, tanpa emoji);
        # isi kotak tetap plugin + switch, ditambah Symbol bar / THEME / Clear All.
        assert "TOOLS" in txt
        assert "AnimatedVisibility" in txt and "PluginRow" in txt

    def test_fab_syntax_aware(self):
        # S6: merah saat syntax error, tapi TETAP bisa run (tanpa blokir)
        txt = read(UI / "workbench/WorkbenchScreen.kt")
        assert "0xFFFF4B4B" in txt and "vm.syntaxError != null" in txt
        assert "applyAutoTrimIfEnabled" in txt  # behavior F5 ke-run

    def test_palette_multi_mode(self):
        txt = read(UI / "workbench/WorkbenchScreen.kt")
        assert "PaletteModeChip" in txt
        assert '"find"' in txt and '"line"' in txt
        assert "onGotoLine" in txt

    def test_goto_line_bridge_contract(self):
        assert "gotoLine" in read(BUNDLE)
        assert "gotoLine" in read(ASSETS / "editor/index.html")
        assert "gotoLine" in read(UI / "workbench/WorkbenchScreen.kt")

    def test_autocomplete_terbundle(self):
        txt = read(BUNDLE)
        # bukti konten: builtins + styling popup OLED + snippet (minify rename id)
        assert "frozenset" in txt
        assert "cm-tooltip-autocomplete" in txt
        assert "web_scraper" in txt

    def test_snippet_sync_js_kotlin(self):
        # Konten snippet HARUS identik di bundle (JS) & SnippetLibrary.kt
        bundle = read(BUNDLE)
        kt = read(JAVA / "core/plugins/SnippetLibrary.kt")
        for sid in ["flask_app", "web_scraper", "async_fetch", "rest_api"]:
            assert sid in bundle and sid in kt, f"snippet {sid} tidak sinkron JS/Kotlin"

    def test_editor_src_autocomplete_source(self):
        src = read(ROOT / "editor-src/src/editor.js")
        assert "zcodeCompletions" in src and "PY_BUILTINS" in src
        assert "activateOnTyping" in src
