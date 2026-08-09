# MIGRASI_CM6 — Rencana Massive Refactor: Ace → CodeMirror 6

**Status:** 🟢 DIEKSEKUSI 2026-08-09 (branch `arena/019fe472-zcode`) — lihat **Log Eksekusi** di bawah.
Sisa yang belum selesai: edit `.github/workflows` (wilayah user, §7) + QA device fisik (§F9).
**Keputusan user:** massive refactor — hilangkan Ace dan semua yang berkaitan, migrasi penuh ke CM6
"tanpa terlewatkan apapun bahkan satu syntaxpun".
**Prinsip repo yang mengikat dokumen ini:**
1. **Jujur** — semua perubahan perilaku (behavior diff) ditulis apa adanya, termasuk yang jelek.
2. **Teliti** — semua case sekecil apapun diinventaris; tidak ada asumsi tanpa bukti dari source.

---

## 1. Hasil Audit Sejarah — case dari semua branch & PR yang TIDAK BOLEH terulang

Semua branch remote (`arena/019fddf3`, `019fe1fa`, `019fe38d`, `019fe191`, `019fe230`,
`fix/editor-focus-and-startup-text-…`) sudah **fully merged ke `main`** (0 commit unik) —
tidak ada kerjaan terlantar. Pelajaran dari sejarah PR:

| Sumber | Case / Pelajaran | Implikasi ke migrasi ini |
|---|---|---|
| **PR #5** | Editor blank saat startup (race condition) → fix `onEditorReady()` handshake JS→Kotlin + `.post { evaluateJavascript }` | Handshake **wajib dipertahankan 1:1** di index.html baru |
| **PR #5** | Keyboard tidak muncul saat tap → `isFocusable=true`, `isFocusableInTouchMode=true`, `OnTouchListener` + `showSoftInput` | Kode Kotlin EditorScreen **tidak boleh disentuh sembarangan**; ini bukan masalah engine |
| **PR #6** | Find & Palette mati karena ext tidak ter-bundle — ditemukan lewat **audit source, bukan tebakan** | Prinsip sama: semua item/fungsi harus diverifikasi hidup dari source CM6 sebelum klaim selesai |
| **PR #6** | Teks popup "gelap-di-gelap" (palette/search/autocomplete default terang di atas OLED) | Semua panel CM6 (search, tooltip autocomplete) **wajib** di-style OLED sejak awal, bukan belakangan |
| **PR #6** | 1 item palette mati (`showSettingsMenu`) disedikitkan dari daftar — 100% item tertampil harus berfungsi | Tidak boleh ada tombol/item UI yang memanggil fungsi JS yang tidak ada di bundle |
| **PR #6** | Pin versi konsisten di 3 file (anti-drift F-09) | Versi paket CM6 dipin eksplisit di `package.json` + lockfile + dicatat di dokumen ini |
| **PR #3** | Bencana CI ~18 commit bisection karena akar masalah tak ditemukan; stub bikin hijau palsu | **Dilarang men-stub apapun** agar tes hijau. Kalau gagal, cari akar masalah. CI buildPython harus tetap Python 3.11, Gradle 8.5 |
| **PR #1 / PLAN** | Kelas bug F-01/S-27/C-50 (HTTP loopback) dihapus via `file://` + `addJavascriptInterface` | Arsitektur bridge **tidak berubah** — CM6 tetap jalan di WebView `file://` |
| **ZMUX lesson** (komentar EditorScreen) | Debounce resize 100ms agar prompt tidak loncat | Tetap berlaku (MainActivity, tidak berubah) |
| **test suite** | 125 test struktural anti-regresi | Jumlah test **tidak boleh berkurang**; test Ace di-rewrite 1:1 menjadi test CM6 |

---

## 2. Inventaris lengkap semua touchpoint Ace (tidak ada yang terlewat)

### 2.1 Aset yang DIHAPUS total (1,08 MB)
| File | Ukuran |
|---|---|
| `app/src/main/assets/editor/ace/ace.js` | 907.754 B |
| `app/src/main/assets/editor/ace/ext-prompt.js` | 130.895 B |
| `app/src/main/assets/editor/ace/ext-searchbox.js` | 20.667 B |
| `app/src/main/assets/editor/ace/mode-python.js` | 16.876 B |
| `app/src/main/assets/editor/ace/theme-tomorrow_night_eighties.js` | 4.407 B |

