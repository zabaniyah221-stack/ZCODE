# AUDIT LIBRARY & SAMPLES — ZCODE v1.0.19

Tanggal snapshot: 2026-08-19  
Branch: `arena/v1019-fondasi`  
Basis: data produk lokal, bukan tebakan atau target jumlah.

## 1. Tujuan dan batas audit

Koreksi sejarah: gelombang awal Gerbong D sudah IMPLEMENTED pada commit
`7111d42` (30 snippet katalog, deskripsi 11 kategori, dan 6 sample baru).
Audit ini menilai **gap setelah gelombang awal tersebut**, bukan menyatakan
Gerbong D belum pernah dimulai.

Audit ini menjawab tiga pertanyaan sebelum konten lanjutan ditulis:

1. Bagian mana dari Detail Library yang benar-benar kosong/generik?
2. Paket TESTED mana yang belum punya sample berguna?
3. Struktur Samples mana yang perlu diperkaya tanpa memasukkan contoh mati?

Audit ini **tidak** menaikkan status paket, tidak menjanjikan paket yang belum
runnable, dan tidak menambah dependency aplikasi. `Samples` tetap memiliki
kontrak: item yang tampil harus dapat dijalankan setelah dependency yang
disebutkan dipasang.

Sumber data:

- `app/src/main/assets/package_catalog/packages.json`
- `app/src/main/assets/package_catalog/tested-manifest.json`
- `app/src/main/java/com/zaba/zcode/core/samples/SampleLibrary.kt`
- `app/src/main/assets/samples/*.py`

## 2. Snapshot Library

### 2.1 Populasi katalog

| Status | Jumlah |
|---|---:|
| TESTED | 231 |
| UNAVAILABLE | 77 |
| EXPERIMENTAL | 16 |
| INCOMPATIBLE | 10 |
| COMPATIBLE | 8 |
| **Total** | **342** |

### 2.2 Kedalaman kurasi

| Tier konten | Semua status | TESTED |
|---|---:|---:|
| Kurasi tangan | 33 | 19 |
| Auto metadata PyPI | 309 | 212 |
| **Total** | **342** | **231** |

Temuan utama: status kompatibilitas katalog sudah luas, tetapi kedalaman kartu
belum mengikuti luasnya katalog. Hanya **19 dari 231 TESTED** yang memiliki
paket lengkap `longDescription + whyUse + example + whoMadeIt + sources +
curatedAt`. Ada **37 TESTED** dengan snippet `example`; berarti **194 TESTED**
belum punya HOW TO USE.

Pada 212 entri TESTED auto-fill:

- 212 belum punya `useCases`;
- 212 belum punya `whyUse`;
- 212 belum punya `risks`;
- 212 belum punya `license` dan `publisher` terstruktur;
- 194 belum punya `example`.

Semua 231 TESTED memiliki `dependencies = []`. Ini bukan bukti bahwa semua
paket bebas dependency; field detail belum diisi dari resolver/metadata secara
terstruktur. UI saat ini juga belum punya field terpisah untuk `testedAt` dan
`testedDevice`; bukti device umumnya tertanam sebagai prosa di `works`.

### 2.3 Distribusi gap per kategori

| Kategori | Total | TESTED | TESTED punya example |
|---|---:|---:|---:|
| AI / ML / NLP | 29 | 5 | 0 |
| Automation / Scripting | 25 | 18 | 1 |
| Data / Math / Science | 55 | 32 | 9 |
| Database / Storage | 25 | 16 | 4 |
| Files / Office / Document | 30 | 26 | 5 |
| GUI / Games / App Framework | 15 | 5 | 0 |
| Image / Audio / Media | 32 | 18 | 4 |
| Security / Cryptography | 20 | 17 | 2 |
| Testing / Quality / Debugging | 25 | 17 | 2 |
| Utilities / CLI / Terminal | 40 | 38 | 2 |
| Web / API / Networking | 45 | 39 | 8 |

Ada satu kategori tambahan `Dev Tools / Testing` yang hanya berisi
`virtualenv`, sementara kategori kanonis `Testing / Quality / Debugging` juga
ada. Jadi katalog saat ini punya **12 string kategori**, bukan target 11.
Kategori tunggal ini perlu dinormalisasi, bukan diberi deskripsi baru seolah
kategori terpisah.

### 2.4 Ketidaksinkronan manifest

`tested-manifest.json` memiliki 237 nama, sedangkan katalog memiliki 231 entri
berstatus TESTED.

- Ada di manifest tetapi tidak ada di katalog:
  `matplotlib-inline`, `pandas-datareader`, `plotnine`, `pyopenssl`.
- Ada di manifest tetapi katalog masih EXPERIMENTAL:
  `mypy`, `paramiko`.
- Tidak ada TESTED katalog yang hilang dari manifest.

