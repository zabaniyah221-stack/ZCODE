"""
wheelinfo — analisis & pencocokan wheel (SPEC-001 §5 Compatibility Algorithm, §7 Wheel Rules).

Menggunakan packaging (ter-bundle bersama pip di Chaquopy):
- parse nama file wheel (PEP 427) via packaging.utils.parse_wheel_filename
- kecocokan tag: irisan antara tag wheel dan tag runtime (packaging.tags.sys_tags)
- prioritas wheel (SPEC §7): tested → Chaquopy Android wheel → universal pure-Python → experimental
"""
from packaging.tags import Tag, sys_tags
from packaging.utils import parse_wheel_filename

from .probe import CHAQUOPY_INDEX_URL

_WHEEL_EXT = ".whl"


class WheelInfoError(ValueError):
    pass


def parse_wheel(filename: str) -> dict:
    """Parse nama file wheel → {name, version, build, tags: [str], filename}."""
    if not filename or not filename.endswith(_WHEEL_EXT):
        raise WheelInfoError("Bukan file wheel (.whl): %s" % filename)
    try:
        name, version, build, tags = parse_wheel_filename(filename)
    except Exception as e:
        raise WheelInfoError("Nama file wheel tidak valid (%s): %s" % (filename, e))
    return {
        "name": str(name),
        "version": str(version),
        "build": str(build[0]) if build else None,
        "tags": sorted(str(t) for t in tags),
        "filename": filename,
    }


def wheel_compatible(filename: str, supported_tags=None) -> bool:
    """
    True bila setidaknya satu tag wheel ada di tag runtime.
    supported_tags: iterable Tag / str. Default sys_tags() (benar di device Chaquopy).
    """
    info = parse_wheel(filename)
    wheel_tags = set()
    for raw in info["tags"]:
        try:
            interp, abi, plat = raw.split("-", 2)
            wheel_tags.add(Tag(interp, abi, plat))
        except ValueError:
            continue
    if not wheel_tags:
        return False

    supported = supported_tags if supported_tags is not None else sys_tags()
    supported_set = set()
    for t in supported:
        if isinstance(t, Tag):
            supported_set.add(t)
        else:
            try:
                interp, abi, plat = str(t).split("-", 2)
                supported_set.add(Tag(interp, abi, plat))
            except ValueError:
                continue
    return bool(wheel_tags & supported_set)


def rank_wheel(filename: str, tested_versions=None, supported_tags=None) -> tuple[int, str]:
    """
    Prioritas SPEC §7:
      1 = ZCODE tested wheel (versi ada di tested_versions)
      2 = Chaquopy Android wheel (platform tag mengandung 'android')
      3 = universal pure-Python (py3-none-any)
      4 = experimental candidate (compatible tapi tidak masuk kategori di atas)
    Return (priority, label). 0 = incompatible (tidak usah dipakai).
    """
    if not wheel_compatible(filename, supported_tags=supported_tags):
        return 0, "incompatible"
    info = parse_wheel(filename)
    tags = info["tags"]
    name = info["name"].lower().replace("_", "-")
    version = info["version"]
    if tested_versions and version in {str(v) for v in tested_versions}:
        return 1, "zcode-tested"
    if any("android" in t.split("-", 2)[2] for t in tags):
        return 2, "chaquopy-android"
    if any(t == "py3-none-any" for t in tags):
        return 3, "universal-pure"
    return 4, "experimental"


def is_universal_pure(filename: str) -> bool:
    try:
        info = parse_wheel(filename)
    except WheelInfoError:
        return False
    return "py3-none-any" in info["tags"] or "py2.py3-none-any" in info["tags"]


def best_wheel(candidates: list[dict], tested_versions=None, supported_tags=None) -> dict | None:
    """
    Pilih wheel terbaik dari daftar kandidat dict {filename, url, sha256?, size?}.
    Return kandidat terpilih dengan field 'priority' & 'compat_reason', atau None.
    """
    ranked = []
    for c in candidates:
        try:
            prio, reason = rank_wheel(
                c.get("filename", ""),
                tested_versions=tested_versions,
                supported_tags=supported_tags,
            )
        except WheelInfoError:
            continue
        if prio > 0:
            ranked.append((prio, c, reason))
    if not ranked:
        return None
    ranked.sort(key=lambda r: (r[0], r[1].get("filename", "")))
    prio, chosen, reason = ranked[0]
    chosen = dict(chosen)
    chosen["priority"] = prio
    chosen["compat_reason"] = reason
    return chosen
