"""
modulename — cari nama modul Python SEBENARNYA dari isi wheel yang terpasang.

KENAPA MODUL INI ADA (2026-08-13, v1.0.13)
------------------------------------------
Nama paket di PyPI dan nama modul yang dipakai `import` SERING BERBEDA:

    pip install fonttools   ->  import fontTools     (huruf T besar)
    pip install pillow      ->  import PIL
    pip install pyyaml      ->  import yaml
    pip install scikit-image->  import skimage

Sampai v1.0.12, ZCODE menebaknya dari katalog bawaan (`packages.json`, 300
paket). Katalog itu ketinggalan untuk `fonttools`, sehingga instalasi
matplotlib GAGAL dan seluruh transaksinya di-rollback:

    ModuleNotFoundError: No module named 'fonttools'
    native .so: 0

Perhatikan `native .so: 0` — ini BUKAN kegagalan pustaka native. Paketnya
sebenarnya sudah terpasang dengan benar; hanya uji impornya yang salah alamat.

SEBERAPA LUAS MASALAHNYA
------------------------
Dari 300 paket di katalog ZCODE, 48 (16%) punya nama impor berbeda. Karena
setiap paket dalam rencana ikut diuji impor — termasuk dependensi yang tidak
diketik pemakai — peluang gagalnya menumpuk:

    3 dependensi  -> 41% kemungkinan minimal satu meleset
    7 dependensi  -> 71%   (matplotlib ada di sini)
    15 dependensi -> 93%

Jadi kegagalan matplotlib bukan kesialan, melainkan hasil yang hampir pasti.
Memperbesar katalog tidak menyembuhkan: paket berikutnya yang belum terdaftar
akan tumbang dengan cara yang sama. Ini pengulangan pelajaran yang sudah
ditulis sendiri di proyek ini untuk pustaka native:

    "peta dependensi statis SELALU ketinggalan"

PENDEKATANNYA
-------------
Sama seperti DT_NEEDED untuk file .so: baca kebenarannya dari artefak itu
sendiri. Setiap wheel membawa metadata namanya sendiri di `*.dist-info/`,
sesuai PEP 427:

    1. top_level.txt  — daftar modul tingkat atas, satu per baris
    2. RECORD         — daftar seluruh berkas; modul tingkat atas terlihat
                        dari pola `<nama>/__init__.py`
    3. nama paket     — jaring terakhir, `-` menjadi `_`

Diverifikasi terhadap paket sungguhan di lingkungan pengembangan:

    setuptools -> ['_distutils_hack','debian','pkg_resources','setuptools']
    pytest     -> ['_pytest','py','pytest']
    packaging  -> tidak punya top_level.txt, RECORD memberi ['packaging']
    pygments   -> tidak punya top_level.txt, RECORD memberi ['pygments']

Dua contoh pertama juga membuktikan satu paket bisa memuat BANYAK modul,
sementara katalog lama hanya menyimpan satu `importName` per paket — jadi
bukan hanya isinya yang kurang, model datanya memang tidak memadai.

BATAS KEJUJURAN
---------------
Modul ini dikembangkan tanpa akses jaringan ke PyPI (sandbox memblokirnya),
jadi pengujian memakai wheel tiruan yang dibuat sendiri mengikuti PEP 427
ditambah paket nyata yang kebetulan ada di lingkungan. Format dist-info sudah
baku sejak 2012, sehingga risikonya kecil — tetapi "kecil" bukan "nol".
"""

import csv
import os

# Berkas yang berada di tingkat atas tetapi BUKAN modul yang layak diuji impor.
# `tests` sering ikut terpasang oleh paket lama dan mengimpornya bisa
# menjalankan kode uji milik paket tersebut.
BUKAN_MODUL = {"tests", "test", "docs", "examples", "example", "benchmarks"}


def _bersih(nama: str) -> str:
    return (nama or "").strip().strip("/").replace("\\", "/")


def _layak(nama: str) -> bool:
    """Modul yang pantas dijadikan sasaran uji impor.

    Modul berawalan garis bawah (`_pytest`, `_distutils_hack`) sengaja
    dilewati: itu detail internal paket, bukan antarmuka publiknya. Kalau
    HANYA modul semacam itu yang tersedia, pemanggil masih bisa memakainya
    lewat daftar lengkap.
    """
    if not nama or nama.startswith("_") or nama.startswith("."):
        return False
    if nama in BUKAN_MODUL:
        return False
    # nama modul Python yang sah
    return nama.replace("_", "a").isalnum()


def _dari_top_level(dist_info: str) -> list[str]:
    berkas = os.path.join(dist_info, "top_level.txt")
    if not os.path.isfile(berkas):
        return []
    try:
        with open(berkas, "r", encoding="utf-8", errors="replace") as f:
            isi = [_bersih(b) for b in f]
    except OSError:
        return []
    return [b for b in isi if b]


