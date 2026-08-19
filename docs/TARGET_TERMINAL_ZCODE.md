# TARGET TERMINAL & INTERPRETER PRIBADI ZCODE

Status: **RESEARCH TARGET / KULKAS** — bukan scope v1.0.19  
Dicatat: 2026-08-19

## 1. Kebutuhan nyata

User membayangkan terminal milik ZCODE sendiri untuk command dasar dan Python,
dipengaruhi pelajaran ZABAWHEELS/ZABACODE serta bukti ZMUX bahwa Alpine+PRoot
bisa bekerja. Target yang sebenarnya perlu dipisahkan:

1. **Command dasar**: `pwd`, `ls`, `cd`, `cat`, `mkdir`, `cp`, `mv`, `rm`,
   `clear`.
2. **Python REPL / runner**: `python`, `python file.py`.
3. **Paket**: `pip install` tetap melalui Package Engine V2 agar transaksi,
   smoke test, rollback, dan diagnostik tidak diduplikasi.
4. **Hard stop & crash isolation**: loop/native crash Python tidak boleh
   membunuh atau meninggalkan thread di proses UI.
5. **Linux penuh** hanya bila kebutuhan sudah mencakup shell sebenarnya,
   `git`, compiler, `apk`, SciPy, atau binary Linux.

## 2. Kondisi sekarang

`ExecutionEngine` disebut dual-backend, tetapi pembagiannya adalah:

- Android: Chaquopy 3.11 **in-process**;
- desktop/CI: `python3` subprocess.

Terminal Android sekarang adalah terminal UI + input/output Python, bukan shell.
Ctrl+C deterministik saat `input()`, tetapi CPU loop/native blocking tidak dapat
dibunuh tanpa mematikan proses aplikasi. Backend subprocess Android belum ada.

## 3. Tangga senjata

### T0 — kontrak kebutuhan

Definisikan command minimum dan non-goal. Jangan memakai nama Linux shell bila
belum mendukung pipe, redirect, environment, dan arbitrary executable.

### T1 — ZCODE Command Console

Command file dasar diimplementasikan lewat FileManager/Kotlin. `python` menuju
runtime ZCODE; `pip install` menuju Package Engine V2. Ringan, tetapi bukan
shell dan belum otomatis menyelesaikan hard-kill interpreter in-process.

### T2 — spike private process Chaquopy

Uji `PythonExecutionService` pada private process `android:process=":python"`:

1. start Python sekali dalam process service;
2. run/REPL dan stream stdout/stderr via Binder;
3. input interaktif;
4. import environment package user;
5. jalankan `while True: pass`;
6. hentikan process Python tanpa mematikan UI;
7. start ulang dan run lagi;
8. ukur startup/RSS pada Infinix ARMv7;
9. uji rotasi, background, app-kill, dan recovery.

Android memungkinkan component memakai private process dengan nama berawalan
`:`. Chaquopy mendokumentasikan `Python.start()` sekali per process dan context
dapat berupa Service. Ini membuat spike layak, **bukan bukti production-ready**.

Sumber:
- https://developer.android.com/guide/topics/manifest/service-element
- https://chaquo.com/chaquopy/doc/current/android.html

### T3 — minimal standalone CPython bionic

Audit executable CPython 3.11 ARMv7+ARM64, dependency native, lisensi, ukuran,
loader, dan distribusi. Tidak boleh diasumsikan cukup download lalu `chmod +x`:
Android 10/API 29+ melarang app target modern mengeksekusi binary dari writable
app home (W^X). Executable harus memiliki jalur distribusi/loader yang sesuai.

Sumber:
- https://developer.android.com/about/versions/10/behavior-changes-10
- https://developer.android.com/privacy-and-security/security-best-practices
- https://github.com/termux/termux-packages/wiki/Termux-execution-environment

### T4 — Alpine/PRoot on-demand

Dipilih terakhir bila command console/private Python tidak cukup. Tidak masuk
APK utama; download harus opt-in, mode dan package environment harus terpisah
jelas dari Chaquopy. Wajib mengatasi:

- rootfs besar, startup/RAM/storage;
- dua interpreter dan dua package manager;
- workspace bridge;
- lifecycle/process ownership;
- corruption recovery dan rootfs update;
- ABI ARMv7/ARM64;
- kebijakan distribusi dan security.

## 4. Keputusan sekarang

- Jangan integrasikan ZMUX/Alpine ke v1.0.19.
- Selesaikan UAT, semantic log migration, PR, dan release lebih dulu.
- Riset pertama setelah release: **private-process Chaquopy spike**, bukan full
  distro.
- Upgrade/downgrade/ganti jalur bila eksperimen memberi bukti baru; dokumen ini
  adalah pegangan, bukan kontrak permanen.

## 5. Status kejujuran

- Command Console: **DESIGNED kasar**, belum IMPLEMENTED.
- Private process: **TECHNICALLY PLAUSIBLE**, belum dibuktikan.
- Standalone bionic: **RESEARCH TARGET**.
- Alpine/PRoot: **PROVEN di ZMUX sebagai produk terpisah**, belum terbukti aman,
  ringan, atau layak sebagai integrasi ZCODE.
