# BENGKEL "INSTALL MODULES" — Rancangan Batch Fix v1.0.18-final
**Status: DITAHAN sampai UAT 341 paket selesai. Dokumen ini rancangan, bukan eksekusi.**
Tanggal rancang: 2026-08-16 · Basis kode: PR #21 HEAD `2ba0a97` · Sumber temuan: UAT device user (Infinix SMART 9 HD, ARMv7 userspace)

---

## 0. Prinsip bengkel

1. Satu PR #21, commit per-bug yang koheren — bukan satu commit raksasa.
2. Setiap fix wajib punya guard test yang BISA MERAH (uji mutasi sebelum commit).
3. Urutan pengerjaan = urutan resiko regresi TERKECIL dulu, supaya kalau ada yang
   pecah, gampang mundur.
4. Label kejujuran dipertahankan: fix berbasis log device = kandidat DEVICE VERIFIED
   setelah user konfirmasi; sisanya SANDBOX VERIFIED sampai dibuktikan.
5. TIDAK menyentuh `.github/workflows/` (ranah user).

## 1. Urutan pengerjaan yang diusulkan

| # | Item | Resiko | Kenapa urutan ini |
|---|------|--------|-------------------|
| 1 | **Bug T** — ANR Diagnostics | Rendah (UI terisolasi) | Kalau user setuju, boleh maju duluan karena mengganggu UAT |
| 2 | **Bug Q** — NATIVE_HOST_DEPS libcxx | Sangat rendah (tambah 3 entri dict) | Pola persis Bug P yang terbukti |
| 3 | **Bug U** — chmod binary bundled | Rendah (satu titik di activate/extract) | Lokal, tidak mengubah alur |
| 4 | **Bug S** — filter pre-release + warning fosil | Sedang (menyentuh jalur pemilihan wheel SEMUA paket) | Butuh guard paling banyak |
| 5 | **Bug R** — smoke skip paket ACTIVE | Sedang (mengubah kontrak smoke) | Dikerjakan setelah S supaya uji ulang resolver memakai logika versi final |
| 6 | **Katalog** — vonis UNAVAILABLE + pin openai + promosi TESTED | Rendah (data JSON) | Terakhir, sekalian menyerap SEMUA log UAT yang sudah masuk |

---

## 2. Bug T — ANR "ZCODE tidak ada tanggapan" di Diagnostics

### Diagnosis (DIKOREKSI setelah baca kode 2026-08-16)
- ~~Baca file di thread UI~~ → **SALAH**. `DiagnosticsScreen.kt:85-99` sudah
  `withContext(Dispatchers.IO)` sejak fix sebelumnya.
- Biang sebenarnya: `DiagnosticsScreen.kt:249-275` —
  `Column + verticalScroll(rememberScrollState())` lalu `filtered.forEach { Text(...) }`.
  1961 baris = 1961 node `Text` dikomposisi + diukur + digambar SEKALIGUS,
  dibungkus `SelectionContainer` (mahal: selection tracking untuk semua node).
- Diperparah `Breadcrumb.MAX_BYTES` 128KB→512KB (`Breadcrumb.kt:30`) +
  `tail(2000)` (`DiagnosticsScreen.kt:90`): jumlah baris yang lolos ke UI membesar 4×.

### Rencana fix
1. `Column+verticalScroll+forEach` → **`LazyColumn` + `items(filtered)`**.
2. Batas tampilan awal: **500 baris terakhir** + tombol "Muat 500 lebih lama"
   (state `shownCount`, tambah per klik; `filtered.takeLast(shownCount)`).
3. `SelectionContainer` per-baris ATAU dipindah: baris pakai `Text` biasa,
   penyalinan lewat tombol **Salin** yang tetap menyalin **file penuh** —
   tidak ada data hilang untuk pelaporan.
4. `tail(2000)` tetap (sumber data lengkap untuk filter tab + Salin);
   yang dibatasi hanya jumlah node yang DIRENDER.

### Guard test (kotlin guards)
- `test_diagnostics_pakai_lazycolumn`: file mengandung `LazyColumn` dan TIDAK
  mengandung `verticalScroll` pada kolom log utama.
