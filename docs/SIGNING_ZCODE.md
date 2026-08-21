# ZCODE Signing Policy

**Tanggal identitas dibuat:** 2026-08-21
**Production package yang dicadangkan:** `com.zaba.zcode`
**Status:** production identity aktif. Workflow fail-closed telah menandatangani
satu APK v1.0.20, fingerprint cocok, exact bytes lulus device UAT, dan release
publik diterbitkan dari draft yang sama tanpa rebuild.

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
PRODUCTION WORKFLOW SOURCE  : MERGED
GITHUB ENVIRONMENT SECRETS  : USER VERIFIED (4 names)
CI PRODUCTION SIGNING       : VERIFIED — run 32472551816
PRODUCTION APK SIGNED       : YES
PRODUCTION DEVICE UAT       : PASS — user report, crash none
PUBLIC RELEASE              : YES — v1.0.20
```

Recovery drill tetap wajib meskipun release pertama sudah terbit: satu backup
harus dibuktikan dapat diekstrak, dibaca sebagai `PrivateKeyEntry`, dan
menghasilkan fingerprint SHA-256 yang sama. Bukti drill tersebut belum tersimpan
di repo. Jangan menyimpan hasil ekstrak lebih lama dari keperluan drill.

## 4. RC signing tidak memakai production key

`ZCODE v1.0.20-rc1` memakai package terpisah:

```text
com.zaba.zcode.rc
```

RC tetap ditandatangani ephemeral CI debug key. Tujuannya menjaga production
identity belum tersentuh dan mencegah secret production tersedia pada workflow
branch/PR. Signature RC dapat berubah antar-run sehingga update mungkin meminta
uninstall; RC bukan tempat satu-satunya menyimpan project penting.

## 5. Production workflow contract

Pola ini **CI VERIFIED untuk v1.0.20**. Revisi hardening v1.0.21 masih berstatus
IMPLEMENTED pada branch sampai canonical CI dan signed draft menjalankannya.
Setiap versi baru harus diverifikasi ulang; keberhasilan v1.0.20 bukan bukti
otomatis bagi v1.0.21:

1. hanya berjalan dari tag/version yang disetujui;
2. memakai GitHub Environment terproteksi + approval;
3. membaca secret dari environment, bukan repository file;
4. membatasi setiap secret ke step minimum—jangan job-level `env`;
5. mendekode keystore hanya ke `$RUNNER_TEMP`;
6. tidak mencetak password, base64, atau keystore path sensitif;
7. build satu optimized production APK dan menjalankan JVM tests;
8. menjalankan `apksigner verify --verbose --print-certs`;
9. membandingkan fingerprint signer dengan nilai publik di dokumen ini;
10. menghasilkan SHA-256 artifact;
11. menghapus keystore dalam shell step yang sama **sebelum** upload/release action;
12. memverifikasi key tetap tidak ada pada cleanup akhir;
13. baru membuat draft GitHub Release setelah seluruh verifikasi hijau.

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

## 8. v1.0.20 production evidence

```text
Workflow run       : 32472551816 — SUCCESS
Source commit/tag  : 55860ff8059fd1b26e268a53dd3178126e80fbb3
Release URL        : https://github.com/muzape28-blip/ZCODE/releases/tag/v1.0.20
Published          : 2026-08-21T11:07:48Z
APK asset ID       : 523592433
APK bytes          : 34,682,027
APK SHA-256        :
b1d36a1d04a97325f325e1576ecfecb6be91308d675a36b41b85576a9a6285ed
Certificate SHA-256:
401392193b734263c8ecce93e12be1f7f307203afe4282dc2550094088f38bd2
Signature scheme   : APK Signature Scheme v2 verified
```

Evidence chain:

1. workflow verified exactly one release APK, package/version/assets, and signer;
2. user downloaded the draft APK and `sha256sum -c` returned `OK`;
3. user reported production UAT PASS and no crash;
4. the existing draft was published without another production workflow run;
5. unauthenticated public download was hashed independently and matched the
   user's UAT hash exactly;
6. GitHub release asset digest also reports the same APK SHA-256;
7. temporary CI keystore cleanup step succeeded.

Production workflow run count at publication: **1**. This proves the one-build
contract for v1.0.20. It does not yet prove update continuity; that requires a
future `com.zaba.zcode` version with a higher versionCode and the same signer.
