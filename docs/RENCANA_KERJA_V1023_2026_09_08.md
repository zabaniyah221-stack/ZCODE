# RENCANA KERJA v1.0.23 — EDITOR INTELLIGENCE + HOUSEKEEPING (2026-09-08)

**Status: MENUNGGU APPROVAL USER — belum satu baris kode pun dieksekusi.**
Basis: `main` @ `26bc8a0` = tag `v1.0.22` (RELEASED 2026-08-25T08:19:33Z).
Branch kerja (usulan): `arena/v1023-intelligence`.
Pelaksana: agent Arena (sesi 2026-09-08). Reviewer/pemutus: user.
Dokumen pendukung: `RISET_SPIKE_INTELLIGENCE_V1023_2026_08_25.md` (§1–8),
`RISET_KELUHAN_USER_IDE_PYTHON_MOBILE_2026_08_26.md`,
`RISET_DESKTOP_LAPTOP_COMPANION_2026_09_08.md`,
`UAT_UPDATER_CHECKPATH_2026_09_08.md`.

---

## 0. Kenapa rencana ini ada (5 gap nyata)

1. **Rekaman repo tertinggal kenyataan (lagi).** PRD masih "produksi 1.0.21",
   RELEASE_NOTES_V1.0.22 masih "NOT RELEASED", SIGNING belum punya §evidence
   v1.0.22 — padahal rilis nyata (run `32822998488`, published
   2026-08-25T08:19:33Z) dan telemetri device 846 baris membuktikannya.
2. **Updater v1.0.22 baru teruji check-path.** Jalur
   download→verify→install→receipt menunggu rilis berikutnya — v1.0.23
   sekalian menjadi kendaraan UAT updater full-path (25→26).
3. **Bug cache 24 jam ditemukan** (`readCache` membaca field statis yang
   null lintas process; telemetri: 0 hit) + FAIL dobel-log + doc drift
   `_SAME` vs `_OK` (bukti: `UAT_UPDATER_CHECKPATH_2026_09_08.md` §2–3).
