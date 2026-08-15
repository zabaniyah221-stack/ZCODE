# 🗺️ CM6_FEATURE_MAP — Peta Fitur CodeMirror 6 di ZCODE (2026-08)

Dokumen ini memetakan **apa saja kapabilitas CodeMirror 6 yang sudah dipakai**
dan **mana yang belum** di editor ZCODE. Sumber: `editor-src/src/editor.js`
dan `editor-src/package.json`. Tujuannya jujur: tahu persis fondasi editor
sebelum menambah fitur (VPP, folding, bracket auto-close, multi-bahasa, dll).

Lihat juga `docs/MIGRASI_CM6.md` untuk sejarah migrasi Ace → CodeMirror 6.

> **RALAT 2026-08-15.** Audit ulang terhadap `editor.js` menemukan dokumen ini
> tertinggal dari kode: **foldGutter, closeBrackets, dan
> highlightSelectionMatches sudah AKTIF** (F1.7/F1.8 + batch folding) padahal
> §3 lama masih menandainya "belum". Tabel di bawah sudah diralat. Ditambah
> §3b: kandidat baru hasil riset ekosistem CM6 (official
> `codemirror.net/docs/extensions` + community Replit). Pelajaran: peta wajib
> di-sync setiap kali `buildState()` berubah — kode selalu menang atas dokumen.

---

## 1. Konteks teknis

- Bundle dibangun dengan **esbuild** dari `editor-src/` lalu di-**commit** ke
  `app/src/main/assets/editor/codemirror.bundle.js` (CI tidak butuh Node).
- Hanya **3 dependensi runtime**:
  - `codemirror` 6.0.2
  - `@codemirror/lang-python` 6.2.1
  - `@codemirror/autocomplete` 6.20.3
- Versi **dipin eksak** (tanpa `^`/`~`) — anti-drift (prinsip F-09).
- Target **es2018** (WebView Android 8.0/Chrome 63+, minSdk 26), kompatibel
  armv7/arm64/x86_64 (JS murni, tanpa WASM/native).
- Hanya **1 bahasa** (Python) dan **1 tema** (true-black OLED) untuk sekarang.

---

## 2. Fitur yang SUDAH dipakai

| Fitur | Sumber di editor.js |
|---|---|
| Syntax highlighting (Lezer Python) | `python()` |
| Line numbers | `lineNumbers()` |
| Active line & gutter highlight | `highlightActiveLine()`, `highlightActiveLineGutter()` |
| Undo/Redo | `history()`, `historyKeymap`, `cmUndo/cmRedo` |
| Bracket matching | `bracketMatching()` |
| Auto indent on input | `indentOnInput()` |
| Indent unit 4 spasi | `indentUnit.of("    ")` |
| Multi/rectangular selection | `drawSelection()`, `rectangularSelection()` |
| Drop cursor | `dropCursor()` |
| Line wrapping | aktif (konfigurasi view) |
| Autocomplete (kasta 1+2, offline) | `autcompletion()` — kata dokumen + keyword + builtins + snippet |
| Find/Replace (panel bawaan) | `search()`, `searchKeymap`, `openSearchPanel` |
| **Code folding** (ralat 2026-08-15) | `foldGutter()` + `foldKeymap` — AKTIF di `buildState()` |
| **Close brackets** (F1.7, ralat 2026-08-15) | `closeBrackets()` via `closeBracketsCompartment` — toggle Settings via bridge `setCloseBrackets()` |
| **Selection match highlight** (F1.8, ralat 2026-08-15) | `highlightSelectionMatches()` via Compartment — toggle via bridge `setHighlightSelectionMatches()` |
| Font family dinamis | `fontFamilyCompartment` — bridge `setFontFamily()` dari Settings |
| Tema OLED fixed | `EditorView.theme({...})` + `HighlightStyle` |
| Bridge Kotlin↔JS | `setCode`, `getCode`, `insertText`, `undo`, `redo`, `duplicateRows`, `toggleCommentLines`, `onEditorReady`, `ZCODE.onCodeChange`, `openFind`, `gotoLine(n)` |

Kontrak bridge dipertahankan 1:1 dengan versi Ace (lihat `MIGRASI_CM6.md §3`).

---

## 3. Fitur yang BELUM dipakai (peluang & catatan)

> Ralat 2026-08-15: baris lama "Code Folding / Close brackets / Selection match
> = belum" DIHAPUS — ketiganya sudah aktif (lihat §2). Tabel ini sekarang hanya
> berisi yang benar-benar belum ada, diperluas hasil riset ekosistem
> (codemirror.net/docs/extensions + changelog + community Replit dkk).

