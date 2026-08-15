# 📚 LIBRARY — Kurasi Konten Kartu Detail (gelombang 1: 11 TESTED)

Hasil riset multi-sumber 2026-08-15 untuk Batch B (②). Template final
(keputusan user): 6 seksi 5W1H, glyph polos ✓/∆/✗ (bukan emoji — konsisten
RENCANA_UPDATE_2026_08 "ikon polos"), sumber inline TAP-ABLE (`↗` →
Intent.ACTION_VIEW), baris `· dikurasi <tanggal>` di bawah. Bahasa:
WHAT/WHY/HOW boleh English (dari sumber resmi), WHERE selalu Indonesia
(milik kita), sumber lokal Indonesia masuk sebagai "Belajar (ID)".

Status kejujuran per klaim WHERE:
- DEVICE VERIFIED = breadcrumb HP Infinix ARMv7 (2026-08-14/15).
- TESTED (manifest) = versi di tested-manifest.json, diuji saat kurasi katalog.
- Klaim lain ditandai sumbernya.

---

## 1. requests — ✓ Tested

**WHAT IS IT** — Python HTTP for Humans. An elegant and simple HTTP library
for Python. [pypi.org/project/requests ↗]

**WHY USE IT** — Standar de-facto HTTP di Python: sintaks paling sederhana
untuk GET/POST, session, header, JSON. Hampir semua tutorial API memakai ini.
[requests.readthedocs.io ↗]

**HOW TO USE**
```python
import requests
r = requests.get("https://api.github.com/repos/python/cpython")
data = r.json()
print(r.status_code, "-", data["description"])
```
[adaptasi quickstart requests.readthedocs.io, jalan di ZCODE]

**WHERE IT RUNS (ZCODE · ARMv7)**
- ✓ Pure Python — langsung jalan, instan
- ✓ GET/POST/JSON/session penuh
- ∆ Butuh izin internet (jelas); timeout default None — selalu pasang
  `timeout=` di HP agar tidak menggantung di jaringan lambat

**WHO MADE IT** — Kenneth Reitz; kini dikelola PSF. Apache-2.0.

**SOURCES** — pypi.org/project/requests · requests.readthedocs.io ·
github.com/psf/requests · dikurasi 2026-08-15

---

## 2. numpy — ✓ Tested · v1.26.2

**WHAT IS IT** — Fundamental package for array computing in Python.
[pypi.org/project/numpy ↗]

**WHY USE IT** — The fundamental building block for scientific computing:
N-dimensional array, vectorized math jauh lebih cepat dari loop Python,
fondasi pandas/matplotlib. [numpy.org ↗]

**HOW TO USE**
```python
import numpy as np
nilai = np.array([72, 85, 60, 91, 77])
print("rata-rata:", np.mean(nilai))
print("di atas 75:", nilai[nilai > 75])
```
[adaptasi numpy.org/doc quickstart; sample bawaan: SAMPLES → Numpy]

**WHERE IT RUNS (ZCODE · ARMv7)**
- ✓ DEVICE VERIFIED 2026-08-14: install 1.26.2 + openblas/libgfortran/
  libcxx, run numpy_stats.py exit 0
- ∆ Versi terkunci 1.26.2 (wheel ARMv7 Chaquopy) — bukan versi PyPI terbaru
- ∆ Download pertama ±15-20MB (wheel + pustaka native)
- ✗ Python 3.12+ features numpy terbaru tidak tersedia di runtime 3.11

**WHO MADE IT** — Travis Oliphant et al.; komunitas NumPy/NumFOCUS.
BSD-3-Clause.

**SOURCES** — pypi.org/project/numpy · numpy.org/doc ·
Belajar (ID): petanikode.com/python-numpy · uji ZCODE 2026-08-14 ·
dikurasi 2026-08-15

---

## 3. pandas — ✓ Tested · v2.1.3

**WHAT IS IT** — Powerful data structures for data analysis, time series,
and statistics. pandas aims to be the fundamental high-level building block
for doing practical, real world data analysis in Python.
[pypi.org/project/pandas ↗ · pandas.pydata.org/about ↗]

**WHY USE IT** — A fast and efficient DataFrame object; tools for reading
and writing data between in-memory structures and CSV, Excel, SQL. Used in
Finance, Neuroscience, Economics, Statistics, Web Analytics.
[pandas.pydata.org/about — Library Highlights ↗]

