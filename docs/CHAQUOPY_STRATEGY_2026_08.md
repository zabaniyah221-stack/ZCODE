# 🐍 Strategi Chaquopy ZCODE — Riset & Rekomendasi (2026-08)

Dokumen ini **mengarsipkan hasil riset** tentang cakupan Chaquopy, alternatifnya,
dan versi terbaru yang layak untuk visi ZCODE. Dibuat dari diskusi "mulai dari
awal" (peraturan tim: *honest about anything / be meticulous in everything*).

Tujuan: supaya keputusan upgrade runtime Python tidak hilang dan kontributor
paham *kenapa* kita di posisi ini. Dua aturan kerja batch ini berlaku di sini
sama seperti di `docs/RENCANA_UPDATE_2026_08.md`.

---

## 0. Status ZCODE saat ini (fakta dari `build.gradle.kts` + `app/build.gradle.kts`)

| Item | Nilai | Sumber |
|---|---|---|
| Plugin Chaquopy | **15.0.1** | `build.gradle.kts` |
| Python runtime | **3.11** (`version = "3.11"`) | `app/build.gradle.kts` |
| pip di-bundle | **23.3.1** (`install("pip==23.3.1")`) | `app/build.gradle.kts` |
| minSdk | 26 | `app/build.gradle.kts` |
| AGP / Kotlin | 8.2.2 / 1.9.22 | `build.gradle.kts` |
| abiFilters | `arm64-v8a + armeabi-v7a + x86_64` | `app/build.gradle.kts` |

Komentar di `app/build.gradle.kts` secara eksplisit: *"version 3.11:
satu-satunya yang masih mendukung armeabi-v7a (HP user)"* dan *"pip 24+ crash di
Chaquopy karena importlib.metadata memindai (bug AssetPath.parent)"*.

---

## 1. Cakupan wheel / pip Chaquopy — seluas apa?

- **Pure-Python = praktis 100% PyPI.** Sejak Chaquopy 3.1.0 (2018) dia bisa
  menginstal *sdist* pure-Python, sehingga semua package murni-Python jalan walau
  tidak ada wheel (lihat changelog resmi Chaquopy).
- **Native (C extension: NumPy, Pillow, scikit-image, OpenCV, dll) = butuh Android
  wheel** yang Chaquopy bangun sendiri di repo `https://chaquo.com/pypi-13.1/`
  (sejak 15.0.1 menggantikan pypi-7.0). NumPy didukung sejak 0.6.1. Cakupannya
  **besar tapi bukan total** — package populer ada, yang niche/baru belum tentu.
- Di ZCODE, pip berjalan **runtime in-process** ke `user_packages` (`--target`),
  menarik dari index wheel Android Chaquopy + PyPI. Jadi coverage =
  pure-Python (semua) + native (wheels Chaquopy, subset luas tapi tak lengkap).
- Warning *"may have fewer packages available"* sudah **dicabut di 16.1.0**
  karena default Python tak lagi punya keunggulan pemilihan package.

**Kesimpulan cakupan:** untung utama ZCODE = package murni-Python apa pun bisa
di`pip install` on-device; package native bergantung pada wheels yang sudah
dibangun Chaquopy (NumPy dkk tersedia). Ini cukup untuk misi "IDE Python di HP
ampas".

---

## 2. Selain Chaquopy — ada yang relevan?

Python resmi menyebut 5 tools utama: Briefcase (BeeWare), Buildozer (Kivy),
**Chaquopy**, pyqtdeploy, Termux (docs.python.org/3/using/android.html).
Tapi untuk **arsitektur ZCODE** (Python di-embed ke app Kotlin/Compose,
in-process, jembatan Java↔Python, menjalankan script user) → **Chaquopy adalah
praktis satu-satunya SDK embedding**. Lainnya:

- **BeeWare / Briefcase** — bangun app Python native (compile ke JVM via VOC).
  Berarti ngebongkar ZCODE jadi app Python → Kotlin/Compose hilang.
  Sudah dibahas di `PLAN_ZCODE.md` ("Kenapa tidak tetap Buildozer/WebView Flask?").
- **Kivy / Buildozer / python-for-android** — APK standalone ber-GUI Python. Ini
  rute **ZPLAY** (fork untuk pygame/kivy di `RENCANA_UPDATE_2026_08.md`), tidak
  bisa di-embed ke app Kotlin yang ada.
- **Termux** — environment Linux POSIX penuh. Ini rute **ZMUX** (download-on-demand),
  jalan terpisah.
- **QPython / SL4A** — IDE/script-engine on-device; kompetitor, bukan SDK untuk
  diintegrasikan.
- **PySide/Qt / pyqtdeploy** — binding Qt, dukungan Android masih preliminary.

