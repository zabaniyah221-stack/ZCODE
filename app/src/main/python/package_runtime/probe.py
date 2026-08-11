"""
probe — runtime diagnostic (SPEC-001 "Required Investigation Before Package Changes").

Output dict ini disimpan Kotlin ke python-env/state/runtime.json dan dipakai
CompatibilityEngine untuk menilai wheel/package terhadap runtime yang nyata.
Di device: Chaquopy 3.11; di sandbox/CI: CPython host (untuk pengembangan test).
"""
import platform
import struct
import sys
import sysconfig

try:
    import packaging
    _PACKAGING_VERSION = packaging.__version__
except Exception:  # pragma: no cover
    _PACKAGING_VERSION = "unknown"

try:
    import pip
    _PIP_VERSION = pip.__version__
except Exception:  # pragma: no cover
    _PIP_VERSION = "unknown"

# ZCODE pin Chaquopy plugin — SINGLE SOURCE: app/build.gradle.kts.
# Jaga sinkron: ubah di sini DAN di root build.gradle.kts saat upgrade Chaquopy.
CHAQUOPY_VERSION = "17.0.0"
PYTHON_VERSION = "3.11"
# Index wheel native Chaquopy (docs 17.0 FAQ masih memakai pypi-13.1).
CHAQUOPY_INDEX_URL = "https://chaquo.com/pypi-13.1/"
PYPI_JSON_URL = "https://pypi.org/pypi/{name}/json"
USER_AGENT = "zcode-package-runtime/1.0"


def _bits():
    try:
        return struct.calcsize("P") * 8
    except Exception:
        return 0


def probe_runtime(android_api=None):
    """Kumpulkan info runtime. `android_api` diisi Kotlin (Build.VERSION.SDK_INT)."""
    try:
        import packaging.tags as pt
        tags = [str(t) for t in pt.sys_tags()]
    except Exception:
        tags = []
    try:
        abis = _native_abis()
    except Exception:
        abis = []

    return {
        "python_version": platform.python_version(),
        "python_full": sys.version,
        "python_bits": _bits(),
        "machine": platform.machine(),
        "platform": sysconfig.get_platform(),
        "abis": abis,
        "android_api": android_api,
        "pip_version": _PIP_VERSION,
        "packaging_version": _PACKAGING_VERSION,
        "chaquopy_version": CHAQUOPY_VERSION,
        "pinned_python_version": PYTHON_VERSION,
        "supported_tags": tags,
        "sys_path": list(sys.path),
        "site_packages": _site_packages(),
    }


def _native_abis():
    """ABI perangkat: pakai sysconfig platform android jika ada, else machine."""
    plat = sysconfig.get_platform()  # mis. 'android-21-arm64-v8a' atau 'linux-x86_64'
    if plat.startswith("android"):
        # android-21-arm64-v8a → arm64-v8a
        parts = plat.split("-")
        if len(parts) >= 3:
            abi = "-".join(parts[2:]) if len(parts) > 3 else parts[2]
            return [abi.replace("_", "-")] if abi else []
        return []
    machine = platform.machine().lower()
    if machine in ("aarch64", "arm64"):
        return ["arm64-v8a"]
    if machine in ("armv7l", "armv8l"):
        return ["armeabi-v7a"]
    if machine in ("x86_64", "amd64"):
        return ["x86_64"]
    if machine in ("i686", "i386", "x86"):
        return ["x86"]
    return [machine]


def _site_packages():
    try:
        return [p for p in sys.path if "site-packages" in p]
    except Exception:
        return []


def runtime_summary():
    r = probe_runtime()
    return (
        "python=%s abi=%s platform=%s pip=%s packaging=%s tags=%d chaquopy=%s"
        % (
            r["python_version"],
            ",".join(r["abis"]),
            r["platform"],
            r["pip_version"],
            r["packaging_version"],
            len(r["supported_tags"]),
            r["chaquopy_version"],
        )
    )


def probe_runtime_json(android_api=None):
    """Versi JSON-string untuk Kotlin (PyObject.toString() dict = repr, bukan JSON)."""
    import json
    return json.dumps(probe_runtime(android_api), default=str)
