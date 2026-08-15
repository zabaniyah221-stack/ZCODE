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

#### 5. Full Android ARMv7 — layer C (API24, QEMU klasik)

Kalau bug menyentuh Kotlin↔Python callback, Activity, Compose, WebView,
PackageManager, atau lifecycle yang tidak dapat dibuktikan `bionic311`, gunakan:

```bash
bash tools/setup_armv7_full_emu.sh
bash tools/start_armv7_full_emu.sh       # long-running; Agent pakai start_process
bash tools/verify_armv7_full_emu.sh
bash tools/stop_armv7_full_emu.sh
```

Aturan keras dari eksperimen 2026-08-13:

- `/dev/kvm` tidak ada; gunakan emulator klasik 27.3.8 + guest API24 ARMv7.
- Semua image/AVD/Gradle eksperimental di `/var/tmp`, tidak di workspace.
- `-memory 512 -qemu -m 512` keduanya wajib; tanpa override RSS mencapai
  ±1.55 GB pada sandbox 1.9 GB.
- SwiftShader wajib untuk ZCODE: `-gpu off` membuat WebView Chromium SIGABRT
  karena EGL pbuffer tidak tersedia.
- Jangan jalankan Gradle dan emulator bersamaan.
- Production minSdk26 tidak dapat dipasang ke official ARMv7 image API24/25.
  APK test-only minSdk24 boleh dipakai untuk membuktikan Android/JVM/Chaquopy,
  tetapi **tidak** boleh dirilis atau disebut DEVICE VERIFIED.
- Full emulator menemukan bug yang lolos 411 test + bionic311: Cancel ditelan
  fallback source lalu berubah menjadi COMPATIBILITY. Karena itu emulator ini
  bukan kosmetik; ia gate untuk perubahan lifecycle/bridge.
- Detail dan angka resource:
  `docs/FULL_ARMV7_ANDROID_EMULATOR_2026_08_13.md`.

##### 5.1 Catatan perjalanan — jangan ulangi kegagalan yang sama

Ini bukan rangkuman kemenangan saja. Urutan kegagalan di bawah WAJIB dibaca
sebelum agent berikutnya menyentuh emulator, supaya sandbox baru tidak kembali
ke titik nol.

| Percobaan | Gejala nyata | Akar | Keputusan permanen |
|---|---|---|---|
| Menganggap sandbox "tidak bisa full Android" | berhenti di `bionic311` | menyamakan "tidak ada KVM" dengan "tidak ada emulasi" | KVM hanya akselerator; ARM QEMU TCG tetap bisa, walau lambat |
| Emulator SDK terbaru + guest ARM32 | `PANIC: CPU Architecture 'arm' is not supported by the QEMU2 emulator` | QEMU2 modern mencabut ARM32/classic engine | pin emulator **27.3.8 build 4848055**, jangan auto-update |
| AVD manual pertama | `Broken AVD system path` | launcher menganggap SDK root rusak bila `platform-tools/` tidak ada; layout image tidak standar | setup wajib menyediakan `sdk/platform-tools` dan `system-images/android-24/default/armeabi-v7a` |
| Menjalankan engine langsung tanpa AVD | API terbaca 10000/default ABI, ADB tidak stabil, emulator meminta `vendor.img` | metadata AVD/build.prop tidak melalui jalur normal launcher | gunakan AVD terdaftar; direct `emulator64-arm -sysdir` hanya alat diagnosis |
| RAM default | emulator RSS ±1.55 GB, sandbox tinggal ±8 MB available | `-memory 768` diam-diam dinaikkan menjadi guest 1024 MB oleh profile/emulator | pakai **dua** pembatas: `-memory 512` dan argumen QEMU terakhir `-qemu -m 512` |
| `-gpu off` untuk hemat RAM | app SIGABRT di WebView: `failed to create a pbuffer surface ... EGL_SUCCESS` | Chromium WebView tetap butuh EGL pbuffer walau headless | SwiftShader wajib untuk test ZCODE; GPU off hanya boleh untuk boot/shell tanpa WebView |
| SwiftShader tanpa RAM override | UI hidup, tetapi RSS kembali terlalu dekat batas | framebuffer/renderer + guest 1 GB | kombinasikan SwiftShader **dengan** QEMU 512 MB; jangan memilih salah satu saja |
| Install APK production di API24 | `INSTALL_FAILED_OLDER_SDK: requires #26, current #24` | official ARMv7 system image berhenti sebelum minSdk production ZCODE | jangan menurunkan minSdk produk; build APK **test-only minSdk24** dan labeli jujur |
| Mencari flag bypass minSdk | `pm` API24 tidak punya `--force-sdk`; bypass target-SDK bukan bypass minSdk | minSdk adalah compatibility contract nyata | berhenti mencari bypass; pakai test variant atau HP API26+ |
| Build test APK memakai JRE saja | `jlink executable ... does not exist` | AGP membutuhkan full JDK, bukan JRE headless | local build perlu JDK lengkap (`openjdk-21-jdk-headless` di sandbox ini) |
| Gradle `-Xmx768m` pada satu percobaan | daemon hilang saat dex merge | peak native/metaspace + heap melewati margin sandbox | emulator harus stop; `--max-workers=1`, compiler in-process, heap 640 MB + metaspace cap terbukti lebih stabil |
| Build dan emulator hampir overlap | available RAM turun <300 MB | dua workload berat bersaing tanpa swap | fase build dan fase runtime HARUS serial; script start menolak MemAvailable <1.2 GB |
| Drawer swipe via `adb input swipe` | gesture tidak konsisten karena WebView/focus/edge semantics | input sintetis bukan gesture Compose yang andal | untuk UAT target, APK test-only boleh start langsung di route `pip`; pulihkan source setelah build |
| Cancel v1.0.16 di full emulator | event `cancelled` ada, tetapi akhir `PKG_ANALYZE_FAIL [COMPATIBILITY]` | fallback PyPI/Chaquopy menelan `ResolveError(CANCELLED)` | setiap catch fallback wajib `_propagate_cancel`; AST guard menjaga kelasnya |
| Metadata support library | PyPI 404 yang sama tampak berulang | kegagalan tidak masuk cache; `_collect` dan `_choose` bertanya lagi | negative metadata cache hidup selama satu resolve, tidak lintas sesi |

