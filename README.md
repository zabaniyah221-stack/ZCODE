# ZCODE — Zabacode Kotlin Edition

ZCODE adalah Zabacode yang dibangun ulang dengan **Kotlin + Android Native**.

Menggabungkan **kesederhanaan Pydroid**, **detail arsitektur VS Code**, dan **optimasi touch Acode** menjadi editor Python mobile offline-first dengan true-black OLED. Referensi proyek: [muzape28-blip/ZABACODE](https://github.com/muzape28-blip/ZABACODE).

---

## 🚀 Fitur yang Sudah Dibangun (Fase 1 & Fase 2)

### 🎨 1. Desain Total & Estetika

- **True-Black OLED (`#050806`)** — ruang editor dan terminal output selalu hitam legam, hemat baterai AMOLED dan nyaman untuk begadang. Warna ini **tidak berubah** walau tema diganti.
- **3 Tema Sinkron** — Retro Green (default, identitas Zabacode), Dracula, Tokyo Night. Tema menyentuh bagian dekoratif (topbar, drawer, dialog, tombol) secara harmonis; pembatas visual dibuat soft (opacity rendah), bukan garis tegas.
- **Tab Bar Multi-File** — font 12, **long-press/hold tab untuk menutup file** (tanpa tombol × yang rawan salah pencet).
- **QuickTools & FAB ▶** — chips bulat (`Tab`, `:`, `;`, `'`, `#`, `(`, `)`, `[`, `]`, `def`, `return`, `import`) di atas handle, FAB Run melayang di atasnya.
- **Topbar** — `≡` (drawer), nama file + lokasi, `🔍` (Command Palette), `+` (file baru).

### 📁 2. File Manager & Persistensi Workspace

- **CRUD penuh** di `filesDir/` internal (anti `files/files` double nesting, `secure_filename`, `MAX_FILE_BYTES` 512KB, `MAX_FILENAME_LEN` 128, anti traversal).
- **Frictionless creation** — tap `+` langsung membuat `untitled_N.py`; ganti nama belakangan via drawer.
- **Rename & Delete** dengan dialog konfirmasi elegan.
- **Workspace Recovery** — isi file tersimpan otomatis tiap perubahan + daftar tab & file aktif dipersist, sehingga alur kerja pulih walau aplikasi ditutup paksa / di-swipe dari Recent Apps.

### 💻 3. Terminal PTY Interaktif Full-Screen

- **Pindah layer** — `▶` membuka terminal full-screen (bukan panel); `◀ Back` di pojok kiri atas kembali ke editor.
- **Ketik langsung** — sentuh terminal untuk memunculkan keyboard; Enter mengirim baris ke stdin proses Python (tanpa kotak stdin / tombol Send). `input()` di script langsung berfungsi.
- **Ctrl+C asli (SIGINT)** — tombol merah di toolbar bawah mengirim `kill -INT <pid>` → `KeyboardInterrupt` yang bisa ditangkap script (bukan SIGKILL).
- **Guard output** — `MAX_OUTPUT_CHARS` 256KB, aliran output di-cap; proses dibersihkan saat keluar terminal.

### 📦 4. Pip Package Manager Layer

- **Real-time log streaming** — `Settings → Pip` → ketik nama package → log unduhan/instalasi/traceback mengalir langsung.
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
- **Duplicate Active Line** & **Toggle Line Comment** — dieksekusi langsung di Ace editor.
- **Clear All Drafts & Files** — dengan dialog konfirmasi.

### 🧩 Editor

- **Ace Editor 1.44.0 asli, bundled offline** (tanpa CDN) di WebView `file://` — font 12px, gutter line numbers, theme tomorrow-night-eighties di atas OLED.
- **Bridge `addJavascriptInterface`** — tanpa loopback HTTP (kelas bug F-01/S-27/C-50 dari Zabacode terhapus).

---

## 🎯 Target Pengembangan Masa Depan (Roadmap)

- [ ] **Visual Problems Panel** — daftar error sintaksis dalam panel bawah yang terorganisir (saat ini: banner warning real-time).
- [ ] **Matplotlib Inline Image** — `plt.savefig("out.png")` tampil inline/expandable di terminal (baseline dedup + skip >8MB).
- [ ] **Runtime Python di HP (Chaquopy 3.11 embed)** — arm64 + armeabi-v7a; saat ini eksekusi `python3` berjalan penuh di lingkungan dengan Python tersedia, runtime on-device menyusul.
- [ ] **10 Tema lengkap + CRT Scanlines toggle**.
- [ ] **Encrypted Keystore UI + Privacy Toggle** (persist draf teks polos off).
- [ ] **Alpine proot terminal** (apk add, git) — Zmux pending, tidak dibundle.
- [ ] **LSP Python (jedi) → autocomplete ala VS Code**.

---

## 🛠️ Build & Test

```bash
# Test struktural + anti-regresi (tanpa JDK/SDK)
bash tools/check.sh
python -m pytest test_zcode_fase0.py test_zcode_fase1.py -v

# Build APK (butuh JDK 17 + Android SDK; CI mengerjakan ini)
gradle assembleDebug
```

Guard yang dijaga: Ace 1.44.0 bundled asli, tanpa unverified SSL, `taskAffinity=com.zaba.zcode singleTop allowBackup=false`, `MAX_CODE_BYTES` 512KB, `MAX_INTERACTIVE_QUEUE` 10k, SIGINT asli, `≡` tiga garis (bukan kata lain), topbar faded grey `#3A4452` referensi.

---

## 🤝 Contribute & Feedback

Lapor bug / saran langsung lewat [GitHub Issues](https://github.com/muzape28-blip/ZCODE/issues) — tanpa telemetri, tanpa iklan, offline-first.