4. **Fokus v1.0.23 yang user kunci** — autocomplete + error checker cerdas
   ringan — riset selesai, kandidat kode ada (PR #31 open, head `8775958`),
   tapi syarat review `REVIEW_PR30_31_32` belum dipenuhi.
5. **About & Contribute**: keputusan UX user 2026-09-08 (Model B expandable,
   copy English, chip tagline, teks bisa disalin) belum terimplementasi.

## 1. Target (definisi selesai) & non-goals

- **(T1)** Rekaman v1.0.22 tertutup jujur: 4 docs + guard post-release
  bermutasi-merah + errata lisensi rope.
- **(T2)** `RFC_V1023_SPIKE_INTELLIGENCE.md` disetujui user, lalu
  implementasi: pyflakes+mccabe → VPP/gutter, un-mute completion bawaan
  CM6, jedi di balik gerbang ukur device, deps via INSTALL MODULES.
- **(T3)** Fix updater cache + dedupe log FAIL; About redesign Model B.
- **(T4)** Rilis v1.0.23 (versionCode 26) via alur rumah: PR → merge main →
  dispatch DARI main → draft byte-exact → UAT → publish tanpa rebuild.
- **(T5)** UAT ganda terdokumentasi: (A) fitur baru; (B) updater full-path
  25→26 — dengan `adb logcat -b crash` dari laptop (kemampuan baru).

**Non-goals (parkir, dengan alasan tercatat):** terminal T1/T2 (v1.0.24,
`TARGET_TERMINAL_ZCODE.md`); rope rename (v1.0.24+, rewrite fail-closed
belum dibuat); keyboard superpowers; bridge API Android; debugger;
Alpine/T4; desktop variant & multi-artifact CI
(`RISET_DESKTOP_LAPTOP_COMPANION_2026_09_08.md`); type-checker/style-checker
in-app (ditolak bukti, riset §8.7); LSP arsitektur apa pun.

## 2. Basis fakta (dicek langsung 2026-09-08)

| Fakta | Nilai | Sumber |
|---|---|---|
| Rilis v1.0.22 | tag `v1.0.22`, target `26bc8a0`=main, published 2026-08-25T08:19:33Z, draft=false | GitHub API `releases/latest` |
| Aset | APK 34.759.201 B, digest `sha256:e7384101d31728c99aa0bcc3f90e75ee139fc04d2c29b483c8e049f5a3f9e32b` + `.sha256` + `apksigner.txt` | GitHub API |
| Production run | `32822998488` workflow_dispatch dari main, SUCCESS, 2026-08-25T07:43:39Z, **tepat 1 run** | GitHub API |
| Pola rilis | PR #33 (arena/01a03332) → merge → push main hijau → dispatch dari main | GitHub API runs |
| PR lama | #30 closed, #32 closed, **#31 open** head `8775958` | GitHub API pulls |
| Updater check-path | 23 check (15 auto+8 user), OK/FAIL sesuai kontrak, retry pulih, **nol crash Java sepanjang log** | `ZCODE_DIAGNOSTICS.txt` (user) |
| Bug cache | 0 cache-hit dari 15 auto-check; akar di `UpdateChecker.kt` `readCache()` | telemetri + baca kode |
| Katalog deps | jedi/pyflakes/mccabe/parso **belum ada** di `packages.json` | grep sesi 2026-08-25 |
| Editor | `editor.js:364` `override:[zcodeCompletions]` mematikan `localCompletionSource`+`globalCompletion` (bundled 6.2.1) | baca source + CHANGELOG resmi |
| Lisensi di APK | `assets/licenses/`: GPL-3.0.txt, MIT.txt, NOTICE.txt | ls sesi ini |
| Keputusan UX user | Model B (expandable), English, chip, About nimbrung v1.0.23 | chat 2026-09-08 |

## 3. FASE 0 — tutup rekaman v1.0.22 (docs + guard; TANPA kode produk)

| File | Perubahan |
|---|---|
| `docs/RELEASE_NOTES_V1.0.22.md` | Status → **RELEASED 2026-08-25T08:19:33Z**; blok evidence: run 32822998488, bytes, SHA-256, signer `40139219…8bd2`, run count 1, verdict UAT user + telemetri (check-path DEVICE VERIFIED; full-path NOT YET — menunggu v1.0.23); catatan known-issue cache → fixed in 1.0.23 |
| `docs/SIGNING_ZCODE.md` | § baru "v1.0.22 production evidence" (meniru struktur §8/§9); update-continuity 1.0.21→1.0.22 = DEVICE VERIFIED (user report + telemetri APP_START 08-25 15:04 v1.0.22 sentinel utuh) |
| `docs/PRD_ZCODE.md` | "Versi produksi saat ini: 1.0.22 / versionCode 25 (RELEASED…)" + kandidat berikutnya 1.0.23 |
| `docs/ROADMAP_V1021_V1022_SAFETY_AND_UPDATE.md` | Baris status v1.0.22 → RELEASED (perubahan kecil) |
| `docs/REVIEW_PR30_31_32_2026_08_24.md` | **Errata**: rope = LGPLv3+ (bukan GPLv3), sumber PyPI — koreksi klaim, bukan vonis |
| `docs/RFC_V1022_ONE_TAP_UPDATE.md` | Errata kecil: `UPDATE_CHECK_SAME` → implementasi memakai `UPDATE_CHECK_OK`; catat bug cache |
| `test_zcode_production_release.py` | Guard post-release v1.0.22: konstanta pasangan 1.0.22/25, run ID, SHA-256, signer; asersi kehadiran di docs — pola guard v1.0.20/21; **uji mutasi** (hapus baris bukti → merah) |
| Commit dokumen sesi | 3 riset + UAT updater + mockup About + rencana ini (7 file) |

**Gate F0:** `tools/check.sh` hijau; mutasi merah terbukti; CI hijau;
user review. **Tidak lanjut Fase 1 sebelum hijau.**

## 4. FASE 1 — RFC v1.0.23 + lock scope (prasyarat T2)

1. Tulis `docs/RFC_V1023_SPIKE_INTELLIGENCE.md` dari riset (§8.5 stratifikasi
   final), minimum: kontrak `problems[]` (merge `Checker.kt` + pyflakes via
   field `source`), `completions[]` bertingkat; strategi deps = pack
   **"Editor Intelligence"** di INSTALL MODULES (jedi+pyflakes+mccabe pin
   versi; parso hadir sebagai dependensi jedi); rencana ukur (breadcrumb
   `SPKE_LINT_MS`/`SPKE_AC_MS`/`SPKE_MEM_KB`; harness `bionic311` SEBELUM
   APK; gerbang angka device keep/tune/kill); wiring (lint debounce →
   `vm.problems` → `setDiagnostics`; autocomplete async PyCall budget+
   timeout); syarat review PR #31 dipenuhi (fix McCabe `preorder`,
   **`rope_layer` DIKELUARKAN** — ditunda, test file masuk daftar
   `check.sh`); non-goals eksplisit.
