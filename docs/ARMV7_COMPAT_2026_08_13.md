# 📱 Paket yang MASIH kompatibel di ARMv7 (armeabi-v7a) — ZCODE Python 3.11

Disurvei langsung dari indeks resmi Chaquopy `https://chaquo.com/pypi-13.1/`
pada **2026-08-13**. Bukan perkiraan — tiap baris berasal dari nama file wheel
yang benar-benar ada di server.

Runtime ZCODE: **Chaquopy 17.0.0 / CPython 3.11 / minSdk 26**.
Syarat sebuah wheel bisa dipakai HP ARMv7 user:

```
cp311  DAN  armeabi_v7a  DAN  android_<API> ≤ 26
```

---

## 1. Aturan besar yang menentukan segalanya

**Chaquopy berhenti membangun wheel 32-bit mulai Python 3.12.**
Semua paket native ber-tag `cp312`/`cp313` hanya tersedia untuk
`arm64_v8a` dan `x86_64`.

Konsekuensi buat ZCODE: **Python 3.11 adalah versi TERAKHIR yang masih
memberi ARMv7 akses ke wheel native.** Kalau suatu hari ZCODE naik ke 3.12+,
HP ARMv7 langsung kehilangan numpy, pandas, pillow — semuanya sekaligus.

Ini alasan kuat untuk **tetap di 3.11** selama ARMv7 masih didukung.

⚠️ Catatan penting: yang berhenti hanyalah wheel **native**. Paket
**pure-Python** (requests, rich, flask, …) tidak terpengaruh sama sekali —
mereka jalan di ABI apa pun.

---

## 2. ✅ HIJAU — ada cp311 + armeabi_v7a, API ≤ 26

Bisa dipasang di HP ARMv7 user **setelah** perbaikan pencocokan tag Android.

| Paket | Versi | Tag | Ukuran |
|---|---|---|---|
| **numpy** | 1.26.2 | `cp311-android_21_armeabi_v7a` | ~5 MB |
| **pandas** | 1.5.0, 2.1.3 | `cp311-android_21_armeabi_v7a` (1.5.0) / `android_24_armeabi_v7a` (2.1.3) | ~10 MB |
| **pillow** | 9.2.0, 11.0.0 | `cp311-android_21_armeabi_v7a` (9.2.0) / `android_24_armeabi_v7a` (11.0.0) | 481 KB |
| **matplotlib** | 3.6.0 | `cp311-android_21_armeabi_v7a` | ~7 MB |
| **lxml** | 5.3.0 | `cp311-android_24_armeabi_v7a` | 1.3 MB |
| **cryptography** | 42.0.8 | `cp311-android_24_armeabi_v7a` | 1.1 MB |
| **regex** | 2023.10.3 | `cp311-android_21_armeabi_v7a` | 273 KB |
| **markupsafe** | 3.0.3 | `cp311-android_24_armeabi_v7a` | 13 KB |
| **pyyaml** | 6.0.3 | `cp311-android_24_armeabi_v7a` | 132 KB |
| **psutil** | 7.1.3 | `cp311-android_24_armeabi_v7a` | 219 KB |

**Semua API ≤ 26**, jadi lolos di perangkat runtime user Android 14 / API 34.

Ini mengoreksi dua catatan lama ZCODE:
- `tested-manifest.json` menulis `numpy==1.26.4` → **tidak ada**. Yang ada `1.26.2`.
- Catatan lama menyebut pyyaml "tidak punya wheel" → **salah**, ada `cp311` ARMv7.

---

## 3. ⛔ MERAH — ada di indeks, tapi TIDAK untuk ARMv7 + cp311

| Paket | Kenapa gagal |
|---|---|
| **scipy** | berhenti di **cp310**; tidak ada cp311 sama sekali |
| **scikit-learn** | butuh scipy → ikut gugur |
| **opencv-python** | cp311 hanya 64-bit |
| **spacy / thinc / blis** | cp311 hanya 64-bit |
| **tensorflow / torch** | terakhir cp38, 2020–2021 |
| **grpcio / h5py / statsmodels** | tidak ada cp311 ARMv7 |

`scipy` sudah lama dilaporkan berhenti di Python 3.10
([chaquopy#1237](https://github.com/chaquo/chaquopy/issues/1237)) — survei ini
mengonfirmasinya. **Untuk HP ARMv7, scipy = tidak mungkin. Titik.**

---

## 4. 🟢 Pure-Python — jalan di ARMv7 tanpa syarat apa pun

Tidak berisi kode native, jadi ABI tidak relevan. Diambil dari PyPI biasa
(`py3-none-any`), bukan dari indeks Chaquopy.

requests · urllib3 · certifi · idna · charset-normalizer · colorama · rich ·
pygments · mdurl · markdown-it-py · tqdm · click · tabulate · python-dateutil ·
pytz · beautifulsoup4 · soupsieve · httpx · httpcore · h11 · anyio · sniffio ·
flask · jinja2 · werkzeug · itsdangerous · blinker · six · attrs · packaging ·
typing-extensions · pyparsing · cycler · openpyxl · et-xmlfile · chardet ·
sortedcontainers · toml · tomli · pyjwt · humanize · emoji · faker

**Ini bagian terbesar** dari yang dipakai sehari-hari, dan **satu-satunya yang
diblokir 4 bug resolver** — bukan diblokir keterbatasan ARMv7.

---

## 5. Kesimpulan jujur untuk HP ARMv7

**Kabar baik.** ARMv7 jauh lebih hidup dari dugaan sebelumnya. numpy, pandas,
matplotlib, pillow, lxml, cryptography, pyyaml — semuanya **ADA** untuk cp311
ARMv7. Catatan lama ZCODE yang bilang "ARMv7 dapat jauh lebih sedikit" **terlalu
pesimis** dan dikoreksi oleh dokumen ini.

**Kabar buruk.** scipy dan seluruh keluarga yang bergantung padanya
(scikit-learn, statsmodels) tidak akan pernah jalan di ARMv7 Python 3.11.
Ini batas ekosistem Chaquopy, bukan bug ZCODE — dan harus dikatakan apa adanya
di UI, bukan disembunyikan di balik "coba lagi nanti".

**Yang menghalangi sekarang bukan ARMv7.** Yang menghalangi adalah 4 bug di
resolver ZCODE sendiri. Bahkan paket pure-Python yang sepenuhnya bebas ABI pun
gagal dipasang. ARMv7 baru jadi faktor pembatas **setelah** keempat bug itu
diperbaiki.

---

## 6. Sumber

- https://chaquo.com/pypi-13.1/ — indeks resmi, diakses 2026-08-13
- Halaman per-paket: `/pillow/`, `/pyyaml/`, `/markupsafe/`, `/cryptography/`,
  `/lxml/`, `/regex/`, `/psutil/`, `/pandas/`, `/scipy/`
- https://github.com/chaquo/chaquopy/issues/1237 — scipy berhenti di 3.10