### 3a. NOL dependensi baru — modul sudah di-vendor, tinggal import + rebuild

Semua baris ini ada di paket yang SUDAH ada di `package.json` (meta-paket
`codemirror` membawa `@codemirror/view`, `commands`, `language`, `search`);
esbuild men-tree-shake yang tidak diimpor, jadi biaya = beberapa KB bundle.

| Fitur | Sumber | Nilai untuk ZCODE | Prioritas |
|---|---|---|---|
| **`highlightSpecialChars`** | `@codemirror/view` | Menampakkan karakter siluman (NBSP, zero-width, tab nyasar) — sumber `IndentationError`/`SyntaxError` misterius saat user copas kode dari chat/web. Kelas bug pemula #1 di Python | **TINGGI** |
| **`highlightTrailingWhitespace`** | `@codemirror/view` (≥6.7.0) | Pasangan visual "Auto Trim on Run" — user LIHAT spasi buntut sebelum trim menghapusnya | TINGGI |
| **`deleteTrailingWhitespace`** | `@codemirror/commands` | Perintah siap pakai untuk aksi **"Trim Now"** (F1.9 TOOLS_CATALOG) — tidak perlu tulis sendiri | TINGGI |
| **`placeholder`** | `@codemirror/view` | Hint saat dokumen kosong (`# tulis kode Python di sini…`) — onboarding pemula, gratis | SEDANG |
| **`scrollPastEnd`** | `@codemirror/view` | Baris terakhir bisa discroll ke atas viewport — penting di HP karena keyboard menutupi 40% layar bawah | SEDANG |
| **`highlightWhitespace`** | `@codemirror/view` (≥6.7.0) | Render semua spasi/tab sebagai titik — berguna untuk debug indentasi, tapi bising; kalau dipasang HARUS toggle (default OFF). Sejak view 6.34.0 pakai CSS background (murah) | RENDAH (toggle) |
| **`hoverTooltip` / `tooltips`** | `@codemirror/view` | Baru bernilai bila ada sumber konten (signature/doc dari jedi — lihat backlog kasta 3). Catat, jangan pasang duluan | NANTI (nunggu jedi) |
| **`phrases`** | `@codemirror/state` | i18n teks UI editor — panel Find/Replace bisa **berbahasa Indonesia** ("Cari", "Ganti", "Semua") konsisten dengan bahasa app | SEDANG (unik!) |

### 3b. Dependensi BARU kecil — dievaluasi ketat (aturan ramping §6)

| Fitur | Paket | Nilai untuk ZCODE | Catatan risiko |
|---|---|---|---|
| **Lint / Problems** | `@codemirror/lint` (`linter`, `lintGutter`, `lintKeymap`) | Fondasi **VPP** (`VPP_DESIGN.md` Opsi B): squiggle + ikon gutter dari `Checker.kt`/pyflakes | Paket official, kecil. Satu-satunya gap "ditunggu" yang tersisa |
| **Indentation markers** | `@replit/codemirror-indentation-markers` | Garis vertikal level indent — Python hidup-mati oleh indentasi; di layar sempit + font kecil ini penyelamat. Dipakai produksi Replit | Set `highlightActiveBlock: false` (opsi resmi utk performa). Dampak WebView ARMv7 = **UNTESTED**, ukur di full emulator dulu |
| **Wrapped line indent** | `codemirror-wrapped-line-indent` (npm, 0 deps) | ZCODE pakai `lineWrapping` — baris panjang yang wrap saat ini jatuh ke kolom 0, merusak persepsi struktur indentasi Python. Extension ini membuat sambungan wrap ikut level indent | Paket komunitas kecil (MIT). Alternatif: gist "awesome-line-wrapping" (vendor manual). Uji performa dokumen panjang |
| **Rainbow brackets** | `eriknewland/rainbowbrackets` | Warna kurung per kedalaman — nilai kecil untuk Python (jarang nesting dalam); `bracketMatching` sudah cukup | RENDAH; penulisnya sendiri bilang eksperimental. Lewati kecuali diminta user |

### 3c. Ditolak sadar / tidak relevan (dengan alternatif ZCODE)

