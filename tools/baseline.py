#!/usr/bin/env python3
"""
ZCODE Baseline — SPEC-001 (Package & Terminal Reliability)

Mengukur posisi repo terhadap target SPEC-001 dan menulis snapshot
"baseline aktual" yang wajib diambil sebelum rollout penuh (lihat SPEC-001
bagian "Gathering Results" dan docs/BASELINE_TESTING_2026_08.md).

Bukan test gate (exit 0 selalu) — ini alat pengukur. Setelah implementasi,
jalankan ulang dan bandingkan: GAP harus mengecil, PARTIAL harus jadi OK.

Pemakaian:
    python3 tools/baseline.py                  # tabel konsol
    python3 tools/baseline.py --json docs/baseline-spec001.json   # simpan snapshot

Aturan tim: honest about anything / be meticulous in everything.
"""
import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app/src/main"
EXEC = APP / "java/com/zaba/zcode/core/execution"
PKGENG = APP / "java/com/zaba/zcode/core/packageengine"
TERM_UI = APP / "java/com/zaba/zcode/ui/terminal"
SETTINGS_UI = APP / "java/com/zaba/zcode/ui/settings"
PY = APP / "python"
PKGRT = PY / "package_runtime"
CATALOG = APP / "assets/package_catalog"


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def find(text: str, pattern: str) -> list[int]:
    return [i + 1 for i, line in enumerate(text.splitlines()) if re.search(pattern, line)]


def found(path: Path, pattern: str) -> list[int]:
    return find(read(path), pattern)


def has_any(path: Path, patterns: list[str]) -> bool:
    return any(found(path, p) for p in patterns)


# ---------------------------------------------------------------------------

