# PLAN_BATCH_ANTI_SEPI — ZCODE: Plugins, Pencarian Pydroid-style, Autocomplete 1+2, Snippets, FAB Syntax-Aware

**Status:** 🟢 DIEKSEKUSI mulai 2026-08-09 — strategi **stacked branch**: PR #7 TIDAK di-merge dulu (permintaan user), batch ditumpuk di branch yang sama; PR #7 merge sekaligus di akhir. Judul/body PR akan di-update saat batch selesai.
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
- [x] Tag `v1.0.0-pre-antise` di HEAD (di atas migrasi CM6) sebagai rollback point
- [x] Strategi stacked: PR #7 tetap terbuka, commit batch numpuk di branch yang sama (keputusan user — session continuity)

### F1 — Rebuild bundle CM6 (+autocomplete)
- [x] `editor-src/package.json`: tambah `@codemirror/autocomplete` **6.20.3 pin eksak** (anti-drift F-09; sama dengan pin Acode) + lockfile ter-update
- [ ] `editor-src/src/editor.js`:
  - import & pasang `autocompletion({...})` dengan sumber kustom:
    - **localWords**: scan `state.doc` (regex `\w{2,}`, dedupe, maks ~50 kandidat internal)
    - **pythonKeywords**: daftar statis (`def class import return lambda async await …`)
    - **pythonBuiltins**: daftar statis (`print len range str int list dict …`)
    - **snippets**: label + body template (Flask, BS4, AsyncIO, REST — isi sama dengan F4)
  - konfigurasi: `activateOnTyping: true`, `maxRenderedOptions: 5`, `activateOnCompletion` untuk trigger `.`
  - tetap TANPA lint (fase Problems Panel nanti) — deviasi tercatat
- [x] Rebuild → **436.081 bytes** (dari 399.612; +36KB wajar), syntax OK, marker kontrak + konten (`frozenset`, `web_scraper`, tooltip OLED) terverifikasi, nol CDN
- [ ] **Jujur:** `package.json` di dokumen ini + README editor-src diperbarui versinya

### F2 — Bridge & index.html
- [x] Fungsi baru **`gotoLine(n)`**: dispatch selection ke baris n + `scrollIntoView` + focus; clamp 1..lineCount (dipakai S3 Find, S3 Line, dan TODO Extractor — satu fungsi tiga pemakai)
- [x] Konfigurasi autocomplete di index.html tidak perlu (semua di bundle); index.html hanya tambah komentar kontrak baru
- [x] Kontrak bridge total sekarang: `setCode, getCode, insertText, undo, redo, duplicateRows, toggleCommentLines, openFind, gotoLine` + handshake `onEditorReady`

### F3 — Backend plugin (Python via Chaquopy + Kotlin)
- [x] `app/src/main/python/zcode_plugins.py` — **port 3 transform Zabacode** (`implementations.py` 478 baris, battle-tested): `SmartCommentGenerator`, `VariableTypeHintGenerator`, `DuplicateLineDetector`; antarmuka seragam `run(plugin_id, code) → {ok, code?, report[]}`; header provenance GPLv3 (temuan §2.5)
- [x] `PluginRunner.kt` (core/plugins): dual-backend mengikuti pola ExecutionEngine — Chaquopy in-process di Android, `python3` subprocess di desktop; timeout + guard ukuran (`MAX_CODE_BYTES` yang sudah ada); parse hasil JSON
- [x] `TodoExtractor.kt` (core/plugins): scan regex `TODO|FIXME|HACK|XXX` per baris → `List<TodoItem(line, tag, text)>` (Kotlin murni, tanpa Python)
- [x] `PluginRegistry.kt` (core/plugins): daftar `PluginInfo(id, name, desc, kind: ACTION|BEHAVIOR)`; enabled-state baca/tulis SharedPreferences; daftar: beautifier, optimize_imports, duplicate_line (transform), toggle_comment (aksi cepat), docstring, type_hints, find_duplicates, todo_extractor, snippets, auto_trim_on_run (BEHAVIOR, default OFF)

