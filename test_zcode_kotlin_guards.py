"""
ZCODE Kotlin static guards — regression test untuk error compile yang pernah
terjadi di CI (compileDebugKotlin). Setiap error compile yang ditemukan di CI
WAJIB dijadikan test di sini supaya tidak muncul lagi.

Riwayat error yang di-guard:
1. TransactionManager.kt:29 — "Unresolved reference: context" (data class
   Transaction memakai `context` yang tidak ada di scope-nya). Fix: simpan
   logDir saat create(); test: body data class Transaction TIDAK boleh
   memakai `context`.
2. ExecutionEngine.kt — TerminalBridge dipanggil dengan trailing lambda yang
   nyasar ke parameter onState (bukan onExit). Fix: named arguments.
   Test: semua pemanggilan TerminalBridge memakai named arguments.
3. PipScreen.kt — withContext() (suspend) dipanggil di dalam callback non-
   suspend (onStep/onLog). Fix: scope.launch.
   Test: denganContext di PipScreen tidak muncul di dalam lambda callback
   (pola `-> kotlinx.coroutines.withContext` / `-> withContext`).
4. PipScreen.kt — Modifier.weight unresolved di TabBox (bukan receiver
   RowScope). Fix: extension RowScope.TabBox.
   Test: TabBox dideklarasikan dengan receiver RowScope.
5. TerminalScreen.kt — local function appendToTerminal dipanggil sebelum
   didefinisikan (Kotlin tidak hoist local fun). Fix: pindah definisi ke atas.
   Test: baris `fun appendToTerminal` < baris pemakaian pertama.

Run: pytest test_zcode_kotlin_guards.py -v
"""
import re
from pathlib import Path

ROOT = Path(__file__).parent
APP = ROOT / "app/src/main/java/com/zaba/zcode"
PKGENG = APP / "core/packageengine"
EXEC = APP / "core/execution"
UI = APP / "ui"


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8", errors="replace") if p.exists() else ""


def lines_where(text: str, pattern: str) -> list[int]:
    return [i + 1 for i, line in enumerate(text.splitlines()) if re.search(pattern, line)]

def strip_kt_comments(text: str) -> str:
    """Buang komentar // dan /* */ dari sumber Kotlin.

    WAJIB dipakai guard yang mencocokkan POLA KODE. Tanpa ini, komentar yang
    menjelaskan bug lama (mis. \"dulu ditulis item(key = { -1L })\") ikut terdeteksi
    dan membuat test gagal padahal kodenya sudah benar — guard yang berbohong
    lebih berbahaya daripada tidak ada guard.
    """
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    text = re.sub(r"//[^\n]*", "", text)
    return text



# ---------------------------------------------------------------------------
# 1. Transaction data class tidak boleh memakai `context`
# ---------------------------------------------------------------------------

class TestTransactionNoContextRef:
    def test_logfile_does_not_use_context(self):
        txt = read(PKGENG / "TransactionManager.kt")
        assert "context" not in txt.split("data class Transaction(")[1].split("data class PlanPackage")[0], (
            "data class Transaction memakai `context` yang tidak ada di scope-nya "
            "(error CI 'Unresolved reference: context'). Simpan logDir saat create()."
        )

    def test_transaction_has_logdir_param(self):
        txt = read(PKGENG / "TransactionManager.kt")
        m = re.search(r"data class Transaction\([^)]*\)", txt)
        assert m and "logDir" in m.group(0), "Transaction harus punya param logDir"

    def test_create_passes_logdir(self):
        txt = read(PKGENG / "TransactionManager.kt")
        assert "Paths.pythonLogs(context)" in txt, "create() harus mengisi logDir dari Paths.pythonLogs"


# ---------------------------------------------------------------------------
# 2. TerminalBridge dipanggil dengan named arguments
# ---------------------------------------------------------------------------

