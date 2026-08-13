# 🔍 LAPORAN KENDALA ZCODE — Audit 2026-08-11 (TANPA PERBAIKAN)

Status: **investigasi saja**. Tidak ada satu baris kode/aset yang diubah.
Semua temuan di bawah dibuktikan dari isi repo (file + baris), bukan tebakan —
kecuali yang gw tandai eksplisit `PERLU VERIFIKASI DEVICE`.

Aturan yang gw pegang: *honest about everything, even our weakness*.

---

## RINGKASAN EKSEKUTIF

| # | Kendala yang lu lapor | Status temuan | Tingkat keyakinan |
|---|---|---|---|
| 1 | Install modul selalu `No module named 'packaging'` | **ROOT CAUSE DITEMUKAN & PASTI** | 100% |
| 2 | LIBRARY → `Detail` malah pop-up ngawur | **ROOT CAUSE DITEMUKAN & PASTI** (2 lapis: UI + data katalog kosong) | 100% |
| 3 | Run code selalu force close (FATAL) | **BELUM PASTI SATU PENYEBAB** — ada 6 kandidat, 3 di antaranya bug nyata di jalur Run | butuh 1 data dari lu (logcat) |

Bonus: 7 temuan lain (dead code, katalog ganda, dokumentasi yang menyesatkan)
yang bikin bug-bug di atas gampang berulang.

---

# 1. FATAL: `No module named 'packaging'` — TERBUKTI 100%

## 1.1 Rantai kejadiannya (persis seperti trace lu)

```
[UI] PipScreen.analyzeThenInstall("colorama")
  └─ PackageEngineV2.analyze()
       └─ RequirementParser.parse()                     (RequirementParser.kt:60)
            └─ PyCall.callJson("package_runtime.requirement",
                               "parse_requirement_json")  (PyCall.kt:22)
                 └─ import package_runtime.requirement
                      └─ requirement.py:14  from packaging.requirements import Requirement
                                            ⇒ ModuleNotFoundError: No module named 'packaging'
```

`PyCall.kt:51` memformat error jadi `"$module.$fn: $it"` — itulah persis string
yang muncul di layar lu:
`package_runtime.requirement.parse_requirement_json: ModuleNotFoundError: No module named 'packaging'`.

## 1.2 Kenapa modulnya nggak ada

`app/build.gradle.kts` baris 91-96 hanya membundel 3 paket ke APK:

```kotlin
pip {
    install("pip==23.3.1")
    install("setuptools==68.2.2")
    install("wheel==0.41.2")
}
```

Komentar di `package_runtime/requirement.py` baris 3-4 mengklaim:

> *"Dibangun di atas `packaging.requirements.Requirement` (**sudah ter-bundle bersama pip 23.3.1 di Chaquopy**)"*

**Klaim itu SALAH.** Gw verifikasi langsung isi wheel-nya di sandbox:

| Wheel | Top-level module yang tersedia | Ada `packaging/` top-level? |
|---|---|---|
| `pip-23.3.1-py3-none-any.whl` | `pip`, `pip-23.3.1.dist-info` | ❌ TIDAK (pip menyimpannya sebagai `pip._vendor.packaging`) |
| `setuptools-68.2.2` | `setuptools`, `pkg_resources`, `_distutils_hack` | ❌ TIDAK (`pkg_resources._vendor.packaging`) |
| `wheel-0.41.2` | `wheel` | ❌ TIDAK (`wheel.vendored.packaging`) |

Vendored ≠ importable. `import packaging` **tidak akan pernah** berhasil di
device dengan konfigurasi build sekarang.

## 1.3 Blast radius: fitur Install 100% MATI di HP

File yang `import packaging` di level atas (fatal saat import modulnya):

| File | Baris | Dipakai untuk |
|---|---|---|
| `package_runtime/requirement.py` | 14, 15 | Parse requirement — **gerbang pertama semua install** |
| `package_runtime/resolve.py` | 19-23 | Dependency resolver (PyPI + Chaquopy index) |
| `package_runtime/wheelinfo.py` | 9, 10 | Pemilihan & ranking wheel |

Karena `RequirementParser.parse()` adalah **langkah 1** di `PackageEngineV2.install()`
(baris 94-100) **dan** di `analyze()` (baris 311), maka:

