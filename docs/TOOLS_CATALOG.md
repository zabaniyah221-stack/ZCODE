# 🧰 TOOLS_CATALOG — Audit Tools dari ZABACODE / VS Code / Acode (2026-08)

Dokumen ini mengaudit **ide fitur/tools** dari tiga acuan dan memutuskan mana
yang **layak di-port** ke sidebar **TOOLS** (yang sudah ada, scrollable,
berisi toggle/aksi), mana yang ditunda, dan mana yang jujur dibuang. Tujuannya
bukan "comot semua", tapi kurasi sesuai visi ZCODE: IDE Python di HP ampas,
ringan, offline, in-process Chaquopy/Kotlin.

> Instruksi user (2026-08): *"semua tools yang harus diambil dari repo
> ZABACODE, VSCODE, dan Acode, ditaruh di sidebar bagian TOOLS yang
> scrollable."* Dokumen ini adalah hasil auditnya; implementasi tetap per-commit
> kecil dengan guard + UAT.

---

## 1. Tools ZCODE yang SUDAH ADA (jangan diduplikasi)

Dari `PluginRegistry.kt`, sidebar TOOLS sekarang berisi:

| Tool | Jenis | Keterangan |
|---|---|---|
| Beautifier Pro (Format Code) | ACTION | PEP-8 spasi operator; string/komentar tak disentuh |
| Optimize Auto-Imports | ACTION | Tambah import standar yang terpakai |
| Duplicate Active Line | ACTION | Gandakan baris/seleksi |
| Toggle Line Comment | ACTION | Comment/uncomment |
| Smart Docstring Generator | ACTION (Python) | Docstring PEP-257 via AST |
| Type Hint Generator | ACTION (Python) | Inferensi anotasi dari default argumen |
| Find Duplicate Lines | ACTION (Python) | Deteksi baris duplikat (DRY) |
| TODO Extractor | ACTION | Kumpul TODO/FIXME/HACK, tap → gotoLine |
| Snippet Pack | ACTION | Template Flask/BS4/AsyncIO/REST |
| Auto Trim on Run | BEHAVIOR (off) | Buang trailing space sebelum Run |
| Symbol bar | toggle | Baris simbol cepat |
| THEME ▸ (cycle) | aksi | RETRO → DRACULA → TOKYO_NIGHT |
| Clear All Drafts & Files | aksi (destruktif) | Konfirmasi wajib |

Aturan semantik yang sudah dikunci: ACTION = ON berarti tersedia; tap baris
selalu eksekusi manual. BEHAVIOR = ON berarti jalan otomatis saat Run.

---

## 2. Kandidat dari VS Code

| Tool | Layak? | Alasan & biaya |
|---|---|---|
| **Code Folding** (`foldGutter`) | ✅ LAYAK | ZCODE belum punya; "lipat" fungsi/kelas sangat berguna. Biaya: pasang `foldGutter` CM6 (kecil), rebuild bundle. Lihat `CM6_FEATURE_MAP.md`. |
| **Go to Definition** | ⚠️ TUNDA (butuh LSP/jedi) | Butuh analisis semantik; kasta 3 autocomplete (jedi) = backlog. Versi ringan (jump ke `def` dalam file via regex/AST) bisa lebih dulu. |
| **Rename Symbol** | ⚠️ TUNDA | Butuh scope-aware rename; regex berbahaya. Tunggu LSP. |
| **Organize Imports** | ✅ SEBAGIAN | "Optimize Auto-Imports" sudah ada; tambah pengurutan/penghapusan import tak terpakai (AST, ringan). |
| **Outline / Symbols** | ✅ LAYAK | Daftar fungsi/kelas di file → tap → gotoLine. Bisa dari AST Python (ringan, mirip TODO Extractor). |
| **Toggle Word Wrap** | ✅ LAYAK (kecil) | Sudah wrap; jadikan toggle. |
| **Trim Trailing Whitespace** | ✅ SUDAH ADA (Auto Trim on Run) | Tambah aksi manual "Trim Now". |
| **Change Case** (UPPER/lower/Title) | ✅ LAYAK (kecil) | Transform teks seleksi, Kotlin/JS murni. |
| **Sort Lines** | ✅ LAYAK (kecil) | Transform seleksi. |
| **Command Palette** | ⚠️ SEBAGIAN | Palette `🔍` sudah punya `>` (plugin) & `:`; bisa diperluas daftar perintah. |
| **Minimap** | ❌ BUANG | Makan layar & daya di HP; tidak untuk device ampas. |
| **Multiple cursors lanjut** | ⚠️ SEBAGIAN | rectangular selection sudah ada; multi-cursor mouse tidak relevan di sentuh. |
| **Integrated terminal themes** | ✅ (lihat `TERMINAL_THEMES.md`) | Palet ANSI, bukan fitur editor. |

---

## 3. Kandidat dari Acode

Acode = editor kode Android berbasis WebView/CodeMirror, jadi relevan secara pola.