Kalau gejala persis di atas muncul lagi, **jangan mulai riset dari nol**. Periksa
dulu apakah invariant/flag/path yang sudah dibuktikan terhapus.

##### 5.2 Inventaris sandbox sebelum eksperimen

Jangan mengandalkan asumsi dari sesi lama. Jalankan dan catat:

```bash
uname -a
uname -m
nproc
free -h
df -h / /var/tmp /home/user
ls -l /dev/kvm || true
cat /proc/meminfo | head
cat /sys/fs/cgroup/memory.max 2>/dev/null || true
command -v docker adb emulator java gradle || true
```

Baseline yang menghasilkan profil ini:

```text
host        x86_64, 2 vCPU
RAM         1.9 GiB, tanpa swap
KVM         tidak ada
Docker      tidak ada
Disk        ±20 GiB kosong
workspace   snapshot terbatas; jangan isi image/cache build
```

Jika baseline berubah, keputusan boleh berubah, tetapi **ukur dulu**. Misalnya,
kalau `/dev/kvm` suatu hari tersedia, emulator x86_64 API26+ mungkin lebih cepat,
namun ia tidak otomatis membuktikan ABI ARMv7.

##### 5.3 Tangga pembuktian — pilih alat termurah yang menjawab pertanyaan

Jangan selalu menyalakan full emulator:

| Pertanyaan | Alat minimum |
|---|---|
| parser/version/dependency pure logic | pytest host |
| tag dan resolver pada ISA ARMv7 | `armpy` |
| import wheel Python 3.11 dengan bionic/linker Android | `bionic311` |
| callback Kotlin↔Python, PackageManager, Activity, lifecycle, Compose, WebView | full ARMv7 emulator |
| artifact production minSdk26 + firmware/device nyata | HP user |

Full emulator lambat dan mahal. Ia adalah **gap closer**, bukan pengganti semua
test yang lebih murah. Sebaliknya, jangan mengklaim callback/lifecycle benar
hanya karena `bionic311` hijau.

##### 5.4 Prosedur resource-safe yang terbukti

1. **Setup/build phase — emulator mati.**
   - Download/extract hanya ke `/var/tmp`.
   - Arsip ZIP langsung dihapus.
   - Gradle `--max-workers=1`; jangan pakai heap 2 GB dari default project di
     sandbox 1.9 GB.
2. **Pastikan source production kembali bersih.**
   - Perubahan minSdk24/direct route hanya test variant.
   - Simpan backup di `/var/tmp`, gunakan `trap`/restore, lalu `git status` wajib
     hanya menampilkan perubahan yang memang hendak di-commit.
   - Jangan pernah commit APK test-only.
3. **Runtime phase — Gradle/Java daemon mati.**
   - Start dengan script, bukan command improvisasi.
   - Tunggu `sys.boot_completed=1`; status ADB `device` saja belum berarti UI
     selesai boot.
4. **Uji paling informatif dulu.**
   - ABI/API → app process → Chaquopy start → target flow → Diagnostics.
   - Ambil `uiautomator dump`, `screencap`, `dumpsys meminfo`, dan breadcrumb.
5. **Stop segera.**
   - `bash tools/stop_armv7_full_emu.sh`.
   - Pastikan `pgrep -af emulator64-arm` kosong dan RAM kembali.

Command operasional:

```bash
bash tools/setup_armv7_full_emu.sh
# Agent Mode: start_process("bash tools/start_armv7_full_emu.sh")
bash tools/verify_armv7_full_emu.sh
# ... UAT ...
bash tools/stop_armv7_full_emu.sh
```

##### 5.5 Cara membuat APK test-only tanpa mencemari produk

Karena guest resmi ARMv7 adalah API24 dan production minSdk26, test variant
boleh mengubah sementara:

```text
app/build.gradle.kts: minSdk 26 → 24
MainActivity startDestination: editor → pip   (hanya bila otomasi target perlu)
```

Kontraknya:

