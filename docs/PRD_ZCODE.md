# 📘 PRD — ZCODE

**Status: BLUEPRINT — bukan kontrak.**
Dokumen ini boleh diubah kapan saja: ada permintaan fitur baru, bug, error, atau
temuan riset yang mematahkan asumsi di sini. Kalau kenyataan bertentangan dengan
PRD, **kenyataan yang menang** dan PRD diperbarui — bukan sebaliknya.

Versi saat ini: `1.0.1` (`gradle.properties` = sumber tunggal)
Terakhir diperbarui: 2026-08-13

---

## 1. Apa itu ZCODE

IDE Python untuk Android, setara Pydroid/Acode/VS Code mobile, dengan tiga
komitmen yang tidak bisa ditawar:

1. **100% gratis, tanpa premium lock.** Tidak ada fitur di balik bayaran.
2. **Offline-first, bukan offline-only.** Jalan tanpa internet; script yang
   butuh jaringan tetap bisa jalan.
3. **ARMv7 kelas satu.** HP murah bukan warga kelas dua.

### Kenapa ketiganya penting

Pydroid menaruh PyTorch di balik paywall
([Grokipedia — Mobile Python](https://grokipedia.com/page/Mobile_Python)).
PocketCode membuang jaringan sepenuhnya
([pocketcodeapp.com](https://www.pocketcodeapp.com/en/blog/python-on-android)).
VSCodroid hanya ARM64
([github.com/rmyndharis/VSCodroid](https://github.com/rmyndharis/VSCodroid)).

Ketiga komitmen ZCODE adalah **ceruk yang secara harfiah kosong**.

### Pengguna utama

Satu orang, dan itu menentukan semua keputusan teknis:

- HP **ARMv7**, Android 12, ~6.6 GB free
- **TIDAK punya PC.** Tidak ada `adb logcat`, tidak ada Android Studio.
- Siklus uji **mahal**: 1 build CI + 1 unduh + 1 install per percobaan
- Merangkap QA tester tunggal

**Konsekuensi wajib:** semua diagnostik harus bisa dibaca **di dalam aplikasi**,
dan semua teks harus **bisa disalin**. Tebakan tidak boleh dikirim satu per
satu — gabungkan dalam satu build.

---

## 2. Yang sudah jalan

| Area | Status |
|---|---|
| Editor CodeMirror 6, `file://` murni, offline | ✅ |
| Multi-file, tab, autosave 600ms | ✅ |
| Run Python (Chaquopy 3.11 in-process) | ✅ |
| Terminal output + `input()` + Ctrl+C | ✅ |
| Samples (pola 2 level) | ✅ |
| Tema, font, true-black `#050806` | ✅ |
| Breadcrumb + Crash Reporter | ✅ (cakupan masih sempit) |
| **Install Modules** | ❌ **rusak — 4 bug** |

---

## 3. Fondasi teknis & alasannya

| Keputusan | Alasan |
|---|---|
| **Chaquopy 3.11** | satu-satunya runtime dengan wheel native ARMv7 Android |
| **JANGAN naik ke 3.12+** | Chaquopy berhenti bangun 32-bit → ARMv7 kehilangan numpy |
| minSdk 26, ARMv7+ARM64+x86_64 | jangkauan HP lama |
| CodeMirror 6 di WebView `file://` | tanpa CDN, tanpa loopback HTTP |
| Alpine + PRoot (rencana) | pola yang sama dengan Acode |

### Fakta keras yang membentuk strategi

`armeabi_v7a` **tidak ada** dalam daftar arsitektur cibuildwheel
([cibuildwheel options](https://cibuildwheel.pypa.io/en/stable/options/)).
PEP 738 hanya menetapkan `arm64_v8a` dan `x86_64`.

⇒ **Indeks Chaquopy adalah satu-satunya sumber wheel native ARMv7 Android di
dunia.** Bukan PyPI, bukan piwheels, bukan cibuildwheel.

⇒ **Python 3.11 + Chaquopy = jendela terakhir.** Menaikkan versi Python
menutupnya permanen.

Bukti survei (2026-08-13, dari <https://chaquo.com/pypi-13.1/>):

| Paket | Versi cp311 ARMv7 |
|---|---|
| numpy | 1.26.2 |
| pandas | 1.5.0 |
| pillow | 9.2.0 |
| matplotlib | 3.6.0 |
| lxml | 5.3.0 |
| cryptography | 42.0.8 |
| pyyaml | 6.0.3 |
| psutil | 7.1.3 |
| **scipy** | **TIDAK ADA — berhenti di cp310** |

---

## 4. Arsitektur dua backend

Bukan mengganti Chaquopy — **menambah**.

| | Chaquopy | Alpine/PRoot |
|---|---|---|
| Startup | instan | boot rootfs |
| Hentikan script | ❌ in-process | ✅ proses terpisah |
| Paket | wheel Chaquopy | `apk` + pip asli |
| scipy | ❌ mustahil | ✅ |
| Ukuran | sudah ada | +2 MB APK, rootfs diunduh |

**Default Chaquopy** (mayoritas script kecil). Alpine untuk yang butuh scipy
atau harus bisa dihentikan. Alpine **opsional** — tidak dipasang = ZCODE normal.

⚠️ Jangan kompilasi PRoot di CI (butuh NDK, sudah diperingatkan sebagai risiko
E-03 di `BUGS_AUDIT_ZABACODE_FOR_ZCODE.md`). Ambil `.so` jadi dari ZMUX.

---

## 5. Bug aktif

Semua sudah dibuktikan dengan menjalankan datanya, bukan dugaan.
Detail lengkap: `RENCANA_BUILD_2.md`.

| ID | Bug | Lokasi |
|---|---|---|
| A | `requires_python` dibanding versi **paket** | `resolve.py:114` |
| B | `optString` → `""` bukan `null` | `DependencyResolver.kt:99` |
| C | `stdlib.json` tidak pernah dibaca | `resolve.py` |
| D | versi disortir **alfabetis** | `wheelinfo.py:119` |
| E | terminal kosong — **regresi build #1** | `OutputBatcher.kt` |
| F | wheel `manylinux` tidak ditolak → SIGSEGV | `wheelinfo.py` |
| G | cache wheel tidak dipisah per-ABI | `Paths.kt:41` |
| H | WebView cold-start belum dijaga | `EditorScreen.kt` |
| I | **tidak ada teks yang bisa disalin** | seluruh UI |
| J | breadcrumb hanya 7 dari 49 file | — |

---

## 6. Roadmap

### Build #2 — installer jalan
A–J + ralat `tested-manifest.json` (`numpy 1.26.4`→`1.26.2`,
`pillow 10.3.0`→`9.2.0`; kedua versi lama tidak ada di Chaquopy).

**Selesai bila:** `print()` tampil · colorama/requests/rich terpasang ·
`math` menjawab "sudah tersedia" · semua teks bisa disalin.

### Build #3 — numpy hidup + UI diagnostik
- Perbaikan tag Android → numpy, pandas, pillow, matplotlib
- **EDITOR HANDLE** — ganti nama SYMBOL BAR; satu baris scrollable
  ("kereta") dengan `^C` dipaku di pinggir ("terowongan"), ukuran & bentuk
  sama, warna merah. Adopsi **sticky CTRL** + **hold-to-repeat**
  (400ms/55ms) dari `ZMUX/ZmuxKeys.kt`
- **DIAGNOSTICS** layar penuh di sidebar, tab Semua/Run/Install/File/Crash
- Export Log pindah ke Diagnostics + daftar run lama + rotasi 50 file

### Build #4 — Alpine
Terminal shell di sidebar · script bisa dihentikan · `apk add py3-scipy` ·
tombol ZMUX yang butuh PTY baru diaktifkan di sini

### Build #5 — Library "perpustakaan mini"
50 entri bertahap, pola SAMPLES 2 level, prosa ditulis tangan.
Vonis: 🟢 sudah diuji · 🔵 bisa dipasang · 🟠 eksperimental · 🔴 tidak bisa.

---

## 7. Yang TIDAK akan dikerjakan

Ditolak dengan bukti. Jangan diusulkan ulang tanpa premis baru.

| Ditolak | Alasan |
|---|---|
| **Pyodide / WASM** | tanpa socket/threading; PocketCode mengonfirmasi "no internet from a script" |
| **CPython PEP 738 mandiri** | ARMv7 bukan tier 3, hanya best-effort |
| **Membuang Chaquopy** | satu-satunya sumber wheel ARMv7; startup instan berharga |
| **Python 3.12+** | mematikan numpy di ARMv7 |
| **Repo wheel sendiri** | beban pemeliharaan selamanya; repo Pydroid & QPython saling tidak kompatibel |
| **Wheel `manylinux_armv7l`** | glibc ≠ bionic → SIGSEGV |
| **Premium / paywall** | melanggar komitmen inti |
| **3 terminal terpisah** | VS Code, Acode, Pydroid semuanya 1 terminal |
| **scipy di ARMv7 Chaquopy** | tidak ada cp311. Titik. |

---

## 8. Batas jujur yang harus dikatakan ke pengguna

Bukan disembunyikan di FAQ:

1. **scipy/scikit-learn mustahil di ARMv7** lewat Chaquopy
2. **Script belum bisa dihentikan paksa** sampai build #4
3. **Alpine perlu unduh sekali** (100–200 MB)
4. **PRoot lambat** — `apk add` bisa 20 detik vs <1 detik native
5. **Wheel bisa lolos tag tapi gagal `import`** — karena itu ada smoke test + rollback

---

## 9. Definisi selesai

Sebuah fitur selesai bila:

- [ ] `tools/check.sh` hijau
- [ ] tiap bug punya guard yang **terbukti bisa gagal** (uji mutasi)
- [ ] CI hijau
- [ ] **diverifikasi di HP ARMv7 nyata**
- [ ] batas jujurnya sudah ditulis

Poin ke-4 tidak bisa diganti dengan "test hijau". Sandbox tidak punya
JDK/Android SDK — kode Kotlin **tidak pernah dikompilasi** sebelum CI.

---

## 10. Metrik

| Metrik | Target |
|---|---|
| Force close saat Run | **0** |
| Install paket pure-Python | > 90% |
| Test lokal | 100% hijau |
| Bug ditemukan asisten sebelum user | mayoritas |
| Fitur di balik paywall | **0, selamanya** |

---

## Sumber

- <https://chaquo.com/pypi-13.1/> — survei wheel ARMv7, 2026-08-13
- <https://cibuildwheel.pypa.io/en/stable/options/> — arch Android resmi
- <https://peps.python.org/pep-0738/> — Android tier 3
- <https://github.com/chaquo/chaquopy/issues/1237> — scipy berhenti di 3.10
- <https://acode.app/faqs> — Alpine+PRoot di produksi
- <https://grokipedia.com/page/Mobile_Python> — Pydroid paywall
- <https://www.pocketcodeapp.com/en/blog/python-on-android> — batas WASM
- <https://github.com/rmyndharis/VSCodroid> — ARM64-only, crash loop WebView
