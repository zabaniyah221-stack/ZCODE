"""
smoke — smoke test package (SPEC-001 §9).

Jenis test:
- IMPORT        : import module berhasil
- NATIVE_LOAD   : pindai .so di staging + verifikasi import (ekstensi termuat)
- BASIC_API     : exec snippet (mis. numpy.arange(3).size == 3)
- FILE_OUTPUT   : exec snippet yang menulis file (mis. matplotlib savefig PNG)
- OFFLINE_RESTART: simulasi restart: bersihkan sys.modules lalu import ulang
                  (test restart beneran = manual di device, lihat BASELINE_TESTING)

Smoke test dijalankan terhadap STAGING dir (belum aktivasi) — sys.path disuntik
sementara, lalu dipulihkan. Deterministic & time-bounded (thread join timeout;
catat TIMEOUT sebagai kegagalan).
"""
import ctypes
import importlib
import os
import struct
import sys
import threading
import time

# Smoke test berjalan di background thread (PyCall). Paket yang memanggil
# signal.signal() saat import (kelas pycurl 2026-08-17: modul `curl` bonus di
# wheel-nya SIGPIPE→SIG_IGN; uvicorn; dsb.) akan mati ValueError "main thread"
# tanpa shim ini. Lihat signalshim.py untuk desain + preseden Chaquopy/rpy2.
from . import signalshim

signalshim.install()

_IMPORT_TIMEOUT_S = 30


def _find_native_libs(staging_dir: str) -> list[str]:
    libs = []
    if not staging_dir or not os.path.isdir(staging_dir):
        return libs
    for root, _dirs, files in os.walk(staging_dir):
        for fn in files:
            if fn.endswith((".so", ".dylib")):
                libs.append(os.path.join(root, fn))
    return sorted(libs)


def diagnose_native(staging_dir: str, error_text: str) -> str:
    """Kumpulkan fakta yang membedakan tiga sebab kegagalan muat .so.

    KENAPA ADA (2026-08-13): pesan `ImportError: Importing the numpy
    C-extensions failed` IDENTIK untuk tiga sebab yang sangat berbeda:

      (a) file .so writable  -> Android W^X menolak memuatnya
      (b) dependensi hilang  -> mis. libc++_shared.so tidak ditemukan
      (c) ABI keliru         -> wheel arm64 di perangkat armv7

    Tanpa memisahkan ketiganya, perbaikan hanya tebakan. Fungsi ini memeriksa
    hal-hal yang bisa diperiksa dari dalam Python dan melaporkan apa adanya —
    termasuk saat tidak menemukan apa pun.
    """
    baris = []
    try:
        libs = _find_native_libs(staging_dir)
        baris.append("native .so: %d" % len(libs))
        for so in libs[:10]:
            try:
                st = os.stat(so)
                writable = bool(st.st_mode & 0o222)
                baris.append(
                    "  %s | %d bytes | writable=%s"
                    % (os.path.basename(so), st.st_size, writable)
                )
            except OSError as e:
                baris.append("  %s | stat gagal: %s" % (os.path.basename(so), e))
        low = (error_text or "").lower()
        if "not found" in low and ".so" in low:
            baris.append("DUGAAN: dependensi .so tidak ditemukan (sebab b)")
        elif "writable" in low:
            baris.append("DUGAAN: Android menolak .so writable (sebab a)")
        elif "wrong elf class" in low or "is 32-bit" in low or "is 64-bit" in low:
            baris.append("DUGAAN: ABI wheel tidak cocok dengan perangkat (sebab c)")
        else:
            baris.append("DUGAAN: belum dapat dipastikan dari teks error")
        try:
            import sysconfig
            baris.append("platform: %s" % sysconfig.get_platform())
        except Exception:  # noqa: BLE001
            pass
    except Exception as e:  # noqa: BLE001 — diagnostik tak boleh jadi sumber error baru
        baris.append("diagnose_native gagal: %s" % e)
    return "\n".join(baris)


