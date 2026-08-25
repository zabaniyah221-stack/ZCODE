# ZCODE v1.0.22 — Verified One-Tap Update

> Candidate notes. This version is **not released** until the exact
> production draft passes update-in-place UAT on the target ARMv7 device.
> Desain & keputusan: `docs/RFC_V1022_ONE_TAP_UPDATE.md` (D1–D11 disetujui
> user 2026-08-24/25).

## Fokus

v1.0.22 menambahkan **satu fitur**: jalur update one-tap dari dalam app —
user tanpa PC tidak lagi membuka browser → cari release → unduh 34 MB →
verify hash → install. Semuanya lewat satu baris drawer **"Cek Update"**
(dibawah "About & Contribute", keputusan user) dengan dialog situasional.

## Cara kerja (ringkas — detail di RFC)

1. **Cek** — `GET /repos/muzape28-blip/ZCODE/releases/latest`
   (unauthenticated; endpoint ini tak pernah mengembalikan draft/prerelease;
   tag diparse ketat `vMAJOR.MINOR.PATCH`; versi dibandingkan NUMERIK per
   segmen — `1.0.10 > 1.0.9`; tampilkan hanya bila remote lebih baru →
   never downgrade). Auto-check sunyi maks 1x saat app start (cache 24 jam);
   tap user = cek fresh.
2. **Unduh** — **foreground service** `dataSync` (keputusan user: unduhan
   tetap jalan saat user beralih ke app lain — SMS/panggilan telepon),
   notifikasi progress ongoing "12.3 / 34.7 MB (35%)", pola download sama
   dengan `PackageEngineV2` yang sudah terbukti di device (buffer 64 KB,
   progress throttle ≥256 KB, cancel → hapus parsial).
3. **Verifikasi** — SHA-256 dihitung sambil streaming dan dibandingkan
   dengan field `digest` respons API GitHub. Mismatch = file dibuang, gagal
   total, breadcrumb `UPDATE_VERIFY_FAIL`.
4. **Amankan workspace** — SEMUA draft di-flush sebelum instalasi
   (`flushSaveSync(verifyAllDrafts=true)`, hook yang sama dengan process
   rebirth). Flush gagal = instalasi TIDAK diluncurkan (fail-closed).
5. **Receipt** — status ditulis atomik HANYA setelah flush sukses; dibaca
   lagi saat start-up berikutnya.
6. **Install** — dialog **system Android** via FileProvider `content://`
   (izin "Install unknown apps" dipandu sekali bila belum ada). Setelah
   dialog diluncurkan, **Android yang pegang**: user sendiri yang memilih
   install. Upgrade in-place (signer sama, data aman) — app tidak dan tidak
   bisa memverifikasi signer di HP (jujur: pembagian integritas di RFC D1).
7. **Setelah restart** — Diagnostics mencatat `UPDATE_INSTALLED` /
   `UPDATE_PENDING` (batal di dialog system) / `UPDATE_RECEIPT_STALE`.
   APK di cache bisa dihapus sistem (storage rendah) atau user
   ("clear cache") — cacheDir adalah best-effort: bila hilang, state balik
   ke AVAILABLE (unduh ulang), bukan error.

## Pembagian integritas (apa yang app lakukan vs tidak)

```text
App di device         : cek release publik, unduh, hitung SHA-256 vs digest
                        API, flush workspace, luncurkan installer system.
Android saat install  : identitas package + signer (signer beda = ditolak;
                        versionCode turun = ditolak), dialog, migrasi data.
TIDAK bisa di device  : menjalankan apksigner/aapt (tidak ada toolchain).
```

## Tidak termasuk (non-goals, RFC D10)

Tanpa silent/background install; tanpa auto-download (setiap unduhan dari
tap user); tanpa downgrade/reinstall versi sama; tanpa PAT/credential di
aplikasi; tanpa Play Store; **tanpa dependensi library baru**
(`HttpURLConnection` + `org.json` bawaan Android); tanpa redesign Workbench;
tanpa janji "100% silent di semua ROM".

## Kontrak update-in-place

```text
source APK      : ZCODE v1.0.21 / versionCode 24
candidate APK   : ZCODE v1.0.22 / versionCode 25
applicationId   : com.zaba.zcode (tetap)
production SHA  : 401392193b734263c8ecce93e12be1f7f307203afe4282dc2550094088f38bd2
uninstall       : TIDAK
clear app data  : TIDAK
```

Workspace open/closed files, preferences, package state, dan import package
lama harus tetap utuh setelah pemasangan langsung. Draft hanya boleh
dipublikasikan jika APK yang diuji adalah byte yang sama dengan asset draft.

## Foreground service — batasan jujur (RFC D11)

- Target 34: limit 6 jam FGS `dataSync` **tidak berlaku** (hanya untuk app
  targeting Android 15+). Bila kelak `targetSdk` naik ke 35, wajib
  implementasi `Service.onTimeout`.
- Risiko nyata di target 34 = handling background OEM (XOS battery killer):
  FGS adalah mitigasi terkuat; perilaku persis hanya bisa dibuktikan UAT.
- `POST_NOTIFICATIONS` diminta kontekstual sekali (saat "Download &
  Update"); bila ditolak: FGS tetap jalan, notifikasi FGS tetap di Task
  Manager (bukan drawer).

## Checklist UAT (perangkat: INFINIX X6532C, ARMv7, Android 14)

1. **Sentinel sebelum update**: buka 1 file `.py` (isi unik A), 1 file
   tertutup (isi unik B), ubah 1 setting, pastikan 1 paket lama importable.
2. Tap **"Cek Update"** → suffix drawer + dialog benar untuk tiap state.
3. Tap **"Download & Update"** → notifikasi FGS muncul; **buka app lain
   (SMS/telepon) ±1 menit** → kembali: unduhan tetap jalan (progress
   konsisten).
4. Selesai → dialog READY → **"Install now"** → dialog system Android →
   update in-place (tanpa uninstall).
5. Setelah restart: versionCode 25, Diagnostics breadcrumb
   `UPDATE_INSTALLED from 24 to 25`, seluruh sentinel utuh, paket lama
   importable.
6. Jalur batal: batal di dialog system → kembali ke app → state PENDING,
   "Install now" memasang ulang dari cache.
7. Jaringan dimatikan saat cek/unduh → FAILED jujur + Retry berfungsi.
8. Kill app (swipe dari recent) saat unduh → FGS melanjutkan; bila OS/OEM
   membunuh FGS → FAILED jujur + retry (dokumentasikan apa yang terjadi).

## Status evidence

Saat notes ini ditulis (fase implementasi, sebelum production):

```text
Feature implementation      : IMPLEMENTED LOCALLY (9 commit, RFC D1–D11)
Local Python/static gate    : LOCALLY VERIFIED (tools/check.sh hijau)
JVM unit tests (update)     : IMPLEMENTED — CI execution pending
Kotlin lexical guards       : IMPLEMENTED + mutasi merah terbukti (3 kelas)
Kotlin compile              : CI = hakim kompilasi (tidak ada JDK/SDK di sandbox)
Production signed draft     : NOT CREATED
Physical ARMv7 update       : NOT DEVICE VERIFIED
Public v1.0.22 release      : NOT RELEASED
```