- `test_diagnostics_batas_render_awal`: konstanta 500 + string "Muat".
- `test_salin_tetap_file_penuh`: jalur Salin memakai teks lengkap (bukan takeLast).
- Uji mutasi: kembalikan `forEach` → guard harus merah.

---

## 3. Bug Q — instal-pertama murmurhash/cymem/preshed gagal `libc++_shared.so`

### Diagnosis
`mrmr.so` DT_NEEDED `libc++_shared.so`. `nativemap.py` (peta pasca-download)
menemukannya, tapi terlambat: kegagalan terjadi saat smoke instal-pertama sebelum
wheel `chaquopy-libcxx` ada di cache. Percobaan kedua sukses karena cache terisi
(pola identik Bug P/cffi→libffi).

### Rencana fix
`resolve.py` `NATIVE_HOST_DEPS` (line ~355), tambah:
```python
"murmurhash": ["chaquopy-libcxx"],
"cymem": ["chaquopy-libcxx"],
"preshed": ["chaquopy-libcxx"],
```
(preshed menarik murmurhash+cymem sebagai deps Python; entri eksplisit menjaga
kalau dipasang sendiri-sendiri.)

### Guard test
- `test_native_host_deps_murmurhash_libcxx` (+ cymem, preshed) di
  test_zcode_package_runtime.py — pola sama dengan guard Bug P cffi.

---

## 4. Bug U — pulp `EACCES` pada binary solver bundled

### Diagnosis
`pulp/solverdir/cbc/linux/i32/cbc` = executable ELF yang dibundel di wheel.
Ekstraksi zip TIDAK memulihkan bit executable → `EACCES` saat pulp mencoba
menjalankannya waktu ACTIVATION.

### Rencana fix — dua lapis
1. **Teknis**: saat extract/activate (`TransactionManager.kt`, sekitar `activate`
   line 93 / titik ekstraksi wheel): file non-`.so` non-`.py` yang punya header ELF
   (magic `\x7fELF`) → `setExecutable(true)`. Murah, aman, umum (paket lain yang
   bundel binary ikut kebagian).
2. **Katalog**: kartu pulp diberi catatan jujur — binary cbc itu **ELF linux
   i686/x86**, BUKAN ARM; walau chmod beres, exec di device ARM tetap gagal
   (format salah). Jadi vonis kartu: "pulp bisa diimpor, solver CBC bawaan tidak
   jalan di Android ARM — perlu solver eksternal". Fix chmod tetap masuk karena
   memperbaiki KELAS masalah, bukan cuma pulp.
   > CATATAN VERIFIKASI: klaim arsitektur i32=x86 masih riset metadata (path
   > `linux/i32/`); kalau mau pasti, bedah wheel pulp di sandbox dulu (readelf).

### Guard test
- Guard kotlin: TransactionManager memuat logika ELF-magic + setExecutable.
- Uji mutasi: hapus panggilan setExecutable → merah.

---

## 5. Bug S — resolver memilih pre-release & versi fosil

### Diagnosis (titik kode pasti)
- `wheelinfo.py:231 best_wheel` — ranking `(prio, versi menurun)`,
  **tanpa membedakan pre-release**: `4.0.0a6 > 3.11.2` → alpha menang.
- `resolve.py:436 _contains` — `prereleases=True` hardcoded: specifier kosong
  meloloskan alpha/beta/rc.
- Kasus fosil (gensim 0.10.1): bukan bug ranking — memang hanya versi purba yang
  punya wheel pure-py; perlu WARNING, bukan filter.

### Rencana fix — tiga lapis
1. **Filter PEP 440 di `best_wheel`** (wheelinfo.py): setelah `ranked` terkumpul,
   pisahkan stable vs pre-release via `Version(v).is_prerelease`. Jika ada
   kandidat stable → buang semua pre-release. Jika TIDAK ada stable → pre-release
   boleh (perilaku pip). Specifier eksplisit yang meminta pre-release
   (mis. `==4.0.0a6`) tetap dihormati lewat jalur `_contains`.
