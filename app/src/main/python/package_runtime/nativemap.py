"""
nativemap — peta "nama file .so" -> "nama paket yang menyediakannya".

KENAPA MODUL INI ADA (2026-08-13)
---------------------------------
Sampai v1.0.10, setiap pustaka pendukung yang hilang ditemukan satu per satu
dari log perangkat, lalu ditambal satu per satu:

    v1.0.8  numpy         butuh libopenblas.so    -> tambah chaquopy-openblas
    v1.0.9  libopenblas   butuh libgfortran.so.3  -> tambah chaquopy-libgfortran
    v1.0.10 _multiarray   butuh libc++_shared.so  -> ...dan seterusnya

Tiap putaran memakan satu siklus CI + satu pemasangan manual di HP. Cara itu
juga hanya menyembuhkan numpy: pemakai yang memasang `lxml`, `pillow`, atau
`h5py` akan menabrak dinding yang sama dengan nama pustaka berbeda.

Modul ini memutus pola tersebut. Peta ditulis LENGKAP sekali di sini —
mencakup SELURUH paket pendukung yang ada di indeks Chaquopy — lalu dipakai
selektif saat instalasi: hanya pustaka yang benar-benar diminta oleh berkas
.so (lewat DT_NEEDED) yang diunduh. Pemakai `pyyaml` tidak ikut mengunduh
openblas 3,8 MB.

SUMBER
------
Struktur dan dua tabel pertama disalin dari alat resmi yang membangun SEMUA
wheel di indeks Chaquopy:

    https://github.com/chaquo/chaquopy/blob/master/server/pypi/build-wheel.py
    (dibaca 2026-08-13)

Di sana, fungsi `check_requirements()` melakukan hal yang sama persis dengan
modul ini, hanya pada waktu yang berbeda: Chaquopy memeriksanya saat MEMBANGUN
wheel, ZCODE memeriksanya saat MEMASANG wheel. Algoritmanya:

    untuk setiap DT_NEEDED:
        ada di COMPILER_LIBS?   -> itu nama paketnya
        ada di available_libs?  -> sudah tersedia, lewati
        selain itu              -> pustaka tak dikenal, laporkan

BATAS KEJUJURAN MODUL INI
-------------------------
Hanya EMPAT baris peta di bawah yang terverifikasi dari sumber otoritatif atau
dari log perangkat sungguhan. Sisanya disusun dari nama paket di indeks
ditambah konvensi penamaan SONAME yang lazim, dan ditandai `DUGAAN`.

Sandbox tempat modul ini ditulis TIDAK punya akses jaringan ke chaquo.com
(curl gagal, exit 35), sehingga isi wheel tidak bisa dibongkar untuk
membuktikan nama .so-nya. Itu keterbatasan nyata dan tidak disembunyikan:
entri `DUGAAN` yang meleset akan muncul sebagai pesan "tidak tahu paketnya"
yang menyebut nama pustakanya — bukan kegagalan diam-diam.
"""

import re

# ---------------------------------------------------------------------------
# 1. PUSTAKA SISTEM ANDROID — JANGAN PERNAH DIUNDUH
# ---------------------------------------------------------------------------
# Disalin verbatim dari STANDARD_LIBS di build-wheel.py. Ini bagian paling
# rawan: tanpa penyaring ini, ZCODE akan melihat `libc.so` di DT_NEEDED lalu
# mencoba mengunduh `chaquopy-libc` yang tidak pernah ada — gagal dengan cara
# baru yang lebih membingungkan daripada masalah aslinya.
#
# Dikelompokkan per level API karena beberapa pustaka baru muncul di Android
# versi tertentu. Perangkat API 21 yang menemukan `libvulkan.so` (API 24)
# memang benar-benar tidak punya pustaka itu, dan berhak tahu.
STANDARD_LIBS: list[tuple[int, list[str]]] = [
    (16, [
        "libandroid.so", "libc.so", "libdl.so", "libEGL.so",
        "libGLESv1_CM.so", "libGLESv2.so", "libjnigraphics.so", "liblog.so",
        "libm.so", "libOpenMAXAL.so", "libOpenSLES.so", "libz.so",
    ]),
    (21, ["libmediandk.so"]),
    (24, ["libcamera2ndk.so", "libvulkan.so"]),
]

# Di Android, pthread dan librt MENYATU ke dalam libc — tidak ada berkas
# terpisah untuk keduanya. Wheel lama yang dibangun di Linux desktop masih
# mencantumkannya di DT_NEEDED. Chaquopy menanganinya dengan membuat pustaka
# kosong saat build (`create_dummy_libs`); di sisi runtime cukup diabaikan.
IMPLICIT_IN_LIBC: set[str] = {
    "libpthread.so", "librt.so", "libdl.so", "libutil.so", "libnsl.so",
    "libresolv.so", "libcrypt.so",
}