Hasil klasifikasi setelah membaca kontrak resolver dan guard: ini **bukan enam
bug status**. `tested-manifest.json` berfungsi sebagai peta versi prioritas
resolver, bukan daftar kartu yang seluruhnya berstatus TESTED. Kontraknya
sengaja satu arah: setiap kartu TESTED wajib punya versi di manifest, tetapi
setiap nama di manifest tidak wajib menjadi kartu TESTED. Empat nama tanpa
kartu dapat dipakai sebagai dependency/pin resolver; `mypy` dan `paramiko`
tetap EXPERIMENTAL walau resolver memiliki versi prioritas.

Utang yang tersisa adalah penamaan `tested-manifest` yang mudah disalahartikan.
Jangan rename file/schema pada batch konten karena dipakai Kotlin, Python, dan
guard; cukup dokumentasikan semantik asimetrisnya sampai ada migrasi tersendiri.

## 3. Snapshot Samples

### 3.1 Struktur saat ini

| Kategori Samples | Jumlah item |
|---|---:|
| Basics | 15 |
| Numpy | 2 |
| Paket Populer | 11 |
| Web | 1 |
| **Total** | **29** |

Ada 30 file `.py`; satu adalah companion `helper_util.py`, sehingga jumlah
asset konsisten dengan 29 item + 1 companion.

Hanya 12 nama paket unik yang direferensikan melalui `requiresPackage`:

`cryptography`, `matplotlib`, `numpy`, `openpyxl`, `pandas`, `pillow`,
`python-docx`, `qrcode`, `requests`, `rich`, `sympy`, `tqdm`.

Artinya **220 dari 231 paket TESTED** belum memiliki sample terkait. Angka 220
bukan target untuk membuat 220 sample: banyak dependency/utility internal tidak
layak mendapat sample sendiri. Seleksi harus berdasarkan kegunaan user.

### 3.2 Gap yang langsung terlihat

1. NumPy hanya 2 pelajaran; belum membentuk jalur array → slicing → reshape →
   operasi multi-array → matriks.
2. Matplotlib hanya 1 sample; belum ada line/scatter/subplot/annotation.
3. Web hanya satu contoh stdlib dan satu Requests di kategori generik.
4. Database package belum punya sample meski 16 paket berstatus TESTED.
5. Office sudah punya Excel dan Word, tetapi belum PPTX/PDF.
6. Utilities memiliki 38 TESTED tetapi hanya sedikit representasi praktis.
7. Kategori `Paket Populer` terlalu lebar untuk pertumbuhan berikutnya; tujuan
   user lebih mudah dicari lewat kategori kegunaan.
8. Belum ada relasi data `sampleId` dari Detail Library ke sample lengkap.
   `example` saat ini adalah snippet terpisah, sehingga berisiko membusuk dan
   belum bisa menjadi tombol `Coba contoh lengkap`.

### 3.3 Ketidakkonsistenan cryptography

Sample `crypto_pesan.py` memakai `requiresPackage = ["cryptography"]`, tetapi
katalog menandai `cryptography` sebagai COMPATIBLE, bukan TESTED. Komentar
SampleLibrary menyatakan kelompok tersebut teruji di device, sementara field
`works` hanya mencatat ARMV7-IMPORT-VERIFIED bionic311.

Keputusan audit: **jangan naikkan status berdasarkan komentar**. Cari bukti UAT
lama yang spesifik atau uji di device pada batch berikutnya. Sampai itu ada,
status tetap COMPATIBLE dan sample diberi perhatian khusus dalam UAT.

## 4. Prioritas kurasi Detail Library

Urutan dipilih berdasarkan hubungan dengan sample, kegunaan nyata, dan bukti
TESTED yang sudah ada—bukan alfabet atau popularitas semata.

### P0 — paket yang sample-nya sudah tampil

Lengkapi lebih dulu kartu auto-fill berikut agar Samples dan Library tidak
berbeda kualitas:

- `python-docx`
- `qrcode`
- `sympy`
- `cryptography` — setelah status/bukti diselesaikan

Kartu kurasi tangan yang sudah ada tetap diaudit sinkronisasi snippet terhadap
sample: `numpy`, `requests`, `rich`, `tqdm`, `openpyxl`, `pillow`, `pandas`,
`matplotlib`.

### P1 — paket untuk gelombang sample paling berguna

- Web/API: `httpx`, `beautifulsoup4`, dan client `aiohttp`. `flask` layak
  diperkaya kartu detailnya sekarang, tetapi sample server ditunda sampai
  kontrak preview, lifecycle, port dinamis, dan loopback aman disepakati.
- Files/Office: `python-pptx`, `pypdf`, `fpdf2` atau `reportlab`,
  `xlsxwriter`.
