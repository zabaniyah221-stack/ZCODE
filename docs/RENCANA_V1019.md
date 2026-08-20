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
UAT user pada Infinix SMART 9 HD, ARMv7, Android 12, APK v1.0.19 commit
`bf5e7ef` (CI run `32196030398`, conclusion `success`): user mengonfirmasi
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
UAT Infinix SMART 9 HD, ARMv7, Android 12: user mengonfirmasi Undo/Redo kini
aktif dan berfungsi seperti kontrak yang disepakati. Regresi tombol selalu
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
`git diff --check` hijau. Status kedua commit: **IMPLEMENTED + LOCALLY
VERIFIED**, menunggu CI dan DEVICE VERIFIED; belum PR/merge/release.
