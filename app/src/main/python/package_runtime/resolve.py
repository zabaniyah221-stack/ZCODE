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
import contextvars
import http.client
import json
import os
import random
import re
import socket
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

from packaging.requirements import Requirement
from packaging.specifiers import InvalidSpecifier, SpecifierSet
from packaging.tags import sys_tags
from packaging.utils import canonicalize_name
from packaging.version import InvalidVersion, Version

from .probe import CHAQUOPY_INDEX_URL, PYPI_JSON_URL, USER_AGENT
from .requirement import RequirementError, parse_requirement
from .wheelinfo import WheelInfoError, best_wheel, parse_wheel, wheel_compatible

_NETWORK_TIMEOUT_S = 20
# v1.0.15 memakai 3 × 20 detik per URL sementara PyCall memotong SELURUH
# dependency graph pada 90 detik. Dua total attempt cukup untuk satu kegagalan
# transient tanpa melipatgandakan waktu buta di jaringan seluler.
# 3 sejak v1.0.18-polish: yt-dlp gagal URLError di attempt 2/2 lalu sukses
# manual (UAT 2026-08-16) — jaringan 4G user sering kedip sesaat. 404 TIDAK
# ikut retry (lihat _retryable_error): jawaban pasti, bukan gangguan.
_MAX_HTTP_ATTEMPTS = 3
_RETRYABLE_HTTP_STATUS = frozenset({408, 429, 500, 502, 503, 504, 520, 527})
_MAX_DEPTH = 20
_MAX_PACKAGES = 60

# ContextVar mengikat progress/cancel ke satu pemanggilan resolve. Jangan pakai
# satu bridge global: callback resolver lama dapat menimpa operasi baru.
_CURRENT_BRIDGE = contextvars.ContextVar("zcode_resolve_bridge", default=None)
_CURRENT_PACKAGE = contextvars.ContextVar("zcode_resolve_package", default="")
_RESOLVE_LOCK = threading.Lock()

_SIMPLE_HREF = re.compile(r'href=["\']([^"\']+\.whl)["\']', re.IGNORECASE)


class ResolveError(Exception):
    """Error resolusi dengan kode stage (SPEC error contract)."""

    def __init__(self, code: str, stage: str, human: str, technical: str = ""):
        super().__init__(human)
        self.code = code
        self.stage = stage
        self.human = human
        self.technical = technical


def _propagate_cancel(error: "ResolveError") -> None:
    """Cancel adalah control-flow, bukan kegagalan source yang boleh fallback.

    Full Android ARMv7 menemukan event `cancelled` ditelan oleh catch PyPI dan
    Chaquopy, lalu berubah menjadi COMPATIBILITY. Setiap fallback ResolveError
    wajib memanggil helper ini sebelum memakai source berikutnya.
    """
    if error.code == "CANCELLED":
        raise error


# ---------------------------------------------------------------------------
# HTTP (small, self-contained; urllib bawaan CPython — jalan di Chaquopy)
# ---------------------------------------------------------------------------

def _source_for_url(url: str) -> str:
    """Nama sumber aman untuk log — URL penuh sengaja tidak diekspos ke UI."""
    host = (urllib.parse.urlparse(url).hostname or "").lower()
    if host == "pypi.org" or host.endswith(".pythonhosted.org"):
        return "pypi"
    if host == "chaquo.com" or host.endswith(".chaquo.com"):
        return "chaquopy"
    return host or "repository"


def _emit_progress(stage: str, source: str = "", attempt: int = 0,
                   max_attempts: int = 0, detail: str = "") -> None:
    """Kirim event terstruktur ke Kotlin; kegagalan UI tidak boleh fatal."""
    bridge = _CURRENT_BRIDGE.get()
    if bridge is None:
        return
    event = {
        "stage": stage,
        "package": _CURRENT_PACKAGE.get(),
        "source": source,
        "attempt": attempt,
        "max_attempts": max_attempts,
        "detail": (detail or "")[:240],
    }
    try:
        bridge.emit(json.dumps(event, ensure_ascii=False))
    except Exception:
        pass


def _check_cancelled() -> None:
    bridge = _CURRENT_BRIDGE.get()
    if bridge is None:
        return
    try:
        cancelled = bool(bridge.isCancelled())
    except Exception:
        cancelled = False
    if cancelled:
        _emit_progress("cancelled", detail="permintaan pengguna")
        raise ResolveError(
            "CANCELLED", "resolve", "Analisis package dibatalkan.",
            "cooperative cancellation acknowledged",
        )


def _is_certificate_error(error: Exception) -> bool:
    """Deteksi tanpa import `ssl` eager (penting di probe bionic minimal)."""
    current = error
    for _ in range(3):
        name = type(current).__name__.lower()
        text = str(current).lower()
        if "certificateverification" in name or "certificate verify failed" in text:
            return True
        nxt = getattr(current, "reason", None)
        if not isinstance(nxt, BaseException) or nxt is current:
            break
        current = nxt
    return False


def _retryable_error(error: Exception) -> bool:
    if isinstance(error, urllib.error.HTTPError):
        return error.code in _RETRYABLE_HTTP_STATUS
    # Sertifikat/hostname salah tidak akan sembuh dengan retry identik.
    if _is_certificate_error(error):
        return False
    if isinstance(error, (socket.timeout, TimeoutError, ConnectionError,
                          http.client.IncompleteRead)):
        return True
    if isinstance(error, urllib.error.URLError):
        return True
    return False


