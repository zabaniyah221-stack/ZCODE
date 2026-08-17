"""
wheelinfo — analisis & pencocokan wheel (SPEC-001 §5 Compatibility Algorithm, §7 Wheel Rules).

Menggunakan packaging (ter-bundle bersama pip di Chaquopy):
- parse nama file wheel (PEP 427) via packaging.utils.parse_wheel_filename
- kecocokan tag: irisan antara tag wheel dan tag runtime (packaging.tags.sys_tags)
- prioritas wheel (SPEC §7): tested → Chaquopy Android wheel → universal pure-Python → experimental
"""
from packaging.tags import Tag, sys_tags
from packaging.utils import parse_wheel_filename
from packaging.version import Version

from .probe import CHAQUOPY_INDEX_URL

_WHEEL_EXT = ".whl"

# API Android terendah yang pernah dipakai wheel Chaquopy (android_16_*).
_MIN_ANDROID_API = 16


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


# BUG F — FIX 2026-08-13. Platform tag yang HARUS selalu ditolak di Android.
#
# Wheel `manylinux*` / `musllinux*` / `linux_*` dibangun untuk Linux desktop
# yang memakai **glibc** (atau musl). Android memakai **bionic**. Simbol libc-nya
# berbeda, jadi memuat .so semacam itu bukan menghasilkan ImportError yang sopan
# melainkan **SIGSEGV native** — crash yang TIDAK bisa ditangkap try/except dan
# tidak meninggalkan traceback Python sama sekali.
#
# Penjagaan ini disengaja bersifat "sabuk dan bretel": normalnya tag tersebut
# memang tidak akan cocok dengan tag runtime, tetapi bila suatu saat daftar tag
# dibangun sendiri (perbaikan tag Android, build #3) satu salah ketik saja bisa
# meloloskannya. Biaya kekeliruan di sini adalah crash yang paling sulit
# didiagnosis di perangkat tanpa logcat, jadi penolakannya dibuat eksplisit.
#
# Sumber: PEP 738 (Android hanya arm64_v8a & x86_64 sebagai tier 3) dan daftar
# arch cibuildwheel yang TIDAK memuat armeabi_v7a sama sekali —
# https://cibuildwheel.pypa.io/en/stable/options/
_FOREIGN_PLATFORM_PREFIXES = ("manylinux", "musllinux", "linux_")


def is_foreign_platform_tag(platform_tag: str) -> bool:
    """True bila platform tag milik Linux desktop (glibc/musl), bukan Android."""
    p = (platform_tag or "").strip().lower()
    return any(p.startswith(prefix) for prefix in _FOREIGN_PLATFORM_PREFIXES)


def android_supported_tags(
    abi: str,
    device_api: int,
    python_tag: str = "cp311",
) -> list[Tag]:
    """
    Bangun daftar tag Android yang sah untuk perangkat ini — TANPA sys_tags().

    FIX 2026-08-13 (build #3). Inilah sebab numpy/pandas/pillow/matplotlib
    selalu ditolak. packaging.tags.sys_tags() di Chaquopy menghasilkan tag gaya
    Linux (`linux_armv7l`), sementara wheel di indeks Chaquopy bertag
    `android_21_armeabi_v7a`. Irisannya kosong, jadi SETIAP wheel native
    dinyatakan "tidak kompatibel" walaupun sebenarnya cocok sempurna.

    Aturan yang dipakai (sesuai PEP 738 dan implementasi cibuildwheel):
      * platform tag = ``android_<api>_<abi>``, abi memakai garis bawah
        (`armeabi_v7a`, `arm64_v8a`, `x86_64`)
      * sebuah wheel cocok bila **API wheel <= API perangkat** — wheel
        android_21 jalan di perangkat API 31, tidak sebaliknya
      * ABI wajib sama persis; tidak ada substitusi lintas-arsitektur

    Sumber:
      - https://peps.python.org/pep-0738/
      - https://cibuildwheel.pypa.io/en/stable/options/
        (``ANDROID_API_LEVEL``, default 24, menjadi bagian tag)
      - survei indeks nyata: docs/ARMV7_COMPAT_2026_08_13.md

    Catatan jujur: ARMv7 sama sekali tidak ada di daftar arsitektur
    cibuildwheel, jadi indeks Chaquopy adalah satu-satunya sumber wheel native
    ARMv7 Android. Karena itu daftar tag ini dibangun sendiri alih-alih
    menunggu dukungan hulu.
    """
    norm_abi = (abi or "").strip().lower().replace("-", "_")
    tags: list[Tag] = []
    if norm_abi:
        api = max(int(device_api or 0), _MIN_ANDROID_API)
        # API menurun: wheel dengan API tertinggi yang masih <= perangkat menang.
        for wheel_api in range(api, _MIN_ANDROID_API - 1, -1):
            platform = "android_%d_%s" % (wheel_api, norm_abi)
            for abi_tag in (python_tag, "abi3", "none"):
                tags.append(Tag(python_tag, abi_tag, platform))
            # FIX 2026-08-13 (lanjutan): interpreter py3/py2.py3 dengan platform
            # ANDROID — bukan hanya "any".
            #
            # Pustaka pendukung Chaquopy (chaquopy-openblas, chaquopy-libjpeg,
            # dst) bertag `py3-none-android_16_armeabi_v7a`: TIDAK terikat versi
            # Python (isinya cuma satu file .so, bukan modul Python), tetapi
            # TETAP terikat CPU. Versi pertama hanya membangkitkan cp311-*
            # untuk platform Android dan py3-none-any tanpa platform, sehingga
            # kombinasi ini tidak pernah ada — seluruh pustaka pendukung
            # ditolak, dan numpy tetap gagal walau openblas sudah diunduh.
            for py_tag in ("py3", "py2.py3"):
                tags.append(Tag(py_tag, "none", platform))
    # Pure-Python selalu sah, di ABI mana pun.
    tags.append(Tag("py3", "none", "any"))
    tags.append(Tag("py2.py3", "none", "any"))
    return tags