def elf_needed(path: str) -> list[str]:
    """Baca daftar pustaka yang dibutuhkan sebuah file .so (DT_NEEDED di ELF).

    KENAPA MEMBACA SENDIRI, bukan memakai daftar hafalan: `libopenblas.so`
    ternyata membutuhkan `libgfortran.so.3`, dan kebutuhan itu TIDAK tercatat
    di meta.yaml chaquopy-openblas (bagian requirements.host-nya kosong).
    Satu-satunya sumber yang jujur adalah berkas .so itu sendiri.

    Format ELF dibaca langsung memakai `struct` — tidak ada pustaka pihak
    ketiga di runtime Chaquopy, dan menambah satu hanya untuk ini tidak
    sepadan. Mendukung ELF 32-bit (ARMv7) maupun 64-bit (ARM64).

    Selalu mengembalikan list; berkas rusak atau bukan ELF menghasilkan list
    kosong, tidak pernah melempar.
    """
    try:
        with open(path, "rb") as f:
            d = f.read()
    except OSError:
        return []
    if len(d) < 64 or d[:4] != b"\x7fELF":
        return []
    try:
        kelas = d[1]                      # 1 = 32-bit, 2 = 64-bit
        little = d[5] == 1
        en = "<" if little else ">"
        if kelas == 1:
            phoff = struct.unpack_from(en + "I", d, 28)[0]
            phentsize = struct.unpack_from(en + "H", d, 42)[0]
            phnum = struct.unpack_from(en + "H", d, 44)[0]
        else:
            phoff = struct.unpack_from(en + "Q", d, 32)[0]
            phentsize = struct.unpack_from(en + "H", d, 54)[0]
            phnum = struct.unpack_from(en + "H", d, 56)[0]

        segmen = []      # (tipe, offset_file, alamat_virtual, ukuran_file)
        for i in range(phnum):
            o = phoff + i * phentsize
            if o + phentsize > len(d):
                break
            if kelas == 1:
                tipe = struct.unpack_from(en + "I", d, o)[0]
                off = struct.unpack_from(en + "I", d, o + 4)[0]
                vaddr = struct.unpack_from(en + "I", d, o + 8)[0]
                fsz = struct.unpack_from(en + "I", d, o + 16)[0]
            else:
                tipe = struct.unpack_from(en + "I", d, o)[0]
                off = struct.unpack_from(en + "Q", d, o + 8)[0]
                vaddr = struct.unpack_from(en + "Q", d, o + 16)[0]
                fsz = struct.unpack_from(en + "Q", d, o + 32)[0]
            segmen.append((tipe, off, vaddr, fsz))

        dyn = next((s_ for s_ in segmen if s_[0] == 2), None)   # PT_DYNAMIC
        if not dyn:
            return []
        _t, dyn_off, _v, dyn_sz = dyn
        langkah = 8 if kelas == 1 else 16
        entri = []
        strtab_vaddr = None
        for pos in range(dyn_off, min(dyn_off + dyn_sz, len(d)), langkah):
            if kelas == 1:
                tag, val = struct.unpack_from(en + "iI", d, pos)
            else:
                tag, val = struct.unpack_from(en + "qQ", d, pos)
            if tag == 0:                    # DT_NULL — akhir tabel
                break
            if tag == 5:                    # DT_STRTAB
                strtab_vaddr = val
            elif tag == 1:                  # DT_NEEDED
                entri.append(val)
        if strtab_vaddr is None or not entri:
            return []

        # Alamat virtual -> offset berkas, lewat segmen PT_LOAD yang memuatnya.
        strtab_off = strtab_vaddr
        for tipe, off, vaddr, fsz in segmen:
            if tipe == 1 and vaddr <= strtab_vaddr < vaddr + fsz:
                strtab_off = strtab_vaddr - vaddr + off
                break

        hasil = []
        for offset_nama in entri:
            mulai = strtab_off + offset_nama
            akhir = d.find(b"\x00", mulai)
            if 0 < mulai < len(d) and akhir > mulai:
                hasil.append(d[mulai:akhir].decode("utf-8", "replace"))
        return hasil
    except Exception:  # noqa: BLE001 — pembaca diagnostik tidak boleh crash
        return []


