# Full Android ARMv7 Emulator — sandbox profile (2026-08-13)

**Status:** BOOT + APK + COMPOSE + WEBVIEW + CHAQUOPY + CANCEL VERIFIED

**Bukan:** pengganti UAT artifact production pada HP API26+

## 1. Kenapa ada lapisan ketiga

Sebelumnya ZCODE punya:

1. `armpy`: Python 3.11 ARMv7, tetapi glibc;
2. `bionic311`: Python 3.11 ARMv7 + Android bionic/linker, tetapi tanpa
   framework Android, ART/JVM, Activity, Compose, WebView, dan APK install.

Full emulator menambah lapisan ketiga:

3. Android 7.0 API24 ARMv7 (`armeabi-v7a`) penuh melalui emulator klasik
   Google/QEMU tanpa KVM.

Ia dapat membuktikan callback Kotlin↔Python, lifecycle Activity, WebView,
Compose, PackageManager, app process, dan Diagnostics yang tidak dapat diuji
`bionic311`.

## 2. Riset dan keputusan

### Fakta resmi/relevan

- Android Emulator mendukung headless `-no-window`, software acceleration, dan
  konfigurasi CLI:
  <https://developer.android.com/studio/run/emulator-commandline>
- VM acceleration Linux normalnya membutuhkan KVM; sandbox ini tidak mempunyai
  `/dev/kvm`:
  <https://developer.android.com/studio/run/emulator-acceleration>
- AVD besar dapat dipindah melalui `ANDROID_AVD_HOME`:
  <https://developer.android.com/tools/variables>
- Emulator modern QEMU2 tidak lagi mendukung guest ARM32 pada host x86_64.
  Emulator 27.3.8 masih membawa `emulator64-arm` klasik. Release notes mencatat
  transisi/penghapusan QEMU1 pada era 29:
  <https://developer.android.com/studio/releases/emulator>
- Image `armeabi-v7a` resmi hanya tersedia sampai API25 atau lebih rendah.
  ZCODE production minSdk26, sehingga tidak ada official image yang sekaligus
  ARMv7-native dan memenuhi minSdk production.

### Kombinasi yang dipilih

| Komponen | Pilihan | Alasan |
|---|---|---|
| Emulator | 27.3.8 build 4848055 | masih punya classic `emulator64-arm` |
| Guest | API24 revision 7 | official ARM EABI v7a image |
| CPU | QEMU TCG, Cortex-A8 | `/dev/kvm` tidak ada; guest harus ARM32 |
| RAM | `-memory 512 -qemu -m 512` | mencegah QEMU menaikkan guest ke 1 GB |
| GPU | SwiftShader | GPU off membuat WebView Chromium abort |
| UI | headless 480×800 | hemat resource; adb/uiautomator/screencap cukup |
| Storage | `/var/tmp` | jangan memenuhi snapshot workspace `/home/user` |

### Yang ditolak

- **Redroid:** butuh Docker/privileged + binder kernel; sandbox tidak punya
  Docker/binder device, dan image x86 tidak membuktikan ARMv7 native.
- **Emulator modern ARM32:** QEMU2 menolak CPU architecture `arm`.
- **API30 x86/x86_64 + ARM translation:** tidak memastikan resolver menerima
  ABI perangkat `armeabi-v7a`; juga butuh emulator modern tanpa KVM dan resource
  lebih besar.
- **Menurunkan minSdk production:** emulator tidak boleh mengubah kontrak produk.

## 3. Batas resource sandbox yang diukur

Sandbox saat eksperimen:

- 2 vCPU x86_64;
- RAM 1.9 GB, tanpa swap;
- `/dev/kvm` tidak ada;
- disk kosong ±20 GB.

Pengukuran:

| Mode | Emulator RSS | Dampak |
|---|---:|---|
| default/guest 1 GB + SwiftShader | ±1.55 GB | nyaris OOM, tidak aman |
| QEMU 512 MB + GPU off | ±585–727 MB | aman, tetapi WebView SIGABRT |
| QEMU 512 MB + SwiftShader | ±874 MB boot; ±1.17 GB app aktif | dapat dipakai, margin tipis |
| ZCODE app PSS | ±47–78 MB | app sendiri relatif ringan |

**Invariant operasional:** jangan jalankan Gradle build dan full emulator secara
bersamaan. Script start menolak bila `MemAvailable < 1.2 GB`.