**HOW TO USE**
```python
import pandas as pd
df = pd.DataFrame({"nama": ["Andi", "Budi", "Citra"],
                   "nilai": [85, 72, 91]})
print(df.describe())
print(df[df["nilai"] >= 80])
```
[adaptasi "10 Minutes to pandas", jalan di ZCODE]

**WHERE IT RUNS (ZCODE · ARMv7)**
- ✓ DEVICE VERIFIED 2026-08-14: install 2.1.3 + 8 deps (pytz/tzdata/
  dateutil ikut benar — Bug K fixed), total ±25MB
- ∆ RAM: dataset besar (>50rb baris) berat di HP 3GB
- ∆ Versi terkunci 2.1.3 / 1.5.0 (wheel ARMv7 Chaquopy)
- ∆ df.plot() butuh matplotlib terpasang terpisah

**WHO MADE IT** — Dimulai di AQR Capital Management (2008), open source
2009, kini proyek NumFOCUS. BSD-3-Clause.

**SOURCES** — pypi.org/project/pandas · pandas.pydata.org/about ·
pandas.pydata.org/docs · Belajar (ID): belajarpython.com/tutorial/
data-analytics-pandas-numpy-python · uji ZCODE 2026-08-14 ·
dikurasi 2026-08-15

---

## 4. matplotlib — ✓ Tested · v3.6.0

**WHAT IS IT** — Matplotlib is a comprehensive library for creating static,
animated, and interactive visualizations in Python.
[pypi.org/project/matplotlib ↗]

**WHY USE IT** — Standar visualisasi Python: line/bar/scatter/histogram,
kontrol penuh atas setiap elemen plot; fondasi seaborn dkk.
[matplotlib.org ↗]

**HOW TO USE**
```python
import matplotlib
matplotlib.use("Agg")          # WAJIB di ZCODE (tanpa layar GUI)
import matplotlib.pyplot as plt
plt.plot([1, 2, 3, 4], [1, 4, 9, 16])
plt.title("Kuadrat")
plt.savefig("plot.png")        # hasil: file PNG di workspace
print("tersimpan: plot.png")
```
[adaptasi matplotlib.org quickstart + pola Agg dari katalog ZCODE]

**WHERE IT RUNS (ZCODE · ARMv7)**
- ✓ DEVICE VERIFIED 2026-08-14: install 3.6.0 + 16 deps (freetype/libpng/
  kiwisolver dll)
- ✓ Backend Agg + savefig PNG (smoke test: matplotlib_agg_png)
- ✗ Window GUI interaktif / plt.show() — tidak ada backend Tk/Qt di
  arsitektur Chaquopy
- ∆ Storage ±20MB terinstall; RAM agak tinggi saat render

**WHO MADE IT** — John D. Hunter (2003); kini tim Matplotlib/NumFOCUS.
Lisensi PSF-based.

**SOURCES** — pypi.org/project/matplotlib · matplotlib.org ·
uji ZCODE 2026-08-14 · dikurasi 2026-08-15

---

## 5. rich — ✓ Tested

**WHAT IS IT** — Render rich text, tables, progress bars, syntax
highlighting, markdown and more to the terminal.
[pypi.org/project/rich ↗]

**WHY USE IT** — Output terminal jadi hidup dengan satu-dua baris: tabel
rapi, warna, panel, markdown — cocok dengan terminal ANSI ZCODE.
[rich.readthedocs.io ↗]

**HOW TO USE**
```python
from rich.console import Console
from rich.table import Table
c = Console()
t = Table(title="Nilai")
t.add_column("Nama"); t.add_column("Skor")
t.add_row("Andi", "85"); t.add_row("Citra", "91")
c.print(t)
```
[adaptasi rich.readthedocs.io quickstart]

**WHERE IT RUNS (ZCODE · ARMv7)**
- ✓ Pure Python; render warna via ANSI — terminal ZCODE mendukung
  (AnsiLineCache)
- ∆ Versi teruji manifest 13.5.3: typing-extensions ikut ter-resolve benar
  (kasus Bug K)
- ∆ Lebar terminal HP sempit — tabel lebar akan terpotong/wrap

**WHO MADE IT** — Will McGugan (Textualize). MIT.

