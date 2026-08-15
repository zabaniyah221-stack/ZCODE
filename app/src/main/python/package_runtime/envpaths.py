"""
envpaths — membangun sys.path dari environment package ZCODE.

Environment (SPEC-001 §2):
  <workspace>/python-env/
    site-packages/<normalized>/<version>/   ← satu direktori per versi terpasang
    state/installed.json                     ← sumber kebenaran versi aktif

zcode_runner memanggil activate() sebelum run script: setiap versi aktif
di-inject ke sys.path sehingga package hasil PackageEngineV2 bisa di-import
di proses yang sama, offline, dan survive restart (installed.json di disk).

BUG N (2026-08-15, DEVICE VERIFIED di Infinix ARMv7 v1.0.17). sys.path saja
TIDAK cukup untuk paket native. Bukti dari HP:
  17:31 install numpy → run numpy_stats.py → code=0   (proses yang sama)
  17:43 APP_START (proses baru) → run yang sama → code=1 FAILED,
        "ImportError: dlopen failed: library 'libopenblas.so' not found"
Akar: di sesi install, smoke test memanggil preload_native_libs() sehingga
libopenblas/libgfortran/libcxx sudah berada di memori proses; run berikutnya
menumpang proses itu. Setelah restart, proses baru hanya menjalankan
activate() lama yang cuma menyuntik sys.path — sedangkan yang mencari
libopenblas.so adalah dynamic linker OS, yang TIDAK melihat sys.path
(penjelasan lengkap: smoke.preload_native_libs). Maka activate() sekarang
juga mem-preload lib*.so dari semua direktori paket aktif, memakai loader
fixpoint yang sama dengan smoke test. Kegagalan preload tetap menjadi
diagnosa, bukan crash: import-lah yang menjadi hakim terakhir.
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
    """Inject sys.path + preload lib*.so paket aktif (Bug N: dua-duanya wajib).

    Urutan: preload DULU baru sys.path — supaya saat script pertama
    mengimpor numpy, seluruh pustaka pendukung sudah di memori proses.
    Preload tidak pernah melempar (kontrak preload_native_libs).
    """
    paths = package_paths(workspace)
    if paths:
        try:
            from .smoke import preload_native_libs
            preload_native_libs(paths)
        except Exception:
            # Loader tidak tersedia (mis. lingkungan dev tanpa modul penuh)
            # → lanjut tanpa preload; import test yang akan bicara.
            pass
    for p in reversed(paths):
        if p not in sys.path:
            sys.path.insert(0, p)
    return paths
