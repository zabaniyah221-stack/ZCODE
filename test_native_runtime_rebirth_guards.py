"""Permanent structural guards for the native-runtime rebirth contract.

These tests intentionally inspect comment-stripped Kotlin where practical: a comment
must never satisfy a lifecycle safety invariant.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app/src/main"
KT = APP / "java/com/zaba/zcode"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def text(path):
    return path.read_text(encoding="utf-8")


def strip_kt_comments(src):
    """Single-pass scanner which does not mistake text/* or URLs in strings for comments."""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if c == '"':
            if src.startswith('"""', i):
                j = src.find('"""', i + 3)
                j = n if j < 0 else j + 3
            else:
                j = i + 1
                while j < n and src[j] != '"':
                    j += 2 if src[j] == "\\" else 1
                j = min(j + 1, n)
            out.append(src[i:j])
            i = j
        elif c == "/" and nxt == "/":
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif c == "/" and nxt == "*":
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
            out.append(" ")
        else:
            out.append(c)
            i += 1
    return "".join(out)


class TestRebirthManifest:
    def activity(self):
        root = ET.parse(APP / "AndroidManifest.xml").getroot()
        matches = [x for x in root.findall("./application/activity")
                   if x.get(ANDROID + "name") == ".ZcodeRebirthActivity"]
        assert len(matches) == 1
        return matches[0]

    def test_helper_is_private_isolated_process(self):
        node = self.activity()
        assert node.get(ANDROID + "process") == ":rebirth"
        assert node.get(ANDROID + "exported") == "false"
        assert node.get(ANDROID + "excludeFromRecents") == "true"
        assert node.get(ANDROID + "noHistory") == "true"

    def test_no_alarm_relaunch_or_exact_alarm_permission(self):
        manifest = text(APP / "AndroidManifest.xml")
        active = "\n".join(strip_kt_comments(text(p)) for p in KT.rglob("*.kt"))
        assert "SCHEDULE_EXACT_ALARM" not in manifest
        assert "USE_EXACT_ALARM" not in manifest
        assert "AlarmManager" not in active


class TestRebirthSafety:
    def test_relaunch_is_explicit_and_kills_only_recorded_old_pid(self):
        src = strip_kt_comments(text(KT / "ZcodeRebirthActivity.kt"))
        assert "Intent(this, MainActivity::class.java)" in src
        assert "Process.killProcess(oldPid)" in src
        assert "oldPid == Process.myPid()" in src
        assert "FLAG_ACTIVITY_NEW_TASK" in src and "FLAG_ACTIVITY_CLEAR_TASK" in src
        assert "finishAndRemoveTask" not in src
        assert "finish()" in src

    def test_helper_process_skips_normal_application_init(self):
        src = strip_kt_comments(text(KT / "ZcodeApp.kt"))
        guard = src.index('endsWith(":rebirth")')
        assert src.index("Breadcrumb.init(this)") > guard
        assert src.index("CrashReporter.install") > guard
        assert src.index("TelemetryStore.init") > guard

    def test_save_is_verified_before_receipt_and_helper(self):
        main = strip_kt_comments(text(KT / "MainActivity.kt"))
        save = main.index("flushSaveSync(verifyAllDrafts = true)")
        receipt = main.index("NativeRuntimeState.prepareRestart")
        helper = main.index("startActivity(ZcodeRebirthActivity.intent")
        assert save < receipt < helper
        assert "return false" in main[save:receipt]
        vm = strip_kt_comments(text(KT / "WorkspaceViewModel.kt"))
        assert "fun flushSaveSync(verifyAllDrafts: Boolean = false): Boolean" in vm
        assert "result.isFailure" in vm
        assert ".commit()" in vm

    def test_receipt_is_durable_and_new_pid_clears_stale_state(self):
        src = strip_kt_comments(text(KT / "core/runtime/NativeRuntimeState.kt"))
        assert src.count(".commit()") >= 3
        assert "previousPid != Process.myPid()" in src
        assert ".putBoolean(REQUIRED, false)" in src
        assert ".remove(RECEIPT)" in src


class TestNativeStaleGates:
    def test_native_evidence_marks_stale_but_has_no_package_hardcode(self):
        engine = strip_kt_comments(text(KT / "core/packageengine/PackageEngineV2.kt"))
        assert "outcome.nativeLibs.isNotEmpty()" in engine
        # Exactly four generic producers: transaction pre-smoke evidence,
        # per-outcome native evidence, activated native environment, and native
        # uninstall. An unconditional fifth producer would make pure Python
        # installs stale and must fail this guard.
        assert engine.count("NativeRuntimeState.markRequired") == 4
        assert "transactionNativePackages.isNotEmpty()" in engine
        assert engine.index('"native-smoke-start"') < engine.index("smokeRunner.run")
        assert "nativeTouched.isNotEmpty()" in engine
        assert "if (hadNative)" in engine
        lifecycle = "\n".join(strip_kt_comments(text(p)) for p in [
            KT / "core/runtime/NativeRuntimeState.kt",
            KT / "ZcodeRebirthActivity.kt",
            KT / "MainActivity.kt",
        ]).lower()
        assert "bokeh" not in lifecycle
        assert "contourpy" not in lifecycle

    def test_run_install_and_uninstall_are_gated_while_stale(self):
        workbench = strip_kt_comments(text(KT / "ui/workbench/WorkbenchScreen.kt"))
        pip = strip_kt_comments(text(KT / "ui/settings/PipScreen.kt"))
        assert "RUN_BLOCKED_RUNTIME_STALE" in workbench
        assert "NativeRuntimeState.isRequired(context)" in workbench
        assert pip.count("runtimeStale || NativeRuntimeState.isRequired(context)") >= 3
        assert "installQueue.isNotEmpty() && !runtimeStale" in pip

    def test_binary_rain_uses_only_repeating_zcode_ascii_binary(self):
        src = strip_kt_comments(text(KT / "BinaryRainView.kt"))
        expected = "01011010" + "01000011" + "01001111" + "01000100" + "01000101"
        assert f'private val sequence = "{expected}"' in src
        assert "sequence[(offset + row) % sequence.length]" in src
        assert "20L" not in src  # don't accidentally raise this low-end transition to 50 FPS
