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
— **dua-duanya salah.** Aturan: **klaim "tidak bisa" harus dibuktikan dengan
mencoba, bukan asumsi.** Pecah kegagalan: DNS / User-Agent / sertifikat /
hostname salah / libc salah — jangan langsung "diblok".

**DISK — kemalangan yang sudah terjadi:** menaruh `system.img` (1.8 GB) di
`/home/user` membuat workspace 100% → "Failed to clear workspace" → semua
bash/read_file mati. **JANGAN ulangi.**

| Boleh | Jangan |
|---|---|
| `/var/tmp/...` (di luar snapshot workspace) | `/home/user/system.img`, zip 270 MB, Termux root |
| unzip → ambil file kecil → **hapus** image | biarkan image 1.8 GB hidup |
| skrip resep di `tools/` | commit tree emulator |

Pasang ulang (idempotent): `bash tools/setup_armv7_emu.sh`

#### 1. Jaringan: indeks Chaquopy = `chaquo.com` (bukan `chaquopy.com`)
- `https://chaquo.com/pypi-13.1/<pkg>/` → HTTP **200** (host & qemu).
- `chaquopy.com` memakai IP yang sama tapi **sertifikat untuk chaquo.com** →
  Hostname mismatch. Pakai **chaquo.com**.
- Cloudflare 403 di `tur.kcubeterm.com` dengan UA default
  `Python-urllib/3.11` — **bukan** blokir tetap. UA
  `zcode-package-runtime/1.0` (yang sudah dipakai resolver) → **200**.
- Bongkar wheel: `METADATA` / `Requires-Dist` + `readelf` `DT_NEEDED`.

#### 2. Lapisan A — `armpy` (3.11.15 **glibc** armv7l)
Resolver ZCODE di ISA/versi Chaquopy. **Bukan** Android.
DNS glibc jalan tanpa trik. Tag Android **dipaksa** lewat
`resolve_json(..., abi="armeabi-v7a", device_api=24)` — jangan andalkan
`sys_tags()` Linux.

```bash
/var/tmp/armpy -c 'import sys,platform; print(sys.version, platform.machine())'
# → 3.11.15  armv7l
```

Sumber: [python-build-standalone 20260807](https://github.com/astral-sh/python-build-standalone/releases/tag/20260807)
`cpython-3.11.15+20260807-armv7-unknown-linux-gnueabihf-install_only.tar.gz`
+ Debian `libc6`/`libgcc-s1` **armhf**.

#### 3. Lapisan B — Termux 3.14 bionic (bukan Chaquopy)
`import` numpy/pillow **Termux**. pandas/matplotlib Termux arm 32-bit **tidak ada**.
Jangan pakai ini untuk wheel Chaquopy (Cython 3.11 vs interpreter 3.14 =
`CYTHON_COMPRESS_STRINGS`).

#### 4. Senjata pamungkas — `bionic311` (3.11.15 **bionic** + linker API 24)

Ini yang menutup celah sesi pagi: **versi Python = Chaquopy**, **libc = Android**.

Komponen (semua di `/var/tmp`, terverifikasi 2026-08-13 malam):
1. `qemu-armhf`.
2. Linker + `libc.so`/`libm.so`/`libdl.so`/`liblog.so` dari **API 24** ARMv7
   (`https://dl.google.com/android/repository/sys-img/android/armeabi-v7a-24_r07.zip`).
   Extract **hanya** file itu via `debugfs` ke `/var/tmp/bionic-sys`, lalu
   **hapus** `system.img`. Linker API &lt; 24 gagal `DT_GNU_HASH`.
3. Interpreter: TUR **`python3.11_3.11.15_arm.deb`**
   (`https://tur.kcubeterm.com/pool/tur/python3.11_3.11.15_arm.deb`) —
   ELF `interpreter /system/bin/linker`, Android 24. Bukan Termux 3.14.
4. `libandroid-support.so` (Termux main) + `libpython3.11.so` di prefix qemu.
5. Env: `ANDROID_ROOT`, `ANDROID_DATA`, `TZDIR`, `QEMU_LD_PREFIX`,
   `SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt`.
6. **DNS bionic (universal, 2026-08-13 malam):** libc bicara ke
   **`/dev/socket/dnsproxyd`**. qemu-user **tidak** memakai `QEMU_LD_PREFIX`
   untuk AF_UNIX — socket harus di path HOST `/dev/socket/dnsproxyd`.
   `tools/dnsproxyd.py` meniru protokol Nougat (`getaddrinfo …\\0` → kode
   `222` + `addrinfo` BE32). Terverifikasi: `example.com`, `wikipedia.org`,
   `httpbingo.org` (tidak ada di hosts) → HTTPS 200.
   Fallback: `/system/etc/hosts` + `/var/tmp/bionic-extra-hosts.txt`.
7. **JANGAN** `export PYTHONHOME=...` sebelum menjalankan `python3` host
   (refresh hosts) — host lalu memakai stdlib ARM → `No module named '_socket'`.

```bash
bash tools/setup_armv7_emu.sh          # sekali per sandbox
/var/tmp/bionic311.sh -c 'import urllib.request; print(urllib.request.urlopen("https://chaquo.com/pypi-13.1/numpy/", timeout=15).status)'
# resolve + tag HP (kode ZCODE asli):
/var/tmp/bionic311.sh -c 'import json,sys; sys.path.insert(0,"app/src/main/python"); from package_runtime.resolve import resolve_json; print(resolve_json("pandas==2.1.3", abi="armeabi-v7a", device_api=24)[:200])'
```

**Terverifikasi di senjata ini (bukan dugaan):**
- `import` wheel Chaquopy `cp311` `armeabi_v7a`: numpy 1.26.2 (`sum=15`),
  pandas 2.1.3 (`Series.sum=6`), matplotlib 3.6.0 + `pyplot.savefig`.
- `resolve_json("pandas==2.1.3")` 14s: pytz/tzdata/dateutil + host
  openblas/libcxx/gfortran (`deps_source=pypi-version` = Bug K).
- `resolve_json("matplotlib")` 31s, 17 paket (&lt; 90s PyCall).

Host deps `.so` Chaquopy (`chaquopy-openblas`, `chaquopy-libcxx`,
`chaquopy-freetype`, …) di-extract ke
`/var/tmp/bionic-sys/system/lib` + site-packages `/var/tmp/chaquo-sp`.

#### Batas jujur senjata (bukan HP)
- Bukan JVM / `Python.start` / UI / timeout PyCall di proses APK.
- `/var/tmp` hilang saat sandbox restart → jalankan `setup_armv7_emu.sh` lagi.
- Bukan pengganti UAT Infinix. User tetap uji APK; laporan UAT + senjata ini
  dipakai bersama.

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
| Selidiki ulang tersangka FC yang gugur | K-1 I/O main-thread, K-4 penumpukan thread, K-5 overlay WebView — semua butuh output sudah mengalir, sedangkan FC terjadi **sebelum** layar output tampil |\n| Taruh `system.img` / zip ≥100 MB di `/home/user` | disk workspace penuh → platform gagal clear workspace |\n| Pakai Termux Python 3.14 untuk wheel Chaquopy 3.11 | `CYTHON_COMPRESS_STRINGS` — pakai TUR python3.11 + `bionic311` |\n| `https://chaquopy.com` sebagai indeks wheel | cert untuk `chaquo.com`; indeks = `https://chaquo.com/pypi-13.1/` |

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
