# ⚙️ SETTINGS_DESIGN — Usulan Isi Menu Pengaturan (2026-08)

Dokumen ini mencatat **usulan isi halaman SETTINGS** ZCODE. Asumsi yang dipakai
berdiskusi: menu SETTINGS sudah ditambahkan di sidebar (tap → pindah layer/halaman,
pola sama seperti INSTALL MODULES/About), **tetapi halamannya masih placeholder**.
Dokumen ini = daftar isi yang layak mengisi placeholder itu. Bukan implementasi.

Aturan tim: *honest about anything* & *be meticulous in everything*. Lihat juga
`RENCANA_UPDATE_2026_08.md`, `PERF_PASS.md`, `TERMINAL_THEMES.md`, `CM6_FEATURE_MAP.md`,
`LIBRARY_DESIGN.md`.

---

## 0. Prinsip tata ruang (biar SETTINGS bukan laci sampah)

- **SETTINGS = preferensi global yang jarang diutak-atik.** Yang **sering dipakai
  saat ngoding** (plugin Beautifier, Auto-Imports, Duplicate Line, dll) **TETAP
  di TOOLS**, jangan dipindah.
- Item yang statusnya cuma informasi (versi, perangkat) tidak butuh tombol aksi.
- Aksi **destruktif** (Clear All) dipindah ke SETTINGS (Privasi & Data) karena
  bukan "tool produktif".
- Tiap setting punya **nilai default yang aman buat HP ampas ARMv7**.
- Setiap setelan **dipersist** (DataStore/EncryptedSharedPreferences yang sudah
  dipakai ZCODE) — hindari state terbelah seperti pelajaran Zabacode.
- Implementasi **bertahap per-commit kecil + UAT**, bukan borongan.

---

## 1. Daftar isi yang diusulkan

Daftar diurutkan per kelompok, dengan penanda:
- ✅ = backend/state sudah ada (tinggal UI/pindah).
- 🔧 = perlu kerja sedang (state + logika).
- 💡 = fitur baru/masih mimpi, prioritas belakangan.

### 🎨 Tampilan / Appearance

| # | Item | Tipe | Status | Catatan |
|---|---|---|---|---|
| 1 | Tema aplikasi | pilih (RETRO/DRACULA/TOKYO_NIGHT) | ✅ | Sekarang "cycle buta" di TOOLS; pindah ke sini jadi daftar yang jelas |
| 2 | Palet terminal | pilih (Phosphor/Dracula/Tokyo/Solarized/Monokai) | 🔧 | Latar terminal TETAP hitam OLED (lihat `TERMINAL_THEMES.md`) |
| 3 | Ukuran font editor | slider/kecil-sedang-besar | 🔧 |  |
| 4 | Ukuran font terminal | slider | 🔧 |  |
| 5 | Editor theme ikut app / tetap OLED | toggle | 💡 | Membatalkan lock OLED bila tak hati-hati — butuh keputusan user |

### ⌨️ Editor

| # | Item | Tipe | Status | Catatan |
|---|---|---|---|---|
| 6 | Symbol bar | toggle | ✅ | State `symbolBarEnabled` sudah ada |
| 7 | Auto-close brackets | toggle | 🔧 | Trivial di CM6 (`closeBrackets`, sudah di dep) — lihat `CM6_FEATURE_MAP.md` |
| 8 | Sorot kata yang diseleksi | toggle | 🔧 | `highlightSelectionMatches`; style sudah ada, tinggal pasang |
| 9 | Code folding | toggle | 💡 | Perlu pasang `foldGutter` + rebuild bundle |
| 10 | Word wrap | toggle | 🔧 |  |
| 11 | Auto-indent & ukuran indent (2/4) | toggle/select | 🔧 |  |
| 12 | Line numbers | toggle | 🔧 |  |
| 13 | Auto-save + interval | toggle + select | 🔧 | Nyambung `PERF_PASS.md` (debounce save; flush saat Run/pause) |

### ▶️ Run & Terminal

| # | Item | Tipe | Status | Catatan |
|---|---|---|---|---|
| 14 | Auto Trim saat Run | toggle | ✅ | Sudah jadi BEHAVIOR plugin; bisa dicerminkan di sini |
| 15 | Indikator "Menyalakan Python…" | toggle | 🔧 | Memperbaiki kesan tombol Run lambat (`PERF_PASS.md` akar F) |
| 16 | Pre-warm Python saat startup | toggle | 🔧 | Run pertama lebih cepat; trade-off waktu start app |
| 17 | Bersihkan terminal tiap Run | toggle | 🔧 |  |
| 18 | Batas output terminal | select (64KB/256KB/1MB) | 🔧 | Anti-jank untuk output deras (`PERF_PASS.md` akar E) |
| 19 | Getar saat Run selesai | toggle | 💡 |  |

### 📦 Packages

| # | Item | Tipe | Status | Catatan |
|---|---|---|---|---|
| 20 | Mode install (`--only-binary` / izinkan sdist) | select | 🔧 | Default aman ARMv7; default di Chaquopy 17 (`CHAQUOPY_STRATEGY`) |
| 21 | Lihat paket terpasang + copot | layar | 💡 | Lanjutan fitur LIBRARY (`LIBRARY_DESIGN.md`) |
| 22 | Bersihkan cache pip/user_packages | aksi | 💡 | Hati-hati, butuh konfirmasi |