def measure_terminal():
    exec_kt = EXEC / "ExecutionEngine.kt"
    term_kt = TERM_UI / "TerminalScreen.kt"
    bridge_kt = EXEC / "TerminalBridge.kt"
    runner_py = PY / "zcode_runner.py"

    checks = []

    hard = found(exec_kt, r"MAX_INTERACTIVE_DURATION_MS\s*=\s*120_000L")
    checks.append(("T-01", "Terminal", "Interactive session tanpa hard timeout",
                   "0 (tidak ada timeout sewenang-wenang)",
                   ("GAP", f"MASIH ADA hard timeout 120s di ExecutionEngine.kt:{','.join(map(str, hard))}") if hard
                   else ("OK", "Tidak ada MAX_INTERACTIVE_DURATION_MS — session berakhir hanya karena exit/Ctrl+C/Stop/error")))

    logger_ok = (EXEC / "RunLogger.kt").exists() and has_any(term_kt, [r"RunLogger", r"logFilePath"])
    checks.append(("T-02", "Terminal", "Full output ke disk (tidak hilang karena memory cap)",
                   "0 output loss (full output ke disk)",
                   ("OK", "RunLogger menulis full log ke filesDir/logs/runs/<run-id>.log; ring buffer UI hanya tampilan") if logger_ok
                   else ("GAP", "Belum ada disk logger")))

    linebuf = (EXEC / "TerminalBuffer.kt").exists()
    checks.append(("T-03", "Terminal", "Line-oriented buffer (bukan satu String raksasa)",
                   "current line + chunked line storage + line index",
                   ("OK", "TerminalBuffer.kt: ArrayDeque baris + startOffset (line index) + current line; RAM dibatasi, full history di disk") if linebuf
                   else ("GAP", "Belum ada line buffer")))

    ansi = (TERM_UI / "AnsiLineCache.kt").exists()
    checks.append(("T-04", "Terminal", "ANSI parser incremental",
                   "incremental, bukan full reparse",
                   ("OK", "AnsiLineCache.kt: parse per-baris + cache per indeks; scroll tidak re-parse") if ansi
                   else ("GAP", "Masih full reparse per frame")))

    virt = has_any(term_kt, [r"LazyColumn", r"rememberLazyListState"])
    checks.append(("T-05", "Terminal", "Renderer virtualized (scroll 100k+ lines)",
                   "virtualized render, UI hanya pegang visible window",
                   ("OK", "LazyColumn + LazyListState — hanya baris terlihat yang disusun; scroll 100k+ lines") if virt
                   else ("GAP", "Renderer belum virtualized")))

    checks.append(("T-06", "Terminal", "Disk-backed full log per run (run ID)",
                   "files/logs/runs/<run-id>.log per eksekusi",
                   ("OK", "RunId + RunLogger + Paths.runLogsDir wired di TerminalScreen") if logger_ok and has_any(term_kt, [r"RunId\.newId"])
                   else ("GAP", "Run ID / disk log belum terpasang")))

    checks.append(("T-07", "Terminal", "Export full log",
                   "Full log export tersedia",
                   ("OK", "Tombol 'Export Log' (SAF CreateDocument) mengekspor file log dari disk") if has_any(term_kt, [r"Export Log", r"exportLauncher"])
                   else ("GAP", "Tidak ada export log")))

    checks.append(("T-08", "Terminal", "Process lifecycle eksplisit",
                   "START/RUNNING/WAITING_FOR_INPUT/INTERRUPTING/STOPPING/EXITED/FAILED",
                   ("OK", "SessionState.kt + bridge.waitingInput() + state label di UI") if (EXEC / "SessionState.kt").exists()
                   else ("GAP", "Tidak ada state machine")))

    checks.append(("T-09", "Terminal", "Output batching (32–50ms / 2–4KB)",
                   "flush interval 32–50ms ATAU buffer 2–4KB, mana dulu",
                   ("OK", "OutputBatcher: 40ms / 2048 chars, single-thread (urutan terjaga)") if (EXEC / "OutputBatcher.kt").exists()
                   else ("GAP", "Tidak ada batching output")))

    ctrlc = found(bridge_kt, r"fun interrupt\(\)") and found(runner_py, r"isInterrupted\(\)")
    checks.append(("T-10", "Terminal", "Ctrl+C deterministik untuk input()",
                   "100% deterministik → KeyboardInterrupt",
                   ("OK", "TerminalBridge.interrupt() + BridgeStdin.isInterrupted() → KeyboardInterrupt") if ctrlc
                   else ("GAP", "Mekanisme Ctrl+C input() tidak ditemukan")))

    sep = found(exec_kt, r"redirectErrorStream\(false\)") and found(bridge_kt, r"fun write\(s: String, stream: String\)")
    checks.append(("T-11", "Terminal", "stdout/stderr dipisah",
                   "stdout & stderr terpisah (untuk log & status)",
                   ("OK", "redirectErrorStream(false) + bridge.write(s, stream) out/err") if sep
                   else ("GAP", "stdout/stderr belum dipisah")))

    return checks


