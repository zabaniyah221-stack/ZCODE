"""
resolve — dependency resolver ZCODE (SPEC-001 §5, §12).

Wheel-first, self-contained:
- sumber 1: local wheel cache (python-env/wheels) — offline reuse + ZCODE wheel source
- sumber 2: PyPI JSON API (pypi.org/pypi/<name>/json) — metadata + wheel + sha256
- sumber 3: Chaquopy Android wheel index (https://chaquo.com/pypi-13.1/<name>/) — native

TIDAK pernah memilih sdist. Bila tidak ada wheel yang kompatibel → UNAVAILABLE
dengan alasan (bukan install palsu).

Hasil: plan = {packages: [{name, canonical_name, version, source, filename, url,
sha256, size, requires_dist, extras, priority, compat_reason, reason?}],
conflicts: [...], unavailable: [...]}
"""
import re
import urllib.request

from packaging.requirements import Requirement
from packaging.specifiers import InvalidSpecifier, SpecifierSet
from packaging.tags import sys_tags
from packaging.utils import canonicalize_name
from packaging.version import InvalidVersion, Version

from .probe import CHAQUOPY_INDEX_URL, PYPI_JSON_URL, USER_AGENT
from .requirement import RequirementError, parse_requirement
from .wheelinfo import best_wheel, parse_wheel, wheel_compatible

_NETWORK_TIMEOUT_S = 20
_MAX_DEPTH = 20
_MAX_PACKAGES = 60

_SIMPLE_HREF = re.compile(r'href=["\']([^"\']+\.whl)["\']', re.IGNORECASE)


class ResolveError(Exception):
    """Error resolusi dengan kode stage (SPEC error contract)."""

    def __init__(self, code: str, stage: str, human: str, technical: str = ""):
        super().__init__(human)
        self.code = code
        self.stage = stage
        self.human = human
        self.technical = technical


# ---------------------------------------------------------------------------
# HTTP (small, self-contained; urllib bawaan CPython — jalan di Chaquopy)
# ---------------------------------------------------------------------------

def _http_get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=_NETWORK_TIMEOUT_S) as resp:
            return resp.read()
    except Exception as e:
        raise ResolveError(
            "NETWORK", "metadata",
            "Tidak bisa menghubungi repository package (network error).",
            "GET %s → %s" % (url, e),
        )


def fetch_pypi_metadata(name: str) -> dict:
    try:
        import json
        data = json.loads(_http_get(PYPI_JSON_URL.format(name=name)).decode("utf-8"))
    except ResolveError:
        raise
    except Exception as e:
        raise ResolveError(
            "METADATA", "metadata",
            "Metadata package tidak bisa dibaca dari PyPI.",
            "%s: %s" % (name, e),
        )
    return data


def fetch_chaquopy_wheels(name: str, index_url: str = CHAQUOPY_INDEX_URL) -> list[dict]:
    """Ambil daftar file .whl dari simple index Chaquopy (PEP 503 HTML)."""
    url = index_url.rstrip("/") + "/" + name + "/"
    try:
        html = _http_get(url).decode("utf-8", "replace")
    except ResolveError:
        return []  # package tidak ada di index Chaquopy → bukan error fatal
    out = []
    for href in _SIMPLE_HREF.findall(html):
        fn = href.split("/")[-1]
        if fn.endswith(".whl"):
            full = href if href.startswith("http") else url.rstrip("/") + "/" + href
            out.append({
                "filename": fn,
                "url": full,
                "sha256": None,  # simple index Chaquopy tidak menyediakan hash upstream
                "size": None,
                "source": "chaquopy",
            })
    return out


# ---------------------------------------------------------------------------
# Filtering
# ---------------------------------------------------------------------------

def _contains(spec_str: str, version: str) -> bool:
    if not spec_str:
        return True
    try:
        return SpecifierSet(spec_str).contains(Version(version), prereleases=True)
    except (InvalidSpecifier, InvalidVersion):
        return False


def is_stdlib_module(name: str) -> bool:
    """
    True bila `name` adalah modul bawaan Python (BUG C).

    Memakai sys.stdlib_module_names (CPython 3.10+) supaya jawabannya mengikuti
    runtime yang benar-benar berjalan, bukan daftar statis yang bisa basi.
    Nama dinormalkan: 'Math' dan 'math' sama; tanda '-' diubah ke '_' karena
    pengguna terbiasa mengetik gaya nama paket.
    """
    if not name:
        return False
    import sys
    candidate = name.strip().lower().replace("-", "_")
    names = getattr(sys, "stdlib_module_names", None)
    if names:
        return candidate in names
    # Fallback untuk Python < 3.10 (tidak terjadi di Chaquopy 3.11).
    return candidate in set(sys.builtin_module_names)


