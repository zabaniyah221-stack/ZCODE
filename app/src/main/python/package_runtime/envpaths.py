"""
envpaths — membangun sys.path dari environment package ZCODE.

Environment (SPEC-001 §2):
  <workspace>/python-env/
    site-packages/<normalized>/<version>/   ← satu direktori per versi terpasang
    state/installed.json                     ← sumber kebenaran versi aktif

zcode_runner memanggil activate() sebelum run script: setiap versi aktif
di-inject ke sys.path sehingga package hasil PackageEngineV2 bisa di-import
di proses yang sama, offline, dan survive restart (installed.json di disk).
"""
import json
import os
import sys


def env_root(workspace: str) -> str:
    return os.path.join(workspace, "python-env")


def installed_file(workspace: str) -> str:
    return os.path.join(env_root(workspace), "state", "installed.json")


def load_installed(workspace: str) -> dict:
    path = installed_file(workspace)
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def package_paths(workspace: str) -> list[str]:
    """Daftar direktori package aktif (urutan stabil)."""
    installed = load_installed(workspace)
    paths = []
    for _name, meta in sorted(installed.items()):
        rel = meta.get("path")
        if not rel:
            continue
        full = os.path.join(env_root(workspace), rel)
        if os.path.isdir(full):
            paths.append(full)
    return paths


def activate(workspace: str) -> list[str]:
    """Inject path package aktif ke sys.path (paling depan, tanpa duplikat)."""
    paths = package_paths(workspace)
    for p in reversed(paths):
        if p not in sys.path:
            sys.path.insert(0, p)
    return paths
