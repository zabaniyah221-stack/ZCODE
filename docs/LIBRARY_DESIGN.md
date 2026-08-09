# 📚 LIBRARY_DESIGN — Fitur LIBRARY di menu INSTALL MODULES (2026-08)

Dokumen ini merancang **layar LIBRARY**: katalog paket pip terkurasi dengan tag
yang menyesuaikan perangkat (ABI + RAM), menggantikan **pip telanjang** sebagai
tampilan utama menu **INSTALL MODULES**. Pip manual (ketik nama paket) tetap
dipertahankan sebagai jalur lanjutan/power-user.

Latar: percakapan dengan user (2026-08) — menu LIBRARY ditaruh **di dalam menu
"INSTALL MODULES"** yang sudah ada, bukan menu sidebar baru. Lihat juga
`PIP_SCOPE.md` (cakupan paket) dan screenshot sidebar ZCODE.

---

## 1. Tujuan & non-tujuan

**Tujuan**
- User awam bisa menemukan & memasang paket populer tanpa harus hafal nama PyPI.
- User langsung tahu paket mana yang **jalan di HP-nya** (tag ✅/⚠️/❌).
- Install yang ❌/gagal memberi pesan **jujur & bersih**, bukan traceback.
- Tidak membengkakkan APK (TANPA bundling wheel).

**Non-tujuan**
- Bukan "semua PyPI tersedia offline". Katalog = metadata kecil terkurasi.
- Bukan repository mirror; wheel tetap diunduh on-device dari Chaquopy/PyPI.
- Bukan mengganti terminal mentah; keduanya hidup berdampingan.

---

## 2. Tata letak (berdasarkan UI yang ada)

Alur di sidebar:
```
ZCODE
INSTALL MODULES  ──tap──►  LAYAR LIBRARY
SAMPLES
TOOLS
About & Contribute
```

