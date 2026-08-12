# 🔍 Laporan Kendala: Install Module gagal total

Tanggal: 2026-08-12 · Sumber: 7 log dari perangkat user (ARMv7)
Status: **DIAGNOSIS SELESAI — BELUM ADA PERBAIKAN DIKIRIM** (menunggu persetujuan)

---

## Ringkasan

7 log lu ternyata **bukan 7 masalah** — cuma **3 bug**, dan **satu bug tunggal
menjelaskan 6 dari 7 log**. Ketiganya sudah gw buktikan lewat eksekusi nyata
terhadap data PyPI, bukan tebakan.

| # | Bug | Log yang dijelaskan | Bukti |
|---|---|---|---|
| **A** | Filter versi Python membandingkan hal yang salah | 2,3,4,5,6,7 | dijalankan, hasil di bawah |
| **B** | Pemilih versi memilih yang **pertama**, bukan yang terbaru | 1 (`colorama==0.3.5`) | dijalankan |
| **C** | `optString` mengembalikan `""` bukan `null` | 1 (gagal Download) | analisis kode + sidik jari pesan |

**Kabar baik: `packaging` sudah beres.** Semua log ini berjalan **melewati** parser
requirement tanpa `ModuleNotFoundError`. Perbaikan build kemarin bekerja. Bug yang
sekarang ada di **lapisan berikutnya** — bug lama yang dulu tersembunyi di balik
bug `packaging`.

---

## BUG A — filter `requires_python` membandingkan versi PAKET, bukan versi Python

**Ini akar dari 6 dari 7 log lu.**

Di `resolve.py`:

```python
def _requires_python_ok(requires_python, version):
    return SpecifierSet(requires_python).contains(Version(version))
```

Argumen `version` yang dikirim adalah **versi paket** (`2.19.1` milik Pygments),
sedangkan `requires_python` adalah syarat untuk **versi Python** (`>=3.8`).

Jadi ZCODE menanyakan: *"apakah Pygments **2.19.1** memenuhi syarat `>=3.8`?"*
Jawabannya **tidak**, karena `2.19.1 < 3.8` sebagai perbandingan angka.

Yang seharusnya ditanya: *"apakah **Python 3.11** memenuhi `>=3.8`?"* → ya.

### Bukti (dijalankan sungguhan terhadap PyPI)

```
pygments   terbaru=2.20.0  wheel lolos filter: 16  -> ['2.0', '2.0.1', '2.0.2', '2.0rc1']
mdurl      terbaru=0.1.2   wheel lolos filter: 0   -> []
urllib3    terbaru=2.7.0   wheel lolos filter: 23  -> ['1.10', '1.10.1', ...]
colorama   terbaru=0.4.6   wheel lolos filter: 7   -> ['0.3.5', '0.3.6', ...]
```

Perhatikan polanya: **yang lolos hanya versi kuno** — versi yang nomornya kebetulan
lebih besar dari `3.8`/`3.7`, atau yang terbit sebelum PyPI mewajibkan
`requires_python`. Paket modern **selalu** tersaring habis.

Lalu digabung dengan batasan versi dari paket induk:

```
urllib3  (requests minta >=1.21.1,<3)  -> ['1.21.1', '1.22']   (lolos tipis)
pygments (rich minta >=2.13,<3)        -> KOSONG
mdurl    (rich minta ~=0.1)            -> KOSONG
```

`mdurl` **satu-satunya** rilis wheel-nya `0.1.2`, dan `0.1.2 < 0.1` gagal → nol
kandidat → `PACKAGE_NOT_AVAILABLE`. **Persis** pesan di log 3 dan 7 lu.

### Kenapa pesannya menyesatkan

Pesannya bilang *"Tidak ada wheel kompatibel untuk runtime ZCODE ini"* — seolah HP
lu yang kurang mampu. **Padahal `mdurl` dan `pygments` itu pure-Python
`py3-none-any`**, jalan di mana pun termasuk ARMv7. Yang salah kodenya sendiri,
bukan perangkat lu. Pesan error ini menuduh perangkat atas kesalahan ZCODE.

---

## BUG B — pemilih versi mengambil kandidat PERTAMA, bukan TERBARU

Log 1: ZCODE memilih `colorama==0.3.5` — rilis **2015**. Versi terbaru `0.4.6`.

Di `wheelinfo.py`:

```python
ranked.sort(key=lambda r: (r[0], r[1].get("filename", "")))
```

Pengurutan hanya memakai **prioritas** dan **nama file** — **versi tidak pernah
ikut diperhitungkan**. Semua wheel `colorama` berprioritas sama (3 =
universal-pure), jadi penentunya nama file secara alfabet:

```
colorama-0.3.5-py2.py3-none-any.whl   <- menang (alfabet "0.3.5" terkecil)
colorama-0.4.6-py2.py3-none-any.whl
```

