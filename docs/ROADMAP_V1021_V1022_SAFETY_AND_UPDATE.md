# Roadmap ZCODE v1.0.21 → v1.0.22

## Data Safety, GPL Compliance, dan Verified One-Tap Update

**Status dokumen:** `APPROVED / IMPLEMENTATION IN PROGRESS LOCALLY`
**Tanggal:** 2026-08-22
**Branch repair lokal:** `arena/v1021-pr28-repair`
**Belum ada push, perubahan PR remote, merge, production signing, atau release.**

Dokumen ini adalah note pengambilan keputusan sebelum agent mengambil kendali.
Urutannya sengaja memisahkan hotfix keselamatan data dari fitur updater agar
satu fitur nyaman tidak menutupi bug yang dapat merusak workspace atau package
environment.

---

## 1. Pemeriksaan aturan inti

### Aturan #1 — Honest about anything

**Rencana ini mematuhi aturan tersebut jika seluruh status tetap dipisahkan:**

```text
DESIGNED
IMPLEMENTED
LOCALLY VERIFIED
CI VERIFIED
ARMV7-IMPORT-VERIFIED
BROWSER-HARNESS VERIFIED
FULL-EMULATOR VERIFIED
DEVICE VERIFIED
PRODUCTION SIGNED
RELEASED
REGRESSION FOUND
```

Kenyataan saat note ini ditulis:

- ZCODE v1.0.20: `RELEASED + DEVICE VERIFIED` untuk artifact production yang
  diuji, dengan SHA-256 dan signer publik yang telah dicocokkan.
- Update-in-place v1.0.20 → v1.0.21: **belum DEVICE VERIFIED**.
- PR #27: kandidat draft, **belum aman di-merge**.
- Tombol update sidebar: **baru DESIGNED**, belum IMPLEMENTED.
- Silent update tanpa kemungkinan campur tangan Android: **tidak dijanjikan**.
- Context7: disetujui sebagai alat riset, tetapi **belum digunakan dalam sesi
  penyusunan note ini karena tool Context7 tidak tersedia langsung pada toolset
  sesi ini**. Official docs/search tetap dapat digunakan; jika Context7 tersedia
  pada sesi implementasi, hasilnya harus dicocokkan lagi ke sumber primer.

### Aturan #2 — Be meticulous in everything

Rencana ini mematuhi aturan tersebut hanya jika:

1. bug data-safety memiliki deterministic regression guard;
2. guard dibuktikan lewat mutasi `bug kembali → RED`, lalu `fix kembali → GREEN`;
3. source-pattern guard Kotlin/JS membuang komentar sebelum mencocokkan pola;
4. callback async membawa identity/revision dan stale result ditolak;
5. pre-commit rollback dipisahkan tegas dari post-commit best-effort work;
6. satu file tidak pernah ditulis paralel oleh dua tool/agent;
7. workflow aktif dan mirror diperiksa byte-for-byte;
8. APK updater diverifikasi package, versionCode, hash, signer, dan ukuran
   sebelum install session dikomit;
9. semua error penting masuk Diagnostics yang dapat disalin tanpa PC;
10. CI, emulator, device, production signing, dan release tidak dianggap sama.

Jika salah satu pagar di atas dihilangkan untuk mengejar kecepatan, pekerjaan
ini tidak lagi memenuhi aturan #2.

### Prinsip produk — build for the user, not for ego

**Kesimpulan:** pekerjaan ini layak karena manfaat user-nya langsung, bukan
karena terlihat canggih.

Manfaat nyata:

- mencegah file yang sudah dihapus muncul kembali akibat save async lama;
- mencegah `installed.json` menunjuk generation yang sudah terhapus;
- menjaga workspace, settings, dan package state saat APK diperbarui;
- mengurangi ritual browser → download → file manager → cari APK;
- memberi status download/verifikasi/install yang terlihat dan dapat disalin;
- membantu user tanpa PC tetap memeriksa apa yang terjadi.

Hal yang **akan menjadi ego/gengsi** dan karena itu ditolak:

- memasukkan updater ke v1.0.21 hanya agar release terlihat lebih besar;
- mengiklankan “silent auto-update” ketika Android/ROM dapat meminta approval;
- menambah dependency networking hanya untuk membungkus API yang bisa ditangani
  platform/existing stack;