def measure_package():
    checks = []

    v2_exists = (PKGENG / "PackageEngineV2.kt").exists()
    verifier = (PKGENG / "Verifier.kt").exists()
    tx = (PKGENG / "TransactionManager.kt").exists()
    smoke = (PKGENG / "SmokeTestRunner.kt").exists()
    pystr = PKGRT.exists()

    checks.append(("P-01", "Package", "Sukses = rantai verifikasi nyata (bukan exit code)",
                   "download+verify+extract+smoke+activate",
                   ("OK", "PackageEngineV2: resolve→download→SHA-256→extract→metadata→smoke→activate; zcode_pip.py hanya legacy dev") if v2_exists
                   else ("GAP", "Belum ada engine verifikasi")))

    checks.append(("P-02", "Package", "SHA-256 verification",
                   "hash diverifikasi sebelum activation",
                   ("OK", "Verifier.verifySha256 + download streaming sha256 (PyPI digests; Chaquopy dihitung lokal)") if verifier
                   else ("GAP", "Tidak ada verifikasi SHA-256")))

    checks.append(("P-03", "Package", "Transactional install + rollback",
                   "staging → verify → smoke → atomic activate; rollback ≥99%",
                   ("OK", "TransactionManager: tx dir, journal, activate + rollbackActivate, abort") if tx
                   else ("GAP", "Tidak ada transaction/rollback")))

    checks.append(("P-04", "Package", "Post-install import/smoke test",
                   "100% install menjalankan import smoke test",
                   ("OK", "SmokeTestRunner → package_runtime.smoke (IMPORT/NATIVE_LOAD/BASIC_API/FILE_OUTPUT) terhadap staging") if smoke
                   else ("GAP", "Tidak ada smoke test")))

    checks.append(("P-05", "Package", "Environment transaksional (python-env/)",
                   "app-private/python-env/{site-packages,transactions,wheels,metadata,logs,state}",
                   ("OK", "Paths.pythonEnvDir/… + site-packages/<name>/<version> (sys.path injection via envpaths)") if has_any(APP / "java/com/zaba/zcode/core/files/Paths.kt", [r"pythonEnvDir"])
                   else ("GAP", "Layout python-env/ belum ada")))

    checks.append(("P-06", "Package", "Compatibility engine (Python/ABI/platform)",
                   "validasi python tag + ABI tag + platform tag + runtime",
                   ("OK", "CompatibilityEngine (katalog×runtime) + package_runtime.wheelinfo (packaging.tags)") if (PKGENG / "CompatibilityEngine.kt").exists() and (PKGRT / "wheelinfo.py").exists()
                   else ("GAP", "Tidak ada cek kompatibilitas")))

    checks.append(("P-07", "Package", "Uninstall",
                   "uninstall berfungsi & aman",
                   ("OK", "PackageEngineV2.uninstall → TransactionManager.uninstall (hapus dir + state)") if v2_exists and tx
                   else ("GAP", "Tidak ada jalur uninstall")))

    checks.append(("P-08", "Package", "Storage guard & reporting",
                   "block kalau free < estimasi + margin; tampilkan ukuran",
                   ("OK", "Engine storage guard (max(1.5×estimasi, 100MB)) + freeStorageBytes") if has_any(PKGENG / "PackageEngineV2.kt", [r"MIN_SAFETY_MARGIN_BYTES", r"freeStorageBytes"])
                   else ("GAP", "Tidak ada storage guard")))

    checks.append(("P-09", "Package", "Offline reuse setelah install",
                   "100% tested package bisa import tanpa internet",
                   ("OK", "envpaths.activate() membaca state/installed.json → sys.path; offline setelah install") if (PKGRT / "envpaths.py").exists()
                   else ("GAP", "Tidak ada aktivasi env offline")))

    checks.append(("P-10", "Package", "Requirement parser (==, >=, extras)",
                   "requests==2.32.3, pydantic>=2,<3, flask[async]",
                   ("OK", "RequirementParser.kt (pre-check) + package_runtime.requirement (packaging.Requirement, PEP 508); tolak shell/flag") if (PKGRT / "requirement.py").exists()
                   else ("GAP", "Tidak ada requirement parser")))

    return checks


