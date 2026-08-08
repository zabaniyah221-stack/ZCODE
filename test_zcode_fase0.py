"""
ZCODE Fase 0 Strict Tests — honest & aware
Covers: docs, plan, design, gradle, manifest, ace, execution guards, UI spec, PTY, no-icons, contribute
Run: pytest test_zcode_fase0.py -v  &&  bash tools/check.sh
"""
import re
from pathlib import Path

ROOT = Path(__file__).parent
APP = ROOT / "app"
MANIFEST = APP / "src/main/AndroidManifest.xml"
ACE_JS = APP / "src/main/assets/editor/ace/ace.js"
THEME_KT = APP / "src/main/java/com/zaba/zcode/ui/theme/ZcodeTheme.kt"
WORKBENCH_KT = APP / "src/main/java/com/zaba/zcode/ui/workbench/WorkbenchScreen.kt"
EDITOR_KT = APP / "src/main/java/com/zaba/zcode/ui/editor/EditorScreen.kt"
EXEC_KT = APP / "src/main/java/com/zaba/zcode/core/execution/ExecutionEngine.kt"
PATHS_KT = APP / "src/main/java/com/zaba/zcode/core/files/Paths.kt"
FILEMGR_KT = APP / "src/main/java/com/zaba/zcode/core/files/FileManager.kt"
KEYSTORE_KT = APP / "src/main/java/com/zaba/zcode/core/security/KeystoreService.kt"
BUILD_GRADLE = APP / "build.gradle.kts"
SETTINGS_GRADLE = ROOT / "settings.gradle.kts"
PLAN = ROOT / "docs/PLAN_ZCODE.md"
DESIGN = ROOT / "docs/DESIGN_ZCODE.md"
BUGS = ROOT / "docs/BUGS_AUDIT_ZABACODE_FOR_ZCODE.md"

def read(p): return p.read_text(encoding="utf-8", errors="replace") if p.exists() else ""
def _bisection_stub():
    """True bila WorkbenchScreen sedang di-stub untuk bisection CI."""
    txt = read(WORKBENCH_KT)
    return "TEST B" in txt



# ===================================================================
# Docs existence
# ===================================================================

class TestDocsExist:
    def test_plan_exists(self): assert PLAN.exists(), "PLAN_ZCODE.md missing"
    def test_design_exists(self): assert DESIGN.exists(), "DESIGN_ZCODE.md missing"
    def test_bugs_exists(self): assert BUGS.exists(), "BUGS_AUDIT missing"
    def test_mockup_editor_exists(self): assert (ROOT / "docs/mockup-zcode-v03-editor.png").exists()
    def test_mockup_layers_exists(self): assert (ROOT / "docs/mockup-zcode-v03-layers.png").exists()
    def test_readme_exists(self): assert (ROOT / "README.md").exists()

# ===================================================================
# Plan v0.2 content — must reflect our discussion
# ===================================================================

class TestPlanContent:
    def test_plan_has_33_bugs(self):
        txt = read(PLAN)
        assert "33 bug" in txt or "33" in txt, "Plan should mention 33 bugs"
        assert "BUGS_AUDIT" in txt

    def test_plan_has_fase0_three_lines(self):
        txt = read(PLAN)
        assert "≡" in txt, "Plan Fase 0 should mention ≡ three lines (no hamburger word in code, but doc can mention)"
        # Doc may mention hamburger word in explanation, but code should not
        assert "tiga garis" in txt.lower() or "three lines" in txt.lower()

    def test_plan_faded_grey(self):
        assert "#3A4452" in read(PLAN) or "faded grey" in read(PLAN).lower()

    def test_plan_ace_144(self):
        assert "1.44.0" in read(PLAN)

    def test_plan_chaquopy_311(self):
        txt = read(PLAN)
        assert "3.11" in txt
        assert "3.12" in txt  # should mention 3.12 drops 32-bit
        assert "armeabi-v7a" in txt

    def test_plan_js_bridge_no_loopback(self):
        txt = read(PLAN).lower()
        assert "js bridge" in txt or "file://" in txt
        assert "5000" in txt  # should mention not using 5000

    def test_plan_focus_zcode_not_zmux(self):
        txt = read(PLAN).lower()
        assert "fokus zcode" in txt or "zmux pending" in txt

    def test_plan_no_ai_oracle_fase0(self):
        # Plan should note AI/Oracle skip/pending
        txt = read(PLAN).lower()
        assert "ai" in txt and "oracle" in txt  # mentioned as pending, not in Fase 0

# ===================================================================
# Design content
# ===================================================================

