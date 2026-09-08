# UAT UPDATER CHECK-PATH v1.0.22 — BUKTI DEVICE + 1 TEMUAN BUG (2026-09-08)

**Status: EVIDENCE RECORDED (user report + telemetri + analisis kode; tanpa
perubahan kode).**
Sumber: `ZCODE_DIAGNOSTICS.txt` (export Diagnostics user, 846 baris,
08-22 → 09-08, v1.0.20→v1.0.22, Infinix ARMv7 API34) + pembacaan
`UpdateChecker.kt` di `main` @ `26bc8a0`.

## 1. Yang TERBUKTI di device (check-path updater)

- **23 UPDATE_CHECK_BEGIN** (15 auto app-start + 8 user-tap) selama 15 hari.
- Online → `UPDATE_CHECK_OK | local=1.0.22` (parse tag + compare numerik +
  never-downgrade bekerja; `UPDATE_CHECK_NEWER` belum pernah muncul karena
  belum ada versi lebih baru — sesuai ekspektasi).
- Offline → `UPDATE_CHECK_FAIL | NETWORK jaringan: Unable to resolve host
  "api.github.com": No address associated with hostname` — alasan DNS
  tercatat verbatim; **retry user pulih** (09-07 21:46 FAIL×2 → 22:12 OK).
- **Nol crash Java sepanjang log** ("(belum pernah crash Java)") — metrik
  PRD "Force close saat Run: 0" terpenuhi di lapangan, lintas 3 versi,
  20 APP_START, 26 sesi script.
- Sejarah update terlihat: v1.0.20 (08-22) → v1.0.21 (08-23 07:59) →
  v1.0.22 (08-25 15:04) — konsisten dengan alur draft-UAT-then-publish.
- 296 `WORKSPACE_FLUSH_OK` — pipeline flush hidup.
- **Belum teruji (jujur)**: seluruh jalur download→FGS→verify→install→
  receipt (`UPDATE_DOWNLOAD/FGS/INSTALL/INSTALLED` = 0 entri) — menunggu
  rilis v1.0.23.

## 2. TEMUAN BUG (nyata, dampak rendah): cache 24 jam TIDAK PERNAH terbaca

**Bukti telemetri:** 0 entri `cache local=` dari 15 auto-check; auto-check
berjarak <24 jam (08-25 15:04 → 20:29 → 08-26 00:41) semuanya tetap
menyentuh jaringan.

**Bukti kode (`UpdateChecker.kt`):**
- `readCache()` baris pertama: `val f = synchronized(lock) { cacheFile }
  ?: return null` — membaca **field statis** `cacheFile`.
- Satu-satunya penginisialisasi `cacheFile` adalah `cachePath(context)`,
  yang hanya dipanggil dari `writeCache()`.
- Akibat: **proses app baru = `cacheFile == null`** → `readCache()`
  return null **tanpa pernah membaca file dari disk** → auto-check app
  start selalu jalan ke jaringan → cache efektifnya dead code untuk
  tujuannya (hanya hidup selama satu process lifetime).

**Kontrak yang dilanggar:** docstring kode sendiri — "auto-check saat app
start BUKAN boleh menyentuh jaringan bila cache masih segar" (RFC D2).
Kelas bug: "state diasumsikan persisten lintas process restart" —
kebalikan pelajaran cacheDir best-effort.

**Dampak nyata:** benign — 1 request GitHub API ekstra per app start
(kuota unauthenticated 60/jam; pemakaian aktual jauh di bawah). Tidak
crash, tidak data loss, UI tak terganggu.

**Fix (kandidat batch v1.0.23, sesuai SKILL 6 "gabungkan perbaikan"):**
`readCache(context)` memakai `cachePath(context)` (atau inisialisasi
`cacheFile` di awal `checkLatest`); unit test mensimulasikan instance baru
membaca file cache yang dipersist instance sebelumnya; **wajib uji
mutasi** (kembalikan `?: return null` statis → test merah).

## 3. Temuan minor (kosmetik/dokumen)

1. **FAIL dobel-log**: layer jaringan log `NETWORK jaringan: <detail>` lalu
   `checkLatest` log `NETWORK` polos — dua owner untuk satu kegagalan
   (anti-pola SKILL 20 #1). Fix: hapus salah satu site.
2. **Doc drift D8**: RFC menyebut `UPDATE_CHECK_SAME`; implementasi memakai
   `UPDATE_CHECK_OK` untuk UpToDate. Cukup dicatat di errata Fase 0.

## 4. Status label (jujur)

```text
Updater check-path (cek+parse+compare+fail/retry+telemetri) : DEVICE VERIFIED (user report + telemetri)
Updater download/verify/install/receipt path                : NOT DEVICE VERIFIED (menunggu v1.0.23)
Cache 24 jam auto-check                                     : BUG DITEMUKAN — fix kandidat v1.0.23
Zero-crash metric (sepanjang log 08-22→09-08)              : TERPENUHI (user report)
```
