# RENCANA KERJA POST-v1.0.21 (2026-08-24)

**Status: APPROVED — eksekusi dimulai 2026-08-25 01:34 WIB (approval final user
"gas"/"Lanjut"). Eksekusi ber-gate per fase sesuai §3–§5.**
Penulis: agent Arena (sesi 01a03332). Reviewer: user (pemilik perangkat).
Basis: `main` @ `2793c19` (merge PR #29). Branch kerja: `arena/01a03332-zcode`.
Revisi 2026-08-24 23:22 WIB: keputusan UI/UX v1.0.22 dicatat (§5.2 + §11),
download menjadi foreground service (D11 — keputusan user), koreksi fakta D10
(tidak ada OkHttp di stack).
Revisi 2026-08-24 23:58 WIB: hasil research Context7 + dokumentasi resmi
Android/GitHub (URL di §10) — koreksi D11 (limit 6 jam hanya berlaku utk
targetSdk 35+), D1/D4 (verifikasi SHA-256 via field `digest` respons API GitHub,
terverifikasi live di aset v1.0.21), flag intent install D5, status pemakaian
Context7 (jujur, §10). Konfirmasi UAT v1.0.21 diterima user (dipindah ke §11
"terjawab").
Revisi 2026-08-25 01:30 WIB: **sweep mismatch atas perintah user** — konsistensi
file vs repo (semua OK; anchor `download()` 767→759), semantik resmi
`releases/latest` (D2), **cacheDir = best-effort** (D4/D5/§5.2: APK bisa hilang,
desain menangani), elemen `<cache-path>` FileProvider (§5.3), riset awal Fase 3
(§6: jedi/rope/mccabe/Chaquopy terverifikasi, pyflakes & parso belum).
**Masih menunggu approval final.**

Dokumen ini adalah **blueprint kerja**: target, rancangan, alur, file yang disentuh,
kendala, dan self-audit kepatuhan 2 aturan inti. Sesuai budaya repo (RFC → approval →
implementasi → guard → CI → UAT device → publish), dokumen ini adalah tahap "RFC".

---

## 0. Kenapa rencana ini ada (3 gap nyata)

1. **Rekor repo tertinggal kenyataan GitHub.** v1.0.21 sudah RELEASED publik
   (2026-08-23), tapi `main` belum punya commit bukti rilis (pola v1.0.20 punya),
   `SIGNING_ZCODE.md` masih status era v1.0.20, release notes masih bertanda kandidat,
   dan baris "update continuity: NOT YET DEVICE VERIFIED" belum di-close padahal
   update v1.0.20→v1.0.21 in-place sudah terjadi.
2. **Tiga PR mengambang di GitHub** (#30/#31/#32 Spike Intelligence Engine) tanpa
   vonis; review sudah ada (`docs/REVIEW_PR30_31_32_2026_08_24.md` di branch
   `arena/01a0336a-zcode`) tapi belum ditindaklanjuti (belum di-post, belum close,
   belum masuk main).
3. **v1.0.22 = verified one-tap update masih "baru DESIGNED"** (catatan eksplisit
   `ROADMAP_V1021_V1022_SAFETY_AND_UPDATE.md` baris 43) — belum ada RFC implementasi.

Urutan kerja di bawah menutup 3 gap itu secara berurutan. **Fase tidak mulai
sebelum gate fase sebelumnya hijau.**

---

## 1. Target

**Target keseluruhan (definisi selesai):**
- (T1) Rekor repo = kenyataan: bukti v1.0.21 tercatat, `SIGNING_ZCODE.md` akurat,
  baris update-continuity = DEVICE VERIFIED (setelah konfirmasi user).
- (T2) PR #30 closed, PR #32 closed, PR #31 = DRAFT satu-satunya; review tercatat di main.
- (T3) v1.0.22 updater: DESIGNED → **RFC disetujui → IMPLEMENTED → CI VERIFIED →
  (lalu, di luar siklus kode ini: production dispatch + device UAT + publish)** —
  dengan semua gate rumah (guard, mutasi, one-build).
- (T4) RFC spike v1.0.23 tertulis (diparkir, tanpa kode) supaya antrean v1.0.23 jelas
  dan tidak lagi diselesaikan oleh agent eksternal tanpa kontrak.

**Bukan target (non-goal siklus ini):** fitur baru di luar updater; penyusunan ulang
UI; dependensi baru; publish v1.0.22 (butuh device + approval user, dilakukan
setelah kode selesai); pengerjaan kode engine spike.

---

## 2. Basis fakta saat ini (sumber tiap klaim — aturan #1)

| Fakta | Nilai | Sumber |
|---|---|---|
| v1.0.21 published | draft=false, 2026-08-23T01:40:05Z, target `2793c19` = tag `v1.0.21` = kepala main | GitHub API (dicek agent 2026-08-24) |
| Production run | `32570675883` SUCCESS, `workflow_dispatch` dari main, 7m24s, **tepat 1 run** | GitHub API |
| Aset publik | 3 aset: APK **34.719.925 B**, `.sha256`, `apksigner.txt` | GitHub API |
| SHA-256 APK | `1d84c60c6d1574610b25464ee8dfae7101e2c63669bfd94473ebe52e4379b4e3` | digest aset GitHub + konsisten release notes |
| Field `digest` respons API releases | `sha256:1d84c60c...e3` utk `ZCODE-v1.0.21.apk` (live di `GET /releases/latest`), `size` 34.719.925 B — keduanya cocok dgn baris di atas | GitHub API (dicek agent 2026-08-24 saat research Context7) |
| Signer | terverifikasi oleh step `apksigner verify` di workflow (run SUCCESS = fingerprint cocok `40139219...8bd2`) | kontrak `production.yml` v1.0.21 + run |
| UAT device | "PRODUCTION + DEVICE VERIFIED", update in-place tanpa uninstall/clear-data, continuity PASS, crash NONE | **release notes = laporan user; agent tidak bisa verifikasi ke HP** |
| Re-download independen APK | **GAGAL dari sandbox** (network ke release-assets diblok) — tidak ada klaim byte-check independen baru | percobaan agent 2026-08-24 |
| Suite test lokal | **668 passed** (10 file test root, py3.11.2) | dieksekusi agent di sesi ini |
| Review 3 PR | PR #30: CI RED + hapus plugin + provenance hilang + test sendiri collection error; PR #31: 20/20 tanpa deps, **18/20 dengan deps** (bug McCabe); PR #32: 10/13 merah (orkestrasi jebol) | dieksekusi agent di worktree + dokumen review sesi paralel |
| Konsistensi file blueprint vs repo | Semua file yang dirujuk dokumen ADA di `main` @ 2793c19 (18 path dicek 2026-08-25); `app/src/main/res/xml/` sudah ada (isi: `data_extraction_rules.xml`) → `file_paths.xml` memang file baru | dicek agent 2026-08-25 (sweep mismatch) |
| `androidx.core` | 1.12.0 (cukup utk overload `ServiceCompat.startForeground(..., type)`) | `app/build.gradle.kts:137` |

**Yang TIDAK terverifikasi (jujur):** perilaku updater di HP (belum ada kodenya),
perjalanan jedi dkk. di ARMv7/Chaquopy (belum ada spike device), hasil drill recovery
key signing (NOT EVIDENCED, terbuka), perilaku battery-killer XOS terhadap FGS
(hanya bisa dites di device — UAT), apakah `POST_NOTIFICATIONS` di-pre-grant pada
jalur upgrade in-place (didesain untuk dua-duanya; dites UAT), API low-level
pyflakes/parso (belum ada di index Context7 — diverifikasi dari source saat Fase 3).

---

## 3. FASE 0 — Tutup rekaman v1.0.21 (docs + 1 guard kecil)

**Target:** T1. **Prasyarat:** konfirmasi user atas verbatim UAT (update in-place?
sentinel file/settings/paket utuh?) supaya label `DEVICE VERIFIED` ditulis benar.
**Status prasyarat (2026-08-24): DITERIMA USER** — "all passed safe and sound,
sesuai yang kita harapkan" (update in-place di atas v1.0.20, sentinel utuh).

| File | Perubahan |
|---|---|
| `docs/RELEASE_NOTES_V1.0.21.md` | Baris status: kandidat/NOT RELEASED → **RELEASED 2026-08-23T01:40:05Z**; blok bukti: run `32570675883`, bytes, SHA-256, signer, run count = 1, verdict UAT (dilabeli *user report*) |
| `docs/SIGNING_ZCODE.md` | §3 status block: tambah `PUBLIC RELEASE : YES — v1.0.21`; §9 baru "v1.0.21 production evidence" (meniru struktur §8 v1.0.20: evidence chain 7 poin); baris update-continuity → **DEVICE VERIFIED (v1.0.20→v1.0.21 in-place, user report)** setelah konfirmasi; `BYTE-FOR-BYTE RECOVERY DRILL: NOT EVIDENCED` tetap |
| `docs/ROADMAP_V1021_V1022_SAFETY_AND_UPDATE.md` | Status baris v1.0.21 → RELEASED (perubahan kecil, struktur dokumen tidak disentuh) |
| `test_zcode_production_release.py` | Tambah **post-release evidence guard** v1.0.21 (konstanta: pasangan 1.0.21/24, run ID, SHA-256, signer; asersi kehadiran di docs) — meniru pola guard pasca-rilis v1.0.20; **uji mutasi** (hapus baris bukti → merah) |
| `docs/REVIEW_PR30_31_32_2026_08_24.md` | Dipindah ke main (cherry-pick file doc dari branch `arena/01a0336a-zcode`, **tanpa** kode engine-nya) + addendum: (a) risiko rope fallback string-naif di PR #31 (bertentangan aturan rename fail-closed v1.0.21), (b) audit rangkuman sesi KAI 9000 (halusinasi: fitur kamera, isi v1.0.21 salah, usulan workflow bertentangan pin full-SHA) sebagai konteks mengapa klaim body PR #32 tidak dipercaya |

**Alur:** edit docs → tambah guard → jalankan suite penuh (harus 669) + mutasi merah →
commit per unit kecil → push → CI hijau → **henti; lanjut Fase 1.**

**Gate Fase 0:** semua baris status konsisten di 4 docs; guard baru hijau + mutasi
merah terbukti; CI `check`+`build` hijau; user mengkonfirmasi verbatim UAT.

---

## 4. FASE 1 — Vonis PR #30/#31/#32 (operasi GitHub, tanpa kode produk)

**Target:** T2.

| Aksi | Rincian |
|---|---|
| Post review ke PR #30 | Ringkas blocker + tautan doc review: CI RED (run gagal di `check`), hapus 4 entry point plugin DEVICE VERIFIED, hapus header provenance GPLv3/ZABACODE (gate `check.sh`), test sendiri collection error, lineage branch mencurigakan (nama branch = branch PR #29 yang sudah merge). **Vonis: CLOSE (reject)** |
| Post review ke PR #32 | Merah dua mode (tanpa deps 11/2, dengan deps 10/3 — orkestrasi inti jebol: `NameError PyflakesWrapper`), tanpa integrasi apa pun, body menyalin template #30 + klaim palsu, audit sesi penulis (bagian addendum). **Vonis: CLOSE** |
| Post review ke PR #31 | Paling layak (murni aditif, gate hijau, arsitektur rapi) tapi belum merge: bug McCabe (fix 1 baris `preorder(tree, mccabe.ASTVisitor)`), false-green CI (test tak masuk `check.sh`), rope rename fallback string-naif (harus fail-closed), deps tak dibundel, tanpa wiring UI. **Vonis: DRAFT — kandidat tunggal v1.0.23** |
| Close #30, close #32, set #31 → draft | via `gh pr close` / `gh pr ready --undo`; branch asal dibiarkan (bukan milik sesi ini untuk dihapus) |
| Commit doc review ke main | sudah masuk Fase 0 (file doc + addendum) |

**Gate Fase 1:** status 3 PR sesuai vonis; komentar review ter-post; tidak ada file
produk yang berubah.

---

## 5. FASE 2 — v1.0.22: Verified One-Tap Update (bagian terbesar)

### 5.1 RFC — keputusan desain (yang harus disetujui user)

- **D1 — Pembagian integritas (JUJUR, bukan teater):** apa yang app lakukan di device =
  (a) cek release publik via GitHub API (unauthenticated, repo publik), (b) unduh APK,
  (c) hitung SHA-256 dan bandingkan dengan field `digest` (`sha256:...`) pada respons
  API GitHub (terverifikasi live di aset v1.0.21 — §2), (d) luncurkan installer system.
  Yang **tidak** bisa app lakukan di device = menjalankan aapt/apksigner (tidak ada
  toolchain di HP). Identitas package+signer dijaga **oleh Android saat install**
  (signer beda = install ditolak system; versionCode turun = `INSTALL_FAILED_DOWNGRADE`).
  Dokumen & UI akan menulis persis pembagian ini — tidak akan ada klaim
  "verifikasi signer di HP".
- **D2 — Sumber & rate-limit:** `GET /repos/muzape28-blip/ZCODE/releases/latest`
  (unauthenticated, 60 req/jam/IP — terkonfirmasi docs GitHub 2026-08-24). Cek hanya:
  saat user tap tombol + maks 1x saat app start (silent, tanpa popup — **disetujui
  user 2026-08-24**), hasil di-cache 24 jam di prefs. **Tidak ada polling background.**
  Parse: ambil `tag_name` → versi; tolak tag yang tidak cocok pola `v\d+\.\d+\.\d+`.
  Header `Accept: application/vnd.github+json`. Semantik resmi endpoint (verified
  github/docs 2026-08-25): `/releases/latest` **hanya** mengembalikan release
  non-prerelease & non-draft, diurutkan by `created_at` = tanggal **COMMIT** yang
  dipakai rilis (bukan tanggal publish). Konsekuensi: (a) draft/prerelease tak
  pernah bocor ke app; (b) bila sebuah rilis dibuat dari commit lebih lama dari
  rilis sebelumnya, `latest` bisa mengembalikan versi lama → D3 menolak
  menwarkan versi ≤ versi lokal (worst case "up to date", **never downgrade**).
  Invarian kerja: setiap rilis ZCODE di-cut dari main tip sehingga `created_at`
  monoton naik.
- **D3 — Perbandingan versi:** parsing numerik per-segmen (bukan string! — kelas
  bug D yang sudah dimakamkan; guard: `1.0.10 > 1.0.9` harus benar). Tampilkan hanya
  bila versi remote > versi lokal (konservatif, pola v1.0.21).
- **D4 — Download & penyimpanan:** streaming ke `cacheDir` privat, **dijalankan oleh
  foreground service** (D11 — keputusan user 2026-08-24: unduhan harus selamat saat
  user beralih ke app lain, mis. SMS/panggilan telepon). Pola di-clone dari
  `PackageEngineV2.download()` yang sudah terbukti di device
  (`PackageEngineV2.kt:759`): `HttpURLConnection`, buffer 64KB, SHA-256 sambil
  streaming, progress di-throttle ≥256KB (pelajaran ARMv7: "47 detik SENYAP"),
  cancel → hapus file parsial. SHA-256 hasil hitung dibandingkan dengan field
  `digest` respons API (prefix `sha256:`) — file `.sha256` publikasi **tidak perlu
  diunduh oleh app** (tetap dipublikasikan oleh production workflow untuk
  verifikasi manual). Guard ukuran (tolak > 100 MB, dari field `size` API), guard
  storage mengikuti pola package engine (1.5× estimasi / 100 MB mana yang lebih
  besar). Gagal jaringan → pesan jujur + retry manual (tidak auto-loop).
  **Catatan resmi (verified 2026-08-25):** file di `cacheDir` **bisa dihapus
  sistem saat storage rendah** (atau oleh user via "clear cache") — APK di cache
  adalah **best-effort, bukan jaminan permanen**; desain wajib menangani hilangnya
  APK (D5.6 + §5.2 kasus batas), dan app tetap menjaga cache-nya sendiri (hapus
  APK lama saat ada versi baru).
- **D5 — Alur install (bebas-siul, sesuai non-goal roadmap):**
  1. User tap "Download & Update" (persetujuan eksplisit — tidak ada auto-download).
  2. Setelah download+verify OK → app **flush semua draft workspace**
     (`WorkspaceViewModel.flushSaveSync(verifyAllDrafts = true)`,
     `WorkspaceViewModel.kt:503` — hook yang sama yang dipakai `MainActivity`
     sebelum process rebirth) — flush gagal (return false) = install tidak
     diluncurkan (fail-closed).
  3. Tulis **update receipt** (`update/receipt.json`: versi lama→baru, SHA, path,
     timestamp, `PENDING_INSTALL`) **setelah** flush sukses, atomik (tmp+rename).
  4. Jika belum pernah, pandu user sekali ke izin `REQUEST_INSTALL_PACKAGES`:
     cek `packageManager.canRequestPackageInstalls()`; jika false → buka
     `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` dengan `Uri.parse("package:$packageName")`
     — di-record di breadcrumb.
  5. Luncurkan installer system via `FileProvider` + `content://` +
     `ACTION_VIEW` dengan `setDataAndType(uri, "application/vnd.android.package-archive")`
     + `FLAG_GRANT_READ_URI_PERMISSION` + `FLAG_ACTIVITY_NEW_TASK` (MIME `.apk`
     diturunkan otomatis oleh FileProvider) → **user tap dialog system** →
     Android melakukan upgrade in-place (signer sama, data aman).
  6. **Setelah restart** (APK baru), Diagnostics baca receipt: cocok →
     `UPDATE_INSTALLED from→to`; receipt ada tapi versi tak berubah →
     `UPDATE_PENDING` (user belum confirm dialog); receipt versi lama →
     `UPDATE_RECEIPT_STALE` (dibuang). **Saat memproses receipt READY/PENDING,
     cek keberadaan file APK dulu** (cache = best-effort, D4): file hilang →
     state balik `AVAILABLE` (unduh ulang) + breadcrumb, bukan error.
     Failure di langkah mana pun = breadcrumb `UPDATE_*_FAIL` dengan alasan;
     **versi lama tidak pernah disentuh** — satu-satunya operasi destruktif
     adalah Android saat user confirm, dan itu di luar kontrol app (jujur
     dicatat).
- **D6 — Anti downgrade / reinstall sama:** hanya tawarkan bila versi remote lebih
  baru (D3); backstop = penolakan Android atas versionCode turun (D1).
- **D7 — UI (label & posisi dikonfirmasi ulang user 2026-08-24: "Sesuai D7"):**
  item drawer label **"Cek Update"** (keputusan eksplisit user) di bawah "About &
  Contribute" (posisi D7 dipertahankan), ikon vektor `ZIcons` baru (bukan emoji,
  24dp manual, tint ikut tema), state machine penuh di §5.2 (ringkas: `NONE →
  CHECKING → AVAILABLE(vX.Y.Z) → DOWNLOADING(p%) → READY → FAILED(reason)`),
  dialog konfirmasi dengan detail jujur (versi target, ukuran, SHA-256 12 digit
  pertama + tombol salin, "data tidak akan dihapus — upgrade in-place").
  Copy dialog = **English** (keputusan user: konsisten dengan label eksisting app).
- **D8 — Telemetri:** breadcrumb baru (semua bisa disalin): `UPDATE_CHECK_BEGIN/_OK/
  _NEWER/_SAME/_FAIL`, `UPDATE_DOWNLOAD_BEGIN/_OK/_FAIL`, `UPDATE_VERIFY_OK/_FAIL`,
  `UPDATE_FLUSH_OK/_FAIL`, `UPDATE_RECEIPT_WRITTEN`, `UPDATE_INSTALL_LAUNCH`,
  `UPDATE_INSTALLED`, `UPDATE_PENDING`, `UPDATE_RECEIPT_STALE`,
  `UPDATE_DOWNLOAD_CANCELLED`, `UPDATE_FGS_START`, `UPDATE_FGS_STOP`.
- **D9 — Testing:** JVM unit test untuk bagian murni (versi parse, SHA-256 + fixture,
  parse respons API dari fixture, receipt JSON, state machine) di
  `app/src/test/.../update/`; guard lexikal baru di `test_zcode_kotlin_guards.py`
  (kontrak: tidak ada string credential/PAT di jalur update, FileProvider MIME
  benar, receipt HANYA ditulis setelah flush sukses — dicek urutan di kode,
  perbandingan versi bukan string, set breadcrumb lengkap, manifest mendeklarasikan
  `foregroundServiceType` + permission FGS (D11)); **setiap guard diuji
  mutasi merah→hijau** (pola rumah).
- **D10 — Non-goals (diturunkan dari roadmap + eksplisit):** tanpa silent/background
  install; tanpa auto-download; tanpa downgrade/reinstall versi sama; tanpa
  PAT/credential di aplikasi; tanpa Play Store; **tanpa dependensi library baru**
  (koreksi 2026-08-24: tidak ada HTTP library di stack — jalur update memakai
  `java.net.HttpURLConnection`, pola yang sudah terbukti di `PackageEngineV2`,
  + `org.json` bawaan Android); tanpa redesign Workbench; tanpa janji "100% silent
  di semua ROM" (roadmap: keyakinan rendah, tidak dijanjikan).
- **D11 — Foreground service download (keputusan user 2026-08-24; detail diverifikasi
  thd docs resmi 2026-08-24/25, URL di §10):** service started, proses sama,
  `UpdateDownloadService` dengan `android:exported="false"`: di-start **hanya** dari
  tap user "Download & Update" (D10: tanpa auto-download; sekaligus aman terhadap
  aturan bg-start FGS untuk target 31+) via `startForegroundService` → dalam
  `onStartCommand` panggil **`ServiceCompat.startForeground(this, id, notification,
  type)`** (helper androidx yang direkomendasikan docs resmi A14; id ≠ 0) + tangkap
  `ForegroundServiceStartNotAllowedException` (API 31+). Nuansa resmi: bila type
  tidak di-pass saat pemanggilan, type default ke nilai manifest — kita tetap
  pass eksplisit. Manifest (aditif):
  `<service android:foregroundServiceType="dataSync">` + `FOREGROUND_SERVICE` +
  `FOREGROUND_SERVICE_DATA_SYNC` (permission install-time, tanpa prompt — aturan
  target 34: type wajib di-declare di manifest, `startForeground()` melempar
  `MissingForegroundServiceTypeException` jika tidak; sistem cek permission saat
  promosi & melempar `SecurityException` jika kurang) + `POST_NOTIFICATIONS`.
  Pilihan type `dataSync` sesuai definisi resminya: "data transfer operations —
  data upload or download, backup-and-restore, import or export, fetch data, local
  file processing" (tidak ada runtime prerequisite).
  Notifikasi **ongoing** di channel `update` (low importance): judul "ZCODE —
  downloading update", isi "12.3 / 34.7 MB (35%)", tap → buka app.
  `POST_NOTIFICATIONS` diminta **kontekstual** (satu kali, saat user pertama kali
  tap "Download & Update" — target ≥33 memberi app kendali penuh atas timing
  prompt); bila ditolak → FGS tetap jalan, notifikasi FGS tetap terlihat di
  **Task Manager** (bukan di notification drawer) — verified docs; dicatat
  breadcrumb, tidak memblokir (dites UAT di device).
  Owner tunggal = service (bukan ViewModel); UI subscribe via `StateFlow` in-process;
  cancel dari dialog → `stopForeground` + `stopSelf` + hapus parsial;
  selesai/gagal → `stopSelf`. Batasan jujur (koreksi research 2026-08-24):
  **limit 6 jam untuk FGS `dataSync` HANYA berlaku untuk app yang targeting
  Android 15+** (device 15+) — ZCODE target 34 → **tidak ada limit waktu** di
  v1.0.22. Bila kelak `targetSdk` naik ke 35, wajib implementasi
  `Service.onTimeout` (dicatat di RFC sebagai forward-looking). Risiko nyata di
  target 34 = handling background OEM (XOS battery killer) — FGS adalah mitigasi
  terkuat yang tersedia; perilaku persis hanya bisa dites UAT.

### 5.2 UI/UX spec — fitur update (diputuskan bersama user, 2026-08-24)

**Keputusan terekam (chat 2026-08-24):**

| # | Pertanyaan | Keputusan user |
|---|---|---|
| 1 | Posisi baris di drawer | **Sesuai D7** — di bawah "About & Contribute" |
| 2 | Auto-check sunyi saat app start (cache 24 jam, tanpa popup) | **Setuju** |
| 3 | Download saat user beralih ke app lain (SMS/panggilan telepon mendesak) | **Foreground service** — unduhan tetap jalan (D11) |
| 4 | Bahasa copy UI | **Konsisten = English** (label drawer tetap **"Cek Update"** — keputusan eksplisit user) |

**Tata letak (nol screen baru, nol redesign):**

- Satu baris baru di drawer (pola `DrawerItem`, `WorkbenchScreen.kt:1571`): ikon
  `ZIcons` baru + label **"Cek Update"** + **suffix status** 12sp di kanan
  (preseden: baris THEME di dalam kotak TOOLS). `DrawerItem` di-extend parameter
  opsional (icon/suffix) → baris lama perilakunya byte-identik.
- Suffix per state: `v1.0.21` (biasa) · `checking…` · `up to date` · `v1.0.22`
  (warna primary) · `12.3/34.7 MB` (sedang unduh) · `ready` (primary) · `failed`
  (warna error — preseden ProblemsBanner).
- Tap baris → **satu dialog** `AlertDialog` Material3 (sudah dipakai di mana-mana di
  app; harus muat landscape Infinix ~360dp: judul 1 baris + teks ≤2 baris + 2 tombol),
  drawer ditutup (pola `closeDrawerThen`). Dialog situasional: state sekarang +
  tombol yang benar untuk state itu.

**Loop (state machine penuh):**

```text
 app start ─► IDLE (auto-check sunyi bila cache >24 jam — D2)
   │ tap
   ▼
 CHECKING ─┬─► UPTODATE               (remote ≤ lokal, compare angka — D3)
           ├─► AVAILABLE ──[Download & Update]──► DOWNLOADING (FGS — D11, progress, Cancel)
           └─► FAILED ◄─────────────────────── retry ──────────────────────┘ (gagal unduh/verifikasi)
                                        │ selesai + SHA-256 OK
                                        ▼
                              VERIFYING ─► FLUSHING (flushSaveSync — gagal = STOP)
                                        │
                                        ▼
 READY (receipt ditulis) ──[Install now]──► dialog SISTEM Android
      ▲                                        │
      │ (user batal di dialog system)   restart, versionCode naik
      ▼                                        ▼
PENDING (APK di cache — best-effort;  UPDATE_INSTALLED → baris IDLE di versi baru
cek keberadaan file dulu; hilang →  + breadcrumb di Diagnostics
balik AVAILABLE, unduh ulang)
```

Jaminan loop:
1. Tiap state = tepat **satu aksi utama + satu jalan keluar** (Later/Cancel/Close).
   Tidak ada buntu.
2. Gagal selalu **retryable** dari state-nya (tidak mulai dari nol).
3. **Idempotent**: cek ulang saat READY tidak mengunduh ulang — cache APK dicek
   terhadap `latest` dulu; versi cache ≠ latest → cache dibuang, unduh ulang.
4. **Tahan restart**: tiap transisi ke-breadcrumb (D8) + receipt persist → setelah
   restart app tahu INSTALLED / PENDING / STALE; penerimaan PENDING selalu
   diawali cek keberadaan file APK (D5.6).
5. **Tutup dialog ≠ batalkan** (download jalan di FGS — D11); progress tetap terlihat
   di suffix drawer + notifikasi.

**Bagaimana kerjanya (7 langkah — semua nyangkut di kode yang sudah ada):**

1. **Cek** — `GET /repos/muzape28-blip/ZCODE/releases/latest` (unauthenticated,
   timeout 10s, `Accept: application/vnd.github+json`) via `HttpURLConnection` +
   `org.json` bawaan Android; tag tidak cocok `v\d+\.\d+\.\d+` → tolak; compare
   numerik (D3); cache 24 jam (D2).
2. **Unduh** — FGS (D11) menjalankan `UpdateDownloader` (pola
   `PackageEngineV2.download()`, `PackageEngineV2.kt:759`): streaming ke
   `cacheDir/update/ZCODE-vX.apk`, buffer 64KB, SHA-256 sambil jalan, progress
   throttle ≥256KB, pre-check ukuran (≤100MB dari field `size`) + storage bebas
   (≥1.5×), hapus parsial saat gagal/batal.
3. **Verifikasi** — SHA-256 hasil vs field `digest` respons API (prefix `sha256:`
   dibuang); mismatch → `UPDATE_VERIFY_FAIL`, file **dihapus**, user tidak boleh
   lanjut (D1: app memverifikasi bytes vs checksum publikasi; signer dijaga
   Android saat install).
4. **Flush** — `flushSaveSync(verifyAllDrafts = true)` (`WorkspaceViewModel.kt:503`):
   **return false = STOP** (fail-closed); UI menunjukkan "Saving your workspace…"
   agar user tahu datanya sedang diamankan.
5. **Receipt** — `update/receipt.json` (versi lama→baru, SHA, path, timestamp,
   `PENDING_INSTALL`) ditulis **setelah** flush sukses, atomik (tmp+rename).
6. **Install** — `FileProvider` + `content://` + `ACTION_VIEW` +
   `setDataAndType(..., "application/vnd.android.package-archive")` +
   `FLAG_GRANT_READ_URI_PERMISSION` + `FLAG_ACTIVITY_NEW_TASK` (D5); bila
   `canRequestPackageInstalls()` false → dialog panduan sekali (buka
   `ACTION_MANAGE_UNKNOWN_APP_SOURCES`); setelah itu **Android yang pegang** —
   user tap dialog system sendiri.
7. **Setelah restart** — baca receipt (D5.6): **cek file APK masih ada** (cache
   best-effort — D4); versionCode naik → `UPDATE_INSTALLED` + baris IDLE versi
   baru; versionCode sama (user batal di dialog system) → `UPDATE_PENDING`,
   baris `AVAILABLE`, APK di cache; file hilang → balik `AVAILABLE` (unduh ulang);
   receipt versi lama → `UPDATE_RECEIPT_STALE`, dibuang.

**Copy dialog (English — keputusan user; label drawer "Cek Update" tetap):**

| State | Teks dialog | Tombol |
|---|---|---|
| UPTODATE | "You're up to date — v1.0.21" | `Check again` / `Close` |
| AVAILABLE | "Update available — v1.0.22, 34.9 MB" + SHA-256 12 digit + tombol salin | **`Download & Update`** / `Later` |
| DOWNLOADING | progress bar (deterministik; indeterminate bila `Content-Length` tak ada) + "12.3 / 34.7 MB (35%)" | **`Cancel`** (FGS di-stop, parsial dihapus) |
| VERIFYING | "Verifying package (SHA-256)…" | `Close` |
| FLUSHING | "Saving your workspace…" | `Close` |
| READY | "Downloaded & verified. Workspace saved. Android will complete the install." | **`Install now`** / `Later` |
| FAILED | "Update failed — <reason>" (Network / Not enough free space / Verification failed / Package removed from cache) | **`Retry`** / `Close` |
| PENDING | "v1.0.22 is ready but not installed yet." | **`Install now`** / `Later` |
| Permission | "Allow 'Install unknown apps' for ZCODE?" (hanya bila belum diizinkan) | `Open settings` / `Cancel` |

**Kasus batas (dan jawabannya):**

| Kasus | Perilaku |
|---|---|
| Offline saat cek | FAILED "Network", retryable, app jalan normal |
| User beralih ke app lain / app di-kill saat unduh | **FGS melanjutkan** + notifikasi progress (keputusan user #3); OS/OEM membunuh FGS (battery killer XOS) = FAILED jujur + retry (dites UAT) |
| Batal di dialog system (tidak install) | `UPDATE_PENDING`; APK di cache (best-effort — bisa dihapus OS/user); bila hilang saat akan install lagi → balik `AVAILABLE`, unduh ulang |
| Cache dihapus OS (storage rendah) / user "clear cache" saat READY/PENDING | Cek keberadaan file APK sebelum install/receipt (D5.6); hilang → balik `AVAILABLE` + pesan jujur "Package removed from cache", unduh ulang (verified docs: cacheDir bukan storage persisten) |
| Versi baru keluar saat APK lama masih di cache | Cek selalu vs `latest`; cache ≠ latest → cache dibuang, unduh ulang |
| Hash mismatch | Gagal total, file dibuang, pesan jujur "do not install", breadcrumb `UPDATE_VERIFY_FAIL` |
| Storage kurang | Ditolak **sebelum** unduh (guard D4), pesan jujur |
| `POST_NOTIFICATIONS` ditolak (API 33+) | FGS tetap jalan; notifikasi FGS tetap di Task Manager (bukan drawer) — verified docs; breadcrumb mencatat (dites UAT) |

### 5.3 File yang disentuh (implementasi, setelah RFC disetujui)

| # | File | Perubahan |
|---|---|---|
| 1 | `docs/RFC_V1022_ONE_TAP_UPDATE.md` | **BARU** — RFC ini dalam bentuk dokumen permanen (D1–D11 + spec UI/UX §5.2 + gate + rencana UAT) |
| 2 | `app/src/main/java/com/zaba/zcode/core/update/UpdateChecker.kt` | **BARU** — client GitHub API (`HttpURLConnection` + `org.json` bawaan Android, unauthenticated), parse tag, compare versi numerik, cache 24 jam; tanpa credential |
| 3 | `app/src/main/java/com/zaba/zcode/core/update/UpdateDownloader.kt` | **BARU** — logika download murni (dipanggil service D11): streaming ke `cacheDir`, buffer 64KB, SHA-256 sambil jalan, progress throttle ≥256KB, guard ukuran/storage, hapus parsial saat gagal/batal (pola `PackageEngineV2.download()`) |
| 4 | `app/src/main/java/com/zaba/zcode/core/update/UpdateDownloadService.kt` | **BARU** — foreground service (D11): `ServiceCompat.startForeground` + notifikasi ongoing channel `update` + progress, `StateFlow` in-process, cancel aman, `stopSelf` saat selesai/gagal/batal |
| 5 | `app/src/main/java/com/zaba/zcode/core/update/UpdateReceipt.kt` | **BARU** — tulis receipt atomik (hanya setelah flush sukses), baca saat start-up + cek keberadaan file APK (D5.6) |
| 6 | `app/src/main/java/com/zaba/zcode/core/update/UpdateInstaller.kt` | **BARU** — cek/pandu `REQUEST_INSTALL_PACKAGES` (`canRequestPackageInstalls` + `ACTION_MANAGE_UNKNOWN_APP_SOURCES`), luncurkan FileProvider intent, tangani hasil |
| 7 | `app/src/main/java/com/zaba/zcode/UpdateViewModel.kt` | **BARU** — owner Activity-scoped (pola `PackageOperationViewModel`): state machine, mutex operasi, view atas `StateFlow` service, cancel aman saat Back |
| 8 | `app/src/main/AndroidManifest.xml` | Aditif: `FileProvider` + meta-data (wajib untuk `content://` APK); `<service>` `UpdateDownloadService` + `foregroundServiceType="dataSync"` + `exported="false"`; permission `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES` |
| 9 | `app/src/main/res/xml/file_paths.xml` | **BARU** — definisi path FileProvider: elemen **`<cache-path>`** (tag resmi utk file di `getCacheDir()`) + authority `${applicationId}.fileprovider` |
| 10 | `app/src/main/java/com/zaba/zcode/ui/workbench/WorkbenchScreen.kt` | Item drawer baru + dialog (komponen dipecah jadi composable kecil agar tidak membebani recomposisi — pelajaran PERF_PASS); `DrawerItem` di-extend parameter opsional — baris lama perilaku byte-identik |
| 11 | `app/src/main/java/com/zaba/zcode/ui/components/ZIcons.kt` | Ikon update vektor baru (aditif, 24dp manual — pola ZIcons) |
| 12 | `app/src/main/java/com/zaba/zcode/core/diagnostics/Breadcrumb.kt` | Tipe event `UPDATE_*` (aditif) |
| 13 | `app/src/main/java/com/zaba/zcode/ZcodeApp.kt` | Buat notification channel `update` (idempotent) saat start-up; baca receipt → `UPDATE_INSTALLED/_PENDING` ke Diagnostics (titik baca receipt final — `ZcodeApp` vs `MainActivity` — diputuskan saat implementasi, keduanya sudah dibaca) |
| 14 | `app/src/test/java/com/zaba/zcode/core/update/UpdateUnitTests.kt` | **BARU** — JVM unit test (bagian murni D9) |
| 15 | `test_zcode_kotlin_guards.py` | Guard keluarga update + uji mutasi (termasuk: manifest mendeklarasikan `foregroundServiceType` + permission FGS) |
| 16 | `gradle.properties` | Bump `zcode.versionName=1.0.22` / `zcode.versionCode=25` — **commit terakhir sebelum push** (pola rumah) |
| 17 | `docs/RELEASE_NOTES_V1.0.22.md` | **BARU** — notes kandidat (juga `--notes-file` draft release nanti) |

File yang **tidak** disentuh (disengaja): `production.yml` (versi v1.0.22-nya dibuat
baru saat dispatch, pola hardened yang sama + mirror `ci/`), seluruh engine spike,
`editor-src`, katalog, `strings.xml` (copy UI = literal Kotlin, pola eksisting app,
bahasa Inggris per keputusan user).

### 5.4 Urutan commit (atomik, kecil — pola rumah)

1. `docs(v1022): RFC one-tap update` → 2. `feat(update): checker (API+versi+cache)` →
3. `feat(update): downloader (stream+sha+guard)` → 4. `feat(update): foreground
service + notifikasi` → 5. `feat(update): receipt (atomik, post-flush)` →
6. `feat(update): installer (FileProvider+izin)` → 7. `feat(update): viewmodel +
ui drawer + ikon` → 8. `test(update): unit test + guard + mutasi` → 9.
`build(release): versi 1.0.22/25 + notes`. Tiap commit: check.sh hijau.

### 5.5 Gate & alur rilis (setelah kode selesai)

```text
check.sh + suite penuh hijau (668+N)
→ kotlin sanity + npm supply-chain (check.sh)
→ push → CI 4 job SUCCESS (check/build/compile-production-source)
→ user review diff
→ (saat dispatch) production workflow v1.0.22 dibuat (pola hardened v1.0.21,
  mirror ci/ byte-identik, guard full-SHA) + test guard pasangan 1.0.22/25
→ USER dispatch (workflow_dispatch + konfirmasi + environment production)
→ 1 run → 1 draft exact bytes
→ USER UAT di Infinix: update in-place v1.0.21→v1.0.22
  (sentinel: file .py terbuka+tertutup, settings, paket lama importable)
  + uji fitur tombol itu sendiri (cek update, download + FGS lanjut saat
  switch app, verify, install, receipt)
→ publish draft yang di-UAT (tanpa rebuild) → v1.0.22 RELEASED
```

**Kill-switch (pola Gerbong A):** tombol update adalah aditif — bila anomali di
device, user tetap bisa update manual via GitHub (jalur lama tidak pernah hilang).
Toggle OFF = perilaku persis sebelum fitur.

---

## 6. FASE 3 (diparkir) — v1.0.23 Spike Intelligence

Siklus ini **hanya menulis RFC** (`docs/RFC_V1023_SPIKE_INTELLIGENCE.md`):
kontrak JSON (`problems[] {line,col,severity,message}` bentuk yang sama dengan
`Checker` hari ini; `completions[] {label,kind,detail}`), strategi dependensi
(during spike = install via INSTALL MODULES, APK tak tersentuh), rencana pengukuran
device (breadcrumb `SPKE_LINT_MS/_COMPLETE_MS/_MEM_KB`), wiring target (pipeline
`vm.problems`→`setDiagnostics` yang sudah ada + source autocomplete CM6), syarat
perbaikan PR #31 (fix McCabe, buang fallback rename naif, test masuk check.sh),
gate device (keep/tune/kill berdasarkan angka, bukan tebakan). **Tanpa kode.**

**Riset awal sudah dikerjakan (2026-08-25, hasilnya masuk RFC):** entry point API
terverifikasi — **jedi** (Context7 `/davidhalter/jedi`): `jedi.Script(code, path=)`,
`.complete(line, col)` → objek `.name/.complete/.type` (`module`/`function`/
`class`)/`.name_with_symbols`, `.goto(line, col, follow_imports=, follow_builtin_
imports=)` → Name dengan `module_path`/line/col (petakan langsung ke kontrak
`completions[]` + go-to-def). **rope** (Context7 `/python-rope/rope`):
`Project` + `libutils.path_to_resource`; `Rename(project, resource, offset)
.get_changes(new_name, docs=, in_hierarchy=, resources=)` dan `ExtractMethod/
ExtractVariable(project, resource, start, end).get_changes(name, similar=)` →
`project.do(changes)`; undo = `project.history.undo()`; rename modul =
`offset=None`. Ini membuat syarat PR #31 jadi konkret: `get_changes` gagal/kosong
→ **tidak ada perubahan apa pun** (JANGAN fallback string-naif). **mccabe** (via
dok flake8, Context7 `/pycqa/flake8`): pelaporan C901 + `--max-complexity`; API
low-level `ASTVisitor.preorder(tree, visitor)` sudah diverifikasi langsung di kode
saat review PR #31 (fix 1 baris tetap valid). **Chaquopy** (docs resmi chaquo.com,
bukan di index Context7): mekanisme bundling resmi = blok `python { pip { install
"..." } }` di build.gradle; 5 library spike (jedi, parso, pyflakes, mccabe, rope)
semua **pure-Python** → tanpa isu wheel native. **Belum diverifikasi (jujur):**
API low-level pyflakes (`pyflakes.api`/Reporter) dan parso — keduanya tidak ada di
index Context7; wajib dibaca dari source saat menulis RFC (pelajaran bug McCabe:
API ditebak, bukan dibaca).

---

## 7. Self-audit kepatuhan 2 aturan inti (jujur, bukan retoris)

**Aturan #1 — HONEST ABOUT ANYTHING:**
- Tabel §2 memisahkan sumber tiap fakta (API-check vs laporan user vs eksekusi gw)
  dan secara eksplisit menulis yang **gagal** (re-download independen) dan yang
  **belum terverifikasi** (device, drill recovery, battery-killer XOS, pre-grant
  POST_NOTIFICATIONS pada jalur upgrade, pyflakes/parso low-level).
- Dokumen ini berlabel **DESIGNED** — tidak ada satu pun klaim "bekerja".
- D1 memaksa UI/dokumen menulis pembagian integritas apa adanya (app tidak bisa
  menjalankan apksigner di HP) — bukan klaim "verifikasi signer on-device".
- Semua angka hanya dari eksekusi nyata (668 test, 34.719.925 B, run ID,
  digest API live) — tidak ada "benchmark teoretis" (anti-pattern yang ditemukan
  di PR #30).
- Confidence per fase diberikan di §9; kelemahan rencana sendiri diakui di §9.
- Status pemakaian Context7 (key tidak terpakai, API v2 tanpa key via fetch_page,
  egress sandbox, library yang tidak ter-index) ditulis apa adanya di §10.
- **Bisa dibantah:** UAT v1.0.21 adalah laporan user (kini dikonfirmasi langsung
  user 2026-08-24) — tetap dilabeli *user report* di dokumen bukti.

**Aturan #2 — BE METICULOUS IN EVERYTHING:**
- File yang akan disentuh sudah dibaca/di-audit sesinya (release notes, SIGNING,
  roadmap, `WorkspaceMutationGate`, pola `PackageOperationViewModel`, `check.sh`,
  `test_zcode_production_release.py`, `WorkbenchScreen.kt` drawer/`DrawerItem`,
  `ZIcons.kt`, `PackageEngineV2.download()`, `WorkspaceViewModel.flushSaveSync`) —
  tidak edit buta; sweep 2026-08-25: semua path yang dirujuk dokumen diverifikasi
  ada di `main`, anchor baris dikoreksi (767→759).
- Desain FGS tidak ditebak: setiap klaim D11 diverifikasi terhadap dokumentasi
  resmi Android 2026-08-24/25 (URL di §10) — termasuk koreksi gw sendiri tentang
  limit 6 jam yang ternyata hanya utk target 15+.
- Lifecycle/ownership: update = satu owner Activity-scoped (pola yang sudah
  teruji di `PackageOperationViewModel`), download = satu owner FGS (D11),
  cancel aman, receipt atomik, flush sebelum instalasi, back navigation tidak
  mematikan operasi; cacheDir diakui best-effort (D4) dengan jalur pemulihan.
- Error path: tiap langkah punya breadcrumb FAIL + alasan; gagal di mana pun =
  versi lama utuh (satu-satunya operasi destruktif ada di tangan Android+user).
- Kelas bug, bukan satu kejadian: guard versi-numerik (kelas string-compare),
  guard urutan flush→receipt, guard anti-credential, cek keberadaan file APK
  (kelas "state menganggap file persisten") — masing-masing masuk guard/mutasi.
- Perubahan kecil utuh: 9 commit atomik, fase ber-gate, kill-switch.
- **Tidak ada writer paralel pada file yang sama** (pelajaran insiden a11y & PR
  #30/31/32): sesi ini satu-satunya yang menulis file di atas; dokumen sesi paralel
  hanya di-cherry-pick sebagai file utuh, tidak diedit bersama.
- **Bisa dibantah:** gw tidak bisa compile Kotlin di sandbox — CI hakim kompilasi
  (aturan rumah, ditulis di sini, tidak disembunyikan); gw tidak bisa uji device —
  UAT lu hakim akhir (checklist akan disertakan di notes rilis).

**Gengsi vs membantu user:** murni membantu. Alasan konkret: user **tanpa PC**,
siklus update sekarang = buka browser HP → cari release → unduh 34,7 MB → verify
hash → install; satu-tap update menghapus friksi terbesar di loop kerja user, dan
itu persis target produk di roadmap: *"user tanpa PC tidak perlu menjadi ahli
File Manager/APK signing"* (asal: ROADMAP_V1021_V1022, target jangka menengah) dan *"user lebih sering berkarya daripada mengurus
aplikasi."* Bukti bukan-gengsi: nol dependensi baru, nol redesign, UI = satu item
drawer, dan seluruh fitur yang **tidak** dilakukan tercatat di D10.

---

## 8. Kendala

**Pasti akan dihadapi (dan penanganannya):**

| Kendala | Penanganan |
|---|---|
| Kotlin tidak bisa dikompilasi di sandbox (tanpa JDK/SDK) | CI = hakim kompilasi (aturan rumah); guard lexikal + kotlin_sanity di `check.sh` menangkap kelas error umum lebih dulu; iterasi cepat push→CI |
| Tidak ada device di sisi agent | UAT user = gerbang terakhir; checklist UAT rinci di release notes; semua langkah punya breadcrumb yang bisa disalin |
| Rilis v1.0.22 butuh dispatch user + environment production | Disiapkan di akhir (workflow v1.0.22 hardened + mirror); dispatch & UAT & publish = keputusan user, bukan agent |
| Konfirmasi verbatim UAT v1.0.21 diperlukan sebelum baris DEVICE VERIFIED ditulis | **Sudah diterima user 2026-08-24** ("all passed safe and sound") — label DEVICE VERIFIED boleh ditulis di Fase 0 |

**Kemungkinan dihadapi (dan penanganannya):**

| Kendala (peluang) | Penanganan |
|---|---|
| Rate-limit GitHub API / jaringan HP putus (sedang) | Cache 24 jam; cek hanya user-initiated + 1x/start (60 req/jam/IP unauthenticated — terkonfirmasi docs); gagal = pesan jujur + retry manual, tidak auto-loop; FGS (D11) membuat unduhan tahan user beralih app, tapi tetap butuh jaringan |
| Detail foreground service: `foregroundServiceType`/permission manifest API 34, prompt `POST_NOTIFICATIONS`, handling background XOS (sedang) | D11 sudah diresearch terhadap docs resmi (URL §10): type `dataSync` + `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`, `ServiceCompat.startForeground` + tangkap exception, prompt kontekstual sekali (bila ditolak: FGS tetap jalan, notifikasi di Task Manager); limit 6 jam TIDAK berlaku di targetSdk 34 (hanya utk app targeting 15+); protokol 2x-CI-merah di titik sama → revisi desain, bukan dipaksa |
| APK di cacheDir terhapus OS (storage rendah) atau user "clear cache" (sedang) | D4/D5.6: cache = best-effort (verified docs); cek keberadaan file sebelum install & saat proses receipt; hilang → balik `AVAILABLE` + unduh ulang, pesan jujur; app juga membersihkan APK lama sendiri |
| Variasi ROM/OEM (Infinix XOS): dialog izin "sumber tidak dikenal" beda-beda, langkah install system bervariasi, battery killer (sedang) | Alur D5 sudah memperhitungkan "Android lanjut otomatis **atau** meminta user action" (bukan asumsi satu jalur); FGS = mitigasi terkuat utk battery killer; breadcrumb mencatat jalur mana yang terjadi; tidak menjanjikan silent 100% (D10) |
| Detail manifest/`FileProvider` bikin CI merah (rendah-sedang) | Iterasi cepat CI; guard manifest; kalau 2x CI merah di titik sama → perbaiki desain bagian itu, bukan dipaksa (protokol 2-UAT-gagal) |
| Sesi agent paralel (preseden `01a0336a`) menyentuh file yang sama (rendah) | Aturan §7: satu writer per file; koordinasi via dokumen repo; kalau bentrok terdeteksi → henti, laporkan, jangan merge paksa |
| `releases/latest` berubah perilaku / rilis dibuat keliru (rendah) | Semantik resmi (verified 2026-08-25): endpoint tak pernah mengembalikan draft/prerelease; "latest" diurut by `created_at` commit (D2). Guard parse tag + compare numerik D3 → worst case "up to date", tak pernah downgrade; invarian: rilis di-cut dari main tip; UI menampilkan versi yang akan dipasang |

---

## 9. Confidence (angka, aturan #1)

- Fase 0 selesai tanpa insiden: **~95%** (docs + guard kecil; prasyarat UAT sudah
  diterima user; risiko = ketidaksinkronan label antar 4 docs — mitigasi: satu
  commit, review silang).
- Fase 1 sesuai vonis: **~99%** (semua bukti sudah dieksekusi; hanya operasi `gh`).
- Fase 2 kode sampai CI hijau: **~80%** (FGS ter-research, tapi tetap permukaan
  baru: FileProvider/manifest detail; mitigasi = commit kecil + guard awal).
- Fase 2 sukses di device (update in-place + tombol berfungsi + FGS lanjut saat
  app di-switch) setelah UAT: **~75%** (risiko utama sekarang = perilaku
  battery-killer XOS — hanya bisa diketahui di device; mitigasi = breadcrumb +
  notifikasi + jalur manual tetap hidup).
- Urutan v1.0.21→v1.0.22 updater→v1.0.23 spike: **~95%** (roadmap resmi repo
  sendiri sudah memutuskan ini — agent hanya mengeksekusi, bukan menciptakan).

---

## 10. Kebijakan pencarian sumber & context7

**Setuju** mencari di situs/sumber relevan selama pekerjaan — dengan batasan:
- Yang memang butuh sumber eksternal: (a) dokumentasi resmi Android
  (`REQUEST_INSTALL_PACKAGES`, `FileProvider`, `ACTION_VIEW` install flow,
  `foregroundServiceType`/FGS restriction per API level, `POST_NOTIFICATIONS`)
  untuk RFC D5/D11; (b) referensi GitHub Releases API (rate-limit, field aset)
  untuk D2; (c) dokumentasi Chaquopy (bundling dependensi, `pip` section) untuk
  RFC spike; (d) dokumentasi resmi jedi/parso/pyflakes/mccabe/rope untuk kontrak
  spike (bug McCabe terjadi persis karena API ditebak, bukan dibaca — pelajaran).
- Semua klaim dari sumber eksternal diberi **URL** di dokumen (aturan #1).
- **MCP context7:** dipakai mendalam bila tersedia di sesi ini. **Jujur:** tool
  context7 belum terlihat di toolset agent saat ini (yang ada: `web_search` +
  `fetch_page`).
- **Status pemakaian Context7 (jujur, 2026-08-24/25):** user menyerahkan API key via
  chat. Key **TIDAK TERPAKAI**, karena: (a) tool `fetch_page` tidak bisa
  menyertakan header `Authorization: Bearer <key>`; (b) egress network sandbox
  (bash/curl) ke `context7.com` diblok — terverifikasi langsung (handshake TLS
  diputus; hanya segelintir host seperti `api.github.com` yang terbuka).
  **Pemakaian aktual:** REST API Context7 v2 (`GET /api/v2/libs/search`,
  `GET /api/v2/context?libraryId=...&query=...&type=json`) via `fetch_page`
  **tanpa autentikasi** (rate-limit rendah — cukup utk seluruh query penelitian
  ini). Index Context7 yang terpakai: `/websites/developer_android` (5,1 juta
  token, updated 2026-07-06), `/github/docs` (2,1 juta token, updated 2026-08-24),
  `/davidhalter/jedi`, `/pycqa/flake8`, `/python-rope/rope`. **Tidak ada di index**
  (per 2026-08-25): chaquopy, pyflakes (standalone), parso → sumber resminya
  dipakai langsung. Kualitas search Context7 utk Android = campuran: bagus utk API
  reference snippets, tapi query utk halaman guide sering meleset — prose resmi
  diambil langsung dari developer.android.com (primary source).
  **Agar key terpakai benar:** aktifkan MCP context7 di konfigurasi Arena (server
  MCP memakai key secara native). Key wajib **dirotasi setelah pekerjaan selesai**
  (sudah masuk chat = dianggap terekspos).
- **Sumber research v1.0.22 (URL — masuk RFC):**
  - FGS overview: https://developer.android.com/develop/background-work/services/fgs
  - FGS types (definisi `dataSync`): https://developer.android.com/develop/background-work/services/fgs/service-types
  - FGS types required, Android 14 (pola `ServiceCompat.startForeground`, nuansa default-manifest): https://developer.android.com/about/versions/14/changes/fgs-types-required
  - Declare FGS (manifest + permission, target 34): https://developer.android.com/develop/background-work/services/fgs/declare
  - Launch FGS (`ServiceCompat.startForeground`, exception): https://developer.android.com/develop/background-work/services/fgs/launch
  - FGS timeout (6 jam = app targeting 15+ doang): https://developer.android.com/develop/background-work/services/fgs/timeout
  - `POST_NOTIFICATIONS` (pre-grant, perilaku bila ditolak): https://developer.android.com/develop/ui/compose/notifications/notification-permission
  - FileProvider: https://developer.android.com/reference/androidx/core/content/FileProvider (tag `<cache-path>` untuk `getCacheDir()`)
  - Persistensi `getCacheDir` (sistem boleh hapus saat storage rendah): https://developer.android.com/guide/topics/data/data-storage (bagian "Saving cache files")
  - Install flow (`canRequestPackageInstalls`, `ACTION_MANAGE_UNKNOWN_APP_SOURCES`, `ACTION_VIEW` + MIME `application/vnd.android.package-archive`): https://developer.android.com/reference/android/content/pm/PackageManager (bagian `canRequestPackageInstalls`)
  - GitHub Releases API (field `digest`, `size`, tanpa auth utk repo publik): https://docs.github.com/en/rest/releases/releases
  - Semantik `GET /releases/latest` (non-prerelease, non-draft, urut by created_at commit): https://docs.github.com/en/rest/releases/releases#the-latest-release
  - Rate limit REST API (60 req/jam/IP unauthenticated): https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api
- **Sumber research Fase 3 (URL):**
  - Chaquopy (blok `python.pip.install`): https://chaquo.com/chaquopy/doc/current/ (bagian "Installing Python dependencies")
  - jedi (Context7 `/davidhalter/jedi` → docs/docs/api.rst)
  - rope (Context7 `/python-rope/rope`)
  - mccabe (Context7 `/pycqa/flake8` → docs C901/`--max-complexity`; API low-level dari source)
  - pyflakes & parso: **belum ada di index Context7** — source resmi (github) dibaca saat Fase 3

---

## 11. Status pertanyaan & keputusan (update 2026-08-25 01:30 WIB)

**Sudah diputuskan / dijawab user:**

| # | Keputusan / jawaban |
|---|---|
| Label item drawer | **"Cek Update"** (keputusan eksplisit user) |
| Posisi baris | **Sesuai D7** — di bawah "About & Contribute" |
| Auto-check sunyi saat app start (cache 24 jam, tanpa popup) | **Setuju** |
| Download saat user beralih ke app lain (SMS/panggilan) | **Foreground service** — unduhan tetap jalan (D11, sudah diresearch thd docs resmi) |
| Bahasa copy UI | **Konsisten = English** (label drawer "Cek Update" tetap) |
| Konfirmasi UAT v1.0.21 (prasyarat Fase 0) | **DITERIMA user 2026-08-24**: "all passed safe and sound, sesuai yang kita harapkan" — update in-place di atas v1.0.20, sentinel utuh → label DEVICE VERIFIED boleh ditulis |
| Permintaan sweep mismatch (2026-08-25) | **Selesai** — hasil: konsistensi file OK (anchor 767→759 dikoreksi), semantik `releases/latest` masuk D2, cacheDir best-effort masuk D4/D5/§5.2/§8, `<cache-path>` masuk §5.3, riset awal Fase 3 masuk §6 |
| context7 | **Setuju** — key diserahkan user 2026-08-24 via chat. Status: **tidak terpakai** (toolset ini tidak bisa menempel header auth; egress sandbox ke context7.com blokir — detail §10); tidak di-echo; tidak masuk repo/artifact; **wajib dirotasi setelah pekerjaan selesai** |

**Tertutup (2026-08-25):**

1. **Approve final blueprint ini** (termasuk §5.2 UI/UX spec, D11 foreground
   service, dan semua koreksi hasil dua sweep research) — urutan Fase 0 → 1 → 2
   → 3 diparkir — **DITERIMA USER** ("Lanjut", 2026-08-25 01:34 WIB) →
   eksekusi dimulai dari Fase 0.

Eksekusi ber-gate: tiap fase berhenti sampai gate hijaunya terpenuhi
(suite lokal + mutasi merah + CI + laporan ke user), sesuai §3–§5.
