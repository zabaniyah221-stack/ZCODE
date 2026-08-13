"""
wheeldeps — baca daftar dependensi dari METADATA di dalam wheel.

KENAPA MODUL INI ADA (2026-08-13, v1.0.14)
------------------------------------------
Log perangkat: instalasi pandas gagal dengan

    ImportError: Unable to import required dependencies: pytz
    host_deps pandas -> numpy          <- HANYA numpy

Padahal METADATA wheel pandas 2.1.3 mencantumkan empat dependensi wajib:
numpy, python-dateutil, pytz, dan tzdata. ZCODE hanya membawa satu karena
resolver memakai peta buatan tangan (`NATIVE_HOST_DEPS`), bukan daftar resmi
milik paket itu sendiri.

Yang membuktikan katalog bukan obatnya: `pytz` SUDAH terdaftar di katalog
bawaan ZCODE, tetapi tetap tidak ikut terpasang. Yang rusak bukan isi
katalog — melainkan tidak adanya pembaca daftar dependensi resmi.

SEBERAPA DALAM MASALAHNYA
-------------------------
Diuji terhadap daftar 279 paket populer (2026-08-13): menelusuri dependensi
mereka memunculkan 54 paket lain yang TIDAK ada di daftar, dan butuh tiga
putaran sampai tidak ada lagi yang kurang:

    putaran 1: 43 paket tambahan ditemukan, 10 masih kurang
    putaran 2: 53 paket tambahan ditemukan,  1 masih kurang
    putaran 3: 54 paket tambahan ditemukan,  0 masih kurang

Nama-nama seperti `argon2-cffi-bindings`, `catalogue`, `cymem`, `narwhals`,
`jsonschema-specifications` adalah dependensi-dari-dependensi yang mustahil
ditebak lebih dulu. Daftar apa pun yang ditulis tangan akan selalu kurang.

PENDEKATANNYA
-------------
Sama seperti DT_NEEDED untuk .so dan top_level.txt untuk nama modul: baca
kebenarannya dari artefaknya sendiri. Setiap wheel memuat `Requires-Dist` di
`*.dist-info/METADATA` sesuai PEP 566, dan wheel itu memang sudah diunduh
untuk dipasang — jadi membacanya TIDAK menambah satu pun panggilan jaringan.

DIVERIFIKASI (2026-08-13)
-------------------------
66 wheel asli diunduh dari PyPI, `Requires-Dist`-nya dibandingkan dengan
paket yang benar-benar dipasang `pip` (kebenaran):

    cocok persis: 82 | beda: 0

Termasuk spacy dengan 43 dependensi. Empat versi format METADATA (2.1, 2.3,
2.4, 2.5) semuanya terbaca dengan parser yang sama.

BATAS KEJUJURAN
---------------
Wheel dari indeks Chaquopy TIDAK bisa diunduh dari lingkungan pengembangan
ini (TLS ke chaquo.com ditutup; dicoba curl, wget, pip, urllib, openssl —
semuanya gagal). Jadi klaim bahwa wheel Chaquopy juga memuat `Requires-Dist`
lengkap bersandar pada pembacaan kode `build-wheel.py` milik Chaquopy, yang
memanggil `update_message_file(..., if_exist="keep")` — artinya ia menambah
field yang hilang tanpa menimpa metadata asli dari upstream. Keyakinan ~90%,
bukan 100%. Karena itu pemanggil WAJIB menyediakan jalur cadangan.

Sumber: https://github.com/chaquo/chaquopy/blob/master/server/pypi/build-wheel.py
"""

import os
import re
import zipfile

_REQ_LINE = re.compile(r"^Requires-Dist:\s*(.+)$", re.MULTILINE)
_NAME_LINE = re.compile(r"^Name:\s*(.+)$", re.MULTILINE)
_METADATA_VERSION = re.compile(r"^Metadata-Version:\s*(.+)$", re.MULTILINE)

# Nama paket yang tidak pernah perlu dipasang: sudah menjadi bagian runtime.
ABAIKAN = {"python", "pip", "setuptools", "wheel"}


def normalisasi(nama: str) -> str:
    """Bentuk kanonik nama paket (PEP 503)."""
    return re.sub(r"[-_.]+", "-", (nama or "").strip()).lower()