Dan gw sudah cek: `0.3.5` memang kandidat **pertama** yang keluar dari API.

Perlu dicatat: sebagai perbandingan alfabet, `"0.10.0" < "0.9.0"` — jadi begitu ada
paket yang mencapai versi dua digit, urutannya makin ngawur. Perbandingan versi
**wajib** memakai `packaging.version.Version`, bukan string.

---

## BUG C — `optString` mengembalikan string kosong, bukan `null`

Ini yang menghentikan `colorama` **tepat di tahap Download** (log 1).

`DependencyResolver.kt:99`:

```kotlin
localPath = p.optString("local_path"),
```

Kalau paket berasal dari PyPI, key `local_path` **tidak ada** di JSON. Tapi
`JSONObject.optString()` mengembalikan **`""`**, bukan `null`. Lalu:

```kotlin
if (p.localPath != null) {          // "" != null  ->  TRUE
    val local = File(p.localPath)    // File("")
    local.copyTo(wheelFile)          // NoSuchFileException
```

Wheel dari PyPI **tidak pernah diunduh** — ZCODE malah mencoba menyalin file
bernama kosong.

### Sidik jari yang mengunci diagnosis ini

Pesan lu:

```
✕ engine: Kegagalan internal engine: : The source file doesn't exist.
```

Perhatikan **dua titik dua berurutan** (`: :`). Di situ seharusnya ada nama file.
Kosong karena nama filenya memang string kosong. Itu tanda tangan `File("")`.
`"The source file doesn't exist."` adalah teks persis dari `kotlin.io.copyTo`.

Bug yang sama juga mengenai `url` (baris 98) dan `sha256`. Untuk `sha256`
sudah ada penjagaan `isNull`, untuk `local_path` dan `url` **tidak ada**.

---

## Log 4 & 5: `math` dan `semantic` — sebagian ini BUKAN bug

- **`math`** — modul **bawaan Python**, tidak ada di PyPI, tidak perlu diinstall.
  Tebakan lu di catatan benar.
- **`semantic`** — memang tidak ada paket bernama itu (yang ada `semantic-version`).

Perilaku menolak sudah **benar**. Yang salah cuma **pesannya**: keduanya bilang
*"tidak ada wheel kompatibel untuk runtime ZCODE ini"*, seolah HP lu yang kurang.
Padahal yang benar:

- `math` → *"`math` sudah tersedia di Python, tidak perlu diinstall."*
- `semantic` → *"Paket `semantic` tidak ada di PyPI. Maksud lu `semantic-version`?"*

Ini gw hitung sebagai **bug UX**, bukan bug logika. ZCODE punya `stdlib.json` yang
sudah berisi daftar modul bawaan — tapi tidak pernah diperiksa sebelum resolve.

---

## Kenapa ini lolos dari 244 test

Jujur: **ini kelemahan gw, bukan kelemahan lu.**

Semua test yang ada memakai data buatan sendiri (`colorama-0.4.6-py3-none-any.whl`
dan sejenisnya) — data yang **sudah bersih**. Tidak ada satu pun test yang:

1. mengambil daftar versi **nyata** dari PyPI (46 versi `colorama`, bukan 2),
2. memastikan versi **terbaru** yang menang,
3. memastikan `local_path` yang hilang menghasilkan `null`.

Bug A dan B **cuma muncul kalau kandidatnya banyak dan versinya beragam** —
persis kondisi dunia nyata, persis yang tidak pernah gw uji. Kalau lu setuju
diperbaiki, tiga test guard di atas ikut gw pasang, dan gw verifikasi lewat
uji mutasi seperti biasa.

---

## Usulan perbaikan (belum dikerjakan — menunggu "gas")

| # | Perbaikan | Risiko |
|---|---|---|
| A | Bandingkan `requires_python` dengan **versi Python runtime** | Rendah — 3 baris |
| B | Urutkan pakai `Version(...)` menurun, versi terbaru menang | Rendah |
| C | Helper `optStringOrNull()` untuk `local_path` + `url` | Rendah |
| D | Cek `stdlib.json` sebelum resolve → pesan jujur untuk `math` | Rendah |
| E | Pesan "tidak kompatibel" wajib menyebut **alasan sebenarnya** | Rendah |

Keempat pertama murni logika, tidak menyentuh UI, tidak menyentuh jalur Run.
Keyakinan gw setelah perbaikan ini: **pure-Python (`colorama`, `rich`, `requests`)
terinstall ~90%.** Sisa 10% = kemungkinan lapisan berikutnya yang belum kelihatan
karena selama ini terhalang bug A/B/C — pola yang sama dengan bug `packaging`.

**numpy/matplotlib tetap belum jalan** — itu masalah tag Android, terpisah,
belum gw sentuh.