**Kesimpulan jujur:** buat visi ZCODE (IDE Python di dalam app Kotlin), Chaquopy
adalah pilihan satu-satunya yang masuk akal. Alternatif = (a) buang codebase
Kotlin, atau (b) ZMUX tetap terpisah (download-on-demand) — **persis keputusan
"hukum keluarga" yang sudah dikunci** (lihat `README.md` roadmap: *"Alpine proot
terminal — Zmux pending, tidak dibundle"* dan `PLAN_ZCODE.md` Q4/Fase 3).

---

## 3. Versi terbaru & layak untuk visi ZCODE

**Terbaru = Chaquopy 17.0.0 (2025-12-01).** Runtime Python yang didukung:
**3.10.19, 3.11.14, 3.12.12, 3.13.9, 3.14.0.**

Yang 17.0.0 bawa:

- ✅ **pip sekarang default `--only-binary`** (#981) → ini **otomatis mewujudkan
  sebagian Tier 0-A** (rencana menambah `--only-binary=:all:`). Efek samping:
  package yang hanya ada sdist (native tak ber-wheel) tidak ter-install dari PyPI
  lagi (pure-Python lokal tetap OK).
- ✅ pip naik ke **25.3**, CA bundle certifi 2025.8.3 (security lebih segar).
- ✅ **16 KB page devices** didukung (syarat Android 15+) — tapi wheel lama
  (&lt;Okt 2024) gagal di 16KB; pakai Py 3.13+ untuk kompat terbaik.
- ✅ Bundling non-Python lib di wheel makin kompatibel (#892/#1383/#1374).
- ✅ AGP 7.0–7.2 di-drop (AGP 8.2.2 ZCODE aman); minSdk now ≥24 (minSdk 26 aman).

### ⚠️ CAWAT KRITIS — device ampas

- **armeabi-v7a & x86 (32-bit) DI-DROP mulai Python 3.12+** (#709). Infinix Smart
  9 HD (HP user) = **ARMv7 (armeabi-v7a)**. Naik ke **Python 3.12+ = bunuh HP user**.
- TAPI Chaquopy 17 **MASIH mendukung Python 3.11.14**, dan 3.11 masih punya
  armeabi-v7a. Maka: **pin Python 3.11 di Chaquopy 17** → dapat semua untung
  17.0.0 + ARMv7 tetap hidup.
- Hindari **3.10 default** (3.11 lebih banyak package + tetap 32-bit, sesuai
  komentar `app/build.gradle.kts`). Hindari **3.12+** (drop 32-bit + masih
  "fewer packages"). Hindari **3.14** (wheel Android masih sangat sedikit).

> **Catatan kepatuhan:** keputusan "Chaquopy 3.11, bukan 3.12" di `PLAN_ZCODE.md`
> (Q2) **SUDAH BENAR** dan riset ini mengonfirmasinya. Yang belum tercatat di
> plan = bahwa **plugin boleh di-bump 15.0.1 → 17.0.0** selama
> `pythonVersion "3.11"` tetap di-pin.

---

## 4. Rekomendasi (teliti & jujur)

**Upgrade 15.0.1 → 17.0.0, TAPI PIN `pythonVersion "3.11"`** (jangan terima
default 3.10, apalagi 3.12+). Ini:

- Dapet pip 25.3 + `--only-binary` default → sebagian **Tier 0-A** kejalan otomatis.
- Selamatkan **armeabi-v7a** (misi device ampas utuh).
- Dapet **16 KB page** + security terbaru (future-proof Android 15+).
- **Wajib diverifikasi (bukan ditebak)** — daftar risiko spike (sama seperti
  rencana "Spike Chaquopy 17 (tetap Py 3.11)"):
  1. Monkey-patch `AssetPath.parent` di `zcode_pip.py` masih perlu atau malah
     redundant di pip 25.3?
  2. `sys.stdin` / `input()` TerminalBridge (#1083) — taruhan nyawa, UAT ulang.
  3. AGP 8.2.2 vs 17.0.0 (harus hijau; hanya 7.0–7.2 yang di-drop).
  4. Build artifact di HP **ARMv7 asli** (Infinix Smart 9 HD).

### Protokol eksekusi (konsisten "HATI-HATI")

- Spike = **SATU commit terisolasi** (15.0.1→17.0.0 + pin 3.11). Gagal?
  `git revert` satu SHA, bersih.
- Timing: **setelah Batch 3 stabil** (tidak sekarang, tidak bareng fitur) — biar
  kalau ada yang patah, pelakunya jelas.

---

## 5. Pemetaan ke roadmap / plan ZCODE

| Roadmap / Plan | Hubungan dengan riset ini |
|---|---|
| `PLAN_ZCODE.md` Q2 (Chaquopy 3.11 bukan 3.12) | **Dikonfirmasi** — riset sejalan 100% |
| Tier 0-A (`--only-binary`) | Chaquopy 17 membuatnya **default** → sebagian otomatis selesai |
| Spike Chaquopy 17 (Batch setelah 3) | Rekomendasi di §4 = implementasi spike ini |
| `README.md` roadmap: *"Alpine proot terminal — Zmux pending, tidak dibundle"* | Tetap di luar Chaquopy; ZMUX terpisah (download-on-demand) — tidak konflik |
| `README.md` roadmap: *"LSP Python (jedi)"* | Jedi jalan via Chaquopy in-process (tidak butuh upgrade ini) |

---

## 6. Sumber

- Chaquopy 17.0.0 release notes — https://chaquo.com/chaquopy/chaquopy-version-17-0-0/
- Chaquopy news/changelog — https://chaquo.com/chaquopy/news/
- Chaquopy change log (pure-Python sdist sejak 3.1.0) — https://chaquo.com/chaquopy/doc/current/changelog.html
- Python docs — official Android tools list — https://docs.python.org/3/using/android.html
- Tools to run Python on Android (alternatives) — https://sqlpey.com/python/python-execution-on-android/

---

*Dokumen arsip — bukan keputusan final. Eksekusi spike mengikuti protokol §4.*