def _retry_wait(attempt: int) -> None:
    """Backoff pendek + jitter; tidur per 100ms agar Cancel responsif."""
    remaining = min(1.5, 0.35 * (2 ** max(0, attempt - 1))) + random.uniform(0.0, 0.25)
    while remaining > 0:
        _check_cancelled()
        step = min(0.1, remaining)
        time.sleep(step)
        remaining -= step


def _http_get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    source = _source_for_url(url)
    last_summary = "unknown"
    last_status = None
    attempts_made = 0
    for attempt in range(1, _MAX_HTTP_ATTEMPTS + 1):
        attempts_made = attempt
        _check_cancelled()
        _emit_progress(
            "http_begin", source=source, attempt=attempt,
            max_attempts=_MAX_HTTP_ATTEMPTS, detail="metadata",
        )
        started = time.monotonic()
        try:
            with urllib.request.urlopen(req, timeout=_NETWORK_TIMEOUT_S) as resp:
                body = resp.read()
            _check_cancelled()
            _emit_progress(
                "http_ok", source=source, attempt=attempt,
                max_attempts=_MAX_HTTP_ATTEMPTS,
                detail="%d bytes, %.1fs" % (len(body), time.monotonic() - started),
            )
            return body
        except ResolveError:
            raise
        except Exception as e:
            retry = _retryable_error(e) and attempt < _MAX_HTTP_ATTEMPTS
            status = getattr(e, "code", None)
            last_status = status
            detail = "%s%s" % (
                type(e).__name__, " HTTP %s" % status if status is not None else ""
            )
            # Jangan masukkan URL mentah/credential ke Diagnostics.
            last_summary = detail
            # HTTP 404 pada probe sumber = "toko ini tidak menjual paket itu".
            # Itu alur NORMAL (probe Chaquopy dulu → fallback PyPI), bukan
            # kegagalan jaringan. Sebelumnya dicatat "http_fail HTTPError
            # HTTP 404" — ±90 baris "fail" palsu per sesi UAT menutupi error
            # sungguhan. Keputusan user 2026-08-17: label TARGET NOT FOUND,
            # tanpa detail. `http_fail` tetap untuk kegagalan nyata
            # (timeout/DNS/5xx/koneksi putus).
            if status == 404:
                stage = "target_not_found"
                detail = ""
            else:
                stage = "http_retry" if retry else "http_fail"
            _emit_progress(
                stage,
                source=source, attempt=attempt,
                max_attempts=_MAX_HTTP_ATTEMPTS, detail=detail,
            )
            if not retry:
                break
            _retry_wait(attempt)
    not_found = last_status == 404
    raise ResolveError(
        "SOURCE_NOT_FOUND" if not_found else "NETWORK",
        "metadata",
        ("Package tidak ditemukan di repository ini." if not_found else
         "Tidak bisa menghubungi repository package (network error)."),
        "GET source=%s package=%s attempts=%d → %s" % (
            source, _CURRENT_PACKAGE.get(), attempts_made, last_summary
        ),
    )


# Singgahan metadata PyPI selama satu proses resolusi.
#
# KENAPA ADA (2026-08-13, v1.0.14). `fetch_pypi_metadata` dipanggil DUA KALI
# untuk setiap paket dengan URL yang persis sama: sekali di `_collect()` untuk
# mencari kandidat wheel, sekali lagi di `_choose()` untuk membaca metadata.
# Balasan endpoint itu memuat SELURUH riwayat rilis — diukur langsung ke PyPI:
#
#     /pypi/matplotlib/json -> 2.390 KB (142 rilis)
#     /pypi/pandas/json     -> 2.081 KB (117 rilis)
#
# Untuk matplotlib (11 paket dalam rencana) itu berarti 33 panggilan dan
# sekitar 32 MB, yang di jaringan 4G memakan ~182 detik dan menabrak batas
# waktu 90 detik di PyCall — persis kegagalan "senyap" yang dilaporkan dari
# perangkat. Sepertiga dari trafik itu murni terbuang karena duplikat.
_METADATA_CACHE: dict[str, dict] = {}
# Negative cache per-resolve. Tanpa ini, satu 404 PyPI support library dibaca
# berulang oleh _collect + tiga fallback _choose (terlihat 4× di emulator).
# Simpan data error, bukan object exception/traceback.
_METADATA_ERROR_CACHE: dict[str, tuple[str, str, str, str]] = {}
# Singgahan metadata PER-VERSI (BUG K). Kunci: (nama, versi). Dipisah dari
# cache utama karena meng-hash per rilis, bukan satu dokumen besar.
_METADATA_VERSION_CACHE: dict[tuple[str, str], dict] = {}


def clear_metadata_cache() -> None:
    """Kosongkan singgahan — dipanggil di awal setiap resolusi baru."""
    _METADATA_CACHE.clear()
    _METADATA_ERROR_CACHE.clear()
    _METADATA_VERSION_CACHE.clear()