2. **Warning versi-tertinggal** (resolve.py, sekitar line 780 yang sudah mengisi
   `best["latest_version"]`): jika versi terpilih jauh di belakang latest
   (usul ambang: beda major ≥ 2 ATAU selisih rilis > 5 tahun bila tanggal
   tersedia dari PyPI JSON `upload_time`), tulis baris log
   `PERINGATAN: {nama} terpasang v{X}, terbaru v{Y} — versi lama karena
   keterbatasan wheel ARMv7`. Tidak memblokir instal.
3. **Vonis katalog** (bagian §7): imblearn (jebakan), watchfiles (cangkang
   0.0.0a1), gensim (fosil; versi berguna butuh scipy) → UNAVAILABLE + alternatif.

### Dampak versi yang diharapkan berubah (perlu re-test setelah fix)
apscheduler 4.0.0a6→3.x · isort 9.0.0b2→6.x/5.x · plotly 7.0.0rc0→6.x/5.x ·
optuna 5.0.0rc1→4.x · sqlalchemy 2.1.0b3→2.0.x · defusedxml 0.8.0rc2→0.7.1 ·
wrapt 2.4.0rc4→stable terakhir yang ada wheel pure/ARMv7.
> Konsekuensi katalog: entri sukses-UAT yang versinya pre-release (apscheduler
> a6, isort b2, plotly rc0, bokeh dev5) TIDAK langsung dipromosikan TESTED pada
> versi itu — dites ulang pada versi stable hasil filter, baru dipromosikan.

### Guard test (test_zcode_package_runtime.py)
- `test_best_wheel_tolak_prerelease_bila_ada_stable`: kandidat [3.11.2, 4.0.0a6]
  → pilih 3.11.2.
- `test_best_wheel_terima_prerelease_bila_tak_ada_stable`: kandidat hanya
  [1.0.0rc1] → pilih 1.0.0rc1.
- `test_specifier_eksplisit_prerelease_tetap_dihormati`: `pkg==4.0.0a6` → a6.
- `test_warning_versi_fosil`: selisih major ≥2 → log warning muncul.
- Uji mutasi: matikan filter → test #1 merah.

---

## 6. Bug R — smoke RE-TEST paket ACTIVE → numpy double-import meledak

### Diagnosis
`PackageEngineV2.kt` loop smoke (line ~456-542) menguji SEMUA paket dalam
transaksi, termasuk dependensi yang SUDAH aktif di env (numpy, matplotlib).
`run_smoke` (smoke.py:375) mengimpor modul dari staging sementara env aktif
sudah pernah memuatnya dalam proses yang sama → numpy tidak mendukung
double-import satu proses → `_NoValueType` TypeError → transaksi paket TAK
BERSALAH (quantities, seaborn, wordcloud) di-rollback.

### Rencana fix
Di loop smoke PackageEngineV2: **skip smoke untuk paket yang (a) sudah tercatat
ACTIVE di installed.json DAN (b) versinya sama dengan yang aktif** — dia sudah
lulus smoke saat instalasinya sendiri. Log tetap jujur:
`"  numpy: dilewati (sudah aktif & pernah lolos smoke)"`.
Paket BARU / versi berbeda tetap di-smoke penuh. Definisi "pernah lolos" =
keberadaan entri di installed.json (satu-satunya jalan masuk ke sana adalah
lolos smoke), jadi tidak butuh penanda baru.

### Interaksi dengan Bug S
Setelah filter pre-release, beberapa paket akan berganti versi → kondisi (b)
"versi sama" otomatis memaksa smoke ulang pada versi baru. Aman.

### Guard test
- Guard kotlin: PackageEngineV2 memuat cabang skip + string log "dilewati".
- Python: skenario installed.json berisi numpy → daftar rencana smoke tidak
  memuat numpy; numpy versi beda → tetap masuk.
- Uji mutasi: hapus cabang skip → merah.

### Kandidat verifikasi device (buat UAT final user)
Pasang numpy → sukses → pasang quantities/seaborn → harus SUKSES tanpa
`_NoValueType`.

---

## 7. Batch katalog — vonis, pin, promosi