## 4. Hasil nyata

### Guest

```text
ro.product.cpu.abi = armeabi-v7a
ro.build.version.sdk = 24
sys.boot_completed = 1
```

Cold boot software-emulated: sekitar 1–3 menit tergantung cache. APK 46 MB
memerlukan sekitar 90–100 detik untuk install/dexopt.

### APK production

Artifact production v1.0.16 ditolak secara benar:

```text
INSTALL_FAILED_OLDER_SDK
Requires newer sdk version #26 (current version is #24)
```

Tidak ada flag sah pada PackageManager API24 yang dapat mengabaikan genuine
minSdk requirement.

### APK emulator-only

Dibangun dari source/commit yang sama dengan dua perubahan sementara yang
selalu dipulihkan setelah build:

- minSdk 26 → 24;
- start route `editor` → `pip` untuk otomasi UAT tanpa gesture drawer.

Production source bersih setelah build; varian ini tidak boleh dirilis.

Yang berhasil:

- install PackageManager;
- Activity/Compose render;
- CodeMirror WebView render dengan SwiftShader;
- Chaquopy startup (`PYTHON_START_OK`);
- Python 3.11.14;
- runtime probe membaca ABI `armeabi-v7a`;
- halaman Install Modules/Manual Install;
- resolve progress + Diagnostics;
- tombol cooperative Cancel.

## 5. Bug M yang hanya terlihat di full emulator

Uji v1.0.16:

```text
PKG_RESOLVE_CANCEL_REQUEST op=1
stage=cancelled package=numpy
PKG_RESOLVE_WORKER_END op=1
PKG_ANALYZE_FAIL [COMPATIBILITY]
```

Akar: `_check_cancelled()` melempar `ResolveError(CANCELLED)`, tetapi catch
fallback metadata PyPI/Chaquopy menganggap semua `ResolveError` sebagai source
failure opsional. Cancel ditelan, resolver lanjut dengan kandidat kosong, lalu
menghasilkan COMPATIBILITY.

Perbaikan v1.0.17:

- `_propagate_cancel(error)` wajib dipanggil sebelum fallback;
- AST guard memeriksa seluruh handler `ResolveError` baru;
- negative metadata failure cache mencegah URL metadata gagal yang sama dibaca
  berulang dalam satu resolve.

Uji ulang:

```text
16:05:29.568 PKG_RESOLVE_CANCEL_REQUEST
16:05:30.800 stage=cancelled package=numpy
16:05:30.943 PKG_RESOLVE_WORKER_END
16:05:31.339 PKG_ANALYZE_CANCELLED matplotlib
```

Cancel selesai ±1.37 detik dan tombol kembali menjadi Install.

## 6. Cara memakai

```bash
# Setup sekali per sandbox (±4 GB setelah extract):
bash tools/setup_armv7_full_emu.sh

# Start sebagai long-running process (gunakan start_process di Agent Mode):
bash tools/start_armv7_full_emu.sh

# Tunggu boot dan verifikasi ABI/API:
bash tools/verify_armv7_full_emu.sh

# Opsional, install APK TEST-ONLY minSdk24:
bash tools/verify_armv7_full_emu.sh /path/to/emulator-only.apk

# Stop segera setelah uji:
bash tools/stop_armv7_full_emu.sh
```

Untuk cold data:

```bash
ZCODE_ARMV7_WIPE=1 bash tools/start_armv7_full_emu.sh
```

## 7. Aturan compatibility sandbox

1. Semua image/cache/AVD di `/var/tmp/zcode-armv7-full`.
2. Jangan taruh system image, Gradle cache, atau AVD di `/home/user`.
3. Arsip ZIP dihapus setelah extract.
4. QEMU dipaksa 512 MB; jangan hapus `-qemu -m 512`.
5. SwiftShader wajib untuk WebView; jangan gunakan `-gpu off` untuk app test.
6. Jangan start jika Gradle/JDK build sedang aktif.
7. Stop setelah uji; emulator menggunakan satu core hampir 100% selama boot.
8. Jangan klaim DEVICE VERIFIED dari image API24/test-only APK.
9. `bionic311` tetap tes resolver yang lebih cepat; full emulator dipakai hanya
   untuk gap Android/JVM/UI/lifecycle.
10. HP ARMv7 API26+ user tetap sumber kebenaran artifact production.