def preload_native_libs(dirs: list[str] | None) -> tuple[int, list[str]]:
    """Muat lebih dulu setiap pustaka pendukung (lib*.so) ke dalam proses.

    KENAPA HARUS BEGINI (dibuktikan dengan eksperimen, 2026-08-13):
    `_multiarray_umath.so` milik numpy mencantumkan `libopenblas.so` pada
    entri NEEDED-nya. Ketika Python mengimpor modul itu, yang mencari
    `libopenblas.so` adalah *dynamic linker* sistem operasi — BUKAN Python.
    Linker sama sekali tidak melihat `sys.path`.

    Karena itu menyuntikkan direktori saudara ke `sys.path` (yang sudah
    dilakukan run_smoke) TIDAK menolong sedikit pun; sudah diuji dan tetap
    gagal dengan pesan yang sama. Yang berhasil adalah memuat pustaka
    pendukung lebih dulu: begitu ia berada di memori proses, linker
    menemukannya lewat SONAME tanpa perlu mencari di disk.

    BERLAPIS (perbaikan v1.0.10). Perangkat membuktikan rantainya lebih dari
    satu tingkat: numpy -> libopenblas.so -> libgfortran.so.3. Satu lintasan
    tidak cukup karena urutan berkas di disk acak — kalau libopenblas dicoba
    sebelum libgfortran termuat, ia gagal dan tidak pernah dicoba lagi.
    Karena itu pemuatan diulang sampai tidak ada kemajuan lagi (fixpoint):
    setiap ronde memuat apa yang bisa dimuat, dan ronde berikutnya mencoba
    lagi sisanya. Rantai sedalam apa pun selesai tanpa perlu tahu isinya.

    Hanya berkas berawalan `lib` yang dimuat. File .so milik modul Python
    (mis. `_multiarray_umath.so`) TIDAK boleh dimuat dengan cara ini — ia
    butuh diinisialisasi oleh mesin impor Python, bukan ctypes.

    Mengembalikan (jumlah_berhasil, catatan) dan TIDAK PERNAH melempar:
    kegagalan preload harus menjadi diagnosa, bukan crash baru.
    """
    catatan: list[str] = []
    if not dirs:
        return 0, catatan

    # Kumpulkan kandidat lebih dulu, supaya bisa dicoba berulang kali.
    kandidat: list[tuple[str, str]] = []      # (nama, path)
    terlihat: set[str] = set()
    for d in dirs:
        if not d or not os.path.isdir(d):
            continue
        for root, _dirs, files in os.walk(d):
            for fn in sorted(files):
                if not (fn.startswith("lib") and ".so" in fn):
                    continue
                if fn in terlihat:
                    continue
                terlihat.add(fn)
                kandidat.append((fn, os.path.join(root, fn)))

    if not kandidat:
        return 0, catatan

    dimuat = 0
    sisa = list(kandidat)
    galat_terakhir: dict[str, str] = {}
    # Batas ronde = jumlah kandidat: setiap ronde minimal satu berhasil, kalau
    # tidak perulangan berhenti sendiri. Jadi ini tidak akan berputar selamanya.
    for _ronde in range(len(kandidat) + 1):
        maju = []
        for nama, path in list(sisa):
            try:
                ctypes.CDLL(path)
                dimuat += 1
                maju.append(nama)
                sisa.remove((nama, path))
                galat_terakhir.pop(nama, None)
            except Exception as e:  # noqa: BLE001
                galat_terakhir[nama] = str(e)
        if maju:
            catatan.append("preload OK: %s" % ", ".join(maju))
        if not maju or not sisa:
            break

    for nama, _path in sisa:
        pesan = galat_terakhir.get(nama, "sebab tidak diketahui")
        catatan.append("preload gagal: %s (%s)" % (nama, pesan))
        # Sebutkan kebutuhan yang belum terpenuhi — inilah yang mengubah
        # "gagal entah kenapa" menjadi nama paket yang harus ditambahkan.
        butuh = elf_needed(_path)
        if butuh:
            catatan.append("  %s butuh: %s" % (nama, ", ".join(butuh)))

    return dimuat, catatan