### 7a. Vonis UNAVAILABLE baru (kartu TETAP ADA, model nisan-jujur + alternatif)
| Paket | Alasan singkat di kartu | Alternatif |
|---|---|---|
| imblearn | paket jebakan PyPI (alias kosong imbalanced-learn) | — (yang asli butuh scipy) |
| watchfiles | wheel placeholder 0.0.0a1 tanpa isi | watchdog (cek dulu ke UAT) |
| gensim | wheel ARMv7 hanya versi 2014; versi berguna butuh scipy | nltk (TESTED) |
| gitpython | butuh binary `git` di sistem | pygithub (SUKSES UAT) |
| jieba | sdist-only | — |
| jinjalint | dep docopt sdist-only | — |
| anthropic | deps Rust (tokenizers/jiter) | openai pin (7b) |
| langchain | deps Rust (orjson) | openai pin (7b) |

### 7b. Pin penyelamatan (tested-manifest.json)
- `openai: ["1.35.0"]` + `pydantic: ["1.10.19"]` — deps 1.35.0 verified pure;
  pydantic 1.10.19 wheel py3-none-any. Penting untuk rencana AI v1.0.19.
  Status saat masuk: ARMV7-IMPORT-VERIFIED dulu (bionic311), promosi setelah
  user cek di device.

### 7c. Promosi TESTED gelombang UAT (semua DEVICE VERIFIED dari log user 2026-08-16)
ansible, autoflake, autopep8, black, mako, nox, pre-commit, pygithub, pystache,
python-crontab, ruamel-yaml, yapf, fabric2, invoke, kubernetes, dask, cookiecutter,
srsly, sympy, tabulate, networkx, mpmath, uncertainties, xarray, pint, prettytable,
pydicom, periodictable, editdistance, ephem, pyerfa, pywavelets, aiosqlite, simpy,
fortranformat.
**DITUNDA sampai re-test versi stable** (kena Bug S): apscheduler, isort, plotly,
optuna(?), bokeh. **preshed/cymem/murmurhash**: promosi setelah fix Bug Q
diverifikasi instal-pertama.

### 7d. Kartu pulp
Update longDescription: importable, solver CBC bawaan tak jalan di Android ARM
(lihat §4 catatan verifikasi).

### Guard test
- `TestKatalogVonisV1018`: entri di atas berstatus UNAVAILABLE + punya alasan.
- Guard pin openai/pydantic di tested-manifest.
- Update `TestKelengkapanKatalogV1018` bila hitungan tier berubah.

---

## 8. Definisi selesai (Definition of Done) batch bengkel

1. Semua guard baru merah saat mutasi, hijau saat fix — dibuktikan di log kerja.
2. `tools/check.sh` + kotlin_sanity + 440+N test hijau lokal.
3. Push → CI "Build ZCODE APK — Fase 1 & 2" hijau → laporkan run ID + SHA
   (user ambil artifact `ZCODE-Fase12-APK` sendiri).
4. UAT final user, minimal: (a) Diagnostics tanpa ANR dengan log besar,
   (b) preshed instal-pertama sukses, (c) apscheduler dapat versi stable,
   (d) quantities/seaborn sukses setelah numpy aktif, (e) openai pin jalan.
5. Merge PR #21 → tutup v1.0.18 → buka gerbang v1.0.19 "Arah D".
6. Ingatkan user revoke PAT saat rilis dinyatakan lancar.

## 8b. TAMBAHAN GELOMBANG 3 UAT (log user 14:40–16:17, 2026-08-16) — DITAHAN

### Bug baru
- **Bug V — NATIVE_LOAD false positive**: smoke menggagalkan paket yang
  [IMPORT] OK hanya karena "tidak ada .so di staging". Bukti: coverage 7.15.4
  (wheel py3-none-any = pure, jelas tanpa .so) & pyzbar 0.1.8. Fix: aturan
  "wajib .so" hanya berlaku bila WHEEL yang terpasang bertag platform
  (cp311-android...), bukan berdasarkan ekspektasi katalog. KONSEKUENSI:
  re-audit vonis watchfiles (heuristik sama).
