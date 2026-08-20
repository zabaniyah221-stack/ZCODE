# 💎 Pemeriksaan 9 referensi user — mana harta karun, mana jalan buntu

Riset 2026-08-13 atas tautan yang dikirim user untuk "imunisasi ZCODE".
Setiap tautan diperiksa, bukan diasumsikan.

---

## ⚠️ Jebakan besar yang harus dipahami dulu

Sebagian besar referensi ini tentang **ARMv7 Linux**, bukan **ARMv7 Android**.
Keduanya **tidak kompatibel**:

| | ARMv7 Linux (Raspberry Pi) | ARMv7 Android |
|---|---|---|
| libc | **glibc** | **bionic** |
| tag wheel | `manylinux_armv7l` | `android_21_armeabi_v7a` |
| bisa dipakai ZCODE? | ❌ **TIDAK** | ✅ ya |

Wheel `manylinux_armv7l` **tidak akan pernah** jalan di Android — bukan karena
tag-nya salah, tapi karena `.so`-nya di-link ke glibc yang tidak ada di Android.
Memaksakannya = crash native (SIGSEGV), bukan ImportError yang sopan.

Ini menggugurkan 4 dari 9 referensi sejak awal.

---

## Hasil per referensi

### ❌ 1. `bjia56/armv7l-wheels` — ARSIP, dan salah platform

> *"This repository was archived by the owner on **Sep 18, 2025**. It is now read-only."*
> *"This repo is not being maintained any longer. Consider using piwheels.org or cibuildwheel."*

Dua masalah: sudah mati, **dan** targetnya `armv7l` Linux (glibc), bukan Android.
Terakhir dibangun untuk Python 3.7/3.8 — ZCODE di 3.11.

### ❌ 2. `forums.balena.io` — piwheels, Raspberry Pi

piwheels = wheel untuk Raspberry Pi OS, glibc. Bukan Android. Sama sekali tidak
bisa dipakai.

### ❌ 3. `maxisoft/pytorch-arm` — torch ARM Linux

Bahkan seandainya kompatibel: torch di ARMv7 Android dengan RAM terbatas tidak
realistis. Pydroid pun menaruh torch di balik paywall.

### ❌ 4. `postmarketos` `.gitignore` — tidak relevan

Berkas `.gitignore` distro Linux. Tidak ada kaitan dengan wheel Python Android.

### 🟡 5. `MagicStack/uvloop#651` — pelajaran, bukan solusi

uvloop butuh libuv + syscall POSIX yang Android batasi. Konfirmasi ulang: paket
yang bergantung erat pada POSIX = risiko tinggi di Android. Berguna sebagai
**data untuk katalog Library** (tandai uvloop 🔴), bukan sebagai perbaikan.

### 🟡 6. GitLab `reasonable/CI.yml` — contoh cibuildwheel biasa

Pola standar. Tidak ada yang khusus Android/ARMv7.

---

## 💎 HARTA KARUN #1 — `cibuildwheel` MEMBUKTIKAN resep tag ZCODE benar

Ini temuan paling bernilai, walau bukan berupa wheel siap pakai.

cibuildwheel 3.1+ mendukung Android resmi (PEP 738). Dari dokumentasinya:

> **archs — Android: `arm64_v8a` `x86_64`**
>
> *"Android builds will honor the `ANDROID_API_LEVEL` environment variable to set
> the minimum supported API level for generated wheels. This **defaults to 24**."*

Tiga hal yang langsung berguna:

1. **Format tag terkonfirmasi resmi**: `cp311-cp311-android_<API>_<abi>` —
   persis bentuk yang dipakai resep perbaikan tag ZCODE. Resep itu **tervalidasi
   oleh standar upstream**, bukan tebakan.

2. **API level adalah bagian tag, dan pencocokannya "wheel ≤ device"** —
   persis aturan yang sudah dirancang ZCODE. Default upstream 24; wheel Chaquopy
   ada yang `android_21` dan `android_24`; perangkat runtime user API 34 → keduanya lolos.

3. **`armeabi_v7a` TIDAK ADA di daftar arch cibuildwheel.**
   Ekosistem Python resmi **tidak mendukung ARMv7 Android sama sekali** —
   PEP 738 hanya menetapkan `arm64` dan `x86_64` sebagai tier 3.

**Konsekuensi keras:** satu-satunya sumber wheel native ARMv7 Android di dunia
adalah **indeks Chaquopy**. Bukan PyPI, bukan cibuildwheel, bukan piwheels.
Itu membuat temuan `ARMV7_COMPAT_2026_08_13.md` (numpy 1.26.2, pandas 1.5.0,
pillow 9.2.0, matplotlib 3.6.0, lxml, cryptography, pyyaml, psutil — semua ada
cp311 ARMv7) jauh **lebih berharga dari perkiraan**: itu bukan salah satu opsi,
itu **satu-satunya**.

