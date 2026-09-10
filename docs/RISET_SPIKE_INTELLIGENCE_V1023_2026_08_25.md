# RISET & DISKUSI — Spike Intelligence untuk v1.0.23 (2026-08-25)

**Status: RESEARCH / DISKUSI — belum ada kode, belum ada keputusan user.**
Trigger: user memprioritaskan Spike Intelligence untuk v1.0.23, tetapi minta
diskusi dulu: (1) fungsinya seperti apa bagi user, (2) apakah benar-benar
bersahabat dengan ZCODE. Mode riset: **luas** (SKILL 11: riset harus
mengubah keputusan atau dinyatakan netral).
Basis: `main` @ `26bc8a0` (tag `v1.0.22`), PR #31 head `8775958` (DRAFT,
kandidat tunggal per review `REVIEW_PR30_31_32_2026_08_24.md`).

---

## 0. Prasyarat apa pun arahnya (bukan bagian riset ini)

1. **Fase 0-style v1.0.22**: repo masih menulis "produksi = 1.0.21" (PRD),
   release notes v1.0.22 "NOT RELEASED" — padahal tag ada & user melaporkan
   berjalan sempurna. Rekaman rilis + guard post-release harus ditutup dulu.
2. **Updater one-tap belum pernah diuji device** — fitur itu ADA DI v1.0.22,
   jadi mustahil dipakai untuk memasang v1.0.22 sendiri (v1.0.21 belum punya).
   Siklus update **v1.0.22 → v1.0.23 = UAT pertama updater**
   (`UPDATE_INSTALLED 25→26`). Konsekuensi desain: v1.0.23 harus layak
   di-update, tidak boleh rakus scope.

---

## 1. Fungsinya seperti apa bagi user (UX)

Spike Intelligence = lapisan "editor paham Python" di atas editor teks.
Lima kemampuan (bisa masuk sebagian, tidak harus semua — lihat §5):

| # | Kemampuan | Yang user rasakan di HP |
|---|---|---|
| 1 | **Lint** (pyflakes) | Setelah berhenti ngetik / via palette: muncul daftar masalah nyata — `undefined name`, `unused import`, dll — plus marker merah/kuning di gutter (`setDiagnostics` bridge **sudah ada** di editor). Menuju "kenapa script gw error?" dijawab SEBELUM Run. |
| 2 | **Autocomplete kasta 3** (jedi) | Ketik `os.` → daftar method/attribut asli hasil analisis; ketik nama variabel/fungsi sendiri → muncul; beda dengan kasta 1+2 (kata-dalam-dokumen + keyword statis) yang sudah ada sekarang. |
| 3 | **Go-to definition** (jedi) | Cursor/palette di atas simbol → lompat ke baris definisinya (pola `gotoLine` yang sudah ada). |
| 4 | **Kompleksitas** (mccabe) | Info: "fungsi ini terlalu berbelit (complexity 14)" — kualitas kode, sifatnya saran. |
| 5 | **Rename/Extract aman** (rope) | Ganti nama simbol sekali tap di SEMUA tempat yang benar — TANKA menyentuh string/komentar (kontrak fail-closed rename v1.0.21). |

