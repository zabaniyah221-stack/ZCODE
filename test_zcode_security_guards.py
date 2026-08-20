"""Security guards for the bundled editor WebView and npm build inputs."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
HTML = ROOT / "app/src/main/assets/editor/index.html"
EDITOR = ROOT / "app/src/main/java/com/zaba/zcode/ui/editor/EditorScreen.kt"
PACKAGE = ROOT / "editor-src/package.json"
LOCK = ROOT / "editor-src/package-lock.json"
SCANNER = ROOT / "tools/npm_supply_chain_check.py"


def test_editor_csp_denies_network_and_remote_content():
    html = HTML.read_text()
    required = (
        "default-src 'none'",
        "script-src 'self'",
        "connect-src 'none'",
        "object-src 'none'",
        "frame-src 'none'",
        "worker-src 'none'",
        "base-uri 'none'",
        "form-action 'none'",
    )
    for directive in required:
        assert directive in html, f"CSP editor kehilangan {directive}"
    assert "'unsafe-eval'" not in html
    assert "script-src 'self' 'unsafe-inline'" not in html


def test_editor_webview_native_network_and_file_boundaries():
    src = EDITOR.read_text()
    for invariant in (
        "allowFileAccess = true",  # trusted APK assets still load
        "allowContentAccess = false",
        "allowFileAccessFromFileURLs = false",
        "allowUniversalAccessFromFileURLs = false",
        "blockNetworkLoads = true",
        "WebSettings.MIXED_CONTENT_NEVER_ALLOW",
        "shouldOverrideUrlLoading",
        "isTrustedEditorUrl",
        "WEBVIEW_NAV_BLOCKED",
    ):
        assert invariant in src, f"WebView hardening hilang: {invariant}"
    assert 'file:///android_asset/editor/' in src


def test_editor_font_urls_relative_inside_trusted_asset_tree():
    src = EDITOR.read_text()
    assert "src:url('fonts/jetbrains_mono.ttf')" in src
    assert "src:url('fonts/fira_code.ttf')" in src
    assert "src:url('fonts/source_code_pro.ttf')" in src
    assert "src:url('file:///" not in src


def test_npm_direct_dependencies_exact_and_lock_has_integrity():
    package = json.loads(PACKAGE.read_text())
    lock = json.loads(LOCK.read_text())
    assert lock["lockfileVersion"] == 3
    declared = {**package.get("dependencies", {}), **package.get("devDependencies", {})}
    for name, version in declared.items():
        parts = version.split(".")
        assert len(parts) == 3 and all(p.isdigit() for p in parts), (
            f"dependency harus exact tanpa range/tag: {name}={version}"
        )
        meta = lock["packages"][f"node_modules/{name}"]
        assert meta["version"] == version
        assert meta["integrity"].startswith("sha512-")


def test_supply_chain_scanner_is_part_of_local_gate():
    scanner = SCANNER.read_text()
    check = (ROOT / "tools/check.sh").read_text()
    assert "BAD_VERSIONS" in scanner
    assert "ALLOWED_INSTALL_SCRIPTS" in scanner
    assert "IOC_FILENAMES" in scanner
    assert "npm_supply_chain_check.py" in check


def test_visual_chrome_uses_text_or_monochrome_glyphs_not_decorative_emoji():
    settings = (ROOT / "app/src/main/java/com/zaba/zcode/ui/settings/SettingsScreen.kt").read_text()
    for label in ("Tampilan / Appearance", "Editor", "Run & Terminal", "Privasi & Data", "Tentang"):
        assert f'SettingsGroupHeader("{label}"' in settings
    for old in ("🎨", "⌨️", "▶️", "🔒", "ℹ️"):
        assert old not in settings

    terminal = (ROOT / "app/src/main/java/com/zaba/zcode/ui/terminal/TerminalScreen.kt").read_text()
    assert "\\u2192 Ke sumber error utama \\u00B7" in terminal
    assert "\\u2026 Menyalakan Python" in terminal
    assert "\\u26A1" not in terminal and "\\u2699" not in terminal

    workspace = (ROOT / "app/src/main/java/com/zaba/zcode/WorkspaceViewModel.kt").read_text()
    for face in ("😢", "🙈", "🙂", "✔"):
        assert face not in workspace

    workbench = (ROOT / "app/src/main/java/com/zaba/zcode/ui/workbench/WorkbenchScreen.kt").read_text()
    for decorative in ("😅", "😭", "🎉", "📜", "🤷", "🗂️", "⚙️", "⚓"):
        assert decorative not in workbench
    assert '"CLASS" -> "C"' in workbench
    assert '"FUNC" -> "ƒ"' in workbench
    assert '"METHOD" -> "m"' in workbench


def test_all_fullscreen_back_labels_use_left_arrow_glyph():
    files = (
        "ui/settings/AboutScreen.kt",
        "ui/settings/DiagnosticsScreen.kt",
        "ui/settings/PipScreen.kt",
        "ui/settings/SettingsScreen.kt",
        "ui/terminal/TerminalScreen.kt",
    )
    base = ROOT / "app/src/main/java/com/zaba/zcode"
    for rel in files:
        src = (base / rel).read_text()
        assert '"← Back"' in src, f"Back glyph hilang dari {rel}"
        assert '"◀ Back"' not in src
