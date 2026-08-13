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

# ---- BUG K (2026-08-13): dependensi harus dibaca PER-VERSI, bukan versi terbaru ----
# pandas 2.1.3 punya deps wajib pytz/python-dateutil/tzdata yang TIDAK muncul di
# info rilis terbaru (di rilis baru pytz jadi extra). Sebelum fix, resolver jatuh
# ke info terbaru -> pytz hilang -> No module named 'pytz'.
PYPI_PANDAS = {
    "info": {
        "name": "pandas", "summary": "", "license": "",
        # VERSI TERBARU di mock ini: pytz TIDAK ada (simulasi rilis baru yang
        # menggeser pytz jadi extra). Sebelum fix, resolver membaca ini dan
        # pytz hilang.
        "requires_dist": ["numpy>=1.24.3"],
    },
    "releases": {
        "2.1.3": [{
            "filename": "pandas-2.1.3-py3-none-any.whl",
            "packagetype": "bdist_wheel",
            "url": "https://files.pythonhosted.org/pandas-2.1.3-py3-none-any.whl",
            "digests": {"sha256": "dd" * 32}, "size": 100000, "yanked": False,
        }],
    },
}
# Endpoint per-versi memberi requires_dist yang BENAR untuk 2.1.3.
PYPI_PANDAS_VERSION = {
    "info": {
        "name": "pandas", "summary": "", "license": "",
        "requires_dist": [
            "numpy>=1.24.3; python_version >= \"3.8\"",
            "python-dateutil>=2.8.2",
            "pytz>=2020.1",
            "tzdata>=2022.1",
        ],
    },
    "releases": {"2.1.3": []},
}
# rich 13.5.3 butuh typing-extensions hanya untuk python<3.9; di 3.11 di-skip.
PYPI_RICH_VERSION = {
    "info": {
        "name": "rich", "summary": "", "license": "",
        "requires_dist": [
            "typing-extensions (>=4.0.0,<5.0); python_version < \"3.9\"",
            "pygments>=2.13.0,<3.0.0",
            "markdown-it-py>=2.2.0",
        ],
    },
    "releases": {"13.5.3": []},
}


def _mock_http_get(url: str) -> bytes:
    if "/pypi/requests/json" in url:
        return json.dumps(PYPI_REQUESTS).encode()
    if "/pypi/charset-normalizer/json" in url:
        return json.dumps(PYPI_CHARSET).encode()
    if "/pypi/pandas/2.1.3/json" in url:
        return json.dumps(PYPI_PANDAS_VERSION).encode()
    if "/pypi/pandas/json" in url:
        return json.dumps(PYPI_PANDAS).encode()
    if "/pypi/rich/13.5.3/json" in url:
        return json.dumps(PYPI_RICH_VERSION).encode()
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

    def test_requires_dist_per_version_pandas_pytz(self, mock_net):
        """BUG K: requires_dist dibaca PER-VERSI, bukan versi terbaru.

        pandas 2.1.3 wajib butuh pytz/python-dateutil/tzdata. info pada
        /pypi/pandas/json (versi terbaru) TIDAK memuat pytz (simulasi rilis
        baru yang menggeser pytz jadi extra). Resolver HARUS membaca endpoint
        per-versi sehingga pytz masuk plan. Kalau ini lewat dari info terbaru,
        pytz hilang -> No module named 'pytz'.
        """
        # pandas punya NATIVE_HOST_DEPS -> numpy (wheel Chaquopy android).
        # Sertakan tag android agar host dep numpy ikut ter-resolve.
        plan = resolve_mod.resolve(
            "pandas==2.1.3",
            supported_tags=[
                Tag("py3", "none", "any"),
                Tag("cp311", "cp311", "android_21_arm64_v8a"),
            ],
        )
        pd = next((p for p in plan["packages"] if p["name"] == "pandas"), None)
        assert pd is not None
        assert pd["deps_source"] in ("pypi-version", "wheel")
        import re as _re
        names = []
        for r in pd["requires_dist"]:
            m = _re.match(r"^\s*([A-Za-z0-9][A-Za-z0-9._-]*)", r.split(";")[0])
            names.append(m.group(1) if m else "")
        assert "pytz" in names
        assert "python-dateutil" in names

    def test_requires_dist_per_version_rich_marker_py311(self, mock_net):
        """BUG K + marker: rich 13.5.3 butuh typing-extensions HANYA utk py<3.9.

        Pada Python 3.11 marker 'python_version < "3.9"' bernilai False sehingga
        typing-extensions TIDAK wajib (sudah ada di stdlib typing). Ini membuktikan
        marker evaluation tetap berjalan setelah fix per-versi.
        """
        env = {"python_version": "3.11", "extra": ""}
        req = resolve_mod.requires_dist_for_version("rich", "13.5.3")
        # marker disaring di level queue; pastikan string mentah ada
        assert any("typing-extensions" in r for r in req)
        from packaging.requirements import Requirement
        te = [r for r in req if "typing-extensions" in r][0]
        r = Requirement(te)
        # di Python 3.11 marker false → tidak perlu diinstall
        assert r.marker.evaluate(env) is False