def fetch_pypi_metadata(name: str) -> dict:
    kunci = (name or "").strip().lower()
    if kunci in _METADATA_CACHE:
        return _METADATA_CACHE[kunci]
    cached_error = _METADATA_ERROR_CACHE.get(kunci)
    if cached_error is not None:
        raise ResolveError(*cached_error)
    try:
        data = json.loads(_http_get(PYPI_JSON_URL.format(name=name)).decode("utf-8"))
    except ResolveError as e:
        _propagate_cancel(e)
        _METADATA_ERROR_CACHE[kunci] = (e.code, e.stage, e.human, e.technical)
        raise
    except Exception as e:
        raise ResolveError(
            "METADATA", "metadata",
            "Metadata package tidak bisa dibaca dari PyPI.",
            "%s: %s" % (name, e),
        )
    _METADATA_CACHE[kunci] = data
    return data


def fetch_pypi_metadata_version(name: str, version: str) -> dict:
    """Metadata PyPI untuk SATU VERSI spesifik.

    BUG K (2026-08-13). `info.requires_dist` pada `/pypi/<nama>/json` SELALU
    milik rilis TERBARU, padahal ZCODE memilih versi tertentu (bisa lama). Saat
    dependensi berbeda antara versi terpilih dan versi terbaru, dependensi jadi
    salah → `ModuleNotFoundError` (contoh: pandas 2.1.3 butuh pytz, rich 13.5.3
    butuh typing-extensions — keduanya hilang saat fallback ke versi terbaru).

    Endpoint `/pypi/<nama>/<versi>/json` mengembalikan `info.requires_dist`
    yang BENAR untuk versi itu. Terverifikasi (2026-08-13) terhadap pandas
    2.1.3 (pytz wajib) dan rich 13.5.3 (typing-extensions wajib).

    Return dict mentah; None bila versi tidak ada / gagal (bukan fatal).
    """
    if not name or not version:
        return None
    kunci = ((name or "").strip().lower(), str(version).strip())
    if kunci in _METADATA_VERSION_CACHE:
        return _METADATA_VERSION_CACHE[kunci]
    url = "https://pypi.org/pypi/%s/%s/json" % (kunci[0], kunci[1])
    try:
        data = json.loads(_http_get(url).decode("utf-8"))
    except ResolveError as e:
        _propagate_cancel(e)
        return None
    except Exception:
        return None
    _METADATA_VERSION_CACHE[kunci] = data
    return data


def requires_dist_for_version(name: str, version: str) -> list[str]:
    """`requires_dist` (hanya wajib, tanpa extra) untuk versi spesifik.

    Sumber paling jujur untuk BUG K: endpoint per-versi. Bila tidak tersedia,
    kembalikan [] — pemanggil memutuskan fallback.
    """
    meta = fetch_pypi_metadata_version(name, version)
    if not meta:
        return []
    info = meta.get("info") or {}
    raw = info.get("requires_dist") or []
    return [r for r in raw if "extra ==" not in (r or "")]