class TestTerminalBridgeNamedArgs:
    def test_all_calls_use_named_args(self):
        for f in [EXEC / "ExecutionEngine.kt"]:
            txt = read(f)
            for i, line in enumerate(txt.splitlines(), start=1):
                if "TerminalBridge(" in line and "class TerminalBridge" not in line:
                    # kumpulkan 8 baris ke depan untuk lihat named args
                    following = txt.splitlines()[i:i + 8]
                    joined = " ".join([line]) + " " + " ".join(following)
                    assert ("onOutput" in joined and "onExit" in joined), (
                        f"{f.name}:{i} — TerminalBridge harus dipanggil dengan named "
                        f"args (onOutput=..., onExit=...). Trailing lambda nyasar ke onState."
                    )


# ---------------------------------------------------------------------------
# 3. withContext tidak dipakai di dalam lambda callback non-suspend (PipScreen)
# ---------------------------------------------------------------------------

class TestNoSuspendInCallback:
    def test_pipscreen_no_withContext_inside_callback_lambda(self):
        txt = read(UI / "settings/PipScreen.kt")
        bad = lines_where(txt, r"->\s*(kotlinx\.coroutines\.)?withContext\(")
        assert not bad, (
            f"PipScreen.kt:{bad} — withContext (suspend) dipanggil di dalam lambda "
            f"callback non-suspend. Ganti dengan scope.launch { ... }."
        )


# ---------------------------------------------------------------------------
# 4. TabBox punya receiver RowScope (agar Modifier.weight resolve)
# ---------------------------------------------------------------------------

class TestTabBoxRowScope:
    def test_tabbox_is_rowscope_extension(self):
        txt = read(UI / "settings/PipScreen.kt")
        assert re.search(r"RowScope\.TabBox\(", txt), (
            "TabBox harus extension RowScope (Modifier.weight unresolved di luar scope)."
        )


# ---------------------------------------------------------------------------
# 5. Local function dideklarasikan sebelum pemakaian (TerminalScreen)
# ---------------------------------------------------------------------------

class TestLocalFunDeclaredBeforeUse:
    def test_append_to_terminal_declared_first(self):
        txt = read(UI / "terminal/TerminalScreen.kt")
        decl = lines_where(txt, r"fun appendToTerminal\(")
        uses = [l for l in lines_where(txt, r"appendToTerminal\(") if l not in decl]
        assert decl, "appendToTerminal harus dideklarasikan"
        assert decl[0] < min(uses), (
            f"appendToTerminal dipanggil di baris {min(uses)} sebelum dideklarasi "
            f"di baris {decl[0]} (Kotlin tidak hoist local function)."
        )


# ---------------------------------------------------------------------------
# 6. Guard umum: brace balance + semua pemanggilan startInteractiveSession
#    memakai named args (kontrak API berubah di SPEC-001)
# ---------------------------------------------------------------------------

class TestGeneralCompileGuards:
    def test_brace_balance_new_files(self):
        files = list(PKGENG.glob("*.kt")) + list(EXEC.glob("*.kt")) + \
            [UI / "terminal/AnsiLineCache.kt"]
        for f in files:
            txt = read(f)
            assert txt.count("{") == txt.count("}"), f"Kurung tidak seimbang: {f}"

    def test_start_interactive_session_named_args(self):
        txt = read(UI / "terminal/TerminalScreen.kt")
        i = txt.find("startInteractiveSession(")
        assert i >= 0
        snippet = txt[i:i + 1600]
        for kw in ["context =", "file =", "runId =", "onOutput =", "onExit =", "onState ="]:
            assert kw in snippet, f"startInteractiveSession harus pakai named arg {kw}"


# ===========================================================================
# 7. ANTI-REGRESI PAKET PERBAIKAN 2026-08-12 ("hentikan pendarahan")
#
# Setiap test di bawah ini mewakili SATU bug nyata yang pernah membuat ZCODE
# force close / mematikan fitur di perangkat user. Jangan hapus tanpa mengerti
# apa yang dijaga — komentar di tiap test menjelaskan gejalanya.
# ===========================================================================

ROOT_DIR = ROOT
GRADLE_APP = ROOT / "app/build.gradle.kts"
PY_DIR = ROOT / "app/src/main/python"
DIAG = APP / "core/diagnostics"