# Pustaka milik runtime Chaquopy sendiri — sudah ada di dalam APK, tidak boleh
# dicari di indeks. `libpython3.11.so` adalah interpreternya sendiri.
CHAQUOPY_BUILTIN: set[str] = {
    "libpython3.11.so", "libpython3.so", "libchaquopy_java.so",
}


def is_system_lib(soname: str, api: int = 16) -> bool:
    """True bila pustaka ini disediakan Android / Chaquopy, bukan oleh paket.

    `api` adalah level API perangkat. Pustaka yang baru ada di API lebih tinggi
    daripada perangkat TIDAK dianggap tersedia — perangkatnya memang tidak
    punya, dan menyebutnya "aman" akan menyembunyikan kegagalan yang nyata.
    """
    nama = (soname or "").strip().rsplit("/", 1)[-1]
    if not nama:
        return False
    # DITEMUKAN SAAT PENGUJIAN (2026-08-13): wheel yang dibangun di Linux
    # desktop mencantumkan `libc.so.6`, `libm.so.6`, `libdl.so.2` — bentuk
    # bersufiks versi milik glibc. Versi pertama fungsi ini hanya membandingkan
    # nama apa adanya, sehingga `libc.so.6` lolos sebagai "tidak dikenal" dan
    # ZCODE akan melaporkannya sebagai pustaka yang hilang. Karena itu bentuk
    # ternormalisasi ikut diperiksa.
    #
    # DITEMUKAN SAAT PENGUJIAN, JEBAKAN KEDUA: normalisasi penuh TIDAK BOLEH
    # dipakai di sini. Aturan pola nomor 2 memotong angka yang menempel, jadi
    # `libc10.so` milik PyTorch berubah menjadi `libc.so` dan akan dikira libc
    # sistem — pustaka nyata yang dibutuhkan justru diabaikan diam-diam.
    # Chaquopy memperingatkan tabrakan persis ini di build-wheel.py. Karena itu
    # di sini hanya sufiks versi (`.so.6`) yang dilucuti; angka yang menempel
    # pada nama dibiarkan utuh.
    kandidat = {nama, re.sub(r"^(lib.*)\.so\..*$", r"\1.so", nama)}
    if kandidat & IMPLICIT_IN_LIBC or kandidat & CHAQUOPY_BUILTIN:
        return True
    try:
        level = int(api)
    except (TypeError, ValueError):
        level = 16
    for sejak, daftar in STANDARD_LIBS:
        if level >= sejak and kandidat & set(daftar):
            return True
    return False


# ---------------------------------------------------------------------------
# 2. PETA PUSTAKA -> PAKET
# ---------------------------------------------------------------------------
# Nilai: (nama_paket, dasar_pengetahuan). Kolom kedua sengaja ada supaya
# perbedaan antara "terbukti" dan "diduga" tidak hilang begitu kode ini dibaca
# orang lain enam bulan lagi.
#
#   RESMI    = tertulis di build-wheel.py milik Chaquopy
#   PERANGKAT= terbaca dari log dlopen di HP sungguhan
#   DUGAAN   = disusun dari nama paket di indeks + konvensi SONAME
#
# TERVERIFIKASI (4 entri)
COMPILER_LIBS: dict[str, str] = {
    # Verbatim dari build-wheel.py. Catatan resmi di sana menjelaskan bahwa
    # chaquopy-libgfortran SENGAJA tidak dimasukkan ke tabel ini — paket yang
    # membutuhkannya harus menyebutnya manual di meta.yaml. Karena ZCODE tidak
    # membaca meta.yaml saat runtime, ia harus menambahkannya sendiri (di
    # tabel besar di bawah).
    "libc++_shared.so": "chaquopy-libcxx",
    "libomp.so": "chaquopy-libomp",
}

RESMI = "RESMI"
PERANGKAT = "PERANGKAT"
DUGAAN = "DUGAAN"

