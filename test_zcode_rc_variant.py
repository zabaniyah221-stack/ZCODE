"""Permanent structural guards for the v1.0.20 RC optimized-build gate.

RC1 promotes the device-verified optimized configuration only: no runtime toggle, recorder,
Compose upgrade, or production signing. Pattern guards strip Kotlin comments so
a historical comment can never satisfy a live invariant.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app"
BUILD = APP / "build.gradle.kts"
RULES = APP / "proguard-rc.pro"
RC_MANIFEST = APP / "src/rc/AndroidManifest.xml"
RC_STRINGS = APP / "src/rc/res/values/strings.xml"
WORKFLOW = ROOT / ".github/workflows/rc.yml"
WORKFLOW_MIRROR = ROOT / "ci/workflows/rc.yml"
CANONICAL_WORKFLOW = ROOT / ".github/workflows/build.yml"
CANONICAL_WORKFLOW_MIRROR = ROOT / "ci/workflows/build.yml"
SIGNING_POLICY = ROOT / "docs/SIGNING_ZCODE.md"
ROADMAP = ROOT / "docs/ROADMAP_V1020_OPTIMIZED_BUILD.md"
RELEASE_NOTES = ROOT / "docs/RELEASE_NOTES_V1.0.20_RC1.md"
SKILLS = ROOT / "docs/SKILLS.md"
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


def rc_block() -> str:
    src = strip_kt_comments(read(BUILD))
    marker = 'create("rc")'
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
    raise AssertionError("RC build block tidak tertutup")


class TestRcBuildContract:
    def test_variant_isolated_non_debuggable_profileable_and_optimized(self):
        block = rc_block()
        required = [
            'applicationIdSuffix = ".rc"',
            'versionNameSuffix = "-rc1"',
            "isDebuggable = false",
            "isProfileable = true",
            "isMinifyEnabled = true",
            "isShrinkResources = false",
            'signingConfigs.getByName("debug")',
            'matchingFallbacks += listOf("release")',
            '"proguard-rc.pro"',
            'getDefaultProguardFile("proguard-android-optimize.txt")',
        ]
        for token in required:
            assert token in block, f"kontrak RC hilang: {token}"
        assert "initWith" not in block, (
            "RC tidak boleh mewarisi release keep-all secara diam-diam"
        )

    def test_version_identity_is_exact_rc1(self):
        props = read(ROOT / "gradle.properties")
        assert "zcode.versionName=1.0.20" in props
        assert "zcode.versionCode=23" in props
        assert "1.0.19" not in props

    def test_legacy_performance_variant_has_been_retired(self):
        src = strip_kt_comments(read(BUILD))
        assert 'create("performance")' not in src
        for old_path in (
            APP / "proguard-performance.pro",
            APP / "src/performance",
            ROOT / ".github/workflows/performance.yml",
            ROOT / "ci/workflows/performance.yml",
            ROOT / "test_zcode_performance_variant.py",
        ):
            assert not old_path.exists(), f"legacy Performance path masih hidup: {old_path}"

    def test_debug_and_release_contract_are_not_repurposed(self):
        src = strip_kt_comments(read(BUILD))
        debug = src[src.index("debug {"):src.index("release {")]
        release = src[src.index("release {"):src.index('create("rc")')]
        assert 'applicationIdSuffix = ".debug"' in debug
        assert "isMinifyEnabled = false" in debug
        assert "isMinifyEnabled = false" in release

    def test_r8_compat_mode_is_explicit_and_temporary(self):
        props = read(ROOT / "gradle.properties")
        assert "android.enableR8.fullMode=false" in props
        assert "hapus" in props.lower() and "device uat" in props.lower()


class TestRcR8Boundaries:
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
            assert token not in src, f"R8 RC dilumpuhkan oleh {token}"
        assert "-dontobfuscate" in src, "RC1 harus menjaga stacktrace tetap terbaca"

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


class TestRcCoexistence:
    def test_manifest_overlay_has_unique_label_and_task(self):
        root = ET.parse(RC_MANIFEST).getroot()
        app = root.find("application")
        assert app is not None
        assert app.get(ANDROID + "label") == "@string/app_name"
        activity = app.find("activity")
        assert activity is not None
        assert activity.get(ANDROID + "name") == ".MainActivity"
        assert activity.get(ANDROID + "taskAffinity") == "com.zaba.zcode.rc"
        assert "ZCODE RC" in read(RC_STRINGS)

    def test_rc_does_not_change_rebirth_or_permissions(self):
        overlay = read(RC_MANIFEST)
        assert "ZcodeRebirthActivity" not in overlay
        assert "uses-permission" not in overlay
        main = read(APP / "src/main/AndroidManifest.xml")
        assert 'android:process=":rebirth"' in main
        assert 'android:exported="false"' in main


class TestRcWorkflow:
    def source(self) -> str:
        return read(WORKFLOW)

    def test_workflow_and_mirror_are_identical(self):
        assert read(WORKFLOW) == read(WORKFLOW_MIRROR)

    def test_only_rc_variant_is_built(self):
        src = self.source()
        assert "assembleRc" in src
        assert "assembleDebug" not in src
        assert "assemblePerformance" not in src
        assert 'branches: [ "arena/v1020-rc1" ]' in src
        assert "workflow_dispatch:" in src

    def test_canonical_debug_push_excludes_rc_branch(self):
        canonical = read(CANONICAL_WORKFLOW)
        assert canonical == read(CANONICAL_WORKFLOW_MIRROR)
        assert '"!arena/v1020-rc1"' in canonical
        assert (
            "github.head_ref != 'arena/v1020-rc1'"
        ) in canonical, "PR RC masih membangun/upload APK Debug kedua"
        assert 'branches: [ main, "arena/**" ]' not in canonical, (
            "canonical Debug masih menelan semua arena/** termasuk RC"
        )

    def test_apk_contract_is_verified(self):
        src = self.source()
        for token in (
            "com.zaba.zcode.rc", "ZCODE RC",
            "application-debuggable", "profileable", ":rebirth",
            "codemirror.bundle.js", "assets/chaquopy", "apksigner",
            "sha256sum",
        ):
            assert token in src, f"workflow tidak memverifikasi {token}"

    def test_exactly_one_user_apk_and_r8_reports_are_separate(self):
        src = self.source()
        assert "ZCODE-v1.0.20-rc1" in src
        assert "ZCODE-v1.0.20-rc1-technical-reports" in src
        assert "app/build/outputs/mapping/rc" in src
        assert "retention-days: 14" in src
        assert "zcode-rc-apk" in src
        assert "zcode-rc-reports" in src
        assert "ZCODE-Fase12-APK" not in src
        assert "app/build/outputs/apk/debug" not in src
        assert (
            "sha256sum ZCODE-v1.0.20-rc1.apk > "
            "ZCODE-v1.0.20-rc1.apk.sha256"
        ) in src, "APK dan checksum harus ikut artifact user"
        assert "find app/build/outputs/apk/rc" in src

    def test_no_release_secret_or_keystore_contract(self):
        src = self.source()
        assert "secrets." not in src
        assert "RELEASE_STORE" not in src
        assert ".jks" not in src and ".keystore" not in src


class TestRcScope:
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

    def test_public_signing_identity_is_recorded_without_false_release_claim(self):
        policy = read(SIGNING_POLICY)
        for token in (
            "Alias                    : zcode-release",
            "Public key               : RSA 4096-bit",
            "Certificate signature    : SHA384withRSA",
            "401392193b734263c8ecce93e12be1f7f307203afe4282dc2550094088f38bd2",
            "BYTE-FOR-BYTE RECOVERY DRILL: NOT EVIDENCED IN REPO",
            "CI PRODUCTION SIGNING       : NOT CONFIGURED",
            "PRODUCTION APK SIGNED       : NO",
            "PUBLIC RELEASE              : NO",
        ):
            assert token in policy, f"signing policy kehilangan fakta/batas: {token}"
        assert "zcode-release.jks\n" in policy
        assert "Private key tidak pernah dikirim kepada agent" in policy

    def test_rc_release_notes_roadmap_and_playbook_are_explicit(self):
        notes = read(RELEASE_NOTES)
        roadmap = read(ROADMAP)
        skills = read(SKILLS)
        for token in (
            "com.zaba.zcode.rc",
            "1.0.20-rc1",
            "User-facing APKs : exactly one",
            "ephemeral CI debug key",
            "not a public release",
            "Project Workbench remains parked for v1.0.25",
        ):
            assert token in notes, f"RC release notes kehilangan: {token}"
        for token in (
            "PROMOTION TO v1.0.20-rc1",
            "exactly one APK + SHA-256 + signer report",
            "Thirteen mutations were proven red",
            "Full local gate               : 626 PASSED",
            "Production signing            : NOT CONFIGURED",
        ):
            assert token in roadmap, f"roadmap RC kehilangan: {token}"
        for token in (
            "SKILL 25 — Release Candidate",
            "Canonical Debug harus mengecualikan push",
            "Technical R8 artifact terpisah boleh ada, tetapi tidak boleh membawa APK",
            "RC CI VERIFIED",
            "PRODUCTION SIGNED",
        ):
            assert token in skills, f"SKILLS RC kehilangan: {token}"

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
