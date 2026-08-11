"""
smoke — smoke test package (SPEC-001 §9).

Jenis test:
- IMPORT        : import module berhasil
- NATIVE_LOAD   : pindai .so di staging + verifikasi import (ekstensi termuat)
- BASIC_API     : exec snippet (mis. numpy.arange(3).size == 3)
- FILE_OUTPUT   : exec snippet yang menulis file (mis. matplotlib savefig PNG)
- OFFLINE_RESTART: simulasi restart: bersihkan sys.modules lalu import ulang
                  (test restart beneran = manual di device, lihat BASELINE_TESTING)

Smoke test dijalankan terhadap STAGING dir (belum aktivasi) — sys.path disuntik
sementara, lalu dipulihkan. Deterministic & time-bounded (thread join timeout;
catat TIMEOUT sebagai kegagalan).
"""
import importlib
import os
import sys
import threading
import time

_IMPORT_TIMEOUT_S = 30


def _find_native_libs(staging_dir: str) -> list[str]:
    libs = []
    if not staging_dir or not os.path.isdir(staging_dir):
        return libs
    for root, _dirs, files in os.walk(staging_dir):
        for fn in files:
            if fn.endswith((".so", ".dylib")):
                libs.append(os.path.join(root, fn))
    return sorted(libs)


def _run_with_timeout(fn, timeout_s: float) -> tuple[bool, str]:
    """Jalankan fn; batasi durasi. Tidak bisa membunuh thread, tapi UI tetap
    dibatasi waktunya (best-effort, didokumentasikan di SPEC-001)."""
    result = {}

    def wrapper():
        try:
            fn()
            result["ok"] = True
            result["err"] = None
        except Exception as e:  # noqa: BLE001
            result["ok"] = False
            result["err"] = "%s: %s" % (type(e).__name__, e)

    t = threading.Thread(target=wrapper, daemon=True)
    t.start()
    t.join(timeout_s)
    if t.is_alive():
        return False, "TIMEOUT setelah %ds" % int(timeout_s)
    return result.get("ok", False), result.get("err")


def _exec_snippet(code: str) -> None:
    g = {"__name__": "__main__", "__builtins__": __builtins__}
    exec(code, g)  # noqa: S102 — kode smoke test berasal dari manifest ZCODE (tepercaya)


def _do_import(target: str) -> None:
    importlib.import_module(target)


def _reimport(target: str) -> None:
    # OFFLINE_RESTART: buang modul dari sys.modules lalu import ulang
    for mod in [m for m in sys.modules if m == target or m.startswith(target + ".")]:
        sys.modules.pop(mod, None)
    importlib.invalidate_caches()
    importlib.import_module(target)


def run_smoke(
    import_name: str,
    staging_dir: str,
    tests: list[dict] | None,
    timeout_s: float = _IMPORT_TIMEOUT_S,
) -> tuple[bool, list[dict], dict]:
    """
    Jalankan smoke test terhadap staging_dir.
    Return (ok, results, native_info).
    """
    results: list[dict] = []
    native_info = {"native_libs": [], "note": ""}

    if not staging_dir or not os.path.isdir(staging_dir):
        return False, [{"test": "setup", "type": "SETUP", "ok": False,
                        "error": "Staging directory tidak ada."}], native_info

    native_info["native_libs"] = _find_native_libs(staging_dir)
    native_info["note"] = (
        "%d file .so ditemukan di staging." % len(native_info["native_libs"])
        if native_info["native_libs"]
        else "Package murni-Python (tanpa .so)."
    )

    old_path = list(sys.path)
    old_modules = set(sys.modules)
    sys.path.insert(0, staging_dir)
    importlib.invalidate_caches()
    try:
        test_list = tests or [{"name": "import", "type": "IMPORT", "target": import_name}]
        for t in test_list:
            kind = (t.get("type") or "IMPORT").upper()
            target = t.get("target") or import_name
            name = t.get("name") or ("%s:%s" % (kind, target))
            if kind == "IMPORT":
                ok, err = _run_with_timeout(lambda: _do_import(target), timeout_s)
                results.append({"test": name, "type": kind, "ok": ok, "error": err})
            elif kind == "NATIVE_LOAD":
                # native load terbukti lewat import yang sukses + .so hadir
                ok, err = _run_with_timeout(lambda: _do_import(target), timeout_s)
                libs = native_info["native_libs"]
                ok = ok and bool(libs)
                if not ok and not err:
                    err = "Import OK tapi tidak ada .so di staging (mungkin butuh .so lain)."
                results.append({"test": name, "type": kind, "ok": ok, "error": err})
            elif kind in ("BASIC_API", "FILE_OUTPUT"):
                code = t.get("code")
                if not code:
                    results.append({"test": name, "type": kind, "ok": False,
                                    "error": "Manifest tanpa field 'code'."})
                else:
                    ok, err = _run_with_timeout(lambda: _exec_snippet(code), timeout_s)
                    results.append({"test": name, "type": kind, "ok": ok, "error": err})
            elif kind == "OFFLINE_RESTART":
                ok, err = _run_with_timeout(lambda: _reimport(target), timeout_s)
                results.append({"test": name, "type": kind, "ok": ok, "error": err})
            else:
                results.append({"test": name, "type": kind, "ok": False,
                                "error": "Jenis smoke test tidak dikenal: %s" % kind})

            if not results[-1]["ok"]:
                return False, results, native_info
        return True, results, native_info
    finally:
        sys.path[:] = old_path
        for mod in list(sys.modules):
            if mod not in old_modules:
                sys.modules.pop(mod, None)
        importlib.invalidate_caches()
        _ = time  # (diimpor untuk dokumentasi; tetap digunakan di atas)


def run_smoke_json(import_name: str, staging_dir: str, tests_json: str, timeout_s: float = _IMPORT_TIMEOUT_S) -> str:
    """Wrapper JSON-string untuk Kotlin (hasil dict → json.dumps)."""
    import json
    try:
        tests = json.loads(tests_json) if tests_json else None
        ok, results, native = run_smoke(import_name, staging_dir, tests, timeout_s)
        return json.dumps({
            "ok": ok,
            "results": results,
            "native_info": native,
        }, default=str)
    except Exception as e:  # noqa: BLE001
        return json.dumps({
            "ok": False,
            "results": [{"test": "setup", "type": "SETUP", "ok": False, "error": str(e)}],
            "native_info": {"native_libs": [], "note": "error"},
        })