Sifat penting: **semua lokal, semua gratis, semua opsional**. Tanpa deps
terpasang, tiap layer fallback diam-diam (arsitektur PR #31) — perilaku app
identik dengan hari ini. Uninstall deps = kill-switch alami.

---

## 2. Fakta TERVERIFIKASI (dengan sumber — aturan #1)

### 2.1 API dari source asli (dua item yang dulu "belum diverifikasi")

- **pyflakes** — `pyflakes.api.check(codeString, filename, reporter)`:
  parse AST → `checker.Checker(tree)` → panggil `reporter.flake(msg)` per
  warning (urut lineno), `reporter.syntaxError(...)` untuk syntax error.
  Integrasi benar = subclass Reporter pengumpul pesan. VERIFIED dari source:
  https://raw.githubusercontent.com/PyCQA/pyflakes/main/pyflakes/api.py
  Implementasi `pyflakes_layer.py` PR #31 **cocok** dengan kontrak ini
  (ListReporter `syntaxError/flake/unexpectedError` — dibaca langsung di head
  `8775958`).
- **parso 0.8.7** — `parso.parse(code, version=)`, `parso.load_grammar()` →
  `grammar.parse()` + `grammar.iter_errors(module)` → daftar error dengan
  `.message`; node punya `get_code()`, `end_pos`. VERIFIED dari source:
  https://raw.githubusercontent.com/davidhalter/parso/master/parso/__init__.py

### 2.2 Ketersediaan & sumber paket (ARMv7)

- Index Chaquopy = **hanya repo paket native**; pure-Python didukung **dari
  PyPI langsung**: "As well as the packages listed here, Chaquopy also
  supports most pure-Python packages on PyPI" —
  https://chaquo.com/pypi-13.1/ . Konsekuensi: `chaquo.com/pypi-13.1/jedi/`
  404 itu **normal & tidak masalah** (pelajaran pyyaml terbalik arah: kali ini
  justru KETIDAKADAAN di index yang tidak berarti apa-apa).
- Keempat paket pure-Python → wheel universal, **tanpa risiko kelas SIGSEGV
  manylinux-vs-bionic** (SKILL 3). Realnya 4 instalasi, bukan 5: jedi
  membawa parso sebagai dependensi; mccabe & pyflakes & rope mandiri.
- **rope 1.14.0** — "Rope only depends on Python itself". Sumber:
  https://pypi.org/project/rope/ . ⚠️ **Bat as jujur baru:** "Most Python
  syntax up to Python 3.10 is supported" — syntax khusus 3.11 (mis.
  `except*`) berpotensi tidak dipahami rope; wajib ditulis di batas fitur.

### 2.3 Lisensi — KOREKSI terhadap dokumen repo

- **rope = LGPL v3+, BUKAN GPLv3** seperti tertulis di
  `REVIEW_PR30_31_32_2026_08_24.md` §3 poin 5. Sumber resmi:
  https://pypi.org/project/rope/#license . Dampak: tetap kompatibel dengan
  distribusi GPLv3 ZCODE (LGPLv3 ↔ GPLv3 kompatibel; instalasi via pip saat
  runtime = mere aggregation), TETAPI review doc wajib dikoreksi dan, bila
  kelak dibundel ke APK, `LICENSES/` + NOTICE perlu entri LGPLv3 (bukan
  sekadar "GPLv3 kompatibel"). jedi/parso/pyflakes/mccabe = MIT (sesuai
  review; konvensi `LICENSES/` tetap berlaku saat bundling).
- **Koreksi ini contoh live SKILL 5**: klaim lama di dokumen sendiri boleh
  salah — temukan, katakan, perbaiki.

### 2.4 Sisi editor (CodeMirror 6) — lebih siap dari dugaan

- Bridge `setDiagnostics(json)` + `lintGutter` (`@codemirror/lint`) **sudah
  ada di bundle** ZCODE sekarang (`editor-src/src/editor.js:48,689`; komentar
  eksplisit "bridge setDiagnostics(json), BUKAN linter JS (satu sumber
  kebenaran)"). Artinya: jalur UI lint tinggal diisi dari sumber Python —
  tidak perlu kerja baru di sisi JS selain mungkin penyesuaian kecil.
- Pola push-imperatif `setDiagnostics` cocok untuk arsitektur
  Python→Kotlin→JS ZCODE (diagnostik dihitung di luar editor, didorong masuk);
  rujukan API: https://codemirror.net/docs/ref/#lint dan pola konversi
  severity (`1=error,2=warning,else info`).

### 2.5 Performa jedi (pertanyaan paling menentukan)

