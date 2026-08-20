"""Permanent structural guards for the v1.0.20 optimized-build gate.

P0 intentionally changes build configuration only: no runtime toggle, recorder,
Compose upgrade, or production signing. Pattern guards strip Kotlin comments so
a historical comment can never satisfy a live invariant.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app"
BUILD = APP / "build.gradle.kts"
RULES = APP / "proguard-performance.pro"
PERF_MANIFEST = APP / "src/performance/AndroidManifest.xml"
PERF_STRINGS = APP / "src/performance/res/values/strings.xml"
WORKFLOW = ROOT / ".github/workflows/performance.yml"
WORKFLOW_MIRROR = ROOT / "ci/workflows/performance.yml"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def strip_kt_comments(src: str) -> str:
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


def performance_block() -> str:
    src = strip_kt_comments(read(BUILD))
    marker = 'create("performance")'
    start = src.index(marker)
    brace = src.index("{", start)
    depth = 0
    in_string = False
    escaped = False
    for i in range(brace, len(src)):
        ch = src[i]
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return src[start:i + 1]
    raise AssertionError("performance build block tidak tertutup")


class TestPerformanceBuildContract:
    def test_variant_isolated_non_debuggable_profileable_and_optimized(self):
        block = performance_block()
        required = [
            'applicationIdSuffix = ".performance"',
            'versionNameSuffix = "-perf1"',
            "isDebuggable = false",
            "isProfileable = true",
            "isMinifyEnabled = true",
            "isShrinkResources = false",
            'signingConfigs.getByName("debug")',
            'matchingFallbacks += listOf("release")',
            '"proguard-performance.pro"',
            'getDefaultProguardFile("proguard-android-optimize.txt")',
        ]
        for token in required:
            assert token in block, f"kontrak performance hilang: {token}"
        assert "initWith" not in block, (
            "performance tidak boleh mewarisi release keep-all secara diam-diam"
        )

    def test_debug_and_release_contract_are_not_repurposed(self):
        src = strip_kt_comments(read(BUILD))
        debug = src[src.index("debug {"):src.index("release {")]
        release = src[src.index("release {"):src.index('create("performance")')]
        assert 'applicationIdSuffix = ".debug"' in debug
        assert "isMinifyEnabled = false" in debug
        assert "isMinifyEnabled = false" in release

    def test_r8_compat_mode_is_explicit_and_temporary(self):
        props = read(ROOT / "gradle.properties")
        assert "android.enableR8.fullMode=false" in props
        assert "hapus" in props.lower() and "device uat" in props.lower()


class TestPerformanceR8Boundaries:
    def source(self) -> str:
        return read(RULES)

    def test_no_global_zcode_keep_all(self):
        src = self.source()
        forbidden = [
            "-keep class com.zaba.zcode.** { *; }",
            "-keep **",
            "-dontshrink",
            "-dontoptimize",
        ]
        for token in forbidden:
            assert token not in src, f"R8 performance dilumpuhkan oleh {token}"
        assert "-dontobfuscate" in src, "perf1 harus menjaga stacktrace tetap terbaca"

    def test_javascript_bridge_is_kept_by_runtime_annotation(self):
        src = self.source()
        assert "@android.webkit.JavascriptInterface <methods>;" in src
        assert "-keepclassmembers,allowoptimization class *" in src
        assert "RuntimeVisibleAnnotations" in src

    def test_python_bridges_keep_public_runtime_api(self):
        src = self.source()
        for class_name in (
            "com.zaba.zcode.core.execution.TerminalBridge",
            "com.zaba.zcode.core.packageengine.ResolveOperationBridge",
        ):
            assert f"-keep,allowoptimization class {class_name} {{ public *; }}" in src

    def test_chaquopy_and_android_entrypoints_are_kept(self):
        src = self.source()
        assert "-keep class com.chaquo.python.** { *; }" in src
        for class_name in ("ZcodeApp", "MainActivity", "ZcodeRebirthActivity"):
            assert f"class com.zaba.zcode.{class_name}" in src


class TestPerformanceCoexistence:
    def test_manifest_overlay_has_unique_label_and_task(self):
        root = ET.parse(PERF_MANIFEST).getroot()
        app = root.find("application")
        assert app is not None
        assert app.get(ANDROID + "label") == "@string/app_name"
        activity = app.find("activity")
        assert activity is not None
        assert activity.get(ANDROID + "name") == ".MainActivity"
        assert activity.get(ANDROID + "taskAffinity") == "com.zaba.zcode.performance"
        assert "ZCODE Performance" in read(PERF_STRINGS)

    def test_performance_does_not_change_rebirth_or_permissions(self):
        overlay = read(PERF_MANIFEST)
        assert "ZcodeRebirthActivity" not in overlay
        assert "uses-permission" not in overlay
        main = read(APP / "src/main/AndroidManifest.xml")
        assert 'android:process=":rebirth"' in main
        assert 'android:exported="false"' in main


class TestPerformanceWorkflow:
    def source(self) -> str:
        return read(WORKFLOW)

    def test_workflow_and_mirror_are_identical(self):
        assert read(WORKFLOW) == read(WORKFLOW_MIRROR)

    def test_only_performance_variant_is_built(self):
        src = self.source()
        assert "assemblePerformance" in src
        assert "assembleDebug" not in src
        assert 'branches: [ "arena/v1020-performance" ]' in src
        assert "workflow_dispatch:" in src

    def test_apk_contract_is_verified(self):
        src = self.source()
        for token in (
            "com.zaba.zcode.performance", "ZCODE Performance",
            "application-debuggable", "profileable", ":rebirth",
            "codemirror.bundle.js", "assets/chaquopy", "apksigner",
            "sha256sum",
        ):
            assert token in src, f"workflow tidak memverifikasi {token}"

    def test_user_apk_and_r8_reports_are_separate(self):
        src = self.source()
        assert "ZCODE-v1.0.19-perf1" in src
        assert "ZCODE-v1.0.19-perf1-technical-reports" in src
        assert "app/build/outputs/mapping/performance" in src
        assert "retention-days: 14" in src

    def test_no_release_secret_or_keystore_contract(self):
        src = self.source()
        assert "secrets." not in src
        assert "RELEASE_STORE" not in src
        assert ".jks" not in src and ".keystore" not in src


class TestPerformanceP0Scope:
    def test_no_recorder_or_runtime_toggle_dependency(self):
        build = strip_kt_comments(read(BUILD))
        java = "\n".join(
            strip_kt_comments(read(p)) for p in (APP / "src/main/java").rglob("*.kt")
        )
        assert "metrics-performance" not in build
        assert "JankStats" not in java
        assert "FrameMetricsAggregator" not in java
        assert "Performance Recorder" not in java

    def test_compose_python_and_chaquopy_versions_stay_fixed(self):
        build = read(BUILD)
        root_build = read(ROOT / "build.gradle.kts")
        assert 'compose-bom:2024.02.00' in build
        assert 'version = "3.11"' in build
        assert 'com.chaquo.python") version "17.0.0"' in root_build

    def test_no_signing_material_in_repository(self):
        forbidden_suffixes = (".jks", ".keystore", ".p12", ".pfx")
        gitignore = read(ROOT / ".gitignore")
        for suffix in forbidden_suffixes:
            assert f"*{suffix}" in gitignore, f"gitignore belum menjaga {suffix}"
        found = [
            str(p.relative_to(ROOT)) for p in ROOT.rglob("*")
            if p.is_file() and p.suffix.lower() in forbidden_suffixes and ".git" not in p.parts
        ]
        assert not found, f"signing material tidak boleh ada di repo: {found}"
        patterns = re.compile(("github" + "_pat_") + "|" + ("gh" + "p_") + r"[A-Za-z0-9]{20,}")
        hits = []
        for p in ROOT.rglob("*"):
            if (
                not p.is_file() or ".git" in p.parts or
                ".pytest_cache" in p.parts or "__pycache__" in p.parts
            ):
                continue
            try:
                if patterns.search(read(p)):
                    hits.append(str(p.relative_to(ROOT)))
            except (UnicodeDecodeError, OSError):
                pass
        assert not hits, f"credential-like token ditemukan: {hits}"