**SOURCES** — pypi.org/project/rich · rich.readthedocs.io ·
github.com/Textualize/rich · dikurasi 2026-08-15

---

## 6. tqdm — ✓ Tested

**WHAT IS IT** — Fast, Extensible Progress Meter. Instantly make your loops
show a smart progress meter. [pypi.org/project/tqdm ↗]

**WHY USE IT** — Bungkus iterable apa pun dengan `tqdm(...)` dan dapatkan
progress bar; overhead sangat kecil. [tqdm.github.io ↗]

**HOW TO USE**
```python
from tqdm import tqdm
import time
total = 0
for i in tqdm(range(50), desc="menghitung"):
    total += i
    time.sleep(0.05)
print("selesai:", total)
```
[adaptasi tqdm.github.io]

**WHERE IT RUNS (ZCODE · ARMv7)**
- ✓ Pure Python, ringan
- ∆ Bar memakai carriage-return; di terminal ZCODE tampil sebagai baris
  yang diperbarui — kecepatan render dibatasi terminal HP

**WHO MADE IT** — Casper da Costa-Luis & kontributor. MPL-2.0 + MIT.

**SOURCES** — pypi.org/project/tqdm · tqdm.github.io ·
github.com/tqdm/tqdm · dikurasi 2026-08-15

---

## 7. flask — ✓ Tested

**WHAT IS IT** — A simple framework for building complex web applications.
[pypi.org/project/flask ↗]

**WHY USE IT** — Micro-framework web paling populer: route + fungsi =
API/web server dalam belasan baris; ekosistem extension luas.
[flask.palletsprojects.com ↗]

**HOW TO USE**
```python
from flask import Flask, jsonify
app = Flask(__name__)

@app.route("/")
def home():
    return jsonify(pesan="Halo dari ZCODE!")

app.run(host="127.0.0.1", port=5000)
# buka browser HP: http://127.0.0.1:5000
```
[adaptasi flask.palletsprojects.com quickstart]

**WHERE IT RUNS (ZCODE · ARMv7)**
- ✓ Pure Python (+ werkzeug/jinja2); server berjalan di dalam proses ZCODE
- ∆ Selama server hidup, script "berjalan terus" — hentikan via tombol stop
- ∆ Hanya terjangkau dari HP sendiri (localhost); akses dari perangkat lain
  perlu jaringan lokal + host 0.0.0.0
- ∆ debug=True reloader tidak cocok di lingkungan Chaquopy — biarkan mati

**WHO MADE IT** — Armin Ronacher / Pallets Projects. BSD-3-Clause.

**SOURCES** — pypi.org/project/flask · flask.palletsprojects.com ·
dikurasi 2026-08-15

---

## 8. httpx — ✓ Tested

**WHAT IS IT** — The next generation HTTP client. [pypi.org/project/httpx ↗]

**WHY USE IT** — API mirip requests tapi mendukung async/await dan HTTP/2 —
pilihan modern bila butuh banyak request paralel.
[python-httpx.org ↗]

**HOW TO USE**
```python
import httpx
r = httpx.get("https://api.github.com", timeout=10)
print(r.status_code)
print(list(r.json())[:5])
```
[adaptasi python-httpx.org quickstart]

**WHERE IT RUNS (ZCODE · ARMv7)**
- ✓ Pure Python; sync API jalan penuh
- ✓ async via asyncio jalan di thread script ZCODE
- ∆ HTTP/2 butuh extras `httpx[http2]` (h2) — install terpisah

**WHO MADE IT** — Tom Christie / Encode. BSD-3-Clause.

**SOURCES** — pypi.org/project/httpx · python-httpx.org ·
github.com/encode/httpx · dikurasi 2026-08-15

---

## 9. beautifulsoup4 — ✓ Tested

**WHAT IS IT** — Screen-scraping library: parse HTML/XML menjadi pohon yang
mudah dijelajahi dan dicari. [pypi.org/project/beautifulsoup4 ↗]

**WHY USE IT** — Cara tercepat mengekstrak data dari halaman web: cari tag,
class, atribut tanpa regex; toleran terhadap HTML berantakan.
[crummy.com/software/BeautifulSoup ↗]

