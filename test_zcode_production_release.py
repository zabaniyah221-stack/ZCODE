"""Permanent guards for the single-build ZCODE v1.0.21 production candidate.

Production signing is fail-closed: source may be reviewed without secrets, but
assembleRelease must never fall back to a debug key. One manually approved build
creates one APK, verifies its public signer fingerprint, and attaches those exact
bytes to a draft GitHub Release for device UAT before publication.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app"
BUILD = APP / "build.gradle.kts"
RULES = APP / "proguard-release.pro"
MAIN_MANIFEST = APP / "src/main/AndroidManifest.xml"
MAIN_STRINGS = APP / "src/main/res/values/strings.xml"
WORKFLOW = ROOT / ".github/workflows/production.yml"
WORKFLOW_MIRROR = ROOT / "ci/workflows/production.yml"
CANONICAL_WORKFLOW = ROOT / ".github/workflows/build.yml"
CANONICAL_WORKFLOW_MIRROR = ROOT / "ci/workflows/build.yml"
SIGNING_POLICY = ROOT / "docs/SIGNING_ZCODE.md"
ROADMAP = ROOT / "docs/ROADMAP_V1020_OPTIMIZED_BUILD.md"
RELEASE_NOTES = ROOT / "docs/RELEASE_NOTES_V1.0.21.md"
SKILLS = ROOT / "docs/SKILLS.md"
EXPECTED_SIGNER = "401392193b734263c8ecce93e12be1f7f307203afe4282dc2550094088f38bd2"


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


def build_type_block(name: str) -> str:
    src = strip_kt_comments(read(BUILD))
    marker = f"{name} {{"
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
    raise AssertionError(f"build type {name} tidak tertutup")


class TestProductionBuildContract:
    def test_release_is_optimized_non_debuggable_non_profileable_and_signed(self):
        release = build_type_block("release")
        for token in (
            "isDebuggable = false",
            "isProfileable = false",
            "isMinifyEnabled = true",
            "isShrinkResources = false",
            'signingConfigs.getByName("production")',
            'getDefaultProguardFile("proguard-android-optimize.txt")',
            '"proguard-release.pro"',
        ):
            assert token in release, f"release contract hilang: {token}"
        assert "signingConfigs.getByName(\"debug\")" not in release
        assert "applicationIdSuffix" not in release
        assert "versionNameSuffix" not in release

    def test_production_signing_reads_four_environment_values_without_fallback(self):
        src = strip_kt_comments(read(BUILD))
        for token in (
            'create("production")',
            '"ZCODE_RELEASE_STORE_FILE"',
            '"ZCODE_RELEASE_STORE_PASSWORD"',
            '"ZCODE_RELEASE_KEY_ALIAS"',
            '"ZCODE_RELEASE_KEY_PASSWORD"',
        ):
            assert token in src, f"production signing config kehilangan: {token}"
        signing_start = src.index('create("production")')
        signing_end = src.index("buildTypes {", signing_start)
        signing = src[signing_start:signing_end]
        assert "debug" not in signing.lower()
        assert ".jks" not in signing and ".keystore" not in signing
        assert "System.getenv" in signing

    def test_version_and_main_identity_are_exact(self):
        props = read(ROOT / "gradle.properties")
        assert "zcode.versionName=1.0.21" in props
        assert "zcode.versionCode=24" in props
        manifest = read(MAIN_MANIFEST)
        strings = read(MAIN_STRINGS)
        assert 'android:taskAffinity="com.zaba.zcode"' in manifest
        assert 'android:label="ZCODE"' in manifest
        assert '<string name="app_name">ZCODE</string>' in strings

    def test_rc_and_performance_build_paths_are_retired(self):
        src = strip_kt_comments(read(BUILD))
        assert 'create("rc")' not in src
        assert 'create("performance")' not in src
        for old in (
            APP / "proguard-rc.pro",
            APP / "proguard-performance.pro",
            APP / "src/rc",
            APP / "src/performance",
            ROOT / ".github/workflows/rc.yml",
            ROOT / ".github/workflows/performance.yml",
            ROOT / "ci/workflows/rc.yml",
            ROOT / "ci/workflows/performance.yml",
            ROOT / "test_zcode_rc_variant.py",
            ROOT / "test_zcode_performance_variant.py",
        ):
            assert not old.exists(), f"legacy pre-production path masih hidup: {old}"


class TestProductionR8Boundaries:
    def source(self) -> str:
        return read(RULES)

    def test_optimization_is_real_but_stacktraces_remain_readable(self):
        src = self.source()
        for forbidden in (
            "-keep class com.zaba.zcode.** { *; }",
            "-keep **",
            "-dontshrink",
            "-dontoptimize",
        ):
            assert forbidden not in src, f"production R8 dilumpuhkan oleh {forbidden}"
        assert "-dontobfuscate" in src

    def test_runtime_boundaries_are_kept(self):
        src = self.source()
        assert "@android.webkit.JavascriptInterface <methods>;" in src
        assert "-keepclassmembers,allowoptimization class *" in src
        assert "RuntimeVisibleAnnotations" in src
        for class_name in (
            "com.zaba.zcode.core.execution.TerminalBridge",
            "com.zaba.zcode.core.packageengine.ResolveOperationBridge",
        ):
            assert f"-keep,allowoptimization class {class_name} {{ public *; }}" in src
        assert "-keep class com.chaquo.python.** { *; }" in src
        for class_name in ("ZcodeApp", "MainActivity", "ZcodeRebirthActivity"):
            assert f"class com.zaba.zcode.{class_name}" in src


class TestSingleProductionWorkflow:
    def source(self) -> str:
        return read(WORKFLOW)

    def test_workflow_and_mirrors_are_identical(self):
        assert self.source() == read(WORKFLOW_MIRROR)
        assert read(CANONICAL_WORKFLOW) == read(CANONICAL_WORKFLOW_MIRROR)
        for name in ("build-wheels.yml",):
            assert read(ROOT / ".github/workflows" / name) == read(ROOT / "ci/workflows" / name)

    def test_every_third_party_action_is_pinned_to_full_commit_sha(self):
        for workflow in (ROOT / ".github/workflows").glob("*.yml"):
            for line in read(workflow).splitlines():
                if "uses:" not in line:
                    continue
                ref = line.split("uses:", 1)[1].strip().split()[0]
                assert re.search(r"@[0-9a-f]{40}$", ref), (
                    f"{workflow.name} action tidak dipin full SHA: {ref}"
                )

    def test_manual_protected_production_only(self):
        src = self.source()
        assert "workflow_dispatch:" in src
        assert "push:" not in src and "pull_request:" not in src
        assert "environment: production" in src
        assert "permissions:" in src and "contents: write" in src
        assert "BUILD-v1.0.21" in src
        assert "concurrency:" in src

    def test_exactly_one_release_apk_is_built(self):
        src = self.source()
        assert "assembleRelease" in src
        assert "assembleDebug" not in src
        assert "assembleRc" not in src
        assert "assemblePerformance" not in src
        assert "find app/build/outputs/apk/release" in src
        assert "app/build/outputs/apk/debug" not in src
        assert "ZCODE-v1.0.21.apk" in src
        assert "ZCODE-Fase12-APK" not in src
        assert "ZCODE-v1.0.20-rc1" not in src

    def test_secret_boundary_is_exact_and_cleanup_is_unconditional(self):
        src = self.source()
        for secret in (
            "secrets.ZCODE_RELEASE_KEYSTORE_B64",
            "secrets.ZCODE_RELEASE_STORE_PASSWORD",
            "secrets.ZCODE_RELEASE_KEY_ALIAS",
            "secrets.ZCODE_RELEASE_KEY_PASSWORD",
        ):
            assert secret in src, f"workflow kehilangan secret contract: {secret}"
        build_job = src[src.index("build-production:"):]
        job_header = build_job[:build_job.index("    steps:")]
        assert "secrets." not in job_header, (
            "signing secrets tidak boleh tersedia untuk semua third-party action"
        )
        assert src.count("secrets.ZCODE_RELEASE_KEYSTORE_B64") == 1
        assert src.count("secrets.ZCODE_RELEASE_STORE_PASSWORD") == 1
        assert src.count("secrets.ZCODE_RELEASE_KEY_ALIAS") == 1
        assert src.count("secrets.ZCODE_RELEASE_KEY_PASSWORD") == 1
        secure_start = src.index("Materialize, build, verify, and destroy production signing identity")
        upload_start = src.index("Upload the one production APK", secure_start)
        secure_step = src[secure_start:upload_start]
        for token in (
            "KEYSTORE_B64",
            "STORE_PASSWORD",
            "KEY_PASSWORD",
            "trap cleanup_keystore EXIT",
            "./gradlew testReleaseUnitTest assembleRelease",
            "apksigner",
            "cleanup_keystore",
            "trap - EXIT",
            'test ! -e "$KEYSTORE"',
        ):
            assert token in secure_step, f"secure signing step kehilangan {token}"
        # Key must be destroyed before the first post-build third-party upload action.
        assert secure_step.rindex("cleanup_keystore") < len(secure_step)
        assert "$RUNNER_TEMP/zcode-release.jks" in src
        assert "shred -u" in src
        assert "if: always()" in src
        assert "signingConfig = signingConfigs.getByName(\"debug\")" not in src

    def test_apk_contract_and_expected_signer_are_verified(self):
        src = self.source()
        for token in (
            "com.zaba.zcode",
            "versionCode='24'",
            "versionName='1.0.21'",
            "application-label:'ZCODE'",
            "application-debuggable",
            "profileable",
            ":rebirth",
            "codemirror.bundle.js",
            "assets/chaquopy",
            "apksigner",
            "sha256sum",
            EXPECTED_SIGNER,
        ):
            assert token in src, f"production APK verification kehilangan: {token}"
        assert "SIGNER_SHA" in src
        assert 'test "$SIGNER_SHA" = "$EXPECTED_SIGNER"' in src

    def test_same_bytes_become_draft_release_without_rebuild(self):
        src = self.source()
        assert "gh release create" in src
        assert "--draft" in src
        assert "--target \"$GITHUB_SHA\"" in src
        assert "docs/RELEASE_NOTES_V1.0.21.md" in src
        assert "gh release view" in src
        assert "git ls-remote --exit-code --tags" in src
        assert "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02 # v4" in src
        assert "zcode-production-apk" in src
        assert "ZCODE-v1.0.21.apk.sha256" in src
        assert "release-promotion" not in src.lower()

    def test_canonical_debug_does_not_emit_competing_apk(self):
        canonical = read(CANONICAL_WORKFLOW)
        assert '"!arena/v1020-production"' in canonical
        assert "github.head_ref != 'arena/v1020-production'" in canonical
        assert "github.ref != 'refs/heads/main'" in canonical
        start = canonical.index("compile-production-source:")
        compile_only = canonical[start:]
        assert ":app:compileDebugKotlin" in compile_only
        assert "assembleDebug" not in compile_only
        assert "upload-artifact" not in compile_only

    def test_pending_unskip_is_branch_agnostic_and_compile_only(self):
        pending = ROOT / "ci/pending/build.yml"
        src = read(pending)
        start = src.index("compile-production-source:")
        compile_only = src[start:]
        assert (
            "github.event_name == 'pull_request' && "
            "startsWith(github.head_ref, 'arena/')" in compile_only
        )
        assert ":app:compileDebugKotlin" in compile_only
        assert "testDebugUnitTest" in compile_only
        assert "assembleDebug" not in compile_only
        assert "upload-artifact" not in compile_only
        # Class fix, not instance fix: a per-branch name list went stale one
        # release after v1.0.20 (run 32542213874 skipped the job), so the
        # replacement gate must not name any specific branch.
        gate = compile_only.split("runs-on:", 1)[0]
        for stale_name in ("01a0272f", "01a02739", "v1021-production", "v1020-production"):
            assert stale_name not in gate, f"gate masih men-HARDCODE nama branch: {stale_name}"

    def test_live_workflow_is_old_gate_or_exact_pending_upload(self):
        live = read(CANONICAL_WORKFLOW)
        pending = read(ROOT / "ci/pending/build.yml")
        old_gate = (
            "github.event_name == 'pull_request' && "
            "github.head_ref == 'arena/v1020-production'" in live
        )
        # Green before the manual upload (live still on the v1.0.20 gate) and
        # after it (live is an exact copy of the pending file). Anything else
        # means a drifted or partial upload.
        assert old_gate or live == pending, (
            "live build.yml harus salah satu dari: gate v1.0.20 lama "
            "(sebelum upload manual ci/pending) atau salinan persis "
            "ci/pending/build.yml"
        )
        assert "ci/pending/build.yml" in read(RELEASE_NOTES)


class TestProductionSigningPolicy:
    def test_public_identity_is_exact_and_private_material_absent(self):
        policy = read(SIGNING_POLICY)
        assert EXPECTED_SIGNER in policy
        for token in (
            "Alias                    : zcode-release",
            "Public key               : RSA 4096-bit",
            "Certificate signature    : SHA384withRSA",
            "BYTE-FOR-BYTE RECOVERY DRILL: NOT EVIDENCED IN REPO",
            "Private key tidak pernah dikirim kepada agent",
        ):
            assert token in policy
        forbidden_suffixes = (".jks", ".keystore", ".p12", ".pfx")
        gitignore = read(ROOT / ".gitignore")
        for suffix in forbidden_suffixes:
            assert f"*{suffix}" in gitignore
        found = [
            str(p.relative_to(ROOT)) for p in ROOT.rglob("*")
            if p.is_file() and p.suffix.lower() in forbidden_suffixes and ".git" not in p.parts
        ]
        assert not found, f"private signing material ditemukan: {found}"

    def test_release_claims_separate_v1020_evidence_from_v1021_candidate(self):
        policy = read(SIGNING_POLICY)
        roadmap = read(ROADMAP)
        notes = read(RELEASE_NOTES)
        skills = read(SKILLS)
        for token in (
            "PRODUCTION WORKFLOW v1.0.20 : CI VERIFIED",
            "PUBLIC RELEASE              : YES — v1.0.20",
            "v1.0.21 SIGNED/DEVICE/RELEASE: NOT YET VERIFIED",
            "INDEPENDENT ASSET RE-DOWNLOAD: NOT VERIFIED",
        ):
            assert token in policy
        assert "ONE PRODUCTION BUILD" in roadmap
        assert "Production compiler CI           : CI VERIFIED" in roadmap
        assert "Production device UAT            : NOT EVIDENCED IN REPO" in roadmap
        assert "Public release                   : RELEASED — v1.0.20" in roadmap
        assert "ZCODE v1.0.21" in notes
        assert "NOT DEVICE VERIFIED" in notes
        assert "NOT RELEASED" in notes
        assert "SKILL 26 — Production signing" in skills

    def test_no_credential_like_material_is_tracked(self):
        patterns = re.compile(("github" + "_pat_") + "|" + ("gh" + "p_") + r"[A-Za-z0-9]{20,}")
        hits = []
        for p in ROOT.rglob("*"):
            if (
                not p.is_file() or ".git" in p.parts or
                ".pytest_cache" in p.parts or "__pycache__" in p.parts or
                "node_modules" in p.parts
            ):
                continue
            try:
                if patterns.search(read(p)):
                    hits.append(str(p.relative_to(ROOT)))
            except (UnicodeDecodeError, OSError):
                pass
        assert not hits, f"credential-like token ditemukan: {hits}"


class TestOfficialGradleWrapper:
    EXPECTED_WRAPPER_SHA256 = "d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd"
    EXPECTED_DISTRIBUTION_SHA256 = "9d926787066a081739e8200858338b4a69e837c3a821a33aca9db09dd4a41026"

    def test_official_gradle_85_wrapper_is_present_and_pinned(self):
        import hashlib
        jar = ROOT / "gradle/wrapper/gradle-wrapper.jar"
        properties = read(ROOT / "gradle/wrapper/gradle-wrapper.properties")
        assert jar.is_file() and jar.stat().st_size > 40_000
        assert hashlib.sha256(jar.read_bytes()).hexdigest() == self.EXPECTED_WRAPPER_SHA256
        assert "gradle-8.5-bin.zip" in properties
        assert f"distributionSha256Sum={self.EXPECTED_DISTRIBUTION_SHA256}" in properties

    def test_gradlew_is_official_launcher_not_success_stub(self):
        launcher = read(ROOT / "gradlew")
        assert "org.gradle.wrapper.GradleWrapperMain" in launcher
        assert "gradle not found" not in launcher
        assert "skipping assemble" not in launcher
        assert (ROOT / "gradlew.bat").is_file()