2. **Nasib PR #31**: kode-nya di-port per-file ke branch sesi baru dengan
   perbaikan; setelah PR v1.0.23 dibuka, #31 di-close sebagai *superseded*
   (topologi satu-kandidat, SKILL 28).
3. Pertanyaan terbuka untuk user (lihat §11).

**Gate F1:** user approve RFC (explicit).

## 5. FASE 2 — implementasi (commit atomik, tiap commit check.sh hijau)

| # | Commit | Isi + guard + mutasi |
|---|---|---|
| 1 | `fix(update)` | `readCache(context)` via `cachePath(context)` (bukan field statis); dedupe log FAIL (satu owner); unit test lintas-instance ("instance baru membaca cache warisan"); **mutasi**: kembalikan `?: return null` statis → test merah |
| 2 | `feat(about)` | Model B: 3 kartu lisensi expand-in-place (pola drawer PLUGINS) memuat `assets/licenses/*` (nol aset baru), `SelectionContainer` (teks bisa disalin), copy English, chip tagline, versi+versionCode, max-480dp dua orientasi; guard: SelectionContainer ada, tanpa nested-scroll lisensi, aset terbaca; mutasi; UAT dua orientasi |
| 3 | `feat(editor)` | `editor.js`: `override` → `[zcodeCompletions, localCompletionSource, globalCompletion]` (urutan final ditentukan harness); rebuild bundle (pin versi, SHA tercatat); verifikasi **browser harness byte-exact** (SKILL 24) sebelum CI; guard marker bundle; mutasi |
| 4 | `feat(intelligence)` | Port `app/src/main/python/editor/` dari PR #31 **tanpa rope_layer** + fix McCabe; test file **didaftarkan** di `tools/check.sh` + dijalankan DENGAN deps asli (jedi/pyflakes/mccabe/parso terpasang di venv); wiring Kotlin: LintScheduler (debounce ≥500 ms, file <256 KB, mati saat Run aktif) → merge Checker+pyflakes → `vm.problems` → `setDiagnostics`; autocomplete jedi async + timeout + opt-in; entri palette/PluginRegistry; **entri katalog** `packages.json` (jedi, pyflakes, mccabe) + status Library EXPERIMENTAL; breadcrumb `SPKE_*`; guard + mutasi per kontrak |
| 5 | `feat(preview)` **[kondisional — keputusan user §11]** | Preview PNG hasil `savefig` (detail di RFC saat keputusan masuk) |
| 6 | `build(release)` | `gradle.properties` → 1.0.23/26; `RELEASE_NOTES_V1.0.23.md` (kandidat); **siapkan konten `production.yml` v1.0.23 (revisi in-place) untuk dibuat/diedit user via web GitHub** (SKILL 28.6 — token agent tak bisa push `.github/workflows/*`); setelah file web dibuat: commit mirror `ci/workflows/` byte-identical + guard pasangan versi |

## 6. FASE 3 — rilis + UAT ganda

```text
PR arena/v1023-intelligence → review user → MERGE main (merge commit)
→ user dispatch production dari main (typed confirm, environment production)
→ 1 run → draft byte-exact → UAT device → publish draft (no rebuild)
```