| Tool | Layak? | Alasan |
|---|---|---|
| **Quick tools bar / gesture** | ✅ SEBAGIAN | ZCODE sudah punya QuickTools/Symbol bar; bisa tambah gesture (swipe untuk tab, long-press) jika ringan. |
| **Preview HTML/Markdown** | ⚠️ TUNDA | Menarik untuk "App Mode"; tapi fokus ZCODE Python. Preview bisa lewat WebView (jalan App Mode Flask). |
| **Remote file (FTP/SFTP)** | ❌ BUANG (dulu) | Di luar visi; butuh dependensi & izin; bisa masuk ZMUX/terpisah. |
| **Find in files / project search** | ✅ LAYAK (nanti) | Berguna; tapi butuh konsep "project/folder". ZCODE saat ini workspace file internal. |
| **Auto-save indicator / status** | ✅ LAYAK (kecil) | Beririsan dengan `PERF_PASS.md` (debounce save); tunjukkan status "Tersimpan". |
| **Emmet** | ❌ BUANG | Untuk web/HTML; ZCODE Python. |
| **Custom key bindings** | ⚠️ TUNDA | Menarik untuk power-user, tapi butuh UI pengaturan. |
| **Color picker** | ❌ RENDAH | Tidak prioritas untuk Python. |

---

## 4. Kandidat dari ZABACODE

Banyak yang **sudah di-port** (lihat §1). `docs/BUGS_AUDIT_ZABACODE_FOR_ZCODE.md`
mencatat bug yang harus dihindari saat mem-port. Yang layak dicek/di-port
(ringan & tidak mengulang bug):

| Tool | Layak? | Catatan |
|---|---|---|
| **Code folding / bracket close** | ✅ LAYAK | Murni editor CM6; lihat `CM6_FEATURE_MAP.md`. |
| **Auto-close brackets** | ✅ TRIVIAL | `closeBrackets` sudah di paket autocomplete ZCODE. |
| **Selection match highlight** | ✅ TRIVIAL | Style `.cm-selectionMatch` sudah ada; tinggal pasang `highlightSelectionMatches`. |
| **Format code lanjutan (isort/autopep8)** | ⚠️ TUNDA | Beautifier Pro sudah ada; autopep8/isort butuh pip (berat) — tawarkan di LIBRARY, jangan dipaksa. |
| **Snippet lanjutan** | ✅ SEBAGIAN | Snippet Pack sudah ada; tambah sesuai permintaan. |
| **Oracle/AI auto-fix** | ⚠️ TERISOLASI | Fitur AI sensitif (privacy/key); bukan tools editor murni. Masuk ranah terpisah dengan guard. |
| **Plugin marketplace** | ❌ JANGAN | "Hukum keluarga": jangan gabung yang belum teruji/eksekusi diam-diam (bug Zabacode). ZCODE pakai plugin terkurasi. |

---

## 5. Ringkasan keputusan

### Prioritas tinggi (ringan, dampak besar)
1. **Close Brackets** (auto) — trivial, sudah di dependensi.
2. **Selection Match Highlight** — trivial.
3. **Code Folding** (`foldGutter`) — fitur yang benar-benar belum ada.
4. **Outline/Symbols** (fungsi/kelas → gotoLine) — pola sama seperti TODO Extractor.
5. **Organize/optimasi imports lanjutan** (urutkan + buang yang tak terpakai).
6. **Transform teks kecil**: Sort Lines, Change Case, Trim Now.

### Prioritas menengah (butuh fondasi)
7. Go to Definition/Rename versi ringan (dalam file) → kemudian LSP/jedi.
8. Find in files (butuh konsep folder/project).
9. Word wrap toggle & status "Tersimpan" (bareng PERF_PASS).
10. Terminal palette (lihat `TERMINAL_THEMES.md`).

### Ditunda / ditolak (jujur)
- Minimap, Emmet, color picker, remote file, plugin marketplace sembarangan,
  multi-cursor mouse — tidak sesuai visi mobile/ringan/Python.

---

## 6. Aturan implementasi (konsisten protokol)

- Tiap tool = **satu commit kecil** + guard (test/`check.sh`) + UAT di ARMv7.
- Perubahan editor **harus rebuild bundle CM6** dan commit hasilnya; jaga
  kontrak bridge (`verifyEditorBundled`).
- Ukur ukuran bundle sebelum/tambah dependensi; jangan tambah yang bikin gendut.
- Jangan memasukkan tool yang punya catatan bug Zabacode tanpa mem-port
  perbaikannya (mis. auto-fix yang merusak kode, B-10/B-11/F-02).
- Semua toggle/aksi mengikuti semantik ACTION/BEHAVIOR yang sudah ada.

---

## 7. Hubungan dengan dokumen lain

- `CM6_FEATURE_MAP.md` — status teknis folding/close brackets/selection match.
- `VPP_DESIGN.md` — `@codemirror/lint` tidak tercampur dengan tools editor.
- `PERF_PASS.md` — semua perubahan editor harus tetap ringan saat mengetik.
- `docs/BUGS_AUDIT_ZABACODE_FOR_ZCODE.md` — bug yang harus dihindari saat port.
- `docs/RENCANA_UPDATE_2026_08.md` — struktur sidebar TOOLS & semantik plugin.

---

*Audit kurasi — bukan kewajiban mengerjakan semua. Tiap tool dievaluasi terhadap
visi "Python IDE di HP ampas": ringan, offline, jujur, tidak bloat.*
