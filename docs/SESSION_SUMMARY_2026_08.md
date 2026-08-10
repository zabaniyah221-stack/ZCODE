# 📜 Ringkasan Lengkap Sesi — ZCODE (2026-08)

Dokumen ini mengarsipkan **seluruh diskusi** dari awal sesi sampai sekarang:
keputusan, riset, temuan kode, usulan fitur, dan artefak `.md` yang ditulis.
Tujuannya supaya konteks tidak hilang dan bisa dilanjutkan di sesi/manusia
berikutnya.

Aturan tim (dari `docs/RENCANA_UPDATE_2026_08.md`):
- **§1 — Honest about anything, no matter our weakness.**
- **§2 — Be meticulous in everything, no matter how small, to minimize edge case.**
- **§3 — Build for the user, not for ego.** (Ditambahkan 2026-08: bangun yang
  berguna/nyata untuk user di perangkat target, bukan yang keren atau memuaskan
  kebanggaan teknis.)

> Cabang sesi ini: **`arena/019fe8ce-zcode`**. (Doc sesi sebelumnya menyebut
> `arena/019fe878-zcode`; lingkungan ini berada di `arena/019fe8ce-zcode` dan
> tetap bekerja di cabang ini sesuai aturan platform.)

---

## 0. Konteks PR / status repo

- Repo: `muzape28-blip/ZCODE` (Kotlin/Compose + CodeMirror 6 + Chaquopy).
- PR **#8** (`arena/019fe707-zcode` → `main`) tercatat di sesi sebelumnya:
  "Fase 3 UI Redesign + SAMPLES" (11 sample, redesign sidebar/topbar).
- Status kode saat ini (fakta dari build):
  - Plugin Chaquopy **15.0.1**, Python **3.11**, pip **23.3.1** (pin).
  - AGP 8.2.2 / Kotlin 1.9.22; minSdk 26; compileSdk 34.
  - abiFilters `armeabi-v7a + arm64-v8a + x86_64`.
  - Editor: CodeMirror 6 (bundle di-commit), hanya Python, tema OLED.

---

## 1. Riset Chaquopy → `docs/CHAQUOPY_STRATEGY_2026_08.md`

- **Cakupan:** pure-Python praktis 100% PyPI; native hanya yang punya wheel
  Android Chaquopy (`chaquo.com/pypi-13.1/`), subset luas tapi tak lengkap.
- **Alternatif:** untuk arsitektur embedded Kotlin+Python, Chaquopy praktis
  satu-satunya. BeeWare/Kivy = bongkar Kotlin (ranah ZPLAY); Termux = ZMUX
  terpisah (download-on-demand).
- **Versi terbaru:** Chaquopy **17.0.0** (Des 2025) bawa pip 25.3,
  `--only-binary` default, dukungan 16 KB page; runtime 3.10/3.11/3.12/3.13/3.14.