- ❌ MANUAL INSTALL — mati total
- ❌ LIBRARY → tombol Install / Install Tested Version — mati total (jalurnya sama, `installFromLibrary` → `analyzeThenInstall`)
- ❌ requirements.txt — mati total
- ✅ `package_runtime/probe.py` selamat — dia pakai `try/except` (baris 13-17, 44-47), tapi konsekuensinya `supported_tags` jadi **list kosong** dan `packaging_version` = `"unknown"`

## 1.4 Kenapa CI hijau padahal fitur mati

`.github/workflows/build.yml` baris 23:

```yaml
pip install --break-system-packages pillow pytest packaging
```

CI meng-install `packaging` **di host runner**, jadi 225 test lulus semua
(gw jalankan ulang barusan: `225 passed`). Test **tidak pernah** memverifikasi
bahwa `packaging` ikut masuk ke dalam APK. Ini blind spot guard kita — tidak ada
satu pun test yang membaca blok `pip { }` di `app/build.gradle.kts`.

## 1.5 ⚠️ Lapis kedua yang akan muncul SETELAH `packaging` dibundel

Ini penting — jangan sampai lu kira sekali fix langsung beres. Gw simulasikan:

```
wheel Chaquopy   : numpy-1.26.2-0-cp311-cp311-android_21_arm64_v8a.whl
tag runtime      : packaging.tags.sys_tags()
```

`wheelinfo.wheel_compatible()` (baris 38-65) menerima wheel HANYA jika ada
irisan tag. Di Chaquopy, `sysconfig.get_platform()` diperkirakan mengembalikan
`linux-armv7l` / `linux-aarch64` (bukan `android-21-...`), sehingga `sys_tags()`
menghasilkan `cp311-cp311-linux_armv7l` dkk — **tidak akan pernah** cocok dengan
`android_21_armeabi_v7a`.

Konsekuensi yang diperkirakan:
- ✅ paket pure-Python (`colorama`, `requests`) tetap lolos, karena `py3-none-any` selalu ada di `sys_tags()`
- ❌ semua paket native (numpy, pillow, matplotlib, lxml…) akan dilaporkan `PACKAGE_NOT_AVAILABLE` walaupun wheel-nya JELAS ADA di `https://chaquo.com/pypi-13.1/` (gw sudah cek indeksnya: numpy 1.26.2 & matplotlib 3.6.0 punya wheel cp311 armeabi-v7a + arm64-v8a)

Catatan tambahan: `tested-manifest.json` mem-pin `numpy==1.26.4` dan
`pillow==10.3.0`, tapi Chaquopy hanya menyediakan **numpy 1.26.2** dan
**matplotlib 3.6.0** untuk cp311. Jadi versi "TESTED" kita menunjuk ke versi yang
tidak punya wheel Android → otomatis gagal.

`PERLU VERIFIKASI DEVICE`: nilai `sysconfig.get_platform()` dan `sys_tags()`
sebenarnya di Chaquopy 17/Py3.11. Layar Library sudah menampilkan baris
`Runtime: Python … · ABI … · …` (PipScreen.kt:472) — itu bisa jadi bukti awal.

---

# 2. LIBRARY → "Detail" = pop-up ngawur — TERBUKTI 100%

Lu benar 100%, dan penyebabnya ada **dua lapis** yang saling memperparah.

## 2.1 Lapis A — memang sengaja dibuat pop-up, bukan halaman/layer

`PipScreen.kt:616` — `PackageDetailsDialog` adalah `AlertDialog`, bukan layar.

```kotlin
AlertDialog(
    onDismissRequest = onDismiss,
    title = { … },
    text = {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .height(360.dp)        // ← kotak mati 360dp, di HP kecil = sempit
        ) { … }
```

Masalah UX konkretnya:
1. **Bukan pindah layer.** Padahal pola "pindah halaman 2 level" sudah ada dan terbukti di `SamplesScreen.kt` (kategori → item, lengkap dengan `BackHandler`). Library tidak memakainya.
2. **Tinggi dipaksa `360.dp`** + scroll internal → konten kepotong, semua teks 10–12sp, tanpa hierarki visual. Di layar HD kecil ini persis "kartu sesak".
3. **Tidak ada BackHandler** khusus; hanya dismiss.
4. Tombol Install di dialog → `installFromLibrary()` → **melempar user ke tab MANUAL INSTALL** (`PipScreen.kt:233`). Jadi tap "Install" di LIBRARY, tiba-tiba pindah tab. Ini yang bikin terasa "loncat-loncat".