LIB_TO_PACKAGE: dict[str, tuple[str, str]] = {
    # --- terverifikasi ----------------------------------------------------
    "libc++_shared.so": ("chaquopy-libcxx", RESMI),
    "libomp.so": ("chaquopy-libomp", RESMI),
    "libgfortran.so": ("chaquopy-libgfortran", PERANGKAT),
    "libopenblas.so": ("chaquopy-openblas", PERANGKAT),

    # --- dugaan: nama paket == nama pustaka -------------------------------
    # Ke-16 paket berawalan `lib` di indeks. Risiko terkecil, karena nama
    # paketnya sendiri sudah berupa nama pustaka.
    "libffi.so": ("chaquopy-libffi", DUGAAN),
    "libiconv.so": ("chaquopy-libiconv", DUGAAN),
    "libcharset.so": ("chaquopy-libiconv", DUGAAN),
    "libjpeg.so": ("chaquopy-libjpeg", DUGAAN),
    "libogg.so": ("chaquopy-libogg", DUGAAN),
    "libpng.so": ("chaquopy-libpng", DUGAAN),
    "libraw.so": ("chaquopy-libraw", DUGAAN),
    "libraw_r.so": ("chaquopy-libraw", DUGAAN),
    "libsndfile.so": ("chaquopy-libsndfile", DUGAAN),
    "libtiff.so": ("chaquopy-libtiff", DUGAAN),
    "libtiffxx.so": ("chaquopy-libtiff", DUGAAN),
    "libvorbis.so": ("chaquopy-libvorbis", DUGAAN),
    "libvorbisenc.so": ("chaquopy-libvorbis", DUGAAN),
    "libvorbisfile.so": ("chaquopy-libvorbis", DUGAAN),
    "libyaml.so": ("chaquopy-libyaml", DUGAAN),
    "libzmq.so": ("chaquopy-libzmq", DUGAAN),

    # JEBAKAN YANG SUDAH MEMAKAN KORBAN SAAT PENGUJIAN:
    # aturan normalisasi resmi Chaquopy memotong angka di akhir nama, sehingga
    # `libxml2.so` menjadi `libxml.so` — padahal paketnya `chaquopy-libxml2`,
    # angka 2 itu bagian dari NAMA, bukan versi. Kedua ejaan didaftarkan
    # supaya normalisasi yang salah pun tetap mendarat di paket yang benar.
    "libxml2.so": ("chaquopy-libxml2", DUGAAN),
    "libxml.so": ("chaquopy-libxml2", DUGAAN),
    "libxslt.so": ("chaquopy-libxslt", DUGAAN),
    "libexslt.so": ("chaquopy-libxslt", DUGAAN),

    # --- dugaan: nama paket BERBEDA dari nama pustaka ---------------------
    # Ke-15 paket sisanya. Di sinilah tebakan berbasis pola pasti gagal, jadi
    # semuanya ditulis eksplisit.
    "libcrc32c.so": ("chaquopy-crc32c", DUGAAN),
    "libcurl.so": ("chaquopy-curl", DUGAAN),
    "libFLAC.so": ("chaquopy-flac", DUGAAN),
    "libfreetype.so": ("chaquopy-freetype", DUGAAN),
    "libgeos.so": ("chaquopy-geos", DUGAAN),
    "libgeos_c.so": ("chaquopy-geos", DUGAAN),
    "libhdf5.so": ("chaquopy-hdf5", DUGAAN),
    "libhdf5_hl.so": ("chaquopy-hdf5", DUGAAN),
    "libmp3lame.so": ("chaquopy-lame", DUGAAN),
    "libLLVM.so": ("chaquopy-llvm", DUGAAN),
    "libproj.so": ("chaquopy-proj", DUGAAN),
    "libsecp256k1.so": ("chaquopy-secp256k1", DUGAAN),
    "libta_lib.so": ("chaquopy-ta-lib", DUGAAN),
    "libta-lib.so": ("chaquopy-ta-lib", DUGAAN),
    "libzbar.so": ("chaquopy-zbar", DUGAAN),

    # openssl TIDAK berawalan `chaquopy-` di indeks — dibuktikan oleh
    # meta.yaml cryptography yang menulis `openssl` polos. Penyaring
    # startsWith("chaquopy-") di tempat lain tidak akan menangkapnya.
    "libssl.so": ("openssl", DUGAAN),
    "libcrypto.so": ("openssl", DUGAAN),
}


# ---------------------------------------------------------------------------
# 3. NORMALISASI NAMA
# ---------------------------------------------------------------------------
# Disalin dari SONAME_PATTERNS di build-wheel.py. Berkas nyata di dalam wheel
# bernama `libgfortran.so.3`, `libpng16.so`, `libyaml-0.so`, atau
# `libssl_chaquopy.so`; peta di atas memakai bentuk kanoniknya.
SONAME_PATTERNS: list[tuple[str, str]] = [
    (r"^(lib.*)\.so\..*$", r"\1.so"),                 # libgfortran.so.3
    (r"^(lib.*?)-?[\d.]+\.so$", r"\1.so"),            # libpng16.so, libyaml-0.so
    (r"^(lib.*)_(chaquopy|python)\.so$", r"\1.so"),   # libssl_chaquopy.so
]


