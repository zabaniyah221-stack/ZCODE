# ZCODE v1.0.19 — Release Candidate Notes

Tanggal kandidat: 2026-08-20  
Branch: `arena/v1019-fondasi`  
Status: **RELEASE CANDIDATE — CI + DEVICE VERIFIED, belum MERGED/RELEASED**

## North Star

> **Bukan tentang punya perangkat terbaik. Tentang tetap bisa berkarya dengan
> perangkat yang kita punya.**

v1.0.19 memperdalam alur Python Android pada perangkat terbatas: editor lebih
mudah menjelaskan masalah, package lebih jujur, workspace lebih tahan regresi,
dan perubahan package native tidak lagi memaksa user menutup lalu membuka ZCODE
secara manual.

## Perubahan utama

### Editor dan workbench

- Lint gutter CodeMirror 6 dan diagnostics dari `Checker`.
- Whitespace guard opsional; deteksi indentasi campuran.
- Traceback terminal dapat ditap untuk kembali ke file/baris sumber.
- TOOLS ditata sebagai satu area scroll dengan THEME tetap terjangkau.
- Reference Card Python dan Project Mini multi-file.
- Undo/Redo touch dengan `EditorState` dan history terpisah per file.
- Callback editor membawa document identity agar event terlambat tidak menimpa
  tab aktif yang berbeda.
- Layout portrait/landscape diperkeras tanpa memasang/melepas focus node ketika
  IME `adjustResize` mengubah tinggi layar.

### Library dan Samples

- 342 kartu package; **231 TESTED**, 8 COMPATIBLE, 16 EXPERIMENTAL,
  77 UNAVAILABLE, dan 10 INCOMPATIBLE.
- 37 sample runnable dalam 11 kategori tujuan.
- Detail Library dapat membuka sample lengkap dan memakai dependency gate yang
  sama dengan Samples.
- Kurasi kartu dan sample untuk NumPy, Matplotlib, HTTPX, Beautiful Soup,
  python-pptx, TinyDB, PyOTP, PyYAML, QR, Word, dan paket lain.
- QR mempunyai fallback SVG pure-Python saat Pillow tidak aktif.

### Package Engine

- Semantic event bertipe: `STEP`, `INFO`, `WARN`, `WAIT`, `OK`, `FAIL`,
  `STOP`, dan `RAW`. Prefix ikut tersalin; cancel bukan failure.
- Uninstall meminta konfirmasi, menyediakan `Batal`, menjelaskan bahwa reverse
  dependency belum diperiksa, dan tidak auto-clean dependency.
- Transport failure (`IncompleteRead`, `URLError`) tidak lagi dipalsukan menjadi
  `PACKAGE_NOT_AVAILABLE`; HTTP 404 dibedakan dari NETWORK.
- Specifier PEP 440 diterapkan pada kandidat local, PyPI, dan Chaquopy sebelum
  ranking/tested priority.
- Verdict `DEPENDENCY_VERSION_UNAVAILABLE` menyebut constraint dan versi yang
  benar-benar tersedia.
- Bokeh 3.9.2 ditolak secara benar karena `contourpy>=1.2` tidak tersedia pada
  CPython 3.11 ARMv7. Bokeh **3.3.4** dipromosikan menjadi TESTED setelah exact
  device UAT.

### Native-runtime rebirth

Smoke test package native dapat meninggalkan `.so`, pybind11 registry, dan
static C/C++ state hidup walau module baru dihapus dari `sys.modules`. v1.0.19
memperbaiki kelas masalah ini dengan process replacement yang disengaja:

1. deteksi `.so` staging dan extension native yang benar-benar termuat;
2. persist `NativeRuntimeState` stale receipt;
3. tawarkan `Nanti` atau `Simpan & mulai ulang`;
4. verifikasi seluruh draft/tab/workspace sebelum PID lama disentuh;
5. jalankan helper private `ZcodeRebirthActivity` pada process `:rebirth`;
6. helper membunuh PID main lama dan membuka explicit `MainActivity` baru;
7. process baru memvalidasi receipt/PID, memulihkan workspace, dan membersihkan
   stale state.

Jika memilih `Nanti`, banner amber tetap terlihat. Run, install, update,
uninstall, dan dispatch antrean package diblokir; editing, copy, save, dan
Diagnostics tetap tersedia.