# ---------------------------------------------------------------------------
# Dependensi pustaka NATIVE (build #3 lanjutan, 2026-08-13)
#
# AKAR MASALAH: `import numpy` gagal dengan
#     dlopen failed: library "libopenblas.so" not found
# padahal wheel numpy sudah terpasang benar.
#
# Chaquopy memisahkan pustaka C bersama (OpenBLAS, libjpeg, freetype, ...)
# menjadi paket `chaquopy-*` tersendiri supaya tidak diduplikasi di setiap
# wheel. Pemisahan itu HANYA tercatat di `requirements.host` pada meta.yaml
# resep Chaquopy — PyPI tidak tahu apa-apa soal ini, dan justru melaporkan
# numpy TANPA dependensi karena wheel PyPI biasa sudah memuat OpenBLAS di
# dalamnya. Resolver kita membaca PyPI, jadi pustaka pendukung tidak pernah
# ikut terunduh.
#
# Peta di bawah disalin dari `requirements.host` masing-masing resep:
#   https://github.com/chaquo/chaquopy/tree/master/server/pypi/packages/<nama>/meta.yaml
# (diperiksa 2026-08-13). Hanya entri yang benar-benar dibaca dari sumbernya
# yang dicantumkan — tidak ada tebakan berdasarkan pola nama, karena
# `cryptography` membuktikan polanya tidak seragam (butuh `openssl`, tanpa
# awalan `chaquopy-`).
#
# Versi sengaja TIDAK dipatok. Indeks Chaquopy hanya menyimpan satu versi per
# pustaka pendukung, dan mematoknya di sini berarti peta ini basi setiap kali
# hulu memperbarui.
#
# TAMBAHAN DARI PERANGKAT (2026-08-13). Sebagian kebutuhan TIDAK tercatat di
# meta.yaml mana pun dan hanya terlihat saat berjalan. Log user v1.0.9:
#     preload gagal: libopenblas.so
#       (dlopen failed: library "libgfortran.so.3" not found)
# meta.yaml chaquopy-openblas TIDAK punya requirements.host sama sekali, jadi
# hubungan ini mustahil diketahui dari dokumen. Sumbernya = bukti runtime.
# Entri semacam itu ditandai "[dari perangkat]" supaya jelas dasarnya berbeda.
NATIVE_HOST_DEPS: dict[str, list[str]] = {
    # [dari perangkat] log v1.0.10: _multiarray_umath.so menautkan
    # libc++_shared.so. Peta ini hanya jaring PERTAMA — jaring sesungguhnya
    # adalah pemindaian DT_NEEDED di nativemap.py, yang menemukan kebutuhan
    # ini tanpa perlu ditulis lebih dulu. Entri di sini menghemat satu putaran
    # unduh untuk paket yang sudah kita ketahui polanya.
    "numpy": ["chaquopy-openblas", "chaquopy-libcxx"],
    # [dari perangkat] libopenblas.so menautkan libgfortran.so.3
    "chaquopy-openblas": ["chaquopy-libgfortran"],
    # [dari meta.yaml] libxslt menautkan libxml2
    "chaquopy-libxslt": ["chaquopy-libxml2"],
    "pandas": ["numpy"],
    "matplotlib": ["chaquopy-freetype", "chaquopy-libpng", "numpy"],
    "pillow": ["chaquopy-libjpeg", "chaquopy-freetype"],
    "lxml": ["chaquopy-libxml2", "chaquopy-libxslt"],
    "pyyaml": ["chaquopy-libyaml"],
    # [dari perangkat] BUG Q (2026-08-16, breadcrumb Infinix): instal PERTAMA
    # murmurhash/preshed gagal "libc++_shared.so not found: needed by mrmr.so"
    # karena wheel chaquopy-libcxx belum ada di cache saat smoke. Percobaan
    # kedua sukses (cache terisi) — pola persis BUG P. Entri ini memastikan
    # libcxx ikut ter-resolve SEBELUM smoke pada instal pertama.
    "murmurhash": ["chaquopy-libcxx"],
    "cymem": ["chaquopy-libcxx"],
    "preshed": ["chaquopy-libcxx"],
    # [dari perangkat + arsip mass-test 2026-08-16] kelas Bug Q juga:
    # pycurl gagal UAT 2026-08-17 "libcurl.so not found" (breadcrumb device);
    # lameenc/pyproj gagal mass-test bionic311 "libmp3lame.so/libproj.so not
    # found" (docs/mass-test-armv7-2026-08-16.jsonl). METADATA wheel toko
    # Chaquopy menyebut host-dep ini, tapi baru terbaca SETELAH wheel masuk
    # cache — celah instal-pertama. Entri di sini menutupnya.
    "pycurl": ["chaquopy-curl-openssl-3"],
    "lameenc": ["chaquopy-lame"],
    "pyproj": ["chaquopy-proj-openssl-3"],
    # [dari METADATA wheel + bionic311 2026-08-17] libproj.so menautkan
    # libtiff.so, dan libtiff.so menautkan libjpeg_chaquopy.so (import
    # pyproj gagal dlopen berlapis sebelum rantai lengkap). METADATA
    # chaquopy-proj/libtiff menyebut semuanya, tapi terbaca SETELAH wheel
    # masuk cache — celah instal-pertama yang sama, dua level lebih dalam.
    # Preseden pola: chaquopy-openblas -> chaquopy-libgfortran.
    "chaquopy-proj-openssl-3": [
        "chaquopy-libcxx", "chaquopy-curl-openssl-3", "chaquopy-libtiff",
    ],
    "chaquopy-libtiff": ["chaquopy-libjpeg", "chaquopy-libcxx"],
    # [dari perangkat, UAT maraton 2026-08-16] hidden-dep murni-Python:
    # matplotlib-inline mengimpor matplotlib saat dipakai ipython, tapi
    # METADATA-nya TIDAK menyebut matplotlib (dep opsional runtime).
    # Bukti dua arah: ipython gagal saat matplotlib belum aktif, sukses
    # setelah matplotlib terpasang. Peta ini sudah terbukti boleh membawa
    # paket Python penuh, bukan hanya .so (lihat "pandas": ["numpy"]).
    "matplotlib-inline": ["matplotlib"],
    "opencv-python": [
        "chaquopy-libgfortran", "chaquopy-libpng", "chaquopy-libjpeg",
        "chaquopy-openblas", "numpy",
    ],
    "scipy": ["chaquopy-openblas", "chaquopy-libgfortran", "numpy"],
    "h5py": ["chaquopy-hdf5", "numpy"],
    "pyzmq": ["chaquopy-libzmq"],
    "shapely": ["chaquopy-geos"],
    "argon2-cffi-bindings": ["cffi"],
    # [dari perangkat] BUG P (2026-08-16, breadcrumb Infinix): percobaan
    # argon2-cffi PERTAMA gagal smoke test "libffi.so not found: needed by
    # _cffi_backend.so" — wheel cffi belum di cache sehingga jaring METADATA
    # wheel belum bisa membaca kebutuhan chaquopy-libffi (resolve berjalan
    # SEBELUM download). Percobaan jwt berikutnya sukses karena wheel cffi
    # sudah di cache dari transaksi yang di-rollback. Entri ini menutup
    # celah instal-pertama; METADATA wheel tetap jaring kedua.
    "cffi": ["chaquopy-libffi"],
}


def native_host_deps(canonical_name: str) -> list[str]:
    """Pustaka pendukung yang WAJIB ikut dipasang bersama paket ini.

    Mengembalikan daftar kosong bila paket tidak butuh apa pun — itu kasus
    mayoritas (semua paket pure-Python).
    """
    return list(NATIVE_HOST_DEPS.get((canonical_name or "").strip().lower(), []))


def is_support_library(canonical_name: str) -> bool:
    """True untuk pustaka pendukung yang BUKAN modul Python.

    Paket `chaquopy-*` hanya membungkus satu file .so; tidak ada apa pun yang
    bisa di-`import`. Menjalankan uji impor terhadapnya akan selalu gagal dan
    membatalkan seluruh transaksi — termasuk paket utama yang sebenarnya
    sudah berhasil.
    """
    return (canonical_name or "").strip().lower().startswith("chaquopy-")