- **Bug W — validator nama paket substring**: `pycurl` ditolak "pola dilarang"
  (37× di log) — diduga blocklist berisi "curl" dan cocok substring. Fix:
  word-boundary/exact-token match. Verifikasi: cari pola validator di
  PipScreen/RequirementException path.
- **Bug X — resolve tanpa verdict**: `telegram` worker END tanpa
  package_chosen dan TANPA ANALYZE_FAIL → user tidak dapat kabar apa-apa.
  Fix: selalu emisi verdict (fail dengan human message) bila kandidat kosong.
- **Bug R bukti #2 (pybind11)**: contourpy "generic_type FillType is already
  registered!" saat instal pycocotools — contourpy sudah ACTIVE (dari
  matplotlib), di-smoke ulang → double-import. Memperkuat fix skip-ACTIVE.
- **Bug S korban baru**: stripe 15.6.0a1, sendgrid 7.0.0rc2, pydantic 2.14.0b1.
- **Koreksi katalog gw sendiri**: importName ruamel-yaml-clib yang benar =
  `_ruamel_yaml` (bukan ruamel.yaml.clib) — error `No module named 'ruamel'`.
- **Shadowing stdlib Chaquopy (kelas baru, riset dulu)**: tox gagal
  `packaging.pylock` & setuptools gagal `AssertionError distutils/core.pyc`
  (stdlib-common.imy) — modul bundel Chaquopy menutupi versi baru; zope-interface
  korban tak bersalah (deps setuptools). JANGAN vonis paketnya sebelum riset.
- **matplotlib-inline hidden dep**: import butuh matplotlib yang TIDAK
  dideklarasi → ipython gagal saat matplotlib belum ada (14:41), padahal
  matplotlib baru terpasang 14:46 via gif. Opsi fix: peta hidden-deps
  (matplotlib-inline→matplotlib) ATAU catatan kartu "instal matplotlib dulu".
- **ipdb transaksi hilang senyap** (15:11:34 download → tanpa OK/FAIL →
  15:13:54 NAV): TANYA USER dulu (ANR? keluar sendiri?) sebelum dicap bug.
- **Polish jaringan**: yt-dlp gagal URLError attempt 2/2 lalu sukses saat
  diulang manual → naikkan attempts utk URLError (bukan 404) jadi 3 + backoff.
- **Polish log**: `http_fail chaquopy 404` itu alur normal (probe toko beku) —
  relabel/turunkan verbosity supaya Diagnostics tak banjir.

### Vonis & pin baru (kandidat)
| Paket | Vonis | Alternatif |
|---|---|---|
| soundfile | UNAVAILABLE (libsndfile.so tak ada di Android) | wavio ✅, miniaudio ✅ |
| imagehash | UNAVAILABLE (butuh scipy) | — |
| moviepy | UNAVAILABLE (imageio-ffmpeg = binary ffmpeg) | imageio ✅ |
| sanic | UNAVAILABLE (httptools tanpa wheel ARMv7) | flask ✅, uvicorn ✅ |
| watchdog | UNAVAILABLE (tak ada wheel ARMv7) — alternatif utk watchfiles GUGUR |
| unittest2 | UNAVAILABLE (fosil; deps traceback2 tak ke-resolve) | pytest ✅ |
| telegram | kartu arahkan ke python-telegram-bot (cek dulu deps-nya) |
| fastapi | KANDIDAT SELAMAT: pin fastapi 0.99.x + pydantic==1.10.19 (verifikasi bionic311) |
| mypy | KANDIDAT SELAMAT: pin versi lama ber-wheel pure (verifikasi) |

