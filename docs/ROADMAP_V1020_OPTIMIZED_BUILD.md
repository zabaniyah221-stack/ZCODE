# Roadmap v1.0.20 — One Optimized APK Performance Gate

Tanggal audit: 2026-08-20
Status: **OPTIMIZED BUILD CI + DEVICE VERIFIED; terminal keyboard fix LOCALLY VERIFIED**
Basis: `main` merge commit `cd982da` (v1.0.19 correctness/stability)
Target branch saat implementasi: `arena/v1020-performance`

Dokumen ini menjawab keputusan user: buat **satu APK optimized**, tanpa toggle,
tanpa Performance Recorder/JankStats, tanpa upgrade Compose, dan tanpa perubahan
UX/runtime. APK ini menjadi jembatan empiris dari v1.0.19 menuju v1.0.20.

---

# 1. INTI

## 1.1 Tujuan

Tujuan batch ini bukan mencari akar setiap nanodetik. Tujuannya menjawab satu
pertanyaan produk:

> **Apakah ZCODE dengan source/perilaku v1.0.19 yang sama, tetapi dibangun
> non-debuggable dan dioptimalkan secara aman, sudah terasa responsif dan smooth
> pada INFINIX X6532C ARMv7?**

Gejala device yang menjadi dasar:

- seluruh tap terasa lambat pada fase awal;
- navigasi/pindah layar terlambat merespons;
- scroll Compose dan editor terasa tertahan;
- fling/scroll bebas cepat kehilangan momentum;
- setelah Run 2–3 kali, swipe sedikit lebih lancar, tetapi tap/navigasi/scroll
  masih belum memuaskan.

Status v1.0.19 yang tidak dibuka kembali:

```text
Correctness      : DEVICE VERIFIED
Stability        : DEVICE VERIFIED
Merged           : ya
Public release   : ditahan karena quality/performance gap
```

## 1.2 Non-goals

Batch pertama **tidak**:

- menambah toggle Performance Lab;
- menambah JankStats, FrameMetrics, recorder, FPS overlay, atau telemetry;
- mengubah Python prewarm;
- mengubah drawer gesture atau delay navigasi;
- mengubah CodeMirror extension, line wrapping, lint, atau history;
- upgrade Compose/Kotlin/AGP/Chaquopy/Python;
- membuat production signing key;
- membuat GitHub Release;
- memperbaiki source hotspot sebelum build A/B memberi bukti;
- mengklaim optimized APK sebagai release publik.

Existing ZCODE Debug di HP adalah baseline. Hanya **satu APK baru** yang perlu
diunduh.

---

## 1.3 Audit kondisi build saat ini

### Debug artifact canonical v1.0.19

```text
variant          : debug
applicationId    : com.zaba.zcode.debug
debuggable       : true
R8               : OFF
debug-only deps  : ui-tooling + ui-test-manifest
app profile      : tidak ada custom Baseline Profile
```

CI saat ini hanya menjalankan `assembleDebug`.

Android menegaskan debug mode membawa biaya performa besar dan tidak layak
menjadi hakim benchmark. Lazy layout juga hanya dapat dinilai andal pada build
release/R8. Sumber:

- https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- https://developer.android.com/develop/ui/compose/performance
- https://developer.android.com/develop/ui/compose/lists

### Release config saat ini

```text
applicationId    : com.zaba.zcode
R8               : OFF
signingConfig    : belum ada
```

Release production tidak disentuh batch ini.

### R8 guard saat ini

`app/proguard-rules.pro` memuat:

```proguard
-keep class com.zaba.zcode.** { *; }
```

Rule tersebut menjaga semuanya tetapi juga mencegah shrinking, obfuscation, dan
optimasi efektif pada seluruh kode ZCODE. Menyalakan R8 sambil mempertahankan
rule ini menghasilkan eksperimen yang menyesatkan. Performance build harus
memakai file rules terpisah; jangan mewarisi keep-all.

### Boundary yang dipanggil tidak langsung

Audit source menemukan boundary berikut:

1. `EditorBridge` dipanggil JavaScript melalui `@JavascriptInterface`:
   `onCodeChange`, `onEditorReady`, `getCode`.
2. `TerminalBridge` dipanggil Python/Chaquopy berdasarkan nama:
   `write`, `readLine`, `waitingInput`, `isInterrupted`, `workspaceDir`,
   `setWorkerThread`, dan `onExit`.
3. `ResolveOperationBridge` dipanggil Python berdasarkan nama:
   `emit` dan `isCancelled`.
4. `com.chaquo.python.Python` dicek melalui `Class.forName` dan juga dipakai
   langsung oleh Kotlin.
5. `Process.pid()` diakses melalui reflection pada framework runtime class;
   R8 tidak mengubah framework Android/Java.
6. Hilt/Dagger menghasilkan direct references dan mendukung R8/ProGuard; library
   consumer rules tetap dihormati.
7. Android components `ZcodeApp`, `MainActivity`, dan `ZcodeRebirthActivity`
   direferensikan manifest; performance rules tetap memberi safety keep kecil.

Implikasi: keep rule tidak boleh hanya menjaga `@JavascriptInterface`; bridge
Kotlin↔Python juga taruhan nyawa.

### Chaquopy + R8

