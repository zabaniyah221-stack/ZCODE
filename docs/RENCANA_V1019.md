# RENCANA v1.0.19 "Arah D" — disepakati 2026-08-17

Status: **DESIGNED** (menunggu ACC user per build). Basis: main @ f259745
(PR #21 merged; katalog 230 TESTED; logo Electric Cyan).

Keputusan user yang mengikat rencana ini:
- Item #3 (AI BYOK) & #5 (Alpine proot / App Mode / jedi) → KULKAS, jangan bocor.
- TOOLS jadi SATU kotak scroll dengan label seksi; **THEME tetap dipaku di
  dasar kotak di luar scroll** (ganti tema tanpa scroll).
- Whitespace guard default **OFF**; Lint gutter & Traceback jump default ON.
- Konten SAMPLES: mulai kurasi ±25-30, bukan 230 sekaligus; tiap sample wajib
  terbukti jalan (device / minimal bionic311 + py_compile).

---

## MAU APA (3 gerbong + 1 antrian riset)

### Gerbong A — Editor "ZCODE nunjukin salahmu di mana" (build 1)
1. **Lint gutter** (`@codemirror/lint`): garis merah bergelombang + ikon
   gutter + tooltip via tap, sumber = `vm.problems` (Checker yang sudah ada).
   VPP tetap hidup dulu (keputusan slim/tidaknya setelah UAT rasa).
2. **Whitespace guard**: `highlightWhitespace`/`highlightTrailingWhitespace`
   (bawaan @codemirror/view) + aturan baru Checker: deteksi indentasi
   campuran tab/spasi → Problem → otomatis tampil di lint gutter juga.
3. **Traceback tap-to-jump**: baris `File "main.py", line N` di terminal jadi
   tappable → `gotoLine(N)` di editor (bridge gotoLine sudah ada).
4. **TOOLS satu-scroll**: LazyColumn tunggal (max ±300dp) berisi seksi
   PLUGINS / EDITOR (lint, whitespace, traceback jump, symbol bar);
   THEME dipaku di dasar kotak. Persist toggle pola symbolBarEnabled.

### Gerbong B — Fondasi konten (build 1, kecil)
5. **requiresPackage** di SampleEntry: sample yang butuh paket belum aktif →
   dialog jujur "butuh X, instal dulu? [Ke Install Modules] [Buka aja]" —
   membuka pintu sample paket pip tanpa risiko crash-saat-coba.

### Gerbong C — Resolver (build 1)
6. **Provided-packages**: peta RUNTIME_PROVIDED (setuptools 68.2.2, wheel
   0.41.2, pip 23.3.1, packaging 24.1 — versi beku APK). Deps yang meminta
   paket ini = terpenuhi runtime, skip download+smoke, catat di notes.
   Specifier yang tak terpenuhi versi beku → vonis jujur, bukan pura-pura.
   Target penyembuhan: zope-interface + kelas korban deps setuptools.

### Gerbong D — Konten besar (build 2, setelah fondasi terbukti)
7. **±25-30 sample baru** dari paket TESTED (office/data/web/teks/crypto),
   semua lewat requiresPackage; py_compile guard; UAT sampling di device.
8. **Perkaya Detail paket TESTED**: testedVersion+tanggal+device, deps,
   catatan ZCODE (pin dsb.), tombol "Coba contohnya →" ke sample terkait;
   field `example` diisi seiring (sample = sumber tunggal).
9. **Deskripsi 11 kategori + Jalur Belajar** (layer navigasi kurasi:
   Basics → File & Data → Web → Office → Data Science).

### Antrian riset (paralel, tidak memblokir build)
10. **4c**: replay setuptools 84 di bionic311 (Python 3.11 punya distutils) —
    hasil sah walau ujungnya "tidak ada obat, ini alasannya".
11. **4b**: deps-opsional (imagehash): field manifest `optionalDeps`,
    warning di console + catatan kartu. Dikerjakan setelah C stabil.

## LANGKAH (urutan eksekusi build 1)

1. Ritual repo + branch baru `arena/v1019-editor-fondasi`.
2. Gerbong C dulu (resolver = pure Python, test termurah): peta + notes +
   guard + mutasi; verifikasi bionic311 resolve zope-interface → import OK.
3. Gerbong B: requiresPackage (data class + dialog + guard).
4. Gerbong A: (a) editor-src: tambah @codemirror/lint pin eksak, expose
   setDiagnostics/toggle via Compartment, npm run build, commit bundle;
   (b) Checker: aturan tab/spasi + guard mutasi; (c) bridge vm.problems →
   setDiagnostics; (d) terminal: regex traceback → annotasi tappable;
   (e) TOOLS restructure.
5. check.sh + kotlin_sanity + full pytest + mutasi → commit per-gerbong →
   push → CI hakim → lapor run ID → UAT user (satu APK, checklist pendek).
6. Versi: zcode.versionName=1.0.19, versionCode=22 (di commit terakhir
   sebelum push, sesuai pola).

## KENDALA YANG DIPREDIKSI + PENANGANAN

| # | Kendala | Antisipasi |
|---|---|---|
| 1 | Rebuild bundle CM6 di sandbox (npm ci butuh network+disk; bundle 449KB→±470KB) | editor-src punya lockfile; pin eksak versi lint kompatibel codemirror 6.0.2 (referensi Acode); kalau npm registry rewel → retry/mirror; bundle di-commit jadi CI tak butuh Node |
| 2 | Kontrak bridge JS baru (setDiagnostics/toggles) tak bisa diuji penuh di sandbox (WebView butuh device) | Guard string dua sisi (JS bundle & Kotlin caller); uji manual di UAT; fallback: toggle OFF = perilaku lama persis |
| 3 | Tooltip lint di HP: tap kadang bentrok dengan cursor placement | Konfigurasi tooltip hanya via gutter-tap bila bentrok; keputusan final saat UAT rasa di device |
| 4 | Regex traceback salah tangkap (mis. path di string user) | Batasi pola ke baris `File "<nama file workspace>", line N` + hanya file yang memang ada di workspace; guard test dgn kasus jebakan |
| 5 | Provided-packages: paket yang BUTUH setuptools versi baru beneran | Specifier check jujur: `>=80` vs beku 68.2.2 → vonis dgn pesan jelas, guard memastikan tidak diam-diam lolos |
| 6 | TOOLS restructure menyenggol state drawer (regresi UI lama) | Perubahan terbatas blok AnimatedVisibility TOOLS; kotlin_sanity + review diff manual; UAT visual |
| 7 | Scope membengkak diam-diam (godaan nyicil gerbong D di build 1) | DILARANG — gerbong D eksplisit build 2; kalau ada temuan baru → catat di rencana, bukan dikerjakan |
| 8 | Snapshot menghapus /var/tmp & node_modules | Resep idempotent: setup_armv7_emu.sh + npm ci ulang; tidak menyimpan artefak besar di workspace |

## TARGET v1.0.19 (definisi selesai, bisa diamati)

1. **Editor**: user melihat garis merah tepat di baris salah; error
   IndentationError kelas tab/spasi terdeteksi SEBELUM run; tap baris
   traceback di terminal → editor lompat ke barisnya. Semua toggle-able
   dari TOOLS; OFF = perilaku lama.
2. **Resolver**: zope-interface terpasang sukses di device (dulu korban
   setuptools 84); tidak ada regresi paket TESTED (sampling UAT).
3. **Konten**: tap sample ber-requiresPackage tanpa paket → dialog jujur,
   bukan crash. (Build 2: 25-30 sample baru teruji + Detail TESTED kaya +
   Jalur Belajar hidup.)
4. **Proses**: setiap fix ber-guard + lolos uji mutasi; CI hijau; label
   kejujuran dipertahankan (SANDBOX → ARMV7-IMPORT → DEVICE VERIFIED);
   commit menyebut yang TIDAK dikerjakan.
5. **Angka**: test count naik dari 298 (file utama); katalog tak turun dari
   230 TESTED; nol regresi fungsi v1.0.18.

---

## LOG EKSEKUSI

### 2026-08-18 — Build fondasi C+B: DEVICE VERIFIED
UAT user (breadcrumb v1.0.19, 11:29-11:36):
- Gerbong C: zope-interface INSTALL_OK 1 paket (notes "setuptools:
  disediakan runtime ZCODE v68.2.2"); root `setuptools` → PKG_STDLIB +
  info ℹ️ di console, 0 network. Katalog: zope-interface → TESTED @6.1
  (paket ke-231).
- Gerbong B: SAMPLES_BUTUH_PAKET 5x (numpy×2/openpyxl/pillow/matplotlib)
  → dialog muncul + tombol ke Install Modules bekerja; web_fetch_json
  (stdlib) lolos tanpa dialog. Kontrol dua arah terbukti.
- Keputusan user: branch TIDAK di-merge dulu — Gerbong A menyambung di
  branch ini juga → satu PR utuh v1.0.19, sekali merge di akhir.

Berikutnya: diskusi mendalam Gerbong A (lint gutter, whitespace guard,
traceback jump, TOOLS satu-scroll + THEME pinned) sebelum implementasi.

### 2026-08-18 — Gerbong A: IMPLEMENTED (6 commit, menunggu CI + UAT)
- 292ba15 A0 rotate resilience (3 titik + SKILL 17)
- b6bef70 bundle CM6 + @codemirror/lint 6.9.7 (449→471KB)
- fb65d22 A2 checker indentasi campuran (WARNING, aturan sempit)
- 4c79913 A3 traceback jump + A6 hint + A4 TOOLS satu-scroll + bridge
- ea5f9f8 A5 reference card (22 pola) + A7 Project Mini multi-file
Transparansi: uji mutasi membongkar 3 guard palsu (kata di komentar/
parameter) → ditulis ulang strip_kt_comments; 2 guard lama direnovasi
dgn sejarah dua era (terowongan editor, sinkron assets-katalog).

### 2026-08-19 — Traceback jump: DEVICE VERIFIED setelah koreksi jalur overlay
UAT Infinix SMART 9 HD, ARMv7, APK v1.0.19 commit `6fa90f5`:
- SyntaxError `main.py:3` menghasilkan frame workspace inline yang dapat ditap.
- Chip fallback `Ke baris error → main.py:3` muncul pada state `FAILED`.
- Jump kembali ke editor berfungsi. Akar regresi sebelumnya bukan API
  CodeMirror: FAB produksi memakai `TerminalScreen` overlay di
  `WorkbenchScreen`, sedangkan callback hanya terpasang pada route output lama
  di `MainActivity`; overlay menerima default `onGotoEditorLine = null` dan
  mematikan inline link sekaligus chip.
- Fix menyambungkan callback + toggle pada overlay aktif, menutup overlay,
  memilih file traceback, menyinkronkan isi CodeMirror, lalu dispatch
  `gotoLine`. Guard baru terbukti merah saat callback/pushCode dimutasi.
- Dua pintu dipertahankan dengan fungsi berbeda: chip = sumber error workspace
  terdalam (jalur utama ramah jempol), inline = navigator frame traceback
  multi-file. Label chip akan dipoles bersama batch glyph, bukan build tunggal.

### 2026-08-19 — UAT Build 1 hardening + swipe + glyph: DEVICE VERIFIED
UAT user pada INFINIX X6532C, ARMv7 (runtime diagnostics terbaru: Android
14/API 34), APK v1.0.19 commit `bf5e7ef` (CI run `32196030398`, conclusion
`success`): user mengonfirmasi
seluruh checklist berfungsi lancar sesuai harapan.

Cakupan yang naik status dari CI VERIFIED menjadi DEVICE VERIFIED:
- Editor WebView tetap memuat CodeMirror setelah CSP dan native network block;
  edit, lint, Find, goto-line, traceback jump, dan font bundled tidak regresi.
- Pembatasan network hanya berlaku pada Editor WebView; internet Python tetap
  berfungsi dan alur Install Modules tidak regresi.
- Tab `LIBRARY` ↔ `MANUAL INSTALL` berfungsi lewat tap maupun swipe; state,
  seleksi/copy console, proses aktif, keyboard, scroll/input, dan rotasi tidak
  menunjukkan regresi pada checklist UAT.
- Migrasi decorative emoji ke glyph/text stabil tampil benar; label Back,
  startup Python, dan traceback root-source tetap berfungsi, sementara emoji
  dalam Samples tetap dipertahankan sesuai kebijakan.

Status jujur: ini DEVICE VERIFIED untuk perangkat UAT di atas, bukan klaim
universal untuk seluruh perangkat/ROM Android. PR belum dibuka dan release
belum dilakukan. Langkah berikutnya adalah audit gap nyata Library dan Samples
sebelum memperkaya detail paket atau menambah sample terkurasi.

### 2026-08-19 — Roadmap konten disetujui; audit gap Library/Samples selesai
User menyetujui arah: Samples hanya berisi contoh runnable, paket yang belum
didukung dijelaskan jujur di Library dan tetap dinilai sebagai kandidat riset,
bukan dikubur atau dijadikan premium lock. Prinsip kanonis dicatat di
`PRD_ZCODE.md` §1 dan aturan kontributor di `SKILLS.md`.

Audit snapshot dicatat di `AUDIT_LIBRARY_SAMPLES_V1019.md`. Koreksi sejarah:
Gerbong D gelombang awal sudah IMPLEMENTED pada commit `7111d42`; pekerjaan
berikut adalah pengayaan lanjutan, bukan memulai dari nol. Temuan utama: 231
paket TESTED tetapi baru 19 kartu TESTED berkedalaman kurasi tangan; 29 sample
runnable dengan 12 requirement package unik; relasi Detail Library → sample
lengkap belum memiliki schema. Enam selisih manifest sudah diklasifikasi bukan
bug: manifest adalah peta versi resolver dengan kontrak sengaja satu arah,
bukan daftar status UI. Status: audit COMPLETED, prioritas DESIGNED, konten
lanjutan belum IMPLEMENTED.

### 2026-08-19 — Jembatan Detail Library → Samples: IMPLEMENTED
Commit `7def2cf` menambahkan relasi opsional `PackageDetails.sampleId`, tombol
`Coba contoh lengkap →`, dan satu `SampleRequirementDialog` yang dipakai oleh
SamplesScreen maupun Detail Library. Sebelas kartu TESTED ditautkan ke sample
lama yang sudah runnable; `cryptography` sengaja tidak ditautkan karena bukti
saat ini baru COMPATIBLE/ARMV7-IMPORT-VERIFIED, bukan DEVICE VERIFIED spesifik.

Alur Detail Library:
- paket aktif → sample dibuat dan kembali ke editor;
- paket belum aktif → pilihan `Install dulu` / `Buka kode` / `Batal`;
- `Install dulu` membuka tab Manual dengan requirement pertama terisi;
- `sampleId` yatim memiliki fallback toast + breadcrumb, tetapi guard seharusnya
  mencegah data tersebut masuk build.

Guard memeriksa round-trip JSON, sample ID unik/ada, kecocokan
`requiresPackage`, larangan link UNAVAILABLE/INCOMPATIBLE, pemakaian dependency
gate bersama, generator, dan navigasi kembali ke editor. Uji mutasi terbukti
merah untuk tujuh arah: ID yatim, requiresPackage hilang, status mustahil,
toJson membuang field, dialog terduplikasi, generator salah mapping, dan
navigasi editor hilang; restore hijau.

Validasi lokal: `tools/check.sh` **531 passed**, 57 Kotlin files lexical sanity,
npm/editor supply-chain guard hijau, `git diff --check` hijau. CI run
`32203918119` kemudian sukses untuk commit `7d578b0`; jembatan berstatus
CI VERIFIED, belum DEVICE VERIFIED.

### 2026-08-19 — Kurasi P0 + QR optional-dependency bug: IMPLEMENTED
Commit `7e73ed5` memperkaya kartu `python-docx`, `qrcode`, dan `sympy` dengan
WHAT/WHY/HOW/WHERE/WHO, use case, dependency, risiko, lisensi/publisher,
sumber resmi, dan tanggal/bukti device. Detail Library kini benar-benar
merender field dependency yang sebelumnya terisi tetapi tidak pernah terlihat.

Audit eksekusi menemukan bug nyata di sample QR: komentar lama mengklaim
`qrcode` otomatis menarik Pillow, tetapi metadata qrcode 8.2 menempatkan Pillow
di extra `pil`, dan eksperimen clean venv `qrcode==8.2 --no-deps` membuat
`qrcode.make()` gagal `ModuleNotFoundError: PIL`. Sample diperbaiki: bila Pillow
aktif hasil PNG; bila tidak, factory pure SVG memberi `qr.svg`. Kedua jalur
dijalankan nyata: fallback SVG 6.539 byte, jalur Pillow PNG 447 byte.

Sumber resmi kurasi:
- python-docx: https://pypi.org/project/python-docx/1.2.0/ dan
  https://python-docx.readthedocs.io/en/latest/
- qrcode: https://pypi.org/project/qrcode/8.2/ dan
  https://github.com/lincolnloop/python-qrcode#usage
- SymPy: https://docs.sympy.org/latest/index.html dan
  https://docs.sympy.org/latest/explanation/best-practices.html

`cryptography` diaudit ulang melalui docs dan riwayat Git: ada wheel Chaquopy,
ARMV7-IMPORT-VERIFIED bionic311, dan sample, tetapi tidak ditemukan breadcrumb
atau laporan DEVICE TESTED spesifik. Status tetap COMPATIBLE; tidak ada
`sampleId` dari kartu.

Guard P0 terbukti merah lewat empat mutasi: whyUse hilang, dependency tak
dirender, import fallback SVG hilang, dan klaim palsu Pillow otomatis kembali.
Restore hijau. Validasi lokal: `tools/check.sh` **535 passed**, 57 Kotlin files,
supply-chain guard dan diff-check hijau. Status: IMPLEMENTED lokal; belum CI
VERIFIED untuk commit P0 dan belum DEVICE VERIFIED.

### 2026-08-19 — Gelombang 8 sample lintas-kegunaan: IMPLEMENTED
Commit `791dc9e` menambah delapan sample berdasarkan lubang audit, bukan target
jumlah:

- NumPy: indexing/slicing;
- Matplotlib: subplots PNG dengan backend Agg;
- HTTPX: API + timeout + HTTPError;
- Beautiful Soup: parsing HTML deterministik/offline;
- python-pptx: presentasi dua slide;
- TinyDB: catatan JSON persisten dengan upsert/query;
- PyOTP: secret/kode TOTP demo + peringatan produksi;
- PyYAML: `safe_load`/`safe_dump`, bukan `yaml.load` berbahaya.

Samples berubah dari 29 item/4 kategori menjadi 37 item/11 kategori tujuan.
Keranjang `Paket Populer` dihapus; NumPy dan Matplotlib punya jalur sendiri,
sisanya dikelompokkan sebagai Web & API, Office, Database, Data & Matematika,
Gambar & QR, Security, Utilities, dan Project Mini. Tidak ada sample GUI/paket
UNAVAILABLE yang ditambahkan. Sample cryptography lama tetap tampil tetapi kini
eksplisit bertuliskan belum DEVICE VERIFIED dan kartu tetap tanpa `sampleId`.

Empat kartu auto-fill (`python-pptx`, `tinydb`, `pyotp`, `pyyaml`) dinaikkan ke
kurasi tangan; HTTPX dan Beautiful Soup mendapat dependency/sumber HOW serta
link sample. Enam kartu baru terhubung ke sample lengkap.

Verifikasi runtime host:
- exact-version clean venv: HTTPX 0.27.2, BeautifulSoup 4.12.3,
  python-pptx 1.0.2, TinyDB 4.9.0, PyOTP 2.10.0, PyYAML 6.0.3 — semua jalan;
- HTTPX mendapat HTTP 200;
- output: PPTX 29.174 byte, TinyDB JSON 131 byte, YAML 85 byte;
- NumPy/Matplotlib sample jalan pada host NumPy 2.3.5/Matplotlib 3.10.9;
  versi Android exact 1.26.2/3.6.0 sudah DEVICE TESTED untuk paket lama,
  tetapi **sample baru** belum DEVICE VERIFIED pada versi exact tersebut;
- subplot PNG berhasil 28.744 byte.

Sumber resmi:
- https://numpy.org/doc/1.26/user/basics.indexing.html
- https://matplotlib.org/3.6.3/gallery/subplots_axes_and_figures/subplots_demo.html
- https://www.python-httpx.org/advanced/timeouts/
- https://www.crummy.com/software/BeautifulSoup/bs4/doc/#quick-start
- https://python-pptx.readthedocs.io/en/latest/user/quickstart.html
- https://tinydb.readthedocs.io/en/latest/intro.html
- https://pyauth.github.io/pyotp/
- https://pyyaml.org/wiki/PyYAMLDocumentation

Enam mutasi terbukti merah: timeout HTTPX dimatikan, PyYAML kembali ke unsafe
load, paket sample diturunkan dari TESTED, kategori tujuan dikembalikan menjadi
keranjang, backend Agg dihapus, dan link PyOTP diputus. Restore hijau. Validasi
lokal `tools/check.sh`: **541 passed**, 57 Kotlin files, supply-chain guard dan
diff-check hijau. CI run `32207368332` kemudian sukses untuk commit `c047adf`.
Status gelombang sample: CI VERIFIED, belum DEVICE VERIFIED.

### 2026-08-19 — Undo/Redo touch + history per-file: IMPLEMENTED
Audit menemukan `history()`/`undo()`/`redo()` sudah ada di CodeMirror tetapi
belum punya UI touch. Lebih penting: semua tab sebelumnya memakai satu
EditorState dan `setCode` replacement, sehingga history berisiko menyeberang
file. Commit `aac56cb` memperbaiki kelas masalah sebelum membuka tombol:

- satu EditorView, satu EditorState tersimpan per file;
- switch tab memakai `openDocument(id, code)`, bukan replacement pada stack yang
  sama;
- close/delete membuang state, rename memindahkan key, Clear All membersihkan
  semua state;
- callback WebView membawa document ID supaya event terlambat tidak menimpa tab
  baru;
- transform programatik menjadi satu undo group terisolasi;
- setting lint/whitespace/bracket/font diterapkan ke semua state;
- `↶`, `↷`, `?` menjadi tiga tombol terowongan editor yang selalu terlihat;
- tombol Undo/Redo redup dan nonaktif bila stack kosong; shortcut CM6 tetap ada;
- isi/secret tidak dicatat; breadcrumb hanya action + nama file.

Bundle CM6 dibangun ulang secara supply-chain-safe menjadi 472.611 byte, SHA-256
`3359bd9af25e8e7f08099ebd968018a47b4ee0ecb847ab4f86bd2832cf0bbc5a`.
Runtime test jsdom menjalankan bundle shipped dan membuktikan history main/helper
terpisah, rename mempertahankan ID, close/clear membuang history. Delapan mutasi
terbukti merah: state lama tak disimpan, callback kehilangan ID, close tidak
membersihkan state, Undo diarahkan ke Redo, transform tidak terisolasi, setting
hanya mengenai tab aktif, sort mengembalikan kode stale, dan callback file
tertutup tetap menulis.

Sumber keputusan:
- https://codemirror.net/docs/ref/#commands.history
- https://codemirror.net/docs/migration/
- https://code.visualstudio.com/api/references/vscode-api

### 2026-08-19 — AGENTS.md universal + SKILLS overlay proyek
Permintaan user untuk membuat panduan agent universal diimplementasikan dengan
nama standar root `AGENTS.md`, bukan `agent.md`. `docs/SKILLS.md` sengaja tidak
dihapus/diubah menjadi generik karena berisi fakta ZCODE yang tidak universal
(Chaquopy, ARMv7, bionic, Compose, CI/emulator). Ia kini menjadi overlay playbook
khusus proyek setelah agent membaca kontrak universal. Commit: `119a7c9`.

Sumber konvensi: https://agents.md/ dan
https://developers.openai.com/codex/guides/agents-md

Validasi gabungan: `tools/check.sh` **550 passed**, 57 Kotlin files,
supply-chain guard dan diff-check hijau. CI run `32218316691` sukses untuk
commit `ea9205d`; fondasi per-file history berstatus CI VERIFIED.

### 2026-08-19 — UAT Undo/Redo: REGRESSION FOUND, status bridge diperbaiki
UAT Infinix SMART 9 HD pada artifact run `32218316691`: per-file editor dan
fitur lain berfungsi, tetapi `↶/↷` selalu redup/nonaktif setelah user mengetik
dan menghapus teks. Screenshot 12:44 membuktikan `?` aktif sementara kedua
tombol history memakai disabled alpha.

Diagnosis kelas jalur: CodeMirror menghitung `undoDepth/redoDepth`, tetapi
status dikirim melalui method JavascriptInterface baru
`onHistoryStateChange`. Jalur baru ini tidak mengubah state Compose pada WebView
device. Fix commit `a5cbd8d` menghapus jalur status terpisah dan menggabungkan
satu event atomik pada callback `onCodeChange` yang sudah DEVICE VERIFIED:

```text
documentId + code + canUndo + canRedo
```

Dengan begitu code dan kemampuan Undo/Redo tidak dapat berbeda snapshot atau
melewati dua bridge path. Runtime jsdom atas bundle shipped membuktikan: typing
mengaktifkan Undo, Undo mengaktifkan Redo, Redo mengaktifkan Undo, dan history
main/helper tetap terpisah (`CM6_COMBINED_BRIDGE_OK`). Dua mutasi tambahan
terbukti merah: status dikembalikan ke callback terpisah dan native callback
kehilangan `canRedo`.

Bundle: 472.559 byte; SHA-256
`4169f7a706257985b384d11ea4ece1d765be83049dbe8ab2134ceb751bb7fb8d`.
Validasi lokal: `tools/check.sh` **551 passed**, 57 Kotlin files, supply-chain
guard dan diff-check hijau.

### 2026-08-19 — Undo/Redo callback fix: DEVICE VERIFIED
GitHub Actions run `32222196121` sukses (`check` + `build`) pada commit
`b0eb67e`; artifact `ZCODE-Fase12-APK` ID `9354329546`, SHA-256
`1c3b5beb6940b107853ccd3bed3e5212acac59053ec8da489aca8df34ece7c86`.
UAT INFINIX X6532C, ARMv7 (runtime diagnostics terbaru: Android 14/API 34):
user mengonfirmasi Undo/Redo kini aktif dan berfungsi seperti kontrak yang
disepakati. Regresi tombol selalu
redup dari artifact sebelumnya tertutup oleh callback gabungan
`documentId + code + canUndo + canRedo`.

Status jujur: Undo/Redo touch + history per-file **DEVICE VERIFIED** pada
perangkat/artifact di atas; bukan klaim universal seluruh WebView/ROM Android.
PR belum dibuka dan release belum dilakukan.


### 2026-08-19 — Semantic package logs: IMPLEMENTED
Commit `f95b838` memisahkan makna event dari teks/dekorasi. Producer package
sekarang mengirim `SemanticLogKind`:

```text
STEP · INFO · WARN · WAIT · OK · FAIL · STOP · RAW
```

Renderer menambahkan label yang ikut tercopy:

```text
[>] [INFO] [WARN] [WAIT] [OK] [ERR] [STOP]
```

Warna berasal dari kind, bukan pencarian emoji dalam kalimat. `Step.Log` generik
diganti `Step.Message(text, kind)`; hasil akhir memakai
`FinishResult.OK/FAIL/STOP`. Cancel install/resolve tidak lagi disamakan dengan
failure. Resolve bridge memetakan stage terstruktur ke severity. Legacy
`ExecutionEngine.startPipStream` juga mengirim `SemanticLog`; output tool mentah
memakai `RAW` tanpa label palsu.

Reader backward-compatible masih memahami status lama `✅ ❌ ⚠️ ℹ️ ⏳ 🛑 ▶`
dan label bracket, tetapi hanya bila token berada di prefix. Producer baru di
PackageEngine/Execution/Pip dilarang menghasilkan emoji status. Dengan begitu
teks user yang kebetulan mengandung simbol tidak mengubah warna/makna.

Sembilan mutasi terbukti merah: WARN dihapus, WAIT menjadi INFO, STOP kehilangan
warna, Cancel menjadi FAIL, Step.Message kehilangan kind, legacy WARN menjadi
INFO, producer emoji kembali, jalur semantic menebak teks, dan resolve Cancel
menjadi FAIL. Restore hijau.

Validasi lokal: `tools/check.sh` **559 passed**, 58 Kotlin files, supply-chain
guard dan diff-check hijau. Status: IMPLEMENTED lokal; belum CI/DEVICE VERIFIED.


### 2026-08-19 — Uninstall hardening sebelum final UAT: IMPLEMENTED
Keputusan: hardening kecil dikerjakan sebelum UAT semantic logs agar satu APK
menguji kandidat v1.0.19 yang utuh. Core resolver/download/smoke/activate tidak
dirombak.

Commit `19ef2a3`:

- menghapus telemetry `uninstall_count` ganda; PackageEngine menjadi satu owner;
- log uninstall berubah dari callback String ke `SemanticLog`;
- tombol Detail tidak lagi langsung menghapus package;
- dialog menjelaskan ZCODE belum memiliki reverse-dependency graph dan package
  lain mungkin berhenti bekerja;
- `Batal` tetap tersedia dan uninstall harus dikonfirmasi eksplisit;
- uninstall ditolak saat analyze/install/engine lain masih berjalan;
- breadcrumb `PKG_UNINSTALL_REQUEST/OK/FAIL` ditambahkan;
- tidak ada auto-clean dependency/orphan sebelum ownership graph tersedia.

Enam mutasi terbukti merah: telemetry ganda kembali, callback kembali String,
warning dependency disembunyikan, tombol menghapus tanpa konfirmasi, uninstall
dibiarkan balapan, dan breadcrumb request dihapus. Restore hijau.

Validasi gabungan semantic logs + uninstall: `tools/check.sh` **566 passed**,
58 Kotlin files, supply-chain guard dan diff-check hijau. Status: IMPLEMENTED
lokal; belum CI/DEVICE VERIFIED.

### 2026-08-19 — Swipe INSTALL MODULES: REGRESSION FOUND; roadmap final disetujui
Final UAT artifact commit `25a6a9e` membuktikan install `hashid`, install
`hashids==1.3.1`, dan uninstall `hashids` berhasil, tetapi perpindahan ke tab
Manual berulang kali diikuti force close sebelum operasi package dimulai.
Crash report runtime-proven berasal dari INFINIX X6532C, Android 14/API 34,
ABI `armeabi-v7a, armeabi`; klaim Android 12 pada catatan UAT lama harus dibaca
sebagai riwayat yang kini dikoreksi oleh bukti runtime lebih kuat.

Exception tepat berada di Compose 1.6.1
`FocusOwnerImpl.dispatchKeyEvent`: key event diterima ketika tidak ada active
focus target. Perbandingan pre/post commit `2c51250` mempersempit regresi ke
kelas topology `HorizontalPager` + lifecycle page + dua
`clearFocus(force = true)`, bukan Package Engine. Klaim swipe DEVICE VERIFIED
di atas resmi berubah menjadi **REGRESSION FOUND**.

Keputusan release v1.0.19: kembali ke tab tap-only, hapus Pager dan kedua forced
focus clear, pertahankan seluruh state input/scroll yang telah di-hoist, tanpa
upgrade Compose. Diagnostics fokus dan matriks eksperimen Pager/TextField/IME
dipisahkan menjadi pembuka v1.0.20, bukan dimasukkan ke kandidat release.
Roadmap, gate, mutation proof, UAT, rollback, dan jalur riset lengkap:
`ROADMAP_FINAL_V1019_FOCUS_STABILITY.md`.

Status awal: roadmap **DESIGNED dan disetujui**; fix saat itu **belum
IMPLEMENTED**; PR, merge, dan release belum dilakukan.

### 2026-08-19 — Tap-only focus fix: IMPLEMENTED + LOCALLY VERIFIED
`PipScreen` kembali memakai satu enum state tap-only. `HorizontalPager`,
`rememberPagerState`, opt-in Pager, `LocalFocusManager`, dan kedua
`clearFocus(force = true)` dihapus. Seluruh state Library/Manual yang penting
(input, scroll Library, scroll halaman Manual, dan scroll console) tetap
di-hoist; breadcrumb tab dipertahankan tanpa side effect focus. Package Engine,
resolver, runtime, dan versi dependency tidak diubah.

Tujuh guard menjaga topology final. Enam mutasi dibuktikan merah: Pager,
PagerState, dan clearFocus dikembalikan; mapping tab ditukar; state scroll
diturunkan dari owner; serta jalur Library → Manual diputus. Setelah restore,
focused guard hijau. Validasi lokal: `tools/check.sh` **572 passed**, 58 Kotlin
files lexical sanity, npm/editor supply-chain guard dan `git diff --check`
hijau.

Status saat itu: **IMPLEMENTED + LOCALLY VERIFIED**, tetapi UAT berikutnya
menemukan crash tetap terjadi sehingga klaim stabilitas berubah menjadi
**REGRESSION FOUND**. Belum PR/merge/release.

### 2026-08-19 — Akar focus crash ditemukan: conditional scroll menyisipkan focus target
UAT artifact `b7078e9` membuktikan tap-only belum cukup. Crash terjadi tepat
saat Backspace/Delete pada Requirement, termasuk field kosong dan sebelum
`PKG_ANALYZE_BEGIN`; Library Search dan editor aman. Perbandingan v1.0.18
`f259745` menunjukkan Compose 1.6.1, TextField, dan dynamic Install button sudah
sama. Perubahan pembeda paling awal adalah commit `292ba15`: ManualTab memasang
`verticalScroll` hanya ketika `maxHeight < 480.dp`.

Manifest memakai `adjustResize`; IME mengecilkan window dan membalik kondisi
tersebut ketika TextField sudah focused. Source Foundation 1.6.1 membuktikan
`ScrollableNode` mendelegasikan `FocusTargetModifierNode`. Jadi scroll
kondisional menyisipkan focus target baru di atas child aktif—persis Google
b/274655703, yang diperbaiki upstream dengan inisialisasi focus target baru:
https://issuetracker.google.com/issues/274655703 dan
https://android.googlesource.com/platform/frameworks/support/+/e3680a88311050c74e2411d30f2e1d054ea9cb56.

Commit `9e5aba3` mempertahankan rotate fix tanpa topology dinamis:
`verticalScroll(pageScroll)` selalu ada, sedangkan `consoleHeight` saja berubah
dan tetap minimum 220dp. Ia juga menambahkan snapshot `activeRequirement`,
mengunci perubahan draft secara logis saat operasi, memakai snapshot untuk
breadcrumb Cancel, dan menjaga TextField/Button dari toggle enabled/readOnly.
Enam mutasi focus/ownership dibuktikan merah lalu restore hijau.

### 2026-08-19 — Network verdict diperluas dari fix v1.0.18
Perbandingan membuktikan retry tiga kali dan `target_not_found` dari v1.0.18
masih hidup. Log baru membuka gap lama: `IncompleteRead` tidak retryable, dan
kegagalan PyPI+Chaquopy ditelan menjadi kandidat kosong lalu verdict palsu
`PACKAGE_NOT_AVAILABLE`. Bukti dua arah: `rich` gagal unavailable, kemudian
sukses `PKG_INSTALL_OK` pada percobaan berikut.

Commit `dbcb8d2` menambahkan retry `IncompleteRead`, kode internal
`SOURCE_NOT_FOUND` untuk 404, fallback antarsumber, dan propagasi `NETWORK` bila
semua sumber gagal dibaca. Tiga mutasi dibuktikan merah: partial read tak
diretry, 404 kembali menjadi NETWORK, dan dua transport error ditelan menjadi
unavailable. Package runtime suite **90 passed**. Full gate setelah dokumentasi:
**576 passed**, 58 Kotlin files lexical sanity, npm/editor supply-chain guard dan
`git diff --check` hijau. Status kedua commit saat itu: **IMPLEMENTED + LOCALLY
VERIFIED**, menunggu CI dan DEVICE VERIFIED; belum PR/merge/release.

### 2026-08-20 — Stable focus topology: DEVICE VERIFIED
CI run `32319191247` sukses untuk SHA `96ad556`; artifact
`ZCODE-Fase12-APK` ID `9389208656`, ukuran 44.710.395 byte, SHA-256
`7538fc8f9a1bb6d7737100f70d661317a16b6dd2b9aff38d1c9761b05bdabb41`.
UAT pada INFINIX X6532C, Android 14/API 34, ARMv7 mengonfirmasi seluruh matriks
focus: Backspace field kosong berulang, hapus sampai kosong, long-press,
Library↔Manual, portrait↔landscape, dan logical operation lock berjalan tanpa
force close. Diagnostics 104 baris menutup dengan `(belum pernah crash Java)`.

Regresi inti tertutup tanpa membatalkan rotate fix. Bukti tambahan non-regresi:
`requests` ter-resolve dan `PKG_INSTALL_OK` bersama empat dependency; kemudian
`bokeh==3.9.2` ter-resolve dan `PKG_INSTALL_OK` bersama total 16 paket termasuk
wheel native Chaquopy/ARMv7. Run Python selesai `code=0`; tab Manual, Samples,
dan Undo/Redo tetap hidup setelah instalasi berat.

Status jujur:
- stable focus topology + operation lock: **DEVICE VERIFIED**;
- resolver transport extension: **CI VERIFIED** dan device tidak regresi, tetapi
  jalur kegagalan `IncompleteRead`/dua repository putus tidak muncul pada UAT
  ini sehingga belum DEVICE VERIFIED untuk failure path tersebut;
- semantic label/warna/copy visual dan dialog uninstall `Batal` belum dibuktikan
  oleh log sesi ini;
- PR, merge, dan release belum dilakukan.

### 2026-08-20 — Cross-source specifier + Bokeh evidence correction: IMPLEMENTED lokal
Final focus UAT membuka bug resolver terpisah: metadata Bokeh 3.9.2 mencatat
`contourpy>=1.2`, tetapi plan memilih wheel Chaquopy 1.0.5. Penyebabnya bukan
Bokeh khusus: PyPI difilter specifier, sedangkan local cache dan Chaquopy masuk
ranking tanpa filter; tested priority dapat mengalahkan constraint.

Resolver kini menggabungkan semua source, memfilter tag runtime dan specifier
PEP 440, lalu baru melakukan ranking. Bila wheel runtime-compatible ada tetapi
versinya tidak memenuhi, verdict baru `DEPENDENCY_VERSION_UNAVAILABLE`
menyebut requirement dan versi tersedia. NETWORK tetap menang bila source yang
mungkin memuat versi valid gagal dibaca; mismatch ABI tetap COMPATIBILITY.

Reproduksi metadata nyata ARMv7/API34:
- `bokeh==3.9.2` → ditolak: contourpy butuh `>=1.2`, tersedia `1.0.5`;
- `bokeh==3.3.4` → plan dependency-correct 20 paket, termasuk contourpy 1.0.5,
  numpy 1.26.2, pandas 2.1.3, Pillow 11.0.0, PyYAML 6.0.3, dan Tornado 6.5.2.

Katalog Bokeh diturunkan dari TESTED menjadi COMPATIBLE; klaim 3.9.2 dibatalkan
dan Bokeh dihapus dari tested-manifest. Versi 3.3.4 hanya kandidat sampai exact
device UAT basic HTML + contour + restart lulus. Total katalog kini 230 TESTED,
9 COMPATIBLE, 16 EXPERIMENTAL, 77 UNAVAILABLE, 10 INCOMPATIBLE (342 total).

Guard: lima test resolver lintas-source dan dua guard data/generator. Enam
mutasi resolver serta tiga mutasi katalog/generator terbukti merah lalu restore
hijau. Status: **IMPLEMENTED lokal**, full gate/CI/device UAT kandidat belum.

### 2026-08-20 — Bokeh 3.3.4 dependency-correct, tetapi runtime native stale
UAT lanjutan pada INFINIX X6532C/API34/ARMv7 membuktikan dua hal yang harus
dipisahkan:

1. `bokeh==3.9.2` ditolak secara benar dengan
   `DEPENDENCY_VERSION_UNAVAILABLE`: ContourPy membutuhkan `>=1.2`, sedangkan
   runtime ZCODE hanya menyediakan 1.0.5.
2. `bokeh==3.3.4` berhasil terpasang dengan dependency yang benar. Basic HTML
   dapat dibuat pada process install yang sama, tetapi direct `import
   contourpy` dan contour plot gagal dengan:

   ```text
   ImportError: generic_type: type "FillType" is already registered!
   ```

Setelah ZCODE ditutup dari Recent Apps dan dibuka kembali, `bokeh 3.3.4`,
`contourpy 1.0.5`, `numpy 1.26.2`, dan `pandas 2.1.3` dapat diimpor; contour
plot juga berhasil. Jadi dependency/version correctness Bokeh 3.3.4 berstatus
**DEVICE VERIFIED**, sedangkan penggunaan native tepat setelah install berubah
menjadi **REGRESSION FOUND**.

Akar berada di `package_runtime/smoke.py`: smoke mengimpor extension `.so`, lalu
cleanup hanya menghapus module baru dari `sys.modules`. Shared library,
pybind11 global type registry, dan static C/C++ state tetap hidup. Import user
berikutnya menginisialisasi extension kedua kali dan menabrak registry lama.
Arbitrary native extension tidak mempunyai kontrak hot-unload yang aman;
solusinya adalah process/interpreter baru, bukan cleanup Python yang semakin
agresif.

Sumber desain:

- CPython embedded runtime/isolation:
  https://bugs.python.org/issue34309
- pybind11 embedding lifecycle:
  https://pybind11.readthedocs.io/en/stable/advanced/embedding.html
- pola helper-process relaunch ProcessPhoenix:
  https://github.com/JakeWharton/ProcessPhoenix
- batas background activity launch Android:
  https://developer.android.com/guide/components/activities/secure-bal

### 2026-08-20 — Native-runtime rebirth + Binary Rain: IMPLEMENTED lokal
Setelah diskusi UX dan persetujuan user, commit `87a1ca6`, `408b3e0`, dan
`5526c21` mengimplementasikan kontrak berikut:

- native smoke success/failure atau perubahan environment `.so` menandai
  `NativeRuntimeState` sebagai stale; uninstall native memakai kontrak sama;
- pure-Python install tidak menandai stale;
- setelah operasi native, dialog menawarkan `Nanti` atau
  `Simpan & mulai ulang`;
- jalur `Nanti` mempertahankan banner amber dan memblokir Run,
  install/update/uninstall, serta dispatch antrean package; edit, copy, save,
  dan Diagnostics tetap boleh;
- `WorkspaceViewModel.flushSaveSync(verifyAllDrafts = true)` memverifikasi
  seluruh draft terbuka dan commit workspace secara sinkron. Kegagalan save
  tidak boleh membunuh process dan UI menawarkan `Coba simpan lagi`;
- receipt minimal (`restart_required`, package, timestamp, old PID) dipersist
  sebelum helper dimulai;
- `ZcodeRebirthActivity` private (`exported=false`) berjalan di process
  `:rebirth`, memvalidasi PID, membunuh PID main lama, lalu membuka explicit
  `MainActivity` dengan `NEW_TASK | CLEAR_TASK`;
- helper hanya memanggil `finish()`, bukan `finishAndRemoveTask()`, agar task
  berisi MainActivity baru tidak ikut terhapus;
- `ZcodeApp` mendeteksi process helper, termasuk fallback API 26–27, dan
  melewati init normal CrashReporter/telemetry/Chaquopy;
- tidak memakai AlarmManager, exact-alarm permission, service background,
  ProcessPhoenix dependency, atau package-name hardcode;
- transisi memakai custom Canvas ringan ±24 FPS. Setiap trail vertikal hanya
  mengulang binary ASCII `ZCODE`:
  `0101101001000011010011110100010001000101`;
- status transisi: `Memulai ulang Python…` lalu `Menyiapkan workspace…`;
- process baru memvalidasi old PID/receipt, membersihkan stale state, kembali ke
  editor, lalu menampilkan sekali:
  `Python berhasil dimulai ulang. Program siap dijalankan.`

Permanent guard hidup di `test_native_runtime_rebirth_guards.py` dan sekarang
menjadi bagian `tools/check.sh`. Mutation proof terbukti merah untuk 13 arah:
process suffix hilang, helper exported, intent implicit, save failure tetap
melanjutkan kill, helper menjalankan init normal, process baru tidak
membersihkan stale, AlarmManager masuk, native tidak menandai stale,
pure-Python salah menandai stale, Run tidak digate, helper menghapus task,
antrean tetap terkuras saat stale, dan native sibling gagal sebelum loop
mencapai package `.so`. Restore kembali hijau.

Validasi lokal setelah final task/queue review:

```text
tools/check.sh                     : 593 passed
Kotlin lexical sanity             : 61 files passed
npm/editor supply-chain guard     : passed
git diff --check                  : passed
```

Status jujur:

```text
Native rebirth + Binary Rain      : IMPLEMENTED + LOCALLY VERIFIED
Canonical Kotlin/APK compilation  : BELUM CI VERIFIED
Automatic relaunch/task handoff   : BELUM DEVICE VERIFIED
Bokeh 3.3.4 catalog promotion     : DITAHAN sampai exact artifact UAT
PR / merge / release              : BELUM
```

UAT satu artifact wajib mencakup: native install → immediate relaunch → tab/file
pulih → identity import/basic HTML/contour; jalur `Nanti` + seluruh gate;
pure-Python install tanpa restart; save-failure tidak menutup app; uninstall
native; semantic log visual/copy; dialog uninstall `Batal`; dan Diagnostics
tanpa `FATAL_JAVA` untuk rebirth yang disengaja.

### 2026-08-20 — CI pertama rebirth: REGRESSION FOUND sebelum artifact
Push SHA `22d0cfb` memicu GitHub Actions run `32344397545`. Job `check` sukses,
tetapi job `build` gagal pada step `Build Debug APK`; verify/upload dilewati dan
tidak ada artifact. Halaman publik hanya membuka annotation exit code 1 dan
menyembunyikan raw compiler log di balik autentikasi. Warning cache Gradle 400
dan deprecation action ada, tetapi step setup tetap sukses sehingga bukan
verdict akar build.

Audit diff Kotlin baru menemukan satu import yang berbeda dari seluruh pola
project: `import androidx.compose.foundation.layout.weight` di `PipScreen`.
Pada Compose yang dipakai ZCODE, `weight` adalah member extension
`RowScope`/`ColumnScope`; file ini sudah memakai `Modifier.weight` bertahun-tahun
tanpa top-level import. Import baru tersebut dihapus. Guard permanen memindai
semua Kotlin dan menolak top-level import yang sama. Mutation proof: import
dikembalikan → guard merah; restore → hijau.

Status koreksi: **IMPLEMENTED + LOCALLY VERIFIED (593 passed)**. Karena raw log
tidak tersedia, penyebab compiler diberi confidence tinggi tetapi belum boleh
disebut terbukti sampai CI kedua hijau. PAT push pertama sudah dihancurkan dan
remote/config/workspace diverifikasi bebas credential; push koreksi membutuhkan
PAT baru/sementara.

### 2026-08-20 — CI kedua hijau, artifact ditahan oleh audit native dependency aktif
Koreksi import pada SHA `38f401f` membuat GitHub Actions run `32346726238`
sukses untuk job check dan build. Artifact `ZCODE-Fase12-APK` ID `9398359511`,
ukuran archive 44.735.503 byte, digest archive
`sha256:8c1bc5784312096fe03d80470d015b29d8fd704a5ab0e8889ae7506fe7b3bae5`.
Ini menguatkan diagnosis import CI pertama.

Artifact sengaja **tidak dinaikkan menjadi kandidat UAT**. Audit alur sebelum
meminta user download menemukan kelas yang belum tertutup: transaksi package
pure dapat mengimpor dependency native yang sudah aktif. Contoh kelas:
seaborn pure-Python mengimpor NumPy/Matplotlib. Karena `.so` tidak berada di
staging root seaborn, detektor staging dapat menganggap transaksi pure padahal
smoke benar-benar memasukkan extension native ke `sys.modules`; cleanup lalu
menghapus module Python tetapi registry C/C++ tetap hidup.

Fix berikutnya membuat `smoke.py` mencatat `loaded_native_modules` dari delta
`sys.modules` sebelum cleanup, berdasarkan `module.__file__` extension `.so`.
`SmokeTestRunner` membawa evidence itu ke Kotlin dan PackageEngine menandai
stale bila staging berisi `.so` **atau** smoke benar-benar memuat extension
native dari dependency aktif. Negative control package pure tanpa extension
tetap menghasilkan daftar kosong dan tidak meminta restart.

Guard runtime + Kotlin menjaga kedua sisi kontrak. Dua mutasi tambahan terbukti
merah: smoke membuang daftar extension termuat, dan engine mengabaikan daftar
tersebut. Restore hijau; full local gate **594 passed**, 61 Kotlin files,
supply-chain guard dan diff-check hijau. Status fix: IMPLEMENTED + LOCALLY
VERIFIED; membutuhkan CI ketiga sebelum satu APK kandidat diberikan ke user.

### 2026-08-20 — Artifact final: native rebirth dan release gate DEVICE VERIFIED
GitHub Actions run `32348956505` sukses untuk check dan build pada SHA
`efa56ad3370e2f69da4f069d614a0a466f0de1be`. Artifact
`ZCODE-Fase12-APK` ID `9399175936`, ukuran archive 44.735.650 byte, digest
`sha256:448af10bbfb0c3e7e8a833e2452dd08ae62852d4f6194deec6596135fff4a37b`.
UAT dijalankan pada INFINIX X6532C, Android 14/API34, ABI
`armeabi-v7a,armeabi`.

Rantai immediate restart terbukti dari breadcrumb:

```text
WORKSPACE_FLUSH_OK
RUNTIME_RESTART_REQUEST | oldPid=26441
REBIRTH_HELPER_START    | helperPid=28225
APP_START
RUNTIME_RESTART_OK      | previousPid=26441 newPid=28569
PYTHON_START_OK
PREWARM_OK
```

PID lama, helper, dan main baru berbeda. Binary Rain terlihat tanpa launcher;
file dan tiga tab kembali utuh; pesan `Python berhasil dimulai ulang. Program
siap dijalankan.` muncul. Diagnostics berakhir dengan `(belum pernah crash
Java)`.

Pada process baru:

- direct import Bokeh 3.3.4, ContourPy 1.0.5, NumPy 1.26.2, dan Pandas 2.1.3
  berhasil;
- standalone HTML berhasil;
- contour HTML berhasil **646.935 byte**, exit code 0;
- `generic_type: FillType already registered` tidak kembali.

Dua kegagalan contour sebelum hasil tersebut berasal dari script UAT agent yang
salah: `levels` diberi integer lalu visual `fill_color/line_color` tidak
diberikan. Kesalahan diakui sebagai invalid test input, bukan regresi ZCODE.

Jalur `Nanti` juga DEVICE VERIFIED: banner amber bertahan; Run dan package
mutation diblokir dengan pesan jelas; editor/tab tetap dapat dipakai; tombol
banner menjalankan Binary Rain/relaunch; Run kembali menghasilkan
`Hello, ZCODE!` exit 0.

Negative control pure-Python dan final UX:

- Colorama 0.4.6 diinstall dari cache dengan smoke
  `0 .so staging, 0 extension native termuat`;
- tidak ada dialog/banner restart dan `PURE_OK 0.4.6` exit 0;
- semantic label `[>]`, `[INFO]`, `[WAIT]`, `[OK]`, dan cancel `[STOP]`
  terlihat serta tersalin;
- dialog uninstall menjelaskan reverse dependency;
- `Batal` mempertahankan Colorama installed;
- konfirmasi Uninstall menghapusnya, lalu reinstall berhasil.

Keputusan user: breadcrumb native yang ramai dipertahankan. Diagnostics adalah
black-box recorder bagi user tanpa PC; raw evidence lebih penting daripada log
yang kosmetik selama tidak memuat secret/isi kode dan tidak menyebabkan ANR.

Status final batch:

```text
Native rebirth/Binary Rain         : DEVICE VERIFIED
Workspace/tab restore              : DEVICE VERIFIED
Nanti + Run/package gates          : DEVICE VERIFIED
Pure-Python negative control       : DEVICE VERIFIED
Semantic copy/STOP                 : DEVICE VERIFIED
Uninstall Batal + confirm          : DEVICE VERIFIED
Bokeh 3.3.4 basic + contour        : DEVICE VERIFIED
Bokeh catalog status               : dipromosikan ke TESTED @3.3.4
Release status                     : RELEASE CANDIDATE; belum merge/released
```

Ringkasan kandidat hidup di `docs/RELEASE_NOTES_V1.0.19.md`. Promosi katalog
Bokeh dijaga empat mutasi: status diturunkan lagi, manifest diganti 3.9.2, bukti
contour dihapus, dan generator menghidupkan klaim lama; semuanya merah lalu
restore hijau. Full gate setelah data/dokumentasi: **594 passed**, 61 Kotlin
files, supply-chain guard, generator check, py_compile, dan diff-check hijau.

PR `arena/v1019-fondasi → main` boleh dibuka sesuai persetujuan user, tetapi
merge dan distribusi release tetap membutuhkan keputusan terpisah.