### 🔒 Privasi & Data

| # | Item | Tipe | Status | Catatan |
|---|---|---|---|---|
| 23 | Status Keystore terenkripsi (AES-256) | info | ✅ | `KeystoreService` sudah ada; tampilkan status saja |
| 24 | Privacy / laporan error | toggle | 💡 | Default OFF; jujur soal apa yang dikirim |
| 25 | Clear All Drafts & Files | aksi (merah) | ✅ | **PINDAH dari TOOLS** ke sini (destruktif, bukan tool); konfirmasi wajib |
| 26 | Ekspor / Impor workspace | aksi | 💡 | ZIP folder `.py` internal |

### 🛠️ Bahasa & Input

| # | Item | Tipe | Status | Catatan |
|---|---|---|---|---|
| 27 | Bahasa aplikasi (ID/EN) | select | 💡 |  |
| 28 | Kustomisasi baris simbol | editor pilihan | 💡 | Pilih karakter yang muncul |
| 29 | Getar saat tombol | toggle | 💡 |  |

### ℹ️ Tentang

| # | Item | Tipe | Status | Catatan |
|---|---|---|---|---|
| 30 | Versi app + versi Chaquopy/Python | info | ✅ | `versionName` & versi runtime |
| 31 | Info perangkat (ABI, RAM, Android) | info | 🔧 | Menjelaskan tag ✅/⚠️ di LIBRARY (`LIBRARY_DESIGN.md` §4) |
| 32 | Lisensi & kredit open source | info | 💡 |  |
| 33 | Kontribusi / repo GitHub | tautan | 💡 |  |
| 34 | Cek pembaruan | aksi | 💡 |  |

---

## 2. Prioritas (jangan dikerjakan borongan)

- **Batch 1 — cepat, pondasi sudah ada:** #1, #2, #3 (atau #4), #6, #14, #23,
  #25 (pindah Clear All), #30.
- **Batch 2 — menyentuh performa & kenyamanan Run:** #13 (auto-save), #15
  (indikator Python), #16 (pre-warm), #18 (batas output).
- **Batch 3 — fitur baru menyusul:** sisanya (💡), setelah fondasi stabil dan
  UAT di Infinix bersih.

---

## 3. Tata letak teknis (ringkas)

- Halaman baru: `ui/settings/SettingsScreen.kt` (sejajar `PipScreen.kt`,
  `AboutScreen.kt`).
- Tambah route `"settings"` di `MainActivity.AppNavHost`; tambah item
  **SETTINGS** di sidebar (di atas **About & Contribute**).
- State preferensi terpusat di `WorkspaceViewModel` (seperti `themeType`,
  `symbolBarEnabled`) dengan penyimpanan terenkripsi yang sudah ada.
- Penataan: `LazyColumn` dengan header kelompok (Tampilan, Editor, Run, dst)
  supaya ringan di HP ampas (jangan Column scroll raksasa).
- Yang cuma link ke halaman lain (Packages → LIBRARY, About) pakai baris navigasi.

---

## 4. Edge case (teliti)

- **Jangan sampai toggle tidak persist** setelah app ditutup (ujian utama).
- **Pre-warm Python** tidak boleh bikin start app lebih lambat atau muncul error
  sebelum user membuka terminal.
- **Auto-save interval** harus tetap flush saat Run, pindah file, dan app
  di-background (lihat `PERF_PASS.md` §3).
- **Clear All** harus konfirmasi 2 langkah & tidak menghapus file yang sedang
  terbuka secara membabi buta.
- **Default aman untuk ARMv7**: fitur yang berisiko berat (pre-warm, animasi)
  default-nya dipilih agar tidak memberatkan HP ampas.
- **Performa UI SETTINGS**: gunakan `LazyColumn`; perubahan setting tidak boleh
  memicu rekomposisi editor yang berat.

---

## 5. UAT (Infinix Smart 9 HD)

- [ ] Tap SETTINGS di sidebar → pindah halaman; Back kembali ke editor.
- [ ] Ganti tema/palet/font langsung berefek dan bertahan setelah restart.
- [ ] Toggle Symbol bar & Auto Trim sinkron dengan yang di TOOLS.
- [ ] Clear All di SETTINGS minta konfirmasi; setelahnya drafts bersih.
- [ ] Scroll panjang pengaturan mulus (LazyColumn); tidak ada lag.
- [ ] Setting yang berhubungan dengan Run (indikator/pre-warm) benar-benar
      mengurangi kesan tombol Run lambat.

---

## 6. Hubungan dengan dokumen lain

- `PERF_PASS.md` — auto-save, indikator Python, pre-warm, batas output.
- `TERMINAL_THEMES.md` — palet terminal (latar hitam OLED).
- `CM6_FEATURE_MAP.md` — auto-close brackets, folding, selection match.
- `LIBRARY_DESIGN.md` — info perangkat (ABI/RAM) & keterkaitan packages.
- `CHAQUOPY_STRATEGY_2026_08.md` — `--only-binary` & info versi runtime.
- `RENCANA_UPDATE_2026_08.md` — pola navigasi layer & aturan UAT.

---

*Catatan desain — isi SETTINGS akan berkembang. Prioritas Batch 1 dulu, sisanya
menyusul per commit kecil dengan UAT di perangkat target.*
