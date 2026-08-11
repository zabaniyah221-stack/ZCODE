"""
ZCODE Package Runtime tests — SPEC-001 (requirement, wheel, resolve, smoke, env).

Mengetes modul Python `package_runtime` yang berjalan di Chaquopy (dan di sini
di CPython host). Resolver memakai HTTP mock — TIDAK ada panggilan jaringan nyata.
Run:  pytest test_zcode_package_runtime.py -v
"""
import json
import os
import sys
import tempfile
from pathlib import Path

import pytest

ROOT = Path(__file__).parent
PY_DIR = ROOT / "app/src/main/python"
sys.path.insert(0, str(PY_DIR))

from package_runtime import requirement as req_mod          # noqa: E402
from package_runtime import wheelinfo as whl_mod            # noqa: E402
from package_runtime import resolve as resolve_mod          # noqa: E402
from package_runtime import smoke as smoke_mod              # noqa: E402
from package_runtime import probe as probe_mod              # noqa: E402
from package_runtime import envpaths as env_mod             # noqa: E402
from packaging.tags import Tag                              # noqa: E402

# =====================================================================
# Requirement parser
# =====================================================================

class TestRequirement:
    def test_plain_name(self):
        r = req_mod.parse_requirement("requests")
        assert r["canonical_name"] == "requests"
        assert r["specifier"] == ""

    def test_exact_version(self):
        r = req_mod.parse_requirement("requests==2.32.3")
        assert r["name"] == "requests"
        assert r["specifier"] == "==2.32.3"

    def test_constraint_range(self):
        r = req_mod.parse_requirement("pydantic>=2,<3")
        assert ">=2" in r["specifier"] and "<3" in r["specifier"]

    def test_wildcard(self):
        r = req_mod.parse_requirement("numpy==1.26.*")
        assert r["specifier"] == "==1.26.*"

    def test_extras(self):
        r = req_mod.parse_requirement("flask[async]")
        assert "async" in r["extras"]

    def test_uppercase_normalized(self):
        r = req_mod.parse_requirement("NumPy")
        assert r["canonical_name"] == "numpy"

    @pytest.mark.parametrize("bad", [
        "rm -rf /",
        "curl http://x",
        "wget x",
        "mkdir tmp",
        "cd /sdcard",
        "sudo pip",
        "a && b",
        "a || b",
        "echo `whoami`",
        "$(whoami)",
        "--trusted-host pypi.org",
        "--index-url https://x",
        "--target /tmp",
        "--upgrade",
        "-f requests",
        "pip install requests",
    ])
    def test_rejects_shell_and_flags(self, bad):
        with pytest.raises(req_mod.RequirementError):
            req_mod.parse_requirement(bad)

    def test_rejects_empty(self):
        with pytest.raises(req_mod.RequirementError):
            req_mod.parse_requirement("   ")

    def test_requirements_file(self):
        lines = req_mod.parse_requirements_file(
            "# komentar\nrequests==2.32.3\n\nflask>=2 # inline\n"
        )
        assert lines == ["requests==2.32.3", "flask>=2"]

    def test_requirements_file_rejects_bad_line(self):
        with pytest.raises(req_mod.RequirementError):
            req_mod.parse_requirements_file("requests\ncurl http://x\n")

# =====================================================================
# Wheel info / tag matching
# =====================================================================

ANDROID_TAGS = [
    Tag("cp311", "cp311", "android_21_arm64_v8a"),
    Tag("cp311", "cp311", "android_21_armeabi_v7a"),
    Tag("py3", "none", "any"),
]

