# 🧠 SKILLS — panduan wajib untuk siapa pun yang mengerjakan ZCODE

Ditulis untuk agent/kontributor berikutnya. Isinya bukan teori — semuanya
pelajaran yang **dibayar dengan bug nyata** di proyek ini.

Baca ini sebelum menyentuh kode.

---

# ⚖️ DUA PERATURAN DI ATAS SEGALANYA

Melanggar keduanya = pekerjaan ditolak, seberapa pun benar teknisnya.

## Peraturan #1 — HONEST ABOUT ANYTHING

Jujur dalam segala hal: kelemahan, keterbatasan, kekurangan — milik siapa pun,
termasuk milikmu sendiri.

**Wajib:**
- Katakan yang **tidak** diperbaiki, bukan hanya yang diperbaiki
- Akui bug yang **kamu** sebabkan, dengan jelas
- Bedakan **terverifikasi** vs **dugaan**. "Saya belum bisa memastikan" itu
  jawaban sah
- Nyatakan keyakinan dalam angka (~85%), jangan "pasti bisa"
- **Setiap keputusan berdasarkan sumber eksternal WAJIB mencantumkan URL-nya**
- Kalau riset baru mematahkan keputusan lamamu, **katakan** dan perbaiki

**Contoh nyata dari proyek ini:**

> "⚠️ Ini bug yang saya buat sendiri di build #1 saat memindahkan
> `batcher.start()` ke `DisposableEffect`. Sebelumnya tidak ada."

> "Saya pernah bilang pyyaml tidak punya wheel. **Salah.** Saya hanya mengecek
> PyPI dan tidak mengecek indeks Chaquopy."

> "~~Token melaporkan `push:false`~~ → **TERBANTAH**: push nyata berhasil."

Jangan menghaluskan. User tidak butuh dihibur, dia butuh tahu.

## Peraturan #2 — BE METICULOUS IN EVERYTHING

Teliti tentang apa pun, sekecil apa pun, untuk meminimalkan edge case.

**Turunan yang wajib dijalankan:**

- **Setiap error CI menjadi test guard permanen**, diverifikasi lewat **uji
  mutasi**: kembalikan bug → test **harus merah**; pulihkan → hijau.
  *Guard yang tidak pernah bisa gagal adalah guard palsu.*
- Perbaiki **kelasnya**, bukan satu kejadiannya. Import `getValue` hilang di
  satu file → guard memindai **semua** file.
- Jangan menebak API. `optString` dikira mengembalikan `null` → bug B.
- Jalankan datanya. Jangan bilang "seharusnya begini".
- Baca kode di sekitar yang kamu ubah.

---

# 🔧 SKILL 1 — Diagnosis tanpa bisa melihat

Kondisi nyata proyek ini:

| Tidak tersedia | Akibat |
|---|---|
| JDK/Gradle/Android SDK di sandbox | **Kotlin tidak pernah dikompilasi sebelum CI** |
| `adb logcat` (user tanpa PC) | tidak ada log sistem |
| Isi log CI | `gh api .../logs` selalu `EOF` (blob storage diblokir) |

**Yang tetap bisa:** daftar run, conclusion per job, **nama step yang gagal**,
daftar artifact.

### Prosedur saat CI merah

1. `gh api repos/<owner>/<repo>/actions/runs/<id>/jobs` → job mana?
2. `.steps[] | select(.conclusion=="failure")` → **step mana?**
3. Kalau job `check` hijau tapi `build` merah ⇒ murni kegagalan kompilasi
4. Pindai pola kesalahan di file yang **baru diubah**
5. Perbaiki → jadikan guard → uji mutasi

**Kisah nyata:** step 9 "Build Debug APK" gagal. Log tidak bisa diunduh.
Ditemukan sendiri dalam 3 menit: `var x by remember {...}` tanpa
`import androidx.compose.runtime.getValue`. Guard baru memindai **semua** file
Compose, bukan hanya yang rusak.