def _pisah_requirement(baris: str) -> tuple[str, str, str]:
    """Pecah satu baris Requires-Dist menjadi (nama, penanda_versi, marker).

    Ditulis tangan alih-alih memakai `packaging.requirements` karena runtime
    Chaquopy belum tentu menyertakan pustaka itu, dan kegagalan impor di sini
    berarti SELURUH resolusi dependensi mati.

    DITEMUKAN SAAT PENGUJIAN: wheel `tifffile` dibangun di Windows, sehingga
    setiap baris METADATA-nya berakhiran CR (`\\r`). Versi pertama parser ini
    tidak membuangnya, sehingga nama paketnya menjadi `numpy\\r` dan dianggap
    tidak valid — dependensi hilang tanpa suara.

    UJI MUTASI (2026-08-13) memperjelas bentuk perlindungannya: CR hanya
    benar-benar bocor bila `.strip()` di baris ini DAN `.strip()` pada nilai
    kembalian di bawah sama-sama dilucuti. Tiga kombinasi lainnya tetap aman
    karena `\\s*` di dalam regex ikut menyerap CR. Keduanya sengaja
    dipertahankan sebagai lapis berganda — jangan "dirapikan" salah satu.
    """
    b = (baris or "").strip()
    if not b:
        return "", "", ""
    marker = ""
    if ";" in b:
        b, marker = b.split(";", 1)
        b = b.strip()
        marker = marker.strip()
    # buang extras: "requests[socks] >=2.0" -> nama requests
    ekstra = ""
    if "[" in b and "]" in b:
        awal = b.index("[")
        akhir = b.index("]")
        ekstra = b[awal + 1:akhir]
        b = b[:awal] + b[akhir + 1:]
    m = re.match(r"^\s*([A-Za-z0-9][A-Za-z0-9._-]*)\s*(.*)$", b)
    if not m:
        return "", "", marker
    del ekstra
    return m.group(1).strip(), m.group(2).strip(), marker


def _marker_wajib(marker: str, env: dict | None = None) -> bool:
    """True bila dependensi ini WAJIB pada lingkungan ini.

    Dependensi yang hanya dibutuhkan oleh sebuah `extra` TIDAK wajib —
    METADATA pandas memuat 77 baris Requires-Dist tetapi hanya 6 yang wajib;
    sisanya milik extra seperti [test] atau [all]. Memasang semuanya berarti
    mengunduh puluhan megabita yang tidak pernah dipakai.

    Marker dievaluasi seadanya: yang tidak bisa dipastikan dianggap TIDAK
    wajib. Melewatkan dependensi opsional jauh lebih murah daripada memasang
    puluhan paket yang salah.
    """
    m = (marker or "").strip()
    if not m:
        return True
    if "extra" in m:
        return False
    e = dict(env or {})
    pv = str(e.get("python_version", "3.11"))
    sp = str(e.get("sys_platform", "linux"))
    os_name = str(e.get("os_name", "posix"))

    # Tolak yang jelas-jelas untuk platform lain.
    for pola, cocok in (
        (r'sys_platform\s*==\s*[\'"]([^\'"]+)[\'"]', sp),
        (r'os_name\s*==\s*[\'"]([^\'"]+)[\'"]', os_name),
    ):
        for nilai in re.findall(pola, m):
            if nilai != cocok:
                return False
    for pola, cocok in (
        (r'platform_system\s*==\s*[\'"]([^\'"]+)[\'"]', ("Android", "Linux")),
    ):
        for nilai in re.findall(pola, m):
            if nilai not in cocok:
                return False

    # python_version: hormati batas bawah/atas yang umum dipakai.
    try:
        pv_t = tuple(int(x) for x in pv.split(".")[:2])
        for op, nilai in re.findall(
            r'python_version\s*(<=|>=|<|>|==|!=)\s*[\'"]([^\'"]+)[\'"]', m
        ):
            t = tuple(int(x) for x in nilai.split(".")[:2])
            if op == "<" and not (pv_t < t):
                return False
            if op == "<=" and not (pv_t <= t):
                return False
            if op == ">" and not (pv_t > t):
                return False
            if op == ">=" and not (pv_t >= t):
                return False
            if op == "==" and not (pv_t == t):
                return False
            if op == "!=" and not (pv_t != t):
                return False
    except (TypeError, ValueError):
        pass
    return True