Sebelum dihapus: buat **git tag `ace-final`** sebagai titik rollback (Ace hidup selamanya di history).

### 2.2 File yang DIREWRITE
| File | Aksi |
|---|---|
| `app/src/main/assets/editor/index.html` (253 baris) | Rewrite penuh: buang semua `<script src="ace/…">`, semua CSS `.ace_*` (~120 baris override OLED), semua kode `ace.edit` → ganti bundle CM6 + bridge baru (kontrak §3) + tema CM6 deklaratif |

### 2.3 File yang DIEDIT
| File | Yang berubah |
|---|---|
| `app/src/main/java/com/zaba/zcode/ui/editor/EditorScreen.kt` | Hanya komentar/dok (`Ace 1.44.0` → `CodeMirror 6`). **Logika fokus, handshake, escapeJavaScriptString, EditorBridge TIDAK berubah** — nama fungsi bridge dipertahankan sama |
| `app/src/main/java/com/zaba/zcode/ui/workbench/WorkbenchScreen.kt` | Tambah 1 item palette **"Find in File"** → `evaluateJavascript("openFind();")` (pengganti akses Find yang tadinya via mobile-menu Ace). 6 pemanggilan JS lain (`setCode`×3, `duplicateRows`×2, `toggleCommentLines`×2, `insertText`) **tidak berubah** karena nama fungsi dipertahankan |
| `app/build.gradle.kts` | Task `verifyAceBundled` → `verifyEditorBundled` (cek `assets/editor/codemirror.bundle.js` ada, > 100KB, bukan stub) |
| `tools/check.sh` | Step `[1/8]`: guard Ace → guard CM6 (bundle ada, ukuran, tanpa string CDN, marker Lezer-python ada di bundle) |
| `test_zcode_fase0.py` | Konstanta `ACE_JS` → `CM6_BUNDLE`; `test_plan_ace_144`, `test_ace_exists`, `test_ace_is_144`, `test_ace_mode_python_exists`, `test_ace_no_cdn` → padanan CM6 (jumlah test tetap) |
| `test_zcode_fase1.py` | `test_ace_real_not_stub`, `test_ace_version_144`, `test_ace_mode_python_real` → padanan CM6 (jumlah test tetap) |
| `README.md` | Section "🧩 Editor" + baris guard (`Ace 1.44.0 bundled asli` → `CodeMirror 6 bundled`) |
| `docs/DESIGN_ZCODE.md` | Baris 8, 47, 127, 133 — referensi Ace/ext-searchbox/multiSelect → CM6 |
| `docs/PLAN_ZCODE.md` | **Tidak dirombak** (dokumen sejarah) — ditambah banner `SUPERSEDED oleh docs/MIGRASI_CM6.md` di atas |
| `.gitignore` | Tambah `node_modules/` (saat ini hanya `__pycache__` + `*.pyc`) |

### 2.4 File BARU
| File | Isi |
|---|---|
| `editor-src/package.json` | Dependensi CM6 **versi pinned** + `esbuild` (devDep). Nama & versi final dicatat di §4 |
| `editor-src/package-lock.json` | Reproducibility (anti-drift, prinsip F-09) |
| `editor-src/build.mjs` | Script esbuild → output single-file IIFE |
| `editor-src/src/editor.js` | Entry: setup CM6 + tema OLED + bridge function (§3) |
| `app/src/main/assets/editor/codemirror.bundle.js` | **Hasil build, DI-COMMIT** (offline-first; CI/APK build tidak butuh Node) |
| `docs/MIGRASI_CM6.md` | Dokumen ini |

### 2.5 Wilayah USER (`.github/workflows`) — SAYA TIDAK SENTUH
Kedua file ini identik dan memuat 3 referensi Ace:
- `.github/workflows/build.yml`
- `ci/workflows/build.yml`

Perubahan yang dibutuhkan (diff persis ada di §7).
- Raw: `https://raw.githubusercontent.com/muzape28-blip/ZCODE/main/.github/workflows/build.yml`
- Edit: `https://github.com/muzape28-blip/ZCODE/edit/main/.github/workflows/build.yml`
- Raw mirror: `https://raw.githubusercontent.com/muzape28-blip/ZCODE/main/ci/workflows/build.yml`
- Edit mirror: `https://github.com/muzape28-blip/ZCODE/edit/main/ci/workflows/build.yml`