def _trace_hint(e: BaseException, max_frames: int = 3) -> str:
    """Frame terakhir traceback sebagai petunjuk pelaku: ' | jejak: a.py:12 -> b.py:34'.

    Frame internal smoke.py sendiri dibuang (wrapper/_do_import selalu ada di
    atas dan tidak informatif). Best-effort: kegagalan membaca traceback tidak
    boleh menutupi error aslinya.
    """
    try:
        import traceback
        frames = traceback.extract_tb(e.__traceback__)
        pilih = [
            "%s:%d" % (os.path.basename(f.filename or "?"), f.lineno or 0)
            for f in frames
            if os.path.basename(f.filename or "") != os.path.basename(__file__)
        ][-max_frames:]
        return " | jejak: %s" % " -> ".join(pilih) if pilih else ""
    except Exception:
        return ""


def _run_with_timeout(fn, timeout_s: float) -> tuple[bool, str]:
    """Jalankan fn; batasi durasi. Tidak bisa membunuh thread, tapi UI tetap
    dibatasi waktunya (best-effort, didokumentasikan di SPEC-001)."""
    result = {}

    def wrapper():
        try:
            fn()
            result["ok"] = True
            result["err"] = None
        except Exception as e:  # noqa: BLE001
            result["ok"] = False
            # Sertakan jejak pemanggil (2026-08-17). Tanpa ini, error seperti
            # "ValueError: signal only works in main thread" (pycurl, device)
            # tidak menyebut SIAPA pemanggilnya — diagnosa jadi tebak-tebakan.
            # Format ringkas file:baris agar muat di breadcrumb (bukan
            # traceback penuh yang membanjiri log HP).
            result["err"] = "%s: %s%s" % (
                type(e).__name__, e, _trace_hint(e)
            )

    t = threading.Thread(target=wrapper, daemon=True)
    t.start()
    t.join(timeout_s)
    if t.is_alive():
        return False, "TIMEOUT setelah %ds" % int(timeout_s)
    return result.get("ok", False), result.get("err")


def _exec_snippet(code: str) -> None:
    g = {"__name__": "__main__", "__builtins__": __builtins__}
    exec(code, g)  # noqa: S102 — kode smoke test berasal dari manifest ZCODE (tepercaya)


def _do_import(target: str) -> None:
    importlib.import_module(target)


def _reimport(target: str) -> None:
    # OFFLINE_RESTART: buang modul dari sys.modules lalu import ulang
    for mod in [m for m in sys.modules if m == target or m.startswith(target + ".")]:
        sys.modules.pop(mod, None)
    importlib.invalidate_caches()
    importlib.import_module(target)