## 2.2 Lapis B — nomor field bocor & tidak lengkap ("nyeleneh")

`PipScreen.kt:650-665` menulis label mentah dari SPEC ke layar user:

```
"1. What is it?"     "2. Useful for"      "4. Works in ZCODE"
"5. Doesn't work"    "6. Device compatibility"   "7. Tested / latest"
"8. Dependency plan" "9-10. Size"         "12. Risks"
"13. Limitations"    "14. Publisher"      "15. Source"
"16. SHA-256"        "17. License"        "Category / type"
```

- Nomor **3, 11, 18 hilang** → user lihat "…2 … 4 …" dan "…9-10 … 12 …". Ini kelihatan seperti bug/asal-asalan.
- Dokumen `docs/SPEC-001_IMPLEMENTATION_2026_08.md:104` mengklaim *"details 18 field"* — **klaim itu tidak akurat**, yang terpasang 15 label + 1 gabungan.
- Campur bahasa: label Inggris ("What is it?", "Doesn't work") di UI yang isinya bahasa Indonesia.
- **Duplikasi**: `pkg.doesNotWork` dirender DUA KALI — sebagai "5. Doesn't work" dan lagi sebagai "13. Limitations" (baris 653 & 659). Persis sumber kesan "ngawur".
- Dua field bukan data, tapi kalimat template statis:
  - `"9-10. Size"` → selalu *"Download & installed size terhitung saat resolusi…"*
  - `"16. SHA-256"` → selalu *"Diverifikasi saat install…"*

## 2.3 Lapis C — datanya MEMANG kosong untuk 280 dari 300 paket

Gw hitung langsung dari `app/src/main/assets/package_catalog/packages.json`
(300 entri):

| Field | Jumlah entri yang KOSONG | dari |
|---|---|---|
| `dependencies` | **300** | 300 |
| `sha256` | **300** | 300 |
| `abis` | **293** | 300 |
| `testedVersion` | **290** | 300 |
| `smokeTest` | **290** | 300 |
| `doesNotWork` | **284** | 300 |
| `useCases` / `works` / `risks` / `license` / `publisher` | **280** | 300 |
| `description` < 60 karakter | **285** | 300 |

Rata-rata panjang `description` = **33 karakter**. Contoh nyata entri
`anthropic`: `"Anthropic Claude API client."` — titik. Habis.

Karena semua `DetailField` dibungkus `if (…isNotEmpty())`, maka untuk 280 paket
dialognya menyusut jadi cuma:

```
1. What is it?      Gradient boosting (native besar).
7. Tested / latest  belum ditetapkan (ikuti resolusi)
9-10. Size          Download & installed size terhitung saat resolusi…
14. Publisher       -
15. Source          https://pypi.org/project/catboost/
16. SHA-256         Diverifikasi saat install (dari PyPI/Chaquopy).
17. License         -
```

Itu **persis** "singkat, padat, nyeleneh, unorganized" yang lu keluhkan. Bukan
perasaan lu — memang datanya begitu.

Yang benar-benar terisi lengkap cuma ~20 paket kurasi tangan (numpy, requests,
matplotlib, pillow, flask, dst.), sisanya di-generate massal oleh
`tools/generate_catalog.py` cuma dari tuple `(nama, import, kategori, tipe, status, deskripsi-1-kalimat)`.

## 2.4 Lapis D — informasi kompatibilitasnya bisa BOHONG

`packages.json`: **60 paket bertipe `native` punya `abis: []`** (termasuk
`tensorflow`, `torch`, `onnxruntime`, `sentencepiece`, `spacy`, `llama-cpp-python`).

`CompatibilityEngine.kt:73` hanya mengecek ABI kalau `details.abis.isNotEmpty()`:

```kotlin
if (details.type == "native" && details.abis.isNotEmpty()) { … }
```

