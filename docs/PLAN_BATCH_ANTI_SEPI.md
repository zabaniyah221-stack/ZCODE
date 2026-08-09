# PLAN_BATCH_ANTI_SEPI — ZCODE: Plugins, Pencarian Pydroid-style, Autocomplete 1+2, Snippets, FAB Syntax-Aware

**Status:** 🟡 DISEPAKATI 2026-08-09 (diskusi Zaki × agent) — menunggu eksekusi setelah **PR #7 di-merge**
**Prinsip repo:** jujur & teliti. Prinsip batch: **isi yang sepi tanpa menambah berat** — semua offline, deterministik, zero-dependency baru.

---

## 1. Kesepakatan final (tidak berubah tanpa diskusi ulang)

| # | Fitur | Keputusan kunci |
|---|---|---|
| S1 | **PLUGINS drawer expandable** | Tap header → kotak berborder `#1B4D2E` expand ke bawah, ±3 baris visible, scrollable; tap badan baris = eksekusi; toggle kanan = aktif/nonaktif; persist di SharedPreferences |
| S2 | **Semantik toggle** (lebih rapi dari Zabacode) | Plugin **Aksi** → ON = tersedia di palette, OFF = disembunyikan; plugin **Behavior** → ON = jalan otomatis saat Run; tap baris selalu = eksekusi manual. **Warn-only never block** (lesson RUN-mati Zabacode) |
| S3 | **🔍 multi-mode** | Chips `[📁 File][🔎 Find][#️ Line]`; Find = cari kata di file aktif → daftar `L<n>: <konteks>` tap → lompat; Line = goto line (validasi angka, clamp). Prefix power-user tetap: `>` perintah, `:` line |
| S4 | **Autocomplete kasta 1+2** | Kata-dalam-dokumen + keyword + builtins Python + item snippet; trigger ≥2 karakter & setelah `.`; maks 5 kandidat; debounce; nol kandidat = nol popup; styling OLED sudah ada di bundle. **Kasta 3 (jedi/LSP) = batch terpisah** |
| S5 | **Snippet Pack** | 1 item drawer/palette → dialog kartu → tap → file baru `snippet_<nama>_N.py`; `+` topbar TETAP frictionless (untitled_N.py) |
| S6 | **FAB syntax-aware** | `vm.syntaxError != null` → FAB merah `#FF4B4B`, **tetap bisa run**; banner ⚠ tetap tampil. Tanpa blocking, tanpa dialog tambahan |
| S7 | **Plugin baru yang di-port** | Docstring Generator, Type Hint Generator, Find Duplicate Lines (via **Python/Chaquopy**, port dari ZABACODE) + TODO Extractor (Kotlin). Beautifier Pro & Optimize Auto-Imports ZCODE **tetap** (tidak diganti) |
| S8 | **Ditolak/ditunda** | Run-Gate blocking (S6 penggantinya), Tier B tools (JSON/Regex/Timer/ASCII = backlog), kasta 3 LSP, provider AI (L3) |

---

## 2. Temuan audit Zabacode yang mengikat desain ini

1. **State plugin Zabacode terbelah** (backend in-memory + frontend localStorage) → ZCODE: **satu sumber** = SharedPreferences Kotlin.
2. **Toggle Zabacode punya 3 arti campur-aduk** (termasuk "aktivasi = eksekusi diam-diam" untuk transform) → ZCODE pakai S2.
3. **Lesson RUN-mati**: `confirm()` tak pernah render di WebView → guard wajib *warn-only, never block* + pesan selalu terlihat.
4. **Transform Zabacode = pure function** `code → (kode_baru, report)` → pola dipertahankan; report jadi toast.
5. **⚠️ Lisensi (JUJUR):** ZABACODE = GPLv3, ZCODE = MIT. Penulis keduanya sama (muzape28-blip) sehingga port sah secara hak cipta, tapi **wajib** header provenance di file hasil port: `Ported from ZABACODE (GPLv3), same author`.

---

## 3. Alur task (fase eksekusi)

### F0 — Persiapan
- [ ] Tag `v1.0.0-pre-antise` di HEAD sebagai rollback point
- [ ] Pastikan PR #7 (migrasi CM6) sudah merged; branch ini lanjut menampung commit batch