### Alat pengganti kompiler

```bash
bash tools/check.sh                  # 244 test
python3 tools/kotlin_sanity_check.py # mini-lexer 49 file
```
Plus pemindai import manual sebelum push. Tidak sempurna, tapi menangkap
mayoritas.

---

# 🧪 SKILL 2 — Menulis guard yang benar

### Uji mutasi itu wajib

```bash
# 1. tulis guard, jalankan → HARUS MERAH pada kode yang masih bug
# 2. perbaiki kode → HIJAU
# 3. kembalikan bug → HARUS MERAH lagi
# 4. pulihkan → HIJAU
```

Kalau langkah 3 tidak merah, guard itu **palsu**. Hapus, tulis ulang.

### Jebakan yang sudah memakan korban

**Komentar memicu false positive.** Guard yang mencocokkan pola kode WAJIB
memakai `strip_kt_comments()` — komentar yang mendeskripsikan bug lama akan
cocok dengan regex.

**Guard berbasis indentasi rapuh.** Pakai konteks blok.

**Jangan mem-pin versi literal.** `assert version == "1.0.1"` akan gagal saat
naik versi. Cek **konsistensi/format**, bukan angka.

---

# 📱 SKILL 3 — Fakta Android yang menentukan segalanya

### `org.json.JSONObject.optString()` TIDAK mengembalikan null

```kotlin
p.optString("x")                                  // "" kalau tidak ada — BUG
if (p.isNull("x")) null else p.optString("x")     // benar
```
Ini bug B. Berlaku untuk **semua** field opsional.

### glibc ≠ bionic

| | Linux ARMv7 | Android ARMv7 |
|---|---|---|
| libc | glibc | **bionic** |
| tag | `manylinux_armv7l` | `android_21_armeabi_v7a` |

Wheel `manylinux_armv7l` di Android = **SIGSEGV**, tidak bisa ditangkap
`try/except`. Selalu tolak eksplisit.

### `sys.settrace` sebagai watchdog: DICABUT

Overhead 2–5×. Jangan diusulkan ulang.

### Chaquopy berhenti membangun wheel 32-bit di Python 3.12+

Sumber: <https://chaquo.com/chaquopy/chaquopy-version-17-0-0/>

**Menaikkan versi Python akan membunuh numpy di ARMv7.** Jangan lakukan.

---

# 🎨 SKILL 4 — Jebakan Jetpack Compose

### Delegasi `by` WAJIB di-import