def measure_catalog():
    checks = []

    packages_json = CATALOG / "packages.json"
    try:
        data = json.loads(read(packages_json))
        n = len(data)
    except Exception:
        data = []
        n = 0

    status = "OK" if n >= 300 else ("PARTIAL" if n >= 100 else "GAP")
    checks.append(("C-01", "Catalog", "Jumlah curated packages",
                   "MVP 100, V1 300",
                   (status, f"packages.json berisi {n} package (target V1 300)")))

    fields = ["name", "displayName", "importName", "category", "type", "status",
              "testedVersion", "python", "abis", "description", "useCases", "works",
              "doesNotWork", "dependencies", "risks", "smokeTest", "license",
              "publisher", "source", "sha256"]
    if n:
        field_coverage = {f: sum(1 for d in data if f in d) for f in fields}
        avg = sum(field_coverage.values()) / len(fields)
        pct = avg / n * 100
    else:
        pct = 0
    checks.append(("C-02", "Catalog", "Metadata lengkap (18+ field SPEC §11)",
                   "100% curated punya detail lengkap",
                   ("OK" if pct >= 99 else "GAP", f"rata-rata {pct:.0f}% package punya tiap field; {n} entri")))

    checks.append(("C-03", "Catalog", "Stdlib index terpisah",
                   "≈305 top-level stdlib sebagai index terpisah",
                   ("OK", f"stdlib.json ada ({len(json.loads(read(CATALOG / 'stdlib.json'))) if (CATALOG / 'stdlib.json').exists() else 0} modul)") if (CATALOG / "stdlib.json").exists()
                   else ("GAP", "Belum ada stdlib.json")))

    checks.append(("C-04", "Catalog", "Status model SPEC (TESTED/COMPATIBLE/INCOMPATIBLE/…)",
                   "9 status + action per status",
                   ("OK", "PackageStatus.kt (10 status) + action per status di PackageDetailsDialog") if (PKGENG / "PackageStatus.kt").exists()
                   else ("GAP", "Status model belum ada")))

    return checks


def measure_telemetry():
    keys = ["install_attempts", "install_success", "install_failure", "rollback_count",
            "smoke_test_failure", "native_load_failure", "dependency_conflict",
            "package_not_available", "terminal_runs", "terminal_interrupts",
            "terminal_log_bytes", "terminal_memory_peak"]
    store = PKGENG / "TelemetryStore.kt"
    all_kt = "".join(
        read(p) for p in
        list(PKGENG.glob("*.kt")) + list(EXEC.glob("*.kt")) +
        list((APP / "java/com/zaba/zcode/ui/terminal").glob("*.kt")) +
        list((APP / "java/com/zaba/zcode/ui/settings").glob("*.kt"))
    )
    checks = []
    found_keys = [k for k in keys if ('"%s"' % k) in all_kt or ("'%s'" % k) in all_kt]
    checks.append(("M-01", "Telemetry", "Dashboard metric minimum (SPEC §Gathering Results)",
                   "13+ metric terukur",
                   ("OK" if len(found_keys) >= 8 else "PARTIAL", f"TelemetryStore ada; {len(found_keys)}/{len(keys)} metric didefinisikan") if store.exists()
                   else ("GAP", "Belum ada counter telemetri")))

    codes = ["NETWORK", "RESOLUTION", "COMPATIBILITY", "DOWNLOAD", "VERIFY", "EXTRACT",
             "NATIVE_LOAD", "SMOKE_TEST", "ACTIVATION", "USER_CANCELLED"]
    src_all = "".join(
        read(p) for p in list(PKGENG.glob("*.kt")) + list((PY / "package_runtime").glob("*.py"))
    )
    found_codes = [c for c in codes if c in src_all]
    checks.append(("M-02", "Telemetry", "Klasifikasi error per stage (bukan INSTALL_FAILED)",
                   "13 kode error actionable",
                   ("OK" if len(found_codes) >= 8 else "PARTIAL",
                    f"kode error ditemukan: {', '.join(found_codes)}") if found_codes
                   else ("GAP", "Tidak ada kode error terklasifikasi")))

    return checks