class TestChaquopyPipBundle:
    """FATAL: 'ModuleNotFoundError: No module named packaging' di semua install.

    pip/setuptools/wheel HANYA memuat salinan ter-vendor (pip._vendor.packaging,
    pkg_resources._vendor.packaging, wheel.vendored.packaging) yang tidak bisa
    di-`import packaging`. Tanpa entri eksplisit di blok pip{}, tiga modul runtime
    kita gagal import dan SELURUH fitur Install Modules mati.
    """

    def test_packaging_dibundel(self):
        txt = read(GRADLE_APP)
        assert re.search(r'install\("packaging[=<>~]', txt), (
            "app/build.gradle.kts blok chaquopy.pip WAJIB memuat install(\"packaging==...\"). "
            "Tanpa ini Install Modules mati total di perangkat."
        )

    def test_modul_yang_butuh_packaging_masih_ada(self):
        # Kalau salah satu file ini dihapus/di-refactor, guard di atas harus ditinjau ulang.
        butuh = ["requirement.py", "resolve.py", "wheelinfo.py"]
        pemakai = [
            f.name for f in (PY_DIR / "package_runtime").glob("*.py")
            if re.search(r"^\s*(from|import)\s+packaging", read(f), re.MULTILINE)
        ]
        for f in butuh:
            assert f in pemakai, f"{f} diharapkan meng-import packaging"


class TestPythonStartTerpusat:
    """Force close saat tap Run: Python.start() balapan antar-thread.

    preWarmPython() (thread IO saat app dibuka) bisa bertabrakan dengan thread Run
    karena pola `if (!isStarted()) start()` tidak sinkron. Inisialisasi ganda di
    layer native berisiko SIGSEGV — dan SIGSEGV TIDAK tertangkap CrashReporter,
    sehingga user hanya melihat aplikasi mati tanpa pesan apa pun.
    """

    def test_hanya_pythonruntime_yang_memanggil_start(self):
        offenders = []
        for f in APP.rglob("*.kt"):
            if f.name == "PythonRuntime.kt":
                continue
            for i, line in enumerate(read(f).splitlines(), 1):
                if re.search(r"^\s*Python\.start\(", line):
                    offenders.append(f"{f.relative_to(ROOT)}:{i}")
        assert not offenders, (
            "Python.start() hanya boleh dipanggil dari PythonRuntime.ensureStarted() "
            f"(kunci global). Pemanggil liar: {offenders}"
        )

    def test_ensurestarted_memakai_synchronized(self):
        txt = read(EXEC / "PythonRuntime.kt")
        assert "synchronized(lock)" in txt, "ensureStarted wajib memakai kunci"
        # cek-ulang di DALAM kunci (double-checked locking) — inti perbaikannya
        i = txt.find("synchronized(lock)")
        assert "Python.isStarted()" in txt[i:i + 400], (
            "Harus ada pengecekan ulang isStarted() DI DALAM blok synchronized, "
            "kalau tidak celah race-nya masih terbuka."
        )


