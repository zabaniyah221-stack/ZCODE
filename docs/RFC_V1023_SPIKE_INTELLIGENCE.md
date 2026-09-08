# RFC v1.0.23 — SPIKE INTELLIGENCE (EDITOR YANG PAHAM PYTHON)

**Status: DESIGNED — menunggu approval user sebelum implementasi (bagian dari
RENCANA_KERJA_V1023_2026_09_08.md Fase 1).**
Basis riset: `RISET_SPIKE_INTELLIGENCE_V1023_2026_08_25.md` (§1–8, semua klaim
ber-URL). Basis kode: `main` @ `26bc8a0` + PR #31 head `8775958` (open, akan
jadi basis port lalu superseded).

---

## 1. Masalah & bukti

Editor ZCODE hari ini hanya memeriksa POLA (kurung/string/indent — `Checker.kt`)
dan autocomplete-nya cuma kata-dalam-dokumen + keyword statis (kasta 1+2).
Kesalahan paling umum Python — `undefined name`, unused import, nama tertimpa —
tidak terlihat sampai Run. Riset pembanding (§8.5–8.6): VS Code = Pylance
(proprietary, mustahil diadopsi), Acode = LSP+npm (berat), github.dev pun
mengirim versi PANGKAS (builtins + stubs + simbol lokal). Riset keluhan (§4
RISET_KELUHAN): tidak ada IDE Python mobile yang punya lint semantik pre-run.
ZCODE punya modal unik: CPython 3.11 hidup in-process — tidak butuh LSP/Node.

## 2. Goals & non-goals

**Goals:**
1. (G1) Lint semantik pre-run (pyflakes) menyatu ke pipeline problems yang
   SUDAH HIDUP: `vm.problems` → VPP + lint gutter + `setDiagnostics`
   (`EditorScreen.kt:100-125`, debounce Checker 800 ms — konsumen TIDAK
   disentuh).
2. (G2) Autocomplete scope-aware instan: un-mute `localCompletionSource` +
   `globalCompletion` yang sudah ter-bundel (`@codemirror/lang-python` 6.2.1;
   saat ini dibisukan `override` — `editor-src/src/editor.js:364`).
3. (G3) Autocomplete kasta 3 (jedi: stdlib, inferensi tipe, lintas file,
   goto-def) — **di belakang gerbang ukur device**, opt-in, async.
4. (G4) Info kompleksitas (mccabe) sebagai aksi palette opsional.
5. (G5) Deps via pack "Editor Intelligence" di INSTALL MODULES (APK tak
   berubah; uninstall pack = kill-switch alami).

**Non-goals (parkir, alasan di riset §8):** type checker in-app
(mypy/pyright/ty — tanpa wheel android / butuh Node); style checker (noise);
rope rename (rewrite fail-closed belum dibuat, rope ≤3.10, VS Code pun
menghapusnya 2021); LSP arsitektur apa pun; autocomplete lintas workspace
penuh; janji performa sebelum angka device.

## 3. Arsitektur bertingkat

```text
Tier 0    Checker.kt + kasta 1+2              — ada sekarang, 0 biaya
Tier 1    pyflakes → vm.problems (+mccabe)     — commit #4 (Python, async)
Tier 2.5  un-mute CM6 completion sources        — commit #3 (JS, instan, 0 dep)
Tier 3    jedi complete/goto                    — commit #4 (async, opt-in,
          GERBANG UKUR: keep/tune/kill by angka device, bukan selera)
```

