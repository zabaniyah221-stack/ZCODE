# ZCODE v1.0.23 — Editor Intelligence + Preview Hasil

> Candidate notes. This version is **not released** until the exact
> production draft passes update-in-place UAT on the target ARMv7 device.
> Rencana & keputusan: `docs/RENCANA_KERJA_V1023_2026_09_08.md` (APPROVED),
> `docs/RFC_V1023_SPIKE_INTELLIGENCE.md`.

## Fokus

v1.0.23 membuat editor ZCODE **paham Python** — dan menjadikan hasil kerja
terlihat. Empat perubahan utama + dua perbaikan:

1. **Lint semantik pre-Run (pyflakes)** — kesalahan paling umum Python
   (`undefined name`, `unused import`, menimpa builtin) ditandai di banner
   Problems + gutter SEBELUM Run. Menambah di belakang Checker bawaan
   (fail-open: tanpa pack, pemeriksaan dasar tetap jalan).
2. **Autocomplete scope-aware di-un-mute** — nama variabel/fungsi lokal
   (scope-aware) + keyword & globals resmi kini ikut menawarkan diri.
   Temuan: sumber bawaan CodeMirror ini sejak lama ter-bundel tapi
   ter-bisukan konfigurasi `override`. Instan, tanpa install apa pun.
3. **Complexity Report (mccabe)** — palette baru: skor kompleksitas per
   fungsi + maintainability index; tap nama fungsi → lompat ke baris.
4. **Preview hasil gambar** — script yang menyimpan PNG/JPG selama Run
   (mis. `plt.savefig`) kini mendapat kartu "Hasil gambar dari run ini"
   di bawah output terminal; tap → dialog preview (downsampled, RAM-safe).
5. **Fix updater** — cache 24 jam kini benar-benar terbaca lintas process
   (bug ditemukan via telemetri device; auto-check tak lagi selalu menyentuh
   jaringan) + telemetri FAIL satu owner.
6. **About & Contribute dirapikan (Model B)** — lisensi (GPLv3 / NOTICE
   ZABACODE / MIT) kini baris expandable berisi teks penuh dari aset APK,
   bisa disalin, copy English, chip tagline + versionCode tampil.

Plus: pack **Editor Intelligence** di INSTALL MODULES (jedi/pyflakes/mccabe,
opsional, EXPERIMENTAL), 2 sample tur baru (Code Health Checkup, Autocomplete
Tour), total 39 sample.

## Cara pakai fitur lint (ringkas)

Tanpa pack: banner Problems memeriksa kurung/string/indent (seperti
sebelumnya). Dengan pack "Editor Intelligence" terpasang: buka file ber-
typo → berhenti sebentar (debounce) → masalah semantik muncul di banner +
gutter; tap → lompat ke baris. Eksperimen cepat: sample **Code Health
Checkup** (kategori Basics).

## Tidak termasuk (non-goals)

Type checker in-app (mypy/pyright/ty — tanpa wheel android yang sah); style
checker (noise untuk pemula); rope rename (ditunda — rewrite fail-closed
belum dibuat); autocomplete popup jedi live di WebView (menunggu angka
gerbang device; engine siap); terminal/shell (v1.0.24).

## Kontrak update-in-place

```text
source APK      : ZCODE v1.0.22 / versionCode 25
candidate APK   : ZCODE v1.0.23 / versionCode 26
applicationId   : com.zaba.zcode (tetap)
production SHA  : 401392193b734263c8ecce93e12be1f7f307203afe4282dc2550094088f38bd2
uninstall       : TIDAK
clear app data  : TIDAK
```

## Checklist UAT (perangkat: INFINIX X6532C, ARMv7, Android 14)

**A — Fitur v1.0.23**
1. Sample "Code Health Checkup" (Basics): hapus satu tanda kutip → banner
   Problems merah muncul tanpa Run; Undo memulihkan.
2. Pasang pack Editor Intelligence (INSTALL MODULES) → typo nama variabel
   → "undefined name" muncul di banner; catat angka SPKE_LINT_MS di
   Diagnostics (gerbang: ≤1500 ms utk file ~500 baris).
3. Ketik nama fungsi sendiri di file baru → autocomplete menawarkan
   (scope-aware, tanpa pack).
4. Palette → "Complexity Report (mccabe)" → daftar fungsi; tap → lompat.
5. Run sample "Bar Chart" (matplotlib) → kartu "Hasil gambar dari run ini"
   muncul → tap → preview tampil.
6. About & Contribute: 3 baris lisensi bisa di-expand DAN disalin
   (long-press); dua orientasi.
7. **Verifikasi fix cache**: buka app 2× dalam <24 jam (online) →
   Diagnostics: auto-check kedua harus menulis `UPDATE_CHECK_OK | cache
   local=1.0.23` (BUKAN cek jaringan lagi).

**B — Updater full-path 25→26 (UAT PERTAMA fitur update)**
1. Sentinel: 1 file terbuka (isi unik A), 1 tertutup (isi unik B), 1
   setting diubah, 1 paket lama importable.
2. "Cek Update" → tawaran v1.0.23 → **Download & Update** → notifikasi FGS
   muncul → pindah ke app lain ±1 menit → kembali: unduhan lanjut.
3. Selesai → READY → "Install now" → dialog system → install in-place.
4. Setelah restart: versionCode 26, sentinel utuh, Diagnostics
   `UPDATE_INSTALLED from 25 to 26`.
5. Jalur batal: batal di dialog system → PENDING → "Install now" dari
   cache. 6. Offline saat cek/unduh → FAILED jujur + Retry works.
7. (Opsional, disarankan) `adb logcat -b crash` dari laptop menyala
   sepanjang proses.

## Status evidence

Saat notes ini ditulis (kandidat, sebelum production):

```text
Implementation                : IMPLEMENTED LOCALLY (13 commit sesi, ber-gate)
Local gate                    : LOCALLY VERIFIED (tools/check.sh hijau — 719 test)
Engine deps-asli (host py3.13): LOCALLY VERIFIED (pyflakes/mccabe/jedi/parso path asli)
Kotlin compile                : CI = hakim kompilasi (sandbox tanpa JDK/SDK)
JVM unit tests baru           : IMPLEMENTED — eksekusi CI (testDebugUnitTest) menunggu push
Browser harness bundle baru   : NOT RUN (disarankan pra-rilis; laptop user bisa)
Production signed draft       : NOT CREATED
Physical ARMv7 UAT            : NOT DEVICE VERIFIED
Updater full-path 25->26      : NOT DEVICE VERIFIED — pengujian nyatanya adalah siklus update INI
Public v1.0.23 release        : NOT RELEASED
```

Batas jujur: perf jedi di ARMv7 belum terukur (gerbang keep/tune/kill by
angka); harness bionic311 direkomendasikan pra-rilis; skip-lint-saat-Run
belum diimplementasi (mitigasi debounce+budget — iterasi T2); dep install
di CI check job menunggu sentuhan user di `.github/workflows/build.yml`.
