# ZCODE — Plan Matang v0.2
### Zabacode Kotlin Edition: Kesederhanaan Pydroid × Detail VS Code × Kelincahan Acode

> Dokumen ini hasil baca `README.md` + bedah total repo acuan **muzape28-blip/ZABACODE** (v1.2.1, 181 commits) + **muzape28-blip/ZMUX** (117 commits) + audit akar 33 bug. Tujuan: ubah ide 1 baris `README.md` jadi blueprint Kotlin yang bisa langsung `gradle` tanpa ngulang bug yang pernah ada.

**Tanggal:** 2026-08-08  
**Cabang:** `arena/019fddf3-zcode` → `main` (24a9d3b)  
**Status:** Pre-implementation v0.2 — sudah include 33 bug audit + 3 jebakan ZMUX (wrap/pembatas/resize), siap dikunci Q1-Q5.

---

## 0. Ringkasan Eksekutif — 1 Menit Baca

**Apa itu ZCODE?** Zabacode yang lahir ulang dengan **Kotlin + Android Native**, bukan `python main.py` + `buildozer webview`. Python tetap sebagai *runtime* untuk kode user, tapi **shell, editor, file manager, terminal, dan keamanan** jadi native Kotlin.

**Rumusnya:**
```
ZCODE = (Jiwa anti-ads & offline-first Zabacode)
      + (1-tap RUN & pip-simple ala Pydroid)
      + (Workbench + Command Palette + Extension Host ala VS Code)
      + (QuickTools + touch-optimized Ace + proot-terminal ala Acode)
```

**Kenapa Kotlin, bukan tetap Python?**
Zabacode Python sudah mentok di 3 plafon: `WebView` di-`buildozer` susah di-`taskAffinity`, `Waitress` loopback port 5000 ketuker Zmux, dan `EncryptedSharedPreferences` vs file fallback masih di-Python. Kotlin memberi: `Jetpack Compose` (UI 60fps native), `EncryptedSharedPreferences` + `Keystore` beneran native, `Coroutines + WorkManager` untuk run isolated, `WebView` bridge yang terenkripsi, dan APK Fat tanpa `p4a` bootstrap.

**Janji yang kita jaga dari Zabacode:** GPLv3, Zero Telemetry, Offline-first, 10 tema, Oracle tetap deterministic (bukan LLM ngarang), Verified TLS + SHA-256.

---

## 1. Hasil Bedah Repo Acuan — ZABACODE v1.2.1

### 1.1 Apa yang sudah bagus (jangan dirusak di ZCODE)

Di-verify dari `README.md`, `SECURITY.md`, `AUDIT_REPORT.md` (F-01 s/d F-09), `CHANGELOG.md` Unreleased 6 batch, `ZABACODEE.md`, `web_app.py` (1196 baris), `executor.py` (541), `oracle.py` (2281), `ai_provider.py` + `BUGS_AUDIT_ZABACODE_FOR_ZCODE.md` (33 bug):

| Modul | Bukti & Kenapa Bagus |
|---|---|
| **Isolasi run** | `subprocess.Popen(start_new_session) + os.killpg + timeout 30s + PGID cleanup`, `RLock` untuk Waitress 4-thread. Aman dari hang. |
| **Verified TLS** | `core/net.py` + `certifi` + `openssl` di `buildozer.spec`. Tidak ada `ssl._create_unverified_context`. Grep CI fails kalau muncul. |
| **Wheel verify** | SHA-256 cek digest PyPI + `path traversal` check sebelum `extractall`. Dua lapis. |
| **Keystore** | `EncryptedSharedPreferences AES256_GCM` (Android) + fallback file HMAC-derived stream cipher encrypt-then-MAC, `chmod 600`, `hmac.compare_digest`. |
| **Headers** | CSP `default-src 'self'`, `nosniff`, `Referrer-Policy no-referrer`, `frame-ancestors 'none'`. Loopback-only `127.0.0.1` + `X-Zabacode-Token` constant-time. |
| **Offline-first** | Ace 1.32.4 bundled `assets/vendor/ace/ace.js` (45-60 FPS), 7 provider + Oracle fallback, no CDN (`cdnjs/unpkg/jsdelivr` di-block CI). |
| **Arsitektur baru v1.2.1** | `events.py` (VS Code `Emitter<T>`), `commands.py` (Command Registry), `services.py` (Service Container) — pola VS Code yang sudah di-port. |
| **Oracle** | 20+ rule `humanize_traceback`, `analyze_buffer` AST, `auto_fix_code` yang hanya offer patch kalau `ast.parse(after)` sukses. |
| **Image capture** | `collect_new_images()` shared antara batch & interactive, dedup via baseline, skip >8 MB, retry half-written. |

### 1.2 Hutang & Bug yang masih nongol — sekarang 33 bug (lihat `docs/BUGS_AUDIT_ZABACODE_FOR_ZCODE.md`)

Dari `AUDIT_REPORT.md` (F-01 s/d F-09) + `CHANGELOG.md` Unreleased 6 batch + `SECURITY.md` #18-#27 + Issue #50 + grep manual — **total 33**:

**Critical untuk di-fix di hari 0 Kotlin (5):**
- **F-01 fetch tanpa Content-Type** → Flask buang body jadi `{}`. 3 endpoint mati (`/api/oracle/explain`, `/api/oracle/fix`). Test pakai `json=` auto-header, jadi lolos CI tapi mati di browser. **ZCODE:** hapus kelas bug — pakai **JS Bridge `file://` + `addJavascriptInterface`** (tanpa HTTP loopback). Kalau butuh HTTP (AI), OkHttp interceptor wajib `application/json` + backend strict 400 `invalid_json`.
- **F-02 Auto-Fix merusak runtime** → `print(prices[9])` → `print("prices[9]")` (5 dari 8 kode valid dirusak). Karena fixer jalan bahkan saat kode sudah valid (`ast.parse` sukses). **ZCODE:** gate `if parses(code): return runtime_error=True` → serahkan ke `humanize_traceback`, patch hanya kalau `parses(before)==False && parses(after)==True`.
- **F-03 `is_success` dibuang** → `ok = len(fixes)>0` bukan `is_success` (ast.parse sukses). **ZCODE:** `ok = is_success && fixes.isNotEmpty()` + `sealed class FixResult`.
- **F-04 `=` → `==` rusak kwarg** → `d.get(default=1)` → `default==1`. **ZCODE:** `_replace_bare_equals` depth-aware (hanya depth 0), jangan regex buta.
- **S-22 `--trusted-host` matikan TLS** → `pip --trusted-host pypi.org` disable verify. **ZCODE:** OkHttp + `certifi` + `conscrypt`, tanpa `trustAllCerts`, tampilkan `TLS_HELP_MESSAGE` kalau cert gagal.
- **S-18 execution tanpa bound** → dulu tanpa 512KB/queue/timeout → OOM. **ZCODE:** `MAX_CODE 512KB, MAX_OUTPUT 256KB, queue 10k, 120s/60s, 8KB/input` (copy `executor.py`).