class TestDiagnostikTerpasang:
    """Tanpa PC/logcat, breadcrumb + crash handler adalah satu-satunya mata kita."""

    def test_file_diagnostik_ada(self):
        assert (DIAG / "Breadcrumb.kt").exists(), "Breadcrumb.kt hilang"
        assert (DIAG / "CrashReporter.kt").exists(), "CrashReporter.kt hilang"

    def test_dipasang_di_application(self):
        txt = read(APP / "ZcodeApp.kt")
        assert "Breadcrumb.init(" in txt, "Breadcrumb.init wajib dipanggil di ZcodeApp"
        assert "CrashReporter.install(" in txt, "CrashReporter.install wajib dipanggil di ZcodeApp"

    def test_breadcrumb_flush_setiap_baris(self):
        # Tanpa flush(), baris terakhir HILANG persis saat crash — momen paling penting.
        txt = read(DIAG / "Breadcrumb.kt")
        assert "w.flush()" in txt, "Breadcrumb wajib flush() tiap baris"

    def test_breadcrumb_tidak_pernah_melempar(self):
        txt = read(DIAG / "Breadcrumb.kt")
        assert txt.count("catch (e: Throwable)") >= 4, (
            "Semua operasi Breadcrumb wajib dibungkus catch Throwable — "
            "diagnostik tidak boleh menjadi sumber crash baru."
        )

    def test_jejak_jalur_run_lengkap(self):
        """Urutan langkah harus terekam sampai script mulai, supaya TKP terlihat."""
        wb = read(UI / "workbench/WorkbenchScreen.kt")
        ts = read(UI / "terminal/TerminalScreen.kt")
        ex = read(EXEC / "ExecutionEngine.kt")
        for tag, txt, nama in [
            ("FAB_TAP", wb, "WorkbenchScreen"),
            ("TERMINAL_COMPOSE", wb, "WorkbenchScreen"),
            ("TERMINAL_EFFECT", ts, "TerminalScreen"),
            ("SESSION_START_CALL", ts, "TerminalScreen"),
            ("PY_THREAD_BEGIN", ex, "ExecutionEngine"),
            ("SCRIPT_BEGIN", ex, "ExecutionEngine"),
        ]:
            assert tag in txt, f"Breadcrumb '{tag}' hilang dari {nama}"


class TestTerminalRegresiPR14:
    """Tiga regresi yang lahir di PR #14 dan berjalan di detik pertama layar terminal."""

    def test_batcher_start_di_dalam_effect(self):
        # `batcher.start()` telanjang di badan composable = side-effect tanpa
        # siklus hidup; thread bisa lahir yatim saat komposisi dibatalkan.
        txt = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        assert "DisposableEffect(batcher)" in txt, (
            "batcher wajib dikelola DisposableEffect(batcher) { start(); onDispose { close() } }"
        )
        # setiap pemanggilan start() harus berada SETELAH pembuka DisposableEffect(batcher)
        # dan sebelum blok itu ditutup (dicek via jarak baris — cukup untuk pola ini).
        lines = txt.splitlines()
        effect_line = next(i for i, l in enumerate(lines) if "DisposableEffect(batcher)" in l)
        starts = [i for i, l in enumerate(lines) if re.search(r"\bbatcher\.start\(\)", l)]
        assert starts, "batcher.start() harus tetap dipanggil"
        for s in starts:
            assert effect_line < s <= effect_line + 5, (
                f"batcher.start() di baris {s + 1} berada di luar blok "
                f"DisposableEffect(batcher) (baris {effect_line + 1})."
            )

    def test_tidak_ada_key_lambda_di_lazycolumn(self):
        # `item(key = { -1L })` mengirim OBJEK LAMBDA sebagai key, bukan angka.
        txt = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        assert not re.search(r"item\(\s*key\s*=\s*\{", txt), (
            "item(key = { ... }) mengirim lambda sebagai key — Compose menyimpan key "
            "ke Bundle; key bertipe lambda adalah bom waktu. Hapus key-nya."
        )

    def test_key_items_tidak_memakai_startoffset(self):
        # startOffset bukan Compose state & bergeser saat buffer trim → key ganda
        # → IllegalArgumentException "Key was used multiple times" = force close.
        txt = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        assert not re.search(r"items\([^)]*key\s*=\s*\{[^}]*startOffset", txt), (
            "key LazyColumn tidak boleh diturunkan dari buffer.startOffset (tidak stabil)."
        )

    def test_requestfocus_ditunda_dan_aman(self):
        # requestFocus() sebelum node ter-place → "FocusRequester is not initialized".
        txt = read(UI / "terminal/TerminalScreen.kt")
        i = txt.find("focusRequester.requestFocus()")
        assert i >= 0
        window = txt[max(0, i - 300):i + 100]
        assert "runCatching" in window, "requestFocus() wajib dibungkus runCatching"
        assert "withFrameNanos" in window, (
            "requestFocus() wajib ditunda sampai satu frame terlewati (withFrameNanos)"
        )

    def test_telemetri_tidak_di_main_thread(self):
        # 2x tulis telemetry.json + 1x flush RunLogger per batch 40ms = ±75 tulis
        # file/detik di UI thread → ANR di eMMC lambat.
        txt = read(UI / "terminal/TerminalScreen.kt")
        i = txt.find("fun appendToTerminal(")
        body = txt[i:i + 1400]
        assert "Dispatchers.IO" in body, (
            "appendToTerminal wajib memindahkan tulis-disk (RunLogger/TelemetryStore) "
            "ke Dispatchers.IO — jangan di Main thread."
        )