- Author jedi-vim: completion library kompleks (numpy) lambat **hanya di
  percobaan pertama**, setelah itu di-cache dan cepat; editor yang lambat
  biasanya karena memanggil jedi **sinkron** di thread UI — solusinya async.
  Sumber: https://github.com/davidhalter/jedi-vim (FAQ "The completion is
  too slow!").
- Riwayat: keluhan IPython (2019) & bug progresif (2016, fixed sejak jedi
  0.10). Sumber:
  https://www.reddit.com/r/IPython/comments/dn9x27/ ,
  https://stackoverflow.com/questions/41149987/ .
- **Lead harta karun (bukan keputusan):** author jedi merilis penerus
  **Zuban** — LSP Python kompatibel mypy, ditulis Rust, diklaim 10–100×
  lebih cepat (https://github.com/davidhalter/jedi-vim, lihat juga
  http://github.com/zubanls/zuban). Untuk ZCODE saat ini **tidak relevan**
  (binary Rust + client LSP = arsitektur besar, berisiko ABI ARMv7) — dicatat
  sebagai kandidat riset jangka panjang saja, bukan jalur v1.0.23.
- **Yang BELUM ada angkanya: jedi di ARMv7device**. Semua bukti di atas dari
  desktop. Tetap wajib: pengukuran device (`SPKE_LINT_MS`-style breadcrumb,
  sesuai rencana pengukuran di `RENCANA_KERJA_POST_V1021` §6).

### 2.6 Kondisi kandidat PR #31 (dibaca ulang di head `8775958`)

- Arsitektur: lazy-load per layer, exception hierarchy, fallback, output JSON
  — sehat dan aditif (+1125/−0).
- `pyflakes_layer.py` cocok dengan API asli (§2.1).
- **Bug McCabe nyata** (review §3.1): `ASTVisitor.preorder()` dipanggil tanpa
  visitor → complexity selalu 0 saat mccabe terpasang. Fix kecil, tapi wajib
  + test DENGAN deps asli.
- **rope_layer bukan rope** (review §7.1 + konfirmasi baca langsung):
  `rename_symbol` memanggil `_safe_replace_symbol` (regex `\b<old>\b` ke
  seluruh source) **juga di jalur sukses** — mengganti nama di dalam
  string/komentar, melanggar kontrak rename fail-closed v1.0.21. Harus
  ditulis ulang memakai `rope.refactor.rename.Rename(...).get_changes(...)`
  dengan kegagalan = TIDAK ADA perubahan.
- Test file-nya tidak dijalankan CI (review §1.1). Sejak SKILL 28 ada guard
  `TestCITestListCompleteness` — kelas bug ini sudah punya guard sistemik;
  tetap wajib mendaftarkan file test baru di `tools/check.sh`.
- Deps 5 paket **belum ada di katalog** `packages.json` → strategi
  INSTALL MODULES butuh entri katalog/pack baru.

---

## 3. Assessment: bersahabat dengan ZCODE?

Uji terhadap 4 komitmen PRD §1 + realitas arsitektur:

| Komitmen PRD | Vonis | Bukti |
|---|---|---|
| 100% gratis | ✅ | MIT×4 + rope LGPLv3+ — tanpa paywall, tanpa fitur premium |
| Offline-first | ✅ | Semua analisis lokal; jaringan hanya dipakai SEKALI untuk memasang deps via INSTALL MODULES |
| ARMv7 kelas satu | ✅ dengan catatan | Pure-Python → tanpa wheel native → tanpa kelas bug SIGSEGV bionic. CATATAN: performa jedi di ARMv7 **belum terukur** (§2.5) |
| Keterbatasan bukan jalan buntu | ✅ | Fallback diam-diam per layer; status harus dilabel EXPERIMENTAL sampai angka device ada |

Realitas arsitektur:
- **Aditif & reversible**: tidak menyentuh Chaquopy runtime, resolver,
  transaction core, terminal. Deps dihapus → semua fitur fallback → perilaku
  = hari ini. Ini pola "senjata" paling aman yang pernah dipakai repo.
- **APK tidak tumbuh** pada fase spike (deps via INSTALL MODULES, keputusan
  strategi bundling ditunda sampai ada angka device; estimasi kasar total
  ~2–4 MB terpasang — **belum diverifikasi, wajib diukur**).
- **Jalur UI sebagian sudah ada**: `setDiagnostics` (lint), `gotoLine`
  (goto-def), pipeline problems Kotlin, palette, PluginRunner dual-backend
  dengan timeout (pola untuk semua PyCall engine).
- **Yang belum ada sama sekali**: wiring Kotlin ↔ engine (PR #31 murni
  backend library), entri katalog deps, UI untuk pack "editor intelligence",
  pengukuran device.

**Vonis jujur: BERSAHABAT — dengan tiga syarat:**
1. autocomplete jedi HARUS async + debounce + budget ukuran file, dan tetap
   berdampingan dengan kasta 1+2 (instan) — jedi menambah saat siap, bukan
   menggantikan;
2. rope rename masuk hanya jika ditulis ulang fail-closed (atau ditunda);
3. tidak ada klaim "cerdas" di UI sebelum angka device ada — label
   EXPERIMENTAL + breadcrumb pengukuran.

---

## 4. Risiko & mitigasi

| Risiko | Level | Mitigasi |
|---|---|---|
| jedi lambat di ARMv7 (cold start, file besar) | 🟡 tinggi-dampak, belum terukur | Gate ukur device (breadcrumb ms/MB); async+debounce; budget ukuran file; fallback kasta 1+2 tetep instan; keep/tune/kill berdasar angka |
| rope: regex-fallback tak aman + syntax ≤3.10 | 🟡 | Rewrite fail-closed `get_changes()`; tolak file yang tidak bisa dirope-kan tanpa perubahan; tulis batas 3.10/3.11 di UI |
| Deps belum di katalog; UX pemasangan membingungkan | 🟡 | Pack "Editor Intelligence" di INSTALL MODULES (satu aksi, semua deps); status jelas saat belum terpasang |
| False-green CI (test tak dijalankan) | 🟢 | Guard `TestCITestListCompleteness` sudah ada (SKILL 28); daftarkan test baru di check.sh |
| Lisensi rope salah tercatat (GPLv3 → LGPLv3+) | 🟢 | Koreksi review doc; entri `LICENSES/` + NOTICE saat bundling |
| Scope v1.0.23 membengkak → rilis tertunda → UAT updater ikut tertunda | 🟡 | Lihat opsi §5; rekomendasi lint-first |

---

## 5. Opsi scope v1.0.23 (keputusan user)

| Opsi | Isi | Ukuran | Konsekuensi |
|---|---|---|---|
| **A — lint-first (REKOMENDASI)** | pyflakes lint (wiring ke `setDiagnostics` yang sudah ada) + mccabe (fix 1 baris, info kompleksitas) + pack deps di INSTALL MODULES + **jedi autocomplete di belakang gerbang ukur** (aktif hanya jika angka device lolos) + rope rename **ditunda ke v1.0.24** kecuali rewrite-nya selesai mulus | Sedang | Nilai paling terlihat (Problems!) paling cepat; risiko perf dikurung; v1.0.23 tetap rilis dalam ukuran manusiawi → updater segera ter-UAT |
| B — mini v1.0.23 dulu | Hanya housekeeping Fase 0 v1.0.22 + rilis kecil (mis. Tier B tools 1–2) | Kecil | Updater ter-UAT paling cepat; spike penuh jadi v1.0.24 dengan risiko menumpuk dua rilis besar |
| C — full 5 layer sekaligus | Semua kemampuan §1 | Besar | Paling berisiko: perf belum terukur, rope rewrite, scope melar — pola PR #30/31/32 |

Rekomendasi agent: **A**, dengan lint sebagai kemenangan yang terlihat dan
jedi sebagai fitur ber-gerbang-ukur (bukan janji). Confidence estimasi:
lint-path sampai CI hijau ~85%; jedi autocomplete lolos gerbang ukur device
~60% (belum ada angka ARMv7 — ini dugaan, bukan bukti).

---

## 6. Pertanyaan terbuka untuk user

1. Scope: A / B / C atau kombinasi custom?
2. UX pemasangan deps: pack "Editor Intelligence" di INSTALL MODULES —
   cukup, atau mau otomatis menawarkan pasang saat fitur pertama dipakai?
3. rope rename: ditunda (rekomendasi) atau dikerjakan sekarang?
4. Prioritas label fitur di UI: "Problems" masuk drawer/palette/tempat lain?

## 7. Batas riset ini (jujur)

- Belum ada satu pun angka performa ARMv7 (belum dijalankan di bionic311 /
  emulator / device). Fase RFC wajib menjalankan harness di bionic311 untuk
  smoke + angka awal sebelum APK.
- Ukuran total deps = estimasi, belum diukur.
- Pydroid closed-source — tidak ada pembanding implementasi internal
  (kebijakan lama `RISET_VSCODE_ACODE_PYDROID_2026_08_19.md` tetap berlaku).
- jedi 0.20.0 = versi terkini saat riset; pin versi final ditentukan di RFC
  (resolver ZCODE menangani `requires_python` — Bug A sudah lama mati).
- Riset ini tidak mengubah kode, PR, workflow, atau rilis apa pun.

---

## 8. Addendum (2026-08-25, sesi tanya-jawab "bersahabat dengan HP ampas?")

### 8.1 ruff DICEK LANGSUNG — vonis: HARAM untuk ZCODE ARMv7

Pengecekan live `https://pypi.org/pypi/ruff/json` (ruff 0.16.4, 2026-08-25):
wheel **armv7l 32-bit ada** (`manylinux_2_17_armv7l.manylinux2014_armv7l`,
`musllinux_1_2_armv7l`) — tetapi **tidak ada wheel bertag android sama
sekali**, dan manylinux/musllinux = glibc/musl ≠ bionic ⇒ persis kelas
`manylinux_armv7l` yang dimakamkan SKILL 3 (bug F): **SIGSEGV di Android,
tidak tertangkap try/except**. Membangun ruff sendiri via NDK = toolchain
baru + beban supply-chain (pola risiko E-03) — bukan jalur ZCODE sekarang.
Koreksi jujur terhadap dugaan awal agent: sempat diduga ruff punya wheel
`aarch64-linux-android` tapi tidak armv7 — **salah dua-duanya**: tidak ada
wheel android apa pun di rilis tersebut. Data > dugaan.

### 8.2 Bukti performa jedi (klasik, dari issue tracker asli)

- "up to 5 seconds for completions" dengan library besar (cv2/PIL) —
  https://github.com/davidhalter/jedi/issues/1195 (2018; motivasi typeshed
  integration di jedi modern).