def _dari_record(dist_info: str) -> list[str]:
    """Simpulkan modul tingkat atas dari daftar berkas di RECORD.

    Dipakai saat `top_level.txt` tidak ada — dan itu bukan kasus langka:
    `packaging` dan `pygments` versi terbaru sudah tidak menyertakannya,
    karena PEP 427 memang tidak mewajibkannya.
    """
    berkas = os.path.join(dist_info, "RECORD")
    if not os.path.isfile(berkas):
        return []
    paket: list[str] = []
    modul_tunggal: list[str] = []
    try:
        with open(berkas, "r", encoding="utf-8", errors="replace", newline="") as f:
            for baris in csv.reader(f):
                if not baris:
                    continue
                jalur = _bersih(baris[0])
                if not jalur or jalur.startswith(".."):
                    continue
                bagian = jalur.split("/")
                # paket: <nama>/__init__.py tepat satu tingkat
                if len(bagian) == 2 and bagian[1] == "__init__.py":
                    if bagian[0] not in paket:
                        paket.append(bagian[0])
                # modul tunggal: <nama>.py di akar, bukan di dalam dist-info
                elif len(bagian) == 1 and jalur.endswith(".py"):
                    n = jalur[:-3]
                    if n not in modul_tunggal:
                        modul_tunggal.append(n)
    except (OSError, csv.Error):
        return []
    # Paket didahulukan: kalau sebuah wheel memuat keduanya, direktori paket
    # hampir selalu antarmuka utamanya.
    return paket + modul_tunggal


def module_names(staging_dir: str, canonical_name: str = "") -> dict:
    """Nama modul yang bisa diimpor dari sebuah paket yang sudah diekstrak.

    Mengembalikan dict:
        names   : daftar modul layak-impor, sudah diurutkan prioritas
        all     : seluruh modul yang ditemukan (termasuk yang berawalan `_`)
        source  : dari mana jawabannya berasal — untuk pelaporan jujur
        error   : kosong bila tidak ada masalah

    TIDAK PERNAH melempar. Kegagalan membaca metadata harus menurunkan
    kualitas tebakan, bukan membatalkan instalasi yang sudah berjalan baik.
    """
    hasil = {"names": [], "all": [], "source": "", "error": ""}
    tebakan = (canonical_name or "").strip().replace("-", "_")
    try:
        if not staging_dir or not os.path.isdir(staging_dir):
            hasil["error"] = "direktori staging tidak ada"
            if tebakan:
                hasil["names"] = [tebakan]
                hasil["all"] = [tebakan]
                hasil["source"] = "nama paket (staging tidak terbaca)"
            return hasil

        # DITEMUKAN SAAT PENGUJIAN (2026-08-13): `setuptools` di lingkungan
        # ini memakai `.egg-info`, bukan `.dist-info`. Format lama itu masih
        # dipakai sebagian paket dan isinya memuat `top_level.txt` yang sama
        # bergunanya. Versi pertama fungsi ini hanya mencari `.dist-info`,
        # sehingga jatuh ke tebakan nama paket dan kehilangan `pkg_resources`.
        dist_infos = sorted(
            os.path.join(staging_dir, d)
            for d in os.listdir(staging_dir)
            if (d.endswith(".dist-info") or d.endswith(".egg-info"))
            and os.path.isdir(os.path.join(staging_dir, d))
        )

        kandidat: list[str] = []
        sumber = ""
        for di in dist_infos:
            kandidat = _dari_top_level(di)
            if kandidat:
                sumber = "top_level.txt"
                break
        if not kandidat:
            for di in dist_infos:
                kandidat = _dari_record(di)
                if kandidat:
                    sumber = "RECORD"
                    break
        if not kandidat and tebakan:
            kandidat = [tebakan]
            sumber = "nama paket (metadata tidak memuat nama modul)"

        hasil["all"] = list(kandidat)
        layak = [k for k in kandidat if _layak(k)]
        # Kalau semua modulnya internal (berawalan `_`), lebih baik memakai
        # yang ada daripada tidak menguji apa pun.
        hasil["names"] = layak or list(kandidat)
        hasil["source"] = sumber
    except Exception as e:  # noqa: BLE001 — pembaca metadata tidak boleh crash
        hasil["error"] = str(e)
        if not hasil["names"] and tebakan:
            hasil["names"] = [tebakan]
            hasil["all"] = [tebakan]
            hasil["source"] = "nama paket (pembacaan gagal)"
    return hasil


def module_names_json(staging_dir: str, canonical_name: str = "") -> str:
    """Pembungkus JSON untuk dipanggil dari Kotlin (PyCall.callJson)."""
    import json
    try:
        return json.dumps(module_names(staging_dir, canonical_name))
    except Exception as e:  # noqa: BLE001
        return json.dumps({
            "names": [], "all": [], "source": "", "error": str(e),
        })
