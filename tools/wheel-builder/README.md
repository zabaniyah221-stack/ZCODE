# 🔧 ZCODE Wheel Builder (SPEC-001 Phase 3)

Tujuan: membangun wheel **Android** untuk package native yang belum tersedia di
index Chaquopy/PyPI — supaya ZCODE tidak menggantung penuh pada ketersediaan
wheel pihak ketiga ("benar-benar free").

## Status: SKELETON — belum diverifikasi build nyata

File ini + `build_wheel.sh` + `.github/workflows/build-wheels.yml` adalah kerangka
berdasarkan dokumentasi resmi Chaquopy ("Building Python C extensions"). Build
nyata butuh **Android NDK** + env Android — verifikasi dilakukan saat ada runner
CI dengan NDK terpasang (lihat `ci/workflows/build.yml` untuk pola setup).

## Alur

1. Developer/CI menjalankan `build-wheels.yml` (workflow_dispatch) dengan daftar package.
2. Toolchain `pip install chaquopy` membangun wheel per ABI (`arm64-v8a`, `armeabi-v7a`) untuk Python 3.11.
3. Wheel di-upload sebagai artifact → dimasukkan ke `python-env/wheels/` (ZCODE wheel source).
4. `package_runtime.resolve` memprioritaskan local wheel cache → wheel hasil build
   ZCODE otomatis dipakai tanpa internet.

## Catatan jujur

- Build C extension butuh toolchain yang cocok dengan versi Chaquopy (17.0.0).
- Package murni-Python TIDAK perlu ini — sudah jalan dari PyPI.
- Output wheel diverifikasi dengan `test_zcode_package_runtime.py::TestWheelInfo`
  (tag matching) sebelum dipakai.
