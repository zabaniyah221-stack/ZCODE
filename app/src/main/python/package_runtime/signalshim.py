"""
signalshim — pelunak `signal.signal` untuk runtime Android (2026-08-17).

MASALAH (bukti device Infinix, breadcrumb 12:03): `import pycurl` mati dengan
`ValueError: signal only works in main thread of the main interpreter`.
Di ZCODE TIDAK ADA kode Python yang pernah jalan di main thread — main thread
Chaquopy = UI thread Android (blokir = ANR); smoke test, script user, dan REPL
semuanya background thread. Artinya SEMUA paket yang memanggil
`signal.signal()` di jalur import/pemakaian pasti meledak — pycurl hanya
korban pertama yang ketahuan.

POLA INI RESMI, BUKAN HACK. Chaquopy sendiri mem-patch stdlib demi kendala
Android di runtime-nya (java/android/__init__.py): os.get_exec_path,
ssl.SSLContext.set_default_verify_paths, os.get_terminal_size, bahkan seluruh
modul _multiprocessing diganti stub yang menunda error dari import ke
pemakaian. Shim ini melanjutkan filosofi yang sama untuk `signal`.

DESAIN CATCH-BASED (pelajaran rpy2 #769 + CPython issue 38904): JANGAN
memutuskan lewat `threading.current_thread() is main_thread()` — di runtime
embedded, modul `threading` bisa menganggap thread lain sebagai "main"
daripada modul `signal` (tergantung thread mana yang menjalankan
Py_Initialize vs import pertama). Satu-satunya hakim yang selalu benar adalah
percobaan nyata: panggil signal.signal asli, tangkap HANYA ValueError
"main thread", log + skip. ValueError lain (mis. nomor signal tak valid)
tetap dilempar apa adanya — shim tidak boleh menelan error sungguhan.

Efek bagi paket: perilaku sama seperti library dewasa di server thread
(uvicorn install_signal_handlers=False, playwright, rpy2): handler tidak
terpasang, program lanjut hidup. Di Android semantics signal terminal memang
tidak ada, jadi tidak ada fungsi nyata yang hilang.
"""
import signal
import sys

_ORIGINAL_SIGNAL = None  # terisi saat install(); None = shim belum terpasang

# Riwayat handler yang di-skip — dibaca smoke.py untuk catatan jujur di hasil
# test ("handler SIGPIPE di-skip"), dan berguna untuk Diagnostics.
skipped_registrations: list[str] = []


def _signal_name(signalnum) -> str:
    try:
        return signal.Signals(signalnum).name
    except Exception:
        return str(signalnum)


def _shimmed_signal(signalnum, handler):
    """Pengganti signal.signal: degradasi anggun di thread non-utama."""
    try:
        return _ORIGINAL_SIGNAL(signalnum, handler)
    except ValueError as e:
        # HANYA kasus "bukan main thread" yang dilunakkan. Pesan CPython:
        # "signal only works in main thread of the main interpreter".
        if "main thread" not in str(e):
            raise
        nama = _signal_name(signalnum)
        skipped_registrations.append(nama)
        print(
            "[signal] handler %s di-skip: kode Python ZCODE berjalan di "
            "background thread Android (kendala platform, bukan bug paket)."
            % nama,
            file=sys.stderr,
        )
        # Kontrak signal.signal = kembalikan handler lama; getsignal aman
        # dipanggil dari thread mana pun.
        return signal.getsignal(signalnum)


def install() -> None:
    """Pasang shim. Idempoten — aman dipanggil dari banyak gerbang.

    Anti-lapis-ganda memakai PENANDA pada fungsi yang terpasang, bukan hanya
    variabel modul: `importlib.reload` (dipakai OFFLINE_RESTART smoke) membuat
    module dict dieksekusi ulang sehingga `_ORIGINAL_SIGNAL` kembali None
    padahal shim lama masih terpasang di signal.signal — tanpa penanda,
    install() kedua menangkap shim lama sebagai "asli" → rekursi tak
    berujung (ditemukan lewat guard test sebelum sampai ke device).
    """
    global _ORIGINAL_SIGNAL
    terpasang = signal.signal
    if getattr(terpasang, "__zcode_signalshim__", False):
        if terpasang is _shimmed_signal:
            return  # shim instance ini sudah aktif — tidak ada yang perlu diubah
        # Shim instance LAMA (pra-reload) masih terpasang: ambil fungsi asli
        # dari penandanya, lalu GANTI dengan instance ini supaya riwayat
        # skipped_registrations tercatat di modul yang hidup.
        _ORIGINAL_SIGNAL = getattr(terpasang, "__zcode_original__", None)
    else:
        _ORIGINAL_SIGNAL = terpasang
    _shimmed_signal.__zcode_signalshim__ = True
    _shimmed_signal.__zcode_original__ = _ORIGINAL_SIGNAL
    signal.signal = _shimmed_signal