- membuat background polling agresif demi badge yang jarang dibutuhkan;
- membangun self-updater rumit sebelum update manual terbukti mempertahankan data;
- mencampur intelligence engine/Jedi/Rope/formatter ke hotfix ini;
- mengejar jumlah fitur sambil membiarkan dua bug data-safety terbuka.

**Putusan produk:** keselamatan data lebih penting daripada gengsi updater.
Updater baru layak setelah jalur update manual production terbukti aman.

---

## 2. Sasaran dan non-goals

### Sasaran dekat — v1.0.21

Menerbitkan satu hotfix yang fokus pada:

1. keselamatan workspace;
2. transaksi package yang benar di commit boundary;
3. kompatibilitas wheel verification yang sesuai standar;
4. GPLv3 Option B dan provenance yang jujur;
5. workflow production-source yang benar-benar berjalan di PR;
6. update-in-place production-signed dari v1.0.20 tanpa uninstall/clear-data;
7. exact tested bytes dipublikasikan tanpa rebuild.

### Sasaran berikutnya — v1.0.22

Menyediakan **auto-check + verified one-tap update** dari sidebar:

```text
cek latest published GitHub release
→ sidebar berubah state bila versionCode lebih baru
→ user memilih Download & Update
→ download ke private/cache storage
→ verifikasi package/version/hash/signer
→ flush workspace dan tulis update receipt
→ commit PackageInstaller session
→ Android lanjut otomatis atau meminta user action
→ setelah restart, Diagnostics mencatat hasil
```

### Non-goals

- Tidak ada updater di v1.0.21.
- Tidak ada silent/background install yang mencoba melewati keputusan Android.
- Tidak ada auto-download tanpa persetujuan user.
- Tidak ada downgrade atau reinstall versi sama.
- Tidak ada PAT/GitHub credential di aplikasi.
- Tidak ada Play Store integration pada scope ini.
- Tidak ada dependency baru tanpa diskusi kedua.
- Tidak ada Jedi, Parso, Pyflakes, Rope, Flake8/Bugbear, Bandit, Vulture,
  Radon, Black, atau isort dalam v1.0.21.
- Tidak ada redesign Workbench/Explorer/Git/plugin besar; pekerjaan itu tetap
  diparkir dan tidak dipercepat.

---

## 3. Roadmap dan alur eksekusi

## Fase 0 — Approval dan custody

**Status:** APPROVED oleh user; implementasi lokal dimulai dengan boundary push/merge/signing tetap tertutup.

Sebelum approval, agent hanya boleh:

- membaca repo/history/docs;
- menyusun note/rencana;
- menjelaskan trade-off;
- menanyakan keputusan yang benar-benar diperlukan.

Sebelum approval, agent tidak boleh:

- mengedit kode produksi;
- mengubah PR #26/#27;
- membuat/push branch;
- mengubah workflow GitHub;
- meminta material production signing;
- merge, dispatch production, atau release.

Setelah approval, cek ulang branch, commit, dirty state, remote, credential, dan
status PR. Snapshot dapat menghapus config remote, executable bit, dan auth.

## Fase 1 — Rapikan topology PR

1. Tutup draft PR #26 sebagai **superseded by PR #27**.
2. Pertahankan PR #27 sebagai satu-satunya kandidat v1.0.21.
3. PR #27 tetap `Draft`; jangan merge sebelum semua gate selesai.
4. Buat branch kerja lokal dari exact head PR #27.
5. Integrasikan evidence v1.0.20 dari commit lokal `431f7bc` secara terkontrol,
   bukan menimpa dokumen PR #27 secara membabi buta.
6. Audit diff dan mode file sebelum mulai perbaikan.

Jika push dibutuhkan, user harus memberi authorization dan credential sementara
yang sesuai. Agent tidak menganggap token lama masih valid. Secret production
keystore/password tidak pernah diminta melalui chat.

## Fase 2 — Fix commit boundary package transaction

Masalah:

```text
atomic installed.json commit
→ cleanup/onLog melempar exception
→ catch pre-commit menghapus promoted finalDir
→ installed.json menunjuk directory yang hilang
```

Desain fix:

```text
PRE-COMMIT
prepare → copy → verify → promote unique generation
→ kegagalan boleh rollback generation baru

COMMIT BOUNDARY
atomic write installed.json sukses

POST-COMMIT
cleanup generation lama → log → journal → tx cleanup
→ semuanya best-effort
→ tidak boleh masuk rollback pre-commit
→ active generation baru tidak pernah dihapus
```