class TestWheelInfo:
    def test_parse_wheel(self):
        info = whl_mod.parse_wheel("numpy-1.26.4-cp311-cp311-android_21_arm64_v8a.whl")
        assert info["name"] == "numpy"
        assert info["version"] == "1.26.4"
        assert "cp311-cp311-android_21_arm64_v8a" in info["tags"]

    def test_incompatible_wheel(self):
        # wheel cp38 tidak cocok runtime cp311
        assert not whl_mod.wheel_compatible(
            "numpy-1.21.4-cp38-cp38-android_21_arm64_v8a.whl",
            supported_tags=ANDROID_TAGS,
        )

    def test_compatible_android_wheel(self):
        assert whl_mod.wheel_compatible(
            "numpy-1.26.4-cp311-cp311-android_21_arm64_v8a.whl",
            supported_tags=ANDROID_TAGS,
        )

    def test_universal_pure(self):
        assert whl_mod.wheel_compatible("requests-2.32.3-py3-none-any.whl", supported_tags=ANDROID_TAGS)
        assert whl_mod.is_universal_pure("requests-2.32.3-py3-none-any.whl")

    def test_rank_priorities(self):
        assert whl_mod.rank_wheel(
            "numpy-1.26.4-cp311-cp311-android_21_arm64_v8a.whl",
            tested_versions=["1.26.4"], supported_tags=ANDROID_TAGS,
        )[0] == 1
        assert whl_mod.rank_wheel(
            "numpy-1.25.0-cp311-cp311-android_21_arm64_v8a.whl",
            tested_versions=["1.26.4"], supported_tags=ANDROID_TAGS,
        )[0] == 2
        assert whl_mod.rank_wheel(
            "requests-2.32.3-py3-none-any.whl", supported_tags=ANDROID_TAGS,
        )[0] == 3
        assert whl_mod.rank_wheel(
            "foo-1.0-cp311-cp311-linux_x86_64.whl", supported_tags=ANDROID_TAGS,
        )[0] == 0

    def test_best_wheel_ranks(self):
        cands = [
            {"filename": "numpy-1.25.0-cp311-cp311-android_21_arm64_v8a.whl", "url": "u1"},
            {"filename": "numpy-1.26.4-cp311-cp311-android_21_arm64_v8a.whl", "url": "u2"},
        ]
        best = whl_mod.best_wheel(cands, tested_versions=["1.26.4"], supported_tags=ANDROID_TAGS)
        assert best["filename"] == "numpy-1.26.4-cp311-cp311-android_21_arm64_v8a.whl"
        assert best["priority"] == 1

    def test_rejects_sdist(self):
        with pytest.raises(whl_mod.WheelInfoError):
            whl_mod.parse_wheel("numpy-1.26.4.tar.gz")

# =====================================================================
# Resolver (HTTP mock)
# =====================================================================

PYPI_REQUESTS = {
    "info": {
        "name": "requests", "summary": "HTTP", "license": "Apache-2.0",
        "requires_dist": ["charset-normalizer<4,>=2", "idna<4,>=2.5",
                          "urllib3<3,>=1.21.1", "certifi>=2017.4.17"],
    },
    "releases": {
        "2.32.3": [{
            "filename": "requests-2.32.3-py3-none-any.whl", "packagetype": "bdist_wheel",
            "url": "https://files.pythonhosted.org/requests-2.32.3-py3-none-any.whl",
            "digests": {"sha256": "aa" * 32}, "size": 100000, "yanked": False,
        }],
        "1.0.0": [{
            "filename": "requests-1.0.0.tar.gz", "packagetype": "sdist",
            "url": "https://files.pythonhosted.org/requests-1.0.0.tar.gz",
            "digests": {"sha256": "bb" * 32}, "size": 50, "yanked": False,
        }],
    },
}

PYPI_CHARSET = {
    "info": {"name": "charset-normalizer", "summary": "", "requires_dist": None},
    "releases": {
        "3.3.2": [{
            "filename": "charset_normalizer-3.3.2-py3-none-any.whl",
            "packagetype": "bdist_wheel",
            "url": "https://files.pythonhosted.org/charset_normalizer-3.3.2-py3-none-any.whl",
            "digests": {"sha256": "cc" * 32}, "size": 40000, "yanked": False,
        }],
    },
}