def runtime_python_version() -> str:
    """Versi Python runtime ('3.11' di Chaquopy) — pembanding untuk Requires-Python."""
    import sys
    return "%d.%d" % sys.version_info[:2]


def _requires_python_ok(requires_python: str | None, python_version: str | None = None) -> bool:
    """
    Apakah RUNTIME PYTHON memenuhi `Requires-Python` sebuah rilis?

    BUG A — FIX 2026-08-13. Versi lama menerima `version` = versi PAKET lalu
    membandingkannya dengan spesifikasi versi PYTHON:

        SpecifierSet(">=3.7").contains(Version("0.4.6"))  -> False

    Pertanyaan yang diajukan menjadi "apakah colorama 0.4.6 memenuhi >=3.7?",
    padahal yang benar "apakah Python 3.11 memenuhi >=3.7?". Akibatnya SEMUA
    rilis modern dibuang dan hanya rilis kuno (era sebelum Requires-Python ada,
    sehingga field-nya kosong dan lolos otomatis) yang tersisa:

        colorama 13 kandidat -> 7      urllib3 82 -> 23
        pygments 50 -> 16              mdurl     3 -> 0   (PACKAGE_NOT_AVAILABLE)

    Itulah sebab ZCODE memilih colorama 0.3.5 (2015) dan melaporkan mdurl
    "tidak ada wheel kompatibel". Diukur dari metadata PyPI nyata, 2026-08-13.
    """
    if not requires_python:
        return True
    probe = python_version or runtime_python_version()
    try:
        return SpecifierSet(requires_python).contains(Version(probe), prereleases=True)
    except (InvalidSpecifier, InvalidVersion):
        # Spesifikasi rusak di metadata upstream: jangan buang kandidat karenanya.
        return True


def _pypi_candidates(data: dict, spec: dict, python_version: str | None = None) -> list[dict]:
    """Kandidat wheel dari data PyPI JSON, sudah difilter constraint + Requires-Python."""
    out = []
    releases = data.get("releases", {})
    for version, files in releases.items():
        if not _contains(spec["specifier"], version):
            continue
        if not files:
            continue
        # per-file requires_python ada di release? PyPI menaruhnya per file (kadang null)
        for f in files:
            if f.get("packagetype") != "bdist_wheel":
                continue
            if f.get("yanked"):
                continue
            rp = f.get("requires_python") or None
            # BUG A: pembandingnya versi PYTHON runtime, bukan versi paket.
            if not _requires_python_ok(rp, python_version):
                continue
            digest = (f.get("digests") or {}).get("sha256")
            out.append({
                "filename": f.get("filename", ""),
                "url": f.get("url", ""),
                "sha256": digest,
                "size": f.get("size"),
                "source": "pypi",
                "requires_python": rp,
            })
    return out


def _marker_ok(req: Requirement, requested_extras: set[str], marker_env: dict) -> bool:
    if req.marker is None:
        return True
    env = dict(marker_env or {})
    extras = set(requested_extras)
    extras.add("")  # marker tanpa extra
    for e in extras:
        env["extra"] = e
        try:
            if req.marker.evaluate(env):
                return True
        except Exception:
            continue
    return False


def _local_wheel_candidates(wheels_dir: str, name: str) -> list[dict]:
    """Sumber 1: cache wheel lokal (offline reuse)."""
    import os
    out = []
    cname = canonicalize_name(name).replace("-", "_")
    if not wheels_dir or not os.path.isdir(wheels_dir):
        return out
    for fn in sorted(os.listdir(wheels_dir)):
        if not fn.endswith(".whl"):
            continue
        try:
            info = parse_wheel(fn)
        except Exception:
            continue
        if info["name"].lower().replace("_", "-") != name.lower().replace("-", "_"):
            # cocok via canonical name dari filename
            if canonicalize_name(info["name"]) != cname:
                continue
        out.append({
            "filename": fn,
            "url": "file://" + os.path.join(wheels_dir, fn),
            "sha256": None,  # diverifikasi di cache metadata bila ada; fallback hitung ulang
            "size": os.path.getsize(os.path.join(wheels_dir, fn)),
            "source": "local",
            "local_path": os.path.join(wheels_dir, fn),
        })
    return out


# ---------------------------------------------------------------------------
# Resolver utama
# ---------------------------------------------------------------------------