→ Karena `abis` kosong, pengecekan **dilewati**, dan status akhirnya jatuh ke
`COMPATIBLE` / `EXPERIMENTAL`. Artinya UI bisa bilang `torch` "Compatible" di
Infinix ARMv7. Itu melanggar Rule 2 SPEC sendiri ("jangan bilang Compatible cuma
karena ada di PyPI") dan langsung berujung ke install yang gagal.

## 2.5 Lapis E — ada DUA katalog, satu jadi zombie

| Aset | Isi | Dibaca oleh | Status |
|---|---|---|---|
| `assets/package_catalog/packages.json` | 300 paket | `PackageRepository.kt` → PipScreen | ✅ AKTIF |
| `assets/libraries.json` | 16 paket (skema lama: `summary`, `note`, `ram_mb_hint`, `heavy_on_low_end`) | `core/library/LibraryCatalog.kt` | 💀 **DEAD CODE** |

`LibraryCatalog` dan `DeviceProbe` **tidak dipanggil dari mana pun** (gw grep
seluruh `app/src/main/java` — nol referensi di luar package `core/library`).
Efeknya: fitur "⚠️ berat di HP RAM kecil" yang dirancang di `docs/LIBRARY_DESIGN.md`
**tidak pernah aktif** — heuristik RAM (`DeviceProbe.isLowRam`) tidak dipakai
sama sekali oleh layar yang sekarang.

## 2.6 Temuan kecil di layar LIBRARY

- `PipScreen.kt:468` — placeholder hardcode `"Search 300 packages..."`.
- `PipScreen.kt:511` — `allItems.count { it.category == cat }` dihitung ulang di dalam komposisi setiap kategori → 11 × scan 300 item tiap recomposition (jank kecil di ARMv7).
- `PipScreen.kt:486` — `repository.loadCatalog()` dipanggil di dalam body `LazyColumn` (parse JSON 300 entri pertama kali di main thread; cached setelahnya).
- Tidak ada penanda "stdlib" di UI walaupun `stdlib.json` (305 modul) sudah dimuat `PackageRepository.loadStdlib()` — fungsi itu **tidak dipanggil siapa pun**.

---

# 3. FATAL saat Run — 6 kandidat, belum bisa gw kunci satu

Gw jujur: **gw belum bisa memastikan penyebab tunggalnya tanpa logcat.**
Yang bisa gw lakukan: menemukan bug nyata di jalur Run dan mengurutkannya
berdasarkan probabilitas. Tiga yang pertama adalah bug beneran (bukan dugaan),
tinggal dibuktikan mana yang jadi FATAL di HP lu.

## K-1. Disk I/O di MAIN THREAD tiap batch output → ANR/force close ⭐ paling curiga

`TerminalScreen.kt:135-146`, `appendToTerminal()` dijalankan di **Main dispatcher**
(dipanggil via `scope.launch` dari thread batcher):

```kotlin
buffer.append(text)
memChars += text.length
TelemetryStore.recordPeak("terminal_memory_peak_chars", memChars)  // ← tulis file
logger?.append(stream, text)                                        // ← tulis file + flush()
logBytes = logger?.bytesWritten ?: 0L
TelemetryStore.recordPeak("terminal_log_bytes", logBytes)           // ← tulis file lagi
```

- `TelemetryStore.recordPeak` → `saveLocked()` → **tulis ulang seluruh `telemetry.json` + rename**, DUA KALI per batch (TelemetryStore.kt:61-68, 104-118).
- `RunLogger.append` → `w.flush()` **tiap batch** (RunLogger.kt:83).
- `OutputBatcher` flush tiap **40 ms** (OutputBatcher.kt:18) ⇒ ±25 batch/detik.

⇒ **±75 operasi tulis file per detik di UI thread** untuk script yang nge-print
dalam loop. Di Infinix ARMv7 (eMMC lambat) ini resep ANR → sistem munculin
"ZCODE isn't responding" / force close.

Ditambah: tiap batch juga memicu `listState.scrollToItem()` (baris 148-155) dan
recomposition seluruh `TerminalScreen`.

## K-2. Key `LazyColumn` salah tulis + tidak stabil ⭐ bug pasti

`TerminalScreen.kt:412`:

```kotlin
item(key = { -1L }) {     // ← yang dikirim BUKAN -1L, tapi objek lambda Function0
```

Maksudnya jelas `key = -1L`. Yang terkirim: sebuah **lambda**. Kontrak Compose:
*"key harus bisa disimpan ke Bundle"*. Lambda Kotlin lolos cek `canBeSaved`
(karena `kotlin.jvm.internal.Lambda` implements `Serializable`) — jadi **bukan
crash instan**, tapi jadi bom waktu saat save-state/rotasi layar, dan key-nya
tipenya beda sendiri dari item lain (Long).

Yang lebih berbahaya, baris 399:

```kotlin
items(relCount, key = { it -> (buffer.startOffset + it) })
```

`buffer.startOffset` **bukan Compose state** (`TerminalBuffer.kt:19`, plain `var`)
dan **berubah saat buffer di-trim** di 10.000 baris. Compose menghitung key
secara lazy saat measure. Kalau `startOffset` bergeser di antara komposisi dan
evaluasi key, Compose bisa melihat key ganda ⇒
`IllegalArgumentException: Key … was used multiple times` = **FATAL**.
Ini kandidat kuat untuk script yang output-nya banyak.

Catatan sekaligus: karena `buffer.lineCount`/`startOffset` bukan state,
terminal cuma ke-refresh sebagai efek samping dari `memChars`/`logBytes` yang
kebetulan state. Arsitekturnya rapuh.

## K-3. `FocusRequester.requestFocus()` terlalu dini

`TerminalScreen.kt:240-242`:

```kotlin
LaunchedEffect(Unit) { focusRequester.requestFocus() }
```

target fokusnya `TextField` berukuran **1.dp** (baris 460). `LaunchedEffect`
jalan setelah komposisi tapi **sebelum layout/placement**. Ini pola klasik
penyebab `IllegalStateException: FocusRequester is not initialized`.
`PERLU VERIFIKASI DEVICE` — tergantung timing, kadang lolos kadang crash
(cocok dengan gejala "selalu" kalau device-nya lambat).

## K-4. Tidak ada mekanisme KILL nyata untuk Chaquopy → kebocoran thread & RAM

`ExecutionEngine.kt:325-329` — `sendKill()` untuk ChaquopySession cuma memanggil
`bridge.interrupt()`, yaitu **menyalakan sebuah flag boolean**. Flag itu hanya
dibaca oleh `BridgeStdin.readline()` (zcode_runner.py:61). Artinya:

- Script `while True: pass` (tanpa `input()`) **TIDAK BISA DIHENTIKAN** — Ctrl+C, Stop, maupun Back tidak berefek.
- `DisposableEffect.onDispose` (baris 244-248) memanggil `sendKill()` lalu keluar; thread Python **tetap hidup di proses yang sama**.
- Tiap kali lu tap Run lagi → thread Python baru menumpuk, semuanya `os.chdir()` ke cwd global yang sama, semuanya makan RAM.

Di HP RAM kecil, penumpukan ini = **OOM kill = force close**. Konsisten dengan
"selalu force close" kalau lu sudah beberapa kali Run.

## K-5. Terminal ditumpuk DI ATAS WebView editor yang masih hidup

`WorkbenchScreen.kt:626-641` — TerminalScreen dirender sebagai **overlay** di
dalam Workbench, bukan pindah route. Jadi saat Run:

- WebView + bundle CodeMirror 449 KB tetap hidup di memori
- \+ interpreter CPython 3.11 in-process (`Python.start()`)
- \+ seluruh Compose tree editor

Di Infinix Smart 9 HD, kombinasi ini rawan **low-memory kill** yang di mata user
= force close, tanpa stacktrace Java sama sekali (cuma `Process ... died` di logcat).

## K-6. `Python.start()` bisa dipanggil balapan dari 3 tempat

`Python.start(AndroidPlatform(...))` muncul di 6 lokasi
(`WorkspaceViewModel:140`, `ExecutionEngine:296` & `:377`, `PyCall:37`,
`RuntimeProbe:65`, `PluginRunner:50` & `:61`), semuanya dengan pola
**cek-lalu-start yang tidak sinkron**:

```kotlin
if (!Python.isStarted()) { Python.start(AndroidPlatform(appContext)) }
```

`WorkspaceViewModel.init` menjalankan `preWarmPython()` di thread IO saat app
buka. Kalau lu langsung tap Run, thread `ChaquopySession` bisa lolos cek yang
sama → `Python.start()` dua kali. Di sisi Java ini `IllegalStateException`
(tertangkap `catch`), tapi race di layer native Chaquopy `PERLU VERIFIKASI`.
Minimal ini bikin Run pertama gagal dengan pesan aneh, bukan crash.

## Yang gw BUTUH dari lu untuk mengunci FATAL ini

Satu di antara ini sudah cukup:

1. **Logcat** saat crash (paling ideal):
   `adb logcat -d | grep -iE "AndroidRuntime|FATAL|zcode|chaquopy"` — 40 baris terakhir.
2. Kalau nggak ada PC: gw bisa **menambahkan crash handler** (`Thread.setDefaultUncaughtExceptionHandler`) yang menulis stacktrace ke `filesDir/logs/crash-*.txt` + tombol Export di layar About. Ini perubahan kode, jadi gw tunggu ACC lu.
3. Info tambahan yang membantu: crash-nya terjadi **sebelum atau sesudah** teks "Menyalakan Python…" muncul? Kalau sebelum → arah K-3/K-6. Kalau sesudah + ada output → arah K-1/K-2. Kalau layar hitam lalu balik ke home tanpa dialog → arah K-4/K-5 (OOM).

---

# 4. TEMUAN LAIN (tidak lu laporkan, tapi bakal menggigit)

| Kode | Temuan | Bukti |
|---|---|---|
| L-1 | **Dua pintu masuk Terminal.** Route `output/{filename}` di `MainActivity.kt:80` **tidak pernah dinavigasi** — `WorkbenchScreen` pakai overlay `showTerminalOverlay`. Parameter `onRun` (`WorkbenchScreen.kt:130`) jadi dead parameter. Fix di satu jalur tidak berefek ke jalur lain. | MainActivity.kt:58-60, WorkbenchScreen.kt:509-524 |
| L-2 | **`ExecutionEngine.startPipStream` / `zcode_pip.py` (legacy)** masih hidup penuh dengan 3 lapis monkey-patch, padahal UI sudah pindah ke PackageEngineV2. Dua backend paket = melanggar Rule 7 SPEC sendiri. | ExecutionEngine.kt:365-410 |
| L-3 | **`gradlew` bukan wrapper asli** — cuma shell stub yang mendelegasi ke `gradle` sistem. Build lokal/offline tidak reproducible. | `gradlew` (7 baris) |
| L-4 | **material3 di-pin `1.1.2`** sementara Compose BOM 2024.02 memberi ui/foundation `1.6.1`. Kombinasi lintas-versi ini tidak didukung resmi dan pernah jadi sumber `NoSuchMethodError` runtime. `PERLU VERIFIKASI` | app/build.gradle.kts:110-115 |
| L-5 | **Dokumentasi menyesatkan** di 3 tempat: (a) `requirement.py` bilang packaging ter-bundle (salah), (b) `SPEC-001_IMPLEMENTATION` bilang "18 field" (aktual 15+1), (c) `CHAQUOPY_STRATEGY` tabel masih tulis plugin 15.0.1 padahal aktual 17.0.0. | — |
| L-6 | **`tested-manifest.json` menunjuk versi yang tak punya wheel Android**: numpy 1.26.4 (Chaquopy punya 1.26.2), pillow 10.3.0, matplotlib 3.6.0 (yang ini ada). | tested-manifest.json vs chaquo.com/pypi-13.1 |
| L-7 | **Tidak ada guard test** untuk isi blok `chaquopy { pip { } }`. Bug #1 lolos ke device justru karena ini. 225 test hijau, fitur mati. | tools/check.sh, test_*.py |

---

# 5. RENCANA PERBAIKAN YANG GW USULKAN (menunggu ACC lu)

Gw **tidak akan menyentuh apa pun** sebelum lu pilih. Ini opsinya, lengkap dengan
konsekuensi jujurnya.

### Blok A — Fix `packaging` (kecil, dampak besar)
- **A1** Tambah `install("packaging==24.1")` di blok `chaquopy.pip` + guard test yang gagal kalau baris itu hilang.
  *Risiko: nol. Ukuran APK +~100 KB.*
- **A2** (rekomendasi gw, dibarengi A1) Perbaiki juga **pencocokan tag Android**: jangan andalkan `sys_tags()` mentah; suntik daftar tag Android (`cp311-cp311-android_21_<abi>`) dari hasil `RuntimeProbe`. Tanpa ini, semua paket native tetap "UNAVAILABLE".
  *Risiko: sedang — logika resolver berubah, butuh test baru + verifikasi di HP lu.*
- **A3** Sinkronkan `tested-manifest.json` dengan wheel yang benar-benar ada di indeks Chaquopy untuk cp311.

### Blok B — LIBRARY jadi halaman detail beneran
- **B1** Ganti `PackageDetailsDialog` (AlertDialog) → **layar penuh 2 level** meniru `SamplesScreen` (dengan `BackHandler`), section bertajuk bahasa Indonesia, tanpa nomor mentah, tanpa duplikasi `doesNotWork`.
- **B2** **Isi ulang katalog**: naikkan kualitas `packages.json` dari 20 entri kurasi → target 300 entri dengan `useCases`/`works`/`doesNotWork`/`risks`/`license`/`publisher`/`abis` terisi. Ini pekerjaan data terbesar; gw usul bertahap (Tier 1: 50 paket paling sering dipakai dulu).
  *Jujur: mengisi 300 entri dengan data akurat itu berat & rawan halusinasi. Gw lebih suka 50 entri BENAR daripada 300 entri karangan. Perlu keputusan lu.*
- **B3** Isi `abis` untuk 60 paket native, supaya status INCOMPATIBLE jujur muncul (bukan "Compatible" palsu).
- **B4** Hapus zombie `libraries.json` + `LibraryCatalog` + `DeviceProbe`, ATAU hidupkan lagi heuristik RAM-nya ke dalam `CompatibilityEngine`. Pilih satu — jangan dua-duanya hidup.
- **B5** Tombol Install di LIBRARY jangan lempar user ke tab MANUAL; console-nya dimunculkan di konteks Library.

### Blok C — FATAL saat Run
- **C0** (dulu, sebelum apa pun) Pasang crash handler → file `logs/crash-*.txt` + Export. Supaya kita berhenti menebak.
- **C1** Pindahkan `TelemetryStore` + `RunLogger` ke thread IO + throttle (mis. flush tiap 1 dtk / saat exit). Menghilangkan 75 tulis-file/detik di UI thread.
- **C2** Perbaiki key LazyColumn: `key = -1L` (bukan lambda), dan jadikan `TerminalBuffer` sumber data ber-state yang stabil (snapshot line-list) supaya key tidak bisa ganda.
- **C3** `requestFocus()` ditunda sampai node ter-place (mis. lewat `awaitFrame()`), + bungkus `runCatching`.
- **C4** Stop/Ctrl+C yang beneran: opsi (a) jalankan script pakai `sys.settrace` watchdog agar bisa dihentikan di tengah loop, atau (b) pisahkan eksekusi ke `:python` process terpisah supaya bisa di-`kill` sungguhan. (b) paling benar, paling mahal.
- **C5** Jadikan Terminal **pindah layer** (route `output/{filename}` yang sudah ada) dan lepaskan WebView editor saat Run, supaya jejak RAM turun. Sekaligus menghapus dead code L-1.

### Urutan yang gw sarankan
```
C0 (crash log)  →  A1  →  C1 + C2 + C3  →  [verifikasi di HP lu]  →  A2 + A3  →  B1  →  B3/B4/B5  →  B2 (data, bertahap)
```

---

## Yang gw butuh lu putuskan

1. **FATAL**: gw pasang crash handler dulu (C0), atau lu bisa kirim logcat?
2. **Katalog Library (B2)**: mau 300 entri "seadanya tapi rapi", atau 50 entri benar-benar akurat dulu lalu ditambah bertahap?
3. **Stop/Ctrl+C (C4)**: cukup watchdog in-process (murah, tidak 100%), atau proses Python terpisah (benar, tapi refactor besar)?
4. Boleh gw kerjakan **A1 + C0** duluan sebagai paket kecil yang aman, atau lu mau semua nunggu satu rencana besar?

Nol baris kode diubah sampai lu jawab.