Dan karena Chaquopy berhenti membangun 32-bit di Python 3.12+, sementara
cibuildwheel pun tidak menyentuh ARMv7 — **Python 3.11 + Chaquopy adalah satu-
satunya jendela yang tersisa untuk numpy di HP user.** Menaikkan versi Python
akan menutupnya permanen.

---

## 💎 HARTA KARUN #2 — `espressif/idf-python-wheels`: pola wheelhouse mandiri

Repo **aktif** (commit terakhir Jul 2026), dipakai produksi oleh Espressif untuk
membangun & meng-host wheel sendiri. Dari pesan commit-nya:

> `fix(armv7): repair pipeline, wheel merge checks, and Windows bleak-winrt`
> - ARMv7/Legacy: **auditwheel repair**, libffi Docker prep, piwheels, S3 warnings,
>   **prune tested artifacts**, upload rewrite fixes
> - **`check_wheel_collisions`** scoped to ARMv7 vs Legacy

Empat praktik yang layak ditiru ZCODE **tanpa perlu membangun wheel apa pun**:

| Praktik Espressif | Padanan di ZCODE |
|---|---|
| `check_wheel_collisions` | guard: dua wheel beda ABI tak boleh menimpa slot sama |
| "prune tested artifacts" | `tested-manifest.json` — **sudah ada**, tapi isinya salah versi |
| auditwheel repair | tidak berlaku (Chaquopy sudah menyediakan wheel jadi) |
| pisahkan indeks per-ABI | ZCODE **harus** memisahkan cache `python-env/wheels` per-ABI |

**Temuan kelemahan ZCODE dari sini:** cache wheel ZCODE
(`Paths.pythonWheels()`) **tidak dipisah per-ABI**. Kalau APK yang sama dipakai
di HP ARMv7 lalu di-restore ke ARM64 (atau sebaliknya), wheel ARMv7 bisa terpakai
di ARM64 → **crash native**. Skenario nyata: backup/restore Android.
Ini bug laten yang belum pernah terjadi tapi pasti akan terjadi.

Perbaikannya murah: `python-env/wheels/<abi>/`.

---

## 📌 Ringkasan imunisasi yang benar-benar didapat

| # | Temuan | Nilai |
|---|---|---|
| 1 | Resep tag Android ZCODE **cocok dengan standar upstream** | validasi |
| 2 | Aturan "API wheel ≤ API perangkat" **benar** | validasi |
| 3 | **Chaquopy satu-satunya sumber** wheel ARMv7 Android | strategis |
| 4 | Python 3.11 = **jendela terakhir** untuk numpy ARMv7 | strategis |
| 5 | Cache wheel ZCODE **tidak dipisah per-ABI** → bug laten | **bug baru** |
| 6 | Wheel `manylinux_armv7l` **wajib ditolak** (glibc≠bionic) | **guard baru** |
| 7 | uvloop dkk = 🔴 di katalog Library | data |

Poin 5 & 6 adalah **temuan bug nyata** — keduanya belum pernah teridentifikasi
sebelumnya, dan keduanya berasal dari referensi user.

---

## Usulan: 2 guard tambahan untuk build #2 (murah, mencegah crash native)

**Guard 1 — tolak wheel Linux yang menyamar.**
`manylinux*`, `musllinux*`, `linux_armv7l` harus **selalu** incompatible,
bahkan jika string tag-nya kebetulan cocok. Ini mencegah SIGSEGV yang tidak
bisa ditangkap `try/except`.

**Guard 2 — pisahkan cache wheel per-ABI.**
`python-env/wheels/<abi>/` supaya wheel ARMv7 tidak pernah dipakai di ARM64.

Keduanya kecil (masing-masing < 20 baris) dan langsung mencegah kelas crash
yang paling sulit didiagnosis di HP tanpa logcat.

---

## Kesimpulan jujur

Dari 9 referensi: **4 jalan buntu** (salah platform / arsip), **2 pelajaran
kecil**, dan **2 harta karun nyata**.

Tidak ada satu pun yang memberi ZCODE wheel siap pakai untuk ARMv7 Android —
karena benda itu **tidak ada di luar Chaquopy**. Tapi referensi user menghasilkan
sesuatu yang lebih berguna: **konfirmasi bahwa arah perbaikan tag ZCODE sudah
benar**, dan **dua bug laten yang belum pernah terlihat**.

Referensi ini tidak mengubah rencana build #2 — hanya menambah 2 guard murah
ke dalamnya.