def resolve(
    requirement_text: str,
    supported_tags=None,
    wheels_dir: str | None = None,
    marker_env: dict | None = None,
    tested_versions: dict | None = None,
    max_depth: int = _MAX_DEPTH,
):
    """
    Resolve requirement + seluruh dependensinya → plan install (wheel-only).

    supported_tags: iterable Tag/str; None → sys_tags() runtime (benar di device).
    tested_versions: {canonical_name: [versions]} dari tested-manifest (prioritas ZCODE).
    """
    spec = parse_requirement(requirement_text)  # RequirementError → propagasi
    root_name = spec["canonical_name"]

    # BUG C — FIX 2026-08-13. Modul stdlib TIDAK ADA di PyPI, jadi mencarinya
    # ke sana selalu berakhir "Tidak ada wheel kompatibel untuk runtime ZCODE
    # ini." — pesan yang MENYESATKAN, karena `math` justru sudah tersedia dan
    # memang tidak perlu dipasang. ZCODE bahkan sudah punya daftar 305 nama di
    # assets/package_catalog/stdlib.json, tetapi resolver tak pernah membacanya
    # (kata "stdlib" muncul 0 kali di berkas ini sebelum perbaikan).
    # Sumber kebenaran dipakai sys.stdlib_module_names (CPython 3.10+) karena
    # ia mencerminkan runtime yang BENAR-BENAR berjalan, bukan daftar statis.
    if is_stdlib_module(root_name):
        return {
            "packages": [],
            "conflicts": [],
            "unavailable": [],
            "stdlib": [{
                "name": spec["name"],
                "canonical_name": root_name,
                "reason": (
                    "'%s' adalah modul bawaan Python — sudah tersedia di ZCODE "
                    "dan tidak perlu dipasang. Langsung 'import %s' di script."
                ) % (spec["name"], spec["name"]),
            }],
        }

    plan: dict[str, dict] = {}
    conflicts: list[dict] = []
    unavailable: list[dict] = []
    seen: set[str] = set()
    env = dict(marker_env) if marker_env else {}
    if "python_version" not in env:
        import sys
        env["python_version"] = "%d.%d" % sys.version_info[:2]
        env["sys_platform"] = sys.platform
        env["platform_machine"] = _platform_machine()
        env["platform_python_implementation"] = "CPython"
        env["platform_system"] = "Android" if "android" in sys.platform else "Linux"
    if "extra" not in env:
        env["extra"] = ""

    def queue(name: str, specifier: str, extras: set[str], parent: str | None, depth: int):
        cname = canonicalize_name(name)
        key = (cname, specifier or "*", tuple(sorted(extras)))
        if key in seen or depth > max_depth:
            return
        seen.add(key)

        # sudah direncanakan dengan versi lain → konflik
        existing = plan.get(cname)
        candidates = _collect(cname, specifier)
        if not candidates:
            unavailable.append({
                "name": name, "canonical_name": cname, "parent": parent,
                "reason": "Tidak ada wheel kompatibel untuk runtime ZCODE ini.",
            })
            return
        chosen = _choose(candidates, cname)
        if existing and existing["version"] != chosen["version"]:
            conflicts.append({
                "name": cname,
                "required_by": parent,
                "version_a": existing["version"],
                "version_b": chosen["version"],
                "specifier": specifier,
            })
            return

        plan[cname] = chosen
        # dependensi
        for dep_req in chosen.get("requires_dist", []) or []:
            try:
                req = Requirement(dep_req)
            except Exception:
                continue
            if not _marker_ok(req, extras, env):
                continue
            child_extras = set(extras) | set(req.extras)
            child_spec = str(req.specifier) if req.specifier else ""
            queue(req.name, child_spec, child_extras, cname, depth + 1)

    def _collect(cname: str, specifier: str) -> list[dict]:
        # 1. local cache (offline)
        local = _local_wheel_candidates(wheels_dir or "", cname)
        # 2. PyPI
        pypi = []
        try:
            data = fetch_pypi_metadata(cname)
            pypi = _pypi_candidates(data, {"specifier": specifier})
        except ResolveError:
            pypi = []
        # 3. Chaquopy index (native wheel)
        chaq = fetch_chaquopy_wheels(cname)
        all_cands = local + pypi + chaq
        return all_cands

    def _choose(cands: list[dict], cname: str) -> dict:
        tested = (tested_versions or {}).get(cname)
        best = best_wheel(cands, tested_versions=tested, supported_tags=supported_tags)
        if best is None:
            raise ResolveError(
                "COMPATIBILITY", "resolve",
                "Tidak ada wheel yang kompatibel dengan runtime ZCODE "
                "(Python %s / ABI %s)." % (env.get("python_version", "?"), ",".join(_abi_hint(supported_tags))),
                "candidates=%d" % len(cands),
            )
        best["name"] = cname
        try:
            best["version"] = parse_wheel(best["filename"])["version"]
        except WheelInfoError:
            best["version"] = ""
        # informasi dependensi dari metadata PyPI (bila tersedia)
        best.setdefault("requires_dist", [])
        try:
            data = fetch_pypi_metadata(cname)
            releases = data.get("releases", {})
            files_for_version = releases.get(best["version"], [])
            if files_for_version:
                rp = files_for_version[0].get("requires_python")
                best["requires_python"] = rp
            info = data.get("info", {})
            if "requires_dist" in info and info["requires_dist"]:
                best["requires_dist"] = info["requires_dist"]
            best["summary"] = (info.get("summary") or "")[:200]
            best["license"] = (info.get("license") or "")[:200]
            best["project_url"] = info.get("project_url")
        except ResolveError:
            pass
        return best

    try:
        queue(root_name, spec["specifier"], set(spec["extras"]), None, 0)
    except ResolveError as e:
        raise
    except Exception as e:
        raise ResolveError(
            "RESOLUTION", "resolve",
            "Resolusi dependensi gagal.",
            "%s: %s" % (requirement_text, e),
        )

    return {
        "packages": list(plan.values()),
        "conflicts": conflicts,
        "unavailable": unavailable,
    }


