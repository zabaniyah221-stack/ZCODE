# 🗺️ CM6_FEATURE_MAP — Peta Fitur CodeMirror 6 di ZCODE (2026-08)

Dokumen ini memetakan **apa saja kapabilitas CodeMirror 6 yang sudah dipakai**
dan **mana yang belum** di editor ZCODE. Sumber: `editor-src/src/editor.js`
dan `editor-src/package.json`. Tujuannya jujur: tahu persis fondasi editor
sebelum menambah fitur (VPP, folding, bracket auto-close, multi-bahasa, dll).

Lihat juga `docs/MIGRASI_CM6.md` untuk sejarah migrasi Ace → CodeMirror 6.

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
| Tema OLED fixed | `EditorView.theme({...})` + `HighlightStyle` |
| Bridge Kotlin↔JS | `setCode`, `getCode`, `insertText`, `undo`, `redo`, `duplicateRows`, `toggleCommentLines`, `onEditorReady`, `ZCODE.onCodeChange`, `openFind`, `gotoLine(n)` |

Kontrak bridge dipertahankan 1:1 dengan versi Ace (lihat `MIGRASI_CM6.md §3`).

---

## 3. Fitur yang BELUM dipakai (peluang & catatan)

| Fitur | Paket CM6 | Status di ZCODE | Catatan |
|---|---|---|---|
| **Lint / Problems** | `@codemirror/lint` | **Belum diimpor** | Fondasi untuk VPP (lihat `VPP_DESIGN.md` Opsi B); butuh sumber diagnostic (Lezer/Checker/pyflakes) |
| **Code Folding** | `@codemirror/language` (`foldGutter`) | **Sengaja OFF** | Disebut eksplisit `showFoldWidgets: false` / `foldGutter` tidak dipasang. **VS Code punya, ZCODE belum** — kandidat TOOLS |
| **Close brackets** | `@codemirror/autocomplete` (`closeBrackets`) | **Belum diaktifkan** | Sudah ada di dependensi autocomplete — **TRIVIAL** tinggal pasang |
| **Selection match highlight** | `@codemirror/search` (`highlightSelectionMatches`) | **Belum** | Style `.cm-selectionMatch` sudah ada di tema tapi "nganggur" — **TRIVIAL** |
| **Highlight active word** | `@codemirror/view` | Belum | Opsional |
| **Multi-bahasa** | `@codemirror/lang-*` | **Hanya Python** | Butuh tambah dependensi + `Compartment` untuk ganti bahasa; **memperbesar bundle** (bertentangan dengan "ramping") |
| **Banyak tema / theme switch** | `EditorView.theme` + `Compartment` | **1 tema fixed OLED** | Editor theme ikut app theme butuh `Compartment.set` + bridge `setTheme` + rebuild; **membatalkan** lock "selalu true-black OLED" bila tidak hati-hati |
| **Keymap ekstra (Vim/Emacs)** | `@codemirror/commands` dll | Tidak | Tidak prioritas |

---

## 4. Estimasi pemanfaatan

- **~75–80% kapabilitas inti yang relevan** untuk editor Python mobile sudah
  dipakai (highlight, edit, sejarah, bracket, indent, find/replace, autocomplete).
- **~15–25% ekosistem paket** yang dieksploitasi (3 dari sekian banyak paket
  `@codemirror/*`; 1 bahasa; 1 tema).
- Yang **paling relevan dengan VPP** = `@codemirror/lint` yang belum dipakai;
  ZCODE saat ini mengandalkan `Checker.kt` sendiri (scanner 1 error).

---

## 5. Tema & gaya (OLED)

- Kursor/drop cursor berwarna hijau neon (`#39FF14`) — nuansa retro fosfor.
- Ada gaya spesifik untuk panel Find/Replace (anti "gelap-di-gelap") dan
  `.cm-searchMatch` / `.cm-searchMatch-selected`.
- Gaya `.cm-selectionMatch` **sudah didefinisikan** namun belum ada ekstensi
  yang menandainya (kandidat "selection match" murah).
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