### Kandidat promosi TESTED gelombang 3 (semua DEVICE VERIFIED log user)
textual, jupyter-core, prompt-toolkit, mutagen, piexif, pydub, qrcode, wavio,
aubio, depthai, jpegio, miniaudio, soxr, gif, imageio, asn1crypto, authlib,
hashids, keyring, oauthlib, passlib, pyotp, python-jose, trustme,
pycryptodomex, tgcrypto, bandit, factory-boy, flake8, freezegun, hypothesis,
memory-profiler, mock, pdbpp, pyright, pytest, pytest-asyncio, pytest-mock,
pytest-xdist, responses, vcrpy, arrow, boltons, colorlog, croniter, dateparser,
fuzzywuzzy, humanize, inflect, pathlib2, python-dotenv, retry, schedule,
tenacity, textdistance, toml, tomli, toolz, unidecode, cytoolz, google-crc32c,
lru-dict, typed-ast, aiohttp, click, discord.py, feedparser, flask, gunicorn,
html5lib, praw, httpx, pyquery, slack-sdk, tornado, tqdm, tweepy, twilio,
uvicorn, waitress, youtube-dl (catat: versi 2021 = fosil beneran, upstream mati;
kartu arahkan yt-dlp), yt-dlp, brotli, greenlet, grpcio, netifaces.
Ditunda (kena Bug S): stripe a1, sendgrid rc2. Perlu re-test: ipython (setelah
matplotlib aktif), ipdb (transaksi hilang), coverage/pytest-cov & pyzbar
(setelah fix Bug V), tox/setuptools/zope-interface (setelah riset shadowing).

## 8c. TAMBAHAN GELOMBANG 4 UAT (log user 16:37–18:52, 2026-08-16) — DITAHAN

### Konfirmasi teori (bukti device menguat)
- **Bug R repro #3 & #4**: quantities 16:49 (numpy re-smoke `_NoValueType`) dan
  seaborn 18:50 (matplotlib re-smoke). KONTRAS KUNCI: emcee 18:24 SUKSES
  men-smoke numpy karena itu impor PERTAMA numpy di proses tsb; seaborn 18:48
  gagal karena numpy/matplotlib sudah terpakai. Deterministik: impor pertama
  aman, impor ulang meledak → fix skip-ACTIVE valid & wajib.
- **Bug Q dari sisi sebalik**: cymem & murmurhash instal-pertama SUKSES
  (16:53-16:54) karena chaquopy-libcxx sudah di cache. Fix NATIVE_HOST_DEPS
  tetap perlu untuk user cache kosong.
- **Bug X DIAGNOSIS PRESISI**: korban bertambah odfpy, crontab, pypeln (deps
  stopit) + telegram. Pola: paket/deps SDIST-ONLY (kandidat wheel = NOL) →
  resolver berakhir TANPA verdict. Pembanding: cheetah3 (punya wheel tapi tak
  cocok) DAPAT verdict COMPATIBILITY. Fix: jalur kandidat-kosong wajib emisi
  ANALYZE_FAIL dengan pesan "paket ini hanya tersedia bentuk source (sdist),
  ZCODE butuh wheel".

### Hasil gelombang 4
- SUKSES (~35 + deps): python-pptx, pdfminer.six (EXPERIMENTAL→terbukti!),
  xlwt, reportlab, rarfile, tablib, send2trash, xlsxwriter(dep), cymem,
  murmurhash, preshed, srsly, yapf, ansible, apscheduler(a6), autoflake,
  autopep8, black, cookiecutter, fabric2, isort(b2), kubernetes, nox,
  pre-commit, pygithub, pystache, python-crontab, ruamel-yaml, emcee,
  bokeh(dev5), fortranformat, mpmath, networkx, nltk, optuna(rc1), pydicom.
  Deps terbukti: sqlalchemy 2.1.0b3 (kena Bug S; re-test versi stable),
  alembic, mako, joblib, cloudpickle, narwhals, xyzservices, asteval? (tidak—
  lmfit gagal sebelum instal).
- Vonis terkonfirmasi ulang: anthropic (jiter), gitpython (butuh git),
  jinjalint (docopt), watchfiles 0.0.0a1.
- Vonis baru: cheetah3 UNAVAILABLE (tak ada wheel cocok; alt jinja2/mako),
  hyperopt UNAVAILABLE (resolver jatuh ke fosil 0.3.0/2013 lalu tetap gagal;
  alt: optuna TERBUKTI SUKSES di device), arviz/lifelines/lmfit/peakutils/
  pykalman UNAVAILABLE (keluarga scipy), odfpy/crontab/pypeln → setelah fix
  Bug X beri pesan sdist-only (odfpy: alt python-docx? cek; crontab: alt
  python-crontab TERBUKTI; pypeln: alt multiprocessing stdlib).