- perubahan hanya di working tree lokal;
- build dari commit produksi yang sama;
- APK diberi nama `emulator-only`, tidak diunggah sebagai release;
- file sumber dipulihkan segera setelah APK disalin ke `/var/tmp`;
- `git diff` diperiksa sebelum commit/push;
- laporan selalu menyebut bahwa manifest test berbeda;
- temuan logic yang valid diperbaiki di source production dan diuji ulang;
- keberhasilan test variant tidak menaikkan status menjadi DEVICE VERIFIED.

##### 5.6 Observability full emulator — bukti yang harus diambil

```bash
adb -s emulator-5554 shell getprop ro.product.cpu.abi
adb -s emulator-5554 shell getprop ro.build.version.sdk
adb -s emulator-5554 shell getprop sys.boot_completed
adb -s emulator-5554 shell pidof com.zaba.zcode.debug
adb -s emulator-5554 shell dumpsys meminfo com.zaba.zcode.debug
adb -s emulator-5554 exec-out screencap -p > /tmp/zcode.png
adb -s emulator-5554 shell uiautomator dump /sdcard/window.xml
adb -s emulator-5554 shell run-as com.zaba.zcode.debug \
  cat files/logs/diagnostics/breadcrumb.log
```

`logcat` penting untuk native crash, tetapi breadcrumb tetap penting karena ia
merepresentasikan kontrak diagnostik yang benar-benar tersedia bagi user tanpa
PC. Pada eksperimen ini, `logcat` membuktikan GPU-off crash, sedangkan breadcrumb
membuktikan Cancel berubah salah menjadi COMPATIBILITY.

##### 5.7 Pola umum memaksimalkan sandbox terbatas

Gunakan urutan ini untuk masalah lain, bukan hanya emulator:

1. **Pisahkan kebutuhan dari implementasi populer.** Kebutuhannya "menjalankan
   Android ARMv7", bukan "Android Studio terbaru + KVM".
2. **Pecah stack menjadi lapisan.** ISA, libc/linker, Python, JVM, framework,
   UI, dan artifact adalah pertanyaan berbeda.
3. **Cari versi alat yang masih memiliki capability yang dibutuhkan.** Terbaru
   tidak selalu paling kompatibel; di sini versi terbaru justru membuang ARM32.
4. **Ubah constraint menjadi parameter terukur.** RAM, disk, API, ABI, startup,
   dan RSS dicatat; jangan pakai kata "berat" tanpa angka.
5. **Isolasi state besar/ephemeral.** `/var/tmp` untuk image/cache; workspace
   hanya menyimpan resep, test, dan laporan.
6. **Eksperimen satu variabel per langkah.** RAM override, GPU mode, AVD layout,
   dan minSdk dibedakan supaya sebab tidak kabur.
7. **Setiap kegagalan menghasilkan invariant atau guard.** Kalau hanya
   diperbaiki di terminal tanpa ditulis ke script/test/SKILLS, siklus belum putus.
8. **Pertahankan label bukti.** BOOT VERIFIED, FULL-EMULATOR VERIFIED, CI
   VERIFIED, dan DEVICE VERIFIED bukan sinonim.
9. **Sediakan setup/start/verify/stop.** Eksperimen yang tidak dapat diulang
   agent berikutnya belum menjadi kemampuan proyek.
10. **Jangan romantisasi keberhasilan.** Full emulator berhasil, tetapi API24,
    software-emulated, lambat, dan bukan artifact production. Batas itu tetap
    ditulis berdampingan dengan kemenangan.

Inti pelajarannya:

> "Sandbox terbatas" bukan jawaban akhir. Itu daftar constraint. Pecah,
> ukur, cari kombinasi yang valid, lalu ubah perjalanan tersebut menjadi resep
> idempotent + guard agar agent berikutnya mulai dari capability baru, bukan dari
> ketakutan lama.

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
| Kirim Gmail lewat SMTP mentah / Guerrilla `send_email` | Guerrilla: `needs_captcha`. SMTP :25 ke MX Gmail **terbuka**, tapi Gmail **550 5.7.26** (DMARC / wajib SPF atau DKIM). Manus kirim lewat **OAuth akun user** atau API (Nylas) di HTTPS, bukan `smtplib` telanjang. |
| "Kredensial tak terdeteksi bot" / bypass captcha | **Jangan.** Itu pabrik akun siluman. |
| Inbox sementara tanpa captcha (TERIMA) | `https://api.mail.tm` — POST `/accounts` + `/token`, GET `/messages`. Terverifikasi 2026-08-13: akun `z…@emalupe.com` 201, JWT 200, inbox `[]`, **tanpa captcha**. Skrip: `tools/temp_inbox.py`. Guerrilla `get_email_address` juga terima-saja. Surat disposable sering ditolak situs besar; Gmail **tidak** bisa dikirim dari sini. |

---

# 🧭 SKILL 10 — Operating model: dari laporan sampai rilis

Bagian ini memberi agent **keleluasaan yang bertanggung jawab**. Tujuannya bukan
menambah birokrasi, tetapi mencegah dua ekstrem yang sama-sama buruk:

- langsung mengubah kode dari satu gejala;
- terus meneliti/berdiskusi sampai tidak ada hasil yang bisa diuji.

## 10.1 Lima mode kerja

Setiap tugas berada pada salah satu mode berikut. Sebutkan perpindahan mode bila
pekerjaannya berisiko atau berlangsung lintas sesi.