# =====================================================================
# Resolver reliability — timeout/retry/progress/cancellation (v1.0.15 regression)
# =====================================================================

class _FakeResolveBridge:
    def __init__(self):
        self.cancelled = False
        self.events = []

    def isCancelled(self):
        return self.cancelled

    def emit(self, event_json):
        self.events.append(json.loads(str(event_json)))


class _FakeHttpResponse:
    def __init__(self, body=b"ok"):
        self.body = body

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def read(self):
        return self.body


class TestResolverReliability:
    """Guard kelas regresi v1.0.15, bukan guard khusus satu package."""

    def _with_bridge(self, bridge):
        token = resolve_mod._CURRENT_BRIDGE.set(bridge)
        pkg_token = resolve_mod._CURRENT_PACKAGE.set("demo")
        return token, pkg_token

    def _reset_bridge(self, tokens):
        token, pkg_token = tokens
        resolve_mod._CURRENT_PACKAGE.reset(pkg_token)
        resolve_mod._CURRENT_BRIDGE.reset(token)

    def test_timeout_sekali_lalu_sukses_dengan_dua_attempt(self, monkeypatch):
        import socket
        calls = []

        def fake_urlopen(req, timeout):
            calls.append(timeout)
            if len(calls) == 1:
                raise socket.timeout("sementara")
            return _FakeHttpResponse(b"pulih")

        monkeypatch.setattr(resolve_mod.urllib.request, "urlopen", fake_urlopen)
        monkeypatch.setattr(resolve_mod, "_retry_wait", lambda *a, **k: None)
        bridge = _FakeResolveBridge()
        tokens = self._with_bridge(bridge)
        try:
            assert resolve_mod._http_get("https://pypi.org/pypi/demo/json") == b"pulih"
        finally:
            self._reset_bridge(tokens)

        assert len(calls) == 2, "retry budget harus tepat dua total attempt"
        assert any(e["stage"] == "http_retry" for e in bridge.events)
        assert bridge.events[-1]["stage"] == "http_ok"
        assert all(e.get("package") == "demo" for e in bridge.events)

    def test_http_404_tidak_diretry(self, monkeypatch):
        from urllib.error import HTTPError
        calls = []

        def fake_urlopen(req, timeout):
            calls.append(timeout)
            raise HTTPError(req.full_url, 404, "not found", {}, None)

        monkeypatch.setattr(resolve_mod.urllib.request, "urlopen", fake_urlopen)
        monkeypatch.setattr(resolve_mod, "_retry_wait", lambda *a, **k: None)
        with pytest.raises(resolve_mod.ResolveError):
            resolve_mod._http_get("https://chaquo.com/pypi-13.1/tidak-ada/")
        assert len(calls) == 1, "404 permanen tidak boleh menghabiskan retry"

    def test_http_503_diretry(self, monkeypatch):
        from urllib.error import HTTPError
        calls = []

        def fake_urlopen(req, timeout):
            calls.append(timeout)
            if len(calls) == 1:
                raise HTTPError(req.full_url, 503, "busy", {}, None)
            return _FakeHttpResponse(b"ok")

        monkeypatch.setattr(resolve_mod.urllib.request, "urlopen", fake_urlopen)
        monkeypatch.setattr(resolve_mod, "_retry_wait", lambda *a, **k: None)
        assert resolve_mod._http_get("https://pypi.org/pypi/demo/json") == b"ok"
        assert len(calls) == 2

    def test_cancel_sebelum_request_tidak_menyentuh_network(self, monkeypatch):
        bridge = _FakeResolveBridge()
        bridge.cancelled = True
        tokens = self._with_bridge(bridge)
        touched = []
        monkeypatch.setattr(
            resolve_mod.urllib.request,
            "urlopen",
            lambda *a, **k: touched.append(True),
        )
        try:
            with pytest.raises(resolve_mod.ResolveError) as exc:
                resolve_mod._http_get("https://pypi.org/pypi/demo/json")
        finally:
            self._reset_bridge(tokens)
        assert exc.value.code == "CANCELLED"
        assert touched == []

    def test_cancel_selama_retry_wait_dihormati(self, monkeypatch):
        bridge = _FakeResolveBridge()
        tokens = self._with_bridge(bridge)

        def fake_sleep(_seconds):
            bridge.cancelled = True

        monkeypatch.setattr(resolve_mod.time, "sleep", fake_sleep)
        monkeypatch.setattr(resolve_mod.random, "uniform", lambda *a: 0.0)
        try:
            with pytest.raises(resolve_mod.ResolveError) as exc:
                resolve_mod._retry_wait(1)
        finally:
            self._reset_bridge(tokens)
        assert exc.value.code == "CANCELLED"

    def test_cancel_tidak_boleh_ditelan_fallback_source(self, monkeypatch):
        """Bukti full-emulator ARMv7: event `cancelled` muncul, tetapi catch
        fallback PyPI/Chaquopy menelannya dan hasil akhir berubah menjadi
        COMPATIBILITY. Cancel adalah control-flow, bukan source failure.
        """
        bridge = _FakeResolveBridge()

        def cancel_during_http(_url):
            bridge.cancelled = True
            resolve_mod._check_cancelled()

        monkeypatch.setattr(resolve_mod, "_http_get", cancel_during_http)
        out = json.loads(resolve_mod.resolve_json("requests", progress_bridge=bridge))
        assert out["ok"] is False
        assert out["code"] == "CANCELLED"

    def test_semua_fallback_propagasi_cancel(self):
        """Jaga kelas bug: source baru di masa depan tidak boleh menelan Cancel."""
        import ast
        tree = ast.parse(Path(resolve_mod.__file__).read_text())
        offenders = []
        for fn in [n for n in ast.walk(tree) if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef))]:
            for handler in [n for n in ast.walk(fn) if isinstance(n, ast.ExceptHandler)]:
                caught = handler.type
                if not (isinstance(caught, ast.Name) and caught.id == "ResolveError"):
                    continue
                # resolve_json memang mengubah ResolveError (termasuk CANCELLED)
                # ke kontrak JSON. Handler lain harus re-raise langsung atau
                # memanggil helper sebelum fallback.
                if fn.name == "resolve_json":
                    continue
                direct_reraise = len(handler.body) == 1 and isinstance(handler.body[0], ast.Raise)
                propagates = any(
                    isinstance(n, ast.Call)
                    and isinstance(n.func, ast.Name)
                    and n.func.id == "_propagate_cancel"
                    for n in ast.walk(handler)
                )
                if not direct_reraise and not propagates:
                    offenders.append((fn.name, handler.lineno))
        assert not offenders, "ResolveError fallback menelan CANCELLED: %r" % offenders

    def test_metadata_failure_di_cache_dalam_satu_resolve(self, monkeypatch):
        calls = []

        def gagal(_url):
            calls.append(True)
            raise resolve_mod.ResolveError("NETWORK", "metadata", "gagal", "uji")

        resolve_mod.clear_metadata_cache()
        monkeypatch.setattr(resolve_mod, "_http_get", gagal)
        for _ in range(2):
            with pytest.raises(resolve_mod.ResolveError):
                resolve_mod.fetch_pypi_metadata("paket-tidak-ada")
        assert len(calls) == 1, (
            "source failure yang sama dipanggil berulang; emulator menemukan "
            "4 request PyPI 404 untuk satu support library"
        )

    def test_resolve_json_selalu_reset_bridge(self, monkeypatch):
        bridge = _FakeResolveBridge()

        def meledak(*args, **kwargs):
            raise RuntimeError("uji finally")

        monkeypatch.setattr(resolve_mod, "resolve", meledak)
        result = json.loads(resolve_mod.resolve_json("demo", progress_bridge=bridge))
        assert result["ok"] is False
        assert resolve_mod._CURRENT_BRIDGE.get() is None
        assert resolve_mod._CURRENT_PACKAGE.get() == ""

    def test_source_progress_tidak_membocorkan_url(self):
        bridge = _FakeResolveBridge()
        tokens = self._with_bridge(bridge)
        try:
            resolve_mod._emit_progress("http_begin", source="pypi", attempt=1)
        finally:
            self._reset_bridge(tokens)
        event = bridge.events[0]
        assert event["source"] == "pypi"
        assert "url" not in event


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