Acceptance criteria:

- callback `onLog` yang melempar setelah commit tidak menghapus active generation;
- `installed.json` selalu menunjuk directory yang ada;
- result tidak berbohong bahwa old environment preserved setelah commit baru
  sudah authoritative;
- cleanup gagal hanya meninggalkan storage lama, bukan environment rusak;
- journal/Diagnostics membedakan activation success dan post-commit warning.

Mutation proof:

- kembalikan satu catch besar yang menghapus `promoted.finalDir` → test RED;
- pulihkan commit boundary → test GREEN.

## Fase 3 — Fix stale inactive-file save resurrection

Masalah:

```text
save file inactive terantre
→ Clear/Delete/Rename mengubah workspace secara transactional
→ save lama baru berjalan
→ file yang dihapus hidup kembali
```

Desain fix:

- capture `documentId + revision + code`;
- acquire `workspaceMutationLock` sebelum disk write;
- setelah lock didapat, recheck file masih valid/open;
- recheck identity dan revision masih sama;
- recheck draft yang aktif untuk identity tersebut masih sama;
- stale save menjadi no-op;
- Clear/Delete/Rename membatalkan atau membuat write lama invalid secara
  deterministik.

Acceptance criteria:

- queued save tidak dapat menghidupkan file setelah Clear All;
- queued save tidak dapat menulis nama lama setelah Rename;
- queued save tidak dapat menghidupkan file setelah Delete;
- callback lama dari document yang sudah ditutup diabaikan;
- save aktif normal tetap berjalan;
- tidak memakai `sleep()` untuk membuktikan race.

Test memakai barrier/fake writer:

```text
queue save → tahan tepat sebelum write
→ clear/delete/rename → release barrier
→ file lama tidak ada
```

Mutation proof:

- hilangkan lock/revision recheck → intended test RED;
- pulihkan → GREEN.

## Fase 4 — Verifier signed-wheel RECORD

Audit terhadap PEP 427 untuk wheel yang memiliki:

```text
RECORD.jws
RECORD.p7s
```

Kedua signature file tersebut dapat hadir tanpa dicatat sebagai baris normal di
`RECORD`. Fix hanya boleh membuat exception yang dinyatakan standar; **jangan
melonggarkan arbitrary unlisted file, path traversal, hash, atau size check**.

Gate:

- fixture signed wheel yang valid diterima;
- file unlisted biasa tetap ditolak;
- hash/size mismatch tetap ditolak;
- path traversal tetap ditolak;
- mutation menghapus exception signature → valid fixture RED;
- mutation menerima arbitrary unlisted file → security test RED.

Acuan: https://peps.python.org/pep-0427/

## Fase 5 — Licensing Option B

Keputusan yang sudah dikunci user:

- jangan klaim “sole copyright holder”;
- jangan klaim ZABACODE-derived portions direlicense MIT;
- pertahankan provenance dan attribution contributor;
- penuhi GPLv3 untuk bagian turunan/distribusi gabungan secara konservatif;
- pertahankan notice MIT untuk bagian yang memang ditulis independen di bawah MIT;
- tambahkan GPLv3 full text dan NOTICE/source/change information;
- tampilkan informasi lisensi di About dan artifact/APK;
- tambahkan guard agar root kembali menjadi MIT-only atau sole-holder claim
  menghasilkan test failure.

Ini guidance engineering/compliance, bukan pengganti nasihat pengacara.

Acuan:

- https://github.com/muzape28-blip/ZABACODE/blob/main/LICENSE
- https://www.gnu.org/licenses/gpl-faq.html#WhatDoesCompatMean
- https://www.gnu.org/licenses/gpl-faq.html#MereAggregation
- https://www.gnu.org/licenses/gpl-faq.html#DoesTheGPLAllowMoney

## Fase 6 — Workflow dan evidence

1. Terapkan branch-agnostic `compile-production-source` ke workflow aktif dan
   mirror, bukan hanya `ci/pending/build.yml`.
2. Hapus/perbaiki instruksi pending yang menunjuk branch PR #26.
3. Sinkronkan `.github/workflows/build.yml` dan `ci/workflows/build.yml`.
4. Rekonsiliasi evidence v1.0.20:

```text
v1.0.20 = RELEASED + DEVICE VERIFIED + exact-byte public audit
v1.0.21 = candidate; belum signed/device/released
```

5. Perbarui README/PRD yang masih menyebut snapshot versi lama tanpa mengubah
   sejarah secara palsu.

PR CI wajib menunjukkan:

```text
check                     : SUCCESS
build/testDebugUnitTest    : SUCCESS
assembleDebug              : SUCCESS
compile-production-source : SUCCESS, bukan SKIPPED
```

## Fase 7 — Local, CI, dan final co-lead review

Local gates minimum:

```text
bash tools/check.sh
python3 tools/kotlin_sanity_check.py
git diff --check
workflow mirror equality
Gradle wrapper checksum
npm/editor supply-chain check
secret/private-key/token scan
file-mode audit
```

Untuk setiap bug nyata:

```text
fix GREEN
→ sengaja reintroduce bug
→ intended test RED karena alasan yang benar
→ restore fix
→ focused + full gate GREEN
```

Setelah CI hijau, agent menyajikan diff, evidence, sisa risiko, dan rollback.
**Merge tetap menunggu review/approval user.**

## Fase 8 — Production v1.0.21 dan update-in-place

Setelah merge yang disetujui:

1. Dispatch satu production workflow dengan confirmation phrase yang disepakati.
2. Build satu APK production-signed.
3. Workflow memverifikasi expected signer.
4. Buat private draft; jangan publish.
5. Pada ZCODE v1.0.20 yang masih terpasang, buat sentinel:
   - open `.py` dengan isi unik;
   - closed `.py` dengan isi unik;
   - settings unik;
   - package state/import yang dapat diperiksa.
6. Download exact draft APK v1.0.21.
7. **Jangan uninstall. Jangan clear app data.** Tap APK dan pilih Update.
8. Verifikasi seluruh sentinel, runtime, package, Diagnostics, versionCode, crash.
9. Cocokkan SHA-256 bytes yang diuji.
10. Bila PASS, publish exact draft bytes tanpa rebuild.
11. Bila FAIL, draft tetap tidak dipublish dan v1.0.20 tetap menjadi release aman.

## Fase 9 — RFC updater v1.0.22

Baru dimulai setelah update-in-place v1.0.21 berstatus `DEVICE VERIFIED`.

Desain state machine awal:

```text
IDLE
→ CHECKING
→ UP_TO_DATE | UPDATE_AVAILABLE(metadata)
→ DOWNLOADING(bytes/total)
→ VERIFYING
→ READY_TO_INSTALL
→ FLUSHING_WORKSPACE
→ COMMITTING_INSTALL_SESSION
→ WAITING_FOR_ANDROID_USER_ACTION | INSTALLING
→ SUCCEEDED | FAILED | CANCELLED
```

Identity minimum:

```text
releaseId
assetId
versionCode
versionName
asset URL
expected size
expected SHA-256
expected package
expected signer certificate SHA-256
requestId
```

Kebijakan UX awal:

- check otomatis ringan paling banyak sekali per 12–24 jam;
- manual refresh tetap ada;
- offline/error tidak mengganggu editor/run;
- tombol sidebar netral saat up-to-date;
- badge/warna aksen saat update tersedia;
- update tidak didownload sampai user memilihnya;
- progress, Cancel, retry yang aman, dan raw Diagnostics tersedia;
- Android system confirmation ditampilkan bila diwajibkan;
- tidak memakai emoji OEM sebagai ikon fungsional.

GitHub menyediakan latest published release endpoint:
https://docs.github.com/en/rest/releases/releases#get-the-latest-release

Android PackageInstaller dapat meminta user action dan caller wajib siap
menangani `STATUS_PENDING_USER_ACTION`:
https://developer.android.com/reference/android/content/pm/PackageInstaller

Trust untuk meminta install package diperiksa melalui
`PackageManager.canRequestPackageInstalls()`:
https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()

Sebelum implementation, perlu keputusan kedua mengenai:

- PackageInstaller Session versus ACTION_INSTALL_PACKAGE fallback;
- permission `REQUEST_INSTALL_PACKAGES` dan UX “Allow from this source”;
- bagaimana release menerbitkan checksum machine-readable;
- apakah update check dijalankan startup/app-resume/manual saja;
- batas ukuran/cache dan resume partial download;
- threat model jika GitHub account/release metadata disusupi;
- apakah pin signer saja cukup atau perlu signed update manifest terpisah.

