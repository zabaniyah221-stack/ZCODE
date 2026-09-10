# RFC v1.0.22 — Verified One-Tap Update

**Status:** APPROVED (keputusan desain D1–D11 disetujui user 2026-08-24/25;
lihat `RENCANA_KERJA_POST_V1021_2026_08_24.md` §11 untuk jejak keputusan).
**Basis:** `main` @ `2793c19` (v1.0.21 RELEASED). **Target rilis:** 1.0.22 /
versionCode 25 (single source `gradle.properties`).
**Prinsip:** upgrade in-place, data user tidak pernah disentuh oleh app —
satu-satunya operasi destruktif ada di tangan Android + user saat confirm
install. Versi lama tidak pernah dirusak oleh fitur ini.

---

## 1. Keputusan desain

### D1 — Pembagian integritas (jujur, bukan teater)

Yang **bisa** app lakukan di device:

1. cek release publik via GitHub API (unauthenticated, repo publik);
2. unduh APK (foreground service — D11);
3. hitung SHA-256 sambil streaming dan bandingkan dengan field `digest`
   (`sha256:...`) pada respons API GitHub (terverifikasi live di aset v1.0.21);
4. luncurkan installer system (dialog Android).

Yang **tidak bisa** app lakukan di device: menjalankan aapt/apksigner (tidak ada
toolchain di HP). Identitas package + signer dijaga **oleh Android saat
install**: signer beda → install ditolak system; versionCode turun →
`INSTALL_FAILED_DOWNGRADE`. UI dan dokumen menulis persis pembagian ini —
tidak akan ada klaim "verifikasi signer di HP".

### D2 — Sumber & rate-limit

- Endpoint: `GET /repos/muzape28-blip/ZCODE/releases/latest` (unauthenticated;
  rate limit 60 req/jam/IP — docs.github.com/rate-limits). Header
  `Accept: application/vnd.github+json`, timeout 10 detik.