Transisi menggunakan Binary Rain Canvas ringan. Setiap trail vertikal hanya
mengulang binary ASCII `ZCODE`:

```text
0101101001000011010011110100010001000101
```

Tidak ada AlarmManager, exact-alarm permission, service background, dependency
ProcessPhoenix, hardcode Bokeh/ContourPy, atau delay kosmetik.

## Bukti release candidate

### Canonical CI

```text
GitHub Actions run : 32348956505
Commit SHA        : efa56ad3370e2f69da4f069d614a0a466f0de1be
Check             : success
Build             : success
Artifact          : ZCODE-Fase12-APK
Artifact ID       : 9399175936
Archive bytes     : 44,735,650
Archive SHA-256   : 448af10bbfb0c3e7e8a833e2452dd08ae62852d4f6194deec6596135fff4a37b
```

### Device UAT

```text
Device  : INFINIX X6532C
Android : 14 / API 34
ABI     : armeabi-v7a, armeabi
```

Terbukti pada artifact exact di atas:

- Bokeh 3.9.2 ditolak dengan constraint ContourPy yang benar.
- Bokeh 3.3.4 + 19 dependency terpasang dan mengaktifkan native restart.
- `WORKSPACE_FLUSH_OK → RUNTIME_RESTART_REQUEST → REBIRTH_HELPER_START →
  APP_START → RUNTIME_RESTART_OK` dengan PID lama/helper/main baru berbeda.
- Binary Rain terlihat; launcher tidak muncul; file dan tiga tab kembali utuh;
  success notice muncul.
- Direct import: Bokeh 3.3.4, ContourPy 1.0.5, NumPy 1.26.2, Pandas 2.1.3.
- Standalone HTML berhasil.
- Contour HTML berhasil, ukuran **646.935 byte**, exit code 0.
- Jalur `Nanti` mempertahankan banner dan memblokir Run/package mutation.
- Tombol banner melakukan relaunch; Run kembali berhasil.
- Colorama 0.4.6 pure-Python menghasilkan `0 .so staging, 0 extension native
  termuat`, tidak meminta restart, dan `PURE_OK 0.4.6` exit 0.
- Semantic log tampil dan tersalin; cancel memakai `[STOP]`.
- Uninstall `Batal` mempertahankan package; confirm uninstall menghapusnya;
  reinstall tetap berhasil.
- Diagnostics berakhir dengan `(belum pernah crash Java)`.

Catatan kejujuran: dua percobaan contour awal gagal karena script UAT yang
diberikan agent salah menggunakan API Bokeh (`levels` integer, lalu tanpa
`fill_color`/`line_color`). Keduanya bukan regresi ZCODE; script valid kemudian
menghasilkan contour 646.935 byte.

## Verification status

```text
DESIGNED          : selesai
IMPLEMENTED       : selesai
LOCALLY VERIFIED  : 594 tests + 61 Kotlin files + supply-chain guard
CI VERIFIED       : ya
DEVICE VERIFIED   : ya, pada device/artifact yang disebutkan
MERGED             : belum
RELEASED           : belum
```

## Batas yang tetap ada

- Python tetap 3.11 demi native wheel ARMv7; 3.12+ menghilangkan 32-bit.
- Python user masih in-process. CPU loop/native call yang macet belum selalu
  dapat dihentikan tanpa mematikan process utama; private-process Python adalah
  target setelah v1.0.19.
- SciPy/scikit-learn tidak tersedia untuk CPython 3.11 ARMv7 melalui indeks
  Chaquopy.
- Kivy/Tkinter/Qt/Pygame dan GUI desktop bukan bagian runtime ZCODE sekarang.
- Uninstall belum memiliki reverse-dependency ownership graph.
- Bukti perangkat utama berasal dari satu INFINIX ARMv7; bukan klaim universal
  seluruh ROM/WebView Android.
- Resolver NETWORK failure path memiliki CI/fault-injection proof, tetapi exact
  `IncompleteRead` dual-source failure tidak muncul alami pada UAT final.
- Build saat ini adalah debug artifact; release signing/distribusi formal belum
  dilakukan.

## Release gate berikutnya

1. Full local regression setelah promosi katalog Bokeh.
2. CI final untuk commit release-note/catalog.
3. Buka PR `arena/v1019-fondasi → main`.
4. Review PR dan persetujuan user sebelum merge.
5. Merge/release bukan konsekuensi otomatis dari PR hijau.