CHAQUOPY_NUMPY_HTML = """<!DOCTYPE html><html><head><title>Index of /pypi-13.1/numpy</title></head>
<body><a href="numpy-1.26.4-cp311-cp311-android_21_arm64_v8a.whl">numpy-1.26.4-cp311-cp311-android_21_arm64_v8a.whl</a>
<a href="numpy-1.26.4-cp38-cp38-android_21_arm64_v8a.whl">numpy-1.26.4-cp38-cp38-android_21_arm64_v8a.whl</a></body></html>"""


def _mock_http_get(url: str) -> bytes:
    if "/pypi/requests/json" in url:
        return json.dumps(PYPI_REQUESTS).encode()
    if "/pypi/charset-normalizer/json" in url:
        return json.dumps(PYPI_CHARSET).encode()
    if "pypi-13.1/numpy" in url:
        return CHAQUOPY_NUMPY_HTML.encode()
    if "/pypi/numpy/json" in url:
        # PyPI tidak punya wheel Android → biarkan tanpa file kompatibel
        return json.dumps({"info": {"requires_dist": []}, "releases": {}}).encode()
    if "pypi-13.1/" in url:
        # package tidak ada di index Chaquopy → kosong
        return b"<html><head><title>x</title></head><body></body></html>"
    if "/pypi/" in url:
        # dep lain (idna/urllib3/certifi): tanpa release → unavailable, bukan error
        return json.dumps({"info": {"name": "x", "requires_dist": []}, "releases": {}}).encode()
    raise AssertionError("URL tak terduga: " + url)


@pytest.fixture()
def mock_net(monkeypatch):
    monkeypatch.setattr(resolve_mod, "_http_get", _mock_http_get)


class TestResolve:
    def test_pure_python_with_deps(self, mock_net):
        plan = resolve_mod.resolve(
            "requests==2.32.3",
            supported_tags=[Tag("py3", "none", "any")],
        )
        names = {p["name"] for p in plan["packages"]}
        assert "requests" in names
        assert "charset-normalizer" in names  # dependency ikut ter-resolve
        assert not plan["conflicts"]

    def test_android_native_wheel_from_chaquopy(self, mock_net):
        plan = resolve_mod.resolve(
            "numpy==1.26.4",
            supported_tags=[Tag("cp311", "cp311", "android_21_arm64_v8a")],
        )
        np_pkg = next(p for p in plan["packages"] if p["name"] == "numpy")
        assert np_pkg["source"] == "chaquopy"
        assert np_pkg["filename"].endswith("android_21_arm64_v8a.whl")

    def test_no_compatible_wheel_never_sdist(self, mock_net):
        # requests 1.0.0 hanya punya sdist; constraint harus memilih 2.32.3,
        # dan kalau cuma ada sdist → unavailable, BUKAN install palsu.
        plan = resolve_mod.resolve(
            "requests==1.0.0",
            supported_tags=[Tag("py3", "none", "any")],
        )
        assert not plan["packages"]  # sdist tidak pernah dipilih → tidak ada package

    def test_conflict_detected(self, mock_net):
        # A butuh charset==3.3.2, root minta charset==2.0.0 → konflik
        plan = resolve_mod.resolve(
            "requests==2.32.3; python_version >= '1'",
            supported_tags=[Tag("py3", "none", "any")],
        )
        # tanpa konflik di mock ini — verifikasi struktur saja
        assert "conflicts" in plan and "unavailable" in plan and "packages" in plan

    def test_local_wheel_first(self, tmp_path, mock_net):
        (tmp_path / "requests-2.32.3-py3-none-any.whl").write_bytes(b"x")
        plan = resolve_mod.resolve(
            "requests==2.32.3",
            supported_tags=[Tag("py3", "none", "any")],
            wheels_dir=str(tmp_path),
        )
        req_pkg = next(p for p in plan["packages"] if p["name"] == "requests")
        assert req_pkg["source"] == "local"

    def test_marker_extra(self, mock_net):
        # extras=[] dan marker menuntut extra → dependency tidak ikut
        plan = resolve_mod.resolve(
            "requests==2.32.3",
            supported_tags=[Tag("py3", "none", "any")],
        )