Layar saat **INSTALL MODULES** di-tap adalah `PipScreen.kt` (judul "Pip Package
Manager"), isinya: field "Package Name" + tombol Install + INSTALLATION LOG.

**Usulan struktur layar baru:**
```
┌─────────────────────────────────────┐
│ ‹ Back   LIBRARY                    │
├─────────────────────────────────────┤
│ [ Search packages... ]              │
├─────────────────────────────────────┤
│ KATEGORI                            │
│  ▸ Web & Networking                 │
│  ▸ Data & Sains                     │
│  ▸ Utility                          │
│  ▸ Office & File                    │
│  ▸ Automation                       │
│  ▸ Sudah terpasang (N)              │
├─────────────────────────────────────┤
│ Daftar item (pola SamplesScreen):   │
│  ✅ requests         [ Install ]    │
│     HTTP requests, ringan           │
│  ⚠️ pandas           [ Install ]    │
│     Berat di HP RAM kecil           │
│  ❌ pyzmq            [ Pasang ]     │
│     Butuh libzmq — tidak ada wheel  │
├─────────────────────────────────────┤
│ ⌨️  Install manual (ketik nama)  ▸  │  ← pip telanjang lama
└─────────────────────────────────────┘
```

- Pola 2 level (kategori → item) mengikuti `SamplesScreen.kt` agar konsisten.
- Layar pip manual (field + log) dipertahankan, diakses dari baris
  "Install manual" — **jangan dibuang** (power-user & paket di luar katalog).
- Log install memakai komponen yang sudah ada (`ExecutionEngine.startPipStream`),
  ditampilkan rapi sesuai Batch 1.5 (warna + status, bukan tumpah ruah).

---

## 3. Sumber data: `assets/libraries.json`

Bundle **metadata saja** (kecil, offline-first). Skema entri:

```json
{
  "name": "pandas",
  "category": "data",
  "kind": "native",
  "supported_abis": ["arm64-v8a", "armeabi-v7a", "x86_64"],
  "min_android": 26,
  "ram_mb_hint": 256,
  "heavy_on_low_end": true,
  "relevance": "heavy",
  "summary": "Analisis data tabular",
  "note": "Berat di RAM kecil; dataset besar bikin ngos-ngosan.",
  "install_name": "pandas"
}
```

| Field | Isi |
|---|---|
| `name` | Nama tampilan/paket |
| `category` | Kunci kategori (`web`, `data`, `utility`, `office`, `automation`, …) |
| `kind` | `pure` (murni Python) atau `native` (punya C extension) |
| `supported_abis` | Daftar ABI yang punya wheel; kosong `[]` = pure (semua ABI) |
| `min_android` | minSdk yang disyaratkan (default 26) |
| `ram_mb_hint` | Estimasi beban puncak (advisory) |
| `heavy_on_low_end` | true → bisa jadi ⚠️ di HP ampas |
| `relevance` | `recommended` / `optional` / `heavy` / `unsupported` |
| `summary` | Deskripsi singkat |
| `note` | Catatan tambahan (alasan ⚠️/❌, alternatif) |
| `install_name` | Nama pip (kalau beda dari `name`, mis. `beautifulsoup4`) |

Isi awal mengacu `PIP_SCOPE.md` §3 (~150–300 entri, diisi bertahap).
Tambah kategori cukup dengan menambah field; tidak perlu hardcode di UI.

---

## 4. Deteksi perangkat & penentuan tag (runtime)

Di Kotlin (ViewModel/Repository):

1. **Baca ABI:** `Build.SUPPORTED_ABIS` (array; biasanya `[arm64-v8a, armeabi-v7a]`
   pada HP 64-bit, atau cuma `armeabi-v7a` di HP 32-bit/Go edition).
2. **Baca RAM:** `ActivityManager.MemoryInfo.totalMem` → kelas RAM (rendah/sedang/tinggi).
   Batas usulan: <2 GB = rendah; 2–4 GB = sedang; >4 GB = tinggi (heuristik,
   **bukan garansi**).
3. **Cocokkan dengan entri:**
   - `kind == "pure"` → ✅/⚠️ murni dari RAM.
   - `kind == "native"` → bila tak ada irisan antara `supported_abis` dan
     `SUPPORTED_ABIS` → **❌ unsupported**.
   - `heavy_on_low_end && kelasRAM == rendah` → **⚠️ heavy** (walau ABI cocok).
   - `relevance == unsupported` → ❌ terlepas dari perangkat.
4. Paket yang **sudah terpasang** dideteksi lewat `pip list`/cek folder
   `user_packages`, lalu ditandai "Terpasang" + tombol Update/Copot (opsional
   fase 2; minimal tampilkan status).

> Penting: tag **advisory & jujur**. Selalu sediakan jalur "coba install tetap"
> untuk ⚠️, dan pesan jelas untuk ❌ (lihat §6).

---

## 5. Alur eksekusi install

1. User tap **Install** di item (atau ketik di layar manual).
2. Panggil **`ExecutionEngine.startPipStream`** yang sudah ada (in-process
   Chaquopy → `user_packages --target`).
3. Dengan **Chaquopy 17** (rencana), `--only-binary` jadi default → mencegah
   sdist native gagal berantakan (lihat `CHAQUOPY_STRATEGY_2026_08.md`). Pada
   15.0.1, teruskan flag eksplisit bila tersedia.
4. Stream log tampil di area log (Batch 1.5): progres, sukses, atau gagal.
5. **Penanganan gagal (BERSIH):**
   - Deteksi error klasik: tidak ada wheel untuk ABI, `ResolutionImpossible`,
     timeout, storage penuh.
   - Tampilkan **pesan ramah** + saran (lihat §6), bukan traceback utuh.
   - Bersihkan instalasi setengah jadi (rollback) agar `user_packages` tidak korup.

---

## 6. Nada pesan (jujur, tetap ramah)

Contoh:
- ❌ *"⚠ jupyter dibatalkan — pyzmq butuh libzmq native, belum ada wheel Android
  Chaquopy untuk HP ini. Mau jalanin notebook? Coba App Mode (Flask+WebView)."*
- ⚠️ *"pandas bisa jalan, tapi berat di HP RAM kecil (~1.5 GB). Lanjut?"*
- ✅ *"requests terpasang. `import requests` langsung jalan."*
- Storage: *"Penyimpanan hampir penuh — kosongkan dulu beberapa ratus MB ya."*

---

## 7. Komponen & berkas yang (diperkirakan) tersentuh

| Berkas | Peran |
|---|---|
| `assets/libraries.json` | **Baru** — katalog metadata |
| `ui/settings/PipScreen.kt` | Naik kelas jadi layar LIBRARY + akses manual |
| (baru) `ui/library/LibraryScreen.kt` | Daftar kategori/item, search, tag |
| (baru) `core/library/LibraryCatalog.kt` | Load JSON, model data |
| (baru) `core/library/DeviceProbe.kt` | Baca ABI + RAM, hitung tag |
| `core/execution/ExecutionEngine.kt` | Sudah punya `startPipStream` (dipakai ulang) |
| `WorkspaceViewModel.kt` | State katalog, status install |

Pola UI meniru `ui/samples/SamplesScreen.kt` + `core/samples/SampleLibrary.kt`
yang sudah terbukti.

---

## 8. Pengujian & UAT (sesuai protokol HATI-HATI)

1. **Sandbox:** ada/buat test yang memvalidasi `libraries.json` (JSON valid,
   field wajib ada, `install_name` tak kosong, kategori dikenal) — seperti
   `test_zcode_fase3.py` memvalidasi katalog samples.
2. **CI:** kompilasi Kotlin = hakim (sandbox tanpa JDK).
3. **UAT di HP ARMv7 (Infinix Smart 9 HD):**
   - [ ] Kategori & item tampil, search bekerja.
   - [ ] Tag benar: ✅ untuk `requests`, ⚠️ untuk `pandas`, ❌ untuk `pyzmq`.
   - [ ] Install `requests`/`numpy` sukses; log rapi; langsung bisa di-import.
   - [ ] Install `pyzmq`/`jupyter` **gagal bersih**, bukan berantakan.
   - [ ] Layar "Install manual" masih berfungsi seperti sebelumnya.

---

## 9. Tahapan (urut, per-commit kecil)

1. **Basis data:** `libraries.json` (~30–50 paket populer dulu) + test validasi.
2. **DeviceProbe** (ABI+RAM) + unit test (tanpa perangkat, pakai fake).
3. **LibraryScreen** baca-only (tag + search, tombol Install belum aksi).
4. Sambungkan Install ke `ExecutionEngine.startPipStream` + log rapi.
5. Penanganan gagal/rollback + pesan jujur.
6. Pindahkan/letakkan layar pip manual di bawah ("Install manual").
7. UAT ARMv7, lalu tambah paket bertahap sesuai `PIP_SCOPE.md`.

---

## 10. Hubungan dengan dokumen lain

- `PIP_SCOPE.md` — isi & klasifikasi paket (sumber kebenaran cakupan).
- `CHAQUOPY_STRATEGY_2026_08.md` — `--only-binary` & pin Python 3.11/ARMv7.
- `PERF_PASS.md` — agar install & log tidak memblokir UI di HP ampas.
- `docs/RENCANA_UPDATE_2026_08.md` — pola layar 2 level (Samples) & aturan UAT.

---

*Rancangan — bukan implementasi final. Semua perubahan per-commit kecil dengan
test + UAT sebelum dianggap selesai.*