## Fase 10 — Implement, verify, dan device UAT updater

Pagar keamanan sebelum commit install:

- release bukan draft/prerelease;
- owner/repo exact;
- asset exact dan hanya satu kandidat yang cocok;
- `applicationId == com.zaba.zcode`;
- `versionCode > installedVersionCode`;
- size sesuai metadata dan batas maksimum;
- local SHA-256 cocok;
- signer certificate cocok production fingerprint;
- APK parse valid;
- workspace flush sukses;
- update receipt tersimpan sinkron.

Failure matrix minimum:

- offline/DNS/TLS failure;
- GitHub 404/403/rate limit/5xx;
- partial body/network reset;
- storage penuh;
- cancel saat download;
- wrong asset/duplicate asset;
- wrong package;
- wrong signer;
- same version/downgrade;
- hash mismatch;
- workspace flush failure;
- process death di setiap state penting;
- Android permission belum diberikan;
- Android meminta user action;
- installer ditolak/dibatalkan/gagal;
- startup setelah sukses dan update receipt stale.

Updater tidak disebut selesai sampai:

```text
DESIGNED
→ IMPLEMENTED
→ LOCALLY VERIFIED + mutation proof
→ CI VERIFIED
→ FULL-EMULATOR VERIFIED (untuk lifecycle/PackageInstaller bila feasible)
→ DEVICE VERIFIED di INFINIX X6532C/API34/ARMv7
→ PRODUCTION SIGNED
→ update exact bytes berhasil
→ RELEASED
```

---

## 4. File yang pasti dan kemungkinan disentuh

Daftar ini bukan izin mengedit; ini blast-radius map sebelum approval.

### A. v1.0.21 — pasti disentuh

| File | Alasan |
|---|---|
| `app/src/main/java/com/zaba/zcode/core/packageengine/TransactionManager.kt` | pisahkan pre/post commit boundary |
| `app/src/main/java/com/zaba/zcode/WorkspaceViewModel.kt` | lock + identity/revision check queued save |
| `app/src/main/java/com/zaba/zcode/core/packageengine/Verifier.kt` | signed-wheel RECORD exception yang sempit |
| `app/src/test/java/com/zaba/zcode/core/packageengine/VerifierTest.kt` | fixture signed wheel + negative security cases |
| `test_zcode_kotlin_guards.py` | deterministic guards transaction/save/licensing/workflow |
| `.github/workflows/build.yml` | aktifkan compile-production-source branch-agnostic |
| `ci/workflows/build.yml` | mirror workflow harus identik |
| `ci/pending/README.md` | hapus instruksi branch PR #26 yang stale |
| `LICENSE` | GPLv3 Option B/full applicable licensing text |
| `README.md` | license/provenance/status versi dan release evidence |
| `app/src/main/java/com/zaba/zcode/ui/settings/AboutScreen.kt` | tampilkan license/provenance yang benar |
| `docs/RELEASE_NOTES_V1.0.20.md` | pertahankan evidence public release yang benar |
| `docs/RELEASE_NOTES_V1.0.21.md` | scope/status/license/update UAT contract |
| `docs/RFC_V1021_DATA_SAFETY_RELIABILITY.md` | desain final dan evidence blockers |

### B. v1.0.21 — kemungkinan disentuh setelah inspeksi/testability

| File | Kemungkinan alasan |
|---|---|
| test Kotlin baru di `app/src/test/...` | fault injection transaction/save lebih tepat daripada lexical guard |
| `app/src/main/java/com/zaba/zcode/core/files/WorkspaceTrashManager.kt` | hanya bila seam/barrier atau invariant clear perlu dipusatkan |
| `app/src/test/java/com/zaba/zcode/core/files/WorkspaceTrashManagerTest.kt` | test no-resurrection end-to-end core |
| `app/src/main/java/com/zaba/zcode/core/packageengine/PackageEngineV2.kt` | hanya bila post-commit warning/result contract perlu dipropagasi |
| `NOTICE` atau `NOTICE.md` baru | provenance ZABACODE, contributors, source commit, modification notice |
| asset license/notice baru di `app/src/main/assets/` atau resources | agar notice benar-benar masuk APK |
| `test_zcode_production_release.py` | guard license asset/workflow/release contract |
| `docs/PRD_ZCODE.md` | versi/status saat ini yang masih stale |
| `ci/pending/build.yml` | dihapus/diarsip setelah workflow aktif menjadi source of truth |