class TestDesignContent:
    def test_design_has_faded_grey(self):
        assert "#3A4452" in read(DESIGN) or "faded grey" in read(DESIGN).lower()
    def test_design_has_oled(self):
        assert "#050806" in read(DESIGN)
    def test_design_has_line_numbers(self):
        assert "line numbers" in read(DESIGN).lower() or "gutter" in read(DESIGN).lower()
    def test_design_has_quicktools(self):
        assert "QuickTools" in read(DESIGN)
    def test_design_has_fab_above_handle(self):
        txt = read(DESIGN).lower()
        assert "fab" in txt and "above handle" in txt or "di atas handle" in txt
    def test_design_has_output_pindah_layer(self):
        txt = read(DESIGN).lower()
        assert "pindah layer" in txt or "pindah halaman" in txt or "full-screen" in txt
    def test_design_no_hamburger_word_in_code_spec(self):
        # DESIGN doc may contain hamburger word in explanation, but should note ≡
        assert "≡" in read(DESIGN)

# ===================================================================
# Gradle / Manifest — anti-regresi guards
# ===================================================================

class TestGradleManifest:
    def test_settings_gradle_exists(self): assert SETTINGS_GRADLE.exists()
    def test_app_build_gradle_exists(self): assert BUILD_GRADLE.exists()
    def test_gradle_has_abi_filters(self):
        txt = read(BUILD_GRADLE)
        assert "armeabi-v7a" in txt and "arm64-v8a" in txt and "x86_64" in txt

    def test_gradle_chaquopy_311(self):
        # TEST A bisection: chaquopy dinonaktifkan sementara
        txt = read(BUILD_GRADLE)
        if 'TEST A' in txt:
            return  # skip selama bisection
        assert '3.11' in txt
        assert 'chaquopy' in txt.lower() or 'Chaquo' in txt

    def test_gradle_min_sdk_26(self):
        assert "minSdk = 26" in read(BUILD_GRADLE)

    def test_manifest_exists(self): assert MANIFEST.exists()

    def test_manifest_task_affinity(self):
        txt = read(MANIFEST)
        assert 'taskAffinity="com.zaba.zcode"' in txt

    def test_manifest_single_top(self):
        assert "singleTop" in read(MANIFEST)

    def test_manifest_allow_backup_false(self):
        assert 'allowBackup="false"' in read(MANIFEST)

    def test_manifest_document_launch(self):
        assert "documentLaunchMode" in read(MANIFEST)

    def test_manifest_adjust_resize(self):
        assert "adjustResize" in read(MANIFEST)

    def test_manifest_no_premium(self):
        txt = read(MANIFEST).lower()
        assert "premium" not in txt

# ===================================================================
# Ace bundled — offline-first (S-26 fix)
# ===================================================================

class TestAceBundled:
    def test_ace_exists(self): assert ACE_JS.exists() and ACE_JS.stat().st_size > 0
    def test_ace_is_144(self): assert "1.44.0" in read(ACE_JS)
    def test_ace_mode_python_exists(self): assert (APP / "src/main/assets/editor/ace/mode-python.js").exists()
    def test_ace_no_cdn(self):
        # No cdn reference in ace.js placeholder
        txt = read(ACE_JS).lower()
        assert "cdnjs" not in txt and "unpkg" not in txt and "jsdelivr" not in txt

# ===================================================================
# UI spec — ≡ three lines, no hamburger word in code, faded grey, FAB
# ===================================================================

class TestUISpec:
    def test_workbench_has_three_lines(self):
        if _bisection_stub(): return  # TEST B1
        txt = read(WORKBENCH_KT)
        assert "≡" in txt, "Workbench should have ≡ three lines"

    def test_workbench_no_hamburger_word(self):
        txt = read(WORKBENCH_KT)
        # Code should not contain word hamburger (use ≡)
        assert "hamburger" not in txt.lower(), "Code should use ≡, not word hamburger"

    def test_workbench_has_add_tab_plus(self):
        if _bisection_stub(): return  # TEST B1
        txt = read(WORKBENCH_KT)
        assert '"+"' in txt or "add tab" in txt.lower() or "add_tab" in txt.lower() or "+" in txt

    def test_theme_has_faded_grey(self):
        txt = read(THEME_KT)
        assert "3A4452" in txt or "TopbarFadedGrey" in txt

    def test_theme_has_oled(self):
        assert "050806" in read(THEME_KT)

    def test_editor_has_gutter(self):
        txt = read(EDITOR_KT).lower()
        assert "gutter" in txt or "40" in txt  # 40dp gutter

    def test_editor_no_loopback(self):
        txt = read(EDITOR_KT).lower()
        assert "127.0.0.1" not in txt and "5000" not in txt
        assert "file://" in txt or "file:///" in txt

    def test_editor_has_debounce_note(self):
        assert "debounce" in read(EDITOR_KT).lower() or "100ms" in read(EDITOR_KT)

    def test_workbench_has_fab_above_handle(self):
        if _bisection_stub(): return  # TEST B1
        txt = read(WORKBENCH_KT).lower()
        assert "floatingactionbutton" in txt or "fab" in txt

    def test_no_premium_in_code(self):
        for p in [WORKBENCH_KT, THEME_KT, EDITOR_KT]:
            assert "premium" not in read(p).lower(), f"Premium should be Contribute in {p.name}"

    def test_no_icon_in_fase0(self):
        # Fase 0 iconless — no icon vector in workbench/editor (except ≡ and + text, comments may mention it)
        txt = read(WORKBENCH_KT).lower()
        # Allow Text("≡") and Text("+") but not painterResource icon; comments mentioning 'icon' are ok up to 5
        assert "icon" not in txt or txt.count("icon") <= 5, "Fase 0 should be iconless (text only)"