- Memory growth 0–2 MB per completion pada sesi panjang dengan numpy/pandas —
  https://github.com/davidhalter/jedi/issues/335 (2013; versi lama, tetapi
  pola "analisis library besar = mahal" tetap).
- Konfirmasi author: completion numpy lambat hanya pertama, lalu cache —
  https://github.com/davidhalter/jedi-vim (FAQ).
- Pembanding kelas: riset akademik 2024 mengukur RAM total IDE Python
  Android — Pydroid ~566 MB, QPython ~674 MB, Termux ~808 MB (device 5.82 GB)
  — https://www.researchgate.net/publication/379936948 . Arah ZCODE beda:
  kecerdasan bertahap tanpa jadi babon memori.

### 8.3 Vonis per komponen vs "HP ampas"

| Komponen | Berat di device | Vonis |
|---|---|---|
| pyflakes (~215 KB terpasang) | sangat ringan: 1× parse AST via parser **C bawaan CPython**, tanpa analisis lintas file | **MASUK** (inti v1.0.23) |
| mccabe (~15 KB) | sangat ringan: 1× walk AST | **MASUK** (syarat: fix 1 baris + test deps asli) |
| parso (~100 KB) | ringan; error-recovery multi-error | ikut jedi (pyflakes sudah menangkap syntax error via `ast`) |
| jedi (~1,5 MB + inference) | berat-relatif; bukti §8.2 | **GERBANG UKUR**: opt-in, async, debounce, budget ukuran file; keep/tune/kill berdasarkan angka device |
| rope (~1,2 MB) | sedang-berat; parser Python-in-Python; syntax ≤3.10 | **TUNDA** (v1.0.24+) |

