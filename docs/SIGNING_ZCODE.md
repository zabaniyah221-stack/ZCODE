# ZCODE Signing Policy

**Tanggal identitas dibuat:** 2026-08-21
**Production package yang dicadangkan:** `com.zaba.zcode`
**Status:** key dibuat dan entry diverifikasi; production workflow belum
dikonfigurasi; belum pernah dipakai menandatangani release publik.

## 1. Public certificate metadata

Informasi di bagian ini adalah publik dan aman disimpan di repository:

```text
Alias                    : zcode-release
Entry type               : PrivateKeyEntry
Owner / issuer           : CN=ZCODE, O=ZCODE, C=ID
Public key               : RSA 4096-bit
Certificate signature    : SHA384withRSA
Created                  : 2026-08-21 05:32:46 GMT
Valid until              : 2054-01-06 05:32:46 GMT
SHA-256 certificate      :
40:13:92:19:3B:73:42:63:C8:EC:CE:93:E1:2B:E1:F7:
F3:07:20:3A:FE:42:82:DC:25:50:09:40:88:F3:8B:D2
SHA-256 compact          :
401392193b734263c8ecce93e12be1f7f307203afe4282dc2550094088f38bd2
```

Certificate fingerprint mengidentifikasi signer. Ia bukan private key dan bukan
password.

## 2. Private material — tidak boleh masuk repository

Material berikut harus tetap berada dalam custody user:

```text
zcode-release.jks
keystore password
key password
encrypted backup password
base64 dari keystore
```

Dilarang memasukkannya ke:

- source, commit, branch, issue, release asset, dokumentasi, atau chat;
- Git remote URL, shell command argument, log, screenshot, atau artifact CI;
- cache/workspace agent.

`.gitignore` menjaga ekstensi `.jks`, `.keystore`, `.p12`, dan `.pfx`, tetapi
ignore bukan pengganti audit secret.

## 3. Custody dan backup status

User melaporkan dua salinan off-device pada dua akun Google Drive berbeda.
Private key tidak pernah dikirim kepada agent.

Status yang boleh diklaim saat dokumen ini dibuat:

```text
KEY CREATED                 : USER VERIFIED
PRIVATE KEY ENTRY           : USER VERIFIED
PUBLIC FINGERPRINT          : RECORDED
OFF-DEVICE COPIES           : USER REPORTED (2 accounts)
BYTE-FOR-BYTE RECOVERY DRILL: NOT EVIDENCED IN REPO
CI PRODUCTION SIGNING       : NOT CONFIGURED
PRODUCTION APK SIGNED       : NO
PUBLIC RELEASE              : NO
```

Sebelum release publik pertama, recovery drill wajib membuktikan bahwa satu
backup dapat diekstrak, dibaca sebagai `PrivateKeyEntry`, dan menghasilkan
fingerprint SHA-256 yang sama. Jangan menyimpan hasil ekstrak lebih lama dari
keperluan drill.

## 4. RC signing tidak memakai production key

`ZCODE v1.0.20-rc1` memakai package terpisah:

```text
com.zaba.zcode.rc
```

RC tetap ditandatangani ephemeral CI debug key. Tujuannya menjaga production
identity belum tersentuh dan mencegah secret production tersedia pada workflow
branch/PR. Signature RC dapat berubah antar-run sehingga update mungkin meminta
uninstall; RC bukan tempat satu-satunya menyimpan project penting.

## 5. Production workflow contract — future, belum diimplementasikan

Ketika RC sudah lulus, workflow release production harus:

1. hanya berjalan dari tag/version yang disetujui;
2. memakai GitHub Environment terproteksi + approval;
3. membaca secret dari environment, bukan repository file;
4. mendekode keystore hanya ke `$RUNNER_TEMP`;
5. tidak mencetak password, base64, atau keystore path sensitif;
6. build satu optimized production APK/AAB;
7. menjalankan `apksigner verify --verbose --print-certs`;
8. membandingkan fingerprint signer dengan nilai publik di dokumen ini;
9. menghasilkan SHA-256 artifact;
10. menghapus keystore sementara pada `always()` cleanup;
11. baru membuat GitHub Release setelah seluruh verifikasi hijau.

Nama secret yang direncanakan, tanpa nilai:

```text
ZCODE_RELEASE_KEYSTORE_B64
ZCODE_RELEASE_STORE_PASSWORD
ZCODE_RELEASE_KEY_ALIAS
ZCODE_RELEASE_KEY_PASSWORD
```

Siapa pun yang dapat mengubah workflow berpotensi mencoba mengeksfiltrasi
secret ketika environment disetujui. Karena itu commit/tag dan workflow diff
harus direview sebelum approval.

## 6. Permanence and rotation

Setelah release `com.zaba.zcode` pertama, signing identity menjadi bagian dari
update contract. Jangan mengganti key, package ID, atau signing provider tanpa
rencana migrasi yang dibuktikan terhadap Android API/kanal distribusi target.

JKS warning dari `keytool` bukan error. Container JKS tetap didukung untuk
workflow Android ini; jangan mengonversi in-place hanya untuk menghilangkan
warning. Jika migrasi container diperlukan, gunakan salinan baru, verifikasi
fingerprint tetap sama, lalu pertahankan backup asli sampai recovery terbukti.

## 7. Official references

- Android app signing:
  https://developer.android.com/studio/publish/app-signing
- Play App Signing:
  https://support.google.com/googleplay/android-developer/answer/9842756
- GitHub deployment environments:
  https://docs.github.com/actions/deployment/targeting-different-environments/using-environments-for-deployment
