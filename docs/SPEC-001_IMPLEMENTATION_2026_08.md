# 📦 Laporan Implementasi SPEC-001 — Package & Terminal Reliability (2026-08-12)

Sesi: `arena/019ff1f4-zcode` · Status: **BELUM di-commit** (menunggu review user).
Acuan: SPEC-001-ZCODE-Package-and-Terminal-Reliability + keputusan forum 2026-08-12.

Format laporan sesuai SPEC-001 "Agent Definition of Done":
`CHANGE / FILES / TESTS / RESULT / KNOWN LIMITATIONS / NEXT RISK` per fase.

---

## Ringkasan Eksekutif

Baseline SPEC-001 (alat `tools/baseline.py`):

| | Sebelum | Sesudah |
|---|---|---|
| OK | 5 | **31** |
| PARTIAL | 3 | **0** |
| GAP | 23 | **0** |
| INFO | 1 | 1 |
| Total | 32 | 32 |

Test: **216 lulus** (171 existing + 45 baru), `tools/check.sh` hijau,
Kotlin lexical sanity 46 file lolos. Tidak ada file di-commit.

---

## PHASE 0 — Instrumentation & Hotfix

### Terminal

**CHANGE**
- Hapus hard timeout interactive: `MAX_INTERACTIVE_DURATION_MS = 120_000` dan
  semua pemakaiannya (`waitForExit()` kini menunggu tanpa batas — session selesai
  hanya karena exit / Ctrl+C / Stop / error / OS).
- `SessionState` eksplisit: START → RUNNING ⇄ WAITING_FOR_INPUT → INTERRUPTING /
  STOPPING → EXITED / FAILED. `waitingInput()` dari Python menandai `input()` nge-blok.
- Output batching: `OutputBatcher` (40ms / 2048 chars, single-thread — urutan terjaga).
- stdout/stderr dipisah: `TerminalBridge.write(s, stream)` + `redirectErrorStream(false)`;
  `zcode_runner.py` memakai `BridgeStdout(stream="out")` + `BridgeStderr(stream="err")`.
- Run ID per session (`RunId`) + disk-backed full log (`RunLogger` →
  `filesDir/logs/runs/<run-id>.log` ber-timestamp, stream tag, exit code, state).