### 8.4 Jawaban "ada yang lebih ringan tapi sepintar & selengkap mereka?"

**Tidak ada satu paket yang menang tiga-tiga (ringan + pintar + lengkap) di
dunia yang bisa jalan di ARMv7 Android bionic.** Peta jujurnya:

| Kandidat | Ringan? | Pintar? | Lengkap? | Tersedia untuk ZCODE ARMv7? |
|---|---|---|---|---|
| ruff | ✅ (Rust) | ✅ | ✅ (900+ rules) | ❌ wheel hanya glibc/musl → SIGSEGV (§8.1) |
| Zuban / pyright / pylsp (LSP) | ❌ | ✅ | ✅ | ❌ arsitektur runtime kedua (subprocess/binary) — lead jangka panjang |
| Lezer scope-aware (kasta 2.5) | ✅✅ (tree sudah dibawa untuk highlighting) | ⚠️ scope-aware saja | ❌ | ✅ nol dependensi baru (verifikasi harness di RFC) |
| **pyflakes (+mccabe)** | ✅ | ✅ (semantik nyata) | ⚠️ (lint saja) | ✅ **paling pintar yang tetap ringan di pure-Python** |
| jedi | ⚠️ | ✅✅ | ✅ (complete+goto) | ⚠️ mungkin — lewat gerbang ukur |