def _baca_metadata(teks: str, env: dict | None = None) -> dict:
    hasil = {
        "name": "",
        "metadata_version": "",
        "requires": [],
        "optional": [],
    }
    n = _NAME_LINE.search(teks)
    if n:
        hasil["name"] = n.group(1).strip()
    mv = _METADATA_VERSION.search(teks)
    if mv:
        hasil["metadata_version"] = mv.group(1).strip()
    for baris in _REQ_LINE.findall(teks):
        nama, spec, marker = _pisah_requirement(baris)
        if not nama:
            continue
        if normalisasi(nama) in ABAIKAN:
            continue
        entri = {"name": nama, "specifier": spec, "marker": marker}
        if _marker_wajib(marker, env):
            if not any(normalisasi(x["name"]) == normalisasi(nama)
                       for x in hasil["requires"]):
                hasil["requires"].append(entri)
        else:
            hasil["optional"].append(entri)
    return hasil


def deps_from_wheel(wheel_path: str, env: dict | None = None) -> dict:
    """Baca dependensi dari berkas .whl (belum diekstrak).

    TIDAK PERNAH melempar. Kegagalan membaca harus membuat pemanggil beralih
    ke jalur cadangan, bukan membatalkan instalasi.
    """
    hasil = {"name": "", "metadata_version": "", "requires": [],
             "optional": [], "source": "", "error": ""}
    try:
        if not wheel_path or not os.path.isfile(wheel_path):
            hasil["error"] = "berkas wheel tidak ada"
            return hasil
        with zipfile.ZipFile(wheel_path) as z:
            kandidat = [n for n in z.namelist()
                        if n.endswith(".dist-info/METADATA")]
            if not kandidat:
                hasil["error"] = "wheel tidak memuat dist-info/METADATA"
                return hasil
            teks = z.read(sorted(kandidat)[0]).decode("utf-8", "replace")
        hasil.update(_baca_metadata(teks, env))
        hasil["source"] = "METADATA wheel"
    except Exception as e:  # noqa: BLE001 — pembaca metadata tidak boleh crash
        hasil["error"] = str(e)
    return hasil


def deps_from_staging(staging_dir: str, env: dict | None = None) -> dict:
    """Versi untuk paket yang SUDAH diekstrak ke direktori staging."""
    hasil = {"name": "", "metadata_version": "", "requires": [],
             "optional": [], "source": "", "error": ""}
    try:
        if not staging_dir or not os.path.isdir(staging_dir):
            hasil["error"] = "direktori staging tidak ada"
            return hasil
        # `.egg-info` ikut diperiksa: sebagian paket lama (setuptools) memakai
        # format itu, dan ia memuat PKG-INFO dengan struktur yang sama.
        for d in sorted(os.listdir(staging_dir)):
            penuh = os.path.join(staging_dir, d)
            if not os.path.isdir(penuh):
                continue
            if not (d.endswith(".dist-info") or d.endswith(".egg-info")):
                continue
            for berkas in ("METADATA", "PKG-INFO"):
                jalur = os.path.join(penuh, berkas)
                if os.path.isfile(jalur):
                    with open(jalur, "r", encoding="utf-8", errors="replace") as f:
                        hasil.update(_baca_metadata(f.read(), env))
                    hasil["source"] = "%s (%s)" % (berkas, d)
                    return hasil
        hasil["error"] = "tidak ada dist-info/egg-info dengan METADATA"
    except Exception as e:  # noqa: BLE001
        hasil["error"] = str(e)
    return hasil


def deps_json(path: str, env_json: str = "") -> str:
    """Pembungkus JSON untuk Kotlin. Menerima berkas .whl ATAU direktori."""
    import json
    try:
        env = json.loads(env_json) if env_json else None
    except Exception:  # noqa: BLE001
        env = None
    try:
        if path and os.path.isdir(path):
            hasil = deps_from_staging(path, env)
        else:
            hasil = deps_from_wheel(path, env)
        # Kotlin hanya butuh nama; penanda versi disertakan terpisah.
        hasil["names"] = [r["name"] for r in hasil.get("requires", [])]
        return json.dumps(hasil)
    except Exception as e:  # noqa: BLE001
        return json.dumps({"names": [], "requires": [], "optional": [],
                           "source": "", "error": str(e)})