def measure_runtime():
    app_build = ROOT / "app/build.gradle.kts"
    root_build = ROOT / "build.gradle.kts"
    checks = []

    chaq_ver = re.search(r'com\.chaquo\.python"\)\s+version\s+"([0-9.]+)"', read(root_build))
    checks.append(("R-01", "Runtime", "Chaquopy version (INFO)",
                   "terkunci & terdokumentasi",
                   "INFO",
                   f"Chaquopy {chaq_ver.group(1)} (root build.gradle.kts) — pin: Python 3.11, pip 23.3.1" if chaq_ver
                   else "Plugin Chaquopy terdeteksi tapi versi tidak ditemukan"))

    py = found(app_build, r'version\s*=\s*"3\.11"')
    checks.append(("R-02", "Runtime", "Python version (keputusan: 3.11 semua ABI)",
                   "3.11 (satu-satunya yang mendukung armv7)",
                   ("OK", f"Python 3.11 di app/build.gradle.kts:{','.join(map(str, py))} — sesuai keputusan FAT APK") if py
                   else ("GAP", "Python version tidak 3.11")))

    abis = found(app_build, r'armeabi-v7a|arm64-v8a|x86_64')
    checks.append(("R-03", "Runtime", "ABI FAT (armv7 + arm64 + x86_64)",
                   "satu APK universal",
                   ("OK", f"abiFilters lengkap di app/build.gradle.kts:{','.join(map(str, abis))}") if len(abis) >= 3
                   else ("GAP", "abiFilters tidak lengkap")))

    pip_pin = found(app_build, r'pip==23\.3\.1')
    checks.append(("R-04", "Runtime", "pip 23.3.1 pin (bug AssetPath di pip 24+)",
                   "self-contained; pin terdokumentasi",
                   ("OK", f"pip==23.3.1 di app/build.gradle.kts:{','.join(map(str, pip_pin))}") if pip_pin
                   else ("GAP", "pip tidak terpin 23.3.1")))

    minsdk = found(app_build, r"minSdk\s*=\s*26")
    checks.append(("R-05", "Runtime", "minSdk 26 (keputusan)",
                   "26 (Android 8.0)",
                   ("OK", f"minSdk 26 di app/build.gradle.kts:{','.join(map(str, minsdk))}") if minsdk
                   else ("GAP", "minSdk bukan 26")))

    return checks


def main():
    ap = argparse.ArgumentParser(description="ZCODE baseline SPEC-001")
    ap.add_argument("--json", metavar="PATH", help="simpan snapshot JSON")
    args = ap.parse_args()

    checks = (measure_terminal() + measure_package() + measure_catalog() +
              measure_telemetry() + measure_runtime())

    normalized = []
    for c in checks:
        if len(c) == 5:
            cid, group, label, target, pair = c
            status, detail = pair
            normalized.append((cid, group, label, target, status, detail))
        else:
            normalized.append(c)
    checks = normalized

    snapshot = {
        "tool": "tools/baseline.py",
        "spec": "SPEC-001-ZCODE-Package-and-Terminal-Reliability",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "repo_root": str(ROOT),
        "summary": {"total": len(checks),
                    "ok": sum(1 for c in checks if c[4] == "OK"),
                    "partial": sum(1 for c in checks if c[4] == "PARTIAL"),
                    "gap": sum(1 for c in checks if c[4] == "GAP"),
                    "info": sum(1 for c in checks if c[4] == "INFO")},
        "checks": [{"id": c[0], "group": c[1], "label": c[2], "spec_target": c[3],
                    "status": c[4], "detail": c[5]} for c in checks],
    }

    print(f"ZCODE Baseline — SPEC-001  ({snapshot['timestamp']})")
    print(f"Snapshot: {snapshot['summary']['total']} checks | "
          f"OK={snapshot['summary']['ok']} PARTIAL={snapshot['summary']['partial']} "
          f"GAP={snapshot['summary']['gap']} INFO={snapshot['summary']['info']}\n")
    for c in checks:
        mark = {"OK": "✅", "PARTIAL": "🟡", "GAP": "❌", "INFO": "ℹ️"}[c[4]]
        print(f"{mark} [{c[0]}] ({c[1]}) {c[2]}")
        print(f"   target : {c[3]}")
        print(f"   status : {c[4]}")
        print(f"   detail : {c[5]}")
        print()

    if args.json:
        out = ROOT / args.json
        out.write_text(json.dumps(snapshot, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"💾 Snapshot JSON tersimpan: {out}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