```kotlin
var x by androidx.compose.runtime.remember { ... }   // ❌ CI MERAH
```
Nama berkualifikasi penuh **tidak** menggantikan `import getValue`/`setValue`.

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
var x by remember { mutableStateOf(...) }            // ✅
```

### Side-effect tidak boleh telanjang di badan composable

```kotlin
batcher.start()                          // ❌ thread yatim
DisposableEffect(batcher) {              // ✅
    batcher.start()
    onDispose { batcher.close() }
}
```

⚠️ **Tapi periksa idempotensi objeknya.** Perbaikan di atas justru melahirkan
**bug E**: `OutputBatcher.running` tidak pernah di-reset di `start()`, jadi
setelah `close()` batcher hidup tapi tuli — semua output dibuang tanpa error.

**Pelajaran:** setelah memindahkan sesuatu ke `DisposableEffect`, tanyakan
"apakah objek ini benar-benar bisa di-start ulang?"

### Key LazyColumn: kalau ragu, hapus

```kotlin
item(key = { -1L })                      // ❌ mengirim LAMBDA, bukan -1L
items(n, key = { buffer.startOffset+it }) // ❌ startOffset bergeser saat trim
items(n) { ... }                          // ✅ append-only tidak butuh key
```
Key non-savable → `IllegalArgumentException` saat save-state = force close.

### `requestFocus()` sebelum node ter-place

```kotlin
LaunchedEffect(Unit) {
    withFrameNanos { }                              // tunggu 1 frame
    runCatching { focusRequester.requestFocus() }   // jangan mematikan app
}
```

### Nama variabel lokal bisa menutupi fungsi

Variabel lokal bernama `error` membuat `error("...")` (`kotlin.error`) ambigu.
Sudah dihindari di `PyCall.kt` — jangan dikembalikan.

---

# 🌐 SKILL 5 — Riset yang jujur

### Selalu cantumkan URL

Setiap klaim dari luar wajib bersumber. Bukan formalitas — di proyek ini riset
sudah **membalik keputusan** beberapa kali.

### Verifikasi klaim, jangan percaya README

**Kisah nyata:** README ZMUX menulis *"Verified on a real ARMv7 device"*,
sementara `ROADMAP_STATUS.md` di repo yang sama menulis *"No device testing"*.
Dua dokumen bertentangan. Yang benar hanya bisa dijawab user.

Kalau menemukan kontradiksi: **katakan**, jangan pilih yang enak didengar.

### Bedakan platform yang mirip

Dari 9 referensi user, **4 gugur** karena menyasar ARMv7 **Linux**, bukan
ARMv7 **Android**. Selalu periksa: ini glibc atau bionic?

### Kendala jaringan sandbox

| Situs | Status | Solusi |
|---|---|---|
| `chaquo.com` via `urllib` | ⚠️ **TERBANTAH** — ternyata BISA (2026-08-13) | jangan menyerah: langsung `urllib.request.urlopen('https://chaquo.com/pypi-13.1/<pkg>/')` |
| Log CI (blob storage) | ❌ permanen | audit mandiri |
| `kaskus.co.id`, `api.pushshift.io` | ❌ | — |

### Mengakali keterbatasan sandbox (pelajaran berbayar, 2026-08-13)

**Jangan pernah menyerah sebelum MENCARI cara, bukan cuma menebak.**
Aku sempat bilang "sandbox tak bisa akses chaquopy.com" dan "tak bisa uji ARMv7"
— **dua-duanya salah.** Keduanya ternyata bisa, dan membuka verifikasi yang jauh
lebih kuat. Aturannya: **klaim "tidak bisa" harus dibuktikan dengan mencoba, bukan
asumsi.**

#### 1. Jaringan: sandbox BISA akses chaquopy.com & PyPI
- `urllib.request.urlopen('https://chaquo.com/pypi-13.1/pandas/', timeout=15)` → **HTTP 200**.
- Dokumen lama bilang "TLS ke chaquo.com ditutup" — **itu kedaluwarsa**. Selalu uji langsung.
- Yang bisa diambil: daftar wheel per paket, dan **bongkar wheel** (`zipfile`) untuk baca
  `METADATA` (`Requires-Dist`) & `DT_NEEDED` dari `.so` — verifikasi resolver & peta native
  terhadap data NYATA, bukan mock.

#### 2. Emulator ARMv7 via qemu (tanpa Android SDK)
Aku berhasil menjalankan **Python 3.11 ARMv7 asli** di sandbox x86_64 — persis versi &
arsitektur device user. Langkahnya:

```bash
sudo apt-get install -y qemu-user-static          # qemu-armhf
# Python 3.11.15 ARMv7 (glibc) dari python-build-standalone:
#   https://github.com/astral-sh/python-build-standalone/releases/latest
#   cpython-3.11.15+<ts>-armv7-unknown-linux-gnueabihf-install_only.tar.gz
# glibc armhf (ld-linux-armhf.so.3): debian pool/main/g/glibc/libc6_..._armhf.deb
# libgcc_s armhf:                          libgcc-s1_..._armhf.deb
# (extract semua ke /tmp/glibc-armhf dgn dpkg-deb -x)
alias armpy='QEMU_LD_PREFIX=/tmp/glibc-armhf qemu-armhf -L /tmp/glibc-armhf \
  /tmp/py311armv7/python/bin/python3.11'