class TestThrowableTertangkap:
    """OutOfMemoryError/StackOverflowError adalah Error, BUKAN Exception.

    Dengan `catch (e: Exception)` keduanya LOLOS, thread Python mati diam-diam,
    dan user hanya melihat aplikasi menutup sendiri tanpa pesan.
    """

    def test_thread_python_menangkap_throwable(self):
        txt = read(EXEC / "ExecutionEngine.kt")
        i = txt.find("class ChaquopySession")
        assert i > 0
        body = txt[i:i + 2600]
        assert "catch (e: Throwable)" in body, (
            "Thread eksekusi Python wajib menangkap Throwable (bukan hanya Exception) "
            "supaya OOM/StackOverflow tercatat, bukan hilang senyap."
        )


class TestSocketTimeoutDefault:
    """Hard timeout 120 detik dihapus (benar), tapi tanpa penggantinya koneksi
    jaringan yang mati bisa menggantung selamanya."""

    def test_zcode_runner_pasang_default_timeout(self):
        txt = read(PY_DIR / "zcode_runner.py")
        assert "import socket" in txt, "zcode_runner wajib import socket"
        assert "setdefaulttimeout" in txt, (
            "zcode_runner wajib memasang socket.setdefaulttimeout() sebagai jaring "
            "pengaman koneksi jaringan yang menggantung."
        )

    def test_timeout_tidak_menimpa_pilihan_user(self):
        txt = read(PY_DIR / "zcode_runner.py")
        assert "getdefaulttimeout() is None" in txt, (
            "Default timeout hanya boleh dipasang bila user belum menetapkannya sendiri."
        )


class TestComposeDelegasiImport:
    """CI merah 2026-08-12, step 'Build Debug APK' (AboutScreen.kt).

    Sintaks delegasi `var x by remember { mutableStateOf(...) }` memerlukan
    operator extension getValue/setValue yang WAJIB di-import secara eksplisit.
    Menuliskan nama berkualifikasi penuh (androidx.compose.runtime.remember)
    TIDAK menggantikan import tersebut — delegasi `by` tetap gagal resolve.

    Guard ini memindai SELURUH file Compose, bukan hanya file yang pernah rusak,
    supaya kelas kesalahan yang sama tidak terulang di file lain.
    """

    def test_semua_file_by_remember_punya_import_getvalue(self):
        offenders = []
        for f in APP.rglob("*.kt"):
            txt = strip_kt_comments(read(f))
            if not re.search(r"\bvar\s+\w+\s+by\s+", txt):
                continue
            if "import androidx.compose.runtime.getValue" not in txt:
                offenders.append(f"{f.relative_to(ROOT)} (butuh getValue)")
            if "import androidx.compose.runtime.setValue" not in txt:
                offenders.append(f"{f.relative_to(ROOT)} (butuh setValue)")
        assert not offenders, (
            "File memakai `var ... by ...` tanpa import operator delegasi Compose: "
            + "; ".join(offenders)
        )

    def test_val_by_remember_punya_import_getvalue(self):
        offenders = []
        for f in APP.rglob("*.kt"):
            txt = strip_kt_comments(read(f))
            if not re.search(r"\bval\s+\w+\s+by\s+(remember|rememberSaveable)", txt):
                continue
            if "import androidx.compose.runtime.getValue" not in txt:
                offenders.append(str(f.relative_to(ROOT)))
        assert not offenders, (
            "File memakai `val ... by remember` tanpa import getValue: " + "; ".join(offenders)
        )