| Mode | Pertanyaan utama | Keluaran minimum |
|---|---|---|
| **TRIAGE** | Apa yang benar-benar terjadi? | fakta, timeline, area terdampak, bukti yang kurang |
| **DISCOVERY** | Bagaimana sistem serupa menanganinya? | pembanding + sumber + batas relevansi |
| **DESIGN** | Kontrak perilaku apa yang akan diubah? | state/flow, failure modes, acceptance criteria |
| **IMPLEMENT** | Perubahan terkecil apa yang menyelesaikan kelas bug? | kode + guard + migrasi bila ada |
| **VERIFY/RELEASE** | Apa buktinya dan bagaimana pulih bila salah? | test, CI, artifact, UAT, rollback |

Tidak semua tugas perlu lima mode secara formal:

- typo/dokumen kecil → langsung IMPLEMENT;
- bug lokal dengan reproduksi deterministik → TRIAGE singkat lalu IMPLEMENT;
- concurrency, installer, filesystem, security, migrasi data, atau runtime
  Python → TRIAGE → DISCOVERY/DESIGN → minta ACC → IMPLEMENT;
- insiden yang bisa merusak workspace user → hentikan perubahan destruktif,
  kumpulkan bukti, lalu diskusi.

## 10.2 Matriks otonomi: kapan jalan, kapan bertanya

Agent **boleh langsung bertindak** jika semuanya benar:

1. intent user jelas;
2. perubahan mudah dibalik;
3. tidak menyentuh data pengguna, format persistensi, keamanan, ABI, atau
   lifecycle worker;
4. test yang relevan tersedia;
5. scope tidak membesar dari permintaan.

Agent **wajib diskusi dulu** jika salah satu benar:

- ada ≥2 desain yang mengubah UX/kontrak secara bermakna;
- bukti menunjuk beberapa akar yang sama kuat;
- perubahan menghapus data, mengubah format, dependency, permission, atau ABI;
- satu build UAT mahal dan keputusan bisa digabung secara lebih aman;
- solusi mengubah prinsip PRD atau membatalkan keputusan lama;
- agent hendak memakai workaround yang menyembunyikan akar masalah.

Agent **wajib berhenti dan eskalasi** jika:

- bukti yang ada bertentangan dan eksperimen aman tidak dapat membedakannya;
- perubahan berisiko membuat data user tidak dapat dipulihkan;
- hasil test tidak konsisten/tidak deterministik;
- disk/snapshot/credential boundary akan dilanggar;
- solusi membutuhkan klaim yang belum bisa diverifikasi tetapi akan dipasarkan
  sebagai fakta.

## 10.3 RFC dan ADR: proporsional, bukan ritual

Tulis RFC singkat sebelum implementasi bila perubahan menyentuh ≥2 dari:

- lebih dari satu layer (Compose/Kotlin/Python/network/storage);
- concurrency, cancellation, retry, timeout, transaction, atau recovery;
- kontrak publik/format JSON/state machine;
- kompatibilitas ARMv7/ABI;
- perubahan yang memerlukan UAT perangkat nyata;
- perkiraan diff produksi >3 file atau acceptance criteria >5 butir.

RFC yang baik cukup menjawab:

1. masalah dan bukti;
2. non-goals;
3. desain dan invariant;
4. alternatif yang ditolak beserta alasan;
5. failure modes;
6. observability;
7. test/mutasi/UAT;
8. rollback.

ADR dipakai untuk keputusan yang ingin dipertahankan lintas fitur, misalnya
“timeout hanya per I/O, bukan umur total resolve”. RFC boleh berubah selama
implementasi; perubahan asumsi wajib dicatat, bukan disembunyikan.

---

# 🔎 SKILL 11 — Riset pembanding sebelum memilih arsitektur

Riset eksternal bukan dekorasi jawaban. Ia dipakai untuk menghindari menemukan
ulang kegagalan yang sudah dibayar proyek lain. Namun **popularitas bukan bukti
bahwa desainnya cocok untuk ZCODE**.

## 11.1 Trigger riset wajib

Lakukan pencarian sebelum mengunci desain jika tugas melibatkan:

- package manager/dependency resolver;
- retry, timeout, cancellation, resume, cache, dan background work;
- Android lifecycle, permission, SAF, WebView, atau process death;
- ABI/native wheel/runtime Python;
- security/crypto/TLS;
- format/standar yang punya implementasi rujukan;
- klaim “IDE lain melakukan X”.

Tidak perlu searching untuk kesalahan sintaks lokal yang sudah terbukti dan
punya dokumentasi API di repo.

## 11.2 Urutan kualitas sumber

Gunakan urutan berikut, lalu nyatakan bila terpaksa turun tingkat:

1. **source code + test upstream**;
2. **dokumentasi resmi/standard/PEP/RFC**;
3. **issue tracker dengan log dan reproduksi**;
4. dokumentasi produk/store listing untuk fakta fitur;
5. laporan pengguna/forum untuk menemukan gejala;
6. blog agregator hanya sebagai petunjuk pencarian, bukan fondasi keputusan.

Untuk proyek closed-source seperti Pydroid, boleh menyimpulkan fitur publik,
tetapi jangan mengarang mekanisme internal. Tulis: “implementasi internal tidak
terverifikasi”.

## 11.3 Matriks pembanding wajib