armpy -c "import platform; print(platform.machine())"   # -> armv7l
```

- **Network juga jalan di dalam qemu** (`urllib` ke chaquopy OK).
- `packaging` pure-Python: download wheel `py3-none-any`, unzip ke `site-packages` ARMv7.
- Dengan ini, resolver ZCODE bisa diuji di **Python 3.11 ARMv7 persis**, bukan 3.13 host.

#### 4. TEROBOSAN: jalankan WHEEL NATIVE Android (bionic) di sandbox
Setelah ~6 jalur gagal, siklus `coba→search→coba lagi→search lebih dalam→coba
lagi` MENEMBUS batas. Kunci: interpreter harus **bionic**, bukan glibc.

Cara (yang TERBUKTI berhasil, 2026-08-13):
1. **qemu-armhf** (user mode).
2. **Android system image API 24** (ARMv7) → mount `system.img` → ambil
   **`/system/bin/linker`** + `libc.so, libm.so, libdl.so, liblog.so` (bionic).
   Linker API < 24 gagal dengan "DT_HASH" (tidak dukung DT_GNU_HASH).
3. **Termux Python bionic** (`python_3.14.6_arm.deb`) — interpreter dibangun utk
   bionic, TIDAK butuh glibc. Butuh `libandroid-support.so` (Termux).
4. Jalankan dgn env: `ANDROID_ROOT`, `ANDROID_DATA`, `TZDIR` (tzdata dari system
   image di `/usr/share/zoneinfo`), `QEMU_LD_PREFIX=android_sys`.
5. **Wheel bionic** dari Termux (numpy, pillow) + rantai lib (libjpeg, libpng,
   freetype, libtiff, libxcb, libzstd...) — unduh tiap `.deb`, ekstrak, salin
   `.so` ke prefix. Rantai DT_NEEDED persis seperti device.
6. Hasil: **`import numpy` + `from PIL import Image` + simpan PNG BERJALAN**.

Script: `bash /home/user/bionic_armv7.sh -c "import numpy; print(numpy.__version__)"`

Catatan jujur: matplotlib/pandas TIDAK ada di Termux arm (terlalu berat untuk
32-bit) — tapi numpy/pillow bionic sudah membuktikan emulator bisa jalankan
wheel native Android. Ini mengubah verifikasi ZCODE: smoke test terhadap wheel
native bisa diuji di sandbox, bukan hanya device.

---

# 💬 SKILL 6 — Bekerja dengan user ini

### Konteksnya

- HP ARMv7, **tanpa PC**, QA tester tunggal
- Bahasa Indonesia informal (gw/lu). Ikuti.
- Sudah pernah frustrasi: *"puluhan kali diperbaiki, ujungnya sama, malah
  nambah bug lain"* — **itu valid, akui**

### Aturan main

1. **Diskusi dulu sebelum eksekusi.** Jangan mengubah kode sebelum disetujui.
2. **Satu siklus uji = 1 build CI + 1 install.** Mahal. Gabungkan perbaikan,
   jangan kirim tebakan satu per satu.
3. **Jangan besarkan scope diam-diam.** Kalau scope membengkak, potong jadi
   beberapa build dan katakan alasannya.
4. **Beri kemenangan yang terlihat.** Perbaikan 2 jam yang kelihatan hasilnya
   lebih berharga daripada arsitektur 3 hari yang belum tentu jalan.
5. **Jangan bertanya soal kredensial.** Git/gh sudah terkonfigurasi.

### Yang sudah terbukti soal alur kerja

- **Push BISA dilakukan asisten sendiri.** Field `permissions` dari `gh api`
  melaporkan `push:false`, tapi `git push` nyata **berhasil**. Jangan jadikan
  alasan menolak push.
- Yang benar-benar terhalang hanya **isi log CI**.

---

# 🏗️ SKILL 7 — Peta ZCODE

```
app/src/main/
├── java/com/zaba/zcode/
│   ├── core/
│   │   ├── execution/     ExecutionEngine, TerminalBridge,
│   │   │                  PythonRuntime (satu-satunya Python.start),
│   │   │                  OutputBatcher, TerminalBuffer, RunLogger
│   │   ├── packageengine/ PackageEngineV2, DependencyResolver,
│   │   │                  WheelSelector, TransactionManager, PyCall
│   │   ├── diagnostics/   Breadcrumb, CrashReporter
│   │   └── files/         Paths, FileManager
│   └── ui/                workbench, editor, terminal, settings, samples
├── python/
│   ├── zcode_runner.py    run_script(bridge, path)
│   └── package_runtime/   resolve, wheelinfo, requirement, probe, smoke
└── assets/
    ├── editor/            CodeMirror 6 bundle
    └── package_catalog/   packages.json (300), stdlib.json (305),
                           tested-manifest.json
