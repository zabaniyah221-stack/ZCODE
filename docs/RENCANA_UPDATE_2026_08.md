# 📘 RENCANA UPDATE — 2026-08 (arsip diskusi & keputusan)

Dokumen ini mengarsipkan rangkaian diskusi desain 2026-08 (batch "UI Redesign +
SAMPLES") beserta alasannya, supaya keputusan tidak hilang dan kontributor
paham *kenapa* arsitekturnya begini. Tiga aturan kerja batch ini:

1. **Honest about everything, even our weakness.**
2. **Be meticulous in everything, no matter how small, to minimize edge case.**
3. **Build for the user, not for ego** — fitur yang dibangun adalah yang
   berguna/nyata dipakai orang di perangkat target (HP ampas ARMv7), bukan yang
   sekadar keren, memuaskan kebanggaan teknis si pembuat, atau menambah
   kompleksitas tanpa manfaat user. Ini menguatkan banyak keputusan: menolak
   minimap (makan daya/layar), tidak mengklaim "semua PyPI" di LIBRARY,
   mempertahankan latar terminal hitam OLED (keterbacaan), dan tidak memaksakan
   GUI native di inti ZCODE (ranah ZPLAY, bukan gengsi membongkar arsitektur).

---

## 1. Redesign UI (batch ini — SELESAI dikerjakan)

### Topbar
`≡ | nama_file (tap → Rename/Delete) | 📁 | 🔍 | +`

- **Ikon polos, bukan emoji.** `ZIcons` (`ui/components/ZIcons.kt`) adalah ikon
  vektor yang digambar manual dari path 24dp — tanpa dependensi material-icons
  (APK tidak membengkak). Di-tint `onSurface` → otomatis mengikuti warna tema dan
  seragam di semua merk HP (emoji beda-beda bentuknya per OEM).
- **`≡` tetap glyph teks** — guard `tools/check.sh` mewajibkan karakter ini ada
  (legacy guard "bukan kata lain"), jadi tidak disentuh.
- **📁 = file manager HP (SAF).** Keputusan: file yang dipilih **di-import copy**
  ke workspace internal (file asli tidak diubah; semua fitur Run/plugins langsung
  jalan). Filter `text/*`. Edge case yang dijaga VM (`importExternalFile`):
  nama bentrok → `nama_N.py` unik; >512KB → ditolak sopan; konten biner / bukan
  UTF-8 → pesan jelas; stream gagal dibuka → pesan jelas.
- Tap nama file → dialog **Rename/Delete** (pengganti seksi FILES MANAGER yang
  dihapus, supaya fitur tidak mati).

### Palette 🔍 — dua fungsi saja
- Chips tinggal **Line** & **Find** (label polos).
- Go to Line: input → **OK** → loncat. Validasi: kosong / bukan angka / di luar
  1..N menampilkan pesan di bawah input, **dialog tidak ditutup** (user langsung
  koreksi). Tone receh disepakati: *"Baris 20 nggak ada njiir — file lo cuma 10
  baris 😭"*.
- Mode rahasia **`>` (perintah plugin)** dan prefix **`:` tetap hidup** (keputusan
  user). Mode File (quick-open) dibuang — pindah file via tab bar/ikon folder.

### Sidebar (drawer) — struktur final
```
ZCODE                                     [logo]
INSTALL MODULES            (ex "Pip Package Manager")
SAMPLES                    (→ halaman baru 2 level)
──────────────────────────
TOOLS           (ex "🧩 PLUGINS", polos tanpa emoji)
 └ expand → ┌───────────────────────────────┐
            │ 10 plugin + switch (scroll ±3)│
            │ Symbol bar              [sw]  │
            │ THEME ▸ <nama tema>  (cycle!) │
            │ Clear All Drafts & Files (merah) │
            └───────────────────────────────┘
──────────────────────────
About & Contribute         (paling bawah)
```
- Seksi **NAVIGATION / EDITOR / SELECT THEME / FILES MANAGER** dihapus total.
- THEME = **satu baris cycle** (`vm.cycleTheme()` wraparound RETRO → DRACULA →
  TOKYO_NIGHT → …). Nama tema aktif selalu terlihat agar user tidak menebak.
- Plugin list tetap di area scroll-nya sendiri; Symbol bar/THEME/Clear All di luar
  scroll (anti scroll-dalam-scroll).

### SAMPLES (halaman baru, 2 level ala Pydroid)
- `ui/samples/SamplesScreen.kt` + `core/samples/SampleLibrary.kt` + 11 file di
  `assets/samples/*.py`.
- Tap item → `vm.createSampleFromAsset()` → file baru unik → kembali ke editor
  dengan tab baru terbuka.
- **Isi v1 (semua dipastikan jalan di Chaquopy + terminal interaktif ZCODE):**
  - **Basics (8):** Hello World, Text Input, Simple Math, Functions (kuadrat),
    For Loop (faktorial), While Loop (Tebak Angka 🎯), Generators, Dictionaries.
  - **Numpy (2):** Array Basics, Quick Stats (butuh install dulu).
  - **Web (1):** Fetch JSON (urllib stdlib, butuh internet).
- Sample disimpan sebagai `.py` asli di assets (bukan string Kotlin) supaya
  `test_zcode_fase3.py` bisa `py_compile` semuanya tiap commit.
- Judul bahasa Inggris + deskripsi Indonesia santai.

---

## 2. "Apakah ZCODE bisa punya GUI yang real?" — analisis jujur tiga pintu

Konteks: target GUI ala Pydroid (Kivy/Pygame/Tkinter/Qt). ZCODE menjalankan
Python via **Chaquopy in-process** — arsitektur tanpa surface GUI; Chaquopy
secara resmi tidak mendukung toolkit GUI tersebut. Tiga pintu yang dianalisis:
**Pintu A** (port native), **Pintu B** (GUI via web), **Pintu C** (grafik via gambar).

| Pintu | Isi | Status buat ZCODE |
|---|---|---|
| **A. Port native (SDL2/Qt/Tk)** | Runtime kustom ala Pydroid (C++/NDK, port library) | ❌ Jutaan unit usaha (bertahun-tahun), alasan Pydroid closed-source. Bukan batasan "ZCODE" — batasan fisik platform untuk library-lawas itu |
| **B. GUI via Web (Flask + WebView)** — "App Mode" | Script Flask → preview full-screen WebView di dalam ZCODE | ✅ **STRATEGI GUI ZCODE.** Pola sama yang dipakai ZABACODE (`flask==2.3.3`, full webview Ace) & ZABAWHEELS/ZMUX (`flask+waitress`, xterm.js, websockets) — terbukti di keluarga ini |
| **C. Grafik via gambar** | Matplotlib/Pillow jalan → hasil PNG ditampilkan | ✅ Roadmap "Matplotlib Inline Image" (sudah tercatat sebelumnya) |

Catatan validasi empiris (2026-08, perangkat Infinix Smart 9 HD ARMv7): sampel
Kivy/Pygame/Tkinter Pydroid memang JALAN di perangkat terbatas — membuktikan bahwa
yang menentukan bukan device, melainkan **runtime di dalam app**. Runtime Pydroid
proprietary → tidak bisa dipinjam. User diminta memahami: kalkulator web yang lancar
berarti jalur **B** terbukti — bukan berarti library GUI native (Kivy dsb) ikut jalan.

## 3. Ide ter-simpan: **ZPLAY** (garasi)

Fork saudari (repo baru) berbasis **buildozer/python-for-android** untuk menjalankan
sampel **pygame & kivy** (resep resmi tersedia). Basis natural: **ZABACODE**
(sudah p4a + IDE lengkap), BUKAN ZCODE (Kotlin/Chaquopy — jangan bongkar rumah yang
rapi). Jujur dicatat: bahkan lewat p4a, **tkinter & PySide/Qt tetap di luar
jangkauan** (Pydroid menambal Tk sendiri). Biaya: resep native mudah pecah di versi
baru, siklus CI lambat, APK +40–80MB. Tidak ada janji jadwal — disimpan, bukan dibuang.

## 4. Strategi testing (3 lapisan)

1. **Sandbox (agent):** `pytest fase0/1/3` + `bash tools/check.sh` +
   `py_compile` seluruh sample assets. Kotlin **tidak bisa dikompilasi di sandbox**
   (tanpa JDK/SDK) — kejujuran kelemahan; CI lah hakim kompilasi APK.
2. **Repo permanen:** `test_zcode_fase3.py` (guard redesign, kontrak dua ujung,
   sinkron katalog↔assets) menyusul gaya `fase1`. CI resmi memanggil `fase0` +
   `check.sh`; menambah fase3 ke job `check` cukup 1 baris edit `build.yml`
   (lihat §5).
3. **HP user (UAT):** artifact CI `ZCODE-Fase12-APK` → install → checklist UAT.
   Untuk App Mode nanti, sampel uji resmi = **Kalkulator Modern** (bukan hello
   world) — mengetes layout, multi-event, state server, edge case ÷0 sekaligus.

## 5. Kenapa `.github/workflows/*` tidak tersentuh (dan tidak perlu)

Semua perubahan batch ini hidup di luar `.github/` — CI yang ada (`build.yml`)
sudah memadai (pin JDK17, Python 3.11 Chaquopy, Gradle 8.5; job check =
pytest + check.sh; upload artifact APK). Batasan mengubah file workflow bukan
kekurangan konfigurasi Arena: GitHub mewajibkan kredensial ber-scope `workflow`
untuk push perubahan CI (perlindungan supply-chain). Bila kelak perlu mengubah
workflow (mis. menambahkan fase3 ke job check), siapkan teksnya dan edit manual
via web editor GitHub (login pemilik repo punya scope itu). Protokol CI: merah →
ambil log asli (`gh run view --log-failed`); log tak terbaca → lapor, **jangan
pernah menebak**.

## 6. Checklist UAT batch ini (untuk pemilik perangkat)

- [ ] Topbar: ikon folder/search/plus/FAB berbentuk ikon polos, senada tema
- [ ] Ganti THEME (3× tap) → ikon & drawer ikut warna tema baru
- [ ] Tap nama file di topbar → Rename & Delete bekerja
- [ ] 📁 → pilih file .py dari file manager HP → ter-import, terbuka di editor,
      nama bentrok otomatis `nama_2.py`
- [ ] 🔍 → Line: input 999 di file pendek → pesan receh muncul, dialog tetap
      kebuka, koreksi lalu OK langsung loncat
- [ ] 🔍 → Find: kata ditemukan, tap hasil → loncat; `>` masih menjalankan plugin
- [ ] Sidebar: TOOLS expand, plugin switch & eksekusi jalan, Symbol bar toggle
      bekerja, Clear All konfirmasi muncul, About di paling bawah
- [ ] SAMPLES: 3 kategori, 11 item; tap While Loop → tab baru → ▶ → main Tebak
      Angka di terminal (input() bekerja); Numpy/Web gagal hanya bila syarat
      (install/internet) belum dipenuhi — itu sesuai deskripsi

## 7. Keterbatasan yang diketahui (jujur, bukan PR berikutnya kecuali ada suara)

- Insiden & pelajaran (2026-08-09): CI `kspDebugKotlin` gagal "Unclosed comment"
  karena glob MIME bintang ditulis apa adanya di dalam doc comment — Kotlin block
  comment BERSARANG, tidak seperti Java/C. Diperbaiki di WorkbenchScreen.kt dan
  kini dijaga permanen oleh `tools/kotlin_sanity_check.py` (langkah 2 `check.sh`).
  Sandbox tetap tanpa JDK, jadi kompilasi Kotlin penuh hanya dinilai oleh CI.
- Pilihan tema belum dipersist antar-restart proses (perilaku lama dipertahankan;
  perubahan sengaja dibatasi sesuai ruang diskusi).
- Judul di dalam layar Pip tetap "Pip Package Manager"; hanya label sidebar yang
  berubah menjadi INSTALL MODULES (mudah diselaraskan bila diinginkan).
- Mode File/quick-open palette dihapus oleh desain; pindah file lewat tab bar.