def fetch_chaquopy_wheels(name: str, index_url: str = CHAQUOPY_INDEX_URL) -> list[dict]:
    """Ambil daftar file .whl dari simple index Chaquopy (PEP 503 HTML)."""
    url = index_url.rstrip("/") + "/" + name + "/"
    try:
        html = _http_get(url).decode("utf-8", "replace")
    except ResolveError as e:
        _propagate_cancel(e)
        if e.code == "SOURCE_NOT_FOUND":
            return []  # 404: package memang tidak dijual sumber ini
        raise  # transport gagal: jangan disamarkan menjadi package unavailable
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


# PROVIDED-PACKAGES (v1.0.19, riset shadowing stdlib 2026-08-17).
# Paket yang SUDAH dibawa APK secara permanen (app/build.gradle.kts pip{}).
# Bukti kelasnya: zope-interface deps 'setuptools' → resolver belanja
# setuptools 84.0.0 dari PyPI → smoke mati AssertionError distutils
# (stdlib-common.imy menshadow; log device 2026-08-17 01:37). Padahal
# setuptools 68.2.2 SUDAH terpasang sehat di runtime. Peta ini membuat
# resolver menganggap requirement terhadap paket-paket ini TERPENUHI oleh
# runtime — skip download+smoke — kecuali specifier menolak versi beku
# (→ vonis jujur, bukan pura-pura terpenuhi).
# SINKRON MANUAL dgn build.gradle.kts; dijaga guard test dua sisi.
RUNTIME_PROVIDED: dict[str, str] = {
    "pip": "23.3.1",
    "setuptools": "68.2.2",
    "wheel": "0.41.2",
    "packaging": "24.1",
}


def runtime_provided_version(canonical: str) -> str | None:
    """Versi beku runtime untuk paket provided; None bila bukan provided."""
    return RUNTIME_PROVIDED.get((canonical or "").strip().lower())


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


def _candidate_version(candidate: dict) -> str | None:
    """Versi wheel dari kandidat semua source; None untuk filename rusak."""
    try:
        return parse_wheel(candidate.get("filename", ""))["version"]
    except (WheelInfoError, KeyError, TypeError):
        return None


def _filter_candidates_by_specifier(
    candidates: list[dict], specifier: str
) -> list[dict]:
    """Terapkan satu kontrak versi ke local, PyPI, dan Chaquopy.

    Sebelum v1.0.19 hanya `_pypi_candidates` yang memfilter specifier. Wheel
    local/Chaquopy langsung masuk ranking, sehingga tested priority dapat
    memilih contourpy 1.0.5 untuk dependency `contourpy>=1.2` milik Bokeh
    3.9.2. Import dasar lolos, tetapi environment secara matematis salah.
    """
    if not specifier:
        return list(candidates)
    return [
        candidate for candidate in candidates
        if (version := _candidate_version(candidate)) is not None
        and _contains(specifier, version)
    ]


def _runtime_compatible_candidates(candidates: list[dict], supported_tags) -> list[dict]:
    """Kandidat yang tag Python/API/ABI-nya cocok dengan runtime target."""
    compatible = []
    for candidate in candidates:
        try:
            if wheel_compatible(
                candidate.get("filename", ""), supported_tags=supported_tags
            ):
                compatible.append(candidate)
        except WheelInfoError:
            continue
    return compatible


def _compatible_available_versions(candidates: list[dict], supported_tags) -> list[str]:
    """Versi unik yang benar-benar cocok runtime, untuk verdict user-facing."""
    versions = {
        version
        for candidate in _runtime_compatible_candidates(candidates, supported_tags)
        if (version := _candidate_version(candidate)) is not None
    }
    try:
        return [str(v) for v in sorted((Version(v) for v in versions), reverse=True)]
    except InvalidVersion:
        return sorted(versions, reverse=True)


def _local_wheel_candidates(wheels_dir: str, name: str) -> list[dict]:
    """Sumber 1: cache wheel lokal (offline reuse)."""
    import os
    out = []
    requested_name = canonicalize_name(name)
    if not wheels_dir or not os.path.isdir(wheels_dir):
        return out
    for fn in sorted(os.listdir(wheels_dir)):
        if not fn.endswith(".whl"):
            continue
        try:
            info = parse_wheel(fn)
        except Exception:
            continue
        # PEP 503 normalization treats runs of '-', '_' and '.' identically.
        # Never mix a hyphen canonical name with an underscore-normalized side.
        if canonicalize_name(info["name"]) != requested_name:
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