```

**Hanya 7 dari 49 file Kotlin** menyentuh Chaquopy. 86% kode tidak peduli
runtime — itulah kenapa menambah backend kedua bukan pembongkaran.

### Aturan tak tertulis

- **`gradle.properties` = sumber tunggal versi.** Jangan hardcode di tempat lain.
- **`PythonRuntime.ensureStarted()` satu-satunya pemanggil `Python.start`.**
  Dulu 6 pemanggil → race condition.
- **Terminal selalu true-black `#050806`** apa pun temanya.
- **Terminal selalu Monospace.** Font pilihan user untuk UI & editor saja.

---

# ✅ SKILL 8 — Checklist sebelum push

```bash
bash tools/check.sh                   # harus hijau
python3 tools/kotlin_sanity_check.py  # 49 file
```

Lalu periksa manual:

- [ ] Semua simbol Compose yang dipakai sudah di-import?
- [ ] Ada `by remember`? → `getValue`/`setValue` ter-import?
- [ ] Composable baru punya `@Composable`?
- [ ] Kurung kurawal seimbang?
- [ ] Field JSON opsional pakai `isNull()`?
- [ ] Setiap bug punya guard yang **terbukti bisa gagal**?
- [ ] Pesan commit menyebutkan yang **tidak** diperbaiki?

Lalu:
```bash
git push origin <branch-sesi>
gh api repos/<owner>/<repo>/actions/runs/<id>/jobs   # pantau sampai completed
```

---

# 🚫 SKILL 9 — Jalan buntu (jangan diulang)

| Jangan | Alasan |
|---|---|
| Usulkan Pyodide/WASM | tanpa socket/threading; filesystem hilang saat refresh |
| Usulkan CPython PEP 738 mandiri | ARMv7 bukan tier 3 |
| Usulkan buang Chaquopy | satu-satunya sumber wheel ARMv7 |
| Naikkan ke Python 3.12+ | membunuh numpy di ARMv7 |
| Coba unduh isi log CI | blob storage diblokir, sudah dicoba 6× |
| `gh run list --status failure` | tidak valid di versi gh ini |
| Kompilasi PRoot di CI | butuh NDK; risiko E-03 |
| Selidiki ulang tersangka FC yang gugur | K-1 I/O main-thread, K-4 penumpukan thread, K-5 overlay WebView — semua butuh output sudah mengalir, sedangkan FC terjadi **sebelum** layar output tampil |

---

# 🎯 Penutup

Kalau ragu antara **cepat** dan **jujur** — pilih jujur.
Kalau ragu antara **elegan** dan **teliti** — pilih teliti.

User ini tidak butuh dikesankan. Dia butuh ZCODE yang tidak force close, dan
butuh tahu persis apa yang belum beres.

---

**Referensi:** `PRD_ZCODE.md` · `RENCANA_BUILD_2.md` ·
`ARMV7_COMPAT_2026_08_13.md` · `RISET_IDE_LAIN_2026_08_13.md` ·
`RISET_REFERENSI_USER_2026_08_13.md` · `BANDING_ZABACODE_2026_08_13.md`