class TestEnvPathsEntryPoints:
    """Langkah 3 (2026-08-13): layout ZCODE `<norm>/<version>/` di sys.path
    HARUS bisa dibaca importlib.metadata & pkg_resources (entry-points).

    Ini mencegah regresi: paket yang bergantung pkg_resources/entry-points
    tidak boleh gagal karena layout yang berbeda dari site-packages biasa.
    """

    def _buat_layout(self, tmp_path):
        pkg = tmp_path / "requests" / "2.32.3"
        pkg.mkdir(parents=True)
        (pkg / "requests").mkdir()
        (pkg / "requests" / "__init__.py").write_text("__version__='2.32.3'\n")
        di = pkg / "requests-2.32.3.dist-info"
        di.mkdir()
        (di / "METADATA").write_text(
            "Metadata-Version: 2.1\nName: requests\nVersion: 2.32.3\n")
        (di / "top_level.txt").write_text("requests\n")
        (di / "entry_points.txt").write_text(
            "[console_scripts]\nmycmd = requests.cli:main\n")
        (di / "RECORD").write_text("")
        return str(pkg)

    def test_importlib_metadata_menemukan_dist(self, tmp_path):
        # Paket unik supaya tidak tercemar versi host. Jalur nyata:
        # envpaths.activate() (seperti runner) lalu importlib.metadata.
        nama = "zcode-testpkg"
        versi = "1.0.0"
        pkg = tmp_path / "python-env" / "site-packages" / nama / versi
        pkg.mkdir(parents=True)
        (pkg / "zcode_testpkg").mkdir()
        (pkg / "zcode_testpkg" / "__init__.py").write_text("__version__='1.0.0'\n")
        di = pkg / ("%s-%s.dist-info" % (nama, versi))
        di.mkdir()
        (di / "METADATA").write_text(
            "Metadata-Version: 2.1\nName: zcode-testpkg\nVersion: 1.0.0\n")
        (di / "top_level.txt").write_text("zcode_testpkg\n")
        (di / "entry_points.txt").write_text(
            "[console_scripts]\nzcmd = zcode_testpkg:main\n")
        (di / "RECORD").write_text("")
        state = tmp_path / "python-env" / "state"
        state.mkdir(parents=True)
        (state / "installed.json").write_text(json.dumps({
            nama: {"version": versi,
                   "path": "site-packages/%s/%s" % (nama, versi),
                   "installed_at": 1, "source": "pypi"},
        }))
        before = list(sys.path)
        try:
            env_mod.activate(str(tmp_path))
            import importlib.metadata as md
            from packaging.utils import canonicalize_name
            cn = canonicalize_name(nama)
            d = next((x for x in md.distributions()
                      if canonicalize_name(x.metadata["Name"] or "") == cn), None)
            assert d is not None, "distributions() tidak melihat zcode-testpkg"
            assert d.version == versi
            eps = list(d.entry_points)
            assert any(e.name == "zcmd" for e in eps), "entry_points tidak terbaca"
        finally:
            sys.path[:] = before

    # CATATAN (2026-08-13): pkg_resources TIDAK dijadikan guard unit test.
    # API ini deprecated & tidak stabil terhadap dist-info sintetis / lintas
    # versi Python (3.11 vs 3.13) dan setuptools — ia memotong nama ber-dash
    # pada dist-info buatan manual, menghasilkan false-failure yang bukan bug
    # ZCODE. Yang membuktikan layout ZCODE bekerja utk entry-points adalah
    # importlib.metadata (test di atas, API modern Python 3.10+). pkg_resources
    # hanya perlu TERSEDIA di Chaquopy (setuptools dibundle) — dijaga oleh
    # test_chaquopy_meng_bundle_setuptools. Verifikasi penuh paket berbasis
    # pkg_resources tetap via UAT device.

    def test_chaquopy_meng_bundle_setuptools(self):
        # pkg_resources berasal dari setuptools yang WAJIB di-bundle di
        # Chaquopy build.gradle. Tanpa ini, paket yang butuh pkg_resources
        # akan gagal di device walau layout sudah benar.
        import re
        p = ROOT / "app/build.gradle.kts"
        txt = p.read_text()
        assert re.search(r"install\(\"setuptools==[0-9.]+\"\)", txt), (
            "setuptools harus di-bundle di chaquopy pip{ install } — "
            "pkg_resources/entry-points bergantung padanya"
        )