File “kemungkinan” tidak disentuh bila invariant bisa dibuktikan dengan diff
yang lebih kecil.

### C. v1.0.22 updater — hampir pasti disentuh jika desain kedua disetujui

| File/area | Alasan |
|---|---|
| `app/src/main/AndroidManifest.xml` | install-package permission/provider/component bila dibutuhkan |
| `app/src/main/java/com/zaba/zcode/ui/workbench/WorkbenchScreen.kt` | item/badge/progress update di sidebar |
| `app/src/main/java/com/zaba/zcode/MainActivity.kt` | lifecycle/navigation/result wiring |
| `app/src/main/java/com/zaba/zcode/WorkspaceViewModel.kt` atau dedicated updater VM | expose state dan safe workspace flush |
| package baru `core/update/` | metadata client, state machine, downloader, verifier, installer, receipt |
| `app/src/main/java/com/zaba/zcode/core/diagnostics/...` | raw update events yang dapat disalin |
| resources untuk FileProvider bila fallback intent dipakai | URI aman untuk APK private storage |
| unit/instrumentation/structural tests baru | failure matrix + mutation guards |
| `.github/workflows/production.yml` dan mirror | publish checksum/update metadata bila contract memerlukannya |
| `docs/` RFC/release/UAT | threat model, UX, rollback, evidence |

Nama class updater belum dikunci. Menentukan nama dan layer final sebelum
membaca call path adalah pura-pura pasti, jadi keputusan itu sengaja ditunda.

---

## 5. Kendala pasti berdasarkan pengalaman agent sebelumnya

Audit dilakukan terhadap `AGENTS.md`, `docs/SKILLS.md`, PRD/roadmap lama,
branch graph, contributor shortlog, dan commit agent Arena/Jules yang tersedia
di Git. “Semua pengalaman” di sini berarti seluruh bukti yang tersimpan di repo
/history; percakapan eksternal yang tidak pernah dicatat di repo tidak dapat
secara jujur diklaim sudah dibaca.

### 5.1 Jules: iterasi cepat yang saling membatalkan

Empat commit Jules pada editor/keyboard menunjukkan pola:

- `ea8d2b6`: handshake editor-ready + focus;
- `ba20bfc`: menambah timeout/resize/Compose focus handling;
- `359bf0d`: menghapus `clearFocus()` yang ternyata memutus IME dan menambah pip
  build config;
- `7d8d514`: mengembalikan perubahan pip dan mengganti dengan cleanup startup.

Pelajaran:

- test struktural “125 passed” tidak membuktikan IME/WebView device behavior;
- fokus, keyboard, load handshake, dan dependency packaging tidak boleh ditambal
  beberapa hipotesis sekaligus;
- setiap fix harus menyebut apa yang dibatalkan dari fix sebelumnya;
- lifecycle/device UAT diperlukan untuk perilaku Android.

Cara menangani:

- satu akar masalah per coherent commit;
- deterministic state/fault test dulu;
- jangan pakai delay sebagai obat kecuali delay adalah kontrak terbukti;
- jalankan focused device scenario dan simpan hasilnya.

### 5.2 Jules: scope besar sekaligus

Commit `9d0f7a7` mengubah 18 file, +1821/-130, sekaligus mengklaim Fase 2 dan 3:
autosave, terminal, themes, library, plugin AST, overlay, prewarm, folding,
Chaquopy upgrade, dan rename. History berikutnya mencatat beberapa compile fix,
termasuk commit `b8a8734` untuk tujuh Kotlin compile errors.

Pelajaran:

- terlalu banyak subsistem dalam satu commit menyulitkan bisect/revert;
- test host hijau bukan Kotlin compile proof;
- fitur dan runtime upgrade tidak boleh digabung;
- scope harus dipotong berdasarkan risiko, bukan rasa ingin cepat selesai.

Cara menangani:

- v1.0.21 dan updater dipisah versi;
- commit per perubahan koheren dan selalu green;
- workflow compile source wajib hidup di PR;
- tidak ada intelligence/runtime redesign di hotfix.

