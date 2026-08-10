# 🗺️ TASK_ROADMAP — Alur Kerja ZCODE (2026-08)

Dokumen ini menyusun **urutan eksekusi** semua pekerjaan yang sudah diarsipkan di
dokumen sesi 2026-08. Urutannya sengaja disusun berdasarkan **dampak ke user di
perangkat target (Infinix Smart 9 HD, ARMv7)** — bukan berdasarkan yang paling
keren/menantang secara teknis (aturan inti #3: *build for user, not for ego*).

Aturan main yang berlaku di SETIAP task:
1. **Satu task = satu commit kecil** (atau beberapa kecil), bukan borongan.
2. **Guard:** test sandbox (`pytest`, `tools/check.sh`, `py_compile`,
   `verifyEditorBundled`) harus hijau. Kompilasi Kotlin dinilai **CI** (sandbox
   tanpa JDK — jujur soal keterbatasan ini).
3. **UAT Infinix** sebelum task dianggap selesai. Belum UAT = belum selesai.
4. Gagal/merusak → `git revert` satu SHA, jangan ditambal berlarut-larut.
5. Tidak mencampur pekerjaan yang belum teruji (hukum keluarga).

---

## Ringkasan urutan (peta besar)

```
Gelombang 1 — Run yang enak (PERF: atasi keluhan tombol Run lambat)
   └─ sub 1A: feedback & indikator (cepat)
   └─ sub 1B: pindah kerja berat + debounce + pre-warm
Gelombang 2 — Rumah Settings (halaman + item dasar + pindah Clear All)
Gelombang 3 — Editor quick wins (close brackets, selection match, dll)
Gelombang 4 — Visual Problems Panel (VPP)
Gelombang 5 — LIBRARY di INSTALL MODULES
Gelombang 6 — Terminal: palet ANSI & ukuran font
Gelombang 7 — Tools lanjutan (folding, outline/symbols)
Gelombang 8 — Spike Chaquopy 17 (TERAKHIR, terisolasi, setelah semua stabil)
```

Prinsip: **selesaikan yang bikin user kesal dulu** (Run lambat), baru fitur baru.
Chaquopy 17 ditaruh paling akhir karena mengubah runtime — hanya dikerjakan
setelah gelombang lain stabil, sesuai `CHAQUOPY_STRATEGY_2026_08.md`.

---

## Gelombang 1 — Run yang enak (prioritas UTAMA)

Sumber: `PERF_PASS.md` (khusus akar F — keluhan user soal tombol Run).

### 1A — Feedback instan & indikator (risiko rendah, cepat)
- [ ] **T1.1** FAB ▶ langsung kasih reaksi saat di-tap (state "running" —
      mis. FAB jadi spinner/berubah warna/ikon "stop"), TIDAK menunggu Python.
- [ ] **T1.2** `TerminalScreen` tampilkan **"Menyalakan Python…"** + indikator
      saat cold-start, bukan layar kosong.
- [ ] **T1.3** Pastikan pesan pertama muncul (banner "ZCODE Terminal") sebelum
      Python siap, supaya user tahu tap-nya kebaca.

**Guard/UAT:** tap Run di Infinix harus terasa "kebaca" seketika; tidak ada
layar hitam/kosong tanpa tulisan.

### 1B — Pindah kerja berat & optimasi (risiko sedang)
- [ ] **T1.4** Evaluasi & pangkas `onClick` FAB: `applyAutoTrimIfEnabled` +
      `pushCode()` (`setCode` sinkron ke WebView) tidak boleh memblokir tap.
      Ambil kode dari sumber otoritatif (VM) tanpa round-trip JS yang berisiko
      feedback-loop. **Hati-hati: jangan sampai ketikan/posisi kursor hilang.**
- [ ] **T1.5** **Debounce auto-save** (tulis file setelah berhenti mengetik),
      dengan **flush wajib** saat: Run, pindah file, app di-background/pause.
      (Akar C, D di PERF_PASS.)
- [ ] **T1.6** **Pre-warm Python** saat app start (di background, tidak blokir
      UI). Default **OFF** dulu (aman buat HP ampas); aktifkan via toggle di
      Settings (Gelombang 2). Ukur dampak start app sebelum memutuskan default.
- [ ] **T1.7** Terminal: **ring buffer/cap output** + **coalesce scrollTo**
      (~120 ms) supaya output deras tidak jank (akar E).

**Gerbang Gelombang 1:** UAT Infinix — mengetik lancar, Run terasa instan,
tidak ada layar kosong, output deras tidak freeze, `input()` tetap jalan.

---

## Gelombang 2 — Rumah Settings

Sumber: `SETTINGS_DESIGN.md` (Batch 1).

- [ ] **T2.1** Buat `ui/settings/SettingsScreen.kt` (LazyColumn), route
      `"settings"`, item **SETTINGS** di sidebar (di atas About & Contribute).
- [ ] **T2.2** **Pindahkan "Clear All Drafts & Files"** dari TOOLS ke SETTINGS
      (Privasi & Data); tetap dengan dialog konfirmasi.
- [ ] **T2.3** Pemilih tema yang **jelas** (daftar RETRO/DRACULA/TOKYO_NIGHT),
      menggantikan "cycle buta" sambil mempertahankan fungsi cycle jika diinginkan.
- [ ] **T2.4** Cerminkan toggle yang sudah ada: Symbol bar, Auto Trim on Run.
- [ ] **T2.5** Tambah toggle untuk: indikator Python (T1.2), pre-warm (T1.6),
      auto-save + interval (T1.5) — semua dipersist (DataStore/EncryptedPrefs).
- [ ] **T2.6** Info perangkat (ABI, RAM, Android) di bagian Tentang — menjelaskan
      tag LIBRARY nanti.

**JANGAN di gelombang ini:** palet terminal, font size, semua item 💡 — itu
Gelombang 6/lanjutan. Tujuannya Settings fungsional, bukan penuh.

**Gerbang:** semua toggle persist setelah restart; scroll pengaturan mulus di
Infinix; Clear All pindah & tetap aman.

---

## Gelombang 3 — Editor quick wins (TOOLS_CATALOG prioritas tinggi)

Sumber: `TOOLS_CATALOG.md` §5 + `CM6_FEATURE_MAP.md`.

> Perubahan editor = WAJIB rebuild bundle CM6 (`cd editor-src && npm run build`)
> dan commit hasilnya; `verifyEditorBundled` akan gagal kalau kontrak bridge
> rusak. Ukur ukuran bundle sebelum/sesudah.

- [ ] **T3.1** **Auto-close brackets** (`closeBrackets` — sudah ada di dep
      `autocomplete`, tinggal aktifkan). Termasuk toggle-nya di Settings.
- [ ] **T3.2** **Selection match highlight** (`highlightSelectionMatches`; gaya
      `.cm-selectionMatch` sudah ada, tinggal pasang).
- [ ] **T3.3** **Word wrap** sebagai toggle (bila belum bisa di-switch runtime).
- [ ] **T3.4** Transform teks kecil (Kotlin/JS murni, tanpa pip):
      **Sort Lines**, **Change Case** (UPPER/lower/Title), **Trim Now**.
      Tambah sebagai plugin ACTION di `PluginRegistry`.
- [ ] **T3.5** **Organize imports lanjutan**: rapikan/buang import tak terpakai
      (berbasis AST Python, ringan; waspada bug Zabacode F-02/B-10 — jangan
      merusak kode valid).

**Gerbang:** bundle bertambah wajar (tidak gendut), semua toggle bekerja &
persist, mengetik tetap ringan, UAT Infinix.

---

## Gelombang 4 — Visual Problems Panel (VPP)

Sumber: `VPP_DESIGN.md`.

- [ ] **T4.1** Ubah `Checker.checkSyntax` → `List<Problem>` (model `Problem`:
      severity, message, line, column, source). Lengkapi unit test untuk semua
      kasus lama (string berisi `:)`, triple-quote, f-string, `async def`, dll).
- [ ] **T4.2** State `problems` + `expanded` di ViewModel (perhitungan terjadwal/
      debounce, bukan tiap ketik tanpa henti).
- [ ] **T4.3** `ProblemsBanner` collapsed: ikon severity terparah + pesan pertama
      (elipsis) + chip `(+N)`.
- [ ] **T4.4** Expand ke bawah (Opsi 3): header "N masalah", ±5 baris,
      scrollable, warna tema, `rememberSaveable`.
- [ ] **T4.5** Tap item → `gotoLine(n)` (sudah ada); panel tetap terbuka.
- [ ] **T4.6** 0 error → banner hilang. Edge: 1 error, pesan panjang, rotasi,
      error tanpa line, performa (lihat PERF_PASS).

**Gerbang:** UAT Infinix mengetik dengan panel terbuka tetap mulus; banner
akurat; goto benar.

---

## Gelombang 5 — LIBRARY di INSTALL MODULES

Sumber: `LIBRARY_DESIGN.md` + `PIP_SCOPE.md`.

- [ ] **T5.1** `assets/libraries.json` (~30–50 paket populer dulu) + test
      validasi (JSON valid, field wajib, kategori dikenal) — seperti
      `test_zcode_fase3.py` untuk samples.
- [ ] **T5.2** `DeviceProbe`: baca `Build.SUPPORTED_ABIS` + RAM, hitung tag
      ✅/⚠️/❌/🚫 (unit test dengan fake, tanpa perangkat).
- [ ] **T5.3** `LibraryScreen` (pola `SamplesScreen`): kategori → item, search,
      tag perangkat, tombol Install. Baca-only dulu.
- [ ] **T5.4** Sambungkan Install ke `ExecutionEngine.startPipStream`; log rapi.
- [ ] **T5.5** Penanganan gagal BERSIH: deteksi error klasik (tak ada wheel,
      resolution, storage penuh, timeout), pesan jujur, rollback setengah jadi.
- [ ] **T5.6** Pindahkan layar pip manual (field nama + log) ke bawah sebagai
      **"Install manual"**; fitur lama tidak hilang.

**Gerbang:** `requests`/`numpy` terpasang & bisa di-import; `pyzmq`/`jupyter`
gagal dengan pesan jelas (bukan berantakan) di Infinix.

---

## Gelombang 6 — Terminal: palet & font

Sumber: `TERMINAL_THEMES.md`.

- [ ] **T6.1** Model `TerminalPalette` (16 warna ANSI + fg/bg/cursor) + unit
      test pemetaan SGR (30–37, 90–97, reset, kombinasi).
- [ ] **T6.2** 2–3 palet awal (Phosphor, Dracula, Tokyo) — latar TETAP hitam OLED.
- [ ] **T6.3** Render SGR di terminal memakai palet aktif; stderr/error merah.
- [ ] **T6.4** Pemilih palet & ukuran font di Settings; persist.
- [ ] **T6.5** (Opsional) palet Solarized/Monokai setelah dasar stabil.

**Gerbang:** output `rich`/`colorama` tampil sesuai palet; output deras tetap
mulus; kontras terbaca di OLED Infinix.

---

## Gelombang 7 — Tools lanjutan

Sumber: `TOOLS_CATALOG.md` (prioritas menengah) + `CM6_FEATURE_MAP.md`.

- [ ] **T7.1** **Code folding** (`foldGutter`) + toggle; rebuild bundle.
- [ ] **T7.2** **Outline/Symbols** (daftar fungsi/kelas → gotoLine), berbasis
      AST Python ringan (pola sama seperti TODO Extractor).
- [ ] **T7.3** (Tunda, butuh LSP/jedi) Go to Definition/Rename versi dalam-file.
- [ ] **T7.4** (Tunda) Find in files — butuh konsep folder/project.

---

## Gelombang 8 — Spike Chaquopy 17 (TERAKHIR & TERISOLASI)

Sumber: `CHAQUOPY_STRATEGY_2026_08.md`.

- [ ] **T8.1** SATU commit terisolasi: plugin `15.0.1 → 17.0.0`, **TETAP**
      `version = "3.11"` (jangan 3.10 default, apalagi 3.12+).
- [ ] **T8.2** Verifikasi: monkey-patch `AssetPath.parent` di `zcode_pip.py`
      masih perlu/redundan di pip 25.3.
- [ ] **T8.3** UAT berat: `sys.stdin`/`input()` (TerminalBridge), AGP 8.2.2
      kompat, build & jalan di ARMv7 asli.
- [ ] **T8.4** Kalau ada yang patah → `git revert` satu SHA, laporkan, jangan
      dipaksa.

**Syarat:** hanya dikerjakan SETELAH Gelombang 1–7 stabil di UAT, tidak bareng
fitur lain.

---

## Yang TIDAK dikerjakan sekarang (jujur)

- AI/Oracle auto-fix (butuh diskusi privasi/API key terpisah).
- ZMUX (Alpine proot) — download-on-demand, tidak dibundle; ranah terpisah.
- ZPLAY (Kivy/pygame) — fork terpisah, bukan ZCODE.
- LSP penuh (jedi/Ruff) — berat di HP ampas; backlog.
- Multi-bahasa penuh — memperbesar bundle; butuh keputusan tersendiri.
- GUI native (Tkinter/Qt) — di luar arsitektur Chaquopy in-process.
- Minimap, Emmet, remote file, marketplace plugin sembarangan — ditolak
  (aturan #3: tidak memberi manfaat sepadan di perangkat target).

---

## Cara bergerak

1. Selesaikan **satu task**, commit, biar CI menilai kompilasi.
2. Setelah satu gelombang, **berhenti & UAT di Infinix**; baru lanjut.
3. Setiap doc yang berubah karena kenyataan implementasi, **update doc-nya**
   (jujur: rencana bukan kitab suci).
4. Urutan bisa diubah atas persetujuan user — terutama kalau ada keluhan baru
   yang lebih mendesak.

---

*Roadmap ini dibaca dari atas ke bawah: dampak user dulu, teknis menarik belakangan.
Chaquopy 17 sengaja di akhir sebagai gerbang keamanan.*