Chaquopy pernah memiliki issue AGP 8/R8 missing `NotNull` (#842), tetapi issue
tersebut ditutup setelah tidak lagi reproducible pada development version. Jangan
menambah `-dontwarn` secara spekulatif. Jika build menghasilkan
`missing_rules.txt`, audit exact class dulu.

Sumber:

- https://github.com/chaquo/chaquopy/issues/842
- https://chaquo.com/chaquopy/doc/current/changelog.html

### Hilt + R8

Dagger secara eksplisit dirancang kompatibel dengan ProGuard/R8. Kita tetap
menguji startup/injection, tetapi tidak menambah keep-all Hilt tanpa bukti.

Sumber: https://dagger.dev/dev-guide/android.html

---

## 1.4 Audit hotspot source — dicatat, belum diperbaiki

Hotspot ini relevan bila APK optimized belum cukup:

1. `EditorScreen` melakukan `post` + beberapa `evaluateJavascript` langsung dari
   body composable pada setiap recomposition. Ini dapat mengirim ulang setting,
   diagnostics, dan font ke WebView saat `activeCode/problems/history` berubah.
2. `WorkbenchScreen` membaca `vm.activeCode` di owner yang besar; typing dapat
   memperluas recomposition scope.
3. `closeDrawerThen` selalu menunggu animasi 150 ms sebelum action/navigation.
4. Python prewarm dimulai di `WorkspaceViewModel.init` saat Compose/WebView juga
   cold-start.
5. `ModalNavigationDrawer` membungkus WebView. WebView/Compose gesture interop
   punya keterbatasan resmi, tetapi drawer sebagai akar vertical-fling ZCODE
   masih hipotesis, bukan verdict.
6. Library/catalog dan beberapa layar melakukan kalkulasi/filtering ketika
   composition pertama.
7. CodeMirror ZCODE `@codemirror/view 6.43.8` sudah modern dan lebih baru dari
   banyak scroll stabilization fixes; 100–1.000 baris tidak seharusnya menjadi
   beban inti CM6.

Sumber:

- https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/wrap-webview-in-compose
- https://github.com/android/compose-samples/issues/738
- https://codemirror.net/docs/changelog/
- https://codemirror.net/examples/million/

Hotspot di atas tidak dicampur dengan P0 supaya perubahan build menjadi satu
variabel besar yang dapat dinilai langsung.

---

## 1.5 Rencana implementasi

### P0 — Branch dan rollback

Buat branch dari `origin/main`:

```text
arena/v1020-performance
```

Commit dipisah:

1. build variant + R8 rules + manifest overlay;
2. permanent guards + mutation proof;
3. workflow performance + artifact verification;
4. dokumentasi/UAT contract.

`main`, `debug`, dan `release` existing tidak boleh berubah perilaku.
Rollback = revert rangkaian commit atau hapus build type `performance`.

### P1 — Build type `performance`

Target konfigurasi:

```kotlin
buildTypes {
    create("performance") {
        applicationIdSuffix = ".performance"
        versionNameSuffix = "-perf1"
        isDebuggable = false
        isProfileable = true
        isMinifyEnabled = true
        isShrinkResources = false
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += listOf("release")
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-performance.pro"
        )
    }
}
```

Kenapa tidak `initWith(release)`: release sekarang membawa
`proguard-rules.pro` keep-all. Build type performance dibuat eksplisit agar tidak
mewarisi rule tersebut secara tidak sengaja.

Karakteristik APK:

```text
Nama launcher   : ZCODE Performance
Application ID  : com.zaba.zcode.performance
Version         : 1.0.19-perf1
Debuggable      : false
Profileable     : true
R8              : ON
Resource shrink : OFF
Signing         : CI debug key sementara
```

Keputusan implementasi: label `1.0.19-perf1`. Source fungsional sengaja identik
dengan v1.0.19; menyebutnya 1.0.20 sebelum hasil device akan melebihkan status.
Ia tetap jembatan menuju v1.0.20. Version code memakai source `22`; package ID
berbeda sehingga tidak berkonflik.

### P2 — Isolasi task/application

Performance dan Debug akan dipasang berdampingan. Main manifest sekarang
meng-hardcode:

```text
android:taskAffinity="com.zaba.zcode"
```

Kalau dibiarkan, dua package berbeda dapat berbagi affinity dan merusak A/B
(coexistence/task routing). Tambahkan source-set overlay:

```text
app/src/performance/AndroidManifest.xml
app/src/performance/res/values/strings.xml
```

Overlay wajib:

- label `ZCODE Performance`;
- `MainActivity.taskAffinity = com.zaba.zcode.performance`;
- tidak mengubah `:rebirth`, exported flags, launchMode, permissions, atau
  soft-input contract;
- profileable hanya untuk variant performance.

Debug/production manifest tetap identik dengan v1.0.19.

### P3 — Conservative but real R8 rules

File baru:

```text
app/proguard-performance.pro
```

Prinsip awal:

```proguard
# Pertahankan nama agar crash/diagnostics tetap terbaca pada perf1.
-dontobfuscate

# Annotation/reflection metadata minimum.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# JS → Kotlin WebView bridge.
-keepclassmembers,allowoptimization class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Python → Kotlin Chaquopy proxy: nama dan member publik adalah API runtime.
-keep,allowoptimization class com.zaba.zcode.core.execution.TerminalBridge { public *; }
-keep,allowoptimization class com.zaba.zcode.core.packageengine.ResolveOperationBridge { public *; }

# Chaquopy/JNI boundary: broad keep sementara untuk perf1.
-keep class com.chaquo.python.** { *; }

# Android/Hilt entry points: kecil dan eksplisit sebagai safety layer pertama.
-keep class com.zaba.zcode.ZcodeApp { *; }
-keep class com.zaba.zcode.MainActivity { *; }
-keep class com.zaba.zcode.ZcodeRebirthActivity { *; }
```

Catatan:

- `allowoptimization` tetap membolehkan optimasi method body, tetapi tidak
  membolehkan member Python/JS hilang atau berganti nama.
- Jangan `-keep com.zaba.zcode.**` pada performance build.
- Jangan `-dontoptimize`; tujuan APK memang menguji optimizer.
- Resource shrinking OFF supaya icon/font/assets/editor tidak menjadi variabel
  tambahan.
- Mulai dengan R8 compatibility mode untuk mengurangi risiko full-mode. Opsi
  implementasi: property sementara `android.enableR8.fullMode=false` yang
  didokumentasikan removal condition-nya. Karena hanya performance variant yang
  minified, debug/release v1.0.19 tidak terpengaruh.
- Setelah perf1 lulus, targeted keep dapat dipersempit dan full mode diuji pada
  batch terpisah; jangan dilakukan sebelum hasil device.

Android menyarankan adopsi R8 bertahap dan menghindari keep-all jangka panjang:

- https://developer.android.com/topic/performance/app-optimization/adopt-optimizations-incrementally
- https://developer.android.com/topic/performance/app-optimization/keep-rules-best-practices
- https://developer.android.com/topic/performance/app-optimization/troubleshoot-the-optimization

### P4 — Build reports, bukan recorder di APK

Agar satu APK tetap sederhana, detail teknis dikumpulkan di CI, bukan UI:

- R8 `mapping.txt`;
- `configuration.txt` (semua merged rules);
- `usage.txt` (kode yang dibuang);
- `seeds.txt` bila tersedia;
- `missing_rules.txt` bila build gagal;
- APK size;
- aapt badging/manifest dump;
- apksigner certificate output;
- optional Compose compiler metrics/reports sebagai artifact build, bukan
  runtime telemetry.

R8 mapping/configuration tidak boleh dipublikasikan sebagai release artifact
jika kelak berisi informasi yang tidak layak publik; untuk branch internal boleh
menjadi CI artifact dengan retention terbatas.

### P5 — Dedicated performance workflow

Rekomendasi file:

```text
.github/workflows/performance.yml
ci/workflows/performance.yml  # mirror sesuai konvensi repo
```

Trigger:

```text
workflow_dispatch
push hanya branch arena/v1020-performance
```

Workflow:

1. checkout;
2. Python 3.11 (Chaquopy buildPython);
3. JDK 17;
4. Android SDK 34;
5. Gradle 8.5;
6. local full gate;
7. `assemblePerformance`;
8. verify APK;
9. upload satu artifact `ZCODE-v1.0.19-perf1` berisi satu APK + checksum;
10. upload R8/build reports sebagai artifact teknis terpisah agar user hanya
    perlu mengambil artifact APK.

Verifikasi APK wajib:

```text
applicationId    = com.zaba.zcode.performance
versionName      = ...-perf1
debuggable       = false
profileable      = true
label            = ZCODE Performance
taskAffinity     = com.zaba.zcode.performance
MainActivity     = singleTop
RebirthActivity  = :rebirth + exported=false
ABI              = armeabi-v7a, arm64-v8a, x86_64
CM6 bundle       = ada
Python assets    = ada
signature        = valid debug test certificate
```

Workflow existing `build.yml` tidak diubah. Perubahan workflow perlu credential
GitHub dengan izin workflow; diskusikan sebelum push.

### P6 — Permanent guards

Guard source setelah comment stripping:

1. performance application ID berbeda dari debug/production;
2. `isDebuggable=false`;
3. `isProfileable=true`;
4. `isMinifyEnabled=true`;
5. `isShrinkResources=false` untuk perf1;
6. performance memakai optimized default rules;
7. performance rules tidak memuat keep-all ZCODE;
8. JS bridge annotation keep ada;
9. TerminalBridge dan ResolveOperationBridge keep ada;
10. Chaquopy boundary keep ada;
11. release/debug config existing tidak berubah;
12. taskAffinity performance unik;
13. label launcher berbeda;
14. no signing secret/JKS/password di repo;
15. workflow membangun `assemblePerformance`, bukan `assembleDebug`;
16. APK dan mapping/report artifact dipisahkan;
17. no JankStats/toggle/Compose upgrade masuk P0.

Mutation proof minimum:

```text
performance debuggable=true              → RED
R8 performance dimatikan                 → RED
keep-all com.zaba.zcode dikembalikan     → RED
JS bridge keep dihapus                   → RED
TerminalBridge keep dihapus              → RED
ResolveOperationBridge keep dihapus      → RED
Chaquopy keep dihapus                     → RED
taskAffinity disamakan dengan Debug      → RED
workflow kembali assembleDebug           → RED
secret/JKS fixture dimasukkan             → RED
restore                                   → GREEN
```

### P7 — Local validation

```bash
bash tools/check.sh
python3 tools/kotlin_sanity_check.py
python3 tools/npm_supply_chain_check.py
git diff --check
```

Tambahan:

- review variant dependency graph: ui-tooling/ui-test-manifest tidak boleh masuk;
- inspect source-set merge;
- scan credential/JKS;
- inspect mode changes;
- review R8 rules dan Python-called method names;
- jangan klaim compile sebelum CI.

### P8 — CI verification

Status maksimal setelah build:

```text
IMPLEMENTED + LOCALLY VERIFIED + CI VERIFIED
```

Belum DEVICE VERIFIED. Bila raw CI log tak dapat diakses, user sudah menawarkan
mengambil log; agent wajib meminta log nyata, bukan menebak.

### P9 — Device UAT satu APK

Install Performance berdampingan dengan ZCODE Debug. Jangan uninstall Debug.

#### Functional gate — R8 tidak boleh merusak

1. cold launch, editor tampil;
2. edit + save + restart;
3. keyboard, selection, Find, lint;
4. Undo/Redo per-file;
5. multi-file switch;
6. Run `print` dan `input()`;
7. traceback jump;
8. Settings/Samples/Library/Diagnostics;
9. pure install Colorama;
10. native package path + Binary Rain + workspace restore;
11. jalur `Nanti` + Run/package gates;
12. Diagnostics tidak mencatat Java crash;
13. rebirth helper tetap PID terpisah.

#### UX comparison

Sebelum Run apa pun:

- tap drawer/menu;
- pindah Settings/Samples/Library;
- tap Library↔Manual;
- scroll dan fling setiap layar;
- import file identik 100/500/1.000 baris via SAF;
- scroll editor dengan keyboard tutup dan buka.

Lalu Run 3 kali dan ulangi.

User cukup memberi verdict:

```text
A. Jauh lebih lancar
B. Lumayan lebih lancar, masih ada tahan/jeda
C. Hampir sama
D. Ada fungsi rusak
```

Tidak perlu angka palsu.

### P10 — Decision gate

#### Hasil A

- Optimized build menjadi fondasi v1.0.20.
- Tidak pasang Performance Recorder.
- UAT regression diperluas sebelum mengubah release config production.
- Public release/signing tetap diskusi terpisah.

#### Hasil B

- Pertahankan optimized build.
- Prioritas source berdasarkan gejala tersisa:
  1. repeated JS bridge work pada recomposition;
  2. explicit drawer delay 150 ms;
  3. prewarm contention;
  4. drawer/WebView gesture;
  5. recomposition scope.
- Tambah alat diagnosis hanya bila satu gejala tidak dapat dibedakan murah.

#### Hasil C

- Build mode/R8 bukan akar dominan.
- Jangan lanjut full R8/production pipeline karena harapan performa.
- Baru implement Performance Recorder manual di Diagnostics atau isolated
  Compose upgrade A/B.

#### Hasil D

- Stop. Jangan patch acak.
- Ambil screen/diagnostics + fungsi yang rusak.
- Gunakan R8 `mapping/configuration/usage/missing_rules`.
- Tambahkan keep rule paling sempit; mutation guard; build ulang.
- Jika blast radius tidak terkendali, revert performance variant ke
  non-debuggable/no-R8 sebagai kontrol berikutnya.

---

## 1.6 Kendala yang mungkin muncul

| Kendala | Kemungkinan | Dampak | Penanganan |
|---|---:|---:|---|
| R8 menghapus/merename method Python bridge | tinggi bila tanpa rules | Run/package mati | exact keep TerminalBridge + ResolveOperationBridge; UAT |
| R8 merusak JS bridge | sedang | editor tidak mengirim code/ready | annotation keep + bridge contract guard |
| Chaquopy/JNI minify issue | sedang-rendah pada v17 | startup Python/build gagal | broad keep Chaquopy; audit missing rules; jangan suppress buta |
| Hilt generated component rusak | rendah | app gagal start | library consumer rules; startup CI/device gate |
| TaskAffinity bentrok Debug/Performance | tinggi bila hardcode dibiarkan | app/task ketuker | performance manifest overlay + aapt guard |
| Debug key CI berubah pada perf2 | pasti antarrunner | update performance APK ditolak | perf1 hanya satu install; perf2 uninstall/reinstall; production signing terpisah |
| Data/package env terpisah | pasti | A/B tidak identik otomatis | import file yang sama via SAF; built-in screen comparison |
| R8 build OOM/timeout CI | rendah-sedang | artifact gagal | Gradle 8.5/JDK17; max workers; inspect log; no emulator concurrently |
| Raw CI log tidak dapat diakses | sudah pernah | diagnosis tertunda | minta user mengambil exact build log |
| APK smooth tetapi sebab tidak diketahui | diterima | pengetahuan terbatas | goal produk tercapai; catat build optimization dominant |
| APK masih tertahan | mungkin | perlu batch kedua | hotspot source berurutan, bukan semua toggle |
| R8 mapping membuat stacktrace sulit | dicegah perf1 | diagnosis sulit | `-dontobfuscate`; mapping tetap diarsipkan |
| Compose 1.6.1 punya perf/fling debt | mungkin | sisa lag | isolated Compose upgrade setelah P0, ulang focus/IME UAT |

Semua kendala di atas dapat ditangani. Tidak ada yang membenarkan menyentuh
production signing atau mengorbankan v1.0.19 Debug pada P0.

---

## 1.7 Target

### Target proses

- satu APK baru, satu download;
- source/UX/runtime tetap;
- no secret/signing production;
- commit reversible;
- guards mutation-proven;
- CI artifact + checksum + R8 reports;
- Debug tetap terpasang sebagai baseline/rollback.

### Target fungsi

- 100% gate v1.0.19 tetap hidup;
- editor bridge, Python bridge, package engine, dan rebirth tidak regresi;
- no Java crash;
- no data loss.

### Target UX

Pada cold start, sebelum Run:

- tap pertama memberi respons tanpa pemanasan 2–3 Run;
- perpindahan layar tidak terasa diam lalu melompat;
- drag scroll mengikuti jari;
- fling tidak terasa tertahan atau mati prematur;
- editor 100/500/1.000 baris usable;
- keyboard tidak memperburuk scroll secara drastis.

Target tidak menjanjikan 120 FPS. Definisi produk:

> **ZCODE tidak terasa melawan jari user pada perangkat target.**

### Target status

```text
P0 success + functional UAT : DEVICE VERIFIED PERFORMANCE CANDIDATE
v1.0.20 production          : belum, sampai hasil P0 diadopsi ke app utama
public release              : tetap ditahan sampai UX layak + signing contract
```

---

# 2. PENUNJANG

## 2.1 Context7 / MCP

Context7 MCP tool khusus tidak tersedia di sesi agent ini; audit tidak
mengarang tool call. Public Context7 dapat diakses:

- https://context7.com/android/performance-samples
- raw curated snippets:
  https://context7.com/android/performance-samples/llms.txt?tokens=10000

Context7 mengindeks repo resmi `android/performance-samples` (Apache-2.0,
trust score 8 pada saat audit) dan menampilkan contoh:

- `MacrobenchmarkRule` startup;
- `FrameTimingMetric` untuk scroll;
- cold/warm/hot compilation modes;
- Baseline Profile journeys;
- benchmark workflow/Firebase Test Lab.

Keputusan tidak hanya bersandar pada Context7. Semua poin disilang-verifikasi ke
Android Developers dan source GitHub resmi.

## 2.2 Android official comparison

### Android Performance Samples

- Macrobenchmark adalah alat resmi untuk startup/scroll UI.
- Target harus non-debuggable/profileable/release-like.
- Benchmark variant boleh debug-signed.

Sumber:

- https://github.com/android/performance-samples
- https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview

### Now in Android

Now in Android memiliki debug/release + benchmark variant, Macrobenchmark, dan
Baseline Profile untuk critical startup path. Ini menguatkan pemisahan variant
performance dari production app.

Sumber: https://github.com/android/nowinandroid

### Compose Samples / Jetsnack

Penambahan Baseline Profile pada Jetsnack dilaporkan meningkatkan average cold
startup sekitar 22%. Ini mendukung hipotesis warm-up/JIT, tetapi P0 ZCODE belum
menambahkan custom Baseline Profile karena tidak ada physical-device generation
pipeline yang representatif.

Sumber: https://github.com/android/compose-samples/pull/748

## 2.3 Acode comparison

Audit current Acode `main` menemukan:

- hybrid Cordova/WebView app;
- CodeMirror 6 current, termasuk `@codemirror/view 6.43.x` dan lint 6.9.7;
- `android:hardwareAccelerated=true`;
- minSdk 26;
- `DisallowOverscroll=true`;
- tidak ditemukan benchmark/Baseline Profile source pada tree yang diperiksa.

Relevansi:

- versi CM6 ZCODE 6.43.8 tidak tertinggal;
- hardware acceleration ZCODE secara platform default aktif (targetSdk tinggi),
  dan tidak ada `hardwareAccelerated=false`; jangan cargo-cult `largeHeap` atau
  overscroll config Acode;
- arsitektur Acode berbeda (Cordova/web shell), jadi ia bukan bukti bahwa satu
  setting tertentu menyembuhkan Compose ZCODE.

Sumber:

- https://github.com/Acode-Foundation/Acode
- https://raw.githubusercontent.com/Acode-Foundation/Acode/main/config.xml
- https://raw.githubusercontent.com/Acode-Foundation/Acode/main/package.json

## 2.4 Pydroid comparison

Pydroid proprietary. Public review menyebut occasional lag pada script besar,
tetapi mekanisme build/editor internal tidak dapat diverifikasi. Pydroid hanya
menjadi bukti bahwa mobile Python IDE juga menghadapi resource/performance
trade-off, bukan sumber keep rule atau arsitektur R8 ZCODE.

Jangan mengklaim Pydroid memakai R8/Baseline Profile tanpa source.

## 2.5 WebView/CodeMirror comparison

- Android mengakui WebView/Compose nested scroll sulit.
- CodeMirror dirancang untuk document besar dan versi ZCODE sudah mencakup
  banyak scrolling fixes.
- Laporan komunitas tentang hardware acceleration/largeHeap bertentangan; ZCODE
  tidak mengubah layer mode tanpa reproduksi A/B.

Sumber:

- https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/wrap-webview-in-compose
- https://codemirror.net/docs/changelog/
- https://codemirror.net/examples/million/

## 2.6 Baseline Profile dan recorder — sengaja ditunda

Baseline Profile berpotensi mengurangi first-use JIT, tetapi memerlukan journey
representatif dan generation/verification. JankStats/FrameMetrics/Performance
Recorder dapat tinggal di Diagnostics bila P0 tidak cukup, tetapi tidak masuk
satu APK pertama sesuai keputusan user.

Sumber:

- https://developer.android.com/develop/ui/compose/performance/baseline-profiles
- https://developer.android.com/topic/performance/jankstats
- https://developer.android.com/reference/android/view/FrameMetrics

---

# 3. FINAL — KEPATUHAN OPERATING AGREEMENT

Catatan nama: source of truth repo adalah **`AGENTS.md`** (plural) di root, bukan
`AGENT.md`; playbook proyek adalah **`docs/SKILLS.md`**.

## 3.1 Kepatuhan `AGENTS.md`

- **Honest:** memisahkan fakta source, bukti eksternal, hipotesis, dan batas.
- **Meticulous:** audit build, manifest, R8, indirect bridges, workflow,
  coexistence, data isolation, rollback, failure modes, dan UAT.
- **Discuss first:** build lifecycle, dependency/tooling, workflow, dan R8 tidak
  diubah sebelum approval.
- **Small coherent scope:** P0 hanya build configuration; source hotspot,
  recorder, Compose upgrade, signing, dan production release ditunda.
- **Verification levels:** DESIGNED/IMPLEMENTED/CI/DEVICE tidak dicampur.
- **Guard + mutation:** setiap invariant baru memiliki mutasi red→green.
- **Security:** tidak ada JKS/password/PAT; debug signing hanya test identity.
- **Supply chain:** tidak menambah dependency runtime pada P0; versions tetap
  pinned; workflow changes diaudit.
- **Resource constrained:** satu APK baru; no Gradle+emulator bersamaan; artifact
  teknis dipisah dari artifact user.
- **Rollback:** performance build type terisolasi dan removable.
- **No auto-release:** optimized APK bukan production release.

Kesimpulan `AGENTS.md`: **sesuai**.

## 3.2 Kepatuhan `docs/SKILLS.md`

- Python tetap 3.11; Chaquopy tetap 17.0.0; ABI ARMv7 dipertahankan.
- Tidak mengusulkan Pyodide, mengganti Chaquopy, atau `sys.settrace`.
- CI tetap hakim compiler; sandbox tidak mengklaim Kotlin compile.
- Satu build UAT memaksimalkan informasi.
- R8 boundary Kotlin↔Python/JS dijaga sebagai kelas masalah, bukan satu method.
- Diagnostics tetap copyable; raw CI log diminta ke user bila agent terhalang.
- Focus/IME matrix v1.0.19 wajib non-regresi.
- ZMUX/Acode hanya referensi, tidak dimasukkan ke project.
- Performance dipisahkan dari correctness/native lifecycle batch.
- Device verification menyebut model/API/ABI/artifact.

Kesimpulan `SKILLS.md`: **sesuai**.

## 3.3 Final recommendation

Implementasi menghasilkan **satu APK `ZCODE Performance 1.0.19-perf1`** dengan:

```text
non-debuggable
profileable
R8 compatibility-mode + targeted conservative keep
no obfuscation
no resource shrink
separate package/task/data
same UX/runtime/Compose as v1.0.19
no recorder/toggle/signing production
```

Confidence rencana setelah audit: **92%**.

Sisa 8% ketidakpastian berada pada perilaku R8 actual build dan rasa device;
keduanya hanya dapat dijawab oleh CI + UAT. Karena itu implementation belum
boleh disebut aman sebelum gate P8/P9.

## 3.4 Log implementasi lokal

Perubahan yang sudah dibuat pada branch `arena/v1020-performance`:

```text
app/build.gradle.kts                         performance build type
app/proguard-performance.pro                targeted conservative R8 rules
app/src/performance/AndroidManifest.xml      label/task isolation
app/src/performance/res/values/strings.xml   ZCODE Performance label
.gitignore                                   reject signing material
.github/workflows/performance.yml            dedicated CI build
ci/workflows/performance.yml                 exact mirror
test_zcode_performance_variant.py            17 permanent guards
tools/check.sh                               guard masuk full gate
```

Kontrak aktual:

```text
applicationId    : com.zaba.zcode.performance
versionName      : 1.0.19-perf1
debuggable       : false
profileable      : true
R8               : ON, compatibility mode
obfuscation      : OFF
resource shrink  : OFF
signing           : debug CI only
runtime/Compose  : sama dengan v1.0.19
```

Mutation proof 11 arah, semuanya RED lalu restore GREEN:

```text
debuggable=true
R8 dimatikan
keep-all ZCODE dikembalikan
JS bridge keep dihapus
TerminalBridge keep dihapus
ResolveOperationBridge keep dihapus
Chaquopy keep dihapus
taskAffinity disamakan
workflow membangun Debug
recorder dependency dibocorkan ke P0
signing material palsu dimasukkan
```

Validasi lokal:

```text
tools/check.sh                 : 611 passed
performance focused guards     : 17 passed
Kotlin lexical sanity          : 61 files
npm/editor supply-chain guard  : passed
workflow mirror/XML/diff check : passed
```

Status pada titik implementasi awal adalah **LOCALLY VERIFIED**, bukan CI
VERIFIED: sandbox tidak mengompilasi Kotlin/R8/manifest merge.

## 3.5 CI dan device evidence pertama

Push SHA `fe51b5652e88fcdd5fb584e70044e7cd6619f9d8` membuktikan token user memang
memiliki izin workflow. Dua pipeline pada source yang sama selesai hijau:

```text
Canonical Debug regression : run 32422826514 · SUCCESS
Performance/R8              : run 32422826694 · SUCCESS
```

Artifact:

```text
Debug artifact ID       : 9426420318
Debug archive bytes     : 44,735,378
Performance artifact ID : 9426425213
Performance archive     : 33,178,943 bytes
Performance archive SHA : 883a4403078c5713dbe75468f4983b2a2a5541b036de309ab2eb97228cec8d46
Technical report ID     : 9426426175
```

Workflow Performance berhasil melewati build R8 serta verifikasi package ID,
label, non-debuggable, profileable, task affinity, `:rebirth`, CM6 assets,
Chaquopy assets, dan APK signature. Ukuran archive turun sekitar 25,8% dibanding
Debug walau archive Performance juga membawa checksum/certificate report. Ini
membuktikan stripping/variant difference nyata, tetapi size bukan target utama.

UAT awal pada INFINIX X6532C/API34/ARMv7 memberi verdict user:

> **"Sangat sangat lancar jaya pool."**

Tap, scroll, swipe, dan pindah layar yang sebelumnya lag/patah/tertahan menjadi
lancar. Keputusan produk: build optimized adalah akar dominan dan layak menjadi
fondasi v1.0.20. Performance Recorder/JankStats tidak perlu dipasang untuk
menjawab masalah utama.

### Regresi tersisa: keyboard terminal tidak dapat dibuka ulang

Skenario device:

1. Run chatbot; terminal otomatis membuka keyboard.
2. User menutup IME.
3. Tap area output terminal; keyboard tidak muncul lagi.

Akar source: `TerminalScreen` memakai TextField transparan 1dp. Menutup IME tidak
selalu melepas focus dari field tersebut. Handler tap lama hanya menjalankan
`focusRequester.requestFocus()`; karena field sudah focused, operasi menjadi
no-op dan tidak meminta IME tampil lagi.

Fix lokal:

```text
request focus
→ tunggu satu frame
→ LocalSoftwareKeyboardController.show()
```

Satu helper `requestTerminalKeyboard()` dipakai oleh:

- tap area output;
- insert dari terminal handle;
- IME Done setelah input dikirim.

Tidak ada pointer interceptor baru, sehingga scroll, fling, long-press selection,
Salin/Bagikan, dan `^C` tetap memakai topology lama. Hanya kegagalan focus yang
dicatat; tap sukses tidak membanjiri breadcrumb.

Tiga mutasi keyboard terbukti merah:

```text
keyboard show dihapus
output tap kembali focus-only
jalur IME Done melewati helper
```

Restore hijau. Full local gate sesudah fix:

```text
tools/check.sh                : 613 passed
Kotlin lexical sanity         : 61 files
npm/editor supply-chain guard : passed
git diff --check              : passed
```

Status:

```text
Optimized responsiveness      : DEVICE VERIFIED
Performance build/R8          : CI VERIFIED
Terminal keyboard reopen fix  : IMPLEMENTED + LOCALLY VERIFIED
Final perf1 functional gate   : menunggu CI + focused device retest
```

---

# 5. CATATAN RISET LANJUTAN — EDITOR DAN PROJECT WORKBENCH

**Tanggal:** 2026-08-21
**Pemicu:** screenshot referensi Acode/VS Code dari user dan diskusi setelah UAT
optimized build.
**Status:** **RESEARCH RECORDED / DESIGN DIRECTION ONLY** — bukan scope
implementasi v1.0.20.

## 5.1 Cara membaca bukti screenshot

Screenshot dipakai sebagai referensi struktur dan discoverability produk:
Explorer tree, project search, activity rail, richer tabs, Git/GitHub, dan
plugin ecosystem. Screenshot membuktikan arah UX yang diinginkan, tetapi tidak
membuktikan engine rendering, performa, permission model, atau arsitektur proses
aplikasi referensi. Karena itu ZCODE tidak menyalin implementasi atau menganggap
Canvas sebagai penyebab kelancaran hanya dari tampilan visual.

## 5.2 Arsitektur editor ZCODE yang sebenarnya

```text
Jetpack Compose
└── AndroidView
    └── WebView/Chromium
        └── CodeMirror 6
            ├── editable DOM viewport
            ├── EditorState per file
            ├── Lezer incremental parser/tree fragments
            ├── history + selection + IME
            ├── lint + autocomplete
            └── hanya merender viewport terlihat + margin
```

Terminal berbeda dan tidak boleh dijadikan analogi langsung:

```text
Compose LazyColumn
├── Text rows + TerminalBuffer
├── visual cursor
└── hidden 1dp TextField untuk IME
```

Terminal adalah grid/stream monospace yang jauh lebih sempit kontraknya.
Code editor harus menangani grapheme/Unicode, wrapping, selection, cursor,
clipboard, undo/history, syntax tree, lint, autocomplete, accessibility,
touch/IME, viewport, dan sinkronisasi document state.

Versi editor saat riset:

```text
@codemirror/view : 6.43.8
@codemirror/lint : 6.9.7
```

CodeMirror 6 memakai viewport rendering dan incremental parsing. Acode current
source juga memakai CodeMirror 6 sekitar versi yang sama. Maka kelancaran Acode
bukan bukti bahwa ZCODE harus mengganti editor ke Canvas.

## 5.3 Alternatif yang diperiksa

### Canvas custom editor

Canvas hanya menyelesaikan pixel drawing. ZCODE masih harus membangun sendiri
IME, grapheme, Unicode, cursor/selection, clipboard, undo, syntax, lint,
folding, wrapping, accessibility, magnifier/touch, viewport, dan history.
Biaya correctness serta maintenance jauh melampaui bukti masalah yang ada.

### Sora Editor

Sora adalah alternatif native Android paling realistis: custom View/render,
layout/input, incremental highlighting, autocomplete, word wrap, undo/redo,
search, diagnostics, TextMate, dan Tree-sitter. Namun migrasinya membawa biaya
lifecycle, IME/accessibility, integrasi bridge/state, kemungkinan native ABI,
dan review lisensi LGPL-2.1. **Tidak dipilih sekarang.**

### Monaco

Monaco tetap editor web/DOM yang tervirtualisasi dan memakai worker. Fitur serta
bebannya lebih besar; bukan solusi otomatis untuk device ARMv7 low-end.

### Ace

Ace memakai virtual DOM renderer, bukan Canvas. ZCODE sudah bermigrasi dari Ace
ke CM6; kembali ke Ace tidak didukung bukti device.

### EditText / BasicTextField

Cocok untuk teks sederhana/kecil, tetapi syntax spans dan dokumen besar mudah
menjadi bottleneck serta akan memindahkan banyak tanggung jawab editor ke kode
ZCODE sendiri.

Sumber langsung:

- https://codemirror.net/docs/guide/
- https://codemirror.net/docs/ref/
- https://codemirror.net/examples/million/
- https://github.com/Acode-Foundation/Acode
- https://github.com/Rosemoe/sora-editor
- https://project-sora.github.io/sora-editor-docs/guide/getting-started
- https://github.com/microsoft/monaco-editor
- https://github.com/ajaxorg/ace
- https://github.com/xtermjs/xterm.js/

## 5.4 Bukti device mengalahkan hipotesis migrasi engine

APK Performance/R8 membuat tap, scroll, swipe, navigasi, Compose screens, dan
editor menjadi sangat lancar tanpa mengganti CodeMirror 6. Ini menunjukkan
Debug/JIT/build configuration adalah akar dominan, bukan engine editor.

Keputusan:

```text
Keep CodeMirror 6                  : ADOPTED FOR v1.0.20
Canvas/Sora/Monaco migration       : REJECTED FOR CURRENT SCOPE
Optimized build as v1.0.20 base    : DEVICE-EVIDENCE SUPPORTED
Confidence keep CodeMirror 6       : 97%
```

Migrasi engine hanya boleh dibuka lagi bila optimized build menunjukkan
bottleneck yang khusus editor, reproducible, terukur, dan tidak dapat
diselesaikan dengan perbaikan lebih kecil.

## 5.5 Project Workbench diparkir untuk v1.0.25

Arah UX dari screenshot diterima untuk riset v1.0.25:

- Explorer tree;
- search in project;
- activity rail/navigation yang mudah ditemukan;
- richer file tabs;
- Git lokal lalu GitHub;
- plugin platform paling akhir.

Urutan aman yang direncanakan:

```text
Project model + backup/recovery
→ Explorer overlay
→ Search in project
→ Command registry
→ Git local
→ GitHub
→ plugin platform last
```

Pada portrait, Explorer tidak dijadikan panel permanen yang memakan ruang
editor; gunakan overlay/drawer yang menutup setelah file dipilih. Marketplace
atau plugin tidak boleh disalin bebas sebelum ada permission model, lifecycle,
process isolation, recovery, dan threat model.

Keputusan scope:

```text
v1.0.20 : optimized foundation + terminal regression gate
v1.0.25 : Project Workbench research target
Current : PARKED — DON'T RUSH
```

Prioritas sebelum Workbench tetap optimization gate, release/signing strategy,
backup/recovery, dan runtime safety. Tidak ada perubahan project explorer, Git,
plugin, atau workbench dalam push terminal keyboard ini.

---

# 6. FINAL FOCUSED DEVICE UAT — PERFORMANCE PERF1

**Tanggal:** 2026-08-21
**Artifact:** `ZCODE-v1.0.19-perf1`, artifact ID `9429914889`
**CI run:** `32433313815`
**Commit:** `d62af5a3365385b84278044bbf1af2bd3083f3d4`
**Perangkat:** INFINIX X6532C, Android 14/API 34, `armeabi-v7a`

User menjalankan focused regression pada APK Performance/R8 baru setelah fix
keyboard terminal. Seluruh checklist berikut dilaporkan lulus:

```text
Run chatbot                         : PASS
Keyboard awal muncul                : PASS
Tutup IME → tap output → IME buka   : PASS
Ulang close/reopen 3–5 kali         : PASS
Ketik/kirim melalui IME Done        : PASS
Scroll dan fling terminal           : PASS
Long-press selection                : PASS
Salin                               : PASS
Bagikan                             : PASS
^C                                  : PASS
Diagnostics/Crash check             : PASS — tidak ada regresi dilaporkan
```

UAT navigasi umum juga dilaporkan melampaui ekspektasi user:

- tap, swipe, dan perpindahan tab terasa sangat lancar;
- gesture membuka sidebar tetap responsif;
- transisi dari sidebar menuju Settings berjalan halus;
- menu dan layar Compose tidak lagi menunjukkan lag dominan Debug build;
- editor tetap CodeMirror 6 dan ikut terasa lancar.

Komentar user tentang kesan "tidak sampai 3 ms" dicatat hanya sebagai ekspresi
kepuasan, **bukan angka pengukuran teknis**, karena sesi ini tidak memakai
FrameMetrics/Macrobenchmark. Klaim yang sah adalah smoothness tersebut
**DEVICE VERIFIED secara observasional** pada perangkat target.

Status setelah focused UAT:

```text
Optimized responsiveness      : DEVICE VERIFIED
Terminal keyboard reopen      : DEVICE VERIFIED
Repeated IME close/open       : DEVICE VERIFIED
IME Done/input                : DEVICE VERIFIED
Scroll/fling                  : DEVICE VERIFIED
Selection/Salin/Bagikan       : DEVICE VERIFIED
^C                            : DEVICE VERIFIED
Canonical Debug regression    : CI VERIFIED — run 32433313805
Performance/R8 build          : CI VERIFIED — run 32433313815
Focused perf1 functional gate : PASSED
Optimized v1.0.20 foundation  : GO FOR PR REVIEW
Merged                        : NO
Released                      : NO
```

Keputusan co-lead: optimized build layak diproses sebagai fondasi v1.0.20.
Tahap berikutnya adalah audit diff, PR menuju `main`, review, dan merge hanya
setelah checkpoint bersama. Identitas `1.0.19-perf1`, workflow branch khusus,
dan ephemeral debug signing belum boleh disalahartikan sebagai konfigurasi
rilis publik v1.0.20.

---

# 7. PRE-RC ACCESSIBILITY GATE — EDITOR WEBVIEW

**Tanggal:** 2026-08-21
**Branch:** `arena/v1020-pre-rc-a11y`
**Basis:** merge `main` `46ed60e`

Chrome DevTools MCP terhadap shipped CodeMirror bundle menemukan tiga gap yang
relevan bagi produk, terpisah dari noise SEO halaman asset:

```text
CM6 textbox accessible name : missing
Gutter contrast              : 3,88:1 (#4D7A5A / #0A100D)
Viewport zoom                : blocked by user-scalable=no
```

Implementasi pre-RC:

1. `EditorView.contentAttributes` memberi `aria-label="Editor kode Python"`;
2. gutter menjadi `#5A8F68` (sekitar 5,09:1 terhadap `#0A100D`);
3. meta viewport tidak lagi menolak scaling;
4. Android WebView mengaktifkan support/built-in pinch zoom dan menyembunyikan
   tombol zoom overlay legacy;
5. touch listener mencatat `ACTION_POINTER_DOWN`, sehingga final `ACTION_UP`
   setelah pinch tidak disalahartikan sebagai tap yang membuka IME;
6. generated `codemirror.bundle.js` direbuild dari exact lockfile.

Bukti shipped bundle baru:

```text
SHA-256:
9c5118c863896ad5a7317ae96b3d7867189fb1c346ddb3d3cf3922b43de77b4e

Accessible tree textbox : "Editor kode Python"
Computed gutter color   : rgb(90, 143, 104)
Computed gutter bg      : rgb(10, 16, 13)
Lighthouse a11y         : 1,00
aria-input-field-name   : PASS
color-contrast          : PASS
meta-viewport           : PASS
5.000 logical lines     : 54 rendered DOM lines
alpha_5000 visible      : PASS
Console after clean load: no warning/error
External network        : none
```

Seven implementation mutations turned the intended tests red:

```text
accessible-name source removed
accessible-name bundle made stale
gutter source reverted
gutter bundle made stale
user-scalable=no restored
built-in pinch disabled
multi-touch IME guard bypassed
```

Semua dipulihkan dan focused gate hijau. Insiden agent saat implementasi juga
menghasilkan rule permanen: dua writer tidak boleh dijalankan paralel terhadap
file yang sama; write set harus disjoint.

Status setelah CI dan focused device UAT:

```text
Agent/Context7/MCP playbook : CI VERIFIED
Editor accessibility source : CI VERIFIED
Generated CM6 bundle         : BROWSER-HARNESS + CI VERIFIED
Canonical Debug              : CI VERIFIED — run 32446512404
Performance/R8               : CI VERIFIED — run 32446511762
Pinch/selection/IME gesture  : DEVICE VERIFIED
Per-tab zoom isolation       : DEVICE OBSERVED — satu sesi
TalkBack spoken label        : NOT DEVICE VERIFIED
Merged                       : NO
RC configured                : NO
Released                     : NO
```

Focused UAT pada INFINIX X6532C, Android 14/API34, ARMv7:

```text
pinch zoom in/out editor                 : PASS
single tap still opens IME               : PASS
pinch completion does not open IME       : PASS
IME typing/Backspace/Enter/Done          : PASS
one-finger vertical/horizontal scroll    : PASS
selection handles + copy/paste           : PASS
switch tabs while zoomed                 : PASS
edge swipe sidebar                       : PASS
rotate portrait/landscape with keyboard  : PASS
reset/readability after process reopen   : PASS
Python run + Diagnostics sanity          : PASS
visual gutter readability                : PASS
```

Device observation yang paling menonjol: tab pertama dapat tetap zoomed ketika
user pindah ke tab kedua yang tetap pada skala normal. Bukti ini berlaku untuk
isolasi selama perpindahan tab dalam sesi yang diuji. Jangan mengklaim zoom
persist setelah process restart tanpa test khusus. TalkBack bersifat opsional
dan belum dilaporkan diuji, sehingga accessible label tetap hanya
BROWSER-HARNESS + CI VERIFIED.

Sumber:

- https://codemirror.net/docs/ref/#view.EditorView%5EcontentAttributes
- https://developer.android.com/reference/android/webkit/WebSettings#setSupportZoom(boolean)
- https://developer.android.com/reference/android/webkit/WebSettings#setBuiltInZoomControls(boolean)
- https://developer.android.com/develop/ui/views/layout/webapps/targeting
- https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/meta/name/viewport
- https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html
- https://www.w3.org/WAI/WCAG22/Understanding/resize-text.html
- https://github.com/ChromeDevTools/chrome-devtools-mcp
- https://context7.com/docs/overview
- https://github.com/upstash/context7