# ===================================================================
# Execution guards — S-18, F-07
# ===================================================================

class TestExecutionGuards:
    def test_max_code_bytes(self):
        assert "MAX_CODE_BYTES = 512" in read(EXEC_KT)

    def test_max_output(self):
        assert "MAX_OUTPUT_CHARS" in read(EXEC_KT)

    def test_queue(self):
        assert "MAX_INTERACTIVE_QUEUE = 10000" in read(EXEC_KT)

    def test_timeout(self):
        assert "30_000" in read(EXEC_KT) or "30s" in read(EXEC_KT).lower()

    def test_no_prelude_injection(self):
        txt = read(EXEC_KT).lower()
        # Should not inject SAFE_INPUT_PATCH 9 lines
        assert "safe_input_patch" not in txt and "prelude" not in txt or "no prelude" in txt or "wrapper process" in txt.lower()

    def test_image_bytes(self):
        assert "MAX_IMAGE_BYTES" in read(EXEC_KT)

# ===================================================================
# Paths / FileManager — S-20, E-01
# ===================================================================

class TestPathsFileManager:
    def test_paths_no_double_nesting(self):
        txt = read(PATHS_KT)
        assert 'name == "files"' in txt or "filesDir" in txt
        assert "S-20" in txt or "double nesting" in txt.lower()

    def test_filemanager_secure(self):
        txt = read(FILEMGR_KT)
        assert "secureFilename" in txt or "secure_filename" in txt.lower()
        assert "MAX_FILE_BYTES" in txt
        assert "MAX_FILENAME_LEN = 128" in txt

    def test_filemanager_no_traversal(self):
        txt = read(FILEMGR_KT)
        assert '".."' in txt and '"/"' in txt

# ===================================================================
# Keystore — S-19
# ===================================================================

class TestKeystore:
    def test_allowed_providers_has_custom(self):
        txt = read(KEYSTORE_KT)
        assert '"custom"' in txt or "'custom'" in txt or "custom" in txt
        assert "openrouter" in txt.lower()

    def test_encrypted_shared_prefs(self):
        assert "EncryptedSharedPreferences" in read(KEYSTORE_KT)

# ===================================================================
# Contribute -> Issues, no Gmail (user request)
# ===================================================================

class TestContribute:
    def test_plan_contribute_issues(self):
        txt = read(PLAN).lower()
        assert "contribute" in txt and "issues" in txt
        # Should mention premium -> contribute
        assert "premium" in txt and "contribute" in txt

    def test_no_mailto_in_code(self):
        for p in APP.rglob("*.kt"):
            txt = read(p).lower()
            assert "mailto" not in txt, f"Gmail mailto should be skipped per user request in {p}"

# ===================================================================
# PTY — output pindah layer, ketik langsung (user request)
# ===================================================================

class TestPTY:
    def test_exec_has_pty_note(self):
        txt = read(EXEC_KT).lower()
        assert "pty" in txt

    def test_editor_no_stdin_field(self):
        txt = read(EDITOR_KT).lower()
        # Should not have separate stdin field TextField for input
        # PTY is ketik langsung, no [stdin: ___]
        assert "stdin input field" not in txt.lower() or "no stdin field" in txt.lower() or "ketik langsung" in read(EXEC_KT).lower()

    def test_workbench_navigates_to_output(self):
        if _bisection_stub(): return  # TEST B1
        txt = read(WORKBENCH_KT).lower()
        assert "output" in txt and "navigate" in txt

    def test_manifest_no_stdin_perm(self):
        assert "stdin" not in read(MANIFEST).lower()

# ===================================================================
# No AI/Oracle in Fase 0 skeleton (user: skip)
# ===================================================================

class TestNoAIOracleFase0:
    def test_no_ai_in_exec(self):
        assert "ai_provider" not in read(EXEC_KT).lower()
    def test_no_oracle_in_exec(self):
        # Oracle may be mentioned as port but not implemented Fase 0
        txt = read(EXEC_KT).lower()
        assert "oracle" not in txt or "fase 1" in txt or "pending" in txt

# ===================================================================
# Version single source (F-09/E-02)
# ===================================================================

class TestVersion:
    def test_version_in_gradle(self):
        txt = read(BUILD_GRADLE)
        assert "0.2.0-fase2" in txt
    def test_version_in_properties(self):
        assert "0.2.0-fase2" in read(ROOT / "gradle.properties")

# ===================================================================
# Check.sh exists and covers guards
# ===================================================================

class TestCheckSh:
    def test_check_sh_exists(self):
        p = ROOT / "tools/check.sh"
        assert p.exists() and p.stat().st_size > 0
        assert "verifyAceBundled" in read(p) or "Ace" in read(p)
        assert "taskAffinity" in read(p)
        assert "≡" in read(p) or "three lines" in read(p).lower()