### 8.5 Jawaban "Acode & VS Code pakai apa?" (riset 2026-08-25, sesi v1.0.23 focus)

**VS Code (Python):**
- Engine default = **Pylance** — closed-source, lisensi hanya untuk produk
  Microsoft resmi ("can only be used on official Microsoft builds of Visual
  Studio Code and GitHub Codespaces" —
  https://github.com/microsoft/pylance-release/blob/main/FAQ.md ;
  fork seperti VSCodium/Cursor jatuh ke pyright/basedpyright —
  https://pydevtools.com/handbook/reference/pyright/).
  Pylance = lapisan pintar (autocomplete, auto-import, rename) di atas
  **Pyright** (MIT, open source) — tapi Pyright ditulis TypeScript dan
  butuh **runtime Node** (paket PyPI bahkan mengunduh Node sendiri via
  nodeenv bila tidak ada — pydevtools handbook, id.).
- **Jedi pernah jadi engine resmi VS Code** sebelum Pylance ( dukungan
  Python 2.7 berbasis Jedi dihapus 2021 —
  https://www.theregister.com/2021/09/07/python_extension_vs_code_september/ );
  `jedi-language-server` (LSP wrapper atas jedi) masih hidup sebagai
  alternatif open source — bukti jedi kelas IDE, bukan mainan.
- Error checker: diagnostik Pylance/Pyright (+ mode basic/strict) + ekstensi
  opsional Flake8/Pylint/Ruff.
- Pola yang bisa diadopsi (bukan engine-nya): language service terpisah dari
  UI + **fallback bertingkat** — VS Code sendiri jatuh ke word-based
  suggestions saat server belum siap.

**Acode:**
- Basis editor = CodeMirror 6 (sama dengan ZCODE).
- Fitur pintar (autocomplete/import-completion JS/TS/**Python**, rename,
  diagnostics, goto) = via **LSP client plugin** `acode.language.client`
  yang menjalankan server `acode-lsp` di-install lewat npm
  (https://acode.app/plugin/acode.language.client) ⇒ arsitektur butuh
  runtime server/Node di device — terlalu berat untuk jalur ZCODE ARMv7.
- Baseline non-LSP-nya = paket bahasa CM6.

**TEMUAN KUNCI ZCODE (verified dari source bundle sendiri):**
`@codemirror/lang-python` **6.1.0 (2022-11-18)** menambahkan
`globalCompletion` (globals + keyword) dan `localCompletionSource`
(nama yang didefinisikan lokal — **scope-aware**) dan keduanya
"**Included in the support extensions returned from `python()`**"
(https://github.com/codemirror/lang-python — CHANGELOG.md).
Bundle ZCODE memakai 6.2.1 ⇒ **keduanya sudah dikirim di dalam APK**.
TETAPI `editor-src/src/editor.js:364` memakai
`autocompletion({ override: [zcodeCompletions] })` — opsi `override`
**mengganti seluruh sumber completion bahasa**, sehingga dua sumber
scope-aware bawaan itu **ter-bisukan** sejak migrasi CM6.
Fix = daftarkan `zcodeCompletions` berdampingan dengan
`localCompletionSource`/`globalCompletion` (mis. array `override` tiga
sumber, atau pindah ke language-data source) + rebuild bundle + verifikasi
harness browser. Biaya: kecil, nol dependensi baru, jalan instan di sisi JS
(tanpa round-trip Python). Status: **DITEMUKAN, belum diimplementasikan** —
verifikasi runtime via browser harness wajib sebelum klaim.

**Kesimpulan adopsi:** engine Pylance/Acode-LSP tidak dapat diadopsi
(lisensi proprietary + runtime Node); sebaliknya ZCODE justru punya
keunggulan yang tidak dimiliki VS Code: runtime Python hidup **in-process**
— tidak butuh LSP/JSON-RPC/Node, cukup panggilan fungsi langsung ke
pyflakes/jedi. Arsitektur ZCODE untuk masalah yang sama justru LEBIH
RINGAN daripada VS Code.

**Strategi akhir v1.0.23 (gabungan seluruh riset) = bertingkat, bukan satu
senjata:**

```text
Tier 0   kasta 1+2 (sudah ada) + Checker.kt ringan
Tier 1   pyflakes + mccabe → vm.problems → VPP/gutter        [Python, debounced]
Tier 2.5 un-mute localCompletionSource + globalCompletion     [JS, instan, nol dep]
Tier 3   jedi complete/goto di belakang gerbang ukur device   [async, opt-in]
```

HP paling ampas dijamin dapat Tier 0–2.5; jedi tidak pernah dapat
menyandera editor; keep/tune/kill ditentukan angka device, bukan selera.

### 8.6 Pembanding: editor GitHub (riset 2026-08-25)

GitHub punya DUA editor, dua kasta berbeda:
1. **Pencil editor github.com** — editor sederhana untuk edit kecil → commit;
   syntax highlight saja, tanpa IntelliSense.
2. **github.dev** (tekan `.` di repo) — VS Code for the Web, jalan penuh di
   browser sandbox (https://docs.github.com/en/codespaces/the-githubdev-web-based-editor):
   - core editor = **Monaco** (jantung editor VS Code);
   - Python intelligence = **Pylance versi web** — closed-source juga, fitur
     **dipangkas** vs desktop: "Completions for built-ins, Pylance's bundled
     stubs, locally defined symbols in the current file, and symbols in open
     files; syntax errors; navigation; outline; signature help" — dan TIDAK
     bisa menjalankan kode (butuh ekstensi Pyodide/Pyolite)
     (https://visualstudiomagazine.com/articles/2021/09/08/vscode-python-sep21.aspx,
     https://github.com/joyceerhl/vscode-pyolite).

Pelajaran untuk ZCODE:
- **Microsoft pun, di environment terbatas, mengirim versi PANGKAS**:
  builtins + stubs + **simbol lokal** + simbol file terbuka — hampir persis
  Tier 2.5 ZCODE (localCompletionSource + globalCompletion) sebelum jedi.
  Validasi pola bertingkat dari pemain terbesar.
- Ekstensi Python VS Code **menghapus dukungan rope & ctags** sejak
  2021.10 (sumber id.) — preseden industri yang menguatkan keputusan
  menunda rope di v1.0.23.
- Jalur "Python di sandbox browser" mereka = Pyodide (CPython→WASM) —
  jalan yang sudah dimakamkan SKILL 9 untuk ZCODE, dan tidak dibutuhkan
  karena ZCODE memilik CPython asli in-process.

### 8.7 Menambal sisa gap keterbatasan device — naik atau turun nilai? (2026-08-25)

Verifikasi baru:
- **ty 0.0.74** (type checker Rust Astral): wheel armv7l hanya
  manylinux/musllinux, **tidak ada wheel android** (live check
  `pypi.org/pypi/ty/json`, metode sama seperti ruff §8.1) — vonis identik
  dengan ruff: mustahil in-app di bionic ARMv7.
- **Traceback tap → jump sudah SHIPPED**: `WorkbenchScreen.kt:243`
  ("A3 v1.0.19") + `:798-803` (`onGotoEditorLine(file,line)` →
  `requestGotoLine`), lengkap dengan toggle "Traceback jump". Artinya
  error tipe runtime SUDAH ditangani dinamis: TypeError di terminal →
  tap → lompat ke baris eksak editor. Biaya nol (sudah dibayar sejak
  v1.0.19).
- **`jedi.Interpreter` TERVERIFIKASI via Context7** (tanpa auth, sesuai
  pola sesi sebelumnya; sumber snippet:
  https://context7.com/davidhalter/jedi/llms.txt ):
  `jedi.Interpreter(code, [namespace_dict])` — completion atas objek
  Python HIDUP (pola IPython/ptpython). ZCODE punya ini gratis karena
  Chaquopy in-process: namespace sesi Run terakhir bisa dipakai sebagai
  sumber completion REPL. Catatan jujur: jedi issue #919 = Interpreter
  bisa mengevaluasi `__repr__` objek besar (lambat) ⇒ hanya konteks
  REPL/terminal + budget timeout; status KANDIDAT (perlu cek retensi
  namespace engine + gerbang ukur).

Peta tambalan & dampak nilai:

| Gap | Tambalan | Nilai | Status |
|---|---|---|---|
| Type error (`len(5)`) — statis | mypy/pyright/ty in-app | ⬇️ TURUN (mustahil/berat; APK bengkak; lag) | DITOLAK bukti (ty §ini, pyright Node, mypy ekstrem lambat di ARMv7) |
| Type error — dinamis | **traceback tap→jump** (runtime = type checker paling akurat utk kode tanpa anotasi; 0 false positive) | ⬆️ NAIK, biaya nol | **SUDAH ADA** (v1.0.19 A3) |
| Autocomplete runtime/atribut dinamis | `jedi.Interpreter` namespace hidup post-Run | ⬆️ NAIK besar (REPL) | KANDIDAT v1.0.24/pasca-ukur |
| Import typo lintas file | import-resolution check di atas jedi (warning) | ⬆️ kalo akurat / ⬇️ jika false positive | kandidat pasca-ukur |
| Gaya kode | pycodestyle (ada, ringan) | ⬇️ TURUN (noise utk user pemula HP) | SKIP |
| Artileri penuh (ruff 900 aturan, ty) | musllinux_armv7l JALAN dalam Alpine/PRoot | ⬆️ NAIK utk power user opt-in | parkir di terminal v1.0.24 |

Prinsip: **menambal dengan paksaan menurunkan nilai; menambal dengan
penempatan (waktu Run / tier Alpine) menaikkan.** Gap karena keterbatasan
device paling murah ditutup oleh (1) hal yang sudah ada di waktu berbeda
(traceback), (2) tier opsional yang beratnya dibayar hanya yang memakai
(Alpine).
