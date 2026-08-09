# ZCODE — Zabacode Kotlin Edition

ZCODE adalah Zabacode yang dibangun ulang dengan **Kotlin + Android Native**.

**Filosofi:** *full free, tanpa premium lock* — untuk pelajar, pemburu hobi coding, dan developer dengan perangkat terbatas & budget tipis. Semua fitur bebas dipakai siapa saja, offline-first, dan sebisanya tetap ringan di HP kentang. 💚

Menggabungkan **kesederhanaan Pydroid**, **detail arsitektur VS Code**, dan **optimasi touch Acode** menjadi editor Python mobile offline-first dengan true-black OLED. Referensi proyek: [muzape28-blip/ZABACODE](https://github.com/muzape28-blip/ZABACODE).

---

## 🚀 Fitur yang Sudah Dibangun (Fase 1 & Fase 2)

### 🎨 1. Desain Total & Estetika

- **True-Black OLED (`#050806`)** — ruang editor dan terminal output selalu hitam legam, hemat baterai AMOLED dan nyaman untuk begadang. Warna ini **tidak berubah** walau tema diganti.
- **3 Tema Sinkron** — Retro Green (default, identitas Zabacode), Dracula, Tokyo Night. Tema menyentuh bagian dekoratif (topbar, drawer, dialog, tombol) secara harmonis; pembatas visual dibuat soft (opacity rendah), bukan garis tegas.
- **Tab Bar Multi-File** — font 12, **long-press/hold tab untuk menutup file** (tanpa tombol × yang rawan salah pencet).
- **QuickTools & FAB ▶** — chips bulat (`Tab`, `:`, `;`, `'`, `#`, `(`, `)`, `[`, `]`, `def`, `return`, `import`) di atas handle, FAB Run melayang di atasnya.
- **Topbar (redesign 2026-08)** — `≡` (drawer), nama file (tap → dialog Rename/Delete), ikon folder (import file dari file manager HP via SAF, salin ke workspace), kaca pembesar polos (palette), plus (file baru). Ikon topbar kini **vektor polos ber-tint mengikuti tema** (bukan emoji — seragam di semua merk HP); FAB ▶ idem.

### 📁 2. File Manager & Persistensi Workspace

- **CRUD penuh** di `filesDir/` internal (anti `files/files` double nesting, `secure_filename`, `MAX_FILE_BYTES` 512KB, `MAX_FILENAME_LEN` 128, anti traversal).
- **Frictionless creation** — tap `+` langsung membuat `untitled_N.py`; ganti nama belakangan via drawer.
- **Rename & Delete** dengan dialog konfirmasi elegan.
- **Workspace Recovery** — isi file tersimpan otomatis tiap perubahan + daftar tab & file aktif dipersist, sehingga alur kerja pulih walau aplikasi ditutup paksa / di-swipe dari Recent Apps.

### 💻 3. Terminal Interaktif Full-Screen (Python di HP — Chaquopy 3.11)

- **Runtime Python di-embed (Chaquopy 3.11)** — APK kini membawa interpreter Python arm64 + armeabi-v7a, jadi `▶ Run` & `input()` **benar-benar berjalan di HP ARMv7** (dual-backend: Chaquopy in-process di Android, `python3` subprocess untuk dev/desktop).
- **Pindah layer** — `▶` membuka terminal full-screen (bukan panel); `◀ Back` di pojok kiri atas kembali ke editor.
- **Ketik langsung** — sentuh terminal untuk memunculkan keyboard; Enter mengirim baris ke stdin (tanpa kotak stdin / tombol Send). `input()` di script langsung berfungsi.
- **Ctrl+C** — tombol merah di toolbar bawah: deterministik untuk script yang nge-blok di `input()` (KeyboardInterrupt), best-effort interrupt thread worker untuk loop CPU.
- **Guard output** — `MAX_OUTPUT_CHARS` 256KB, cap antrian input 10k, lifetime 120s; proses dibersihkan saat keluar terminal.
- **cwd = folder workspace** — `plt.savefig("out.png")` / `open("data.txt")` relatif bekerja seperti di desktop.

### 📦 4. Pip Package Manager Layer

- **Real-time log streaming** — `Settings → Pip` → ketik nama package → log unduhan/instalasi/traceback mengalir langsung (pip in-process Chaquopy di Android).
- **Guard** — validasi nama package (anti shell injection) + cap log.

### 🔍 5. Command Palette & Quick Open (Fase 2)

- Tombol `🔍` di topbar (akses jempol, tanpa keyboard fisik).
- Ketik nama file → Quick Open; awali dengan `>` → perintah (Beautifier, Auto-Import, Duplicate, Comment Toggle, Pip, About).

### ⚡ 6. Real-time Syntax Diagnostic (Fase 2)

- Scanner offline single-pass: strip komentar & string (aman untuk `print(' :)')`), deteksi string tak tertutup, keseimbangan `() [] {}` dengan nomor baris.
- Debounce 800ms + pembatalan job lama → tanpa race, UI tetap 60fps.

### 🔧 7. Plugin Transformasi Kode (Fase 2)

- **Beautifier Pro** — spasi operator rapi dengan prioritas longest-first (`->`, `**`, `//=`, `<<`, ...); string & komentar tidak pernah disentuh; unary (`-1`, `*args`, `~x`) & unpacking (`**kwargs`) aman.
- **Optimize Auto-Imports** — mendeteksi pemakaian `os, sys, math, json, time, random, datetime` dan menambahkan import bila belum ada.
- **Duplicate Active Line** & **Toggle Line Comment** — dieksekusi langsung di editor (CodeMirror 6).
- **Clear All Drafts & Files** — dengan dialog konfirmasi.

### 🧩 Editor

- **CodeMirror 6 bundled offline** (migrasi penuh dari Ace 1.44.0, 2026-08 — `docs/MIGRASI_CM6.md`; tanpa CDN, single-file bundle esbuild target es2018, armv7/arm64/x86_64 aman) di WebView `file://` — font 12px, gutter line numbers, tema tomorrow-night-eighties deklaratif di atas OLED, Python syntax via Lezer parser, panel Find built-in. Sumber bundle: `editor-src/` (`npm ci && npm run build`).
- **Bridge `addJavascriptInterface`** — tanpa loopback HTTP (kelas bug F-01/S-27/C-50 dari Zabacode terhapus).

### 🧩 Batch Anti-Sepi (2026-08) — plugins, pencarian, autocomplete

Dokumen lengkap: `docs/PLAN_BATCH_ANTI_SEPI.md`.

- **Drawer TOOLS expandable (redesign 2026-08)** — satu kotak: 10 plugin (±3 baris scrollable, tap baris = eksekusi, switch = aktif/nonaktif, persist SharedPreferences satu sumber) + toggle **Symbol bar** + **THEME** cycle satu tombol (tap-tap berganti tema, nama tema aktif selalu terlihat) + **Clear All** (merah, konfirmasi). Di luarnya: INSTALL MODULES, SAMPLES (halaman baru 2 level, 11 contoh yang pasti jalan), About & Contribute di paling bawah. Seksi NAVIGATION / EDITOR / SELECT THEME / FILES MANAGER dipangkas (lihat `docs/RENCANA_UPDATE_2026_08.md`).
- **3 transform port ZABACODE** (GPLv3, same author — header provenance di `zcode_plugins.py`): Smart Docstring Generator, Type Hint Generator, Find Duplicate Lines — via Python/Chaquopy in-process (dual-backend subprocess untuk dev).
- **TODO Extractor** — kumpulkan TODO/FIXME/HACK, tap → lompat ke baris.
- **Snippet Pack** — Flask / BS4 / AsyncIO / REST → file baru (template Zabacode).
- **🔍 dua fungsi (redesign 2026-08)** — chips `[Line][Find]`: **Go to Line** ber-validasi (input → OK → loncat; nomor melebihi jumlah baris → peringatan receh muncul di bawah input, dialog tetap kebuka) & **Find** cari kata di file aktif (hasil `L<n>:`, tap → lompat). Prefix power-user `>` (perintah plugin) & `:` tetap hidup.
- **Autocomplete kasta 1+2** — kata dalam dokumen + keyword + builtins Python + item snippet; OLED; maks 5 kandidat; nol kandidat = nol popup. (Kasta 3 jedi/LSP = backlog.)
- **FAB ▶ syntax-aware** — merah `#FF4B4B` saat ada error syntax tapi **tetap bisa run** (sinyal tanpa otoriter).
- **Bridge baru `gotoLine(n)`** — satu fungsi tiga pemakai (mode Line, hasil Find, TODO).

---

## 🎯 Target Pengembangan Masa Depan (Roadmap)

> 📘 **Rencana Update terkini & keputusan desain (2026-08): lihat [`docs/RENCANA_UPDATE_2026_08.md`](docs/RENCANA_UPDATE_2026_08.md)** — berisi redesign sidebar/topbar/palette (batch ini), halaman SAMPLES, strategi "App Mode" (GUI via Flask+WebView, terbukti di ZABACODE/ZABAWHEELS), catatan jujur keterbatasan GUI native (Pintu A), dan ide fork **ZPLAY** (pygame/kivy lewat buildozer) yang diarsip untuk masa depan.

- [ ] **App Mode (GUI via Flask + WebView)** — script `# ZCODE:WEBAPP` → preview web app full-screen di dalam ZCODE; flag UAT: **Kalkulator Modern**. [batch berikutnya]
- [ ] **Visual Problems Panel** — daftar error sintaksis dalam panel bawah yang terorganisir (saat ini: banner warning real-time).
- [ ] **Matplotlib Inline Image** — `plt.savefig("out.png")` tampil inline/expandable di terminal (baseline dedup + skip >8MB).
- [ ] **10 Tema lengkap + CRT Scanlines toggle**.
- [ ] **Encrypted Keystore UI + Privacy Toggle** (persist draf teks polos off).
- [ ] **Alpine proot terminal** (apk add, git) — Zmux pending, tidak dibundle.
- [ ] **LSP Python (jedi) → autocomplete ala VS Code**.
- [ ] *(Garasi — jujur tanpa janji jadwal)* **ZPLAY**: saudari ZCODE berbasis buildozer/p4a untuk menjalankan sampel pygame/kivy (tkinter & Qt tetap tidak memungkinkan, bahkan di situ).

---

## 🛠️ Build & Test

```bash
# Test struktural + anti-regresi (tanpa JDK/SDK)
bash tools/check.sh
python -m pytest test_zcode_fase0.py test_zcode_fase1.py test_zcode_fase3.py -v

# Build APK (butuh JDK 17 + Android SDK; CI mengerjakan ini)
gradle assembleDebug
```

Guard yang dijaga: CodeMirror 6 bundled asli (bukan stub, kontrak bridge lengkap), tanpa unverified SSL, `taskAffinity=com.zaba.zcode singleTop allowBackup=false`, `MAX_CODE_BYTES` 512KB, `MAX_INTERACTIVE_QUEUE` 10k, SIGINT asli, `≡` tiga garis (bukan kata lain), topbar faded grey `#3A4452` referensi.

Catatan jujur: eksekusi Python memakai **Chaquopy 3.11 in-process** di Android (bukan PTY OS-level; terminal ini ala Pydroid — output teks + ketik langsung + Enter + Ctrl+C). PTY penuh (escape sequence, `apk add`, git) tetap menjadi target Fase 3 via ZMUX/proot. Build APK diverifikasi oleh CI (sandbox tanpa JDK/Android SDK).

---

## 🤝 Contribute & Feedback

Lapor bug / saran langsung lewat [GitHub Issues](https://github.com/muzape28-blip/ZCODE/issues) — tanpa telemetri, tanpa iklan, offline-first.