(Kalau user berubah pikiran dan mengizinkan saya mengeditnya langsung di branch ini, bilang saja.)

---

## 3. Kontrak Bridge JS (dipertahankan 1:1 — ini kunci "tanpa terlewat")

Semua nama fungsi yang dipanggil Kotlin **dipertahankan identik** di CM6, sehingga sisi
Kotlin praktis tanpa perubahan:

| Fungsi | Perilaku saat ini (Ace) | Implementasi CM6 |
|---|---|---|
| `setCode(code)` | `editor.setValue(code,-1)` + guard `isSettingValue` anti echo-loop | `view.dispatch({changes: {from:0, to: doc.length, insert: code}})` + flag guard yang sama |
| `getCode()` | `editor.getValue()` | `view.state.doc.toString()` |
| `insertText(text)` | `editor.insert(text); editor.focus()` | dispatch insert di posisi selection + `view.focus()` (QuickTools chips) |
| `undo()` / `redo()` | bawaan Ace | `undo(view)` / `redo(view)` dari `@codemirror/commands` |
| `duplicateRows()` | custom: copy baris selection → insert di bawah | custom CM6: hitung line range dari `state.selection`, insert duplikat sebagai transaction tunggal (undoable) |
| `toggleCommentLines()` | custom regex per-baris + `session.replace` + clearSelection | custom CM6 dengan semantik **identik** (`# ` saat comment, hapus 1 `#`+spasi opsional saat uncomment) — output teks harus byte-identik dengan versi Ace untuk input yang sama |
| `onEditorReady()` (JS→Kotlin) | dipanggil setelah init selesai | dipanggil setelah `new EditorView(...)` sukses (handshake PR #5) |
| `ZCODE.onCodeChange(code)` | dipanggil tiap change (kecuali saat `setCode`) | `EditorView.updateListener` dengan guard yang sama |
| **BARU** `openFind()` | — (Find tadinya hanya via mobile-menu Ace) | `openSearchPanel(view)` dari `@codemirror/search` — dipanggil item palette Compose baru |

Konfigurasi editor yang dipertahankan (dari index.html lama, baris 158–170):
font 12px, gutter ON, **fold widgets OFF** (permintaan user), highlight active line,
`wrap: true`, cursor line, `tabSize: 4`, soft tabs, tanpa print margin,
autocomplete OFF untuk sekarang (roadmap LSP nanti).

---

## 4. Bundle CM6 — komposisi & pipeline

**Paket — versi AKTUAL yang di-pin eksak (tanpa ^/~, anti-drift F-09):**
- `codemirror` **6.0.2** (meta; npm registry 2026-08-09)
- `@codemirror/lang-python` **6.2.1** (sama dengan pin Acode-Foundation/Acode — harta karun)
- `esbuild` **0.28.2** (devDep)

**⚠️ Deviasi jujur dari rencana awal:** bundle TIDAK memakai `basicSetup`. Ekstensi
dipilih eksplisit (lebih kurus & terkontrol) dan paket `autocomplete`/`lint` **sengaja
belum di-import** — dipasang di fase Problems Panel / LSP sesuai roadmap. Konsekuensi:
pondasi autocomplete tidak "gratis" di bundle ini; harus rebuild saat fase itu (satu
perintah `npm run build`). Ini keputusan sadar, bukan kelalaian.

**Build:** `editor-src/build.mjs` → esbuild, `format: iife`, `minify: true`,
`target: es2018` (envelope WebView Android 8 / Chrome 63+; sudah dianalisis di diskusi
kompatibilitas armv7), output → `app/src/main/assets/editor/codemirror.bundle.js`.

**Kejujuran pipeline:** Node v22 + npm 10 tersedia di sandbox ini, jadi bundle
dihasilkan & di-commit dari sini. CI build APK **tidak butuh Node sama sekali**.
Regenerasi bundle di masa depan = `cd editor-src && npm ci && npm run build`.

**Ukuran:** target < 400KB (vs 1.08MB Ace) → APK kurus ~700KB.

---

## 5. Perubahan perilaku (HONEST BEHAVIOR DIFF — prinsip #1)

| Aspek | Sebelum (Ace) | Sesudah (CM6) |
|---|---|---|
| Mobile selection menu "…" (Select All/Find/Palette) | Ada (Ace mobile-menu, di-style OLED) | **Hilang** — pakai selection menu native Android. Find dipindahkan ke item palette Compose + panel search CM6 (Mod-f) |
| Find di HP | Via mobile-menu Ace | Via palette 🔍 → "Find in File" (panel search CM6, di-style OLED) |
| Item palette internal Ace (goto line, change mode, goto error via `error_marker`, fold) | Ada di mobile-menu Ace | Tidak ada padanan langsung; **belum dibangun** — masuk backlog (jujur, tidak diklaim sebagai fitur migrasi ini) |
| Multi-cursor (desktop Ctrl+Click) | Ace multiSelect | CM6 belum punya true multi-caret (hanya rectangular selection) — **tidak relevan di mobile**, tapi dicatat jujur |
| Diagnostic (Checker.kt) | Banner warning Compose (tidak menyentuh Ace) | **Tidak berubah** — Checker tetap banner; integrasi gutter via `@codemirror/lint` adalah fase berikutnya (roadmap) |
| Startup text & keyboard focus | Handshake + fokus Kotlin | Identik (Kotlin tidak berubah) |
| WebView lama tanpa Play Store (Chrome 58–62 stock) | Ace jalan | CM6 target es2018 + **feature-guard**: kalau init gagal, tampilkan pesan ramah di WebView, bukan layar putih |

---

## 6. Alur Task (fase eksekusi — setelah approval)

- [x] **F0 — Persiapan:** tag `ace-final` dibuat ✅; `node_modules/` masuk `.gitignore` ✅
- [x] **F1 — Bundle:** `editor-src/` (package.json pinned + lockfile + build.mjs + src/editor.js) ✅; bundle **399.612 bytes** ✅; verifikasi: syntax `node --check` OK, nol CDN (satu-satunya string http = namespace SVG w3.org, bukan request), marker `nonlocal` (Lezer-python) ada ✅. **1 kegagalan build terdeteksi & diperbaiki jujur** (import `lineWrapping` seharusnya `EditorView.lineWrapping`)
- [x] **F2 — index.html baru:** bridge §3 (8 fungsi lama + `openFind`) ✅, tema OLED deklaratif ✅, feature-guard WebView tua (pesan ramah, bridge no-op) ✅, scrollbar webkit dipertahankan ✅
- [x] **F3 — Kotlin:** komentar EditorScreen/WorkbenchScreen diperbarui ✅; +1 item palette "Find in File" ✅; 6 pemanggilan JS lain tidak berubah ✅. **Jujur:** kompilasi Kotlin tidak bisa diverifikasi di sandbox (tanpa JDK/Android SDK) — menunggu CI `assembleDebug`
- [x] **F4 — Gradle:** `verifyAceBundled` → `verifyEditorBundled` (cek ukuran + kontrak bridge + wiring index.html) ✅
- [x] **F5 — Guard & test:** `tools/check.sh` [1/8] + rewrite test fase0/fase1 ✅ — **125/125 pytest hijau + check.sh 8/8 langkah hijau** ✅
- [x] **F6 — Penghapusan total:** 5 file Ace (1,08 MB) di-`git rm` ✅; grep final word-boundary: nol referensi fungsional (sisa = komentar provenance sah + wilayah user §7) ✅
- [x] **F7 — Dokumentasi:** README, DESIGN_ZCODE.md, banner PLAN_ZCODE.md, dokumen ini ✅
- [ ] **F8 — Wilayah user:** diff + link sudah diserahkan (lihat chat report); **belum dieksekusi — menunggu user**
- [ ] **F9 — QA checklist (12 perilaku) di device fisik:** startup text, tap→keyboard, ketik→autosave, QuickTools insert (termasuk `Tab`→4 spasi), Duplicate Line, Toggle Comment (drawer + palette), pindah tab tanpa lompat kursor, Find panel, warna OLED semua surface, gutter ramping, wrap mode, terminal layer-switch tidak terpengaruh — **menunggu APK dari CI + test user di device**

## 7. Diff persis untuk `.github/workflows` (wilayah user)

**Perubahan 1** — step verifikasi APK (baris ~80):
```yaml
      # SEBELUM:
      - name: Verify APK contains Ace + taskAffinity
        run: |
          ...
          unzip -l "$APK" | grep -i -E "ace.js|AndroidManifest" | head -n 20
      # SESUDAH:
      - name: Verify APK contains CodeMirror 6 + taskAffinity
        run: |
          ...
          unzip -l "$APK" | grep -i -E "codemirror.bundle.js|AndroidManifest" | head -n 20
```
**Perubahan 2** — CI Summary (baris ~104):
```
- "- Editor: OLED #050806 Ace 1.44.0 file:// + gutter 40px"
+ "- Editor: OLED #050806 CodeMirror 6 bundled file:// + gutter ramping"
```
Kedua file (`.github/` dan `ci/`) harus diedit **identik** (konvensi repo sejak PR #3).

## 8. Risiko & mitigasi
| Risiko | Level | Mitigasi |
|---|---|---|
| WebView stock tua (tanpa Play) gagal parse bundle | 🟡 | target es2018 + try/catch init + pesan fallback; QA matrix |
| Feel touch CM6 beda di device ARMv7 low-end | 🟡 | QA F9 di device fisik sebelum klaim selesai; tag `ace-final` untuk rollback |
| Regresi perilaku tak terlihat (echo-loop, kursor lompat) | 🟡 | guard `isSettingValue` dipertahankan; pola `setCode` on-demand dari Workbench dipertahankan |
| Drift versi paket di masa depan | 🟢 | lockfile + versi pinned + tercatat di dokumen ini (F-09) |
| Lupa satu referensi Ace | 🟢 | F6 grep final wajib nol; guard test baru menolak string `ace/` |

## 9. Rollback
`git revert <merge-commit>` atau restore dari tag `ace-final`. Semua guard/test lama
hidup di tag tersebut.

## 10. Out of scope (fase berikutnya, bukan migrasi ini)
LSP/jedi autocomplete (sudah siap pondasi `@codemirror/autocomplete` via basicSetup),
Visual Problems Panel via `@codemirror/lint`, tema Dracula/Tokyo-Night untuk editor,
palette internal (goto line dsb.).

---

## 11. Log Eksekusi (2026-08-09)

1. Tag `ace-final` dibuat di HEAD sebelum perubahan (rollback point).
2. `editor-src/` dibuat: package.json (pin eksak), package-lock.json, build.mjs (esbuild
   IIFE, minify, target es2018), src/editor.js (bridge + tema).
3. Build pertama **gagal** — `lineWrapping` bukan named export @codemirror/view.
   Diperbaiki jadi `EditorView.lineWrapping`. Build kedua sukses: **399.612 bytes**
   (Ace sebelumnya 1.080.599 bytes → **-63%**).
4. Verifikasi bundle: `node --check` lolos; marker bridge + `nonlocal` + `cm-editor` ada;
   satu-satunya string http = `http://www.w3.org/2000/svg` (namespace createElementNS,
   bukan network request) — offline-first aman.
5. `index.html` di-rewrite penuh (253 → 59 baris); semua CSS `.ace_*` & script Ace hilang.
6. Kotlin: komentar EditorScreen + WorkbenchScreen; palette +`"Find in File" → openFind()`.
7. Gradle guard + check.sh + pytest fase0/fase1 di-rewrite 1:1 (jumlah test tetap **125**).
8. `git rm` 5 file Ace. Grep final word-boundary: bersih.
9. Hasil verifikasi lokal: **pytest 125/125 ✅, tools/check.sh 8/8 ✅**.

**Yang masih terbuka (jujur):**
- Edit `.github/workflows/build.yml` + `ci/workflows/build.yml` — wilayah user (§7).
  Tanpa edit ini CI step "Verify APK contains Ace" akan **gagal** karena mencari `ace.js`.
- CI `assembleDebug` (kompilasi Kotlin) — baru bisa dibuktikan setelah workflow diupdate.
- QA device fisik F9.
- Rollback cepat tersedia: `git revert` atau checkout tag `ace-final`.