- **Cawat kritis:** Python **3.12+ mencabut armeabi-v7a/x86 (#709)**. Maka
  rekomendasi: **upgrade plugin 15.0.1 → 17.0.0, TAPI PIN `pythonVersion "3.11"`**.
- Spike = **satu commit terisolasi**, setelah Batch 3 stabil, dengan verifikasi
  (monkey-patch AssetPath, `input()`/stdin, AGP, build ARMv7 asli).

---

## 2. VPP (Visual Problems Panel) → `docs/VPP_DESIGN.md`

- **Desain terkunci (Opsi 3):** banner 1 baris collapse; tap → **expand ke
  bawah** (bukan modal/screen), ±5 baris, scrollable di dalam panel; tap item
  → `gotoLine`; panel tetap terbuka; 0 error → banner hilang.
- **Temuan jujur dari kode:** `Checker.checkSyntax()` cuma balikin **1 error**
  (`String?`). VPP butuh sumber **daftar** masalah:
  - Opsi A: perluas `Checker` jadi `List<Problem>` (ringan, offline).
  - Opsi B: `@codemirror/lint` + diagnostic Lezer (rebuild bundle).
- Rekomendasi: mulai Opsi A; model `Problem` dirancang agar B bisa menyatu.
- `gotoLine(n)` sudah ada di `WorkbenchScreen.kt`.

---

## 3. Audit CodeMirror 6 → `docs/CM6_FEATURE_MAP.md`

- **Sudah dipakai:** syntax highlight Python, line numbers, active line,
  undo/redo, bracket matching, auto-indent, draw/rectangular selection, drop
  cursor, line wrap, autocomplete kasta 1+2, find/replace, tema OLED, bridge.
- **Belum dipakai:** `@codemirror/lint` (VPP), **code folding** (sengaja off),
  **close brackets** (sudah di dep autocomplete, tinggal aktif), **selection
  match** (style sudah ada tapi nganggur), multi-bahasa, multi-tema editor.
- Aturan: tambah dependensi = ukur bundle, pin versi, jaga kontrak bridge.

---

## 4. Tiga sumber tools (ZABACODE/VS Code/Acode) → `docs/TOOLS_CATALOG.md`

- Instruksi user: tools dari ketiga repo ditaruh di sidebar **TOOLS** yang
  scrollable.
- ZCODE sudah punya 10+ plugin (Beautifier, Optimize Imports, Duplicate Line,
  Docstring/Type Hint, Duplicate Detector, TODO Extractor, Snippet, Auto Trim).
- **Prioritas tinggi (ringan):** Close Brackets, Selection Match, Code Folding,
  Outline/Symbols, Organize Imports lanjutan, Sort Lines/Change Case/Trim Now.
- **Tunda:** Go to Def/Rename (butuh LSP/jedi), find in files (butuh folder),
  key bindings.
- **Tolak (jujur):** minimap, emmet, color picker, remote file, marketplace
  plugin sembarangan.

---

## 5. LIBRARY di INSTALL MODULES → `docs/LIBRARY_DESIGN.md` + `PIP_SCOPE.md`

- Keputusan tata letak (dari gambar/tes user): fitur **LIBRARY ditaruh di
  dalam menu INSTALL MODULES**, bukan menu sidebar baru.
- Layar "Pip Package Manager" yang sekarang (field nama + log) dijadikan jalur
  **"Install manual"**; tampilan utama jadi katalog paket terkurasi.
- Tag perangkat: ✅ recommended / ⚠️ heavy / ❌ unsupported / 🚫 out-of-scope,
  dihitung dari **ABI (`Build.SUPPORTED_ABIS`) + RAM** vs metadata.
- Data: `assets/libraries.json` (metadata saja; **tidak** bundling wheel).
- Install tetap pakai `ExecutionEngine.startPipStream`; yang ❌ gagal **bersih &
  jujur** (rollback setengah jadi), bukan traceback berantakan.
- Out-of-scope: tensorflow/torch (besar), jupyter/pyzmq (native gap),
  tkinter/PyQt/kivy (ZPLAY), selenium (butuh browser).

---

## 6. Performa ("tersendat/lag") → `docs/PERF_PASS.md`

Lima akar dari pembacaan kode:
1. WebView CM6 dibongkar/dibuat ulang tiap navigasi editor↔terminal (paling parah).
2. Python cold-start ~1–3 dtk tanpa indikator "Menyalakan Python…".
3. Simpan file ke disk **tiap ketik** + mutasi state dari thread WebView.
4. `activeCode` di state puncak → `WorkbenchScreen` (~1000+ baris) rekompos penuh.
5. Terminal = 1 string raksasa + `scrollTo` tiap chunk output.

Fix: terminal overlay/jangan recreate WebView; pre-warm + indikator; debounce
save + flush saat Run/pause/ganti file; pecah composable + `derivedStateOf`;
coalesce scroll + buffer.

---

## 7. Tema terminal → `docs/TERMINAL_THEMES.md`

- **Latar terminal TETAP hitam OLED** (dikunci); foreground/palet ANSI yang
  berganti.
- Usulan palet: Phosphor/Retro, Dracula, Tokyo Night, Solarized, Monokai,
  ZABACODE Classic.
- Pemetaan SGR/ANSI 16 warna; preferensi disimpan; default menunggu keputusan
  user ("ikut tema"/"phosphor"/terserah).

---

## 8. Keputusan TERKUNCI

- PR #8 (Fase 3) tercatat sebagai konteks.
- **Chaquopy: Python 3.11 (jangan 3.12+)**; plugin boleh 17.0.0 dengan pin.
- **VPP Opsi 3** (expand ke bawah).
- **ZMUX** = download-on-demand, tidak di-bundle.
- **ZPLAY** (Kivy/pygame) = fork terpisah berbasis p4a, bukan ZCODE.
- **App Mode (Flask+WebView)** = strategi GUI ZCODE.
- "Hukum keluarga": jangan gabung yang belum teruji / bikin bloat; perubahan
  kecil per-commit + guard + CI + UAT di HP ARMv7 asli (Infinix Smart 9 HD).
- Sandbox tanpa JDK → CI yang mengadili kompilasi Kotlin; jujur soal ini.

---

## 9. Hal yang masih menunggu suara user (pending)

1. **Warna default output terminal**: `ikut tema` / `phosphor` / terserah.
2. **Editor theme ikut app theme**: latar ikut berubah atau token saja (lock
   OLED bisa batal).
3. **Multi-bahasa**: full (APK lebih besar) atau subset (web/config/docs).
4. **GASS signal** untuk mulai eksekusi Tier 0 (A+B+C+D) + Batch 1.5.
5. **Urutan dokumen/fitur**: mana yang dikerjakan dulu
   (PIP_SCOPE/LIBRARY_DESIGN/PERF_PASS/TOOLS_CATALOG/VPP/dst).

---

## 10. Artefak yang dibuat di sesi ini

Semua di folder `docs/`:
- `CHAQUOPY_STRATEGY_2026_08.md`
- `PIP_SCOPE.md`
- `LIBRARY_DESIGN.md`
- `PERF_PASS.md`
- `VPP_DESIGN.md`
- `CM6_FEATURE_MAP.md`
- `TOOLS_CATALOG.md`
- `TERMINAL_THEMES.md`
- `SESSION_SUMMARY_2026_08.md` (dokumen ini)

---

## 11. Langkah berikutnya (terbuka)

- Tunggu aba-aba user: arsip saja, atau mulai eksekusi salah satu dokumen.
- Kalau eksekusi: mulai dari yang **paling ringan & terisolasi** (mis. close
  brackets + selection match di CM6, atau fondasi `List<Problem>` untuk VPP),
  dengan per-commit kecil + guard + UAT ARMv7.
- Chaquopy 17.0.0 tetap **spike setelah Batch 3 stabil**, tidak dikerjakan bareng.

---

*Arsip sesi — bukan keputusan final eksekusi. Semua perubahan mengikuti protokol
"HATI-HATI": diff kecil, guard, CI gate, dan UAT di perangkat asli.*