def _resolve_unlocked(
    requirement_text: str,
    supported_tags=None,
    wheels_dir: str | None = None,
    marker_env: dict | None = None,
    tested_versions: dict | None = None,
    max_depth: int = _MAX_DEPTH,
    max_packages: int = _MAX_PACKAGES,
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

    # PROVIDED-PACKAGES sebagai ROOT (user mengetik `setuptools` langsung).
    # Tanpa cabang ini plan pulang kosong tanpa penjelasan — UX buntu.
    # Kontrak `stdlib` DIPAKAI ULANG dengan sengaja: Kotlin (DependencyResolver
    # → PipScreen cabang BUG C) sudah menampilkan `reason` sebagai info ℹ️,
    # bukan error — persis perilaku yang diinginkan, nol perubahan Kotlin.
    _root_provided = runtime_provided_version(root_name)
    if _root_provided is not None:
        if not spec["specifier"] or _contains(spec["specifier"], _root_provided):
            return {
                "packages": [],
                "conflicts": [],
                "unavailable": [],
                "stdlib": [{
                    "name": spec["name"],
                    "canonical_name": root_name,
                    "reason": (
                        "'%s' sudah disediakan runtime ZCODE v%s (bawaan APK) "
                        "— tidak perlu dipasang. Langsung 'import %s'."
                    ) % (spec["name"], _root_provided, spec["name"]),
                }],
            }
        return {
            "packages": [],
            "conflicts": [],
            "unavailable": [{
                "name": spec["name"], "canonical_name": root_name,
                "parent": None,
                "reason": (
                    "Runtime ZCODE menyediakan %s v%s (bawaan APK, tidak bisa "
                    "diganti); requirement '%s' tidak terpenuhi. Memasang versi "
                    "lain memicu bentrok dengan runtime beku (kelas shadowing "
                    "stdlib — bukti: setuptools 84 AssertionError distutils)."
                ) % (root_name, _root_provided, requirement_text),
            }],
        }

    plan: dict[str, dict] = {}
    conflicts: list[dict] = []
    unavailable: list[dict] = []
    # Jejak keputusan resolver yang tidak terlihat dari daftar paket akhir.
    # Dipakai untuk mendiagnosis dari perangkat (user tidak punya logcat).
    notes: list[str] = []
    # Singgahan hanya berlaku untuk SATU resolusi. Menyimpannya lebih lama
    # berarti pemakai yang memasang paket lagi setengah jam kemudian bisa
    # mendapat daftar versi yang sudah basi.
    clear_metadata_cache()
    seen: set[str] = set()
    queued_names: set[str] = set()
    if max_packages < 1:
        raise ResolveError(
            "DEPENDENCY_LIMIT", "resolve",
            "Batas jumlah package resolver tidak valid.",
            "max_packages=%s" % max_packages,
        )
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
        if cname not in queued_names:
            if len(queued_names) >= max_packages:
                raise ResolveError(
                    "DEPENDENCY_LIMIT", "resolve",
                    "Dependency graph melebihi batas aman %d package." % max_packages,
                    "next=%s parent=%s depth=%d" % (cname, parent, depth),
                )
            queued_names.add(cname)
        seen.add(key)

        _check_cancelled()

        # PROVIDED-PACKAGES (v1.0.19): setuptools/wheel/pip/packaging sudah
        # dibawa APK. Membelanjakannya dari PyPI = kelas bug shadowing stdlib
        # (setuptools 84 mati AssertionError distutils; zope-interface ikut
        # tumbang — device 2026-08-17). Requirement terhadapnya dianggap
        # terpenuhi runtime, KECUALI specifier menolak versi beku → jujur.
        provided_v = runtime_provided_version(cname)
        if provided_v is not None:
            if not specifier or _contains(specifier, provided_v):
                notes.append(
                    "%s: disediakan runtime ZCODE v%s (bawaan APK) — "
                    "tidak diunduh ulang%s" % (
                        cname, provided_v,
                        " (diminta %s)" % parent if parent else "",
                    )
                )
                return
            # Specifier eksplisit menolak versi beku. Memasang versi lain
            # berisiko shadowing (bukti: setuptools 84). Vonis jujur.
            unavailable.append({
                "name": name, "canonical_name": cname, "parent": parent,
                "reason": (
                    "Runtime ZCODE menyediakan %s v%s (bawaan APK, tidak bisa "
                    "diganti); requirement '%s%s' tidak terpenuhi. Memasang "
                    "versi lain memicu bentrok dengan runtime beku "
                    "(kelas shadowing stdlib, lihat kartu setuptools)."
                ) % (cname, provided_v, cname, specifier),
            })
            _emit_progress(
                "package_unavailable",
                detail="provided v%s tak memenuhi %s" % (provided_v, specifier),
            )
            return

        # sudah direncanakan dengan versi lain → konflik. Package context hanya
        # mengurung collect/choose; recursion anak memasang context-nya sendiri.
        existing = plan.get(cname)
        package_token = _CURRENT_PACKAGE.set(cname)
        try:
            _emit_progress("package_begin", detail="depth=%d" % depth)
            candidates = _collect(cname, specifier)
            if not candidates:
                unavailable.append({
                    "name": name, "canonical_name": cname, "parent": parent,
                    "reason": "Tidak ada wheel kompatibel untuk runtime ZCODE ini.",
                })
                _emit_progress("package_unavailable", detail="tidak ada kandidat")
                return
            chosen = _choose(candidates, cname)
            _emit_progress(
                "package_chosen", source=chosen.get("source", ""),
                detail="%s==%s" % (cname, chosen.get("version", "?")),
            )
        finally:
            _CURRENT_PACKAGE.reset(package_token)
        _check_cancelled()
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

        # Pustaka pendukung native (chaquopy-openblas, chaquopy-libjpeg, ...).
        # Diantrikan LEBIH DULU daripada dependensi PyPI: tanpa file .so ini,
        # paket induknya terpasang tetapi gagal diimpor — kegagalan yang jauh
        # lebih membingungkan daripada gagal mengunduh.
        host_deps = native_host_deps(cname)
        if host_deps:
            # Jejak eksplisit: v1.0.8 gagal TANPA menyebut chaquopy-openblas
            # sama sekali, sehingga tidak mungkin dibedakan apakah peta ini
            # tidak terbaca, indeks tidak terjangkau, atau wheel-nya ditolak.
            # Catatan ini menjawabnya langsung dari perangkat.
            notes.append("host_deps %s -> %s" % (cname, ",".join(host_deps)))
        for host_dep in native_host_deps(cname):
            before = set(plan.keys())
            queue(host_dep, "", set(), cname, depth + 1)
            if host_dep not in plan and canonicalize_name(host_dep) not in plan:
                notes.append(
                    "host_dep GAGAL diambil: %s (indeks/tag menolak)" % host_dep
                )
            elif set(plan.keys()) != before:
                notes.append("host_dep OK: %s" % host_dep)

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
        source_errors: list[ResolveError] = []
        # 2. PyPI
        pypi = []
        try:
            data = fetch_pypi_metadata(cname)
            # Constraint difilter SATU KALI setelah semua source digabung.
            # Requires-Python tetap difilter di helper ini.
            pypi = _pypi_candidates(data, {"specifier": ""})
        except ResolveError as e:
            _propagate_cancel(e)
            if e.code != "SOURCE_NOT_FOUND":
                source_errors.append(e)
        # 3. Chaquopy index (native wheel)
        chaq = []
        try:
            chaq = fetch_chaquopy_wheels(cname)
        except ResolveError as e:
            _propagate_cancel(e)
            source_errors.append(e)
        all_cands = local + pypi + chaq
        runtime_cands = _runtime_compatible_candidates(all_cands, supported_tags)
        valid_cands = _filter_candidates_by_specifier(runtime_cands, specifier)
        if valid_cands:
            # Ranking hanya melihat wheel yang lolos ABI DAN constraint.
            # Source lain boleh gagal selama kandidat valid nyata sudah ada.
            return valid_cands

        # Tanpa kandidat valid, source yang gagal membuat verdict versi tidak
        # pasti: versi yang memenuhi bisa berada di repository yang tak terbaca.
        if source_errors:
            raise source_errors[-1]

        compatible_versions = _compatible_available_versions(
            all_cands, supported_tags
        )
        if specifier and compatible_versions:
            available = ", ".join(compatible_versions[:8])
            raise ResolveError(
                "DEPENDENCY_VERSION_UNAVAILABLE", "resolve",
                "%s membutuhkan versi %s, tetapi versi yang tersedia untuk "
                "runtime ZCODE ini: %s." % (cname, specifier, available),
                "package=%s required=%s available=%s" % (
                    cname, specifier, ",".join(compatible_versions)
                ),
            )

        # Ada wheel, tetapi tidak satu pun cocok tag Python/API/ABI: biarkan
        # `_choose` mempertahankan verdict COMPATIBILITY beserta ABI target.
        if all_cands and not runtime_cands:
            return all_cands

        # Tidak ada kandidat wheel sama sekali: queue menghasilkan unavailable.
        return valid_cands

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
        # Penanda untuk Kotlin: paket ini pustaka pendukung, bukan modul Python.
        # Uji impor terhadapnya akan selalu gagal (tidak ada yang bisa diimpor).
        best["support_library"] = is_support_library(cname)
        try:
            best["version"] = parse_wheel(best["filename"])["version"]
        except WheelInfoError:
            best["version"] = ""
        # DEPENDENSI — SUMBER BERLAPIS (v1.0.14 + BUG K 2026-08-13).
        #
        # Lapis 1: METADATA di dalam wheel yang sudah ada di singgahan lokal.
        # Ini sumber paling jujur DAN paling murah: wheel-nya memang harus
        # diunduh untuk dipasang, jadi membacanya nol panggilan jaringan.
        # Terverifikasi terhadap 79 wheel PyPI asli — cocok persis dengan
        # daftar yang benar-benar dipasang pip, nol selisih.
        #
        # Lapis 2 (BUG K): metadata PyPI PER-VERSI (`/pypi/<nama>/<versi>/json`).
        # Dipakai bila wheel belum ada di singgahan — persis kasus instal
        # PERTAMA (resolve berjalan SEBELUM download). Endpoint per-versi memberi
        # requires_dist yang BENAR untuk versi yang dipilih, tidak seperti
        # `info` pada `/pypi/<nama>/json` yang selalu milik rilis TERBARU.
        #
        # Lapis 3 (di bawah): metadata PyPI rilis terbaru, fallback terakhir.
        # Wheel indeks Chaquopy belum bisa diuji dari lingkungan pengembangan
        # (TLS ke chaquo.com ditutup), jadi lapis ini WAJIB tetap ada.
        best.setdefault("requires_dist", [])
        best.setdefault("deps_source", "")
        # Ambil info/version terbaru SEKALI (di-cache di _collect → 0 HTTP baru).
        # Dipakai untuk optimasi: hanya panggil per-versi bila versi terpilih ≠ terbaru.
        _latest_data = {}
        _latest_info = {}
        try:
            _latest_data = fetch_pypi_metadata(cname)
            _latest_info = _latest_data.get("info", {}) or {}
        except ResolveError as e:
            _propagate_cancel(e)
            _latest_data = {}
            _latest_info = {}
        if _latest_info.get("version"):
            best["latest_version"] = _latest_info["version"]
            # BUG S lapis-2 (2026-08-16): warning versi-fosil. Kasus gensim
            # 0.10.1 (2014) & hyperopt 0.3.0 (2013) — resolver mundur jauh ke
            # versi purba karena hanya itu yang ber-wheel ARMv7, user tidak
            # diberi tahu. Tidak memblokir instal; hanya jujur di log.
            try:
                from packaging.version import Version as _V
                _chosen_v = _V(best.get("version") or "0")
                _latest_v = _V(best["latest_version"])
                if (not _chosen_v.is_prerelease and not _latest_v.is_prerelease
                        and _latest_v.release and _chosen_v.release
                        and _latest_v.release[0] - _chosen_v.release[0] >= 2):
                    notes.append(
                        "PERINGATAN: %s terpasang v%s, terbaru v%s — versi jauh "
                        "tertinggal karena keterbatasan wheel ARMv7; API bisa "
                        "beda dari tutorial modern." % (
                            cname, best["version"], best["latest_version"])
                    )
            except Exception:  # noqa: BLE001 — warning tidak boleh fatal
                pass
        try:
            from .wheeldeps import deps_from_wheel
            berkas = best.get("local_path") or ""
            if not berkas and wheels_dir and best.get("filename"):
                calon = os.path.join(wheels_dir, best["filename"])
                if os.path.isfile(calon):
                    berkas = calon
            if berkas:
                dw = deps_from_wheel(berkas, env)
                if not dw.get("error"):
                    best["requires_dist"] = [
                        (r["name"] + " " + r["specifier"]).strip()
                        for r in dw.get("requires", [])
                    ]
                    best["deps_source"] = "wheel"
                    notes.append(
                        "deps %s dari METADATA wheel: %s" % (
                            cname,
                            ",".join(r["name"] for r in dw.get("requires", [])) or "(tidak ada)",
                        )
                    )
        except Exception:  # noqa: BLE001 — pembaca lokal tidak boleh fatal
            pass

        # BUG K — lapis 2: requires_dist per-versi bila wheel belum ada di cache.
        #
        # OPTIMASI (2026-08-13, mencegah timeout matplotlib v1.0.14 regresi):
        # HANYA panggil endpoint per-versi bila versi TERPILIH ≠ versi TERBARU.
        # Untuk paket yang memilih versi terbaru, `info.requires_dist` (dari
        # fetch_pypi_metadata yang sudah di-cache di _collect) sudah benar dan
        # TIDAK butuh HTTP tambahan. Ini memotong panggilan HTTP per-versi utk
        # matplotlib (banyak deps versi terbaru) yang dulu membuatnya timeout.
        if (not best.get("requires_dist") and best.get("version")
                and best.get("version") != best.get("latest_version")):
            pv_req = requires_dist_for_version(cname, best["version"])
            if pv_req:
                best["requires_dist"] = pv_req
                best["deps_source"] = "pypi-version"
                notes.append(
                    "deps %s dari PyPI per-versi %s: %s" % (
                        cname, best["version"],
                        ",".join(r.split(";")[0].strip() for r in pv_req),
                    )
                )

        try:
            # data full (riwayat rilis) — sudah di-cache, 0 HTTP baru.
            data = _latest_data if _latest_data else fetch_pypi_metadata(cname)
            releases = data.get("releases", {})
            files_for_version = releases.get(best["version"], [])
            if files_for_version:
                rp = files_for_version[0].get("requires_python")
                best["requires_python"] = rp
            info = data.get("info", {}) or _latest_info
            # HANYA dipakai bila lapis 1 & 2 tidak memberi apa pun (wheel belum
            # ada di cache DAN endpoint per-versi gagal/versi tak tersedia).
            # Jangan ditimpa oleh info rilis terbaru (Bug K): itu yang
            # menyebabkan pytz/typing-extensions hilang.
            if (not best.get("requires_dist")
                    and "requires_dist" in info and info["requires_dist"]):
                best["requires_dist"] = [
                    r for r in info["requires_dist"] if "extra ==" not in (r or "")
                ]
                best["deps_source"] = "pypi"
            best["summary"] = (info.get("summary") or "")[:200]
            best["license"] = (info.get("license") or "")[:200]
            best["project_url"] = info.get("project_url")
        except ResolveError as e:
            _propagate_cancel(e)
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
        "notes": notes,
    }


def resolve(*args, **kwargs):
    """Serialize resolver sessions which share the process metadata cache.

    PackageEngineV2 already enforces one operation, but this lock also protects
    direct/diagnostic callers. It is deliberately outside `_resolve_unlocked` so
    one session owns clear/fill/read of both metadata caches atomically.
    """
    with _RESOLVE_LOCK:
        return _resolve_unlocked(*args, **kwargs)


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
    progress_bridge=None,
) -> str:
    """Wrapper JSON-string untuk Kotlin + progress/cancel bridge opsional."""
    bridge_token = _CURRENT_BRIDGE.set(progress_bridge)
    package_token = _CURRENT_PACKAGE.set("")
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
    finally:
        # Wajib reset walau parse/error/cancel: callback operasi lama tidak boleh
        # menerima progress resolver berikutnya pada thread yang sama.
        _CURRENT_PACKAGE.reset(package_token)
        _CURRENT_BRIDGE.reset(bridge_token)


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