def device_supported_tags(abi: str | None = None, device_api: int | None = None):
    """
    Tag runtime untuk pencocokan wheel — Android-aware (build #3).

    sys_tags() TIDAK dipakai di Android karena menghasilkan tag gaya Linux
    (`linux_armv7l`) yang tidak pernah beririsan dengan tag wheel Chaquopy
    (`android_21_armeabi_v7a`). Lihat wheelinfo.android_supported_tags().

    Bila ABI/API tidak diketahui (mis. dijalankan di desktop untuk
    pengembangan), kembalikan None agar pemanggil jatuh ke sys_tags() —
    perilaku lama yang benar di luar Android.
    """
    from .wheelinfo import android_supported_tags

    resolved_abi = abi
    if not resolved_abi:
        try:
            from .probe import probe_runtime
            abis = probe_runtime().get("abis") or []
            resolved_abi = abis[0] if abis else None
        except Exception:
            resolved_abi = None

    resolved_api = device_api
    if not resolved_api:
        try:
            import sysconfig
            plat = sysconfig.get_platform()  # 'android-21-arm64-v8a'
            if plat.startswith("android"):
                resolved_api = int(plat.split("-")[1])
        except Exception:
            resolved_api = None

    import sys
    is_android = "android" in sys.platform or (resolved_api is not None)
    if not resolved_abi or not is_android:
        return None  # desktop/dev → sys_tags() tetap benar

    py_tag = "cp%d%d" % sys.version_info[:2]
    return android_supported_tags(resolved_abi, resolved_api or 21, py_tag)


def resolve_json(
    requirement_text: str,
    wheels_dir: str | None = None,
    marker_env_json: str | None = None,
    tested_versions_json: str | None = None,
    abi: str | None = None,
    device_api: int | None = None,
) -> str:
    """Wrapper JSON-string untuk Kotlin. Error → dict {ok:false, code, stage, ...}."""
    import json
    try:
        marker_env = json.loads(marker_env_json) if marker_env_json else None
        tested = json.loads(tested_versions_json) if tested_versions_json else None
        plan = resolve(
            requirement_text,
            # BUILD #3: tag Android dibangun sendiri; None hanya di desktop/dev.
            supported_tags=device_supported_tags(abi, device_api),
            wheels_dir=wheels_dir,
            marker_env=marker_env,
            tested_versions=tested,
        )
        plan["ok"] = True
        return json.dumps(plan, default=str)
    except RequirementError as e:
        return json.dumps({"ok": False, "code": "REQUIREMENT", "stage": "parse",
                           "human": str(e), "technical": ""})
    except ResolveError as e:
        return json.dumps({"ok": False, "code": e.code, "stage": e.stage,
                           "human": e.human, "technical": e.technical})
    except Exception as e:  # noqa: BLE001
        return json.dumps({"ok": False, "code": "RESOLUTION", "stage": "resolve",
                           "human": "Resolusi dependensi gagal.", "technical": str(e)})


def _platform_machine():
    import platform
    return platform.machine()


def _abi_hint(supported_tags):
    hint = set()
    if not supported_tags:
        return ["unknown"]
    for t in supported_tags:
        s = str(t)
        if "android" in s:
            hint.add(s.split("-")[-1])
    return sorted(hint) or ["unknown"]