HP paling ampas dijamin dapat Tier 0–2.5 tanpa pack terpasang. Tiap tier
gagal diam-diam ke tier di bawahnya (pola PR #31 yang dipertahankan).

## 4. Kontrak data

### 4.1 problems (memperluas `core/editor/Problem` yang sudah hidup)

`Problem` bertambah field opsional `source: String = "checker"` (pola
VPP_DESIGN §3; nilai: `"checker" | "pyflakes"`). VPP/gutter tetap membaca
`List<Problem>` — tidak ada perubahan konsumen. Jalur pyflakes (Python):

```json
{"ok": true, "problems": [
  {"line": 3, "column": 10, "severity": "warning",
   "message": "unused import 'os'", "source": "pyflakes"}]}
```

Kegagalan engine/timeout → `{"ok": false, "error": "..."}` → **daftar
problems pyflakes dikosongkan, Checker tetap jalan** (fail-open ke Tier 0,
bukan error UI). Aturan merge: Checker selalu dievaluasi; pyflakes menambah
(non-mengganti); dedupe sederhana (line+pesan) tidak dilakukan — sumber
berbeda boleh menampilkan hal yang sama (jujur, murah).

### 4.2 completions (jedi, kasta 3)

```json
{"ok": true, "completions": [
  {"name": "listdir", "complete": "dir", "type": "function",
   "detail": "os.listdir(path=None)"}]}
```

Operasi async dengan `operationId`; hasil yang datang setelah operasi
diganti **dibuang** (pembelajaran stale-callback SKILL 18/12.3). Budget:
file <256 KB, timeout 3000 ms, maks 20 kandidat.

## 5. Strategi dependensi — pack "Editor Intelligence"

- Entri katalog `packages.json` (3 baru, skema eksisting): `jedi`, `pyflakes`,
  `mccabe` — `type: "pure"`, `status: "EXPERIMENTAL"`, `python: ["3.11"]`,
  `abis: []`, `testedVersion: null` (belum ada device UAT — naik status hanya
  setelah evidence), kategori mengikuti taksonomi katalog. `parso` hadir
  otomatis sebagai dependensi jedi (resolver mengurus; 4 instalasi nyata).
- Pin versi ditetapkan saat implementasi dari indeks PyPI saat itu, dicatat
  di katalog + release notes (kebijakan pin rumah; anti-drift F-09).
- UI pack: entri Library "Editor Intelligence" menjelaskan 3 paket + alasan
  (prosa jujur, pola Library). TANPA auto-install senyap: fitur yang butuh
  pack menampilkan status "butuh Editor Intelligence pack" + tombol ke
  INSTALL MODULES (persetujuan eksplisit, pola sample-dependency gate).
- CI: job `check` menambah `pip install` 4 deps (pure wheel, cepat) supaya
  path DEPS ASLI ikut diuji CI (pelajaran bug McCabe PR #31: hijau fallback
  bukan bukti path asli).

## 6. Pengukuran & gerbang (angka menentukan, bukan selera)

**Lapisan 1 — bionic311 (SEBELUM APK):** harness menjalankan pyflakes &
jedi pada fixture (file 100/500/2000 baris; `os.` + kode ber-stdlib):
waktu per panggilan pertama & ter-cache, RSS delta. Angka masuk dokumen
hasil (format pola SKILL 16). Merah di sini = desain ditinjau, bukan
dipaksakan ke device.

**Lapisan 2 — device (UAT v1.0.23):** breadcrumb `SPKE_LINT_MS`,
`SPKE_AC_FIRST_MS`, `SPKE_AC_CACHED_MS`, `SPKE_MEM_KB` (pola pengukuran
RENCANA_KERJA_POST_V1021 §6). Gerbang (nilai awal, boleh direvisi dengan
bukti):

```text
pyflakes 500-baris        : ≤ 1500 ms   — di atasnya → tune (debounce/budget)
jedi complete pertama     : ≤ 2500 ms   — di atasnya → opt-in default OFF
jedi complete ter-cache   : ≤ 900 ms    — di atasnya → kill kasta 3
RSS delta engine aktif    : ≤ 120 MB    — di atasnya → kill kasta 3
```

Kill = fitur disembunyikan/dimatikan (bukan dihapus) + catat jujur; Tier 1
dan 2.5 tidak ikut mati.

## 7. Wiring (semua nyangkut ke titik yang sudah ada)

1. **Lint (G1)**: `LintScheduler` (baru, `core/editor/`): debounce idle
   ≥500 ms + skip saat Run aktif (workflow gate Run: `SCRIPT_BEGIN`→
   `SCRIPT_END`) + file <256 KB → PyCall pyflakes → merge ke producer
   `vm.problems` (tempat Checker dipanggil hari ini). Checker tetap sinkron
   & instan; pyflakes async di belakangnya. Breadcrumb `SPKE_LINT_MS` per
   eksekusi.
2. **Un-mute (G2)**: `editor-src/src/editor.js` — `override:
   [zcodeCompletions, localCompletionSource, globalCompletion]` (import dari
   `@codemirror/lang-python`); urutan & interaksi diverifikasi di browser
   harness byte-exact (SKILL 24) SEBELUM rebuild dipercaya; bundle rebuild
   dengan pin versi + SHA tercatat + guard marker bundle diperbarui.
3. **jedi (G3)**: request async via PyCall (operationId + timeout + buang
   stale); setelan "Deep completions (jedi)" default OFF sampai gerbang
   hijau; goto-def via palette (pola `gotoLine`).
4. **mccabe (G4)**: aksi palette "Complexity report" — dialog hasil per
   fungsi (complexity + baris), tombol goto; tanpa memblokir apa pun.
5. **Pendaftaran backend**: `zcode_plugins.py` menerima `run_spike_
   intelligence` (port PR #31 — validasi input + exception hierarchy +
   lazy-load per layer dipertahankan).

## 8. Port PR #31 — daftar perubahan wajib (syarat REVIEW §3)

| Item | Aksi |
|---|---|
| `editor/{__init__,intelligence_engine,jedi_layer,parso_layer,pyflakes_layer,cabe_layer}.py` | PORT dari head `8775958` dengan perbaikan di bawah |
| `rope_layer.py` | **TIDAK di-port** (ditunda; kontrak fail-closed belum dibuat) |
| Bug McCabe `ASTVisitor.preorder()` | FIX: `preorder(tree, mccabe.ASTVisitor())` — verifikasi API dari source mccabe saat implementasi (pelajaran: API dibaca, bukan ditebak) |
| `test_engine_spike_intelligence.py` | Port + DIDAFTARKAN di `tools/check.sh` (baris pytest) + deps asli di venv check & CI |
| Registrasi `run_spike_intelligence` di `zcode_plugins.py` | Port (murni aditif) |
| `docs/ENGINE_SPIKE_INTELLIGENCE.md` (PR #31) | Tidak di-port; diganti RFC ini sebagai sumber kebenaran |
| Klaim status | Semua label mengikuti ladder §AGENTS (IMPLEMENTED/LOCALLY/CI/DEVICE) — tanpa "100% pass" tanpa bukti |

Setelah PR v1.0.23 dibuka: PR #31 di-close sebagai **superseded**
(topologi satu-kandidat, SKILL 28).

## 9. Acceptance criteria (dapat diamati)

1. File dengan `print(nma)` + `import os` (tak terpakai): tanpa pack →
   perilaku hari ini; dengan pack → VPP menampilkan 2 masalah pyflakes +
   masalah Checker tetap ada; tap → goto baris.
2. Autocomplete `os.` + nama lokal muncul INSTAN tanpa pack (Tier 2.5).
3. Setelan "Deep completions" OFF default; ON + pack terpasang → completion
   stdlib bertipe; file 2000 baris tidak membekukan UI (async + stale-drop).
4. Uninstall pack → semua fitur fallback diam; Checker + kasta 1+2 utuh.
5. `SPKE_*` muncul di Diagnostics dan bisa disalin.
6. Suite penuh hijau + mutasi merah untuk tiap guard baru; CI hijau
   (kompilasi = hakim; deps asli terpasang di CI check).

## 10. Failure modes & rollback

| Kegagalan | Perilaku |
|---|---|
| Pack tak terpasang | fitur pyflakes/jedi/mccabe OFF; UI menjelaskan + tautan INSTALL MODULES |
| PyCall error/timeout | problems pyflakes kosong (fail-open), Checker jalan; breadcrumb |
| jedi lambat di device | gerbang §6 → tune (default OFF) atau kill; Tier 1/2.5 tak tersentuh |
| Bundle rebuild drift | pin + SHA + harness byte-exact + guard marker (pola F-09) |
| Regresi fatal | seluruh perubahan aditif + reversible: revert PR = kembali v1.0.22 perilaku |

## 11. Alternatif yang ditolak (ringkas, bukti di riset)

ruff/ty in-app (wheel manylinux/musllinux only → SIGSEGV kelas bug F);
Pylance (lisensi Microsoft-only); pyright/LSP (runtime Node/arsitektur kedua);
mypy (lambat di ARMv7); bundling deps ke APK (bengkak, belum ada angka);
rope (regex-fallback tak aman + ≤3.10 + maintenance dubius).

## 12. Sumber utama

- https://github.com/PyCQA/pyflakes — api.py (check/Reporter, diverifikasi)
- https://github.com/davidhalter/parso — __init__ (iter_errors, diverifikasi)
- https://github.com/codemirror/lang-python — CHANGELOG 6.1.0 (completion sources)
- https://github.com/davidhalter/jedi-vim — FAQ performa (cache pertama)
- https://pypi.org/project/rope/#license — LGPLv3+ (errata review PR)
- Dokumen internal: RISET_SPIKE_INTELLIGENCE (§8.1–8.7), REVIEW_PR30_31_32,
  UAT_UPDATER_CHECKPATH, RENCANA_KERJA_V1023.