Untuk setiap pembanding, catat minimal:

| Aspek | Pertanyaan |
|---|---|
| Platform | Android bionic atau Linux/glibc/desktop? |
| Runtime | in-process, subprocess, service, PRoot, atau remote? |
| Package source | PyPI, indeks custom, bundled, source build? |
| Ownership | siapa memiliki worker dan kapan dianggap selesai? |
| Failure | timeout per I/O atau seluruh operasi? retry di layer mana? |
| Cancellation | membatalkan tunggu saja atau pekerjaan nyata? |
| Progress | apa yang terlihat user selama operasi panjang? |
| Recovery | cache/resume/transaction/rollback tersedia? |
| Relevansi | bagian mana yang bisa/tidak bisa diadopsi ZCODE? |

Contoh: Termux memberi pelajaran bagus tentang subprocess, streaming, dan
Ctrl+C; tetapi tidak membuktikan wheel glibc cocok untuk Chaquopy Android.
Pydroid membuktikan nilai curated native repository; ia tidak membuktikan cara
resolver internalnya karena closed-source.

## 11.4 Protokol riset

1. Tulis pertanyaan yang hendak diputuskan, bukan query acak.
2. Cari minimal satu implementasi langsung dan satu sumber prinsip/standar.
3. Cari juga **failure report**, bukan hanya halaman pemasaran.
4. Uji klaim penting dengan data/endpoint/source bila mungkin.
5. Catat kontradiksi dan pilih berdasarkan bukti terkuat.
6. Hentikan riset saat bukti sudah cukup membedakan alternatif; jangan mengejar
   kepastian 100% yang mustahil.
7. Tautkan URL langsung dalam RFC/SKILLS untuk keputusan eksternal.

## 11.5 Output riset harus mengubah keputusan atau dinyatakan netral

Setelah riset, tulis salah satu:

- **menguatkan** desain awal;
- **mengubah** desain awal (jelaskan apa yang diralat);
- **tidak relevan** karena platform/kontrak berbeda;
- **belum cukup bukti** sehingga perlu eksperimen.

Riset yang tidak memengaruhi keputusan dan tidak memperkecil ketidakpastian
adalah aktivitas, bukan kemajuan.

---

# ⏱️ SKILL 12 — Operasi panjang: timeout, retry, cancel, dan ownership

Pelajaran v1.0.15: menaikkan satu HTTP request dari `1 × 20s` menjadi
`3 × 20s`, sementara wrapper memberi seluruh resolver 90s, membuat semua paket
menabrak timeout. Lebih parah: wrapper berhenti menunggu tetapi worker Python
masih hidup. Ini **kelas bug lifecycle**, bukan bug numpy/matplotlib/pandas.

## 12.1 Taksonomi waktu — jangan campur

Setiap operasi jaringan/paket harus membedakan:

| Batas | Makna |
|---|---|
| **connect timeout** | waktu membuka koneksi/DNS/TLS |
| **read inactivity timeout** | tidak ada byte/progress selama interval |
| **per-attempt timeout** | umur satu percobaan request |
| **retry budget** | jumlah/waktu tambahan seluruh retry |
| **operation deadline** | batas bisnis opsional untuk seluruh workflow |
| **watchdog** | mendeteksi worker macet/bug internal, bukan jalur normal |
| **user patience** | kapan UI memberi progress/cancel, bukan kapan worker dibunuh |

Aturan:

- timeout I/O **bukan** batas total resolve/install;
- operasi yang masih menunjukkan progress tidak boleh dianggap hang hanya
  karena durasinya panjang;
- jika ada outer deadline, jumlah timeout + backoff seluruh inner attempt wajib
  muat di dalamnya, atau inner call menerima **remaining budget**;
- watchdog harus lebih panjang dari skenario normal terburuk dan menghasilkan
  diagnostik state terakhir;
- menaikkan retry/timeout di satu layer mewajibkan audit semua outer layer.

## 12.2 Retry hanya sekali di layer yang memiliki konteks

Retry dapat memperbesar kegagalan. Terapkan:

1. klasifikasikan retriable: timeout sementara, connection reset, HTTP 408/429
   (hormati `Retry-After`), dan 5xx tertentu;
2. jangan retry validation error, parsing deterministic, hash mismatch, 4xx
   permanen, wheel incompatible, atau storage penuh;
3. retry pada **satu layer**, jangan Python + Kotlin + UI sama-sama mengulang;
4. batasi attempt dan total retry budget;
5. gunakan capped backoff + jitter untuk menghindari retry serentak;
6. GET metadata boleh diulang; operasi berefek samping hanya boleh diulang bila
   idempotent atau punya transaction/request ID;
7. catat attempt, alasan, host/source, durasi, dan hasil.

Sumber prinsip: AWS Builders’ Library
<https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/>
dan Google SRE
<https://sre.google/sre-book/addressing-cascading-failures/>.

## 12.3 Timeout menunggu ≠ membatalkan pekerjaan

Invariant wajib:

> Owner tidak boleh mengumumkan `IDLE`, melepas mutex, menghapus state/cache,
> atau menerima kerja baru sampai worker sebelumnya benar-benar mencapai state
> terminal.