def scan_missing_libs(dirs: list[str] | None, api: int = 16) -> dict:
    """Pindai semua .so di `dirs`, cari kebutuhan yang belum terpenuhi.

    KENAPA ADA (2026-08-13). Sampai v1.0.10, pustaka pendukung yang hilang
    hanya ketahuan SETELAH impor gagal di perangkat pemakai — satu lapis per
    rilis. Fungsi ini memindahkan penemuan itu ke saat instalasi, sebelum
    apa pun diaktifkan, dan menemukan SEMUA lapis sekaligus.

    Caranya membaca DT_NEEDED setiap berkas .so (termasuk modul Python seperti
    `_multiarray_umath.so`, yang justru paling sering menautkan pustaka luar),
    lalu mengurangi apa yang sudah tersedia di direktori itu sendiri.

    Sisa yang belum terpenuhi diterjemahkan menjadi nama paket lewat
    package_runtime.nativemap. Pustaka sistem Android (libc, libm, liblog, ...)
    disaring lebih dulu — tanpa itu ZCODE akan mencoba mengunduh paket yang
    tidak pernah ada.

    TIDAK PERNAH melempar: kegagalan pemindaian harus melemahkan diagnosa,
    bukan membatalkan instalasi yang sebenarnya baik-baik saja.
    """
    hasil = {
        "packages": [], "unknown": [], "system": [],
        "satisfied": [], "sources": {}, "scanned": 0, "error": "",
    }
    try:
        from . import nativemap
    except Exception as e:  # noqa: BLE001
        hasil["error"] = "nativemap tidak tersedia: %s" % e
        return hasil

    try:
        tersedia: set[str] = set()
        semua_so: list[str] = []
        for d in (dirs or []):
            if not d or not os.path.isdir(d):
                continue
            for root, _dirs, files in os.walk(d):
                for fn in files:
                    if ".so" not in fn:
                        continue
                    semua_so.append(os.path.join(root, fn))
                    tersedia.add(fn)

        butuh: list[str] = []
        for so in semua_so:
            for n in elf_needed(so):
                if n not in butuh:
                    butuh.append(n)

        hasil["scanned"] = len(semua_so)
        rencana = nativemap.resolve_needed(butuh, tersedia, api)
        hasil.update(rencana)
    except Exception as e:  # noqa: BLE001 — pemindai tidak boleh jadi sumber crash
        hasil["error"] = str(e)
    return hasil


def scan_missing_libs_json(dirs_json: str, api: int = 16) -> str:
    """Pembungkus JSON untuk dipanggil dari Kotlin (PyCall.callJson)."""
    import json
    try:
        dirs = json.loads(dirs_json) if dirs_json else []
        if not isinstance(dirs, list):
            dirs = []
    except Exception:  # noqa: BLE001
        dirs = []
    try:
        return json.dumps(scan_missing_libs([str(d) for d in dirs], int(api or 16)))
    except Exception as e:  # noqa: BLE001
        return json.dumps({
            "packages": [], "unknown": [], "system": [], "satisfied": [],
            "sources": {}, "scanned": 0, "error": str(e),
        })