**UAT A — fitur v1.0.23** (checklist masuk release notes): lint contoh
`undefined name` muncul di VPP+gutter+tap→goto; autocomplete lokal+global
instan tanpa deps; jedi opt-in via pack (catat ms → keep/tune/kill);
About dua orientasi + teks lisensi bisa disalin; **verifikasi cache fix:
auto-check kedua <24 jam harus menulis `cache local=` di Diagnostics**.

**UAT B — updater full-path 25→26**: checklist 8 poin
RELEASE_NOTES_V1.0.22 §UAT (sentinel, FGS saat pindah app, jalur batal,
offline, kill app) + `adb logcat -b crash` menyala dari laptop.
Lolos → label **UPDATER DEVICE VERIFIED** + publish.

## 7. Self-audit kepatuhan 2 aturan inti

**#1 HONEST:** semua klaim ber-sumber (§2); yang belum terverifikasi
ditandai — angka jedi ARMv7 belum ada (gerbang ukur yang menentukan),
preview-PNG belum diputuskan, verdict emulator-laptop = analisis bukan
percobaan; ladder status dipakai persis (check-path ≠ full-path; draft ≠
RELEASED); bukti UAT dilabeli *user report*.

**#2 METICULOUS:** satu writer per file (insiden 2026-08-21); kode
device-verified tak disentuh (About = UX change approved user; updater =
bug fix bermutasi-merah); test baru WAJIB masuk daftar pytest `check.sh`
(guard `TestCITestListCompleteness`); tiap guard diuji mutasi; sandbox
tanpa JDK → **CI hakim kompilasi Kotlin**; workflow file lewat tangan
user; bundle JS diverifikasi byte-exact via harness sebelum dipercaya;
audit kompilasi lintas-file sebelum push (SKILL 28.5).

## 8. Kendala

| Kendala | Penanganan |
|---|---|
| CI merah di compile Kotlin | iterasi cepat push→CI; audit 28.5 sebelum push; 2× merah di titik sama → revisi desain |
| jedi gagal gerbang ukur device | fitur tetap rilis tanpa jedi (Tier 0–2.5 tetap berdiri; kill-switch alami: uninstall deps) |
| Rebuild bundle drift | pin versi eksak + SHA + harness byte-exact + guard marker |
| Scope membengkak | potong per fase + laporkan (SKILL 6); versi 26 tidak dinegosiasikan naik dua kali |
| Token agent tak bisa push workflow | pola web-hand-off terbukti v1.0.22 (SKILL 28.6) |
| Ketersediaan user untuk UAT | checklist siap salin; bukti adb opsional tapi direkomendasikan |

## 9. Confidence

- Fase 0 tanpa insiden: **~95%** (docs+guard kecil, semua bukti sudah di tangan)
- Fix cache + About sampai CI hijau: **~90%**
- Intelligence (lint+wiring+bundle) sampai CI hijau: **~80%** (permukaan baru)
- jedi lolos gerbang ukur device: **~60%** (belum ada angka ARMv7 — dugaan)
- Rilis v1.0.23 sesuai alur (setelah UAT user): **~90%**

## 10. Sumber utama

- https://api.github.com/repos/muzape28-blip/ZCODE/releases/latest (dicek 2026-09-08)
- https://api.github.com/repos/muzape28-blip/ZCODE/actions/runs (run 32822998488)
- `ZCODE_DIAGNOSTICS.txt` (export user 2026-09-08, 846 baris)
- https://pypi.org/project/rope/#license (LGPLv3+ — dasar errata)
- https://github.com/codemirror/lang-python (CHANGELOG 6.1.0 — completion sources)
- Dokumen riset internal sesi 2026-08-25/26 + 09-08 (tercantum di header)

## 11. Keputusan user & pertanyaan terbuka

**Tercatat (chat 2026-09-08):** About = Model B expandable; copy English;
chip tagline dipakai; About nimbrung batch v1.0.23; terminal = v1.0.24;
rencana ini menunggu approval sebelum eksekusi.

**KEPUTUSAN FINAL USER (2026-09-08, via pertanyaan terstruktur):**
1. Rencana kerja ini **DISETUJUI PENUH** — eksekusi mulai Fase 0, tiap fase
   berhenti di gate untuk laporan + review user.
2. **preview-PNG MASUK v1.0.23** (commit #5 Fase 2 aktif).