### 5.3 Arena: CI compile errors tanpa log penuh

History mencatat missing Compose imports, nullable callback, forward reference,
nested Kotlin comment, wrong workflow path, dan variable mismatch. Blob log CI
pernah tidak dapat diambil walau job/step status tersedia.

Cara menangani:

- Kotlin sanity scanner + lexical comment stripping;
- audit semua imports/symbol wiring pada file yang disentuh;
- compile-production-source wajib berjalan;
- bila log unavailable, gunakan nama failing step + source audit; jangan mengarang
  pesan error yang tidak terlihat;
- error baru menjadi permanent guard dan mutation proof.

### 5.4 Arena: parallel same-file edit corruption

Pada 2026-08-21, dua writer paralel menyentuh `editor-src/src/editor.js`.
Handshake terduplikasi dan edit masuk lokasi salah; esbuild menemukan syntax
error.

Cara menangani:

- parallel hanya untuk read atau disjoint write set;
- satu file selalu diedit serial;
- periksa diff setelah setiap batch edit;
- source clean dulu, baru generate shipped artifact.

### 5.5 Snapshot merusak executable bit/config

History mencatat executable bit `gradlew`/tool hilang dan Git config/remote/auth
dapat hilang antar snapshot.

Cara menangani:

- cek `git diff --summary` sebelum mengubah mode;
- pulihkan hanya executable drift yang benar-benar dibuktikan Git;
- cek branch/remote/auth setiap sesi;
- jangan pernah menganggap credential lama tersedia.

### 5.6 Sandbox resource dan emulator

Pengalaman nyata:

- `system.img` 1.8 GB di `/home/user` pernah memenuhi workspace;
- sandbox sekitar 1.9 GiB RAM tanpa KVM;
- Gradle dan full emulator bersamaan menghabiskan resource;
- production minSdk26 tidak dapat dipasang ke official ARMv7 API24 image;
- `-gpu off` membuat WebView crash karena EGL;
- emulator API24/test-only bukan device verification production.

Cara menangani:

- semua image/cache besar di `/var/tmp`;
- serialkan Gradle dan emulator;
- gunakan test-only minSdk24 hanya untuk layer Android/JVM/Chaquopy yang sesuai;
- label `FULL-EMULATOR VERIFIED`, bukan `DEVICE VERIFIED`;
- production update akhirnya diuji di Infinix API34 ARMv7.

### 5.7 Android/package lifecycle lebih sulit daripada happy path

Bug nyata sebelumnya:

- timeout caller meninggalkan worker tanpa owner;
- Cancel ditelan fallback dan berubah menjadi compatibility failure;
- native smoke meninggalkan process stale;
- task handoff/rebirth dapat merusak state jika receipt/flush tidak benar;
- queued save dan callback lama dapat menimpa identity baru.

Cara menangani updater:

- state machine dan operation identity eksplisit;
- cancel berarti worker benar-benar menuju terminal cleanup, bukan UI berhenti
  menunggu;
- process death/update receipt diuji;
- workspace flush fail-closed;
- callback stale ditolak;
- Diagnostics mencatat stage terakhir.

### 5.8 GitHub workflow permission dan branch drift

Pengalaman menunjukkan GitHub App/PAT tertentu tidak dapat mengubah workflow,
mirror dapat berbeda, dan branch gate lama membuat job penting `SKIPPED`.

Cara menangani:

- workflow aktif + mirror diubah bersama;
- equality guard;
- PR CI harus menunjukkan job `SUCCESS`, bukan hanya overall green saat job skip;
- jika push membutuhkan auth, minta authorization/token sementara yang minimal;
- jangan pernah meminta production passwords/keystore lewat chat.

### 5.9 Licensing claim dapat melampaui bukti

PR #27 mengandung klaim sole-holder/MIT relicense yang tidak didukung contributor
history dan upstream GPL notice.

Cara menangani:

- Option B;
- provenance + attribution;
- no sole-holder statement;
- no MIT-only claim untuk derived code;
- full license/NOTICE di repo dan APK;
- legal uncertainty ditulis jujur.

### 5.10 Update metadata bukan trust anchor tunggal

GitHub latest endpoint memberi discovery, tetapi metadata release sendiri tidak
cukup untuk mempercayai APK.

Cara menangani:

