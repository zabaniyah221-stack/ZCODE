# Autocomplete Tour — semua tingkatan bantuan ketik di ZCODE 🎓
# v1.0.23: autocomplete sekarang memahami KODE kamu, bukan cuma kata.
# File ini bisa langsung di-Run, tapi yang utama: ikuti tur dengan
# MENGETIK di baris kosong yang disediakan di tiap perhentian.

import math

harga = 125000
diskon = 0.2
nama_toko = "Toko Python Nusantara"


def hitung_total(harga_awal, potongan):
    """Total belanja setelah diskon."""
    return harga_awal * (1 - potongan)


# ── TUR 1 · Nama dari kodemu sendiri (instan, tanpa install) ────────────
# Ketik di baris kosong bawah:   hit
# → ditawarkan `hitung_total` — hasil pemindaian definisi di file ini
#   (scope-aware). Coba juga:  nam → `nama_toko`  |  dis → `diskon`

harga_final = hitung_total(harga, diskon)


# ── TUR 2 · Keyword & builtin Python (instan) ───────────────────────────
# Ketik di baris kosong bawah:  ran → `range`  |  enu → `enumerate`
# Semua keyword & builtin siap bahkan di file kosong yang baru.

print("Total belanja di", nama_toko, "=", harga_final)
print("Akar 144 =", math.sqrt(144))


# ── TUR 3 · Isi modul asli (perlu pack Editor Intelligence) ─────────────
# Ketik di baris kosong bawah:  math.
# → daftar fungsi matematika hasil ANALISIS modul sungguhan, bukan
#   tebakan. Tanpa pack, Tur 1 & 2 tetap bekerja penuh — ini bonusnya.

# ── EKSPERIMEN: pilih `floor` dari daftar setelah `math.`, tambahkan
# angka (mis. math.floor(9.81)), lalu Run file ini lagi. 👷