| Fitur ekosistem | Kenapa tidak | Alternatif yang sudah ada |
|---|---|---|
| **LSP via WebSocket** (`codemirror-languageserver`) | Butuh server + socket; salah arsitektur untuk in-process Android | Jedi via bridge PyCall (backlog kasta 3) — hasil sama tanpa server |
| **Minimap** (`@replit/codemirror-minimap`) | Ditolak PRD §7: makan layar & daya HP ampas | gotoLine + Find + (nanti) Outline dari AST |
| **Multi-bahasa** (`@codemirror/lang-*`) | Bundle bengkak; ZCODE Python-first | 1 bahasa, dipin |
| **Theme packs** (`@uiw/codemirror-theme-*` 40+) | Lock true-black OLED (daya + keterbacaan) adalah keputusan | Palet ANSI di terminal, bukan di editor |
| **Vim/Emacs/VSCode keymap** (`@replit/*`) | Layar sentuh tanpa Ctrl/Esc fisik | EDITOR HANDLE + sticky CTRL |
| **Emmet** (`@emmetio/codemirror6-plugin`) | HTML/CSS-centric | Snippet Python di autocomplete kasta 1+2 |
| **Interact / drag angka** (`@replit/codemirror-interact`) | Butuh hold **Alt** + drag = gestur desktop, mustahil di sentuh | — (tidak dibutuhkan) |
| **Collab & Merge** (`@codemirror/collab`, `merge`) | Butuh server sinkronisasi / workflow desktop | Di luar visi ZCODE |
| **`crosshairCursor`** | Perilaku tombol Alt + mouse | — |

---

## 4. Estimasi pemanfaatan (diralat 2026-08-15)

- **~90% kapabilitas inti yang relevan** untuk editor Python mobile sudah
  dipakai (highlight, edit, sejarah, bracket, indent, find/replace,
  autocomplete, folding, close-brackets, selection-match, Compartment toggles).
- Gap fungsional tersisa dua kelas saja:
  1. **`@codemirror/lint`** — fondasi VPP, satu-satunya modul official
     bernilai tinggi yang belum diimpor (ZCODE masih `Checker.kt` sendiri).
  2. **Quick win §3a** — modul yang sudah di-vendor tapi belum diimpor
     (`highlightSpecialChars`, `highlightTrailingWhitespace`,
     `deleteTrailingWhitespace`, `placeholder`, `scrollPastEnd`, `phrases`).
- Fitur ekosistem lain absen **karena ditolak sadar** (§3c), bukan karena lupa.

---

## 5. Tema & gaya (OLED)

- Kursor/drop cursor berwarna hijau neon (`#39FF14`) — nuansa retro fosfor.
- Ada gaya spesifik untuk panel Find/Replace (anti "gelap-di-gelap") dan
  `.cm-searchMatch` / `.cm-searchMatch-selected`.
- Gaya `.cm-selectionMatch` sudah didefinisikan **dan sejak F1.8 sudah
  dipakai** oleh `highlightSelectionMatches()` (ralat 2026-08-15; catatan lama
  "nganggur" tidak berlaku lagi).
- Lihat `TERMINAL_THEMES.md` untuk palet yang terkoordinasi dengan tema app.

---

## 6. Aturan kalau mau menambah/ubah

1. **Tambah dependensi = tambah ukuran bundle.** Patuhi "ramping"; ukur
   sebelum/ sesudah bundle (`codemirror.bundle.js`, wajib >100 KB per
   `verifyEditorBundled`).
2. **Pin versi eksak** di `package.json`; jalankan `npm run build` dan
   commit bundle hasilnya.
3. **Jangan mematahkan kontrak bridge** (`setCode/getCode/...`) — dijaga
   test `verifyEditorBundled`.
4. Perubahan tema/bahasa sebaiknya pakai **`Compartment`** agar bisa di-reconfigure
   tanpa recreated editor (penting untuk performa, lihat `PERF_PASS.md`).
5. Setiap penambahan dievaluasi terhadap HP ampas ARMv7 (offline, ringan).

---

## 7. Hubungan dengan dokumen lain

- `VPP_DESIGN.md` — memakai `@codemirror/lint` (Opsi B) atau `Checker.kt` (Opsi A).
- `TOOLS_CATALOG.md` — folding/close brackets/selection match = kandidat tools.
- `PERF_PASS.md` — reconfigure via Compartment, hindari recreate WebView.
- `docs/MIGRASI_CM6.md` — kontrak bridge & alasan migrasi.

---

*Peta kapabilitas — bukan daftar keinginan. Tiap penambahan diuji terhadap
ukuran bundle, performa HP ampas, dan kontrak bridge yang sudah ada.*