- pin package ID dan production signer;
- verify versionCode monotonic;
- verify local bytes/hash/size;
- pertimbangkan signed update manifest pada RFC v1.0.22;
- fail closed dan hapus artifact invalid;
- tidak pernah install hanya berdasarkan filename atau warna button.

---

## 6. Searching dan Context7

**Setuju. Bahkan untuk updater, searching wajib.** Perubahan ini melibatkan
Android lifecycle, install permission, PackageInstaller, GitHub Release API,
TLS/download, process death, storage, dan supply chain.

Urutan sumber:

1. source code + test upstream;
2. official Android/GitHub/Kotlin/standar docs;
3. maintainer issue dengan reproduksi;
4. controlled experiment;
5. secondary comparison hanya sebagai lead.

Context7 dipakai untuk menemukan dokumentasi version-specific bila tersedia,
tetapi tidak menjadi hakim akhir. Protokol:

```text
baca exact version dari repo
→ resolve library ID/version yang benar di Context7
→ query satu keputusan per call
→ simpan direct source URL
→ cross-check official source/docs/test
→ verify pada artifact/runtime ZCODE
```

Updater comparison yang layak diteliti sebelum coding:

- Android `PackageInstaller.Session` self-update behavior;
- F-Droid client/update metadata model;
- Obtainium/GitHub release discovery model;
- AppUpdater-style libraries hanya untuk membandingkan failure/maintenance,
  bukan otomatis diadopsi;
- GitHub API rate limit/cache semantics;
- Android unknown-app-source UX pada API34/OEM Infinix.

Hasil comparison harus dinyatakan sebagai salah satu:

- menguatkan desain;
- mengubah desain;
- tidak relevan bagi ZCODE/ARMv7;
- belum cukup bukti dan perlu eksperimen.

Tidak ada dependency baru yang boleh masuk hanya karena populer.

---

## 7. Target ZCODE

### Target jangka pendek

- v1.0.21 menjadi hotfix yang benar-benar menjaga file dan package state;
- update v1.0.20 → v1.0.21 lolos tanpa uninstall/clear-data;
- licensing/provenance tidak berbohong;
- satu exact production artifact diuji lalu bytes yang sama dirilis.

### Target jangka menengah

- v1.0.22 memberi update discovery dan verified one-tap update dari sidebar;
- semua tahap update terlihat di Diagnostics;
- kegagalan tidak merusak workspace atau versi lama;
- user tanpa PC tidak perlu menjadi ahli File Manager/APK signing.

### Target produk

> Memperkecil jarak antara “gw punya ide” dan “gw berhasil membuat sesuatu”
> pada Android terbatas, tanpa paywall, tanpa kehilangan data, dan tanpa klaim
> palsu.

Ukuran sukses bukan jumlah fitur. Ukuran sukses:

- force close mendekati nol;
- kehilangan/resurrection file nol;
- package environment selalu authoritative dan recoverable;
- update aman dan mudah;
- error raw dapat dilihat/disalin;
- ARMv7 tetap first-class;
- user lebih sering berkarya daripada mengurus aplikasi.

### Confidence saat ini

- Urutan **v1.0.21 safety dulu, v1.0.22 updater kemudian**: ~95%.
- Updater one-tap dapat dibuat secara teknis: ~90%.
- Update selalu 100% silent pada semua ROM/API: rendah; **tidak dijanjikan**.
- File/blast radius v1.0.21 di atas lengkap: ~90%, karena testability dapat
  menuntut satu atau dua seam/file tambahan setelah source inspection mendalam.

---

## 8. Approval gate

Tidak ada tindakan eksekusi sampai user memberi approval eksplisit.

Jika user berkata `GASS`, default interpretasinya hanya:

```text
mulai Fase 1–7 untuk memperbaiki PR #27 sebagai kandidat v1.0.21
```

Itu **bukan** izin otomatis untuk:

- merge PR #27;
- dispatch production;
- menggunakan credential;
- publish release;
- mulai implementasi updater v1.0.22.

Masing-masing boundary tersebut tetap membutuhkan evidence dan approval yang
sesuai. User tetap pemegang keputusan akhir; agent boleh memimpin pekerjaan
teknis setelah approval, tetapi wajib berhenti lagi bila evidence mengubah
scope, keamanan, permission, lifecycle, signing, atau release contract.