- Database: `tinydb`, `aiosqlite`, `sqlalchemy` atau `peewee`.
- Security: `pyotp`, `bcrypt` atau `pynacl`.
- Utilities: `python-dotenv`, `pyyaml`, `schedule`, `dateparser`.
- Data: `networkx`, `seaborn` (backend non-GUI dan peringatan beban).

Daftar ini masih shortlist audit, **bukan janji seluruhnya masuk satu build**.
Paket yang fungsinya tumpang tindih dipilih satu berdasarkan kejelasan sample,
beban dependency, dan manfaat belajar.

### P2 — kartu kendala yang berguna meski belum runnable

Paket seperti TensorFlow, PyTorch, OpenCV, Kivy, Qt/PySide, Tkinter, Pygame,
SciPy, dan scikit-learn tetap berada di Library. Kartunya harus menjawab:

- kendala teknis sekarang;
- apakah masalahnya ABI, binary, GUI surface, Python, atau resource;
- alternatif yang dapat dipakai sekarang;
- status riset dan premis yang dapat membuka kembali target.

Mereka tidak dimasukkan sebagai sample abu-abu/mati.

## 5. Rancangan pertumbuhan Samples

Struktur yang direkomendasikan:

- **Dasar Python** — pertahankan 15 item yang ada.
- **NumPy** — tambah slicing, reshape, multi-array, dan matrix solver.
- **Matplotlib** — line/scatter, subplot, annotation; selalu output file via
  backend `Agg`.
- **Web & API** — Requests/HTTPX, parsing HTML, async request; selalu timeout.
- **File & Office** — Word, Excel, PowerPoint, PDF.
- **Database** — SQLite stdlib, TinyDB, async SQLite, satu ORM.
- **Data & Matematika** — pandas, SymPy, NetworkX; sample berat diberi informasi.
- **Gambar & Media** — Pillow/QR dan hanya paket lain yang runnable.
- **Security & Utilities** — Fernet/OTP/hash/config/YAML/scheduler.
- **Project Mini** — contoh multi-file yang menggabungkan beberapa konsep.

Kategori package khusus layak dibuat jika paket memiliki jalur belajar yang
cukup dalam, seperti NumPy dan Matplotlib. Paket lain dikelompokkan berdasarkan
tujuan pengguna agar menu tidak berubah menjadi indeks nama package.

## 6. Guard sebelum implementasi konten

Setiap sample baru wajib:

- tercatat di `SampleLibrary` dan asset-nya ada;
- punya `requiresPackage` kanonis yang cocok dengan katalog;
- lolos `py_compile`;
- tidak menyimpan secret/API key;
- network call memiliki timeout;
- GUI desktop tidak dipakai;
- file output mudah ditemukan dan tidak menimpa file user;
- companion asset tidak overwrite;
- paket berstatus UNAVAILABLE/INCOMPATIBLE tidak boleh menjadi dependency;
- deskripsi menyebut output dan kebutuhan paket secara jujur.

Perubahan relasi `sampleId`, kategori, atau tombol `Coba contoh lengkap` adalah
perubahan kontrak UX/data. Implementasinya harus dibahas dan diberi guard
sinkronisasi dua arah sebelum dikerjakan.

## 7. Urutan kerja yang disarankan

1. Putuskan normalisasi kategori yatim `virtualenv`; jangan ubah data dulu
   karena guard lama secara eksplisit memperlakukannya sebagai pengecualian.
2. Pertahankan kontrak asimetris manifest; dokumentasikan bahwa ini peta versi
   resolver, bukan daftar status UI.
3. Lengkapi P0: kartu paket yang sample-nya sudah ada.
4. Putuskan schema relasi `PackageDetails ↔ SampleEntry` sebelum menyalin kode.
5. Buat gelombang sample kecil namun lintas-kegunaan dari P1.
6. Tambah kartu detail bersamaan dengan sample sebagai satu sumber kebenaran.
7. Jalankan guard + mutation test, CI, lalu satu UAT device terkurasi.
8. Baru lanjut gelombang berikutnya berdasarkan hasil UAT, bukan mengejar angka.

## 8. Status jujur

- Audit data: **COMPLETED** untuk snapshot commit sebelum pengayaan lanjutan.
- Rancangan prioritas konten: **DESIGNED**, belum IMPLEMENTED.
- Sample/kartu baru setelah audit: **NOT IMPLEMENTED**.
- Relasi `Coba contoh lengkap`: **IMPLEMENTED lokal** pada commit `7def2cf`
  dengan schema nullable `PackageDetails.sampleId`, 11 link TESTED, dan satu
  dependency gate bersama; belum CI/DEVICE VERIFIED.
- Paket GUI/ML besar: tetap **TERKENDALA/UNAVAILABLE** sesuai bukti saat ini,
  tetapi menjadi kandidat riset berdasarkan prinsip produk baru.