- Katalog: xlsxwriter naik status (terbukti sbg deps python-pptx).

## 8d. TAMBAHAN GELOMBANG 5 UAT (screenshot user 11:09–19:02, 2026-08-16) — DITAHAN

### Konfirmasi & koreksi
- **Hidden-dep TERBUKTI**: ipython 9.16.1 ✅ & ipdb 0.13.13 ✅ setelah matplotlib
  aktif — matplotlib-inline lolos. ipdb "transaksi hilang" tak perlu diusut.
- **KOREKSI Bug X**: screenshot odfpy menunjukkan console MEMBERI verdict
  `[PACKAGE_NOT_AVAILABLE]`. Yang bolong hanya pencatatan breadcrumb (tidak ada
  PKG_ANALYZE_FAIL di Diagnostics utk kasus kandidat-kosong). Fix = emisi event
  log, bukan perombakan UX. (telegram/crontab/pypeln diasumsikan sama; verifikasi
  saat fix.)
- **Bug W visual**: screenshot pycurl polos ditolak "pola dilarang" — konfirmasi
  substring blocklist "curl".

### BUG BARU — Bug Y: Diagnostics terpotong & Salin tidak utuh (keluhan user)
- Penyebab ganda: (1) rotasi Breadcrumb 512KB memotong file jadi separuh →
  sejarah lama HILANG dari disk; (2) tail(2000) membatasi tampilan, dan Salin
  menyalin dari buffer tampilan itu, bukan file penuh.
- Fix (gabung paket Bug T): tombol "Ekspor log penuh" via SAF (pola
  exportLauncher run-log yang sudah ada), Salin diarahkan ke buffer terlengkap,
  pertimbangkan retensi lebih besar (mis. 2MB) atau file arsip harian.
- Guard: teks Salin ≠ takeLast(tampilan); tombol Ekspor ada.

### Panen sukses gelombang 5 (dari screenshot, DEVICE VERIFIED versi terpasang)
- Files/Office: babel 2.18.0, chardet 7.6.0, charset-normalizer 3.5.1, csvkit
  2.2.0, docutils 0.23, filetype 1.2.0, fpdf2 2.8.8, ftfy 6.3.1, markdown
  3.10.3, markdownify 1.2.3, mistune 3.3.4, parsedatetime 2.6, patool 4.0.5,
  pdfplumber 0.11.10 (!), pypdf 6.16.1, python-docx 1.2.0, xlrd 2.0.2 →
  OFFICE SUITE LENGKAP (PDF/Word/Excel/PPT/MD).
- Database/Storage: boto3 1.43.72, cloudpickle 3.1.2, dataset 2.0.0, dill
  0.4.1, diskcache 5.6.3, fabric 3.2.3, joblib 1.5.3, lz4 4.3.2, minio 7.2.20,
  peewee 4.3.0, pymysql 1.2.0, redis 8.1.0, tinydb 4.9.0, zstandard 0.15.2.
- GUI/Testing/dll: blessed 1.48.0, ipython 9.16.1, ipdb 0.13.13, contourpy
  1.0.5, kiwisolver 1.4.5, argon2-cffi-bindings 21.2.0, pycryptodome 3.21.0.
- Vonis wheel-tak-ada dapat verdict rapi (blosc, psycopg2-binary, pymongo,
  python-snappy, python-magic) → kartu tinggal diselaraskan.
- Sisa belum tersentuh kini SANGAT tipis: py7zr, pyzipper, pickledb,
  sqlitedict, sqlmodel, pendulum, websockets, ormar (+ EXPERIMENTAL berat
  AI/ML yang mayoritas sudah bervonis).

## 9. Yang SENGAJA tidak masuk batch ini
- Deskripsi 11 kategori Library + WHY tier-2/3 + example non-TESTED → konten,
  bisa nyusul tanpa resiko kode (boleh ikut kalau batch lancar).
- Lint gutter, AI BYOK, Alpine proot → v1.0.19, jangan bocor ke sini.
- `.github/workflows/*` → ranah user.