### F1 — Rebuild bundle CM6 (+autocomplete)
- [ ] `editor-src/package.json`: tambah `@codemirror/autocomplete` (pin eksak, anti-drift F-09) + `npm install` → lockfile ter-update
- [ ] `editor-src/src/editor.js`:
  - import & pasang `autocompletion({...})` dengan sumber kustom:
    - **localWords**: scan `state.doc` (regex `\w{2,}`, dedupe, maks ~50 kandidat internal)
    - **pythonKeywords**: daftar statis (`def class import return lambda async await …`)
    - **pythonBuiltins**: daftar statis (`print len range str int list dict …`)
    - **snippets**: label + body template (Flask, BS4, AsyncIO, REST — isi sama dengan F4)
  - konfigurasi: `activateOnTyping: true`, `maxRenderedOptions: 5`, `activateOnCompletion` untuk trigger `.`
  - tetap TANPA lint (fase Problems Panel nanti) — deviasi tercatat
- [ ] Rebuild (`npm run build`) → verifikasi: syntax OK, ukuran naik wajar (<500KB), marker `setCode/openFind/gotoLine` + marker autocomplete ada, nol CDN
- [ ] **Jujur:** `package.json` di dokumen ini + README editor-src diperbarui versinya

### F2 — Bridge & index.html
- [ ] Fungsi baru **`gotoLine(n)`**: dispatch selection ke baris n + `scrollIntoView` + focus; clamp 1..lineCount (dipakai S3 Find, S3 Line, dan TODO Extractor — satu fungsi tiga pemakai)
- [ ] Konfigurasi autocomplete di index.html tidak perlu (semua di bundle); index.html hanya tambah komentar kontrak baru
- [ ] Kontrak bridge total sekarang: `setCode, getCode, insertText, undo, redo, duplicateRows, toggleCommentLines, openFind, gotoLine` + handshake `onEditorReady`

### F3 — Backend plugin (Python via Chaquopy + Kotlin)
- [ ] `app/src/main/python/zcode_plugins.py` — **port 3 transform Zabacode** (`implementations.py` 478 baris, battle-tested): `SmartCommentGenerator`, `VariableTypeHintGenerator`, `DuplicateLineDetector`; antarmuka seragam `run(plugin_id, code) → {ok, code?, report[]}`; header provenance GPLv3 (temuan §2.5)
- [ ] `PluginRunner.kt` (core/plugins): dual-backend mengikuti pola ExecutionEngine — Chaquopy in-process di Android, `python3` subprocess di desktop; timeout + guard ukuran (`MAX_CODE_BYTES` yang sudah ada); parse hasil JSON
- [ ] `TodoExtractor.kt` (core/plugins): scan regex `TODO|FIXME|HACK|XXX` per baris → `List<TodoItem(line, tag, text)>` (Kotlin murni, tanpa Python)
- [ ] `PluginRegistry.kt` (core/plugins): daftar `PluginInfo(id, name, desc, kind: ACTION|BEHAVIOR)`; enabled-state baca/tulis SharedPreferences; daftar: beautifier, optimize_imports, duplicate_line (transform), toggle_comment (aksi cepat), docstring, type_hints, find_duplicates, todo_extractor, snippets, auto_trim_on_run (BEHAVIOR, default OFF)

### F4 — UI Compose
- [ ] **Drawer PLUGINS expandable**: header `🧩 PLUGINS (N aktif)` → `animateContentSize` + `LazyColumn` fixed-height ±3 baris; baris: nama + deskripsi pendek + `Switch`; tap badan = eksekusi (transform → hasilnya di-`setCode` + toast report; TODO/duplicates → dialog hasil dengan item tap → `gotoLine`)
- [ ] **Dialog Snippets**: kartu OLED (nama + deskripsi 1 baris) → tap → `FileManager` buat `snippet_<id>_N.py` + buka tab; `SnippetLibrary.kt` berisi template (Flask, BeautifulSoup, AsyncIO, REST API)
- [ ] **🔍 multi-mode**: PaletteDialog di-upgrade — chips mode `[📁 File][🔎 Find][#️ Line]`; mode Find mencari di `vm.activeCode` (case-insensitive, maks 100 hasil, render `L<n>: <konteks terpotong>`); mode Line validasi integer; prefix `>` dan `:` tetap berfungsi lintas mode
- [ ] **FAB syntax-aware**: warna `MaterialTheme` normal vs `Color(0xFFFF4B4B)` saat `vm.syntaxError != null`; klik tidak berubah
- [ ] Palette (🔍 commands) hanya memuat plugin ACTION yang enabled (semantik S2)