### F4 — UI Compose
- [x] **Drawer PLUGINS expandable**: header `🧩 PLUGINS (N aktif)` → `animateContentSize` + `LazyColumn` fixed-height ±3 baris; baris: nama + deskripsi pendek + `Switch`; tap badan = eksekusi (transform → hasilnya di-`setCode` + toast report; TODO/duplicates → dialog hasil dengan item tap → `gotoLine`)
- [x] **Dialog Snippets**: kartu OLED (nama + deskripsi 1 baris) → tap → `FileManager` buat `snippet_<id>_N.py` + buka tab; `SnippetLibrary.kt` berisi template (Flask, BeautifulSoup, AsyncIO, REST API)
- [x] **🔍 multi-mode**: PaletteDialog di-upgrade — chips mode `[📁 File][🔎 Find][#️ Line]`; mode Find mencari di `vm.activeCode` (case-insensitive, maks 100 hasil, render `L<n>: <konteks terpotong>`); mode Line validasi integer; prefix `>` dan `:` tetap berfungsi lintas mode
- [x] **FAB syntax-aware**: warna `MaterialTheme` normal vs `Color(0xFFFF4B4B)` saat `vm.syntaxError != null`; klik tidak berubah
- [x] Palette (🔍 commands) hanya memuat plugin ACTION yang enabled (semantik S2)

### F5 — Wiring perilaku (behavior)
- [x] `auto_trim_on_run` (BEHAVIOR): jalan di klik FAB sebelum Run. **Deviasi jujur dari rencana awal**: eksekusi ZCODE membaca file dari disk, dan Zabacode asli pun mengubah buffer (`setEditorValue`) — maka trim disimpan ke file (editor ikut rapi), bukan buffer-sementara
- [x] Report transform → Toast (maks 3 baris pertama)

### F6 — Guard & test (jumlah test TIDAK BOLEH turun dari 125; target +15 baru)
- [x] Test struktural + behavioral baru (18 test): `PluginRegistry` ada & berisi id wajib; `zcode_plugins.py` ada + header provenance + 3 kelas; `TodoExtractor`; `SnippetLibrary` ≥4 template; `gotoLine` ada di bundle & index kontrak; `autocompletion` marker di bundle; FAB `0xFFFF4B4B` di WorkbenchScreen; chips `Find`/`Line` di dialog; SharedPreferences key konsisten
- [x] `tools/check.sh`: guard gotoLine/autocomplete + zcode_plugins + PluginRegistry
- [x] Threshold tetap valid; angka baru tercatat (bundle 436.081 B)
- [x] Hasil: **143/143 pytest hijau** (dari 125, +18) + check.sh 8/8 langkah hijau

### F7 — Dokumentasi
- [x] README: section "🧩 Batch Anti-Sepi"
- [x] Desain terdokumentasi di dokumen ini §1 + mockup di diskusi (DESIGN_ZCODE update minor menyusul bila diminta)
- [x] Dokumen ini final dengan angka aktual

### F8 — CI & verifikasi
- [x] **Tidak ada perubahan `.github/workflows`** untuk batch ini (verifikasi APK CM6 sudah ada) — terbukti tidak perlu menyentuh wilayah user
- [ ] Push → CI `check` + `assembleDebug` wajib hijau → APK artifact (kompilasi Kotlin baru bisa dibuktikan CI — sandbox tanpa JDK/SDK)
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
## 7. Log Eksekusi (2026-08-09)

1. Tag `v1.0.0-pre-antise` di HEAD migrasi CM6.
2. `zcode_plugins.py` di-port apa adanya + **smoke test lokal lolos** (docstring, type hints `b: int = 10` ter-infer, duplicate `x = 5` L7/L8, plugin palsu graceful).
3. Bundle CM6 rebuild dengan `@codemirror/autocomplete` 6.20.3 → **436.081 B**; audit URL: hanya namespace SVG w3.org + URL di dalam *konten template snippet* (bukan request bundle).
4. Kotlin: PluginRunner (dual-backend + timeout + graceful), TodoExtractor, PluginRegistry (10 plugin), SnippetLibrary (4 template), ViewModel (pluginFlags satu-sumber, runPythonPlugin async, findInActiveCode, createFileFromSnippet, applyAutoTrimIfEnabled).
5. UI: drawer 🧩 PLUGINS expandable (LazyColumn 176dp + border), PaletteDialog multi-mode chips + prefix power-user, dialog TODO & Snippets, FAB `#FF4B4B` syntax-aware.
6. Test: **143/143** (125 lama + 18 baru, termasuk 2 behavioral test via interpreter asli) + check.sh hijau.

**Masih terbuka (jujur):** verifikasi kompilasi Kotlin oleh CI (`assembleDebug`) + QA device 12 poin (§3 F8).