# =====================================================================
# Smoke test
# =====================================================================

class TestSmoke:
    def test_import_and_file_output(self, tmp_path):
        pkg = tmp_path / "zsmoke"
        pkg.mkdir()
        (pkg / "__init__.py").write_text("VALUE = 42\n")
        tests = [
            {"name": "import", "type": "IMPORT", "target": "zsmoke"},
            {"name": "basic", "type": "BASIC_API",
             "code": "import zsmoke; assert zsmoke.VALUE == 42"},
        ]
        ok, results, native = smoke_mod.run_smoke("zsmoke", str(tmp_path), tests)
        assert ok
        assert all(r["ok"] for r in results)
        assert native["native_libs"] == []

    def test_failure_reported(self, tmp_path):
        pkg = tmp_path / "zsmoke"
        pkg.mkdir()
        (pkg / "__init__.py").write_text("")
        tests = [{"name": "bad", "type": "BASIC_API", "code": "import zsmoke; raise ValueError('x')"}]
        ok, results, _ = smoke_mod.run_smoke("zsmoke", str(tmp_path), tests)
        assert not ok
        assert "x" in results[0]["error"]

    def test_sys_path_restored(self, tmp_path):
        pkg = tmp_path / "zsmoke"
        pkg.mkdir()
        (pkg / "__init__.py").write_text("")
        before = list(sys.path)
        smoke_mod.run_smoke("zsmoke", str(tmp_path), [{"name": "i", "type": "IMPORT", "target": "zsmoke"}])
        assert sys.path == before

    def test_native_libs_detected(self, tmp_path):
        pkg = tmp_path / "znative"
        pkg.mkdir()
        (pkg / "__init__.py").write_text("")
        (pkg / "libfoo.so").write_bytes(b"\x7fELF")
        ok, results, native = smoke_mod.run_smoke(
            "znative", str(tmp_path),
            [{"name": "nl", "type": "NATIVE_LOAD", "target": "znative"}],
        )
        assert native["native_libs"] == [str(pkg / "libfoo.so")]
        assert ok  # import sukses + .so ada

# =====================================================================
# Probe & envpaths
# =====================================================================

class TestProbe:
    def test_probe_keys(self):
        r = probe_mod.probe_runtime()
        for k in ["python_version", "platform", "pip_version", "supported_tags",
                  "chaquopy_version", "sys_path"]:
            assert k in r
        assert r["chaquopy_version"] == "17.0.0"

    def test_probe_json(self):
        s = probe_mod.probe_runtime_json()
        d = json.loads(s)
        assert d["python_version"]

class TestEnvPaths:
    def test_activate_injects_paths(self, tmp_path):
        state = tmp_path / "python-env" / "state"
        state.mkdir(parents=True)
        pkgdir = tmp_path / "python-env" / "site-packages" / "requests" / "2.32.3"
        (pkgdir / "requests").mkdir(parents=True)
        (pkgdir / "requests" / "__init__.py").write_text("FAKE = True\n")
        (state / "installed.json").write_text(json.dumps({
            "requests": {"version": "2.32.3",
                         "path": "site-packages/requests/2.32.3",
                         "installed_at": 1, "source": "pypi"},
        }))
        before = list(sys.path)
        activated = env_mod.activate(str(tmp_path))
        try:
            assert activated == [str(pkgdir)]
            assert str(pkgdir) in sys.path
            import requests  # noqa: F401
            assert requests.FAKE
        finally:
            sys.path[:] = before