**HIGH (12) — hasil salah tapi keliatan jalan:**
- **B-10 Beautifier** `def f() -> int:` → `def f() - > int:` + `//=` → `// =` → fix longest-first `//=` sebelum `//` + test AST identical 125 kombinasi. **ZCODE:** copy tuple `_OPERATORS`.
- **B-11 Syntax Guard** `print(' :)')` diblock (hitung `(` tanpa strip string) → fix pakai `strip_comments_and_strings` + `/api/check`.
- **B-13 matplotlib** tidak muncul di RUN (interactive tidak `collect_new_images`) → fix `collect_new_images(baseline)` shared, `snapshot before run`, `dedup`, `skip >8MB`.
- **B-14 `fix my code` pakai regex `hello world`** bukan `auto_fix_code()` → fix panggil engine.
- **B-18 error card salah baris** (off-by-9 `PRELUDE`, chained traceback salah, substring match, `async def` invisible) → fix `line_offset`, `cause chain`, `full-word` match.
- **S-27 + C-50 coexistence** `p4a.port=5000` + `singleTask` → ketuker Zmux → fix `taskAffinity=com.zaba.zcode` + `singleTop` + `documentLaunchMode=intoExisting` (PR #50). **ZCODE:** Manifest native, `ServerSocket(0)` kalau butuh port.

**MEDIUM/LOW (16):** S-19 `custom` hilang keystore, S-21 draft localStorage plain tanpa consent, S-23 JSON 500, S-20 `files/files`, F-07 off-by-9, F-05/F-06 CI gap, E-01 `MAX_FILENAME_LEN` 500, E-02 `User-Agent` versi ngaco, dll — semua ada penanganan di `BUGS_AUDIT...` §3-4.

**3 Jebakan ZMUX yang mengintai ZCODE (baru, dari bedah 117 commits ZMUX):**
- **Wrap kepagian** — PS1 warna `31 byte CSI` dihitung shell → `Z$` polos `\[ \]` wrapper. **ZCODE editor aman** (bukan PTY), tapi **terminal nanti rawan** → pakai `ZCODE$ ` polos.
- **Pembatas 80x24** — `TerminalSession 80x24` lupa `post { updateSize() }` → ketabrak. **ZCODE:** `terminalView.post { updateSize() }` + `ace.resize()`.
- **Resize loncat 4-5 baris** — `adjustResize` tiap frame → reflow berulang. **ZCODE:** debounce `100ms` untuk `Ace.resize()` & `TerminalView.updateSize()`.

> Detail per bug ada di `docs/BUGS_AUDIT_ZABACODE_FOR_ZCODE.md` — 201 baris, 33 baris tabel + akar + bukti grep + penanganan Kotlin line-by-line.

### 1.3 Peta modul Zabacode yang akan kita port

```
zabacode/
  web_app.py (Flask+Waitress)        → Ktor/Compose Navigation + ViewModel
  core/executor.py (isolated+interactive) → Kotlin ExecutorService (Coroutine + ProcessBuilder)
  core/oracle.py (humanize/analyze/fix)   → kotlin:oracle (port 1:1, tambah test)
  core/ai_provider.py (7 providers)       → kotlin:ai (OkHttp + certifi via conscrypt)
  core/keystore.py                        → AndroidX Security Crypto
  core/net.py (get_ssl_context)           → OkHttp + CertificatePinner + certifi bundle
  core/file_manager.py                    → kotlin:files (SAF + scoped storage)
  core/paths.py (APP_DIR)                 → Context.filesDir + dataStore
  core/checker.py / diagnostics.py        → kotlin:checker (tree-sitter atau top-level parse)
  lib_manager.py (zabapip)                → kotlin:packagemanager (pip via Chaquopy/uv)
  plugins/* (5 transform)                 → kotlin:plugins (Command Registry)
  themes/definitions.py (10 tema)         → kotlin:themes (Compose Theme + Ace theme sync)
  templates/index.html (4846 baris)       → Compose + WebView(Ace/Monaco) + bridge
```

---

## 2. Pelajaran dari 3 Acuan — Pydroid, VS Code, Acode

### 2.1 Pydroid 3 — Kesederhanaan yang bikin pemula betah

*Sumber: Play Store + blindhelp.net + applist360*

**Yang kita curi (jangan ditiru mentah, tapi esensinya):**

- **Offline interpreter 1-tap:** Tidak perlu `adb`, tidak perlu `apk add python3`. User buka app → `Run` langsung jalan. Zabacode sudah punya isolated subprocess, tapi UX-nya masih WebView. ZCODE: tombol `▶ Run` di QuickBar, `stdin` box auto-muncul kalau `input()` terdeteksi (bukan mode terpisah).
- **Pip + Quick Install:** `pip` + repo prebuilt wheel (`numpy, scipy, matplotlib, sklearn, jupyter, opencv, torch`). User tidak compile. **Pelajaran:** Zabacode `lib_manager.py` sudah catalog 50+ lib dengan `tier`/`mode`, tapi UX masih manual. ZCODE: `+ Add Library` chip di editor (kayak Pydroid Quick Install) + `offline` badge hijau.
- **Tkinter + GUI support:** Pydroid support `Tkinter` via SDL2. Zabacode tidak. ZCODE v1 tidak perlu Tkinter, tapi siapkan `Canvas` preview untuk `matplotlib` (sudah ada `collect_new_images`).
- **Contoh out-of-the-box:** Pydroid kasih contoh runnable. Zabacode punya `Starter Kits` (To-do, Safe Calculator). ZCODE: pertahankan + tambah `templates/` native.
- **Terminal emulator sederhana, bukan proot penuh:** Pydroid terminal itu *readline* + `pip` + `C/C++/Fortran compiler` via `Cython` — ringan. **Pelajaran:** Jangan langsung Alpine penuh seperti Acode kalau user cuma mau `pip install requests`. ZCODE Phase 1: simple `Run Terminal` (stdin/stdout), Phase 2 baru proot opsional.

**Yang kita hindari dari Pydroid:**
- Freemium/paywall libs. ZCODE tetap GPLv3 100% free.
- Iklan. Tetap zero.

### 2.2 VS Code — Detail yang bikin pro betah

*Sumber: microsoft/vscode architecture + thedeveloperspace*

**Pola yang Zabacode v1.2.1 sudah mulai port (kita lanjutkan di Kotlin):**

- **Multi-process:** Main ↔ Renderer ↔ Extension Host ↔ Language Server (isolasi, tidak nge-freeze UI). Zabacode sudah `Executor isolated` + `RLock`. ZCODE Kotlin: `UI (Compose) ↔ Editor (WebView) ↔ Executor (Process) ↔ Oracle (Coroutine)` — semua `Dispatchers.IO` terpisah.
- **EventEmitter + Disposable + Service Container:** Zabacode `core/events.py` sudah mirip `src/vs/base/common/event.ts`. ZCODE: pakai `kotlinx.coroutines.flow.MutableSharedFlow` + `Lifecycle-aware Disposable`.
- **Command Registry:** Zabacode `commands.py` sudah `get_command_registry()`. ZCODE: `CommandManager` + `Command Palette (Ctrl+Shift+P)` native Compose dialog.
- **Workbench Layout:** `Activity Bar | Side Bar | Editor Groups | Panel (Terminal/Output) | Status Bar`. Zabacode sekarang sidebar + modal settings. ZCODE: beneran Workbench — Activity Bar vertikal (Explorer, Search, Run, Plugins, Settings), Editor tabs dengan `preview` mode, Panel bawah untuk Terminal/Oracle.
- **Extension Host:** Zabacode plugins masih `if/elif` di `PluginExecutor`. ZCODE: host terisolasi, plugin komunikasi via `MessageChannel` (kaya VS Code RPC), jadi plugin crash tidak matikan editor.
- **Settings & Keybindings:** VS Code punya `settings.json` + UI. Zabacode punya `Settings Dashboard`. ZCODE: `DataStore Preferences` + `settings.json` yang bisa di-sync.

### 2.3 Acode — Detail mobile yang Zabacode belum punya

*Sumber: acode.app + Play Store foxdebug*

**Yang kita curi:**

- **Ace v1.32 via WebView tapi touch-optimized:** Zabacode sudah Ace bundled 45-60 FPS, Acode juga Ace v1.22. ZCODE: pertahankan Ace bundled (atau naik ke Monaco via `monaco-editor` CDN offline), tapi tambah `QuickTools` bar: `TAB : ( ) [ ] { } " ' = _ def return import` persis Zabacode + Acode, plus `Undo/Redo/Find/Palette` context menu.
- **File Browser + Remote:** Acode punya FTP/SFTP, `acode .` CLI. Zabacode hanya `files/` lokal. ZCODE: Phase 1 lokal + SAF, Phase 2 SFTP (optional).
- **Terminal Alpine proot + apk:** Acode `apk add nodejs python3 git`. Zabacode executor hanya `python`. ZCODE: tawarkan dua mode — `Simple Python Runner` (default, ringan) dan `Full Linux (proot)` toggle di Settings untuk yang butuh `git, node, pip compile`.
- **250+ plugins + LSP:** Acode plugin store. Zabacode 12+ plugins + 5 transform offline. ZCODE: marketplace Compose + `LSP client` untuk Python (`pyright`/ `jedi`) — IntelliSense beneran.
- **Command Palette & Quick Open:** `Ctrl+Shift+P`, `Ctrl+P`. Zabacode belum. ZCODE: wajib.
- **Performa:** Acode `offload heavy checks on background thread`. Zabacode masih WebView single thread. ZCODE: semua `analyze_buffer`, `auto_fix`, `collect_images` di `Dispatchers.Default`.

---

## 3. Visi ZCODE — Satu Kalimat, Satu Janji

> **ZCODE adalah Zabacode yang kalau dibuka pemula Pydroid langsung paham, kalau dibuka user VS Code langsung nyaman, dan kalau dibuka user Acode langsung merasa di rumah — tapi semuanya Kotlin native, offline-first, tanpa iklan.**

**Prinsip keras (dari Zabacode PHILOSOPHY + ZABACODEE.md):**
1. **GPLv3, Zero Ads/Telemetry** — tidak ada `arena` branding permanen di version/CI.
2. **Offline-first, bukan offline-maybe** — Ace/Monaco bundled, Oracle deterministic, `certifi` bundled, no CDN.
3. **Tools as Tools** — Custom Endpoint tetap ada (`custom` provider neutral), tapi Oracle tetap otak offline. Tidak ada AI yang ngaku bisa RAG kalau belum ada generator ter-evaluasi (lihat `CHANGELOG.md` Unreleased RAG note).
4. **Keamanan by default** — `taskAffinity`, `allowBackup=false`, `loopback+token` kalau masih pakai server, atau `WebView bridge` encrypted kalau native.

---

## 4. Tech Stack yang Diusulkan (Kotlin)

### 4.1 Stack Utama — Rekomendasi

| Lapisan | Pilihan Utama | Kenapa | Alternatif |
|---|---|---|---|
| **Bahasa & Build** | Kotlin 1.9 + Gradle Kotlin DSL + AGP 8 | Null-safety, coroutines, official Android | KTS saja |
| **UI** | **Jetpack Compose + Material 3** | Deklaratif, theming 10 tema gampang, 60fps | XML View (lebih berat) |
| **Editor** | **WebView + Ace 1.44.0 bundled** (upgrade dari 1.32.4 Zabacode, 300KB, longest-first, AST identical) + `IEditor` abstraction siap CodeMirror 6 | Ace proven 45-60 FPS, CodeMirror 6 juara mobile tapi effort 1 minggu — Ace dulu | Monaco 0.50 (1-5 MB, berat di ARMv7, tidak rekom) |
| **Bridge** | `WebView file:///android_asset/editor/index.html + addJavascriptInterface` (tanpa loopback, tanpa port, tanpa Content-Type bug) | Hapus F-01/S-27/C-50 selamanya, offline murni | `Ktor embedded server` loopback (balik ke port 5000 issue) |
| **Python runtime** | **Chaquopy 3.11** (bukan 3.12 — 3.12 drop armeabi-v7a) `abiFilters arm64-v8a + armeabi-v7a + x86_64` | Chaquopy mature untuk `pip` + prebuilt `numpy`, tapi +15 MB — kita diet via App Bundle | `python-for-android NDK` (ramping tapi compile wheel sendiri, 20 menit build) |
| **Execution** | `ProcessBuilder` + `CoroutineScope(IO)` + `Channel` untuk interactive stdin | Mirip `executor.py` tapi Kotlin: `MAX_CODE_BYTES 512KB`, `MAX_OUTPUT 256KB`, `timeout 30s`, `PGID kill` via `Process.destroyForcibly()` | WorkManager untuk background |
| **Terminal** | Phase 1: `Compose TerminalView` (stdin box + xterm style). Phase 2: `proot Alpine` via `jackpal/androidterm` atau `termux` lib | Pydroid simple dulu, Acode full later | `WebSocket` ke local shell |
| **AI** | `OkHttp 4 + conscrypt + certifi` (port `net.py` verified TLS) + `kotlinx.serialization` | 7 providers sama seperti `ai_provider.py` | Ktor client |
| **Oracle** | **Port Python `oracle.py` ke Kotlin** 1:1 (regex + AST via `org.python:python-ast` atau `jep` atau tree-sitter) | Deterministic, tanpa LLM | Tetap panggil Python `oracle.py` via Chaquopy (lebih cepat) |
| **Storage** | `DataStore Preferences` + `EncryptedSharedPreferences (AndroidX Security)` + `Room` untuk files metadata | Ganti `keystore.py` fallback | `SQLDelight` |
| **DI & Arch** | `Hilt` + `MVVM` + `StateFlow` + `Navigation Compose` | Mirip `services.py` container | Koin |
| **Tests** | `JUnit5 + Turbine + MockWebServer + Compose UI Test` | Port 132+ `test_main.py` | Kaspresso |

**Keputusan yang sudah kita diskusikan & rekomendasi v0.2 (lihat §9):**
- **Editor:** **Ace 1.44.0 dulu** (upgrade fix) + `IEditor` abstraction siap CodeMirror 6 — Monaco ditolak untuk ARMv7
- **Python runtime:** **Chaquopy 3.11** (bukan 3.12, biar ARMv7 HP kamu tetap support) — `buildozer.spec` tidak ada, ganti Gradle
- **Bridge:** **JS Bridge `file://`** — hapus server offline, nggak ada port 5000 yang ketuker
- **Terminal:** **Fokus ZCODE dulu** (Zmux pending), editor punya Panel Output simple; terminal full embed ZMUX nanti Phase 3 dengan 3 fix ZMUX (wrap/pembatas/resize)
- **Arch:** **arm64-v8a + armeabi-v7a + x86_64** via App Bundle (universal APK buat sideload) — HP ARMv7 kamu jadi device referensi

### 4.2 Kenapa tidak tetap Buildozer/WebView Flask?

Karena 3 masalah Zabacode tidak bisa sembuh tanpa native:
- `buildozer.spec` tidak punya `android.taskAffinity` → harus `p4a_hook.py` hack.
- `Waitress` loopback port collision → Zabacode vs Zmux ketuker (Issue #50).
- `EncryptedSharedPreferences` di Python harus via `pyjnius` (rapuh). Di Kotlin langsung API.

---

## 5. Arsitektur — Kotlin Native Workbench

```
┌──────────────────────────────────────────────────────────────────┐
│  ZCODE Workbench (Compose)                                       │
│  ┌──────────────┬──────────────────────────┬────────────────────┐ │
│  │ Activity Bar │  Side Bar                │  Editor Groups     │ │
│  │ ───────────  │  Explorer (files/)       │  ┌──────────────┐ │ │
│  │  Explorer    │  Search (Ctrl+P)         │  │ Ace/Monaco   │ │ │
│  │  Search      │  Source Control (Git)    │  │ WebView      │ │ │
│  │  Run & Debug │  Plugins (Marketplace)   │  │ QuickTools   │ │ │
│  │  Plugins     │  Settings Dashboard      │  │ Tabs + Minimap│ │ │
│  │  Settings    │                          │  └──────────────┘ │ │
│  └──────────────┴──────────────────────────┴────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ Panel: Terminal | Output | Oracle | Problems (Diagnostics)   │ │
│  └──────────────────────────────────────────────────────────────┘ │
│  └─ Status Bar: branch, Python 3.11, Ln 12, Col 4, Oracle ●   ─┘ │
└──────────────────────────────────────────────────────────────────┘
         ↕ StateFlow / Events              ↕
┌─────────────────┐   ┌──────────────────┐   ┌──────────────────┐
│ EditorBridge    │   │ ExecutionEngine  │   │ OracleEngine     │
│ (WebView JS)    │   │ (ProcessBuilder) │   │ (Kotlin port)    │
│ - getValue()    │   │ - runIsolated()  │   │ - humanize()     │
│ - setValue()    │   │ - startInteractive│   │ - analyze()      │
│ - onDidChange   │   │ - sendInput()    │   │ - autoFix()      │
└─────────────────┘   └──────────────────┘   └──────────────────┘
         ↕                         ↕                   ↕
┌──────────────────────────────────────────────────────────────────┐
│  ServiceContainer (Hilt)                                         │
│  FileManager | KeystoreService | AiProviderService | ThemeService│
│  PluginHost | PackageManager (zabapip) | NetService (TLS)       │
└──────────────────────────────────────────────────────────────────┘
         ↕
┌──────────────────────────────────────────────────────────────────┐
│  Data Layer: DataStore + EncryptedPrefs + Room + files/         │
└──────────────────────────────────────────────────────────────────┘
```

**Aliran `▶ Run` (Pydroid-simple tapi VS Code-detail):**

1. User tap `▶` / `Ctrl+Enter` → `CommandManager.execute("run.python")`
2. `EditorBridge.getValue()` → `Checker` (strip strings/comments, cek `()` balance — fix F-07, tanpa prelude injection)
3. `ExecutionEngine.runIsolated(code, stdin)` → `ProcessBuilder("python", "_active_run.py")` di `files/` dengan `PYTHONPATH=user_packages`, `timeout 30s`, `MAX_CODE_BYTES` 512KB guard (port `executor.py`).
4. Streaming ke `Panel > Output` via `Flow<String>`. `collect_new_images()` polling `files/*.png` (sama seperti Zabacode, tapi Kotlin `FileObserver`).
5. Kalau `stderr` ada traceback → `OracleEngine.humanize(traceback, lineOffset=0)` → kartu `🔮 Oracle` di Panel dengan `tappable line → jump to editor`.
6. Kalau `stderr` SyntaxError → `OracleEngine.autoFix(code, stderr)` → Diff preview `red/green` + `Apply Fix` (port `diff.py` + `compute_line_diff`) → `EditorBridge.applyEdit()` dalam 1 transaksi + `Undo Oracle Fix` guarded.

**Aliran `Interactive` (input()):**
- `startInteractiveSession` → `Process` dengan `stdin PIPE`, `stdout PIPE` char-by-char `MAX_INTERACTIVE_QUEUE 10k`, `MAX_INTERACTIVE_DURATION 120s`, `inactivity 60s`. Sama seperti Zabacode `InteractiveSession`, tapi Kotlin `Channel` + `select`.

---

## 6. Struktur Proyek Gradle (yang akan kita `init`)

```
ZCODE/
├── app/
│   ├── build.gradle.kts              # compose, hilt, security-crypto, chaquopy
│   └── src/main/
│       ├── AndroidManifest.xml       # taskAffinity=com.zaba.zcode, allowBackup=false, singleTop
│       ├── assets/editor/            # ace/ (ace.js, mode-python.js, theme-tomorrow_night_eighties.js ...)
│       ├── java/com/zaba/zcode/
│       │   ├── MainActivity.kt       # Compose Workbench
│       │   ├── ZcodeApp.kt           # Hilt Application
│       │   ├── core/
│       │   │   ├── execution/        # ExecutionEngine.kt, InteractiveSession.kt, ImageCollector.kt
│       │   │   ├── oracle/           # OracleEngine.kt (port oracle.py), Diagnostics.kt, Diff.kt
│       │   │   ├── ai/               # AiProvider.kt, Providers.kt (7 + custom), Net.kt
│       │   │   ├── files/            # FileManager.kt, Paths.kt
│       │   │   ├── security/         # KeystoreService.kt, AuthToken.kt
│       │   │   ├── editor/           # EditorBridge.kt, Checker.kt
│       │   │   ├── plugins/          # PluginHost.kt, Registry.kt, implementations/
│       │   │   ├── themes/           # ThemeDefinitions.kt (10 tema retro..cyberpunk)
│       │   │   └── libmanager/       # PackageManager.kt (zabapip port)
│       │   ├── ui/
│       │   │   ├── workbench/        # WorkbenchScreen.kt, ActivityBar.kt, SideBar.kt
│       │   │   ├── editor/           # EditorScreen.kt, QuickTools.kt, TabBar.kt
│       │   │   ├── terminal/         # TerminalPanel.kt
│       │   │   ├── oracle/           # OracleCard.kt, AutoFixDiff.kt
│       │   │   ├── settings/         # SettingsDashboard.kt, PrivacyCard.kt
│       │   │   └── theme/            # ZcodeTheme.kt (Material3)
│       │   └── di/                   # AppModule.kt (ServiceContainer)
│       └── res/values/themes.xml
├── docs/
│   ├── PLAN_ZCODE.md                 # ← file ini
│   ├── COEXISTENCE_FIX.md            # port dari Zabacode docs
│   └── custom-endpoint.md
├── tools/
│   ├── check.sh                      # ./gradlew lint test + security checks (port zabacode/tools/check.sh)
│   └── p4a_hook.py                   # tidak perlu jika full Kotlin, tapi simpan untuk coexistence test
├── build.gradle.kts
├── settings.gradle.kts
└── README.md                         # update dari 3 baris jadi landing page
```

**Manifest kunci (anti-ketuker Zmux):**
```xml
<activity
    android:name=".MainActivity"
    android:launchMode="singleTop"
    android:taskAffinity="com.zaba.zcode"
    android:documentLaunchMode="intoExisting"
    android:allowBackup="false"
    android:exported="true">
```

---

## 7. Modul Demi Modul — Apa yang Dicuri & Apa yang Diperbaiki

### 7.1 Editor — Ace Bundled, tapi Compose Shell

- **Bundle:** Copy `assets/vendor/ace/` Zabacode → `app/src/main/assets/editor/ace/`. Verifikasi di CI: `test -f assets/editor/ace/ace.js`.
- **WebView config:** `setJavaScriptEnabled(true)`, `addJavascriptInterface(EditorBridge)`, `WebViewClient` block remote URL (CSP native).
- **QuickTools:** Compose `LazyRow` di atas WebView: `TAB : ( ) [ ] { } " ' = _ def return import` + `Undo Redo Find Palette` (port Zabacode 4846 baris `index.html` tapi pecah jadi composable).
- **Tabs:** `TabBar` dengan `auto-save 500ms debounce` (port `templates/index.html` localStorage → `DataStore`).
- **Command Palette:** `Ctrl+Shift+P` → `ModalBottomSheet` list `get_all_commands_info()`.
- **Minimap & minimap?** Phase 1 tanpa minimap (berat di mobile), Phase 1.5 tambah.

### 7.2 File Manager — Pydroid Simple

- **Lokasi:** `Context.filesDir/files/` (port `paths.py` logic, hindari `files/files` double nesting, cek `APP_DIR.name == "files"`).
- **Guard:** `secure_filename`, no `..`, no `/`, no `\`, no null byte, no dotfile, `MAX_CODE_BYTES`.
- **UX Pydroid:** `+ New File` FAB, `Open / Manage Files` bottom sheet, `Save As .py` one-tap. Zabacode sudah ada, kita bikin Compose lebih native.
- **SAF:** Untuk `Open Folder` di storage eksternal (Android 11+ scoped).

### 7.3 Execution Engine — Isolasi Beneran

Port `executor.py` 1:1 tapi Kotlin coroutines:

```kotlin
object ExecutionEngine {
    const val MAX_CODE_BYTES = 512 * 1024
    const val MAX_OUTPUT_CHARS = 256 * 1024
    const val DEFAULT_TIMEOUT = 30_000L
    suspend fun runIsolated(code: String, stdin: String = ""): RunResult
    fun startInteractive(code: String): InteractiveSession // Flow<OutputChunk>
}
```

- **Normal runner:** `ProcessBuilder` + `withTimeout(30s)` + `killpg` via `destroyForcibly()` + `awaitClose`.
- **Interactive:** `Channel<Char>(MAX_INTERACTIVE_QUEUE=10_000)` + `totalChars` guard + `outputTruncated` flag + `inactivity 60s` job.
- **Prelude:** Jangan inject `SAFE_INPUT_PATCH` 9 baris ke file user. Bungkus di `ProcessBuilder` wrapper yang `exec` file user terpisah, jadi traceback line number akurat tanpa `line_offset`.
- **Image:** `ImageCollector` `FileObserver` di `files/` + `baseline snapshot` sebelum run + `encode base64 data:` URI + `MAX_IMAGE_BYTES 8MB` skip.

### 7.4 Oracle — Deterministic, Bukan Halu

Port `oracle.py` (20+ `_ERROR_RULES`, `analyze_buffer`, `auto_fix_code`, `offline_reply`):

- **Humanize:** Regex + template `title + explanation + fix` tetap, tambah `line source echo` tappable.
- **Analyze:** `tree-sitter` atau `jep` `ast.parse` via Chaquopy untuk `analyze_buffer` (deep nesting, bare except, mutable defaults, eval, TODO).
- **Auto-Fix:** Gate `if parses(code) -> return noFix (serahkan ke humanize)`. Patch hanya kalau `parses(before)==false && parses(after)==true`. Diff bounded + `Undo Oracle Fix` guarded (cek tab revision sebelum apply, port `CHANGELOG` safety 2026-07-30).
- **Offline reply:** Rule-based tsundere, fallback kalau `allow_offline=true` dan provider rate-limited. Jangan klaim RAG sebelum punya retriever + generator tervalidasi (lihat `CHANGELOG` Unreleased RAG note).

### 7.5 AI Providers — 7 + Custom

Port `ai_provider.py`:

- **Providers:** `openrouter, gemini, groq, mistral, deepseek, ollama, custom` (sama `ALLOWED_PROVIDERS`).
- **Net:** `OkHttp` dengan `CertificatePinner` + `conscrypt` + `certifi` bundle (port `net.py get_ssl_context`). **Hapus** `--trusted-host` forever (fix #22).
- **Custom Endpoint:** `endpoint_url` terpisah dari `api_key`, validasi `http/https`, warning `HTTP cleartext` untuk non-loopback/private (port fix #24). UI tunjukkan destination + TLS status sebelum send.
- **Fallback:** Kalau `429/402` atau `no key` → `Oracle.offline_reply()`.

### 7.6 Keystore — Native, Bukan Python Fallback

- **Primary:** `EncryptedSharedPreferences` `MasterKey.AES256_GCM` + `AES256_SIV` (sudah ada di Zabacode, tapi di Kotlin langsung API tanpa `pyjnius`).
- **Fallback:** `DataStore` encrypted file dengan `HKDF` + `AES/GCM` (ganti HMAC stream cipher Python jadi `Tink` atau `AndroidKeyStore` AES/GCM). `chmod 600` via `File.setReadable(false)`.
- **Merge:** Kalau keystore ada tapi `custom` hilang (upgraded install) → merge dari file fallback (fix #19).

### 7.7 Plugins & Themes — VS Code Style

- **Registry:** `MARKETPLACE_PLUGINS` 12+ + `THEMES` 10 (retro, solarized, dracula, cyberpunk, nord, monokai, synthwave, dsb) port ke `ThemeDefinitions.kt`.
- **Transform plugins 5:** `Auto-Import Optimizer, Duplicate Detector, Comment Generator, Beautifier Pro, Type Hint Generator` — port `PluginExecutor` tapi fix beautifier `//=` longest-first + guard `ast identical`.
- **Command Registry:** Plugin register sebagai `Command`, bukan `if/elif`. `get_all_plugins()` → `CommandManager`.

### 7.8 Library Manager — zabapip Kotlin

- **Catalog:** `KNOWN_LIBRARIES` 50+ dengan `tier`/`mode` offline/online/hybrid/buildtime.
- **Install:** `PyPI` direct via `OkHttp` + `SHA-256` verify + `path traversal` check + `USER_PACKAGES_DIR` + `PYTHONPATH` inject. Fallback `pip` tanpa `--trusted-host` + `TLS_HELP_MESSAGE`.
- **UI:** `Library Manager` card di Settings + `+ Add Library` chip di editor (Pydroid Quick Install feel).

### 7.9 Keamanan & Privacy — Port SECURITY.md

- **Loopback token:** Tidak perlu kalau pakai `WebView bridge`. Kalau tetap pakai `Ktor loopback`, token per-install `128-bit hex` + `hmac.compare_digest`.
- **Headers:** CSP native di `WebViewClient.shouldInterceptRequest`.
- **Privacy drafts:** `DataStore` key `zabacode-persist-drafts` toggle + `Clear Local Drafts` + disclosure `device-only, not encrypted, plain text` (fix #21).
- **JSON validation:** `kotlinx.serialization` strict, reject array/primitive, field type check, `413` kalau oversize, `400 invalid_json_type` (fix #23).
- **Build locking:** `gradle.lockfile` + `version catalog` pinned + `tools/check.sh` → `gradlew check` + `detekt` + `mypy` equivalent `ktlint`.

---

## 8. Roadmap — 4 Fase, Tiap Fase Bisa Di-APK-kan

### Fase 0 — Bootstrap (Minggu 1) — *Kita sekarang di sini — v0.2 anti-regresi*

- [ ] **Init Gradle Kotlin + Compose + Hilt + Room + Security Crypto + versionCatalog (single source, cegah F-09/E-02)**
- [ ] **Copy `assets/vendor/ace` 1.32.4 → `assets/editor/ace` 1.44.0**, setup `WebView file://` + `EditorBridge` (`IEditor` abstraction) + `addJavascriptInterface`
- [ ] **Port `paths.py, security.py, net.py` → Kotlin** (`filesDir` langsung tanpa `files/files`, `EncryptedSharedPreferences`, `OkHttp + certifi` tanpa `trustAllCerts`)
- [ ] **Guard hari 0:** `MAX_CODE 512KB`, `MAX_OUTPUT 256KB`, `debounce resize 100ms` (cegah F-07 + 3 jebakan ZMUX), `taskAffinity=com.zaba.zcode singleTop` (cegah C-50)
- [ ] **UI kerangka: Topbar faded grey `#3A4452` dengan `≡` (tiga garis, tanpa tulisan “hamburger”) + `+` add tab di kanan** — `≡` buka drawer `Settings/About` (text only, no icon)
- [ ] `README.md` baru + `docs/PLAN_ZCODE.md v0.2` + `docs/BUGS_AUDIT_ZABACODE_FOR_ZCODE.md` di-merge ke `main`
- [ ] **CI `build.yml`** (lint `detekt/ktlint` + test `JUnit` + `grep no unverified SSL` + `Ace bundled 1.44` + `certifi` + `provider registry` — port Zabacode #26 + pin Actions SHA)

**Exit criteria:** `gradlew assembleDebug` sukses, WebView Ace 1.44.0 60 FPS, Topbar `≡` + `+` muncul, `healthCheck` Compose, `check.sh` hijau, tidak ada `5000` hardcode.

### Fase 1 — Pydroid-Simple MVP (Minggu 2-3) — *“Pemula langsung Run” — cegah B-10 s/d B-20*

- [ ] **Workbench minimal:** Activity Bar (Explorer/Run/Settings), Editor tabs, Panel Output (debounce `editor.resize()` 100ms — cegah ZMUX resize loncat)
- [ ] **FileManager + Run isolated** (1-tap `▶`, stdin box, output 256KB truncate, timeout 30s, `secure_filename` + `MAX_FILENAME 128` + `OSError` catch — cegah E-01)
- [ ] **Oracle humanize + Analyze** (20+ rule, `async def` support, `line_offset=0` tanpa prelude — tanpa auto-fix dulu, biar aman dari F-02)
- [ ] **Themes 3 dulu** (retro, dracula, nord) + QuickTools `TAB : ( )` (copy Zabacode 4846 baris jadi composable)
- [ ] **Library Manager basic** (install `requests`, `matplotlib` via Chaquopy `pip` + SHA-256 + `path traversal` + `User-Agent Zabacode/1.44` versi tunggal — cegah S-22)
- [ ] **Image capture** untuk `matplotlib` (`collect_new_images(baseline)` shared, `snapshot before`, `dedup`, `skip >8MB` — cegah B-13)

**Exit criteria:** User bisa `print("hello")`, `input()`, `pip install requests`, lihat `plt.savefig("out.png")` inline — 100% offline. 30 tests hijau + beautifier `AST identical` test port.

### Fase 2 — VS Code-Detail (Minggu 4-5) — *“Pro betah” — cegah F-02/F-03/F-04 + S-19/S-23*

- [ ] **Command Palette + Quick Open (`Ctrl+P`)** (port `commands.py` + `navigation.py`)
- [ ] **Oracle Auto-Fix** dengan safety gate `parses(before)==False && parses(after)==True` + `is_success && fixes` + depth-aware `=` + `Single Ace transaction` + `Undo guarded` (`tabId+revision` fingerprint — lihat `index.html:3616`) — cegah F-02/F-03/F-04/B-20 freeze
- [ ] **Diagnostics panel** (`checker.py` tanpa prelude off-by-9, `strip_comments_and_strings`, `async def` — port `diagnostics.py` + `editor_intelligence.py`)
- [ ] **Plugin host + 5 transform** (fix beautifier longest-first `//=` sebelum `//` + `AST identical` 125 kombinasi)
- [ ] **Keystore + Custom Endpoint** (central `ALLOWED_PROVIDERS` + `endpoint_url` terpisah + `http` warning + `EncryptedSharedPreferences` — cegah S-19/S-24)
- [ ] **Privacy card** (`DataStore` `persistDrafts` default `false` untuk fresh install + `Clear` + disclosure plain text — cegah S-21) + **JSON strict** (`invalid_json_type` 400 — cegah S-23)

**Exit criteria:** 132→272 tests ported, `check.sh` hijau, beautifier tidak merusak `def f() -> int:`, Auto-Fix tidak freeze di file besar (bounded diff).

### Fase 3 — Acode-Power (Minggu 6-7) — *“Mobile pro” — Zmux pending, tapi siap embed*

- [ ] **10 themes lengkap + CRT toggle** (port `definitions.py` 10 tema)
- [ ] **Terminal Panel simple dulu** (stdin box, bukan proot) — **Full Alpine proot embed ZMUX ditunda** sesuai kesepakatan “fokus ZCODE dulu”. Kalau embed, pakai `zmux-engine` module + `download on-demand 47MB` + `prompt ZCODE$ ` polos + `post { updateSize() }` + `debounce 100ms` (cegah 3 jebakan ZMUX). **Bukan bundle di Fase 1.**
- [ ] **Git basic** (clone/commit/push via `libgit2`/`JGit` atau proot nanti)
- [ ] **LSP Python** (pyright via Node atau jedi via Chaquopy) → IntelliSense + `organizeImports` + `rename` (port `editor_intelligence.py`)
- [ ] **Plugin marketplace UI** (Compose, `MARKETPLACE_PLUGINS` 12+)
- [ ] **Coexistence check** tetap: `taskAffinity=com.zaba.zcode singleTop` verify via `dumpsys` (walaupun Zmux belum embed, jaga tidak regresi)

**Exit criteria:** APK `arm64-v8a + armeabi-v7a` via App Bundle (universal sideload), `allowBackup=false`, no CDN, `certifi` bundled, `adb` coexist test lulus (ZCODE saja).

### Fase 4 — Polish & Release (Minggu 8)

- [ ] **RAG honest** (kalau mau): *project search* dulu (file paths + symbols), bukan klaim “understand code”. Index lokal, privacy-respect, mobile storage aware (lihat `CHANGELOG` Unreleased RAG plan).
- [ ] **Voice TTS Oracle** (optional)
- [ ] **Performance:** 45-60 FPS, `offload heavy checks` ke `Dispatchers.Default`, WebView prewarm
- [ ] **Publishing:** Play Store listing, F-Droid metadata, `SECURITY.md` Kotlin version

---

## 9. Keputusan yang Sudah Kita Kunci Bareng (v0.2) — tinggal final OK

Diskusi 2026-08-08, rule `honest & aware`:

| # | Pertanyaan | Keputusan v0.2 | Alasan jujur |
|---|---|---|---|
| **Q1** | **Editor** | **Ace 1.44.0 upgrade** (dari 1.32.4) + `IEditor` abstraction siap CodeMirror 6 — **Monaco ditolak** untuk ARMv7 | Ace 45-60 FPS proven, 300KB, fix longest-first + AST identical; CodeMirror 6 juara mobile tapi effort 1 minggu → evaluasi Phase 1.5; Monaco 1-5 MB berat di HP kamu |
| **Q2** | **Python runtime** | **Chaquopy 3.11** (bukan 3.12) `abiFilters arm64-v8a+armeabi-v7a+x86_64` | 3.12 drop `armeabi-v7a` [chaquopy changelog #709]; 3.11 tetap support HP kamu; `buildozer.spec` diganti Gradle Kotlin DSL |
| **Q3** | **Bridge** | **JS Bridge `file://` + `addJavascriptInterface`** | Hapus F-01, S-27, C-50 selamanya; offline murni tanpa `127.0.0.1:5000` yang ketuker Zmux |
| **Q4** | **Terminal** | **Fokus ZCODE dulu** — Panel Output simple di Fase 1, **Zmux full pending** Phase 3 (embed sebagai `zmux-engine` module + download on-demand) | Kamu sudah test ZMUX lancar di HP, tapi ZCODE harus layak dulu; cegah APK 100MB di hari 0; 3 jebakan ZMUX (wrap/pembatas/resize) sudah di-docs |
| **Q5** | **Min SDK + Arch** | **minSdk 26 + App Bundle `arm64-v8a+armeabi-v7a` (universal sideload)** | Zabacode minApi 26 (`EncryptedSharedPreferences` stabil); HP ARMv7 kamu jadi device referensi, Play Store split otomatis hemat kuota |

**Kalau kamu sudah OK dengan tabel di atas, kita langsung kunci → Fase 0 `gradlew init`.** Kalau mau tweak 1 baris, sebut aja (misal “Q1 mau CodeMirror langsung”).

---

## 10. Risiko & Mitigasi — plus pelajaran ZMUX

| Risiko | Prob | Dampak | Mitigasi (sudah include audit 33 bug) |
|---|---|---|---|
| **Chaquopy + matplotlib + numpy** APK 80 MB+ | Tinggi | Medium | App Bundle `arm64-v8a+armeabi-v7a` split + `Chaquopy 3.11` (bukan 3.12) + `abiFilters`. Universal APK cuma buat sideload. |
| **WebView Ace di low-end HP lag** | Medium | Tinggi | `HARDWARE layer`, `prewarm`, `debounce 100ms` (ZMUX resize), `offload analyze` ke `Dispatchers.Default`, test di 2GB RAM target 45-60 FPS. |
| **Oracle port Python → Kotlin meleset** | Medium | Tinggi | Port 1:1 + **272 tests** jadi `oracleTest` Kotlin + `AST identical` 125 kombinasi + `is_success && fixes` guard (F-02/F-03). |
| **Coexistence Zmux ketuker** | Medium | Tinggi | `taskAffinity=com.zaba.zcode singleTop documentLaunchMode=intoExisting` + test `dumpsys` (C-50/S-27). |
| **RAG halu** | Tinggi | Tinggi | Tunda generator, mulai `project search` jujur (file paths + line ranges) — ikut `CHANGELOG` RAG honest. |
| **Scoped storage Android 13+** | Tinggi | Medium | `SAF` + fallback `filesDir` internal, jangan `MANAGE_EXTERNAL_STORAGE` kalau tidak perlu. |
| **3 jebakan ZMUX keulang di ZCODE** | Medium | Tinggi | **Wrap:** `ZCODE$ ` polos + `\[ \]` kalau warna; **Pembatas:** `post { updateSize() }` + `ace.resize()`; **Resize:** debounce 100ms — sudah di Fase 0 guard. |
| **33 bug regresi** | Tinggi kalau nggak di-test | Tinggi | CI `check.sh` port Zabacode: `no unverified SSL`, `Ace 1.44 bundled`, `CSP`, `certifi`, `no CDN`, `273 tests`, `Content-Type 400` test (F-05). |

---

## 11. Definisi “Done” — Kapan Kita Bilang ZCODE v1 Layak Ganti Zabacode?

- [ ] `Zero telemetry`, `GPLv3`, `offline-first` checklist hijau (CI block CDN, unverified SSL, Ace missing, certifi missing, provider registry).
- [ ] 272 test Zabacode ported → Kotlin, `ruff` → `ktlint/detekt` critical 0, `mypy` → `kotlin strict` 0.
- [ ] `▶ Run` untuk 3 snippet Zabacode: `print("hi")`, `input("nama: ")`, `import matplotlib.pyplot as plt; plt.plot([1,2]); plt.savefig("out.png")` → image inline.
- [ ] Oracle: `IndexError list index out of range` → kartu `🔮` dengan line tappable, `Auto-Fix` hanya untuk `SyntaxError` dan `is_success` benar.
- [ ] `pydroid-simple` test: pemula install → buka contoh → `Run` → lihat output tanpa buka Settings.
- [ ] `vscode-detail` test: pro `Ctrl+Shift+P` → `Format Document` → `Command Palette` muncul.
- [ ] `acode-touch` test: QuickTools + context menu + multi-tab + theme switch lancar di 5" HP.

---

## 12. Langkah Besok — Kalau Plan v0.2 Di-ACC

1. **Kamu OK-kan tabel Q1-Q5 v0.2** (atau tweak 1 baris).
2. Aku `gradlew init` Fase 0 skeleton: `app/build.gradle.kts` (versionCatalog, Chaquopy 3.11, `abiFilters`), `AndroidManifest.xml` (`taskAffinity`), `MainActivity.kt` Compose Workbench, `assets/editor/ace` 1.44.0, `tools/check.sh` Kotlin + `docs/BUGS_AUDIT...` guard.
3. `commit` Fase 0 → `push arena/019fddf3-zcode` → CI hijau → baru Fase 1 code (Pydroid-simple).

> **Fokus sesuai request:** ZCODE dulu sampai layak & lancar, Zmux pending Phase 3. Dokumen Zmux 3 jebakan sudah kita amankan jadi tidak perlu embed sekarang.

---

### Lampiran — Contribute & Feedback
- **Contribute:** `Premium` → `Contribute` → langsung `https://github.com/muzape28-blip/ZCODE/issues` (pilih `Bug` / `Feedback` template) — no Gmail, text-only, iconless debug leluasa. Issues jadi sumber feedback utama.
- **No AI/Oracle di kerangka:** skip dulu, fokus Editor/Files/Run/Pip/Theme.

### Lampiran — Link Acuan yang Dibaca

- `muzape28-blip/ZABACODE` README, `ZABACODEE.md` (roadmap v1.2.0 → v1.2.1 VSCode architecture), `SECURITY.md` (Issue #18-#27), `AUDIT_REPORT.md` (F-01 s/d F-09, 144→159 tests), `CHANGELOG.md` (Unreleased Oracle safety + RAG deferred), `buildozer.spec` (p4a.port 5000, archs armeabi-v7a+arm64-v8a, certifi+openssl), `main.py`, `pyproject.toml`, `tools/check.sh`, `build_apk.yml`, `zabacode/web_app.py` (1196 baris, CSP+token+JSON validation), `zabacode/core/*` (executor, oracle, ai_provider, keystore, net, paths, checker), `templates/index.html` (4846 baris).
- Pydroid 3: Play Store `ru.iiec.pydroid3`, blindhelp.net, applist360 — offline interpreter, pip prebuilt wheel, Tkinter, terminal, C/C++/Fortran compiler.
- Acode: `acode.app` + `foxdebug/acode` Play — Ace 1.22, Alpine proot + apk, 250+ plugins, LSP, command palette, Git, FTP/SFTP, AI agents.
- VS Code: `microsoft/vscode` arch — multi-process (Main/Renderer/Extension Host/Language Server), Emitter+Disposable, Command Registry, Service Container.

---

**Siap di-review.** Komen langsung di file ini atau jawab Q1-Q5, nanti aku eksekusi `gradlew init` sesuai pilihanmu. Kalau ada yang mau di-tweak (misal “tidak mau Chaquopy, mau p4a NDK aja” atau “mau Monaco dari awal”), bilang — plan ini sengaja dibikin modular biar pivot tidak ngulang dari nol.

*— ZCODE Plan v0.2, 2026-08-08, cabang `arena/019fddf3-zcode` — include 33 bug audit + 3 jebakan ZMUX, fokus ZCODE dulu*