`latch.await(90s) == false` hanya berarti **caller berhenti menunggu**. Itu tidak
membuktikan thread berhenti. Begitu pula `Job.cancel()` bersifat kooperatif:
worker harus mencapai suspension point atau memeriksa token/isActive.

Rujukan resmi Kotlin:
<https://kotlinlang.org/docs/cancellation-and-timeouts.html>.

Untuk bridge Chaquopy/blocking code:

- simpan `operationId`, handle worker, cancel token, dan last progress;
- periksa cancel sebelum/selesai I/O, sebelum retry, dan setiap loop dependency;
- tutup stream/socket yang dimiliki bila API mendukung;
- `cancel` mengubah state ke `CANCELLING`, bukan langsung `CANCELLED`;
- `CANCELLED` baru terminal setelah cleanup/finally selesai;
- hasil worker lama wajib diabaikan bila `operationId` tidak lagi aktif;
- jangan mengandalkan `Thread.interrupt()` untuk menghentikan CPython
  in-process tanpa bukti bahwa API blocking tersebut interruptible.

## 12.4 Model state eksplisit

Operasi panjang minimal memakai state machine:

```text
IDLE
  → STARTING
  → RUNNING(stage, item, attempt, progressAt)
  → RETRY_WAIT(nextAttemptAt)
  → CANCELLING
  → SUCCEEDED | FAILED | CANCELLED
```

Transition ilegal harus ditolak/test:

- `RUNNING → IDLE` tanpa terminal result;
- start baru saat `CANCELLING`;
- progress dari operation lama menimpa operation baru;
- `FAILED` tetapi transaction/cache lock belum dilepas;
- UI hilang lalu worker menjadi tanpa owner.

Compose mengikuti UDF: state turun, event (`Start`, `Cancel`, `Retry`) naik ke
state holder. Jangan jadikan boolean terpisah (`isInstalling`, `busyFlag`,
`pending...`) sebagai beberapa sumber kebenaran yang bisa bertentangan.
Rujukan: <https://developer.android.com/develop/ui/compose/architecture>.

## 12.5 Progress adalah bagian dari correctness

Operasi >2 detik wajib memberi tanda hidup yang bermakna. Minimal event:

```text
operation_id, elapsed_ms, stage, source, package, attempt,
bytes_read/total (jika diketahui), cache_hit, message_code
```

Aturan observability:

- log event terstruktur; teks UI boleh diterjemahkan dari `message_code`;
- jangan log token, query credential, isi script, atau path sensitif;
- throttle event hot path agar Compose/log file tidak banjir;
- heartbeat hanya bila tidak ada progress alamiah;
- simpan last-known stage agar watchdog menjelaskan worker macet di mana;
- Diagnostics harus dapat disalin dan tetap berguna tanpa logcat;
- error user-facing ringkas, detail teknis tersedia terpisah.

## 12.6 Cache memiliki scope dan freshness

- cache per-operation: aman untuk dedup selama satu resolve;
- cache process-wide: wajib thread-safe dan tidak boleh dibersihkan sepihak;
- cache persisted: wajib punya schema/version, ABI, Python version, source, dan
  freshness/revalidation;
- negative cache harus pendek dan membedakan 404 permanen dari network timeout;
- cache wheel tetap dipisah ABI dan hash diverifikasi;
- jangan menonaktifkan cache untuk “memperbaiki” bug tanpa bukti—pip sendiri
  memperingatkan itu menambah network dan memperlambat operasi:
  <https://pip.pypa.io/en/stable/topics/caching/>.

## 12.7 Android lifecycle dan kerja penting bagi user

Navigasi/rotasi tidak boleh otomatis berarti pekerjaan selesai. Tentukan umur
worker secara sadar:

- screen scope bila hasil hanya berguna selama layar hidup;
- ViewModel/app scope bila harus bertahan rotasi/navigasi;
- foreground/user-initiated/WorkManager hanya jika benar-benar perlu bertahan
  background/process constraints—jangan cargo-cult karena installer Chaquopy
  in-process punya kebutuhan khusus.

Untuk kerja panjang yang memang harus bertahan background, Android menekankan
progress terlihat, cancellation, unique work, dan cooperative cleanup:
<https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running>
dan
<https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work>.

---

# 🧪 SKILL 13 — Verifikasi profesional: model, fault injection, dan perangkat

398 test hijau tidak membantah bug yang tidak dimodelkan. Test suite harus
mengukur kontrak, bukan hanya kemunculan string.

## 13.1 Piramida bukti ZCODE

1. **unit pure logic** — parser, tag, version, dependency metadata;
2. **contract test** — JSON Kotlin↔Python, optional/null, error code;
3. **state-machine test** — transition, ownership, stale result;
4. **fault-injection test** — timeout/reset/404/429/503/partial read/cancel;
5. **integration host** — endpoint nyata dan wheel nyata;
6. **ARMv7 glibc probe** — resolver/tag, bukan import Android;
7. **bionic311** — Python 3.11 + bionic/native wheel;
8. **CI APK** — compiler/packaging/manifest;
9. **UAT HP nyata** — Android/JVM/Chaquopy/UI/lifecycle.

Jangan melompati layer dan menganggap layer 5 menggantikan 9. Sebaliknya,
jangan membebankan semua diagnosis kepada user bila layer 1–8 bisa dikerjakan
agent.

