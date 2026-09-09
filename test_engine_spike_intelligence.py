"""
Spike Intelligence Engine — permanent tests (v1.0.23, RFC_V1023).

Dua mode, dua-duanya wajib hijau:
- TANPA deps (simulasi APK tanpa pack): semua layer fallback, tidak crash.
- DENGAN deps asli (jedi/parso/pyflakes/mccabe terpasang): path ASLI
  terbukti — pelajaran bug McCabe PR #31: hijau fallback bukan bukti.

Guard McCabe: pemanggilan kanonik `visitor.preorder(tree, visitor)`
(diverifikasi dari source mccabe McCabeChecker.run) dijaga lexikal +
mutasi; bentuk `preorder(tree)` telanjang (bug PR #31) dan
`preorder(tree, mccabe.ASTVisitor())` (usulan fix REVIEW §3 yang juga
salah — graphs tak pernah terbentuk) keduanya harus tetap absen.

Run: pytest test_engine_spike_intelligence.py -v
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PYDIR = ROOT / "app/src/main/python"
CABE_SRC = (PYDIR / "editor/cabe_layer.py").read_text(encoding="utf-8")

import sys

sys.path.insert(0, str(PYDIR))

import pytest

import zcode_plugins as zp  # noqa: E402


def _has(module: str) -> bool:
    try:
        __import__(module)
        return True
    except Exception:
        return False


HAS_JEDI = _has("jedi")
HAS_PYFLAKES = _has("pyflakes")
HAS_MCCABE = _has("mccabe")
HAS_PARSO = _has("parso")

CODE_BAD = "import os\nimport json\n\ndef f():\n    return nma\n"
CODE_KNOTTED = (
    "def knotted(x):\n"
    "    if x > 0:\n"
    "        if x > 1:\n"
    "            if x > 2:\n"
    "                for i in range(x):\n"
    "                    while x:\n"
    "                        x -= 1\n"
    "    return x\n"
)


class TestPackageStructure:
    def test_exception_hierarchy_and_no_rope(self):
        import editor

        assert issubclass(editor.JediError, editor.SpikeError)
        assert issubclass(editor.ParsoError, editor.SpikeError)
        assert issubclass(editor.PyflakesError, editor.SpikeError)
        assert issubclass(editor.CabeError, editor.SpikeError)
        assert not hasattr(editor, "RopeError"), "rope ditunda v1.0.23 (RFC §8)"
        assert not (PYDIR / "editor/rope_layer.py").exists()

    def test_health_check_shape_exactly_four_layers(self):
        h = zp.run_spike_intelligence("", "health_check")["health"]
        assert set(h.keys()) == {"jedi", "parso", "pyflakes", "cabe"}
        for v in h.values():
            assert set(v.keys()) == {"installed", "status"}

    def test_unknown_action_fails_closed(self):
        r = zp.run_spike_intelligence("x = 1", "bukan_action")
        assert r["ok"] is False and "Unknown action" in r["error"]


class TestLint:
    def test_syntax_error_always_reported_any_mode(self):
        r = zp.run_spike_intelligence("x = \n", "lint", filename="t.py")
        assert r["ok"]
        assert any("SyntaxError" in m["message"] for m in r["issues"])

    @pytest.mark.skipif(not HAS_PYFLAKES, reason="pyflakes tidak terpasang (mode fallback)")
    def test_real_pyflakes_catches_undefined_and_unused(self):
        r = zp.run_spike_intelligence(CODE_BAD, "lint", filename="bad.py")
        msgs = [m["message"] for m in r["issues"]]
        assert any("nma" in m for m in msgs), "undefined name lolos"
        assert any("unused" in m and "os" in m for m in msgs), "unused import lolos"
        for m in r["issues"]:
            assert m["line"] >= 1 and m["severity"] in ("error", "warning")


class TestComplexity:
    def test_fallback_never_crashes_any_mode(self):
        r = zp.run_spike_intelligence(CODE_KNOTTED, "analyze_complexity", threshold=3)
        assert r["ok"]
        a = r["analysis"]
        assert a["cyclomatic_complexity"] >= 1
        assert a["blocks"], "fallback minimal melaporkan block fungsi"

    @pytest.mark.skipif(not HAS_MCCABE, reason="mccabe tidak terpasang (mode fallback)")
    def test_real_mccabe_numbers_and_clean_block_names(self):
        a = zp.run_spike_intelligence(CODE_KNOTTED, "analyze_complexity", threshold=3)["analysis"]
        # 3 if + for + while = 6 decision -> cc >= 6 (bukan 0/1 — bug PR #31)
        assert a["cyclomatic_complexity"] >= 6, "complexity 0/1 = mccabe tidak jalan (bug PR #31)"
        knot = [b for b in a["blocks"] if b["name"] == "knotted"]
        assert knot and knot[0]["line"] == 1, "block harus bernama bersih (graph.entity) + baris"


class TestAutocompleteAndParse:
    @pytest.mark.skipif(not HAS_JEDI, reason="jedi tidak terpasang (mode fallback)")
    def test_real_jedi_stdlib_completion(self):
        r = zp.run_spike_intelligence("import os\nos.", "autocomplete", line=2, column=3)
        names = [c["name"] for c in r["completions"]]
        assert "listdir" in names, "completion stdlib tidak jalan"
        assert r["completions"][0]["type"], "tipe completion wajib terisi"

    def test_jedi_fallback_returns_empty_not_crash(self):
        if HAS_JEDI:
            pytest.skip("jedi terpasang — jalur fallback diuji di mode tanpa deps")
        r = zp.run_spike_intelligence("import os\nos.", "autocomplete", line=2, column=3)
        assert r["ok"] and r["completions"] == []

    @pytest.mark.skipif(not HAS_PARSO, reason="parso tidak terpasang (mode fallback)")
    def test_real_parso_error_recovery(self):
        r = zp.run_spike_intelligence("def f(:\n    pass\n", "parse_ast")
        errs = r["ast"]["errors"]
        assert errs and errs[0]["line"] == 1


class TestJsonEntryContract:
    """Kontrak PyCall Kotlin: run_spike_json(payload) -> JSON string, fail-open."""

    def test_roundtrip_lint(self):
        payload = json.dumps({"action": "lint", "code": "x = \n", "kwargs": {"filename": "t.py"}})
        out = json.loads(zp.run_spike_json(payload))
        assert out["ok"] and out["issues"]

    def test_bad_payload_fails_open_with_json(self):
        out = json.loads(zp.run_spike_json("bukan json"))
        assert out["ok"] is False and "bad payload" in out["error"]

    def test_empty_payload_defaults_to_health(self):
        out = json.loads(zp.run_spike_json("{}"))
        assert out["ok"] and set(out["health"].keys()) == {"jedi", "parso", "pyflakes", "cabe"}

    def test_non_dict_kwargs_coerced(self):
        payload = json.dumps({"action": "lint", "code": "x=1", "kwargs": "bukan-dict"})
        out = json.loads(zp.run_spike_json(payload))
        assert out["ok"], "kwargs non-dict harus dikoersi, bukan crash"


class TestMcCabeCallContract:
    """Regression guard bug McCabe PR #31 — pola pemanggilan kanonik mccabe."""

    def test_canonical_preorder_self_passing_call(self):
        # Inspeksi AST (bukan regex): docstring yang MENDESKRIPSIKAN bug lama
        # memuat bentuk salahnya dan akan false-positive pada guard lexikal
        # (pelajaran SKILL 2 — terjadi lagi di guard pertama tulisan ini).
        import ast as _ast

        tree = _ast.parse(CABE_SRC)
        calls = [
            n for n in _ast.walk(tree)
            if isinstance(n, _ast.Call)
            and isinstance(n.func, _ast.Attribute)
            and n.func.attr == "preorder"
        ]
        assert calls, "pemanggilan preorder mccabe hilang dari cabe_layer"
        for call in calls:
            assert len(call.args) == 2, (
                "bug PR #31 kembali: preorder(tree) tanpa visitor -> TypeError -> selalu fallback"
            )
            second = call.args[1]
            is_self_visitor = isinstance(second, _ast.Name) and second.id == "visitor"
            is_astvisitor_construct = (
                isinstance(second, _ast.Call)
                and isinstance(second.func, _ast.Attribute)
                and second.func.attr == "ASTVisitor"
            )
            assert is_self_visitor, (
                "bentuk kanonik = visitor mem-pass DIRINYA (mccabe.McCabeChecker.run); "
                "ASTVisitor() polos tak punya visit* -> graphs kosong -> complexity 0 diam-diam"
            )
            assert not is_astvisitor_construct, (
                "preorder(tree, mccabe.ASTVisitor()) adalah usulan fix REVIEW §3 yang SALAH"
            )