### F5 — Wiring perilaku (behavior)
- [ ] `auto_trim_on_run` (BEHAVIOR): saat Run, bila aktif → trimEnd tiap baris sebelum dieksekusi (port perilaku `auto_formatter` Zabacode) — **tidak mengubah** file, hanya kode yang dikirim ke runner
- [ ] Report transform → Toast (maks 3 baris pertama + "…N more")

### F6 — Guard & test (jumlah test TIDAK BOLEH turun dari 125; target +15 baru)
- [ ] Test struktural baru: `PluginRegistry` ada & berisi id wajib; `zcode_plugins.py` ada + header provenance + 3 kelas; `TodoExtractor`; `SnippetLibrary` ≥4 template; `gotoLine` ada di bundle & index kontrak; `autocompletion` marker di bundle; FAB `0xFFFF4B4B` di WorkbenchScreen; chips `Find`/`Line` di dialog; SharedPreferences key konsisten
- [ ] `tools/check.sh`: tambah step verifikasi bundle autocomplete + `zcode_plugins.py`
- [ ] Update `TestCM6Bundled` bila threshold/markers berubah (catat angka baru di dokumen ini)
- [ ] Jalankan: `python -m pytest` + `bash tools/check.sh` → wajib hijau semua sebelum lanjut

### F7 — Dokumentasi
- [ ] README: fitur baru di section yang sesuai (Plugins, Search, Autocomplete, Snippets, FAB)
- [ ] `docs/DESIGN_ZCODE.md`: mockup drawer PLUGINS + dialog 🔍 multi-mode + status FAB
- [ ] Dokumen ini: isi versi paket aktual, ukuran bundle aktual, checklist fase

### F8 — CI & verifikasi
- [ ] **Tidak ada perubahan `.github/workflows`** yang dibutuhkan batch ini (verifikasi APK CM6 sudah ada dari PR #7) — jika ternyata perlu, LAPOR USER dulu (wilayah user)
- [ ] Push → CI `check` + `assembleDebug` wajib hijau → APK artifact
- [ ] **QA device (12 poin)**: toggle plugin on/off persist; eksekusi docstring/type-hints/duplicate + toast report; TODO tap→lompat; 🔍 File/Find/Line; autocomplete muncul/hilang benar + posisi tidak nabrak QuickTools/keyboard; snippet jadi file; FAB merah saat error & tetap run; trim-on-run; terminal & lapisan lain tak terpengaruh; armv7 fisik + arm64

---

## 4. Risiko & mitigasi (jujur)

| Risiko | Level | Mitigasi |
|---|---|---|
| Popup autocomplete nabrak keyboard/QuickTools di layar kecil | 🟡 | QA device wajib (F8); `aboveCursor` fallback CM6; kalau parah → trigger hanya via `.` |
| Port Python salah semantik vs Zabacode asli | 🟡 | Port apa adanya + test unit kecil per transform (input→output known-good dari repo asal) |
| Chaquopy call lambat di ARMv7 (transform AST) | 🟡 | In-process (bukan spawn), guard timeout, progress toast; file ≤512KB sudah dibatasi |
| Kompile Kotlin (LazyColumn/animation imports, annotation experimental) | 🟡 | CI `assembleDebug` sebagai wasit; fix cepat tanpa stub (pelajaran PR #3) |
| False-positive Checker → FAB merah padahal kode benar | 🟢 | Dampak hanya kosmetik (S6 sengaja dipilih karena ini); sumber warna bisa di-upgrade ke `compile()` di fase Problems Panel tanpa ubah UX |
| Lisensi GPLv3 → MIT | 🟢 | Satu author; header provenance wajib (§2.5) |

## 5. Backlog (bukan batch ini — tercatat biar tidak hilang)
Kasta 3 autocomplete (jedi via Chaquopy), Problems Panel + `@codemirror/lint`, Humanizer traceback (L1), Saran-fix ber-diff (L2), Provider AI ala Colab (L3, keputusan strategis), Tier B tools (JSON Formatter, Regex Tester, Timer, ASCII Art), 10 tema + CRT, Keystore UI.

## 6. Rollback
Tag `v1.0.0-pre-antise` → `git revert` range batch; CM6 (PR #7) tidak disentuh sehingga editor tetap berfungsi apa pun yang terjadi.

---
**Menunggu PR #7 merged, lalu eksekusi F0.**
