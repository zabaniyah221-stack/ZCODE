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

Audit snapshot dicatat di `AUDIT_LIBRARY_SAMPLES_V1019.md`. Temuan utama:
231 paket TESTED tetapi baru 19 kartu TESTED berkedalaman kurasi tangan; 29
sample runnable dengan 12 requirement package unik; kategori yatim dan enam
selisih manifest perlu diklasifikasi; relasi Detail Library → sample lengkap
belum memiliki schema. Status: audit COMPLETED, prioritas DESIGNED, konten baru
belum IMPLEMENTED.