def normalize_soname(soname: str) -> str:
    """Ubah nama berkas .so menjadi bentuk kanonik untuk pencarian di peta.

    PENTING: peta eksplisit selalu MENANG atas aturan pola ini. `libxml2.so`
    sudah terdaftar apa adanya, jadi ia tidak pernah sampai ke sini dan tidak
    pernah salah dipotong menjadi `libxml.so`. Pola hanya jaring terakhir
    untuk nama yang belum terdaftar.
    """
    nama = (soname or "").strip()
    if not nama:
        return ""
    nama = nama.rsplit("/", 1)[-1]
    for pola, ganti in SONAME_PATTERNS:
        baru = re.sub(pola, ganti, nama)
        if baru != nama:
            return baru
    return nama


def package_for_lib(soname: str) -> tuple[str, str] | None:
    """Paket penyedia pustaka ini, atau None bila tidak dikenal.

    Mengembalikan (nama_paket, dasar_pengetahuan) supaya pemanggil bisa
    membedakan entri terverifikasi dari dugaan saat melaporkannya.
    """
    nama = (soname or "").strip().rsplit("/", 1)[-1]
    if not nama:
        return None
    # 1. cocok persis — termasuk kasus libxml2.so yang tidak boleh dinormalisasi
    hit = LIB_TO_PACKAGE.get(nama)
    if hit:
        return hit
    # 2. cocok setelah dinormalisasi
    return LIB_TO_PACKAGE.get(normalize_soname(nama))


def resolve_needed(
    needed: list[str] | None,
    tersedia: set[str] | None = None,
    api: int = 16,
) -> dict:
    """Terjemahkan daftar DT_NEEDED menjadi rencana tindakan.

    Meniru `check_requirements()` milik Chaquopy, tetapi tidak pernah melempar:
    kegagalan di sini harus menjadi laporan yang bisa dibaca pemakai di HP,
    bukan crash.

    `tersedia` = nama berkas .so yang SUDAH ada (staging + paket terpasang).
    Pustaka yang sudah ada tidak perlu diunduh lagi.

    Mengembalikan:
        packages  : paket yang perlu diunduh, urut, tanpa duplikat
        unknown   : pustaka yang tidak dikenal siapa pun — HARUS dilaporkan
        system    : pustaka sistem yang diabaikan (untuk diagnostik)
        satisfied : pustaka yang sudah tersedia
        sources   : {paket: dasar_pengetahuan} untuk pelaporan jujur
    """
    hasil = {
        "packages": [],
        "unknown": [],
        "system": [],
        "satisfied": [],
        "sources": {},
    }
    if not needed:
        return hasil
    ada = set()
    for t in (tersedia or set()):
        nama_t = (t or "").strip().rsplit("/", 1)[-1]
        if nama_t:
            ada.add(nama_t)
            ada.add(normalize_soname(nama_t))

    for raw in needed:
        nama = (raw or "").strip().rsplit("/", 1)[-1]
        if not nama:
            continue
        if is_system_lib(nama, api):
            if nama not in hasil["system"]:
                hasil["system"].append(nama)
            continue
        if nama in ada or normalize_soname(nama) in ada:
            if nama not in hasil["satisfied"]:
                hasil["satisfied"].append(nama)
            continue
        cocok = package_for_lib(nama)
        if cocok is None:
            if nama not in hasil["unknown"]:
                hasil["unknown"].append(nama)
            continue
        paket, dasar = cocok
        if paket not in hasil["packages"]:
            hasil["packages"].append(paket)
            hasil["sources"][paket] = dasar
    return hasil


def jelaskan_tak_dikenal(unknown: list[str]) -> str:
    """Pesan untuk pemakai saat ada pustaka di luar peta.

    Peraturan proyek: jangan pernah gagal diam-diam. Pemakai yang membaca
    pesan ini bisa menyebutkan nama pustakanya, dan peta bisa dilengkapi
    tanpa menebak-nebak lebih dulu.
    """
    if not unknown:
        return ""
    return (
        "Paket ini butuh pustaka yang belum ada di peta ZCODE: %s. "
        "Pemasangan tetap dicoba, tetapi kemungkinan besar gagal saat impor. "
        "Laporkan nama pustaka di atas supaya bisa ditambahkan."
    ) % ", ".join(unknown)


def daftar_paket_pendukung() -> list[str]:
    """Semua paket pendukung yang dikenal — untuk uji dan diagnostik."""
    return sorted({paket for paket, _dasar in LIB_TO_PACKAGE.values()})