## 13.2 Transport harus dapat dipalsukan

Kode network baru sebaiknya menerima abstraksi transport/clock/sleeper agar
test deterministik dapat mensimulasikan:

- sukses attempt pertama;
- timeout lalu sukses;
- semua attempt timeout;
- partial body lalu reset;
- 404 tanpa retry;
- 429 + `Retry-After`;
- 503 + backoff;
- cancel ketika socket aktif dan ketika menunggu retry;
- deadline hampir habis;
- cache hit/miss/stale.

Test tidak boleh benar-benar menunggu 20/60/90 detik. Pakai fake clock atau
nilai timeout kecil yang diinjeksi. Network nyata dipakai sebagai integration
probe terpisah, bukan unit test CI yang flaky.

## 13.3 Test concurrency dan stale work

Wajib ada test untuk:

- dua tap Start hampir bersamaan → hanya satu worker;
- timeout caller tidak melepas owner;
- cancel lalu start cepat → start kedua menunggu terminal state;
- worker lama selesai terlambat → hasilnya tidak menimpa state baru;
- rotasi/navigasi → observer berganti, worker tidak dobel;
- exception di cleanup → lock tetap dilepas tepat sekali;
- cache sesi A tidak dihapus sesi B;
- callback progress setelah terminal state diabaikan.

Gunakan barrier/latch/fake worker, bukan `sleep()` tebak-tebakan.

## 13.4 Uji mutasi tetap wajib, tetapi sesuai kontrak

Mutasi untuk bug timeout/retry misalnya:

- ubah max attempts 2 → 3 hingga budget terlampaui;
- lepaskan busy di timeout caller;
- hapus check cancellation di loop dependency;
- izinkan HTTP 404 diretry;
- pakai cache global lalu clear pada start;
- hilangkan operationId check pada callback.

Setiap mutasi harus membuat test yang dituju merah. Setelah dipulihkan, seluruh
suite hijau.

## 13.5 Acceptance criteria harus bisa diamati

Buruk:

> “Installer lebih stabil.”

Baik:

- selama analyze, stage baru terlihat maksimal setiap N detik saat ada kerja;
- timeout satu host menyebut source/attempt/durasi;
- Cancel berpindah `RUNNING → CANCELLING → CANCELLED` tanpa menerima start baru;
- setelah terminal state, analyze berikutnya bisa berjalan;
- numpy/pandas/matplotlib mempertahankan dependency yang benar;
- tidak ada thread resolver yatim menurut instrumentasi operation registry.

---

# 📐 SKILL 14 — Governance dokumen dan keputusan

Dokumen punya fungsi berbeda. Jangan menaruh semua hal ke PRD atau membiarkan
SKILLS menjadi changelog.

| Dokumen | Fungsi | Jangan diisi |
|---|---|---|
| **PRD** | tujuan produk, pengguna, batas, roadmap, active known issues | detail implementasi sementara |
| **RFC** | desain satu perubahan sebelum coding | klaim sukses sebelum verifikasi |
| **ADR** | keputusan arsitektur yang ingin dipertahankan | timeline debugging harian |
| **SKILLS** | cara kerja/pelajaran lintas fitur | patch khusus satu baris tanpa prinsip umum |
| **UJI/LAPORAN** | bukti build, device, versi, hasil nyata | aspirasi roadmap |
| **SESSION SUMMARY** | handoff fakta dan next step | mengganti source of truth |

## 14.1 Kapan SKILLS di-upgrade

Tambahkan aturan jika pelajarannya:

- sudah menyebabkan atau hampir menyebabkan regresi nyata;
- berlaku lintas file/fitur;
- tidak cukup ditahan oleh satu test;
- membantu agent berikutnya memilih proses/desain yang benar.

Setiap tambahan harus memuat: trigger, aturan, contoh ZCODE, dan bila eksternal
mencantumkan sumber. Bila bukti baru membantahnya, edit/hapus—SKILLS bukan kitab
suci.

## 14.2 Traceability ringan

Untuk perubahan berisiko, jaga rantai:

```text
log/gejala → bug ID → RFC decision → code diff → guard/mutasi
→ CI artifact → UAT device/version → PRD status
```

Tidak perlu tool enterprise. Nama dokumen, commit, dan breadcrumb yang konsisten
sudah cukup. Tujuannya agar enam bulan kemudian kita tahu **mengapa**, bukan
hanya **apa**.

## 14.3 Definition of Done bertingkat

Gunakan bahasa status yang jujur:

- **DESIGNED** — rancangan disetujui, belum diimplementasikan;
- **IMPLEMENTED** — kode dan test lokal selesai;
- **CI VERIFIED** — APK dibangun dan checks hijau;
- **DEVICE VERIFIED** — skenario UAT lulus pada device/ABI/API tertentu;
- **RELEASED** — artifact yang diverifikasi tersedia sebagai rilis;
- **REGRESSION FOUND** — bukti baru membuka kembali isu.

Jangan menulis “fixed di ARMv7” saat baru lolos host/qemu. Tulis perangkat,
Android API, ABI, versi app, requirement, network condition, dan hasil.

## 14.4 Rollback dan blast radius

Sebelum push perubahan berisiko, jawab:

- bisakah commit direvert tanpa migrasi balik?
- data/cache versi baru aman dibaca versi lama?
- feature flag/fallback diperlukan atau justru menambah mode yang sulit dites?
- apa gejala pertama jika salah?
- bagaimana user tunggal memulihkan tanpa PC?

Prioritaskan perubahan yang atomik, dapat direvert, dan punya failure message.
Jangan menggabungkan refactor kosmetik dengan fix reliability yang perlu
dibisect.

---

# 🤝 SKILL 15 — Komunikasi engineering dan handoff

## 15.1 Format laporan keputusan

Untuk bug penting, komunikasi minimum:

1. **Fakta** — apa yang terlihat di log/test;
2. **Interpretasi** — dugaan akar + confidence;
3. **Yang belum diketahui**;
4. **Pembanding** — apa yang dipelajari dan batas relevansinya;
5. **Rencana + non-goals**;
6. **Bukti yang akan dibuat**;
7. **keputusan yang diminta dari user**, bila ada.

Hindari kalimat absolut jika hanya inferensi. Gunakan confidence yang masuk akal,
bukan presisi palsu (`~80%`, bukan `83.47%`).

## 15.2 Satu build UAT harus memaksimalkan informasi

Karena user tidak punya PC dan satu siklus mahal:

- gabungkan instrumentasi yang membedakan seluruh hipotesis utama;
- beri langkah uji singkat dan urut;
- pastikan semua output bisa disalin;
- sertakan expected result per langkah;
- jangan minta user mengulang paket besar bila paket kecil dapat membedakan
  akar yang sama;
- setelah UAT, simpan hasil ke laporan dengan versi/device/timestamp.

## 15.3 Handoff harus executable

Session summary yang baik menyebut:

- branch/commit/status workspace;
- perubahan yang sudah dilakukan;
- command test dan hasil;
- asumsi yang masih terbuka;
- file utama dan invariant;
- next command/next decision;
- hal yang dilarang diulang beserta alasan.

Agent berikutnya harus bisa melanjutkan tanpa membaca seluruh chat, tetapi tetap
wajib membaca PRD/SKILLS yang ditunjuk.

---

# 📚 Referensi engineering untuk bagian baru

- pip CLI — timeout/retries:
  <https://pip.pypa.io/en/latest/cli/pip/>
- pip dependency resolution:
  <https://pip.pypa.io/en/latest/topics/more-dependency-resolution/>
- pip caching:
  <https://pip.pypa.io/en/stable/topics/caching/>
- AWS — timeouts, retries, backoff, jitter:
  <https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/>
- AWS — retry dan idempotency:
  <https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/>
- Google SRE — cascading failures, retry budget, cancellation propagation:
  <https://sre.google/sre-book/addressing-cascading-failures/>
- Kotlin — cooperative cancellation dan structured concurrency:
  <https://kotlinlang.org/docs/cancellation-and-timeouts.html>
- Android — Compose UDF/state holder:
  <https://developer.android.com/develop/ui/compose/architecture>
- Android — long-running work, progress, dan cancel:
  <https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running>
- Android — unique work dan cooperative stop:
  <https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work>

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

---

## SKILL 16 — Uji massal katalog di bionic311 (2026-08-16)

**Konteks.** User meminta semua paket katalog diuji "prinsip coba-search-coba"
sebelum fix. Full emulator = meriam (RAM 1.9GB sandbox tidak kuat 341×QEMU);
senjata yang tepat = bionic311 (qemu-user + Python 3.11 Termux/bionic ARMv7).

**Harness.** `/var/tmp/mass_test.py`: resolve (resolve.py PRODUKSI, abi
armeabi-v7a + tested-manifest) -> download wheel -> extract -> preload lib*.so
(smoke.preload_native_libs) -> import. Resume-safe via jsonl. 286 paket ±80
menit, RAM stabil <1GB. Hasil diarsipkan: docs/mass-test-armv7-2026-08-16.jsonl.

**Label hasil: ARMV7-IMPORT-VERIFIED (bionic311)** — di bawah DEVICE VERIFIED
(tanpa JVM/Chaquopy), di atas "harusnya jalan".

**ARTEFAK HARNESS YANG DIKENAL (JANGAN vonis paket berdasarkan ini):**
1. `cannot import name '_adapters' from 'importlib_metadata'` — shadowing
   backport tua di site-dir uji. Bukti bantahan: click gagal harness tapi
   smoke OK di device user.
2. `dlopen failed: libsqlite3/libexpat/libbz2/libssl_chaquopy/
   libandroid-posix-semaphore` — lib sistem yang Chaquopy SEDIAKAN built-in
   tapi Termux rootfs minimal tidak. Bukti: nltk & exifread gagal harness,
   sukses device.
3. `cannot locate symbol ffi_type_sint8` — libffi Termux != chaquopy-libffi.
4. Setup wajib: LD_LIBRARY_PATH ke usr/lib; libffi_*_arm.deb; ln -s libz.so.1
   libz.so (zlib zipfile).

**Temuan NYATA dari uji ini:** 16 importName salah di katalog; Bug O baru
(sympy/mpmath, aiohttp/yarl.Query); lameenc+pyproj = UNAVAILABLE jujur;
7 paket dependency metadata bolong (docopt, traceback2, dst).