- Semantik resmi endpoint (docs.github.com/releases#the-latest-release):
  mengembalikan **hanya** release non-prerelease & non-draft, diurutkan by
  `created_at` = tanggal **commit** yang dipakai rilis (bukan tanggal publish).
  Konsekuensi: (a) draft/prerelease tak pernah bocor ke app; (b) bila sebuah
  rilis dibuat dari commit lebih lama, `latest` bisa mengembalikan versi lama →
  D3 menolak menwarkan versi ≤ versi lokal (worst case "up to date", **never
  downgrade**). Invarian kerja: setiap rilis ZCODE di-cut dari main tip sehingga
  `created_at` monoton naik.
- Kapan cek terjadi: tap user "Cek Update" + **maks 1x silent saat app start**
  (tanpa popup) — hasil di-cache 24 jam di prefs (DataStore). **Tidak ada
  polling background.**
- Parse: ambil `tag_name`; tolak tag yang tidak cocok pola `v\d+\.\d+\.\d+`.

### D3 — Perbandingan versi

Parsing numerik per-segmen, bukan string (kelas bug yang sudah dimakamkan):
guard unit wajib membuktikan `1.0.10 > 1.0.9`. Tampilkan update hanya bila
versi remote > versi lokal (konservatif, pola v1.0.21).

### D4 — Download & penyimpanan

- Streaming ke `cacheDir` privat (`cacheDir/update/ZCODE-vX.apk`), dijalankan
  oleh foreground service (D11).
- Pola di-clone dari `PackageEngineV2.download()`
  (`core/packageengine/PackageEngineV2.kt`, fungsi `download()`) yang sudah
  terbukti di device: `HttpURLConnection`, buffer 64 KB, SHA-256 sambil
  streaming, progress emit di-throttle ≥256 KB (pelajaran ARMv7 "47 detik
  SENYAP" + "156 event" flood), cancel/gagal → hapus file parsial.
- Verifikasi: SHA-256 hasil vs field `digest` respons API (prefix `sha256:`
  dibuang). Mismatch → file **dihapus**, breadcrumb `UPDATE_VERIFY_FAIL`, user
  tidak boleh lanjut. File `.sha256` yang dipublikasikan **tidak perlu
  diunduh app** (tetap dipublikasikan untuk verifikasi manual).
- Guard ukuran: tolak bila field `size` API > 100 MB; guard storage mengikuti
  pola package engine (ruang bebas ≥ 1.5× ukuran, minimum 100 MB).
- Gagal jaringan → pesan jujur + retry manual (tidak auto-loop).
- **CacheDir = best-effort (docs resmi Android):** sistem boleh menghapus file
  cache saat storage rendah; user bisa "clear cache". APK di cache **bukan
  jaminan permanen** — desain wajib menangani hilangnya APK (D5.6 + §3 kasus
  batas), dan app membersihkan APK lamanya sendiri saat ada versi baru.

### D5 — Alur install (bebas-siul)

1. User tap **"Download & Update"** (persetujuan eksplisit — tanpa
   auto-download).
2. Setelah download + verify OK → app **flush semua draft workspace**:
   `WorkspaceViewModel.flushSaveSync(verifyAllDrafts = true)`
   (`WorkspaceViewModel.kt:503` — hook yang sama yang dipakai `MainActivity`
   sebelum process rebirth). **Flush gagal (return false) = install tidak
   diluncurkan (fail-closed).**
3. Tulis **update receipt** (`update/receipt.json`: versi lama→baru, SHA, path,
   timestamp, status `PENDING_INSTALL`) **setelah** flush sukses, atomik
   (tmp + rename).
4. Bila belum pernah, pandu user sekali ke izin `REQUEST_INSTALL_PACKAGES`:
   `packageManager.canRequestPackageInstalls()` (minSdk 26 → API 26+, tidak ada
   jalur fallback); jika false → `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`
   dengan `Uri.parse("package:$packageName")`. Dicatat breadcrumb.
5. Luncurkan installer system: `FileProvider` → `content://` + `ACTION_VIEW`
   + `setDataAndType(uri, "application/vnd.android.package-archive")` +
   `FLAG_GRANT_READ_URI_PERMISSION` + `FLAG_ACTIVITY_NEW_TASK` (MIME `.apk`
   diturunkan otomatis oleh FileProvider) → **user tap dialog system** →
   Android melakukan upgrade in-place (signer sama, data aman).
6. **Setelah restart** (APK baru), Diagnostics baca receipt:
   - versionCode naik → `UPDATE_INSTALLED from→to`;
   - receipt ada, versi tak berubah (user batal di dialog system) →
     `UPDATE_PENDING`;
   - receipt versi lama → `UPDATE_RECEIPT_STALE` (dibuang).
   **Saat memproses receipt READY/PENDING, cek keberadaan file APK dulu**
   (cache best-effort — D4): file hilang → state balik `AVAILABLE` (unduh
   ulang) + breadcrumb, **bukan** error.
- Kegagalan di langkah mana pun = breadcrumb `UPDATE_*_FAIL` dengan alasan.
- `REQUEST_INSTALL_PACKAGES` di-declare di manifest.

### D6 — Anti downgrade / reinstall sama

Hanya tawarkan bila versi remote lebih baru (D3). Backstop: Android menolak
versionCode turun (D1). Reinstall versi sama tidak pernah ditawarkan.

### D7 — UI

- Item drawer label **"Cek Update"** (keputusan eksplisit user) di bawah
  "About & Contribute" (posisi D7 dipertahankan). Ikon vektor `ZIcons` baru
  (bukan emoji — keputusan user 2026-08; 24 dp manual, tint mengikuti tema
  Retro/Dracula/Tokyo Night otomatis).
- State machine + dialog situasional: full spec di §3.
- Copy dialog = **English** (keputusan user: konsisten dengan label eksisting
  app). Dialog menampilkan detail jujur: versi target, ukuran, SHA-256 12
  digit pertama + tombol salin, "data tidak akan dihapus — upgrade in-place".

### D8 — Telemetri (breadcrumb, semua bisa disalin user)

`UPDATE_CHECK_BEGIN`, `UPDATE_CHECK_OK`, `UPDATE_CHECK_NEWER`,
`UPDATE_CHECK_SAME`, `UPDATE_CHECK_FAIL`, `UPDATE_DOWNLOAD_BEGIN`,
`UPDATE_DOWNLOAD_OK`, `UPDATE_DOWNLOAD_FAIL`, `UPDATE_DOWNLOAD_CANCELLED`,
`UPDATE_VERIFY_OK`, `UPDATE_VERIFY_FAIL`, `UPDATE_FLUSH_OK`, `UPDATE_FLUSH_FAIL`,
`UPDATE_RECEIPT_WRITTEN`, `UPDATE_INSTALL_LAUNCH`, `UPDATE_INSTALLED`,
`UPDATE_PENDING`, `UPDATE_RECEIPT_STALE`, `UPDATE_FGS_START`, `UPDATE_FGS_STOP`.

### D9 — Testing

- JVM unit test untuk bagian murni (parse versi numerik, SHA-256 + fixture,
  parse respons API dari fixture, receipt JSON, state machine) di
  `app/src/test/java/com/zaba/zcode/core/update/UpdateUnitTests.kt`.
- Guard lexikal baru di `test_zcode_kotlin_guards.py`: tidak ada
  credential/PAT string di jalur update; FileProvider MIME benar
  (`application/vnd.android.package-archive`); receipt HANYA ditulis setelah
  flush sukses (urutan di kode); perbandingan versi bukan string; breadcrumb
  `UPDATE_*` lengkap; manifest mendeklarasikan `foregroundServiceType` +
  permission FGS (D11).
- **Setiap guard diuji mutasi merah→hijau** (pola rumah).

### D10 — Non-goals

Tanpa silent/background install; tanpa auto-download; tanpa downgrade/reinstall
versi sama; tanpa PAT/credential di aplikasi; tanpa Play Store; **tanpa
dependensi library baru** (stack tidak memiliki HTTP library — jalur update
memakai `java.net.HttpURLConnection`, pola yang sudah terbukti di
`PackageEngineV2`, + `org.json` bawaan Android); tanpa redesign Workbench;
tanpa janji "100% silent di semua ROM" (perilaku OEM hanya bisa dibuktikan
UAT — keyakinan rendah, tidak dijanjikan).

### D11 — Foreground service download (keputusan user 2026-08-24)

Unduhan harus selamat saat user beralih ke app lain (SMS/panggilan telepon
mendesak) — keputusan eksplisit user.

- `UpdateDownloadService` di-start **hanya** dari tap user "Download &
  Update" (aman terhadap aturan bg-start FGS untuk target 31+).
- Service: `android:exported="false"`; dalam `onStartCommand` panggil
  **`ServiceCompat.startForeground(this, id, notification, FOREGROUND_SERVICE_
  TYPE_DATA_SYNC)`** (helper androidx yang direkomendasikan docs resmi A14;
  id ≠ 0) + tangkap `ForegroundServiceStartNotAllowedException` (API 31+).
  Nuansa resmi: bila type tidak di-pass saat pemanggilan, default ke nilai
  manifest — kita tetap pass eksplisit.
- Manifest (aditif): `<service android:foregroundServiceType="dataSync">` +
  permission `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`
  (install-time, tanpa prompt — aturan target 34: type wajib di-declare,
  `startForeground()` melempar `MissingForegroundServiceTypeException` jika
  tidak; sistem cek permission saat promosi → `SecurityException` jika kurang)
  + `POST_NOTIFICATIONS`.
- Type `dataSync` sesuai definisi resminya: "data transfer operations — data
  upload or download, backup-and-restore, import or export, fetch data, local
  file processing" (runtime prerequisite: none).
- Notifikasi **ongoing**, channel `update` (low importance): judul "ZCODE —
  downloading update", isi "12.3 / 34.7 MB (35%)", tap → buka app.
- `POST_NOTIFICATIONS` diminta **kontekstual** (sekali, saat user pertama
  kali tap "Download & Update"; target ≥33 memberi app kendali timing prompt).
  Bila ditolak: FGS tetap jalan; notifikasi FGS tetap terlihat di **Task
  Manager** (bukan notification drawer) — verified docs; breadcrumb mencatat,
  tidak memblokir (dites UAT).
- Owner tunggal = service (bukan ViewModel); UI subscribe via `StateFlow`
  in-process. Cancel dari dialog → `stopForeground` + `stopSelf` + hapus
  parsial. Selesai/gagal → `stopSelf`.
- **Batasan waktu jujur:** limit 6 jam/24 jam untuk FGS `dataSync` HANYA
  berlaku untuk app yang **targeting Android 15+** (device 15+;
  `Service.onTimeout` baru ada di API 35). ZCODE target 34 → **tidak ada
  limit waktu** di v1.0.22. Forward-looking: bila `targetSdk` kelak naik ke
  35, wajib implementasi `onTimeout` (dicatat di sini sebagai kewajiban).
- Risiko nyata di target 34 = handling background OEM (XOS battery killer) —
  FGS adalah mitigasi terkuat yang tersedia; perilaku persis hanya bisa dites
  UAT (dijaminkan apa adanya).

---

## 2. File yang disentuh

| # | File | Perubahan |
|---|---|---|
| 1 | `app/src/main/java/com/zaba/zcode/core/update/UpdateChecker.kt` | **BARU** — client GitHub API (`HttpURLConnection` + `org.json`, unauthenticated), parse tag, compare versi numerik, cache 24 jam; tanpa credential |
| 2 | `app/src/main/java/com/zaba/zcode/core/update/UpdateDownloader.kt` | **BARU** — logika download murni (dipanggil service): streaming ke `cacheDir`, buffer 64 KB, SHA-256 sambil jalan, progress throttle ≥256 KB, guard ukuran/storage, hapus parsial |
| 3 | `app/src/main/java/com/zaba/zcode/core/update/UpdateDownloadService.kt` | **BARU** — foreground service (D11): `ServiceCompat.startForeground` + notifikasi ongoing + progress, `StateFlow` in-process, cancel aman, `stopSelf` |
| 4 | `app/src/main/java/com/zaba/zcode/core/update/UpdateReceipt.kt` | **BARU** — tulis receipt atomik (hanya setelah flush sukses), baca saat start-up + cek keberadaan file APK (D5.6) |
| 5 | `app/src/main/java/com/zaba/zcode/core/update/UpdateInstaller.kt` | **BARU** — cek/pandu `REQUEST_INSTALL_PACKAGES`, luncurkan FileProvider intent |
| 6 | `app/src/main/java/com/zaba/zcode/UpdateViewModel.kt` | **BARU** — owner Activity-scoped (pola `PackageOperationViewModel`): state machine, mutex operasi, view atas `StateFlow` service, cancel aman saat Back |
| 7 | `app/src/main/AndroidManifest.xml` | Aditif: `FileProvider` + meta-data; `<service>` + `foregroundServiceType="dataSync"` + `exported="false"`; permission `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES` |
| 8 | `app/src/main/res/xml/file_paths.xml` | **BARU** — elemen `<cache-path>` (tag resmi untuk `getCacheDir()`) + authority `${applicationId}.fileprovider` |
| 9 | `app/src/main/java/com/zaba/zcode/ui/workbench/WorkbenchScreen.kt` | Item drawer baru + dialog (composable kecil — pelajaran PERF_PASS); `DrawerItem` di-extend parameter opsional (icon/suffix) — baris lama perilaku byte-identik |
| 10 | `app/src/main/java/com/zaba/zcode/ui/components/ZIcons.kt` | Ikon update vektor baru (aditif) |
| 11 | `app/src/main/java/com/zaba/zcode/core/diagnostics/Breadcrumb.kt` | Tipe event `UPDATE_*` (aditif) |
| 12 | `app/src/main/java/com/zaba/zcode/ZcodeApp.kt` | Buat notification channel `update` (idempotent) saat start-up; baca receipt → `UPDATE_INSTALLED/_PENDING` ke Diagnostics |
| 13 | `app/src/test/java/com/zaba/zcode/core/update/UpdateUnitTests.kt` | **BARU** — JVM unit test (bagian murni D9) |
| 14 | `test_zcode_kotlin_guards.py` | Guard keluarga update + uji mutasi (termasuk manifest FGS) |
| 15 | `gradle.properties` | Bump `zcode.versionName=1.0.22` / `zcode.versionCode=25` — **commit terakhir sebelum push** |
| 16 | `docs/RELEASE_NOTES_V1.0.22.md` | **BARU** — notes kandidat (juga `--notes-file` draft release nanti) |

**Tidak disentuh** (disengaja): `production.yml` (versi v1.0.22-nya dibuat
baru saat dispatch — pola hardened v1.0.21 + mirror `ci/` byte-identik +
guard full-SHA + guard pasangan 1.0.22/25), seluruh engine spike,
`editor-src`, katalog, `strings.xml` (copy UI = literal Kotlin English, pola
eksisting app).

## 3. UI/UX spec

**Tata letak (nol screen baru, nol redesign):** satu baris baru di drawer
(pola `DrawerItem`, `WorkbenchScreen.kt:1571`): ikon + label "Cek Update" +
suffix status 12sp di kanan (preseden: baris THEME di dalam kotak TOOLS).
Tap baris → **satu dialog** `AlertDialog` Material3 (harus muat landscape
Infinix ~360 dp: judul 1 baris + teks ≤2 baris + 2 tombol), drawer ditutup
(pola `closeDrawerThen`).

Suffix per state: `v1.0.21` (biasa) · `checking…` · `up to date` · `v1.0.22`
(primary) · `12.3/34.7 MB` · `ready` (primary) · `failed` (warna error —
preseden ProblemsBanner).

**State machine:**

```text
 app start ─► IDLE (auto-check sunyi bila cache >24 jam — D2)
   │ tap
   ▼
 CHECKING ─┬─► UPTODATE              (remote ≤ lokal, compare angka — D3)
           ├─► AVAILABLE ─[Download & Update]─► DOWNLOADING (FGS — D11, progress, Cancel)
           └─► FAILED ◄─────────────────────── retry ──────────────────────┘
                                        │ selesai + SHA-256 OK
                                        ▼
                              VERIFYING ─► FLUSHING (flushSaveSync — gagal = STOP)
                                        │
                                        ▼
 READY (receipt ditulis) ──[Install now]──► dialog SISTEM Android
      ▲                                        │
      │ (user batal di dialog system)   restart, versionCode naik
      ▼                                        ▼
PENDING (APK di cache — best-effort;  UPDATE_INSTALLED → baris IDLE versi baru
cek keberadaan file dulu; hilang →  + breadcrumb di Diagnostics
balik AVAILABLE, unduh ulang)
```

Jaminan loop: (1) tiap state = tepat satu aksi utama + satu jalan keluar
(Later/Cancel/Close) — tidak ada buntu; (2) gagal selalu retryable dari
state-nya; (3) idempotent — cek ulang saat READY tidak mengunduh ulang (cache
APK dicek terhadap `latest`; versi cache ≠ latest → cache dibuang, unduh
ulang); (4) tahan restart — tiap transisi ke-breadcrumb (D8) + receipt
persist; (5) tutup dialog ≠ batalkan (download jalan di FGS — D11).

**Copy dialog (English):**

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

**Kasus batas:**

| Kasus | Perilaku |
|---|---|
| Offline saat cek | FAILED "Network", retryable, app jalan normal |
| User beralih app / app di-kill saat unduh | FGS melanjutkan + notifikasi progress (keputusan user #3); OS/OEM membunuh FGS (battery killer XOS) = FAILED jujur + retry (dites UAT) |
| Batal di dialog system | `UPDATE_PENDING`; APK di cache (best-effort); hilang saat akan install lagi → balik `AVAILABLE`, unduh ulang |
| Cache dihapus OS / user "clear cache" saat READY/PENDING | Cek keberadaan file APK sebelum install/receipt (D5.6); hilang → balik `AVAILABLE` + pesan jujur "Package removed from cache", unduh ulang |
| Versi baru keluar saat APK lama masih di cache | Cek selalu vs `latest`; cache ≠ latest → cache dibuang, unduh ulang |
| Hash mismatch | Gagal total, file dibuang, pesan jujur, breadcrumb `UPDATE_VERIFY_FAIL` |
| Storage kurang | Ditolak **sebelum** unduh (guard D4), pesan jujur |
| `POST_NOTIFICATIONS` ditolak (API 33+) | FGS tetap jalan; notifikasi FGS tetap di Task Manager (bukan drawer) — verified docs; breadcrumb mencatat (dites UAT) |

## 4. Urutan commit (atomik, kecil — pola rumah)

1. `docs(v1022): RFC one-tap update`
2. `feat(update): checker (API+versi+cache)`
3. `feat(update): downloader (stream+sha+guard)`
4. `feat(update): foreground service + notifikasi`
5. `feat(update): receipt (atomik, post-flush)`
6. `feat(update): installer (FileProvider+izin)`
7. `feat(update): viewmodel + ui drawer + ikon`
8. `test(update): unit test + guard + mutasi`
9. `build(release): versi 1.0.22/25 + notes`

Tiap commit: `tools/check.sh` hijau. Kotlin tidak bisa dikompilasi di sandbox
— CI adalah hakim kompilasi (aturan rumah); guard lexikal + kotlin_sanity
menangkap kelas error umum lebih dulu.

## 5. Gate & alur rilis

```text
check.sh + suite penuh hijau (671+N)
→ kotlin sanity + npm supply-chain (check.sh)
→ push → CI (check/build) SUCCESS
→ user review diff
→ (saat dispatch) production workflow v1.0.22 dibuat (pola hardened v1.0.21,
  mirror ci/ byte-identik, guard full-SHA) + test guard pasangan 1.0.22/25
→ USER dispatch (workflow_dispatch + konfirmasi "Type BUILD-v1.0.22..." +
  environment production)
→ 1 run → 1 draft exact bytes
→ USER UAT di Infinix X6532C: update in-place v1.0.21→v1.0.22
  (sentinel: file .py terbuka+tertutup, settings, paket lama importable)
  + uji fitur tombol itu sendiri: cek update, download + FGS lanjut saat
  switch app, verify, flush, install, receipt
→ publish draft yang di-UAT (tanpa rebuild) → v1.0.22 RELEASED
```

**Checklist UAT (disertakan di RELEASE_NOTES_V1.0.22):**

1. Sentinel sebelum update: buka 1 file `.py` (isi unik A), 1 file tertutup
   (isi unik B), ubah 1 setting, pastikan 1 paket lama importable.
2. Tap "Cek Update" → state suffix + dialog benar untuk tiap state.
3. Tap "Download & Update" → notifikasi FGS muncul; **buka app lain (SMS/telepon)
   1 menit** → kembali: unduhan tetap jalan (progress konsisten).
4. Selesai → dialog READY → "Install now" → dialog system Android → update
   in-place (tanpa uninstall).
5. Setelah restart: versionCode 25, Diagnostics breadcrumb `UPDATE_INSTALLED
   from 24 to 25`, seluruh sentinel utuh, paket lama importable.
6. Jalur batal: ulangi update dari rilis berikutnya bila tersedia — batal di
   dialog system → `UPDATE_PENDING`, APK masih bisa dipasang ulang.
7. Jaringan dimatikan saat cek/download → FAILED jujur + Retry berfungsi.

**Kill-switch (pola Gerbong A):** tombol update aditif — bila anomali di
device, user tetap bisa update manual via GitHub (jalur lama tidak pernah
hilang). Toggle OFF = perilaku persis sebelum fitur.

## 6. Referensi (URL)

- FGS overview: https://developer.android.com/develop/background-work/services/fgs
- FGS types (definisi `dataSync`): https://developer.android.com/develop/background-work/services/fgs/service-types
- FGS types required, Android 14 (`ServiceCompat.startForeground`): https://developer.android.com/about/versions/14/changes/fgs-types-required
- Declare FGS (manifest + permission, target 34): https://developer.android.com/develop/background-work/services/fgs/declare
- Launch FGS (exception): https://developer.android.com/develop/background-work/services/fgs/launch
- FGS timeout (6 jam = app targeting 15+ doang): https://developer.android.com/develop/background-work/services/fgs/timeout
- `POST_NOTIFICATIONS`: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- FileProvider (tag `<cache-path>`): https://developer.android.com/reference/androidx/core/content/FileProvider
- Persistensi `getCacheDir` (best-effort): https://developer.android.com/guide/topics/data/data-storage
- Install flow (`canRequestPackageInstalls` dkk.): https://developer.android.com/reference/android/content/pm/PackageManager
- GitHub Releases API (field `digest`, `size`, unauthenticated): https://docs.github.com/en/rest/releases/releases
- Semantik `/releases/latest`: https://docs.github.com/en/rest/releases/releases#the-latest-release
- Rate limit REST (60 req/jam/IP unauthenticated): https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api

---

## Errata implementasi (2026-09-08, sesi arena/v1023-intelligence)

1. **D8 — nama breadcrumb:** daftar kontrak menyebut `UPDATE_CHECK_SAME`;
   implementasi memakai `UPDATE_CHECK_OK` untuk kondisi up-to-date. Kontrak
   telemetri aktual: `UPDATE_CHECK_BEGIN / _OK / _NEWER / _FAIL`.
2. **D2 — bug cache 24 jam:** `readCache()` membaca field statis `cacheFile`
   yang hanya diinisialisasi oleh `writeCache()`, sehingga pada process baru
   cache selalu terbaca kosong dan auto-check app start selalu menyentuh
   jaringan (telemetri 2026-09-08: 0 cache-hit dari 15 auto-check). Dampak
   benign — 1 request API ekstra per app start. Fix direncanakan di v1.0.23
   (`RENCANA_KERJA_V1023_2026_09_08.md` §5 commit 1). Bukti:
   `docs/UAT_UPDATER_CHECKPATH_2026_09_08.md` §2.