def android_supported_tags_json(abi: str, device_api: int, python_tag: str = "cp311") -> str:
    """Wrapper JSON untuk Kotlin/diagnostik."""
    import json
    return json.dumps([str(t) for t in android_supported_tags(abi, device_api, python_tag)])


def wheel_compatible(filename: str, supported_tags=None) -> bool:
    """
    True bila setidaknya satu tag wheel ada di tag runtime.
    supported_tags: iterable Tag / str. Default sys_tags() (benar di device Chaquopy).

    Wheel Linux desktop (glibc/musl) SELALU ditolak lebih dulu — lihat
    _FOREIGN_PLATFORM_PREFIXES.
    """
    info = parse_wheel(filename)
    # BUG F: tolak sebelum pencocokan apa pun.
    for raw in info["tags"]:
        parts = raw.split("-", 2)
        if len(parts) == 3 and is_foreign_platform_tag(parts[2]):
            return False
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


def _version_key(filename: str) -> Version:
    """Versi wheel sebagai objek Version (BUG D). Tak terbaca -> paling tua."""
    try:
        return Version(parse_wheel(filename)["version"])
    except Exception:
        return Version("0")


class _NegVersion:
    """Pembungkus untuk mengurutkan Version MENURUN di dalam sort menaik.

    `Version` tidak mendukung negasi, dan `reverse=True` akan ikut membalik
    kunci prioritas. Membalik hanya perbandingan versi menjaga prioritas tetap
    menaik (1 = terbaik) sementara versi menjadi menurun (terbaru menang).
    """

    __slots__ = ("v",)

    def __init__(self, v: Version):
        self.v = v

    def __lt__(self, other: "_NegVersion") -> bool:
        return other.v < self.v

    def __eq__(self, other: object) -> bool:
        return isinstance(other, _NegVersion) and self.v == other.v


def best_wheel(candidates: list[dict], tested_versions=None, supported_tags=None) -> dict | None:
    """
    Pilih wheel terbaik dari daftar kandidat dict {filename, url, sha256?, size?}.
    Return kandidat terpilih dengan field 'priority' & 'compat_reason', atau None.
    """
    ranked = []
    for c in candidates:
        fn = c.get("filename", "")
        try:
            prio, reason = rank_wheel(
                fn,
                tested_versions=tested_versions,
                supported_tags=supported_tags,
            )
        except WheelInfoError:
            continue
        if prio > 0:
            ranked.append((prio, _version_key(fn), c, reason))
    if not ranked:
        return None
    # BUG D — FIX 2026-08-13. Versi lama mengurutkan dengan
    # `key=(prio, filename)` lalu mengambil ranked[0]: itu urutan ALFABETIS
    # atas nama file, bukan urutan versi. Akibatnya:
    #   "0.3.5" < "0.4.6"        -> colorama 2015 selalu menang
    #   "urllib3-1.10" < "1.9"   -> '1' < '9', jadi 1.10 dikira LEBIH TUA
    # Simulasi pipeline penuh memproduksi ulang colorama-0.3.5, requests-2.0.0
    # (2013) dan Click-7.0 — sama persis dengan log perangkat user.
    # Sekarang: prioritas menaik (1 terbaik), lalu VERSI MENURUN (terbaru menang).
    #
    # BUG S (2026-08-16, log UAT Infinix): "versi terbaru" tanpa pandang bulu
    # membuat pre-release menang — apscheduler 4.0.0a6, isort 9.0.0b2, plotly
    # 7.0.0rc0, sqlalchemy 2.1.0b3, pydantic 2.14.0b1, stripe 15.6.0a1,
    # watchfiles 0.0.0a1 (placeholder kosong). Aturan pip/PEP 440: pre-release
    # HANYA dipilih bila tidak ada rilis stable sama sekali (atau specifier
    # secara eksplisit memintanya — kasus itu sudah tersaring di _contains
    # sebelum kandidat sampai ke sini). Terapkan hal yang sama.
    stable = [r for r in ranked if not r[1].is_prerelease]
    if stable:
        ranked = stable
    ranked.sort(key=lambda r: (r[0], _NegVersion(r[1])))
    prio, _vkey, chosen, reason = ranked[0]
    chosen = dict(chosen)
    chosen["priority"] = prio
    chosen["compat_reason"] = reason
    return chosen