- Metrik: memori tampilan (chars), log bytes, free storage (StatFs) di bar bawah.
- Ring buffer UI dipertahankan sementara (SPEC Phase 0 #7) → diganti line buffer di Phase 3.

**FILES**
- `core/execution/ExecutionEngine.kt`, `TerminalBridge.kt` (M)
- `core/execution/SessionState.kt`, `OutputBatcher.kt`, `RunLogger.kt` (N)
- `python/zcode_runner.py` (M)
- `ui/terminal/TerminalScreen.kt` (M — run id, logger, state, export, metrik)

### Package

**CHANGE**
- Audit invokasi pip: `zcode_pip.py` dipertahankan HANYA untuk legacy/dev
  (`pip_main --target user_packages`); UI baru tidak lagi memakainya (Rule 7).
- Runtime probe: `package_runtime/probe.py` menangkap Python version, ABI,
  platform, pip version, wheel tags (packaging.tags), Chaquopy version →
  `python-env/state/runtime.json` via `RuntimeProbe.kt`.
- Telemetri: `TelemetryStore` (JSON atomic) — 12+ metric dashboard + event
  failure terklasifikasi per stage; init di `ZcodeApp`.
- Transaction ID: `tx_<ts>_<n>_<hex>` + journal `state/transactions.json`
  (+ SQLite `transactions`).

**FILES**
- `core/packageengine/TelemetryStore.kt`, `RuntimeProbe.kt` (N)
- `python/package_runtime/probe.py` (N), `ZcodeApp.kt` (M)

**TESTS** — existing 171 hijau (guard S-18, SIGINT, dsb dipertahankan).
**RESULT** — PASS (baseline T-01/T-06/T-07/T-08/T-09/T-11 → OK).
**NEXT RISK** — `waitForExit()` tanpa timeout: pastikan UI selalu punya jalur Stop
(Back/Ctrl+C memanggil `sendKill()` di `DisposableEffect`).

---

## PHASE 1 — Library + Minimum Real Package Engine

**CHANGE**
- Environment transaksional `python-env/`: `site-packages/<norm>/<version>/`,
  `transactions/<tx-id>/`, `wheels/`, `metadata/`, `logs/`, `state/`.
  Aktivasi = update `state/installed.json` (temp+rename) + pindah staging;
  `zcode_runner` meng-inject path aktif via `package_runtime.envpaths.activate`.
- `PackageEngineV2` (satu-satunya backend — Library & Manual sama-sama lewat sini):
  `parse → resolve → storage guard → download → SHA-256 → extract (path-safe) →
  metadata validate → smoke test (staging) → atomic activate → telemetri`.
- `Verifier`: SHA-256 streaming, proteksi path traversal (reject `../`, absolute,
  keluar staging), validasi `*.dist-info/METADATA` + `RECORD`.
- `PackageRepository`: katalog 300 package (spec §19) + stdlib index 305 + smoke-tests.
- `CompatibilityEngine`: status per runtime (Python/ABI/API) + katalog = knowledge base.
- `SmokeTestRunner` → `package_runtime.smoke` (IMPORT / NATIVE_LOAD / BASIC_API /
  FILE_OUTPUT / OFFLINE_RESTART; time-bounded; sys.path dipulihkan).
- Katalog: `packages.json` 300 entri (10 kategori pas hitungan SPEC §10),
  `stdlib.json` (305 modul Py3.11), `smoke-tests.json` (10 manifest),
  `tested-manifest.json` (10 versi TESTED: requests 2.32.3, httpx 0.27.2,
  flask 3.0.3, bs4 4.12.3, tqdm 4.66.4, rich 13.7.1, openpyxl 3.1.5,
  numpy 1.26.4, matplotlib 3.6.0, pillow 10.3.0).

**FILES**
- `core/packageengine/*` (PackageEngineV2, PackageStatus, PackageDetails,
  PackageRepository, CompatibilityEngine, RequirementParser, DependencyResolver,
  WheelSelector, Verifier, TransactionManager, SmokeTestRunner, PackageDb, PyCall) (N)
- `python/package_runtime/{__init__,probe,requirement,wheelinfo,resolve,smoke,envpaths}.py` (N)
- `assets/package_catalog/*` (N, via `tools/generate_catalog.py`)
- `ui/settings/PipScreen.kt` (M — Library tab + details 18 field + action per status)

**TESTS** — `test_zcode_package_runtime.py`: 45 test (requirement, wheel tag,
resolve mock, smoke, envpaths, probe).
**RESULT** — PASS (baseline P-01..P-10, C-01..C-04 → OK).
**NEXT RISK** — Package yang butuh `pkg_resources`/entry-points: layout
`<norm>/<version>/` + sys.path injection mendukung `importlib.metadata`, tapi
belum diuji untuk semua package berat.

---

## PHASE 2 — Manual Install

**CHANGE**
- `RequirementParser.kt` (pre-check anti shell/flag) + `package_runtime.requirement`
  (kanonik PEP 508 via `packaging.Requirement`): dukung `requests`,
  `requests==2.32.3`, `pydantic>=2,<3`, `numpy==1.26.*`, `flask[async]`,
  `requirements.txt` (baris + komentar); tolak shell command & flag pip.
- `DependencyResolver` (Python `resolve.py`): sumber local wheel cache → PyPI JSON
  API → Chaquopy index (`https://chaquo.com/pypi-13.1/`, docs 17.0) ; filter
  version + Requires-Python + wheel tag (packaging.tags, sys_tags di device);
  tolak sdist; deteksi konflik versi; marker environment (extras, python_version).
- Flow UI: Parse → Resolve → **konfirmasi hanya kalau risky** (experimental /
  belum TESTED) → Install. Installation Console (step Begin/Log/OK/FAIL) + log mentah.
- Wheel-only: bila tidak ada wheel kompatibel → `PACKAGE_NOT_AVAILABLE` + alasan
  (bukan install palsu).

**FILES**
- `core/packageengine/{RequirementParser,DependencyResolver,WheelSelector}.kt` (N)
- `python/package_runtime/{requirement,resolve,wheelinfo}.py` (N)
- `ui/settings/PipScreen.kt` (M — Manual tab + risky dialog)

**TESTS** — requirement parser 14 test, resolve 6 test (mock HTTP).
**RESULT** — PASS (baseline P-10 → OK).
**NEXT RISK** — Index Chaquopy `pypi-13.1` tanpa hash upstream: wheel Chaquopy
diverifikasi SHA-256 hasil hitung lokal (bukan expected) — dicatat di Verifier.

---

## PHASE 3 — Ecosystem

**CHANGE**
- Katalog 300 (knowledge base: TESTED/COMPATIBLE/EXPERIMENTAL/INCOMPATIBLE/
  UNAVAILABLE tetap searchable + dijelaskan).
- Tested manifest (prioritas wheel ZCODE) → diumpankan ke resolver.
- Wheel builder: `.github/workflows/build-wheels.yml` + `tools/wheel-builder/`
  (toolchain resmi Chaquopy `pip install chaquopy`; per ABI; verify job pakai
  `TestWheelInfo`). **SKELETON — belum diverifikasi build nyata (butuh NDK).**
- Support request: `PackageEngineV2.requestSupport` → `state/support-requests.json`
  (termasuk info runtime perangkat).
- Compatibility database: `PackageDb` SQLite schema persis SPEC §3 (6 tabel) +
  sinkron `installed_packages` & `transactions`.
- Terminal platform penuh: `TerminalBuffer` (line-oriented + line index),
  `AnsiLineCache` (incremental per-baris), renderer virtualized (`LazyColumn`),
  Export Log (SAF), storage guard (50MB + metrik), stress test checklist
  (`docs/BASELINE_TESTING_2026_08.md`).
- CI: `.github/workflows/build.yml` + `ci/workflows/build.yml` + `tools/check.sh`
  kini menjalankan `test_zcode_package_runtime.py`.

**FILES**
- `.github/workflows/build-wheels.yml`, `tools/wheel-builder/*` (N)
- `core/packageengine/PackageDb.kt` (N)
- `core/execution/TerminalBuffer.kt`, `ui/terminal/AnsiLineCache.kt` (N)
- `ui/terminal/TerminalScreen.kt` (M — virtualized)
- `tools/generate_catalog.py` (N), `assets/package_catalog/` (N)

**TESTS** — 45 baru; check.sh + CI diperluas.
**RESULT** — PASS (baseline 31 OK / 0 GAP).
**NEXT RISK** — Wheel builder butuh runner dengan NDK untuk diverifikasi;
renderer LazyColumn perlu uji manual di device (scroll 100k lines, ANSI).

---

## Known Limitations (jujur)

1. **Chaquopy index `pypi-13.1`**: per docs Chaquopy 17.0 FAQ masih memakai URL
   ini; jika berubah per versi Chaquopy, update `probe.CHAQUOPY_INDEX_URL`.
2. **Wheel Chaquopy tanpa hash upstream** → SHA-256 dihitung & dicatat lokal
   (bukan perbandingan expected).
3. **Dual backend**: PackageEngineV2 butuh Chaquopy (device); di desktop dev,
   PipScreen menampilkan pesan engine tidak tersedia (logika engine diuji via
   pytest, bukan di device).
4. **Smoke test time-bounded best-effort**: thread join timeout tidak bisa
   membunuh native code yang nge-blok (didokumentasikan di smoke.py).
5. **requirements.txt**: didukung per-baris (install berurutan per baris),
   bukan satu transaction multi-package.
6. **Wheel builder** masih skeleton (belum ada NDK di runner CI).
7. **16KB page device**: wheel native lama bisa gagal load → status
   `UNAVAILABLE` + knowledge base (keputusan forum).

## Next Steps

1. Review user → commit (sesi ini sengaja tidak commit).
2. Build APK di CI (`assembleDebug`) untuk memvalidasi kompilasi Kotlin —
   sandbox tidak punya JDK/Android SDK.
3. Baseline manual di device (`docs/BASELINE_TESTING_2026_08.md`) → isi KPI.
4. Verifikasi install nyata: requests, numpy, matplotlib (Phase 1 tested list).
5. Upgrade chaquopy `pypi-13.1` → index baru bila docs berubah.
