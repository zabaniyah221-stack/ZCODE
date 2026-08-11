# 📋 Baseline Testing ZCODE — SPEC-001 (2026-08)

Dokumen ini berisi **checklist manual di perangkat** + **skrip baseline otomatis**
untuk mengukur posisi ZCODE terhadap *SPEC-001-ZCODE-Package-and-Terminal-Reliability*.

**Kapan dipakai:** baseline aktual WAJIB diambil sebelum rollout penuh (SPEC-001
§"Gathering Results"). Setelah setiap fase implementasi, jalankan ulang dan
bandingkan — GAP harus mengecil.

Aturan tim: *honest about anything / be meticulous in everything / build for the user.*

---

## 0. Keputusan yang sudah dikunci (forum diskusi 2026-08-12)

| # | Keputusan | Nilai |
|---|---|---|
| 1 | Bentuk rilis | **Satu FAT APK universal** (armeabi-v7a + arm64-v8a + x86_64) |
| 2 | Python runtime | **3.11** (satu-satunya versi Chaquopy yang mendukung armv7; 3.12+ drop 32-bit #709) |
| 3 | Chaquopy | 17.0.0 (sudah terpasang di repo) |
| 4 | Distribusi | **GitHub Releases** (APK gratis, sideload, tanpa iklan/premium) |
| 5 | Katalog 300 | **Knowledge base** — INCOMPATIBLE/UNAVAILABLE tetap searchable + dijelaskan |
| 6 | Storage margin | **1.5× estimasi install atau 100 MB** (yang lebih besar) |
| 7 | Local wheel (.whl file) | **V1** (MVP: wheel dari PyPI/Chaquopy saja) |
| 8 | Package engine | **Self-contained** — verifikasi/hash/transaction milik ZCODE; pip 23.3.1 hanya fetch/extract |
| 9 | minSdk | 26 (Android 8.0) |
| 10 | Offline PyPI | Curated + stdlib + cache saja; full PyPI butuh internet |

Konsekuensi jujur yang diterima: device arm64 baru ber-page **16KB** bisa gagal
load wheel native lama (dibangun 4KB) → status `UNAVAILABLE` + penjelasan di
katalog; mitigasi jangka panjang = wheel builder CI ZCODE (V1).

---

## 1. Cara pakai

### 1a. Baseline otomatis (di komputer / CI)

```bash
python3 tools/baseline.py                        # tabel konsol
python3 tools/baseline.py --json docs/baseline-spec001.json   # simpan snapshot
```

Hasil baseline pertama (2026-08-12): **32 checks → 5 OK / 3 PARTIAL / 23 GAP / 1 INFO**.
Simpan snapshot ini sebagai acuan. Jangan commit hasilnya kalau belum final —
dokumen ini hanya memandu; snapshot JSON bisa di-commit saat baseline resmi.

### 1b. Baseline manual (di HP)

Ikuti checklist di §3 dan §4. Isi tabel hasil (PASS/FAIL/NA + catatan), lalu
laporkan bersama snapshot JSON. Satu run baseline manual ≈ 30–45 menit.

---

## 2. Info perangkat (isi sebelum mulai)

| Item | Isi |
|---|---|
| Merk / model HP | |
| Android version / API level | |
| ABI (armv7 / arm64 / x86_64) | |
| Page size (`adb shell getconf PAGE_SIZE`, 4096 atau 16384) | |
| RAM total | |
| Free storage (Settings → Storage) | |
| ZCODE version (dari APK) | |
| Tanggal test | |
| Nama tester | |

> Catatan: kalau `getconf PAGE_SIZE` = 16384, device termasuk kelas 16KB page —
> perhatikan hasil package native (numpy/matplotlib) di test §4.

---

## 3. Checklist TERMINAL (manual)

Target SPEC: tidak ada hard timeout, output tidak hilang, scroll 100k+ lines,
Ctrl+C 100%, full log ekspor.

| ID | Test | Langkah | Target | Hasil (PASS/FAIL/NA) |
|---|---|---|---|---|
| T1 | Hello world | Buat file `print("halo")`, ▶ Run | Output "halo" + exit code 0 | |
| T2 | `input()` lama | Script: `x = input("Nama: "); print(x)` — **biarkan menunggu > 2 menit** sebelum mengetik | Proses TIDAK mati sendiri (tidak ada timeout 120s) | |
| T3 | Long-running | Script loop `while True: print(...); sleep(1)` — biarkan 10 menit | Tetap jalan; STOP/Ctrl+C saja yang menghentikan | |
| T4 | Output besar | Loop `print(i)` 100.000 baris | Terminal tetap responsif; tidak OOM; tidak crash | |
| T5 | Output burst | `sys.stdout.write("x"*1_000_000)` cepat | Output utuh (atau tercatat di log), UI tetap mulus | |
| T6 | ANSI warna | Script print dengan `\x1b[31mmerah\x1b[0m` + bold | Warna muncul; tanpa artefak `ESC[` mentah | |
| T7 | stderr | `import sys; print("ke stderr", file=sys.stderr)` | Muncul di terminal (saat ini tergabung — catat) | |
| T8 | Ctrl+C saat `input()` | Jalankan script `input()`, tekan Ctrl+C | KeyboardInterrupt, status INTERRUPTED, exit 130 | |
| T9 | Ctrl+C CPU loop | `while True: pass`, tekan Ctrl+C | Best-effort berhenti / recovery policy (catat perilaku) | |
| T10 | Exception | Script `1/0` | Traceback jelas, status EXCEPTION, tidak menggantung | |
| T11 | Scroll 100k lines | Setelah T4, scroll ke atas → bawah | Scroll mulus tanpa jank parah | |
| T12 | App force-close | Jalankan T3, swipe app dari Recent Apps, buka lagi | App tidak crash; (harapan V1: session state jelas) | |

**Hasil terminal:** `interactive_hard_timeout_teramati = YA/TIDAK` (wajib TIDAK)
`output_hilang = YA/TIDAK` (wajib TIDAK)

---

## 4. Checklist PACKAGE (manual)

Target SPEC: install sukses = rantai verifikasi nyata; false-success = 0;
installed package survive restart + offline.

| ID | Test | Langkah | Target | Hasil (PASS/FAIL/NA) |
|---|---|---|---|---|
| P1 | Pure-Python install | INSTALL MODULES → Manual → `requests` | Install selesai, import `requests` jalan | |
| P2 | Exact version | `requests==2.32.3` | Version terpasang sesuai | |
| P3 | Constraint | `pydantic>=2,<3` | Resolusi dependency benar | |
| P4 | Native install | `numpy` | Import `numpy` + `numpy.arange(3)` jalan | |
| P5 | Matplotlib smoke | `matplotlib` lalu render Agg PNG (`savefig`) | File PNG tersimpan (target V1: smoke test otomatis) | |
| P6 | Package tidak ada | Install nama acak `xyz-not-exist-123` | Pesan jelas + status gagal — **TIDAK ada klaim sukses** | |
| P7 | Offline reuse | Install `requests`, matikan internet, RESTART app, import | Import jalan tanpa internet | |
| P8 | Restart survival | Install `numpy`, close app, buka lagi, import | Package masih ada & bisa import | |
| P9 | Uninstall | (Belum ada fitur — catat NA di baseline ini; wajib ada di V1) | Uninstall bersih | |
| P10 | Rollback | (Belum ada fitur — catat NA; wajib ada di V1) | Gagal install → environment lama utuh | |
| P11 | Storage info | Lihat layar install | Ukuran download/installed tampil (wajib di V1) | |

**Hasil package:** `false_success_teramati = YA/TIDAK` (wajib TIDAK)

---

## 5. Pencatatan KPI (isi dari hasil di atas)

| KPI (SPEC) | Target MVP | Baseline ini | Catatan |
|---|---|---|---|
| Install success (curated/tested) | ≥90% | | |
| False-success | 0% | | |
| Rollback success | ≥99% | (belum ada fitur) | |
| Post-install import verification | 100% | (belum ada fitur) | |
| Offline use setelah install | 100% tested | | |
| Interactive hard timeout | 0 | | |
| Full output loss | 0 | | |
| Terminal scroll | ≥100k lines | | |
| Ctrl+C untuk `input()` | 100% | | |
| Full log export | Ya | (belum ada fitur) | |

---

## 6. Klasifikasi failure (dipakai untuk laporan, bukan kategori tunggal)

Setiap kegagalan dilaporkan dengan kode stage:

`NETWORK` `METADATA` `RESOLUTION` `COMPATIBILITY` `DOWNLOAD` `VERIFY` `EXTRACT`
`NATIVE_LOAD` `SMOKE_TEST` `ACTIVATION` `RUNTIME` `USER_CANCELLED` `STORAGE`

Format: `[KODE] ringkasan — detail teknis — recoverable? — rollback?`

Contoh: `[NATIVE_LOAD] numpy gagal load .so di device 16KB — <log> — recoverable: no — rollback: n/a`

---

## 7. Release gate (jangan rilis kalau melanggar)

**Package:** false-success > 0 · rollback tidak reliable · installed package tidak
survive restart · TESTED package tidak bisa import offline.

**Terminal:** interactive timeout > 0 · full output hilang · Ctrl+C `input()`
gagal · terminal OOM pada stress profile (T4/T5).

---

## 8. Setelah baseline diambil

1. Simpan hasil di sini (isi kolom) + snapshot `docs/baseline-spec001.json`.
2. Laporkan ke forum: temuan tak terduga, package yang gagal, perilaku aneh.
3. Mulai implementasi Phase 0 (urutan SPEC): terminal timeout hotfix → output
   architecture → PackageEngineV2 minimum → Library 100 → Manual Install →
   300 katalog → ≥50 tested → ecosystem/wheel builder.
4. Jalankan ulang `tools/baseline.py` di tiap akhir fase; bandingkan GAP.

---

*Dokumen ini bagian dari proses SPEC-001. Skrip pendamping: `tools/baseline.py`.*