def run_smoke(
    import_name: str,
    staging_dir: str,
    tests: list[dict] | None,
    timeout_s: float = _IMPORT_TIMEOUT_S,
    sibling_dirs: list[str] | None = None,
) -> tuple[bool, list[dict], dict]:
    """
    Jalankan smoke test terhadap staging_dir.

    sibling_dirs: direktori staging paket LAIN dalam transaksi yang sama.

    FIX 2026-08-13 — BUG KELAS "dependensi tak terlihat saat smoke test".
    Versi lama hanya menyuntikkan `staging_dir` (SATU paket) ke sys.path.
    Untuk paket tanpa dependensi hal itu kebetulan berhasil, tetapi setiap
    paket yang punya dependensi runtime pasti gagal: `import requests` mencari
    urllib3 yang ada di folder saudaranya dan tidak terlihat, sehingga muncul
    ModuleNotFoundError lalu SELURUH transaksi di-rollback.

    Ini bukan kasus khusus requests. Dari 23 paket populer yang diperiksa,
    12 (52%) punya dependensi runtime wajib — flask 7, pandas 5, requests 4,
    httpx 4, rich 2, beautifulsoup4 2, dst. Semuanya mustahil dipasang selama
    smoke test tidak melihat saudaranya.

    Return (ok, results, native_info).
    """
    results: list[dict] = []
    native_info = {"native_libs": [], "note": ""}

    if not staging_dir or not os.path.isdir(staging_dir):
        return False, [{"test": "setup", "type": "SETUP", "ok": False,
                        "error": "Staging directory tidak ada."}], native_info

    native_info["native_libs"] = _find_native_libs(staging_dir)
    native_info["note"] = (
        "%d file .so ditemukan di staging." % len(native_info["native_libs"])
        if native_info["native_libs"]
        else "Package murni-Python (tanpa .so)."
    )

    old_path = list(sys.path)
    old_modules = set(sys.modules)
    # Paket yang diuji harus menang atas versi lama yang mungkin sudah aktif,
    # jadi ia disisipkan PALING DEPAN; saudara-saudaranya menyusul di belakang.
    for d in reversed(sibling_dirs or []):
        if d and os.path.isdir(d) and d != staging_dir and d not in sys.path:
            sys.path.insert(0, d)
    sys.path.insert(0, staging_dir)
    importlib.invalidate_caches()

    # NATIVE-LOADER: muat pustaka pendukung SEBELUM impor apa pun.
    # sys.path di atas hanya menolong Python menemukan modul .py — linker
    # sistem yang mencari libopenblas.so tidak melihatnya sama sekali.
    dimuat, catatan_preload = preload_native_libs(
        [staging_dir] + list(sibling_dirs or [])
    )
    native_info["preloaded"] = dimuat
    native_info["preload_dirs"] = len([d for d in ([staging_dir] + list(sibling_dirs or [])) if d])
    if catatan_preload:
        native_info["preload_log"] = catatan_preload[:40]
    else:
        # TIDAK ADA satu pun lib*.so yang terlihat. Ini fakta penting: berarti
        # pustaka pendukung memang tidak ada di direktori mana pun yang
        # dikirimkan — bukan gagal dimuat, tapi tidak pernah sampai.
        native_info["preload_log"] = [
            "tidak ada lib*.so di %d direktori yang diberikan" % native_info["preload_dirs"]
        ]

    # Pindai kebutuhan yang BELUM terpenuhi dan sebutkan nama paketnya.
    # Sebelum ini, satu-satunya cara mengetahui pustaka apa yang kurang adalah
    # membaca pesan dlopen setelah impor gagal — dan pesan itu hanya menyebut
    # SATU nama, yang pertama gagal. Pemindaian menyebut semuanya sekaligus.
    kurang = scan_missing_libs([staging_dir] + list(sibling_dirs or []))
    native_info["missing_libs"] = kurang
    if kurang.get("packages"):
        native_info["preload_log"].append(
            "pustaka kurang -> paket: %s" % ", ".join(
                "%s [%s]" % (p, kurang["sources"].get(p, "?"))
                for p in kurang["packages"]
            )
        )
    if kurang.get("unknown"):
        native_info["preload_log"].append(
            "pustaka TIDAK DIKENAL: %s" % ", ".join(kurang["unknown"])
        )
    # Pustaka yang dikenal tetapi memang tidak punya wheel (mis. libssl).
    # Tanpa baris ini pemakai hanya melihat impor gagal tanpa sebab.
    for nota in kurang.get("notes", [])[:5]:
        native_info["preload_log"].append(nota)

    try:
        test_list = tests or [{"name": "import", "type": "IMPORT", "target": import_name}]
        for t in test_list:
            kind = (t.get("type") or "IMPORT").upper()
            target = t.get("target") or import_name
            name = t.get("name") or ("%s:%s" % (kind, target))
            if kind == "IMPORT":
                ok, err = _run_with_timeout(lambda: _do_import(target), timeout_s)
                if not ok:
                    # Import native yang gagal = satu-satunya kesempatan memeriksa
                    # .so selagi staging masih ada (rollback akan menghapusnya).
                    native_info["diagnosis"] = diagnose_native(staging_dir, err or "")
                    err = "%s\n--- diagnosa native ---\n%s" % (
                        err, native_info["diagnosis"]
                    )
                results.append({"test": name, "type": kind, "ok": ok, "error": err})
            elif kind == "NATIVE_LOAD":
                # BUG V (2026-08-16): aturan lama "wajib ada .so di staging"
                # menggagalkan paket yang importnya SUKSES — coverage 7.15.4
                # (resolver memilih wheel py3-none-any = murni Python, jelas
                # tanpa .so) dan pyzbar 0.1.8 (.so-nya dimuat dari pustaka
                # pendukung, bukan staging paket ini) dibunuh padahal sehat.
                # Hakim sesungguhnya adalah IMPORT: kalau ekstensi native
                # benar-benar hilang, import pasti gagal dlopen. Ketiadaan
                # .so saat import sukses = informasi, bukan kegagalan.
                ok, err = _run_with_timeout(lambda: _do_import(target), timeout_s)
                libs = native_info["native_libs"]
                if ok and not libs:
                    native_info["note"] = (
                        native_info.get("note", "") +
                        " NATIVE_LOAD: import OK tanpa .so di staging "
                        "(wheel murni Python / .so dari pustaka pendukung)."
                    ).strip()
                results.append({"test": name, "type": kind, "ok": ok, "error": err})
            elif kind in ("BASIC_API", "FILE_OUTPUT"):
                code = t.get("code")
                if not code:
                    results.append({"test": name, "type": kind, "ok": False,
                                    "error": "Manifest tanpa field 'code'."})
                else:
                    ok, err = _run_with_timeout(lambda: _exec_snippet(code), timeout_s)
                    results.append({"test": name, "type": kind, "ok": ok, "error": err})
            elif kind == "OFFLINE_RESTART":
                ok, err = _run_with_timeout(lambda: _reimport(target), timeout_s)
                results.append({"test": name, "type": kind, "ok": ok, "error": err})
            else:
                results.append({"test": name, "type": kind, "ok": False,
                                "error": "Jenis smoke test tidak dikenal: %s" % kind})

            if not results[-1]["ok"]:
                return False, results, native_info
        # Jujur di hasil: bila selama smoke ada handler signal yang di-skip
        # oleh shim (kelas pycurl 2026-08-17), catat — bukan disembunyikan.
        if signalshim.skipped_registrations:
            native_info["note"] = (
                native_info.get("note", "") +
                " [signal] handler di-skip (background thread Android): %s."
                % ", ".join(sorted(set(signalshim.skipped_registrations)))
            ).strip()
            signalshim.skipped_registrations.clear()
        return True, results, native_info
    finally:
        # A pure-Python root package may import an already-active native
        # dependency (seaborn -> numpy/matplotlib, for example). Looking only
        # for .so files in the root staging directory misses that process
        # contamination. Capture the actual newly loaded extension modules
        # before sys.modules cleanup; the loader/C++ registry survives cleanup.
        loaded_native = []
        for mod_name in sorted(set(sys.modules) - old_modules):
            module = sys.modules.get(mod_name)
            module_file = str(getattr(module, "__file__", "") or "")
            if ".so" in os.path.basename(module_file):
                loaded_native.append("%s:%s" % (mod_name, module_file))
        native_info["loaded_native_modules"] = loaded_native

        sys.path[:] = old_path
        for mod in list(sys.modules):
            if mod not in old_modules:
                sys.modules.pop(mod, None)
        importlib.invalidate_caches()
        _ = time  # (diimpor untuk dokumentasi; tetap digunakan di atas)


def run_smoke_json(
    import_name: str,
    staging_dir: str,
    tests_json: str,
    timeout_s: float = _IMPORT_TIMEOUT_S,
    sibling_dirs_json: str | None = None,
) -> str:
    """Wrapper JSON-string untuk Kotlin (hasil dict → json.dumps).

    sibling_dirs_json: JSON array berisi direktori staging paket lain dalam
    transaksi yang sama. Opsional demi kompatibilitas pemanggil lama.
    """
    import json
    try:
        tests = json.loads(tests_json) if tests_json else None
        siblings = json.loads(sibling_dirs_json) if sibling_dirs_json else None
        if siblings is not None and not isinstance(siblings, list):
            siblings = None
        ok, results, native = run_smoke(import_name, staging_dir, tests, timeout_s, siblings)
        return json.dumps({
            "ok": ok,
            "results": results,
            "native_info": native,
        }, default=str)
    except Exception as e:  # noqa: BLE001
        return json.dumps({
            "ok": False,
            "results": [{"test": "setup", "type": "SETUP", "ok": False, "error": str(e)}],
            "native_info": {"native_libs": [], "note": "error"},
        })
