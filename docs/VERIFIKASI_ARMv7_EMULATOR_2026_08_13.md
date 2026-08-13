# 🧪 Verifikasi Resolver di Emulator ARMv7 (2026-08-13)

Dokumen ini mencatat hasil verifikasi **resolver ZCODE** yang dijalankan pada
**Python 3.11.15 ARMv7 asli** (armv7l) via qemu di sandbox — persis versi &
arsitektur device user. Ini bukan mock: resolver membaca data PyPI & Chaquopy
NYATA, dan `packaging` berjalan di interpreter ARMv7.

> Status: **pegangan**, bukan acuan terkunci. Dapat diperbarui bila ada temuan baru.

---

## Cara menjalankan (ringkas)

Lihat `docs/SKILLS.md` → "Mengakali keterbatasan sandbox" untuk langkah lengkap.

```bash
# /tmp/armpy = Python 3.11 ARMv7 via qemu (QEMU_LD_PREFIX glibc armhf)
/tmp/armpy <script>.py
```

---

## Hasil Verifikasi

### 1. Resolve pandas==2.1.3 — Bug K (deps per-versi) ✅
```
SUKSES: pandas, numpy, chaquopy-openblas, chaquopy-libgfortran, chaquopy-libcxx,
        python-dateutil, six, pytz, tzdata
pandas  v2.1.3  src=chaquopy  deps_src=pypi-version
waktu 10.7s, HTTP total 30, per-versi 0
```
**pytz & tzdata MASUK** → Bug K terbukti beres di ARMv7. Waktu jauh di bawah
batas 90s PyCall.

### 2. Marker environment (rich) ✅
```
Py3.11: rich deps = [rich, pygments, markdown-it-py, mdurl]
        typing_extensions TIDAK ada (marker python_version < "3.9" = false) ✅
Py3.8 : rich deps = [rich, typing-extensions, pygments, ...]
        typing_extensions ADA (marker true) ✅
```
Marker dievaluasi benar di Python 3.11 ARMv7.

### 3. Resolve paket native (host deps) ✅
| Paket | Hasil | Waktu |
|---|---|---|
| lxml | +chaquopy-libxml2, +chaquopy-libxslt | 8.1s |
| cryptography | +cffi, +pycparser | 4.8s |
| pillow | +chaquopy-libjpeg, +chaquopy-freetype | 5.2s |
| pyyaml | +chaquopy-libyaml | 1.9s |
| aiohttp | 9 paket (deps lengkap) | 17.1s |
| matplotlib==3.6.0 | 17 paket lengkap | 23.0s |

Semua < 23s — jauh di bawah 90s.

### 4. Edge cases ✅
- `math` (stdlib) → balas "sudah tersedia di ZCODE" ✅
- `xyz-tidak-ada-123` → unavailable dengan alasan ✅
- `requests==1.0.0` (sdist-only) → ditolak (0 paket, bukan install palsu) ✅
- Offline cache wheel → `source=local`, `deps_source=wheel` (baca cache, 0 HTTP) ✅

---

## Kesimpulan

Resolver ZCODE (Bug K + optimasi timeout + peta native + marker) **terbukti
benar dan cepat di Python 3.11 ARMv7 persis device**. APK yang dulu timeout
matplotlib kemungkinan build sebelum optimasi `faab182`; kode saat ini (dengan
emulator terverifikasi) seharusnya tidak timeout.

## Batas (jujur)

Emulator ini **hanya** menguji **logika resolver** (metadata, deps, host deps,
marker, cache). Ia **tidak** mengeksekusi **wheel native Android** (matplotlib
`.so`, dll.) karena butuh bionic libc + Chaquopy runtime yang hanya ada di APK.
Jadi `import` penuh paket native tetap hanya bisa diverifikasi di **device nyata**.