**HOW TO USE**
```python
import requests
from bs4 import BeautifulSoup
html = requests.get("https://example.com", timeout=10).text
soup = BeautifulSoup(html, "html.parser")
print("judul:", soup.title.string)
for a in soup.find_all("a"):
    print("link:", a.get("href"))
```
[adaptasi docs resmi; parser html.parser = bawaan, tanpa lxml]

**WHERE IT RUNS (ZCODE · ARMv7)**
- ✓ Pure Python dengan parser bawaan `html.parser`
- ∆ Parser `lxml` (lebih cepat) adalah paket native terpisah — cek
  ketersediaannya sendiri; html.parser selalu aman
- ∆ Butuh requests/httpx untuk mengambil halamannya

**WHO MADE IT** — Leonard Richardson. MIT.

**SOURCES** — pypi.org/project/beautifulsoup4 ·
crummy.com/software/BeautifulSoup · Belajar (ID): code.tutsplus.com/id
(seri Beautiful Soup) · dikurasi 2026-08-15

---

## 10. openpyxl — ✓ Tested

**WHAT IS IT** — A Python library to read/write Excel 2010 xlsx/xlsm files.
[pypi.org/project/openpyxl ↗]

**WHY USE IT** — Buat dan baca file Excel asli langsung dari HP: laporan,
rekap nilai, data kiriman orang — tanpa Microsoft Office.
[openpyxl.readthedocs.io ↗]

**HOW TO USE**
```python
from openpyxl import Workbook
wb = Workbook()
ws = wb.active
ws.append(["Nama", "Nilai"])
ws.append(["Andi", 85]); ws.append(["Citra", 91])
wb.save("rekap.xlsx")
print("tersimpan: rekap.xlsx")  # bisa dibuka di app Excel/WPS HP
```
[adaptasi openpyxl.readthedocs.io tutorial]

**WHERE IT RUNS (ZCODE · ARMv7)**
- ✓ Pure Python
- ∆ File tersimpan di workspace internal ZCODE — buka via file manager/
  share ke app lain
- ∆ Workbook sangat besar (puluhan ribu baris) berat di RAM HP

**WHO MADE IT** — Eric Gazoni, Charlie Clark & kontributor. MIT.

**SOURCES** — pypi.org/project/openpyxl · openpyxl.readthedocs.io ·
dikurasi 2026-08-15

---

## 11. pillow — ✓ Tested · v11.0.0

**WHAT IS IT** — Python Imaging Library (fork). Image processing:
buka/olah/simpan puluhan format gambar. [pypi.org/project/pillow ↗]

**WHY USE IT** — Resize, crop, konversi format, filter, watermark, generate
gambar — semua dari script. [pillow.readthedocs.io ↗]

**HOW TO USE**
```python
from PIL import Image, ImageDraw
img = Image.new("RGB", (200, 100), "navy")
d = ImageDraw.Draw(img)
d.text((20, 40), "Halo ZCODE!", fill="white")
img.save("halo.png")
print("tersimpan: halo.png")
```
[adaptasi pillow.readthedocs.io tutorial]

**WHERE IT RUNS (ZCODE · ARMv7)**
- ✓ DEVICE VERIFIED 2026-08-14: install 11.0.0 + libjpeg/freetype (±20s
  karena deps sudah di cache)
- ✓ PNG/JPEG penuh (chaquopy-libjpeg + freetype ikut terpasang)
- ∆ Image.show() tidak membuka viewer — simpan file lalu buka manual
- ∆ Gambar resolusi sangat besar berat di RAM HP

**WHO MADE IT** — Fork PIL (Fredrik Lundh) oleh Jeffrey A. Clark &
kontributor. MIT-CMU.

**SOURCES** — pypi.org/project/pillow · pillow.readthedocs.io ·
uji ZCODE 2026-08-14 · dikurasi 2026-08-15

---

## Catatan implementasi Batch B

- Semua snippet HOW TO USE wajib diuji jalan (minimal py_compile + logika;
  ideal: run di device/emulator) SEBELUM masuk packages.json.
- Field baru per entri: `longDescription` (WHAT), `whyUse`, `example`,
  `whoMadeIt`, `sources[] {untuk,label,url}`, `curatedAt`.
- Sumber Indonesia = entri sources dengan label "Belajar (ID): ...".
- WHERE dirakit dari works/doesNotWork/risks yang SUDAH ada + fakta
  DEVICE VERIFIED baru; jangan duplikasi ke field baru.
