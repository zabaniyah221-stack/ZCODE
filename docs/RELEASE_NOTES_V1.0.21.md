# ZCODE v1.0.21 — Data Safety & Reliability Hotfix

> Candidate notes. This version is **not released** until the exact production
> draft passes update-in-place UAT on the target ARMv7 device.

## Fokus

v1.0.21 tidak menambahkan engine intelligence baru. Release ini memprioritaskan
workspace user, package environment, operasi yang dapat diamati, dan update
langsung dari production v1.0.20 tanpa uninstall.

## Workspace

- Settings Clear All sekarang selalu meminta konfirmasi dan menampilkan jumlah
  seluruh file `.py`, termasuk file yang tab-nya sudah ditutup.
- Clear All memindahkan file ke private transactional trash, bukan langsung
  menghapusnya.
- `Restore Last Deletion` memulihkan file tanpa menimpa file baru yang memiliki
  nama sama.
- Operasi Clear All yang terputus sebelum commit dipulihkan saat workspace
  dibuka lagi.
- Delete file aktif tidak lagi memanggil jalur flush yang dapat membuat file
  terhapus muncul kembali.
- Run dibatalkan dengan pesan jelas jika source terbaru gagal disimpan; ZCODE
  tidak lagi mencatat `SAVE_OK` atau mengeksekusi file disk yang stale.

## Install Modules

- Aktivasi memakai generation directory unik. Environment lama tidak dihapus
  sebelum `installed.json` baru berhasil commit.
- Copy package diverifikasi berdasarkan path, ukuran, dan SHA-256 sebelum
  aktivasi.
- Same-version reinstall tidak menimpa direktori aktif secara in-place.
- `installed.json` memakai Android AtomicFile dan recovery dijalankan pada
  startup sebelum reader package/Python.
- SQLite diperlakukan sebagai cache sekunder; kegagalan sinkronisasi cache tidak
  mengubah install yang sudah commit menjadi laporan rollback palsu.
- Analyze/install/uninstall dimiliki satu Activity-scoped operation owner.
  Menekan Back tidak membuat engine kedua atau memutus akses terhadap operasi.
- Uninstall memakai backend lock yang sama serta menonaktifkan pointer state
  sebelum cleanup direktori best-effort.
- Pesan rollback diperjelas menjadi: tidak ada perubahan environment aktif yang
  di-commit.

## Package integrity

- Pencarian offline wheel memperbaiki normalisasi `python-dateutil` /
  `python_dateutil` sesuai PEP 503.
- Dependency resolver menerapkan batas default 60 package unik.
- Ekstraksi wheel memiliki batas compressed size, uncompressed size, jumlah
  entry, panjang nama, duplicate entry, dan path traversal.
- METADATA Name/Version harus cocok dengan plan.
- WHEEL/Wheel-Version dan seluruh RECORD path, size, serta hash diverifikasi.

## Plugin source safety

- Hasil plugin async terikat ke document ID, revision, dan source snapshot.
  Hasil stale dibuang, bukan diterapkan ke tab lain.
- Rename Symbol tidak lagi mengganti string/comment dan menolak kasus yang belum
  dapat ditransformasi dengan aman.
- Type Hint Generator mempertahankan default expression dan struktur signature.
- Organize Imports masuk safe read-only mode sampai preview + transactional
  change-set tersedia; import Python dapat memiliki side effect dan tidak aman
  dihapus/diurutkan diam-diam.

## Runtime dan toolchain

- Batch timeout mematikan proses sebelum menunggu reader threads.
- RuntimeProbe tidak lagi meninggalkan private worker setelah fallback timeout.
- Status update package menggunakan ordering versi konservatif, bukan sekadar
  string tidak sama.
- Catalog generator menolak overwrite jika source-nya akan menurunkan shipped
  catalog/tested manifest.
- Official Gradle 8.5 wrapper, wrapper checksum, dan distribution checksum
  ditambahkan.
- CI menjalankan JVM unit tests melalui wrapper dan GitHub Actions dipin ke full
  commit SHA.
- Production keystore dibuat, dipakai, diverifikasi, dan dihancurkan dalam satu
  shell step sebelum artifact upload atau draft release.

## Kontrak update-in-place

Candidate wajib memenuhi:

```text
source APK      : ZCODE v1.0.20 / versionCode 23
candidate APK   : ZCODE v1.0.21 / versionCode 24
applicationId   : com.zaba.zcode (tetap)
production SHA  : 401392193b734263c8ecce93e12be1f7f307203afe4282dc2550094088f38bd2
uninstall       : TIDAK
clear app data  : TIDAK
```

Workspace open/closed files, preferences, package state, dan import package lama
harus tetap utuh setelah pemasangan langsung. Draft hanya boleh dipublikasikan
jika APK yang diuji adalah byte yang sama dengan asset draft.

## Status evidence

Saat notes ini ditulis:

```text
Source implementation       : IMPLEMENTED
Local Python/static gate     : LOCALLY VERIFIED
Previous-branch debug CI     : CI VERIFIED — run 32542213874 on c84d48e
compile-production-source    : SKIPPED on that run
  reason                     : job if only matches arena/v1020-production PRs
  compiler evidence          : already present via debug job
                               ./gradlew testDebugUnitTest assembleDebug
  live unskip                : BLOCKED — Arena GitHub App lacks workflows
                               permission to update .github/workflows/build.yml
  ready-to-upload source     : ci/pending/build.yml
Physical ARMv7 update        : NOT DEVICE VERIFIED
Production signed draft      : NOT CREATED
Public v1.0.21 release       : NOT RELEASED
```
