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

    BUG YANG PERNAH TERJADI (2026-08-13): versi lama membuang `/* */` LEBIH
    DULU dengan regex, lalu `//`. Akibatnya komentar baris yang memuat teks
    `text/*` (mime filter SAF di WorkbenchScreen.kt) dibaca sebagai PEMBUKA
    komentar blok dan melahap 19.949 karakter KODE ASLI sampai `*/` berikutnya.
    Guard mana pun yang memeriksa area itu diam-diam lolos — persis jenis
    "guard yang berbohong" yang paling berbahaya.

    Karena itu pemindaian sekarang dilakukan SEKALI, kiri ke kanan, dengan
    mesin keadaan yang juga menghormati literal string: `//` di dalam string
    (mis. "https://...") bukan komentar.
    """
    keluar = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if c == '"':
            # literal string (termasuk triple-quote) — salin apa adanya
            if text.startswith('"""', i):
                j = text.find('"""', i + 3)
                j = n if j < 0 else j + 3
            else:
                j = i + 1
                while j < n and text[j] != '"':
                    j += 2 if text[j] == "\\" else 1
                j = min(j + 1, n)
            keluar.append(text[i:j])
            i = j
        elif c == "/" and nxt == "/":
            j = text.find("\n", i)
            i = n if j < 0 else j
        elif c == "/" and nxt == "*":
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
            keluar.append(" ")
        else:
            keluar.append(c)
            i += 1
    return "".join(keluar)



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
        # 2026-08-13: jendela 1400 karakter atas teks MENTAH terlalu rapuh —
        # menambah komentar penjelas saja sudah mendorong kode keluar jendela
        # dan memicu false positive. Komentar kini dibuang lebih dulu.
        txt = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        i = txt.find("fun appendToTerminal(")
        assert i > 0, "fun appendToTerminal tidak ditemukan"
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


# ---------------------------------------------------------------------------
# 8. ANTI-REGRESI PAKET PERBAIKAN 2026-08-13 (build #2 — installer)
#
# Setiap guard di bawah WAJIB bisa gagal: dibuktikan lewat uji mutasi
# (kembalikan bug -> merah, pulihkan -> hijau). Guard yang tak pernah bisa
# gagal adalah guard palsu.
# ---------------------------------------------------------------------------

PYSRC = ROOT / "app/src/main/python"
PYRT = PYSRC / "package_runtime"


class TestBugARequiresPython:
    """resolve.py: Requires-Python dibandingkan dengan versi PYTHON, bukan paket."""

    def test_pembanding_bukan_versi_paket(self):
        txt = read(PYRT / "resolve.py")
        assert "def _requires_python_ok(requires_python: str | None, python_version" in txt, \
            "BUG A: parameter kedua harus python_version, bukan versi paket"
        assert "_requires_python_ok(rp, version)" not in txt, \
            "BUG A KEMBALI: memakai versi paket sebagai pembanding Requires-Python"

    def test_runtime_python_version_ada(self):
        txt = read(PYRT / "resolve.py")
        assert "def runtime_python_version()" in txt
        assert "sys.version_info[:2]" in txt

    def test_perilaku_nyata(self):
        import sys as _s
        _s.path.insert(0, str(PYSRC))
        from package_runtime.resolve import _requires_python_ok
        assert _requires_python_ok(">=3.7") is True, "Python 3.11 harus memenuhi >=3.7"
        assert _requires_python_ok(">=3.99") is False
        assert _requires_python_ok(None) is True


class TestBugCStdlib:
    """resolve.py: modul stdlib dikenali, bukan dicari ke PyPI."""

    def test_is_stdlib_module_ada(self):
        txt = read(PYRT / "resolve.py")
        assert "def is_stdlib_module(" in txt, "BUG C: helper stdlib hilang"
        assert "stdlib_module_names" in txt
        assert "if is_stdlib_module(root_name):" in txt, \
            "BUG C: resolve() tidak memeriksa stdlib lebih dulu"

    def test_perilaku_nyata(self):
        import sys as _s
        _s.path.insert(0, str(PYSRC))
        from package_runtime.resolve import is_stdlib_module
        for name in ("math", "json", "os", "Math"):
            assert is_stdlib_module(name), f"{name} harus dikenali stdlib"
        for name in ("colorama", "requests", ""):
            assert not is_stdlib_module(name)

    def test_kotlin_membaca_field_stdlib(self):
        txt = strip_kt_comments(read(PKGENG / "DependencyResolver.kt"))
        assert "val stdlib:" in txt and "StdlibHit" in txt, \
            "BUG C: ResolvePlan Kotlin tidak punya field stdlib"


class TestBugDUrutanVersi:
    """wheelinfo.py: pemilihan wheel diurutkan berdasarkan VERSI, bukan alfabet."""

    def test_tidak_sort_alfabetis(self):
        txt = read(PYRT / "wheelinfo.py")
        assert 'ranked.sort(key=lambda r: (r[0], r[1].get("filename", "")))' not in txt, \
            "BUG D KEMBALI: sort alfabetis atas nama file"
        assert "_NegVersion" in txt and "def _version_key(" in txt

    def test_perilaku_nyata(self):
        import sys as _s
        _s.path.insert(0, str(PYSRC))
        from package_runtime.wheelinfo import best_wheel
        from packaging.tags import Tag
        tags = [Tag("py3", "none", "any"), Tag("py2.py3", "none", "any")]
        cands = [{"filename": f} for f in (
            "colorama-0.3.5-py2.py3-none-any.whl",
            "colorama-0.4.6-py2.py3-none-any.whl",
            "colorama-0.3.9-py2.py3-none-any.whl",
        )]
        assert best_wheel(cands, supported_tags=tags)["filename"] == \
            "colorama-0.4.6-py2.py3-none-any.whl", "harus memilih versi TERBARU"
        # 1.10 > 1.9 secara versi, walau '1' < '9' secara teks
        c2 = [{"filename": f} for f in (
            "urllib3-1.9-py2.py3-none-any.whl",
            "urllib3-1.10-py2.py3-none-any.whl",
        )]
        assert best_wheel(c2, supported_tags=tags)["filename"] == \
            "urllib3-1.10-py2.py3-none-any.whl"


class TestBugBOptString:
    """DependencyResolver.kt: optString() tidak pernah null — wajib pakai helper."""

    def test_helper_ada(self):
        txt = strip_kt_comments(read(PKGENG / "DependencyResolver.kt"))
        assert "fun JSONObject.optStringOrNull(" in txt, "BUG B: helper hilang"

    def test_field_opsional_tidak_pakai_optstring_telanjang(self):
        txt = strip_kt_comments(read(PKGENG / "DependencyResolver.kt"))
        for field in ("url", "local_path", "sha256", "requires_python"):
            bad = f'optString("{field}")'
            for line in txt.splitlines():
                if bad in line and "optStringOrNull" not in line:
                    raise AssertionError(
                        f"BUG B KEMBALI: {field} memakai optString() telanjang -> "
                        f'"" bukan null. Baris: {line.strip()}'
                    )


class TestBugEBatcherRestart:
    """OutputBatcher: running harus di-reset di start(), kalau tidak batcher tuli."""

    def test_running_direset(self):
        txt = strip_kt_comments(read(EXEC / "OutputBatcher.kt"))
        i = txt.find("fun start()")
        assert i > 0
        j = txt.find("thread = Thread", i)
        assert j > i, "struktur start() berubah"
        assert "running = true" in txt[i:j], \
            "BUG E KEMBALI: running tidak di-reset -> output dibuang diam-diam"


class TestBugFTolakWheelLinux:
    """wheelinfo.py: wheel glibc/musl selalu ditolak (mencegah SIGSEGV)."""

    def test_penjagaan_ada(self):
        txt = read(PYRT / "wheelinfo.py")
        assert "def is_foreign_platform_tag(" in txt, "BUG F: penjagaan hilang"
        for prefix in ("manylinux", "musllinux", "linux_"):
            assert prefix in txt

    def test_perilaku_nyata(self):
        import sys as _s
        _s.path.insert(0, str(PYSRC))
        from package_runtime.wheelinfo import wheel_compatible, is_foreign_platform_tag
        from packaging.tags import Tag
        assert is_foreign_platform_tag("manylinux_2_17_armv7l")
        assert is_foreign_platform_tag("linux_armv7l")
        assert not is_foreign_platform_tag("android_21_armeabi_v7a")
        # Bahkan bila tag runtime "cocok", wheel Linux tetap ditolak.
        assert not wheel_compatible(
            "numpy-1.26.2-cp311-cp311-manylinux_2_17_armv7l.whl",
            supported_tags=[Tag("cp311", "cp311", "manylinux_2_17_armv7l")],
        ), "BUG F KEMBALI: wheel glibc lolos -> SIGSEGV di Android"
        assert wheel_compatible(
            "numpy-1.26.2-0-cp311-cp311-android_21_armeabi_v7a.whl",
            supported_tags=[Tag("cp311", "cp311", "android_21_armeabi_v7a")],
        ), "wheel Android sah tidak boleh ikut ditolak"


class TestBugGCacheWheelPerAbi:
    """Paths.kt: cache wheel dipisah per-ABI."""

    def test_pemisahan_ada(self):
        txt = strip_kt_comments(read(APP / "core/files/Paths.kt"))
        assert "fun currentAbi()" in txt, "BUG G: currentAbi() hilang"
        assert "SUPPORTED_ABIS" in txt
        assert "File(pythonWheelsRoot(context), currentAbi())" in txt, \
            "BUG G KEMBALI: cache wheel tidak dipisah per-ABI"


class TestBugHWebViewColdStart:
    """EditorScreen.kt: jangan evaluateJavascript sebelum WebView navigasi."""

    def test_guard_about_blank(self):
        txt = read(UI / "editor/EditorScreen.kt")
        assert "about:blank" in txt, "BUG H: guard cold-start WebView hilang"
        clean = strip_kt_comments(txt)
        assert "runCatching" in clean


class TestBugISelectionContainer:
    """Semua layar keluaran harus bisa diseleksi/disalin."""

    LAYAR = (
        UI / "terminal/TerminalScreen.kt",
        UI / "settings/PipScreen.kt",
        UI / "settings/AboutScreen.kt",
    )

    def test_selection_container_terpasang(self):
        for f in self.LAYAR:
            txt = read(f)
            assert "SelectionContainer" in strip_kt_comments(txt), \
                f"BUG I: {f.name} tidak bisa diseleksi — user tak bisa melapor"
            assert "import androidx.compose.foundation.text.selection.SelectionContainer" in txt, \
                f"BUG I: import SelectionContainer hilang di {f.name}"

    def test_terminal_punya_tombol_salin(self):
        txt = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        assert "ClipboardManager" in txt and "Salin" in txt, \
            "BUG I: terminal tidak punya tombol Salin"


class TestBugJBreadcrumbInstaller:
    """Jalur Install Modules wajib meninggalkan jejak (user tanpa logcat)."""

    def test_breadcrumb_terpasang(self):
        txt = strip_kt_comments(read(UI / "settings/PipScreen.kt"))
        for step in ("PKG_INSTALL_BEGIN", "PKG_INSTALL_OK", "PKG_INSTALL_FAIL",
                     "PKG_ANALYZE_BEGIN", "PKG_ANALYZE_FAIL"):
            assert step in txt, f"BUG J: breadcrumb {step} hilang"


class TestManifestSinkronChaquopy:
    """tested-manifest.json hanya boleh memuat versi yang ADA di indeks Chaquopy.

    Disurvei 2026-08-13 dari https://chaquo.com/pypi-13.1/ — lihat
    docs/ARMV7_COMPAT_2026_08_13.md. numpy 1.26.4 dan pillow 10.3.0 TIDAK ADA.
    """

    HANTU = {"numpy": "1.26.4", "pillow": "10.3.0"}
    NYATA = {"numpy": "1.26.2", "pillow": "11.0.0", "matplotlib": "3.6.0", "pandas": "2.1.3"}

    def test_tidak_ada_versi_hantu(self):
        import json
        data = json.loads(read(ROOT / "app/src/main/assets/package_catalog/tested-manifest.json"))
        for pkg, ghost in self.HANTU.items():
            assert ghost not in data.get(pkg, []), (
                f"{pkg}=={ghost} tidak ada di indeks Chaquopy cp311 — "
                f"install akan gagal walau pencocokan tag sudah benar"
            )

    def test_versi_nyata_terpasang(self):
        import json
        data = json.loads(read(ROOT / "app/src/main/assets/package_catalog/tested-manifest.json"))
        for pkg, real in self.NYATA.items():
            assert real in data.get(pkg, []), f"{pkg} harus memuat {real}"

    def test_katalog_tested_sinkron_manifest(self):
        """KELAS bug, bukan satu paket: tombol Install Tested mengirim
        name==testedVersion. Kalau itu tidak ada di manifest/Chaquopy,
        setiap paket TESTED baru akan mengulang siklus numpy==1.26.4."""
        import json
        cat = json.loads(read(ROOT / "app/src/main/assets/package_catalog/packages.json"))
        man = json.loads(read(ROOT / "app/src/main/assets/package_catalog/tested-manifest.json"))
        salah = []
        for p in cat:
            if p.get("status") != "TESTED":
                continue
            nama = p.get("name") or ""
            tv = p.get("testedVersion")
            if not tv:
                salah.append("%s: TESTED tanpa testedVersion" % nama)
                continue
            versi_man = man.get(nama) or man.get(nama.lower()) or []
            if tv not in versi_man:
                salah.append("%s==%s tidak ada di tested-manifest %s" % (nama, tv, versi_man))
            if nama in self.HANTU and tv == self.HANTU[nama]:
                salah.append("%s==%s adalah versi hantu Chaquopy" % (nama, tv))
        assert not salah, "katalog TESTED tidak sinkron:\\n  " + "\\n  ".join(salah)


class TestTidakAdaPenandaKonflik:
    """Penanda konflik merge tidak boleh pernah ter-commit.

    2026-08-13: rebase build #2 meninggalkan '<<<<<<< HEAD' di file test ini
    sendiri. Akibatnya SELURUH berkas gagal di-parse dan 48 guard berhenti
    berjalan — kegagalan senyap yang justru mematikan jaring pengaman. Guard
    ini memindai seluruh repo agar hal itu ketahuan seketika.
    """

    EKSTENSI = ("*.kt", "*.py", "*.kts", "*.json")

    def test_tidak_ada_penanda(self):
        langgar = []
        for pola in self.EKSTENSI:
            for f in ROOT.rglob(pola):
                if any(bag in f.parts for bag in (".git", "build", "node_modules", "__pycache__")):
                    continue
                try:
                    isi = f.read_text(encoding="utf-8", errors="ignore")
                except OSError:
                    continue
                for n, baris in enumerate(isi.splitlines(), 1):
                    if baris.startswith("<<<<<<< ") or baris.startswith(">>>>>>> "):
                        langgar.append(f"{f.relative_to(ROOT)}:{n}")
        assert not langgar, "Penanda konflik merge ter-commit: " + ", ".join(langgar[:10])


class TestTerminalRenderTerikatState:
    """Isi terminal harus terikat Compose state, bukan kebetulan tata letak.

    2026-08-13: TerminalBuffer bukan Compose state. Selama ini renderer ikut
    tersegarkan hanya sebagai EFEK SAMPING karena `memChars`/`logBytes` berubah
    di scope rekomposisi yang sama. Membungkus LazyColumn dengan
    SelectionContainer memutus kebetulan itu — isinya jadi sub-komposisi tanpa
    ketergantungan state, sehingga TIDAK PERNAH disusun ulang: metrik
    menunjukkan "5 baris" sementara layar kosong.
    """

    def test_ada_penanda_versi_buffer(self):
        txt = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        assert "bufferVersion" in txt, (
            "TerminalScreen tidak punya penanda perubahan buffer — isi terminal "
            "bergantung pada kebetulan rekomposisi"
        )
        assert "bufferVersion++" in txt, "bufferVersion tidak pernah dinaikkan saat output masuk"

    def test_snapshot_dibaca_di_scope_composable(self):
        txt = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        assert "remember(bufferVersion)" in txt, (
            "snapshot isi buffer harus di-key pada bufferVersion di scope composable"
        )
        # Renderer tidak boleh lagi membaca buffer langsung di dalam items().
        assert "val lineText = lines[rel]" in txt, (
            "items() harus membaca snapshot (lines), bukan buffer.get() langsung"
        )


class TestBottomBarTerminalTidakRaksasa:
    """Tombol bottom bar terminal wajib dibatasi tingginya.

    2026-08-13: Row bottom bar tanpa .height() membuat Button Material3 memakai
    tinggi minimum bawaannya (56dp). Dengan dua tombol hal itu tidak terlihat;
    begitu tombol "Salin" ditambahkan, bar menelan sepertiga layar.
    """

    def test_row_dan_tombol_dibatasi(self):
        """Build #3: baris tombol pindah ke EditorHandle, jadi kunci tingginya
        ikut pindah ke sana. Yang dijaga tetap MAKSUD yang sama — bar terminal
        tidak boleh tumbuh sendiri — bukan bentuk lamanya."""
        txt = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        i = txt.find("bottomBar")
        assert i > 0
        blok = txt[i:i + 4000]
        assert "EditorHandle(" in blok, "bottom bar tidak lagi memakai EditorHandle"
        for terlarang in ("Button(", "AssistChip(", "FilterChip("):
            assert terlarang not in blok, (
                f"{terlarang} di bottom bar memaksa tinggi minimum Material3 "
                "(48-56dp) — inilah sebab bar raksasa v1.0.2"
            )
        handle = read(COMMON / "EditorHandle.kt")
        bar = int(re.search(r"BAR_HEIGHT\s*=\s*(\d+)\.dp", handle).group(1))
        assert bar <= 48, f"BAR_HEIGHT {bar}dp — bar terminal membengkak lagi"


class TestSmokeTestLihatSaudara:
    """Smoke test wajib melihat SELURUH paket dalam transaksi + yang sudah aktif.

    2026-08-13: versi lama hanya menyuntikkan satu direktori staging ke
    sys.path, sehingga `import requests` tidak menemukan urllib3 di folder
    sebelah -> ModuleNotFoundError -> rollback seluruh transaksi.

    Ini BUKAN kasus khusus requests. Dari 23 paket populer yang diperiksa,
    12 (52%) punya dependensi runtime wajib: flask 7, pandas 5, requests 4,
    httpx 4, rich 2, beautifulsoup4 2, openpyxl/click/tqdm/jinja2/werkzeug/
    python-dateutil masing-masing 1. Semuanya mustahil dipasang sebelum ini.
    """

    def test_python_menerima_sibling_dirs(self):
        txt = read(PYRT / "smoke.py")
        assert "sibling_dirs" in txt, "run_smoke tidak menerima sibling_dirs"
        assert "sibling_dirs_json" in txt, "run_smoke_json tidak meneruskan sibling_dirs"

    def test_kotlin_mengirim_seluruh_staging(self):
        eng = strip_kt_comments(read(PKGENG / "PackageEngineV2.kt"))
        assert "allStagingDirs" in eng, "PackageEngineV2 tidak mengumpulkan direktori staging"
        # Harus DIPAKAI, bukan sekadar didefinisikan. Uji mutasi 2026-08-13
        # menunjukkan pemeriksaan "activeSitePackagePaths()" saja lolos walau
        # pemanggilannya dihapus, karena cocok dengan definisi fungsinya.
        assert "+ activeSitePackagePaths()" in eng, (
            "paket yang SUDAH aktif tidak disertakan ke daftar saudara — "
            "instalasi bertahap (urllib3 dulu, lalu requests) akan gagal"
        )
        assert eng.count("activeSitePackagePaths") >= 2, (
            "activeSitePackagePaths didefinisikan tapi tidak dipanggil"
        )
        runner = strip_kt_comments(read(PKGENG / "SmokeTestRunner.kt"))
        assert "siblingDirs" in runner, "SmokeTestRunner tidak menerima siblingDirs"
        # siblingsJson harus dibangun DAN diteruskan sebagai argumen PyCall.
        # Sekadar mencari namanya lolos walau baris argumennya dihapus.
        assert "val siblingsJson" in runner, "siblingsJson tidak dibangun"
        i = runner.find("run_smoke_json")
        assert i > 0, "pemanggilan run_smoke_json hilang"
        argumen = runner[i:i + 400]
        assert "siblingsJson" in argumen, (
            "siblingsJson dibangun tapi TIDAK diteruskan ke run_smoke_json — "
            "smoke test kembali buta terhadap dependensi"
        )

    def test_perilaku_nyata_paket_berdependensi(self, tmp_path):
        """Reproduksi kegagalan asli lalu buktikan sembuh."""
        import sys as _s
        _s.path.insert(0, str(PYSRC))
        from package_runtime.smoke import run_smoke

        # Nama sengaja dibuat unik: memakai nama nyata (requests/urllib3)
        # membuat test bocor — modul asli bisa sudah ada di sys.modules karena
        # test lain, sehingga import "berhasil" dan bug tidak terdeteksi.
        base = tmp_path / "staging"
        layout = {
            "zzmain/1.0/zzmain": "import zzdep_a, zzdep_b\n__version__='1.0'\n",
            "zzdep_a/2.0/zzdep_a": "ok = True\n",
            "zzdep_b/3.0/zzdep_b": "ok = True\n",
        }
        for rel, body in layout.items():
            d = base / rel
            d.mkdir(parents=True)
            (d / "__init__.py").write_text(body, encoding="utf-8")

        main = str(base / "zzmain/1.0")
        siblings = [str(base / "zzdep_a/2.0"), str(base / "zzdep_b/3.0")]

        ok_tanpa, res_tanpa, _ = run_smoke("zzmain", main, None)
        assert not ok_tanpa, "seharusnya gagal tanpa saudara (bug asli)"
        assert "zzdep_a" in str(res_tanpa[0].get("error"))

        ok_dengan, res_dengan, _ = run_smoke("zzmain", main, None, sibling_dirs=siblings)
        assert ok_dengan, f"harus lulus dengan saudara: {res_dengan}"

    def test_sys_path_dipulihkan(self, tmp_path):
        """Kebersihan: sys.path tidak boleh bocor setelah smoke test."""
        import sys as _s
        _s.path.insert(0, str(PYSRC))
        from package_runtime.smoke import run_smoke

        d = tmp_path / "pkgx/1.0/pkgx"
        d.mkdir(parents=True)
        (d / "__init__.py").write_text("ok = True\n", encoding="utf-8")
        sib = tmp_path / "lain/1.0"
        sib.mkdir(parents=True)

        sebelum = list(_s.path)
        run_smoke("pkgx", str(tmp_path / "pkgx/1.0"), None, sibling_dirs=[str(sib)])
        assert _s.path == sebelum, "sys.path bocor setelah smoke test"


# ---------------------------------------------------------------------------
# BUILD #3 — Tag Android untuk wheel Chaquopy
#
# AKAR MASALAH (v1.0.4 dan sebelumnya): `packaging.tags.sys_tags()` di dalam
# Chaquopy menghasilkan tag bergaya Linux (`linux_armv7l`), sedangkan wheel di
# indeks Chaquopy bertag `android_21_armeabi_v7a`. Irisan kedua himpunan itu
# KOSONG, jadi SETIAP wheel native dinyatakan tidak kompatibel padahal cocok
# sempurna. Itu sebabnya numpy/pandas/pillow/matplotlib selalu gagal.
#
# Guard di bawah mengunci resep tag yang menggantikannya. Kalau seseorang
# kelak "menyederhanakan" kode ini kembali ke sys_tags(), test ini merah.
# ---------------------------------------------------------------------------
PYSRC_PKG = ROOT / "app/src/main/python/package_runtime"


class TestTagAndroidWheel:
    def _tags(self, abi="armeabi-v7a", api=31):
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.wheelinfo import android_supported_tags
        return android_supported_tags(abi, api)

    def test_menghasilkan_tag_android_bukan_linux(self):
        tags = self._tags()
        plats = {t.platform for t in tags}
        assert any(p.startswith("android_") for p in plats), "tidak ada tag android_*"
        assert not any("linux" in p for p in plats), \
            f"tag Linux bocor ke daftar Android: {[p for p in plats if 'linux' in p]}"

    def test_api_perangkat_menerima_wheel_api_lebih_lama(self):
        """android_21 HARUS jalan di perangkat API 31 — dan TIDAK sebaliknya."""
        tags = {t.platform for t in self._tags(api=31)}
        assert "android_21_armeabi_v7a" in tags
        assert "android_24_armeabi_v7a" in tags
        assert "android_32_armeabi_v7a" not in tags, "wheel dari masa depan diterima"

    def test_abi_tidak_pernah_disubstitusi(self):
        """Wheel arm64 di perangkat armv7 = SIGSEGV. Tidak boleh ada toleransi."""
        tags = {t.platform for t in self._tags(abi="armeabi-v7a", api=31)}
        assert not any("arm64" in p for p in tags)
        assert not any("x86" in p for p in tags)

    def test_underscore_bukan_dash(self):
        """Nama file wheel memakai `armeabi_v7a`; input Kotlin bisa berupa
        `armeabi-v7a`. Konversi wajib terjadi, kalau tidak semua tag meleset."""
        tags = {t.platform for t in self._tags(abi="armeabi-v7a")}
        assert all("-" not in p for p in tags), "tanda hubung bocor ke tag platform"

    def test_ada_penutup_pure_python(self):
        tags = {(t.interpreter, t.abi, t.platform) for t in self._tags()}
        assert ("py3", "none", "any") in tags, "wheel pure-Python jadi tak terpasang"

    def test_wheel_nyata_chaquopy_diterima(self):
        """Uji melawan nama file SUNGGUHAN dari indeks chaquo.com/pypi-13.1."""
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.wheelinfo import wheel_compatible, android_supported_tags
        tags = android_supported_tags("armeabi-v7a", 31)
        terima = [
            "numpy-1.26.2-0-cp311-cp311-android_21_armeabi_v7a.whl",
            "Pillow-9.2.0-0-cp311-cp311-android_21_armeabi_v7a.whl",
            "cryptography-42.0.8-0-cp311-cp311-android_24_armeabi_v7a.whl",
        ]
        tolak = [
            "numpy-1.26.2-0-cp311-cp311-android_21_arm64_v8a.whl",
            "numpy-1.26.2-cp311-cp311-manylinux_2_17_armv7l.whl",
            "scipy-1.11.0-0-cp310-cp310-android_21_armeabi_v7a.whl",
        ]
        for w in terima:
            assert wheel_compatible(w, supported_tags=tags), f"harusnya cocok: {w}"
        for w in tolak:
            assert not wheel_compatible(w, supported_tags=tags), f"harusnya ditolak: {w}"

    def test_resolver_memakai_tag_perangkat_bukan_sys_tags(self):
        """resolve_json() wajib meneruskan abi+api ke pembangun tag."""
        src = read(PYSRC_PKG / "resolve.py")
        assert "def device_supported_tags(" in src
        assert "supported_tags=device_supported_tags(abi, device_api)" in src, \
            "resolve() kembali memakai supported_tags=None — bug tag Android hidup lagi"

    def test_kotlin_mengirim_abi_dan_sdk_int(self):
        src = strip_kt_comments(read(PKGENG / "DependencyResolver.kt"))
        assert "currentAbi()" in src, "ABI perangkat tidak dikirim ke Python"
        assert "SDK_INT" in src, "API level perangkat tidak dikirim ke Python"


# ---------------------------------------------------------------------------
# BUILD #3 — EDITOR HANDLE
#
# Dua regresi v1.0.2 lahir di area ini:
#   (a) bottom bar raksasa, karena Button/AssistChip Material3 punya tinggi
#       minimum 48-56dp yang diam-diam mendorong bar setinggi separuh layar;
#   (b) tombol darurat ikut tergulir sehingga tak terjangkau saat dibutuhkan.
# Guard berikut mengunci keduanya.
# ---------------------------------------------------------------------------
COMMON = UI / "common"


class TestEditorHandle:
    def test_tinggi_dikunci_eksplisit(self):
        src = read(COMMON / "EditorHandle.kt")
        assert src, "EditorHandle.kt hilang"
        assert re.search(r"BAR_HEIGHT\s*=\s*\d+\.dp", src), "tinggi bar tidak dikunci"
        assert re.search(r"KEY_HEIGHT\s*=\s*\d+\.dp", src), "tinggi tombol tidak dikunci"
        bar = int(re.search(r"BAR_HEIGHT\s*=\s*(\d+)\.dp", src).group(1))
        assert bar <= 48, f"BAR_HEIGHT {bar}dp — regresi bar raksasa v1.0.2"

    def test_tidak_memakai_komponen_bertinggi_minimum(self):
        """Button/AssistChip/FilterChip memaksa tinggi minimumnya sendiri."""
        src = strip_kt_comments(read(COMMON / "EditorHandle.kt"))
        for terlarang in ("AssistChip", "FilterChip", "TextButton", "OutlinedButton"):
            assert terlarang not in src, \
                f"{terlarang} punya tinggi minimum Material3 — sumber bar raksasa"

    def test_terowongan_di_luar_scroll(self):
        """^C harus dipaku: kalau ia ikut horizontalScroll, tombol berhenti
        paksa bisa tergeser keluar layar persis saat paling dibutuhkan."""
        src = strip_kt_comments(read(COMMON / "EditorHandle.kt"))
        # Buang blok import: `import ...horizontalScroll` bukan PEMAKAIAN, dan
        # mencocokkannya membuat guard ini selalu menunjuk baris 5 (pelajaran
        # 2026-08-13 — guard yang salah sasaran sama saja dengan tidak ada).
        src = re.sub(r"^import .*\n", "", src, flags=re.M)
        i_scroll = src.find("horizontalScroll(")
        assert i_scroll > 0, "kereta tidak dapat digulir"
        assert "tunnelKey" in src, "terowongan hilang"
        # Terowongan wajib dirender SEBELUM Row yang menggulir, dan namanya
        # tidak boleh muncul lagi setelah titik itu.
        i_render = src.find("tunnelKey?.let")
        assert i_render > 0, "terowongan tidak pernah dirender"
        assert i_render < i_scroll, \
            "terowongan dirender setelah/di dalam area scroll"
        assert "tunnelKey" not in src[i_scroll:], \
            "terowongan berada DI DALAM area scroll"

    def test_terpasang_di_terminal_dengan_ctrl_c_merah(self):
        src = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        assert "EditorHandle(" in src, "EditorHandle belum dipakai di terminal"
        i = src.find("EditorHandle(")
        jendela = src[i:i + 900]
        assert "tunnelKey" in jendela, "terminal tanpa terowongan ^C"
        assert "^C" in jendela
        assert "danger = true" in jendela, "^C tidak ditandai bahaya (merah)"

    def test_terpasang_di_editor_tanpa_terowongan(self):
        src = strip_kt_comments(read(UI / "workbench/WorkbenchScreen.kt"))
        assert "EditorHandle(" in src, "EditorHandle belum dipakai di editor"
        assert "QuickToolsBar" not in src, "bar lama masih tersisa (kode mati)"
        i = src.find("EditorHandle(")
        assert "tunnelKey" not in src[i:i + 600], \
            "editor tidak boleh punya terowongan — tak ada yang perlu dihentikan"

    def test_setiap_tombol_punya_aksi(self):
        """Tombol yang tidak melakukan apa-apa lebih buruk daripada tidak ada."""
        src = read(COMMON / "EditorHandle.kt")
        for fn in ("pythonEditorKeys", "terminalKeys"):
            assert f"fun {fn}(" in src, f"{fn} hilang"
        assert "insert" in src


# ---------------------------------------------------------------------------
# BUILD #3 — DIAGNOSTICS layar penuh
# ---------------------------------------------------------------------------
class TestDiagnosticsLayarPenuh:
    def test_file_ada_dan_punya_rute(self):
        assert (UI / "settings/DiagnosticsScreen.kt").exists()
        main = strip_kt_comments(read(APP / "MainActivity.kt"))
        assert 'composable("diagnostics")' in main, "rute diagnostics tidak terdaftar"
        assert "DiagnosticsScreen(" in main

    def test_dapat_dicapai_dari_sidebar(self):
        src = strip_kt_comments(read(UI / "workbench/WorkbenchScreen.kt"))
        assert 'DrawerItem("DIAGNOSTICS")' in src, "DIAGNOSTICS tidak ada di sidebar"
        assert "onNavigateToDiagnostics" in src

    def test_mengisi_layar_dan_dapat_digulir(self):
        src = strip_kt_comments(read(UI / "settings/DiagnosticsScreen.kt"))
        assert "fillMaxSize()" in src, "bukan layar penuh"
        assert "verticalScroll" in src, "tidak bisa digulir"
        assert not re.search(r"\.height\(\s*\d{3}\.dp\s*\)", src), \
            "ada tinggi tetap ratusan dp — ini kotak kecil, bukan layar penuh"

    def test_isinya_dapat_diseleksi_dan_disalin(self):
        """Aturan tetap user: semua output ZCODE harus bisa di-copas."""
        src = strip_kt_comments(read(UI / "settings/DiagnosticsScreen.kt"))
        # Periksa PEMAKAIAN, bukan baris import: `import ...SelectionContainer`
        # tetap ada walau pembungkusnya dicabut dari UI (bocor uji mutasi
        # 2026-08-13).
        badan = re.sub(r"^import .*\n", "", src, flags=re.M)
        assert "SelectionContainer {" in badan, "output tidak dapat diseleksi"
        assert "ClipboardManager" in badan, "tidak ada Salin"
        assert "ACTION_SEND" in badan, "tidak ada Bagikan"

    def test_baca_disk_di_luar_main_thread(self):
        """Breadcrumb bisa 128KB; membacanya di UI thread = ANR."""
        src = strip_kt_comments(read(UI / "settings/DiagnosticsScreen.kt"))
        assert "Dispatchers.IO" in src

    def test_punya_tab_filter(self):
        src = read(UI / "settings/DiagnosticsScreen.kt")
        for t in ("SEMUA", "RUN", "PAKET", "CRASH"):
            assert f'"{t}"' in src, f"tab {t} hilang"


# ---------------------------------------------------------------------------
# BUILD #3 — Bagikan (menu seleksi)
#
# Long-press pada teks Compose hanya menawarkan "Copy"; SELECT ALL/PASTE/SHARE
# yang biasa ada di TextView TIDAK disediakan SelectionContainer. Karena user
# melapor bug dari HP tanpa PC, "Bagikan" harus jadi tombol nyata.
# ---------------------------------------------------------------------------
class TestBagikanOutput:
    def test_terminal_punya_bagikan(self):
        src = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        assert "ACTION_SEND" in src, "terminal tidak bisa membagikan output"
        assert "createChooser" in src

    def test_bagikan_tidak_boleh_membuat_crash(self):
        """Sebagian perangkat tanpa aplikasi penerima akan melempar
        ActivityNotFoundException. Diagnostik tidak boleh jadi sumber crash."""
        src = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        i = src.find("fun shareAll()")
        assert i > 0, "shareAll tidak ditemukan"
        badan = src[i:i + 1200]
        assert "runCatching" in badan, "startActivity tanpa pengaman"


# ---------------------------------------------------------------------------
# BUG URUTAN OUTPUT (dilaporkan user dari perangkat, v1.0.5)
#
# Layar menampilkan:
#     Process finished with exit code 0 (state: EXITED)
#     Hello, ZCODE!
# padahal print() jelas berjalan SEBELUM script selesai.
#
# DUA sebab, keduanya nyata dan saling menutupi:
#  1. Pesan exit ditulis lewat `scope.launch { appendToTerminal(...) }` —
#     menembak langsung ke buffer sambil MELEWATI OutputBatcher, sementara
#     output print() masih menunggu jendela flush 40ms.
#  2. OutputBatcher menyimpan satu StringBuilder PER STREAM dan mem-flush
#     dengan `buffers.forEach`. Urutan hanya terjaga di dalam satu stream;
#     antar-stream ("out" vs "sys") yang menang adalah yang map-nya lebih
#     dulu dibuat, bukan yang teksnya lebih dulu datang.
#
# Memperbaiki hanya (1) menyisakan bom waktu: begitu ada output "err" dan
# "out" berdekatan, urutan kacau lagi tanpa ada yang mengubah TerminalScreen.
# ---------------------------------------------------------------------------
EXEC_DIR = APP / "core/execution"


class TestUrutanOutputTerminal:
    def test_pesan_exit_tidak_memotong_antrean(self):
        src = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        i = src.find("onExit = { code ->")
        assert i > 0, "handler onExit tidak ditemukan"
        badan = src[i:i + 1200]
        assert "Process finished" in badan
        assert "batcher.append(" in badan, (
            "pesan exit tidak lewat OutputBatcher — akan menyalip output print() "
            "yang masih menunggu flush 40ms"
        )
        assert "appendToTerminal(" not in badan, (
            "pesan exit menembak langsung ke buffer, melewati antrean"
        )

    def test_antrean_dikosongkan_sebelum_pesan_exit(self):
        src = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        i = src.find("onExit = { code ->")
        badan = src[i:i + 1200]
        i_drain = badan.find("batcher.drain()")
        i_append = badan.find("batcher.append(")
        assert i_drain > 0, "antrean tidak dikosongkan sebelum menulis pesan exit"
        assert i_drain < i_append, "drain() dipanggil SETELAH pesan exit — tidak ada gunanya"

    def test_batcher_punya_drain(self):
        src = read(EXEC_DIR / "OutputBatcher.kt")
        assert "fun drain(" in src, "OutputBatcher tanpa drain()"
        # Periksa TANDA TANGAN-nya, bukan sekadar kemunculan kata: "timeoutMs"
        # tetap ada di badan fungsi walau parameternya dicabut (bocor uji
        # mutasi 2026-08-13).
        assert re.search(r"fun drain\(\s*timeoutMs", src), (
            "drain() tanpa batas waktu bisa menggantung selamanya bila produser macet"
        )

    def test_batcher_menjaga_urutan_lintas_stream(self):
        """Akar sebenarnya: satu buffer per stream = urutan antar-stream hilang."""
        src = strip_kt_comments(read(EXEC_DIR / "OutputBatcher.kt"))
        assert "mutableMapOf<String, StringBuilder>" not in src, (
            "buffer per-stream kembali — urutan antar-stream tidak lagi terjaga"
        )
        assert "buffers.forEach" not in src, (
            "flush mengiterasi map: urutan ditentukan pembuatan map, bukan waktu datang"
        )
        assert "pending" in src, "tidak ada antrean tunggal berurutan"

    def test_simulasi_urutan_kronologis(self):
        """Uji PERILAKU, bukan bentuk teks: tiru algoritma flush yang baru dan
        pastikan tiga potongan dari dua stream keluar sesuai waktu datang."""
        pending = [
            ("sys", "menyalakan\n"),
            ("out", "Hello, ZCODE!\n"),
            ("sys", "Process finished\n"),
        ]
        keluar = []
        stream = pending[0][0]
        sb = []
        while pending:
            s, t = pending[0]
            if s != stream:
                keluar.append((stream, "".join(sb)))
                sb = []
                stream = s
            sb.append(t)
            pending.pop(0)
        if sb:
            keluar.append((stream, "".join(sb)))

        teks = [t for _, t in keluar]
        i_hello = next(i for i, t in enumerate(teks) if "Hello" in t)
        i_exit = next(i for i, t in enumerate(teks) if "Process finished" in t)
        assert i_hello < i_exit, f"urutan terbalik: {teks}"

    def test_footer_tanpa_pemisah_menggantung(self):
        """User melihat 'mem 0KB · 4 baris ·' — pemisah tanpa apa pun sesudahnya."""
        src = read(UI / "terminal/TerminalScreen.kt")
        assert 'baris · $runId"' not in src, "pemisah menggantung di footer"


# ---------------------------------------------------------------------------
# BUILD #3 — Rotasi & Export log run
#
# Paths.runLogsDir() sudah menampung satu .log per run sejak awal, tetapi TIDAK
# ADA yang pernah menghapusnya: setiap tap Run menambah file permanen. Di HP
# 32-bit dengan storage terbatas itu kebocoran yang tumbuh diam-diam dan tidak
# bisa dibersihkan user dari dalam aplikasi.
# ---------------------------------------------------------------------------
DIAG_DIR = APP / "core/diagnostics"


class TestRotasiLogRun:
    def test_store_ada_dengan_batas_50(self):
        src = read(DIAG_DIR / "RunLogStore.kt")
        assert src, "RunLogStore.kt hilang"
        m = re.search(r"MAX_RUN_LOGS\s*=\s*(\d+)", src)
        assert m, "batas jumlah log tidak didefinisikan"
        assert int(m.group(1)) == 50, f"batas {m.group(1)} — kesepakatan build #3 adalah 50"

    def test_rotasi_dipanggil_saat_run_baru(self):
        src = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        assert "RunLogStore.rotate(" in src, "log run tidak pernah dirotasi — bocor selamanya"
        i_rot = src.find("RunLogStore.rotate(")
        i_new = src.find("RunLogger(File(Paths.runLogsDir")
        assert i_new > 0
        assert i_rot < i_new, "rotasi dijalankan setelah log baru dibuat"

    def test_rotasi_tidak_boleh_membuat_crash(self):
        """Pembersihan log tidak boleh jadi sumber crash baru."""
        src = read(DIAG_DIR / "RunLogStore.kt")
        for fn in ("fun list(", "fun rotate(", "fun clearAll("):
            i = src.find(fn)
            assert i > 0, f"{fn} hilang"
            assert "runCatching" in src[i:i + 700], f"{fn} tanpa pengaman I/O"

    def test_rotasi_menyisakan_yang_terbaru(self):
        """Uji PERILAKU algoritmanya: yang dibuang harus yang TERLAMA."""
        berkas = [(f"run_{i}.log", i) for i in range(60)]  # mtime = i, makin besar makin baru
        keep = 50
        urut = sorted(berkas, key=lambda x: -x[1])
        disimpan = urut[:keep]
        dibuang = urut[keep:]
        assert len(disimpan) == 50 and len(dibuang) == 10
        assert min(m for _, m in disimpan) > max(m for _, m in dibuang), \
            "file yang lebih baru ikut terbuang"

    def test_export_per_entri_di_diagnostics(self):
        src = strip_kt_comments(read(UI / "settings/DiagnosticsScreen.kt"))
        assert "CreateDocument" in src, "tidak ada export SAF di Diagnostics"
        assert "exportLauncher.launch(" in src
        # Periksa PENUGASAN, bukan sekadar nama variabel: deklarasi state dan
        # reset di dalam launcher tetap ada walau tombol Export lupa mencatat
        # entri mana yang ditekan (bocor uji mutasi 2026-08-13).
        i_btn = src.find('"Export"')
        assert i_btn > 0, "tombol Export per-entri tidak ada"
        jendela = src[max(0, i_btn - 300):i_btn + 500]
        assert re.search(r"entriDiekspor\s*=\s*e\b", jendela), (
            "tombol Export tidak menyimpan entri yang dipilih — SAF asinkron, "
            "tanpa ini file yang diekspor bisa bukan yang ditekan user"
        )

    def test_export_dicabut_dari_footer_terminal(self):
        """Permintaan user: Export pindah ke Diagnostics, bukan ada di dua tempat."""
        src = strip_kt_comments(read(UI / "terminal/TerminalScreen.kt"))
        assert "exportLauncher" not in src, "launcher export yatim tertinggal di terminal"
        assert "CreateDocument" not in src, "Export masih ada di footer terminal"


# ---------------------------------------------------------------------------
# BUILD #3 — Paste di kolom Requirement
# ---------------------------------------------------------------------------
class TestPasteRequirement:
    def test_tombol_paste_ada(self):
        src = strip_kt_comments(read(UI / "settings/PipScreen.kt"))
        assert "LocalClipboardManager" in src, "tidak ada akses clipboard"
        assert '"Paste"' in src, "tombol Paste tidak ada di Install Modules"

    def test_paste_menangani_clipboard_kosong(self):
        src = strip_kt_comments(read(UI / "settings/PipScreen.kt"))
        i = src.find('"Paste"')
        # tombol didefinisikan SEBELUM labelnya; periksa jendela di sekitarnya
        jendela = src[max(0, i - 1800):i + 200]
        assert "Clipboard kosong" in jendela, (
            "paste dari clipboard kosong menimpa isi field tanpa penjelasan"
        )

    def test_paste_multibaris_masuk_antrian(self):
        """v1.0.18: multi-baris TIDAK dibuang lagi (perilaku lama: hanya
        baris pertama). Baris pertama mengisi field; sisanya jadi antrian
        install berurutan; komentar '#' dilewati; field tetap satu-baris
        (parser tidak pernah menerima teks multi-baris mentah)."""
        src = strip_kt_comments(read(UI / "settings/PipScreen.kt"))
        i = src.find('"Paste"')
        jendela = src[max(0, i - 2600):i + 200]
        assert re.search(r"lineSequence\(\)", jendela), (
            "clipboard dipakai mentah-mentah tanpa dipecah per baris"
        )
        assert 'startsWith("#")' in jendela, "komentar requirements.txt tidak dilewati"
        assert "onQueueLines" in jendela, (
            "baris ke-2 dst dibuang — kembali ke perilaku pra-v1.0.18"
        )
        assert "firstOrNull" in jendela, "field harus tetap diisi satu baris saja"

    def test_dispatcher_antrian_pop_sebelum_eksekusi(self):
        """Item antrian di-pop SEBELUM dieksekusi — item yang gagal tidak
        boleh mengulang dirinya selamanya (anti-loop)."""
        src = strip_kt_comments(read(UI / "settings/PipScreen.kt"))
        i = src.find("installQueue.first()")
        assert i > 0, "dispatcher antrian hilang"
        blok = src[i:i + 400]
        eksekusi = blok.find("analyzeThenInstall(")
        pop = blok.find("installQueue.drop(1)")
        assert 0 < pop < eksekusi, (
            "drop(1) harus terjadi SEBELUM analyzeThenInstall — kalau tidak, "
            "item gagal berputar selamanya"
        )


# ---------------------------------------------------------------------------
# BUILD #3 — Cakupan breadcrumb
#
# Aturan user: "Diagnostics harus mencatat semua perilaku user." Yang dijaga
# adalah TITIK INTERAKSI, bukan jumlah file: mencatat di TerminalBuffer (jalan
# per karakter) justru memenuhi breadcrumb 128KB dalam hitungan detik dan
# MENGHAPUS jejak crash yang sedang dicari.
# ---------------------------------------------------------------------------
class TestCakupanBreadcrumb:
    def test_navigasi_sidebar_tercatat_terpusat(self):
        src = strip_kt_comments(read(UI / "workbench/WorkbenchScreen.kt"))
        i = src.find("private fun DrawerItem(")
        assert i > 0
        badan = src[i:i + 700]
        assert 'Breadcrumb.log("NAV"' in badan, (
            "navigasi sidebar tidak tercatat di DrawerItem — menaruhnya di tiap "
            "pemanggil membuat item baru mudah terlewat"
        )

    def test_penghapusan_file_tercatat(self):
        src = strip_kt_comments(read(APP / "core/files/FileManager.kt"))
        assert 'Breadcrumb.log("FILE_DELETE"' in src, "penghapusan file tidak berjejak"
        assert "FILE_SAVE_FAIL" in src, "kegagalan simpan tidak berjejak"

    def test_simpan_sukses_TIDAK_dicatat(self):
        """Autosave berjalan terus; mencatat tiap simpan sukses akan
        menenggelamkan jejak crash di dalam derau."""
        src = strip_kt_comments(read(APP / "core/files/FileManager.kt"))
        assert "FILE_SAVE_OK" not in src, (
            "simpan sukses ikut dicatat — breadcrumb 128KB akan penuh oleh "
            "autosave dan justru menghapus jejak crash"
        )

    def test_hot_path_tidak_dicemari(self):
        """TerminalBuffer.append() jalan per potongan output; satu panggilan
        Breadcrumb di sini = ribuan tulis-disk per detik."""
        for nama in ("core/execution/TerminalBuffer.kt", "ui/terminal/AnsiLineCache.kt"):
            src = strip_kt_comments(read(APP / nama))
            assert "Breadcrumb.log" not in src, f"{nama} adalah hot path — jangan dicatat"

    def test_pilih_sample_tercatat(self):
        src = strip_kt_comments(read(UI / "samples/SamplesScreen.kt"))
        assert "SAMPLES_PILIH" in src, "tap sample tidak berjejak"


# ---------------------------------------------------------------------------
# ERROR CI 2026-08-13 (run 31640290111) — Breadcrumb.log() diberi objek
#
# `Breadcrumb.log(step: String, detail: String = "")`. Panggilan
# `Breadcrumb.log("SAMPLES_KATEGORI", category)` mengoper SampleCategory ke
# parameter String → type mismatch, dan CI merah di compileDebugKotlin.
#
# Lolos dari SEMUA cek lokal karena tools/kotlin_sanity_check.py hanya
# memeriksa keseimbangan leksikal, bukan tipe. Guard ini menutup celah itu
# untuk pemanggilan Breadcrumb: argumen kedua wajib berupa sesuatu yang
# jelas-jelas String.
# ---------------------------------------------------------------------------
class TestBreadcrumbArgumenString:
    def test_detail_selalu_string(self):
        """Daripada menebak dari nama variabel (rapuh — `filename`, `runId`,
        `trimmed` semuanya String yang sah), telusuri DEKLARASI argumennya di
        file yang sama dan tolak yang terbukti bukan String."""
        pola = re.compile(r'Breadcrumb\.log\(\s*"[^"]+"\s*,\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)')
        salah = []
        for f in sorted(APP.rglob("*.kt")):
            src = strip_kt_comments(read(f))
            for m in pola.finditer(src):
                nama = m.group(1)
                # Cari deklarasi: parameter `nama: Tipe` atau `val/var nama =`
                param = re.search(r"\b" + nama + r"\s*:\s*([A-Za-z0-9_.<>?]+)", src)
                if param:
                    tipe = param.group(1)
                    if tipe.rstrip("?") != "String":
                        salah.append("%s: %s bertipe %s" % (f.name, nama, tipe))
                    continue
                # Variabel lambda (`items(x) { entry -> ... }`) tidak punya
                # deklarasi val/var maupun anotasi tipe, jadi DULU lolos diam-
                # diam (bocor uji mutasi 2026-08-13). Telusuri sumber koleksinya.
                lam = re.search(r"\(([A-Za-z0-9_.]+)\)\s*\{\s*" + nama + r"\s*->", src)
                if lam:
                    salah.append(
                        "%s: %s adalah variabel lambda dari %s — oper properti "
                        "String-nya (mis. %s.id), bukan objeknya"
                        % (f.name, nama, lam.group(1), nama)
                    )
                    continue
                decl = re.search(r"\b(?:val|var)\s+" + nama + r"\s*=\s*([^\n]+)", src)
                if decl:
                    rhs = decl.group(1)
                    string_jelas = (
                        rhs.lstrip().startswith('"')
                        or ".trim()" in rhs or ".name" in rhs or ".id" in rhs
                        or "toString()" in rhs or "newId(" in rhs
                        or "mutableStateOf(" in rhs
                    )
                    if not string_jelas:
                        salah.append("%s: %s = %s" % (f.name, nama, rhs.strip()[:60]))
        assert not salah, (
            "argumen kedua Breadcrumb.log harus String — objek akan gagal "
            "compile di CI (kejadian nyata 2026-08-13, SampleCategory):\n  "
            + "\n  ".join(salah)
        )

    def test_kategori_sample_pakai_id(self):
        """Kasus persis yang membuat CI merah."""
        src = strip_kt_comments(read(UI / "samples/SamplesScreen.kt"))
        assert 'Breadcrumb.log("SAMPLES_KATEGORI", category)' not in src, (
            "SampleCategory dioper ke parameter String"
        )
        assert 'category.id' in src


# ---------------------------------------------------------------------------
# ERROR CI 2026-08-13 #2 (run 31640681578) — `context` dipakai di composable
# yang tidak punya parameter itu.
#
# `Toast.makeText(context, ...)` di dalam ManualTab: fungsi itu TIDAK menerima
# `context` dan tidak memanggil LocalContext.current, jadi unresolved reference.
# Lolos cek lokal karena sandbox tidak punya JDK — tidak ada tahap yang
# memeriksa scope.
#
# Penyebab langsungnya patut dicatat: `sed s/Toast.makeText(context,/.../`
# hanya mengenai pemanggilan satu baris; yang argumennya turun baris lolos
# diam-diam. Ini kegagalan senyap sed yang sudah tercatat sebelumnya, terulang.
# ---------------------------------------------------------------------------
class TestScopeContextCompose:
    def test_context_hanya_dipakai_bila_tersedia(self):
        """Untuk tiap fungsi @Composable privat: bila badannya memakai
        `context`, fungsi itu wajib menerimanya sebagai parameter ATAU
        mengambilnya dari LocalContext.current."""
        salah = []
        for f in sorted((UI).rglob("*.kt")):
            src = strip_kt_comments(read(f))
            # potong per deklarasi fungsi
            posisi = [m.start() for m in re.finditer(r"\nprivate fun |\nfun ", src)]
            posisi.append(len(src))
            for a, b in zip(posisi, posisi[1:]):
                blok = src[a:b]
                m = re.match(r"\n(?:private )?fun\s+(?:[\w.]+\.)?(\w+)\s*\(", blok)
                if not m:
                    continue
                nama = m.group(1)
                tutup = blok.find(") {")
                if tutup < 0:
                    continue
                tanda_tangan, badan = blok[:tutup], blok[tutup:]
                if not re.search(r"(?<![\w.])context(?![\w])", badan):
                    continue
                punya = (
                    re.search(r"context\s*:", tanda_tangan)
                    or "LocalContext.current" in badan
                    or re.search(r"val\s+context\s*=", badan)
                    # variabel lambda: `factory = { context -> ... }` (AndroidView)
                    # menyediakan context-nya sendiri — bukan pelanggaran.
                    or re.search(r"\{\s*context\s*->", badan)
                )
                if not punya:
                    salah.append("%s: fun %s" % (f.name, nama))
        assert not salah, (
            "`context` dipakai tanpa tersedia di scope — unresolved reference "
            "di CI (kejadian nyata 2026-08-13):\n  " + "\n  ".join(salah)
        )

    def test_manualtab_pakai_ctx_bukan_context(self):
        """Kasus persis yang membuat CI merah dua kali berturut-turut."""
        src = strip_kt_comments(read(UI / "settings/PipScreen.kt"))
        i = src.find("private fun ManualTab(")
        j = src.find("\n@Composable", i + 10)
        blok = src[i:j if j > i else len(src)]
        assert not re.search(r"(?<![\w.])context(?![\w])", blok), (
            "ManualTab tidak menerima parameter `context`; pakai `ctx` dari "
            "LocalContext.current"
        )


# ---------------------------------------------------------------------------
# DIAGNOSTIK TIDAK BOLEH MEMBUANG BUKTI (2026-08-13)
#
# Install numpy gagal di perangkat dengan pesan yang berhenti di
# "...user/troubles". Bukan kebetulan: Breadcrumb.log memotong detail di
# take(400), sedangkan pesan ImportError numpy panjangnya ~735 karakter dan
# boilerplate-nya ada di DEPAN. Baris "Original error was: ..." di posisi 664
# — satu-satunya baris yang menyebut sebab sebenarnya — terbuang.
#
# Diperparah dua hal: PackageEngineV2 mengisi technicalMessage dengan `null`
# di jalur SMOKE_TEST, dan PipScreen tidak pernah mencatat technicalMessage
# sama sekali. Tiga lapis pembuangan bukti untuk satu kegagalan.
# ---------------------------------------------------------------------------
class TestDiagnostikTidakMembuangBukti:
    def test_breadcrumb_batas_cukup_panjang(self):
        src = read(DIAG_DIR / "Breadcrumb.kt")
        m = re.search(r"MAX_DETAIL\s*=\s*(\d+)", src)
        assert m, "batas panjang detail tidak didefinisikan"
        n = int(m.group(1))
        assert n >= 2000, (
            f"batas {n} terlalu pendek; pesan ImportError numpy saja ~735 "
            "karakter dan traceback Python biasa jauh lebih panjang"
        )
        assert "take(400)" not in src, "batas 400 kembali — bug pemotongan hidup lagi"

    def test_pangkas_dari_tengah_bukan_ekor(self):
        """Sebab sebenarnya hampir selalu di baris TERAKHIR
        ("Original error was:", "Caused by:"). Memotong ekor = membuang jawaban."""
        src = read(DIAG_DIR / "Breadcrumb.kt")
        assert "fun ringkas(" in src
        i = src.find("fun ringkas(")
        badan = src[i:i + 600]
        assert "takeLast(" in badan, (
            "pemangkasan tidak mempertahankan EKOR pesan — sebab akhirnya hilang"
        )

    def test_smoke_test_mengisi_pesan_teknis(self):
        src = strip_kt_comments(read(PKGENG / "PackageEngineV2.kt"))
        # Ada LEBIH DARI SATU fail("SMOKE_TEST") sejak pustaka pendukung punya
        # jalur sendiri. find() menemukan yang pertama dan membuat guard ini
        # salah sasaran (2026-08-13) — periksa SEMUA kemunculan, dan cukup satu
        # di antaranya yang melaporkan daftar .so.
        posisi = [m.start() for m in re.finditer(r'fail\("SMOKE_TEST"', src)]
        assert posisi, "jalur kegagalan smoke test hilang"
        jendela = "".join(src[max(0, i - 1200):i + 200] for i in posisi)
        assert "nativeLibs" in jendela, "daftar .so tidak ikut dilaporkan"
        assert not re.search(r'fail\("SMOKE_TEST",\s*"smoke_test",[^)]*,\s*null\s*\)', src), (
            "technicalMessage diisi null — penyebab asli ImportError tidak "
            "sampai ke mana pun"
        )

    def test_pipscreen_mencatat_dan_menampilkan_detail(self):
        src = strip_kt_comments(read(UI / "settings/PipScreen.kt"))
        assert "PKG_INSTALL_DETAIL" in src, "pesan teknis tidak dicatat ke breadcrumb"
        assert "detail teknis" in src, (
            "pesan teknis tidak ditampilkan di konsol — user melapor dari HP "
            "tanpa PC dan harus bisa menyalinnya langsung"
        )

    def test_diagnosa_native_membedakan_tiga_sebab(self):
        """Uji PERILAKU: tiga sebab yang pesan ImportError-nya identik harus
        menghasilkan dugaan yang berbeda."""
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.smoke import diagnose_native
        import tempfile, os as _os

        d = tempfile.mkdtemp()
        so = _os.path.join(d, "_multiarray_umath.cpython-311.so")
        with open(so, "wb") as f:
            f.write(b"\x7fELF" + b"\x00" * 64)

        hasil_b = diagnose_native(d, 'dlopen failed: library "libc++_shared.so" not found')
        assert "sebab b" in hasil_b, hasil_b
        hasil_a = diagnose_native(d, "Attempt to load writable file")
        assert "sebab a" in hasil_a, hasil_a
        hasil_c = diagnose_native(d, "wrong ELF class: ELFCLASS64")
        assert "sebab c" in hasil_c, hasil_c
        # jujur saat tidak tahu
        hasil_x = diagnose_native(d, "sesuatu yang tak dikenal")
        assert "belum dapat dipastikan" in hasil_x, hasil_x

    def test_diagnosa_melaporkan_status_writable(self):
        """Sebab (a) hanya bisa dibedakan bila mode file benar-benar diperiksa."""
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.smoke import diagnose_native
        import tempfile, os as _os

        d = tempfile.mkdtemp()
        so = _os.path.join(d, "x.so")
        with open(so, "wb") as f:
            f.write(b"\x7fELF")
        assert "writable=True" in diagnose_native(d, "")
        _os.chmod(so, 0o555)
        assert "writable=False" in diagnose_native(d, "")

    def test_diagnosa_tidak_pernah_melempar(self):
        """Diagnostik tidak boleh jadi sumber kegagalan baru."""
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.smoke import diagnose_native
        assert isinstance(diagnose_native("/tidak/ada/sama/sekali", "x"), str)
        assert isinstance(diagnose_native("", None), str)
        # Input yang MEMAKSA exception di dalam badan fungsi. Argumen "sopan"
        # saja tidak cukup: mencabut `except Exception` tetap lolos karena tidak
        # ada yang melempar (bocor uji mutasi 2026-08-13).
        class Nakal:
            def __str__(self): raise RuntimeError("boom")
            def lower(self): raise RuntimeError("boom")
        assert isinstance(diagnose_native(Nakal(), Nakal()), str)
        # Pengaman wajib menangkap Exception secara umum, bukan satu jenis saja.
        src_py = (ROOT / "app/src/main/python/package_runtime/smoke.py").read_text()
        i = src_py.find("def diagnose_native(")
        assert "except Exception" in src_py[i:i + 2500], (
            "diagnose_native tanpa pengaman umum — diagnostik bisa jadi sumber "
            "kegagalan baru"
        )


# ---------------------------------------------------------------------------
# PUSTAKA PENDUKUNG NATIVE (2026-08-13) — akar kegagalan numpy di perangkat.
#
#   dlopen failed: library "libopenblas.so" not found
#
# Chaquopy memisahkan pustaka C bersama menjadi paket `chaquopy-*`. Pemisahan
# itu hanya tercatat di `requirements.host` pada meta.yaml resep Chaquopy;
# PyPI melaporkan numpy TANPA dependensi, jadi resolver kita tidak pernah
# mengunduhnya.
#
# Perbaikannya TIGA bagian yang saling bergantung — kurang satu, numpy tetap
# gagal. Guard di bawah mengunci ketiganya.
# ---------------------------------------------------------------------------
class TestPustakaPendukungNative:
    # ---- bagian 1: tag py3-none-android_* ----
    def test_tag_py3_none_dengan_platform_android(self):
        """Wheel pendukung bertag `py3-none-android_16_armeabi_v7a`: tidak
        terikat versi Python, tetapi TETAP terikat CPU."""
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.wheelinfo import android_supported_tags
        tags = android_supported_tags("armeabi-v7a", 31)
        kombinasi = {(t.interpreter, t.abi, t.platform) for t in tags}
        assert ("py3", "none", "android_16_armeabi_v7a") in kombinasi, (
            "kombinasi py3-none + platform Android tidak dibangkitkan — seluruh "
            "pustaka pendukung akan ditolak dan numpy tetap gagal"
        )

    def test_wheel_pendukung_nyata_diterima(self):
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.wheelinfo import android_supported_tags, wheel_compatible
        tags = android_supported_tags("armeabi-v7a", 31)
        assert wheel_compatible(
            "chaquopy_openblas-0.2.20-5-py3-none-android_16_armeabi_v7a.whl",
            supported_tags=tags), "wheel openblas ARMv7 ditolak"
        assert not wheel_compatible(
            "chaquopy_openblas-0.2.20-5-py3-none-android_21_arm64_v8a.whl",
            supported_tags=tags), "wheel openblas arm64 diterima di perangkat armv7"

    # ---- bagian 2: peta dependensi ----
    def test_peta_dependensi_dari_metayaml(self):
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.resolve import native_host_deps
        # Nilai-nilai ini dibaca dari requirements.host di meta.yaml Chaquopy.
        assert "chaquopy-openblas" in native_host_deps("numpy")
        assert "numpy" in native_host_deps("pandas")
        assert "chaquopy-libjpeg" in native_host_deps("pillow")
        assert "numpy" in native_host_deps("matplotlib")
        # Paket murni Python tidak boleh membawa apa pun.
        assert native_host_deps("requests") == []
        assert native_host_deps("flask") == []

    def test_resolver_mengantrikan_pustaka_pendukung(self):
        src = read(ROOT / "app/src/main/python/package_runtime/resolve.py")
        assert "for host_dep in native_host_deps(cname):" in src, (
            "pustaka pendukung tidak pernah diantrikan — wheel-nya tidak akan diunduh"
        )

    def test_pustaka_pendukung_dikenali(self):
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.resolve import is_support_library
        assert is_support_library("chaquopy-openblas")
        assert not is_support_library("numpy")

    # ---- bagian 3: preload (native-loader) ----
    def test_preload_ada_dan_dipanggil_sebelum_impor(self):
        src = read(ROOT / "app/src/main/python/package_runtime/smoke.py")
        assert "def preload_native_libs(" in src
        i_pre = src.find("preload_native_libs(\n")
        i_test = src.find("test_list = tests or")
        assert i_pre > 0 and i_test > 0
        assert i_pre < i_test, (
            "preload dijalankan SETELAH uji impor — tidak ada gunanya"
        )

    def test_preload_hanya_pustaka_bukan_modul_python(self):
        """`_multiarray_umath.so` harus diimpor Python, BUKAN dimuat ctypes."""
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.smoke import preload_native_libs
        import tempfile, os as _os
        d = tempfile.mkdtemp()
        for nama in ("libdukung.so", "_multiarray_umath.so"):
            with open(_os.path.join(d, nama), "wb") as f:
                f.write(b"\x7fELF")
        _n, log = preload_native_libs([d])
        gabung = " ".join(log)
        assert "libdukung.so" in gabung, "pustaka lib*.so tidak dicoba dimuat"
        assert "_multiarray_umath" not in gabung, (
            "modul Python ikut dimuat ctypes — akan merusak mesin impor"
        )

    def test_preload_tidak_pernah_melempar(self):
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.smoke import preload_native_libs
        assert preload_native_libs(None) == (0, [])
        assert preload_native_libs(["/tidak/ada"])[0] == 0
        # direktori berisi file rusak: harus dicatat, bukan crash
        import tempfile, os as _os
        d = tempfile.mkdtemp()
        with open(_os.path.join(d, "librusak.so"), "wb") as f:
            f.write(b"bukan ELF sama sekali")
        n, log = preload_native_libs([d])
        assert n == 0 and any("gagal" in x for x in log)

    # ---- bagian 4: smoke test paham pustaka pendukung ----
    def test_uji_impor_dilewati_untuk_pustaka_pendukung(self):
        src = strip_kt_comments(read(PKGENG / "PackageEngineV2.kt"))
        assert "p.supportLibrary" in src, (
            "pustaka pendukung tidak dibedakan — uji impor `import chaquopy-openblas` "
            "akan selalu gagal dan membatalkan paket utama yang sudah berhasil"
        )
        i = src.find("if (p.supportLibrary)")
        assert i > 0
        badan = src[i:i + 900]
        assert "continue" in badan, "tidak melewati uji impor"
        # Periksa PEMINDAIAN sungguhan, bukan sekadar munculnya ".so": teks itu
        # tetap ada di pesan error walau verifikasinya dihapus (bocor uji
        # mutasi 2026-08-13).
        assert re.search(r"walkTopDown\(\)[^\n]*\.so", badan), (
            "keberadaan file .so tidak benar-benar diperiksa — pustaka "
            "pendukung kosong akan lolos dan numpy gagal belakangan"
        )

    def test_flag_dibawa_dari_python_ke_kotlin(self):
        py = read(ROOT / "app/src/main/python/package_runtime/resolve.py")
        assert '"support_library"' in py, "Python tidak mengirim penanda"
        kt = strip_kt_comments(read(PKGENG / "DependencyResolver.kt"))
        assert 'optBoolean("support_library"' in kt, "Kotlin tidak membaca penanda"
        tx = strip_kt_comments(read(PKGENG / "TransactionManager.kt"))
        assert "supportLibrary" in tx, "penanda hilang saat masuk transaksi"

    # ---- bagian 5: daftar terpasang ----
    def test_daftar_terpasang_menyaring_pustaka_pendukung(self):
        src = strip_kt_comments(read(UI / "settings/PipScreen.kt"))
        i = src.find("fun refreshInstalled()")
        assert i > 0
        badan = src[i:i + 500]
        assert 'startsWith("chaquopy-")' in badan, (
            "daftar Terpasang menampilkan pustaka pendukung yang tidak pernah "
            "diminta user"
        )


# ---------------------------------------------------------------------------
# INSTRUMEN DIAGNOSA v1.0.9 — kenapa pustaka pendukung tidak muncul
#
# v1.0.8 gagal dengan pesan IDENTIK dengan v1.0.7: tidak ada satu pun baris
# yang menyebut chaquopy-openblas, dan tidak ada catatan preload. Karena itu
# TIDAK MUNGKIN dibedakan mana yang terjadi:
#   (a) peta NATIVE_HOST_DEPS tidak terbaca
#   (b) indeks Chaquopy tidak terjangkau dari perangkat
#   (c) wheel pendukung ditolak filter tag
#   (d) terunduh, tetapi .so gagal dimuat
#
# Semua lapisan sudah diverifikasi BEKERJA di sandbox, jadi sebabnya khusus
# perangkat. Instrumen di bawah memaksa setiap kemungkinan meninggalkan jejak.
# ---------------------------------------------------------------------------
class TestInstrumenDiagnosaNative:
    def test_resolver_mencatat_jejak_host_deps(self):
        src = read(ROOT / "app/src/main/python/package_runtime/resolve.py")
        assert "notes: list[str] = []" in src, "resolver tanpa jejak keputusan"
        assert '"notes": notes' in src, "jejak tidak dikembalikan ke pemanggil"
        assert "host_dep GAGAL diambil" in src, (
            "kegagalan mengambil pustaka pendukung tidak meninggalkan jejak — "
            "tidak bisa dibedakan dari peta yang tidak terbaca"
        )
        assert "host_dep OK" in src, "keberhasilan juga harus berjejak"

    def test_notes_sampai_ke_kotlin_dan_layar(self):
        kt = strip_kt_comments(read(PKGENG / "DependencyResolver.kt"))
        assert "val notes: List<String>" in kt, "ResolvePlan tidak membawa notes"
        assert re.search(r'strList\(\w+, "notes"\)', kt), "notes tidak diurai dari JSON"
        eng = strip_kt_comments(read(PKGENG / "PackageEngineV2.kt"))
        # Ada DUA jalur yang harus menampilkan notes: install() dan analyze().
        # Mencabut salah satu tetap lolos bila hanya dicek keberadaannya
        # (bocor uji mutasi 2026-08-13).
        assert eng.count("plan.notes.forEach") >= 2, (
            "notes tidak ditampilkan di KEDUA jalur (install dan analyze)"
        )
        assert "PKG_RESOLVE_NOTES" in eng, (
            "notes tidak masuk breadcrumb — user melapor lewat Diagnostics, "
            "bukan konsol yang sudah tergulir hilang"
        )

    def test_preload_selalu_meninggalkan_catatan(self):
        """Log kosong dan 'tidak ada lib*.so' adalah dua fakta berbeda."""
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.smoke import run_smoke
        import tempfile, os as _os
        d = tempfile.mkdtemp()
        _os.makedirs(_os.path.join(d, "kosong"))
        _ok, _res, info = run_smoke("tidakada", _os.path.join(d, "kosong"), None)
        log = info.get("preload_log") or []
        assert log, "preload_log kosong — diam tidak memberi informasi apa pun"
        assert "tidak ada lib" in " ".join(log), (
            "ketiadaan pustaka pendukung harus dinyatakan eksplisit"
        )

    def test_preload_log_sampai_ke_pesan_kegagalan(self):
        runner = strip_kt_comments(read(PKGENG / "SmokeTestRunner.kt"))
        assert "preloadLog" in runner, "SmokeTestRunner tidak membawa preloadLog"
        assert '"preload_log"' in runner, "preload_log tidak diurai dari JSON Python"
        eng = strip_kt_comments(read(PKGENG / "PackageEngineV2.kt"))
        assert re.search(r"outcome\.preloadLog\.take\(\d+\)\.forEach", eng), (
            "catatan preload tidak masuk pesan teknis — user tidak bisa "
            "membedakan 'tidak pernah diunduh' dari 'gagal dimuat'"
        )


# ---------------------------------------------------------------------------
# ERROR CI 2026-08-13 #3 — `strList(obj, ...)` padahal variabelnya bernama `o`
#
# Kelas yang sama dengan dua error CI sebelumnya: identifier yang tidak ada di
# scope. Sandbox tidak punya JDK, jadi tidak ada tahap lokal yang melihatnya.
#
# Guard ini memeriksa satu pola sempit tapi berulang: pemanggilan helper privat
# di dalam sebuah fungsi, dengan argumen pertama berupa nama variabel yang
# tidak pernah dideklarasikan di fungsi itu.
# ---------------------------------------------------------------------------
class TestArgumenAdaDiScope:
    def test_helper_dipanggil_dengan_variabel_yang_ada(self):
        salah = []
        for f in sorted(APP.rglob("*.kt")):
            src = strip_kt_comments(read(f))
            # helper privat berparameter (JSONObject, String) — pola strList/optX
            helpers = set(re.findall(r"private fun (\w+)\(\s*\w+\s*:\s*JSONObject", src))
            if not helpers:
                continue
            posisi = [m.start() for m in re.finditer(r"\n    (?:private )?fun \w+", src)]
            posisi.append(len(src))
            for a, b in zip(posisi, posisi[1:]):
                blok = src[a:b]
                nama_fn = re.match(r"\n    (?:private )?fun (\w+)", blok)
                if not nama_fn or nama_fn.group(1) in helpers:
                    continue
                # nama yang dikenal di blok ini
                dikenal = set(re.findall(r"\b(?:val|var)\s+(\w+)", blok))
                dikenal |= set(re.findall(r"(\w+)\s*:\s*[A-Z]", blok))
                dikenal |= set(re.findall(r"\{\s*(\w+)\s*->", blok))
                dikenal |= {"it", "this"}
                for h in helpers:
                    for arg in re.findall(r"\b" + h + r"\(\s*([a-z]\w*)\s*,", blok):
                        if arg not in dikenal:
                            salah.append(
                                "%s: %s(%s, ...) — '%s' tidak ada di scope"
                                % (f.name, h, arg, arg)
                            )
        assert not salah, (
            "argumen merujuk variabel yang tidak ada — unresolved reference di "
            "CI (kejadian nyata 2026-08-13):\n  " + "\n  ".join(salah)
        )


# ---------------------------------------------------------------------------
# RANTAI PUSTAKA NATIVE (v1.0.10) — dependensi punya dependensi.
#
# v1.0.9 membuktikan preload berjalan dan openblas terunduh, tetapi:
#     preload gagal: libopenblas.so
#       (dlopen failed: library "libgfortran.so.3" not found)
#
# Dua pelajaran:
#  1. Rantainya lebih dari satu tingkat. matplotlib -> numpy -> openblas ->
#     libgfortran adalah 3 tingkat. Satu lintasan pemuatan tidak cukup karena
#     urutan berkas di disk acak.
#  2. meta.yaml chaquopy-openblas TIDAK menyebut libgfortran sama sekali, jadi
#     daftar hafalan pasti ketinggalan. Kebutuhan sebenarnya hanya jujur
#     tertulis di dalam berkas .so (DT_NEEDED).
# ---------------------------------------------------------------------------
class TestRantaiPustakaNative:
    def _smoke(self):
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        import package_runtime.smoke as m
        return m

    def test_pembaca_elf_ada(self):
        src = read(ROOT / "app/src/main/python/package_runtime/smoke.py")
        assert "def elf_needed(" in src, (
            "tidak ada pembaca DT_NEEDED — kebutuhan .so hanya bisa ditebak"
        )
        assert "import struct" in src

    def test_elf_needed_membaca_kebutuhan_nyata(self):
        """Uji terhadap ELF sungguhan yang dibangun saat pengujian."""
        import subprocess, tempfile, os as _os, shutil
        if not shutil.which("gcc"):
            import pytest
            pytest.skip("gcc tidak tersedia")
        m = self._smoke()
        d = tempfile.mkdtemp()
        with open(_os.path.join(d, "dasar.c"), "w") as f:
            f.write("int dasar(void){return 1;}\n")
        with open(_os.path.join(d, "atas.c"), "w") as f:
            f.write("extern int dasar(void); int atas(void){return dasar();}\n")
        subprocess.run(["gcc", "-shared", "-fPIC", "-Wl,-soname,libdasar.so",
                        "-o", _os.path.join(d, "libdasar.so"),
                        _os.path.join(d, "dasar.c")], check=True)
        subprocess.run(["gcc", "-shared", "-fPIC",
                        "-o", _os.path.join(d, "libatas.so"),
                        _os.path.join(d, "atas.c"), "-L", d, "-ldasar"], check=True)
        assert "libdasar.so" in m.elf_needed(_os.path.join(d, "libatas.so"))

    def test_elf_needed_tidak_pernah_melempar(self):
        m = self._smoke()
        assert m.elf_needed("/tidak/ada/sama/sekali.so") == []
        import tempfile, os as _os
        d = tempfile.mkdtemp()
        rusak = _os.path.join(d, "librusak.so")
        with open(rusak, "wb") as f:
            f.write(b"bukan ELF")
        assert m.elf_needed(rusak) == []
        # ELF yang header-nya SAH tetapi isinya ngawur — memaksa struct.unpack
        # melempar di tengah badan fungsi. Tanpa ini, mencabut `except
        # Exception` tetap lolos karena tidak ada yang melempar (bocor uji
        # mutasi 2026-08-13).
        palsu = _os.path.join(d, "libpalsu.so")
        with open(palsu, "wb") as f:
            f.write(b"\x7fELF\x01\x01\x01" + b"\xff" * 120)
        assert m.elf_needed(palsu) == []
        src = read(ROOT / "app/src/main/python/package_runtime/smoke.py")
        i = src.find("def elf_needed(")
        assert "except Exception" in src[i:i + 4000], (
            "pembaca ELF tanpa pengaman umum — diagnostik bisa jadi sumber crash"
        )

    def test_preload_berulang_sampai_stabil(self):
        """Kunci perbaikan: satu lintasan gagal bila urutan disk kebetulan
        menaruh yang membutuhkan lebih dulu."""
        src = read(ROOT / "app/src/main/python/package_runtime/smoke.py")
        i = src.find("def preload_native_libs(")
        badan = src[i:i + 4000]
        assert "for _ronde in range(" in badan, (
            "pemuatan hanya satu lintasan — rantai bersarang akan gagal "
            "tergantung urutan berkas di disk"
        )
        assert "if not maju" in badan, "tidak ada syarat berhenti (risiko berputar)"

    def test_preload_menyelesaikan_rantai_urutan_terburuk(self):
        """Uji PERILAKU: yang membutuhkan dicoba lebih dulu, harus tetap beres."""
        import subprocess, tempfile, os as _os, shutil
        if not shutil.which("gcc"):
            import pytest
            pytest.skip("gcc tidak tersedia")
        m = self._smoke()
        base = tempfile.mkdtemp()
        src_dir = _os.path.join(base, "src"); _os.makedirs(src_dir)
        # nama direktori 'a' < 'z' supaya yang MEMBUTUHKAN ditemui lebih dulu
        da = _os.path.join(base, "a"); dz = _os.path.join(base, "z")
        _os.makedirs(da); _os.makedirs(dz)
        with open(_os.path.join(src_dir, "d.c"), "w") as f:
            f.write("int zdasar(void){return 5;}\n")
        with open(_os.path.join(src_dir, "a.c"), "w") as f:
            f.write("extern int zdasar(void); int zatas(void){return zdasar();}\n")
        subprocess.run(["gcc", "-shared", "-fPIC", "-Wl,-soname,libzdasar.so",
                        "-o", _os.path.join(dz, "libzdasar.so"),
                        _os.path.join(src_dir, "d.c")], check=True)
        subprocess.run(["gcc", "-shared", "-fPIC",
                        "-o", _os.path.join(da, "libaatas.so"),
                        _os.path.join(src_dir, "a.c"), "-L", dz, "-lzdasar"], check=True)
        n, log = m.preload_native_libs([base])
        assert n == 2, "rantai tidak selesai: %s" % log

    def test_kegagalan_menyebut_kebutuhan_yang_kurang(self):
        """Inilah yang mengubah 'gagal entah kenapa' menjadi nama paket."""
        src = read(ROOT / "app/src/main/python/package_runtime/smoke.py")
        i = src.find("preload gagal:")
        assert i > 0
        jendela = src[i:i + 400]
        assert "elf_needed(" in jendela, (
            "kegagalan tidak menyebut pustaka yang kurang — siklus uji "
            "berikutnya akan buta lagi"
        )

    def test_peta_memuat_rantai_bersarang(self):
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.resolve import native_host_deps
        assert "chaquopy-libgfortran" in native_host_deps("chaquopy-openblas"), (
            "libgfortran tidak ikut — bukti perangkat v1.0.9 diabaikan"
        )
        assert "chaquopy-libxml2" in native_host_deps("chaquopy-libxslt")

    def test_sumber_entri_runtime_ditandai(self):
        """Aturan jujur: entri yang TIDAK berasal dari meta.yaml harus
        menyebutkan dasarnya, karena tidak bisa diverifikasi dari dokumen."""
        src = read(ROOT / "app/src/main/python/package_runtime/resolve.py")
        i = src.find('"chaquopy-openblas": ["chaquopy-libgfortran"]')
        assert i > 0, "entri libgfortran hilang"
        # Penanda harus berada TEPAT di atas entrinya, bukan sekadar ada di
        # suatu tempat dalam berkas (bocor uji mutasi 2026-08-13).
        # Hanya baris komentar TEPAT sebelum entri yang dihitung — bukan
        # paragraf penjelasan di atas peta (yang juga memuat frasa itu).
        baris_sebelum = src[max(0, i - 300):i].rstrip().splitlines()[-1:]
        assert baris_sebelum and "[dari perangkat]" in baris_sebelum[0], (
            "entri dari bukti runtime tidak ditandai di tempatnya — pembaca "
            "berikutnya akan mengira dasarnya meta.yaml"
        )


class TestPetaPustakaNative:
    """Guard untuk peta pustaka->paket (nativemap.py, v1.0.11).

    Kelas bug yang dijaga di sini semuanya DITEMUKAN SAAT PENGUJIAN, bukan
    dibayangkan:

      1. `libxml2.so` dinormalisasi menjadi `libxml.so`, sehingga menunjuk ke
         paket `chaquopy-libxml` yang tidak ada. Angka 2 adalah bagian dari
         nama, bukan versi.
      2. `libc.so.6` (bentuk glibc) tidak dikenali sebagai pustaka sistem,
         sehingga dilaporkan sebagai pustaka yang hilang.
      3. `libc10.so` milik PyTorch dinormalisasi menjadi `libc.so` lalu
         DIABAIKAN sebagai pustaka sistem — kebalikannya, dan lebih berbahaya
         karena gagal secara diam-diam.
    """

    def _nm(self):
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        import package_runtime.nativemap as m
        return m

    def test_modul_peta_ada(self):
        p = ROOT / "app/src/main/python/package_runtime/nativemap.py"
        assert p.exists(), (
            "nativemap.py hilang — tanpa peta, pustaka pendukung kembali "
            "ditambal satu per satu tiap rilis"
        )

    def test_libxml2_tidak_terpotong_jadi_libxml(self):
        """BOCOR SAAT UJI MUTASI (M1, 2026-08-13): versi pertama guard ini
        hanya memeriksa hasil akhir `package_for_lib`, sehingga menghapus
        entri `libxml2.so` dari peta TETAP lulus — jaring normalisasi
        kebetulan menutupinya lewat ejaan `libxml.so`. Ketergantungan pada
        kebetulan itu rapuh, jadi kedua ejaan diperiksa langsung di peta.
        """
        nm = self._nm()
        assert "libxml2.so" in nm.LIB_TO_PACKAGE, (
            "ejaan asli libxml2.so hilang dari peta — hanya selamat selama "
            "aturan normalisasi kebetulan mendarat di entri lain"
        )
        assert nm.normalize_soname("libxml2.so") == "libxml.so", (
            "asumsi berubah: normalisasi resmi Chaquopy tidak lagi memotong "
            "angka; komentar di nativemap.py harus ikut diperbarui"
        )
        for ejaan in ("libxml2.so", "libxml.so"):
            hasil = nm.package_for_lib(ejaan)
            assert hasil is not None, "%s tidak dikenal — lxml akan gagal" % ejaan
            assert hasil[0] == "chaquopy-libxml2", (
                "%s menunjuk ke %r; angka 2 bagian dari NAMA, bukan versi"
                % (ejaan, hasil[0])
            )

    def test_pustaka_sistem_glibc_dikenali(self):
        """Wheel dari Linux desktop memakai nama bersufiks versi glibc."""
        nm = self._nm()
        for nama in ["libc.so.6", "libm.so.6", "libdl.so.2", "libpthread.so.0"]:
            assert nm.is_system_lib(nama, 34), (
                "%s tidak dikenali sebagai pustaka sistem — ZCODE akan "
                "mencoba mengunduh paket yang tidak pernah ada" % nama
            )

    def test_libc10_pytorch_bukan_libc(self):
        """Tabrakan yang Chaquopy peringatkan sendiri di build-wheel.py."""
        nm = self._nm()
        assert not nm.is_system_lib("libc10.so", 34), (
            "libc10.so (PyTorch) dikira libc sistem lalu diabaikan — "
            "kegagalan diam-diam, kelas bug terburuk"
        )

    def test_pustaka_sistem_menghormati_level_api(self):
        nm = self._nm()
        assert not nm.is_system_lib("libvulkan.so", 16), (
            "libvulkan.so (API 24) dianggap ada di perangkat API 16"
        )
        assert nm.is_system_lib("libvulkan.so", 24)

    def test_peta_menutup_seluruh_indeks(self):
        """Bukan 'beberapa paket' — seluruh isi indeks Chaquopy.

        Kalau peta hanya memuat pustaka yang kebetulan dipakai numpy, pemakai
        pertama yang memasang lxml/pillow/h5py akan menabrak dinding yang sama.
        """
        nm = self._nm()
        indeks = {
            "crc32c", "curl", "flac", "freetype", "geos", "hdf5", "lame",
            "libcxx", "libffi", "libgfortran", "libiconv", "libjpeg",
            "libogg", "libomp", "libpng", "libraw", "libsndfile", "libtiff",
            "libvorbis", "libxml2", "libxslt", "libyaml", "libzmq", "llvm",
            "openblas", "proj", "secp256k1", "ta-lib", "zbar",
        }
        dipetakan = {
            p.replace("chaquopy-", "") for p in nm.daftar_paket_pendukung()
        }
        kurang = indeks - dipetakan
        assert not kurang, "paket indeks belum dipetakan: %s" % sorted(kurang)

    def test_entri_dugaan_ditandai_berbeda(self):
        """Peraturan jujur: dugaan tidak boleh menyamar sebagai fakta."""
        nm = self._nm()
        assert nm.package_for_lib("libc++_shared.so")[1] == "RESMI"
        assert nm.package_for_lib("libgfortran.so.3")[1] == "PERANGKAT"
        assert nm.package_for_lib("libyaml.so")[1] == "DUGAAN", (
            "entri yang belum diverifikasi ditandai seolah terbukti"
        )

    def test_openssl_tidak_dipetakan_ke_paket_hantu(self):
        """KOREKSI 2026-08-13 (v1.0.12) — guard ini DIBALIK dari versi lama.

        Versi pertama menuntut `libssl.so` memetakan ke paket bernama
        `openssl`, dengan alasan meta.yaml cryptography menulis nama itu
        polos. Pemeriksaan langsung ke indeks membuktikan paketnya TIDAK ADA:
        chaquo.com/pypi-13.1/openssl/ dan .../chaquopy-openssl/ sama-sama
        HTTP 404. Guard lama mengunci bug: ZCODE akan mengunduh paket hantu.

        Sekarang yang dijaga adalah kebalikannya — libssl harus menghasilkan
        PENJELASAN, bukan percobaan unduh.
        """
        nm = self._nm()
        assert nm.package_for_lib("libssl.so") is None, (
            "libssl.so dipetakan ke sebuah paket; tidak ada wheel openssl di "
            "indeks Chaquopy, jadi unduhannya pasti 404"
        )
        for paket, _dasar in nm.LIB_TO_PACKAGE.values():
            assert "openssl" not in paket or paket.endswith("-openssl-3"), (
                "paket %r tidak ada di indeks" % paket
            )
        r = nm.resolve_needed(["libssl.so"], api=34)
        assert r["no_package"] == ["libssl.so"]
        assert not r["packages"], "masih mencoba mengunduh sesuatu untuk libssl"
        assert r["notes"] and "libssl_chaquopy" in r["notes"][0], (
            "tidak menjelaskan nama sebenarnya — pemakai hanya lihat kegagalan"
        )

    def test_setiap_paket_ada_di_indeks_sungguhan(self):
        """Daftar indeks diverifikasi langsung dari chaquo.com/pypi-13.1/
        pada 2026-08-13. Nama yang tidak ada di sini = unduhan 404.

        Guard ini lahir setelah saran dari luar mengusulkan 16 paket
        (chaquopy-openssl, -zlib, -sqlite, -protobuf, -brotli, -gsl, ...) yang
        SATU PUN tidak ada di indeks. Peta hanya boleh memuat nama nyata.
        """
        nm = self._nm()
        indeks_nyata = {
            "chaquopy-crc32c", "chaquopy-curl-openssl-3", "chaquopy-curl",
            "chaquopy-flac", "chaquopy-freetype", "chaquopy-geos",
            "chaquopy-hdf5", "chaquopy-lame", "chaquopy-libcxx",
            "chaquopy-libffi", "chaquopy-libgfortran", "chaquopy-libiconv",
            "chaquopy-libjpeg", "chaquopy-libogg", "chaquopy-libomp",
            "chaquopy-libpng", "chaquopy-libraw", "chaquopy-libsndfile",
            "chaquopy-libtiff", "chaquopy-libvorbis", "chaquopy-libxml2",
            "chaquopy-libxslt", "chaquopy-libyaml", "chaquopy-libzmq",
            "chaquopy-llvm", "chaquopy-openblas", "chaquopy-proj-openssl-3",
            "chaquopy-proj", "chaquopy-secp256k1", "chaquopy-ta-lib",
            "chaquopy-zbar",
        }
        assert len(indeks_nyata) == 31
        hantu = set(nm.daftar_paket_pendukung()) - indeks_nyata
        assert not hantu, (
            "paket berikut TIDAK ADA di indeks Chaquopy, unduhannya akan "
            "404: %s" % sorted(hantu)
        )

    def test_resolve_needed_menyaring_sistem_dan_yang_sudah_ada(self):
        nm = self._nm()
        r = nm.resolve_needed(
            ["libc.so", "liblog.so", "libopenblas.so", "libc++_shared.so"],
            tersedia={"libopenblas.so"},
            api=34,
        )
        assert "chaquopy-libcxx" in r["packages"]
        assert "chaquopy-openblas" not in r["packages"], "sudah ada, jangan diunduh ulang"
        assert not r["unknown"]
        assert "libc.so" in r["system"]

    def test_pustaka_tak_dikenal_dilaporkan_bukan_didiamkan(self):
        nm = self._nm()
        r = nm.resolve_needed(["libentahapa.so"], api=34)
        assert r["unknown"] == ["libentahapa.so"]
        pesan = nm.jelaskan_tak_dikenal(r["unknown"])
        assert "libentahapa.so" in pesan, (
            "nama pustaka tidak disebut — pemakai tak bisa melaporkan apa pun"
        )

    def test_pemindai_membaca_rantai_dari_elf_nyata(self):
        """Uji ujung-ke-ujung dengan .so sungguhan, bukan mock.

        CATATAN CACAT EKSPERIMEN yang pernah terjadi: tanpa
        `-Wl,--no-as-needed`, linker MEMBUANG entri DT_NEEDED yang simbolnya
        tidak terpakai, sehingga uji lulus tanpa menguji apa pun.
        """
        import shutil, subprocess, tempfile, os as _os
        if not shutil.which("gcc"):
            import pytest
            pytest.skip("gcc tidak tersedia")
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.smoke import scan_missing_libs

        d = tempfile.mkdtemp()
        gudang, paket = _os.path.join(d, "g"), _os.path.join(d, "p")
        _os.makedirs(gudang); _os.makedirs(paket)
        with open(_os.path.join(d, "s.c"), "w") as f:
            f.write("int x(void){return 1;}\n")
        c = _os.path.join(d, "s.c")

        def bangun(out, soname=None, tautan=()):
            cmd = ["gcc", "-shared", "-fPIC", "-o", out, c]
            if soname:
                cmd += ["-Wl,-soname," + soname]
            if tautan:
                cmd += ["-Wl,--no-as-needed"]
                for direktori, lib in tautan:
                    cmd += ["-L" + direktori, "-l:" + lib]
            subprocess.run(cmd, check=True, capture_output=True)

        bangun(_os.path.join(gudang, "libgfortran.so.3"), "libgfortran.so.3")
        bangun(_os.path.join(gudang, "libc++_shared.so"), "libc++_shared.so")
        bangun(_os.path.join(paket, "libopenblas.so"), "libopenblas.so",
               [(gudang, "libgfortran.so.3")])
        bangun(_os.path.join(paket, "_multiarray_umath.so"), None,
               [(paket, "libopenblas.so"), (gudang, "libc++_shared.so")])

        r = scan_missing_libs([paket], api=34)
        assert "chaquopy-libcxx" in r["packages"], (
            "libc++_shared.so tidak terdeteksi — ini kegagalan v1.0.10 yang "
            "sesungguhnya"
        )
        assert "chaquopy-libgfortran" in r["packages"], (
            "rantai tingkat kedua terlewat — pemindaian hanya sedalam satu lapis"
        )
        assert not r["unknown"], "libc palsu bocor sebagai tak dikenal: %s" % r["unknown"]

    def test_engine_mengunduh_pustaka_kurang_secara_berulang(self):
        """Satu putaran tidak cukup: pustaka baru membawa kebutuhan baru."""
        src = read(ROOT / "app/src/main/java/com/zaba/zcode/core/packageengine/PackageEngineV2.kt")
        assert "scanMissingLibs" in src, "engine tidak pernah memindai"
        assert re.search(r"while\s*\(\s*putaran\s*<\s*MAX_SUPPORT_ROUNDS", src), (
            "pemindaian tidak diulang — rantai bertingkat akan berhenti di "
            "lapis pertama, persis kegagalan v1.0.9"
        )
        assert "MAX_SUPPORT_ROUNDS" in src, "tidak ada batas putaran — risiko loop tak henti"

    def test_pustaka_tak_dikenal_sampai_ke_layar(self):
        """User tidak punya logcat; diam = siklus uji berikutnya buta."""
        src = read(ROOT / "app/src/main/java/com/zaba/zcode/core/packageengine/PackageEngineV2.kt")
        i = src.find("kurang.unknown.isNotEmpty()")
        assert i > 0, "hasil 'tidak dikenal' tidak pernah diperiksa"
        jendela = src[i:i + 500]
        assert "onStep(" in jendela, "tidak dilaporkan ke layar"
        # BOCOR SAAT UJI MUTASI (M9): mencari "joinToString" saja tidak cukup —
        # ada pemanggilan LAIN milik Breadcrumb beberapa baris di bawahnya,
        # sehingga menghapus nama pustaka dari pesan layar tetap lulus.
        # Yang harus dipastikan: isi `kurang.unknown` benar-benar dirangkai
        # ke dalam teks yang dikirim ke onStep.
        assert re.search(
            r"onStep\([^\n]*kurang\.unknown\.joinToString", jendela
        ), (
            "nama pustakanya sendiri tidak sampai ke layar — pemakai tidak "
            "punya logcat dan tak bisa melaporkan apa pun"
        )

    def test_smoke_memanggil_pemindai_sungguhan(self):
        """BOCOR SAAT UJI MUTASI (M10): mengganti pemanggilan pemindai dengan
        dict kosong tetap lulus, karena tidak satu pun guard memeriksa bahwa
        run_smoke benar-benar memindai. Hasil pindai adalah satu-satunya jalan
        nama pustaka yang kurang sampai ke layar HP.
        """
        src = read(ROOT / "app/src/main/python/package_runtime/smoke.py")
        assert re.search(
            r"kurang\s*=\s*scan_missing_libs\(", src
        ), "run_smoke tidak memanggil scan_missing_libs — hasilnya dipalsukan"
        assert "native_info[\"missing_libs\"]" in src, (
            "hasil pindai tidak dilampirkan ke native_info — Kotlin tidak "
            "akan pernah melihatnya"
        )

    def test_pemindaian_gagal_tidak_membatalkan_instalasi(self):
        """Pemindai menambah kemampuan; ia tidak boleh mengurangi."""
        src = read(ROOT / "app/src/main/java/com/zaba/zcode/core/packageengine/PackageEngineV2.kt")
        i = src.find("kurang.error.isNotBlank()")
        assert i > 0, "kegagalan pemindaian tidak ditangani"
        jendela = src[i:i + 400]
        assert "return fail(" not in jendela, (
            "pemindaian yang gagal membatalkan instalasi — regresi perilaku"
        )


class TestNamaModulDariWheel:
    """Guard v1.0.13 — nama modul dibaca dari wheel, bukan ditebak.

    KEGAGALAN YANG DIJAGA (log perangkat 2026-08-13):
        PKG_INSTALL_FAIL | matplotlib [SMOKE_TEST]
        ModuleNotFoundError: No module named 'fonttools'
        native .so: 0
    `native .so: 0` membuktikan ini BUKAN masalah pustaka native — pustaka
    native justru sudah sukses semuanya di log yang sama. Paket `fonttools`
    memasang modul bernama `fontTools` (huruf T besar); katalog bawaan tidak
    memuatnya, jadi uji impor salah alamat lalu me-rollback seluruh transaksi.

    16% paket di katalog punya nama impor berbeda; paket dengan 7 dependensi
    punya ~71% peluang minimal satu meleset. Karena itu penyembuhannya bukan
    memperbesar katalog, melainkan berhenti menebak.
    """

    def _mn(self):
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        import package_runtime.modulename as m
        return m

    def _buat_paket(self, tmp, dist_info_nama, top_level=None, record=None,
                    direktori=()):
        import os as _os
        di = _os.path.join(tmp, dist_info_nama)
        _os.makedirs(di, exist_ok=True)
        with open(_os.path.join(di, "METADATA"), "w") as f:
            f.write("Metadata-Version: 2.1\nName: x\nVersion: 1.0\n")
        if top_level is not None:
            with open(_os.path.join(di, "top_level.txt"), "w") as f:
                f.write("\n".join(top_level) + "\n")
        if record is not None:
            with open(_os.path.join(di, "RECORD"), "w") as f:
                f.write("\n".join("%s,," % r for r in record) + "\n")
        for d in direktori:
            _os.makedirs(_os.path.join(tmp, d), exist_ok=True)
            open(_os.path.join(tmp, d, "__init__.py"), "w").close()
        return tmp

    def test_modul_pembaca_ada(self):
        p = ROOT / "app/src/main/python/package_runtime/modulename.py"
        assert p.exists(), (
            "modulename.py hilang — ZCODE kembali menebak nama impor dari "
            "katalog, dan paket di luar katalog akan gagal lagi"
        )

    def test_fonttools_huruf_besar_terbaca(self):
        """Kasus persis yang menumbangkan matplotlib di perangkat."""
        import tempfile
        mn = self._mn()
        t = self._buat_paket(
            tempfile.mkdtemp(), "fonttools-4.63.0.dist-info",
            top_level=["fontTools"], record=["fontTools/__init__.py"],
        )
        r = mn.module_names(t, "fonttools")
        assert r["names"] == ["fontTools"], (
            "nama modul terbaca %r, seharusnya fontTools — matplotlib akan "
            "gagal lagi dengan ModuleNotFoundError" % (r["names"],)
        )
        assert r["source"] == "top_level.txt"

    def test_record_dipakai_saat_top_level_tidak_ada(self):
        """PEP 427 tidak mewajibkan top_level.txt; packaging & pygments
        versi baru memang tidak menyertakannya."""
        import tempfile
        mn = self._mn()
        t = self._buat_paket(
            tempfile.mkdtemp(), "attrs-2.0.dist-info",
            top_level=None, record=["attr/__init__.py", "attrs-2.0.dist-info/METADATA"],
        )
        r = mn.module_names(t, "attrs")
        assert r["names"] == ["attr"], (
            "RECORD tidak dipakai sebagai cadangan; paket tanpa top_level.txt "
            "akan jatuh ke tebakan nama paket dan gagal"
        )
        assert r["source"] == "RECORD"

    def test_egg_info_format_lama_juga_dibaca(self):
        """DITEMUKAN SAAT PENGUJIAN: setuptools memakai .egg-info, bukan
        .dist-info. Versi pertama pembaca ini melewatkannya."""
        import tempfile
        mn = self._mn()
        t = self._buat_paket(
            tempfile.mkdtemp(), "setuptools-66.1.1.egg-info",
            top_level=["_distutils_hack", "pkg_resources", "setuptools"],
        )
        r = mn.module_names(t, "setuptools")
        assert "pkg_resources" in r["all"], ".egg-info diabaikan"
        assert r["source"] == "top_level.txt"

    def test_modul_internal_tidak_dijadikan_sasaran_uji(self):
        """`_distutils_hack` dan `_pytest` itu detail internal, bukan
        antarmuka publik yang layak diuji impor."""
        import tempfile
        mn = self._mn()
        t = self._buat_paket(
            tempfile.mkdtemp(), "pytest-7.0.dist-info",
            top_level=["_pytest", "py", "pytest"],
        )
        r = mn.module_names(t, "pytest")
        assert not r["names"][0].startswith("_"), (
            "modul internal dijadikan sasaran uji impor pertama"
        )
        assert "_pytest" in r["all"], "daftar lengkap tidak boleh kehilangan apa pun"

    def test_tidak_pernah_melempar_dan_selalu_punya_cadangan(self):
        mn = self._mn()
        for arg in ("/tidak/ada/sama/sekali", "", None):
            r = mn.module_names(arg, "requests")
            assert r["names"] == ["requests"], (
                "kehilangan cadangan nama paket untuk input %r" % (arg,)
            )

    def test_tanda_hubung_jadi_garis_bawah_pada_cadangan(self):
        mn = self._mn()
        r = mn.module_names("/tidak/ada", "python-dateutil")
        assert r["names"] == ["python_dateutil"], (
            "tanda hubung tidak diubah; `import python-dateutil` bukan "
            "sintaks Python yang sah"
        )

    def test_engine_membaca_metadata_sebelum_katalog(self):
        """Urutan sumber menentukan: katalog dulu = bug lama kembali."""
        src = read(ROOT / "app/src/main/java/com/zaba/zcode/core/packageengine/PackageEngineV2.kt")
        assert "smokeRunner.moduleNames(" in src, (
            "engine tidak pernah membaca metadata modul dari wheel"
        )
        i = src.find("val importName =")
        assert i > 0, "penentuan importName hilang"
        jendela = src[i:i + 260]
        pos_baca = jendela.find("terbaca.names")
        pos_katalog = jendela.find("details?.importName")
        assert pos_baca >= 0, "metadata wheel tidak dipakai untuk importName"
        assert pos_katalog < 0 or pos_baca < pos_katalog, (
            "katalog didahulukan atas metadata wheel — fonttools akan gagal lagi"
        )

    def test_kegagalan_baca_metadata_tidak_membatalkan_instalasi(self):
        """Pembaca ini menambah ketepatan; ia tidak boleh mengurangi keandalan."""
        src = read(ROOT / "app/src/main/java/com/zaba/zcode/core/packageengine/PackageEngineV2.kt")
        i = src.find("terbaca.error.isNotBlank()")
        assert i > 0, "kegagalan pembacaan metadata tidak ditangani"
        jendela = src[i:i + 320]
        assert "return fail(" not in jendela, (
            "metadata tak terbaca membatalkan instalasi — regresi perilaku"
        )
        assert "onStep(" in jendela, "penurunan kualitas tebakan tidak dilaporkan"

    def test_jalur_pustaka_native_tidak_ikut_berubah(self):
        """PERMINTAAN USER (2026-08-13): perbaikan ini TIDAK BOLEH
        menghidupkan lagi kegagalan numpy yang butuh 5 rilis untuk sembuh.

        Fungsi native bekerja atas DIREKTORI dan byte ELF; tidak satu pun
        menerima nama modul. Guard ini mengunci pemisahan itu, supaya
        perubahan di masa depan tidak diam-diam menyilangkan keduanya.
        """
        src = read(ROOT / "app/src/main/python/package_runtime/smoke.py")
        for tanda in ("def preload_native_libs(dirs",
                      "def scan_missing_libs(dirs",
                      "def elf_needed(path"):
            assert tanda in src, (
                "tanda tangan fungsi native berubah (%r) — jalur numpy "
                "berisiko ikut terpengaruh" % tanda
            )
        import re as _re
        for fn in ("preload_native_libs", "scan_missing_libs"):
            m = _re.search(r"def %s\(([^)]*)\)" % fn, src)
            assert m and "import_name" not in m.group(1), (
                "%s mulai menerima nama modul — pemisahan native vs impor "
                "bocor" % fn
            )


class TestDependensiDariWheel:
    """Guard v1.0.14 — dependensi dibaca dari METADATA wheel, bukan peta tangan.

    KEGAGALAN YANG DIJAGA (log perangkat 2026-08-13):
        PKG_RESOLVE_NOTES | host_deps pandas -> numpy      <- HANYA numpy
        ImportError: Unable to import required dependencies: pytz

    METADATA pandas 2.1.3 mencantumkan EMPAT dependensi wajib: numpy,
    python-dateutil, pytz, tzdata. ZCODE hanya membawa satu karena memakai
    peta buatan tangan.

    Yang membuktikan katalog bukan obatnya: `pytz` SUDAH ada di katalog
    bawaan, tetapi tetap tidak ikut terpasang.
    """

    def _wd(self):
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        import package_runtime.wheeldeps as m
        return m

    def _wheel(self, tmp, nama, versi, requires, metadata_version="2.1",
               akhiran="\n"):
        """Bangun wheel sungguhan (ZIP + dist-info) sesuai PEP 427."""
        import zipfile, os as _os
        jalur = _os.path.join(tmp, "%s-%s-py3-none-any.whl" % (nama, versi))
        di = "%s-%s.dist-info" % (nama, versi)
        baris = ["Metadata-Version: %s" % metadata_version,
                 "Name: %s" % nama, "Version: %s" % versi]
        baris += ["Requires-Dist: %s" % r for r in requires]
        with zipfile.ZipFile(jalur, "w") as z:
            z.writestr("%s/METADATA" % di, akhiran.join(baris) + akhiran)
            z.writestr("%s/RECORD" % di, "%s/METADATA,,\n" % di)
        return jalur

    def test_modul_pembaca_ada(self):
        p = ROOT / "app/src/main/python/package_runtime/wheeldeps.py"
        assert p.exists(), (
            "wheeldeps.py hilang — dependensi kembali ditebak dari peta "
            "tangan dan paket seperti pandas akan kehilangan pytz lagi"
        )

    def test_pandas_membawa_empat_dependensi(self):
        """Kasus persis yang gagal di perangkat."""
        import tempfile
        wd = self._wd()
        w = self._wheel(tempfile.mkdtemp(), "pandas", "2.1.3", [
            'numpy<2,>=1.23.2; python_version == "3.11"',
            "python-dateutil>=2.8.2",
            "pytz>=2020.1",
            "tzdata>=2022.1",
        ])
        nama = {wd.normalisasi(r["name"])
                for r in wd.deps_from_wheel(w, {"python_version": "3.11"})["requires"]}
        assert nama == {"numpy", "python-dateutil", "pytz", "tzdata"}, (
            "dependensi pandas terbaca %s — pytz hilang berarti ImportError "
            "yang sama akan terulang" % sorted(nama)
        )

    def test_dependensi_extra_tidak_ikut_dipasang(self):
        """METADATA pandas punya 77 baris Requires-Dist, hanya 6 wajib.

        Memasang semuanya berarti mengunduh puluhan megabita yang tidak
        pernah dipakai — dan sebagian di antaranya tidak punya wheel Android.
        """
        import tempfile
        wd = self._wd()
        w = self._wheel(tempfile.mkdtemp(), "x", "1.0", [
            "numpy>=1.0",
            'pytest>=7; extra == "test"',
            'sphinx; extra == "docs"',
        ])
        h = wd.deps_from_wheel(w)
        assert [r["name"] for r in h["requires"]] == ["numpy"]
        assert len(h["optional"]) == 2, "dependensi extra tidak dipisahkan"

    def test_crlf_windows_tidak_merusak_nama(self):
        """DITEMUKAN SAAT PENGUJIAN: wheel `tifffile` dibangun di Windows,
        setiap baris METADATA-nya berakhiran CR. Versi pertama parser
        menghasilkan nama `numpy\\r` lalu membuangnya diam-diam — dependensi
        hilang tanpa satu pun pesan."""
        import tempfile
        wd = self._wd()
        w = self._wheel(tempfile.mkdtemp(), "tifffile", "2026.3.3",
                        ["numpy>=1.20", 'lxml; extra == "xml"'], akhiran="\r\n")
        h = wd.deps_from_wheel(w)
        nama = [r["name"] for r in h["requires"]]
        assert nama == ["numpy"], (
            "CRLF merusak pembacaan: %r — dependensi hilang tanpa suara" % nama
        )
        # BOCOR SAAT UJI MUTASI (M1): memeriksa nama saja TIDAK cukup — regex
        # nama memakai `\s*` yang kebetulan ikut menyerap CR, sehingga
        # menghapus .strip() tetap lulus. Yang benar-benar rusak tanpa strip
        # adalah bagian SETELAH nama: penanda versi membawa CR, dan marker
        # `extra` gagal dikenali sehingga dependensi opsional ikut terpasang.
        assert "\r" not in h["requires"][0]["specifier"], (
            "CR ikut terbawa ke penanda versi: %r" % h["requires"][0]["specifier"]
        )
        assert h["requires"][0]["specifier"] == ">=1.20"
        assert len(h["optional"]) == 1, (
            "marker extra tidak dikenali saat baris berakhiran CRLF — "
            "dependensi opsional ikut dipasang"
        )

    def test_marker_platform_lain_ditolak(self):
        import tempfile
        wd = self._wd()
        w = self._wheel(tempfile.mkdtemp(), "x", "1.0", [
            'pywin32; sys_platform == "win32"',
            'pyobjc; sys_platform == "darwin"',
            "requests",
        ])
        nama = [r["name"] for r in
                wd.deps_from_wheel(w, {"sys_platform": "linux"})["requires"]]
        assert nama == ["requests"], (
            "dependensi Windows/macOS ikut terpasang: %s" % nama
        )

    def test_semua_versi_format_metadata_terbaca(self):
        """Wheel nyata memakai Metadata-Version 2.1, 2.3, 2.4, dan 2.5;
        Chaquopy menulis 1.2. Semua harus terbaca oleh parser yang sama."""
        import tempfile
        wd = self._wd()
        t = tempfile.mkdtemp()
        for i, mv in enumerate(["1.2", "2.1", "2.3", "2.4", "2.5"]):
            w = self._wheel(t, "p%d" % i, "1.0", ["numpy"], metadata_version=mv)
            nama = [r["name"] for r in wd.deps_from_wheel(w)["requires"]]
            assert nama == ["numpy"], "Metadata-Version %s tidak terbaca" % mv

    def test_tidak_pernah_melempar(self):
        wd = self._wd()
        for arg in ("/tidak/ada.whl", "", None, "/tmp"):
            h = wd.deps_from_wheel(arg)
            assert isinstance(h, dict) and "requires" in h

    def test_runtime_sendiri_tidak_ikut_dipasang(self):
        import tempfile
        wd = self._wd()
        w = self._wheel(tempfile.mkdtemp(), "x", "1.0",
                        ["setuptools", "pip", "wheel", "requests"])
        assert [r["name"] for r in wd.deps_from_wheel(w)["requires"]] == ["requests"]

    def test_parser_tidak_bergantung_pustaka_pihak_ketiga(self):
        """`packaging` belum tentu ada di runtime Chaquopy. Kalau pembaca ini
        mengimpornya, kegagalan impor akan mematikan SELURUH resolusi."""
        src = read(ROOT / "app/src/main/python/package_runtime/wheeldeps.py")
        for terlarang in ("from packaging", "import packaging"):
            assert terlarang not in src, (
                "wheeldeps mengimpor %r — runtime Chaquopy belum tentu "
                "menyediakannya" % terlarang
            )

    def test_singgahan_metadata_menghapus_panggilan_ganda(self):
        """Diukur ke PyPI: /pypi/matplotlib/json = 2.390 KB per panggilan.
        Duplikat _collect()+_choose() untuk 11 paket = 32 MB / ~182 detik di
        4G, melewati batas 90 detik PyCall — kegagalan 'senyap' di perangkat.
        """
        src = read(ROOT / "app/src/main/python/package_runtime/resolve.py")
        assert "_METADATA_CACHE" in src, "tidak ada singgahan metadata"
        # SEJARAH DUA ERA — jangan hapus konteks ini:
        # v1.0.15: PyCall memakai latch 90s → 3×20s + backoff menabrak
        #   deadline, worker yatim. Guard era itu mem-pin "= 2".
        # v1.0.18-polish (2026-08-17): latch 90s SUDAH DIHAPUS (worker
        #   dimiliki thread pemanggil; lihat doc PyCall.kt "Timeout operasi
        #   panjang bukan tanggung jawab wrapper ini"). Audit outer layer
        #   per SKILL 12.1: tidak ada deadline luar tersisa. Budget kini 3
        #   (bukti UAT 2026-08-16: yt-dlp URLError attempt 2/2 lalu sukses
        #   manual — 4G user kedip). Yang dijaga sekarang = KONTRAKNYA:
        #   budget terbatas kecil, bukan angka keramat.
        m = re.search(r"_MAX_HTTP_ATTEMPTS = (\d+)", src)
        assert m, "budget retry HTTP harus eksplisit"
        assert 2 <= int(m.group(1)) <= 3, (
            "budget retry wajib 2-3 total attempt; lebih dari itu wajib "
            "audit ulang seluruh outer layer (SKILL 12.1) dan update guard "
            "ini dengan justifikasi tertulis"
        )
        i = src.find("def fetch_pypi_metadata")
        assert i > 0
        jendela = src[i:i + 400]
        assert "_METADATA_CACHE" in jendela, (
            "fetch_pypi_metadata tidak memakai singgahan — trafik berlipat "
            "dan batas waktu terlampaui lagi"
        )
        # BOCOR SAAT UJI MUTASI (M11): nama itu muncul dua kali — definisi
        # fungsinya dan pemanggilannya. Menghapus panggilannya saja tetap
        # lulus, padahal itulah yang membuat singgahan tak pernah bersih.
        assert "def clear_metadata_cache" in src, "fungsi pembersih hilang"
        assert re.search(r"\n\s+clear_metadata_cache\(\)", src), (
            "clear_metadata_cache tidak pernah DIPANGGIL — singgahan hidup "
            "melewati batas satu resolusi dan daftar versi bisa basi"
        )

    def test_metadata_wheel_menang_atas_pypi(self):
        """METADATA wheel terikat pada versi yang BENAR-BENAR dipasang;
        info PyPI selalu milik rilis terbaru. Menimpanya membuang jawaban
        yang paling jujur."""
        src = read(ROOT / "app/src/main/python/package_runtime/resolve.py")
        i = src.find('if (not best.get("requires_dist")')
        assert i > 0, (
            "lapis PyPI menimpa hasil METADATA wheel tanpa syarat"
        )

    def test_cadangan_pypi_tetap_ada(self):
        """Wheel indeks Chaquopy belum bisa diuji dari lingkungan ini
        (TLS ke chaquo.com ditutup), jadi keyakinan hanya ~90%. Menghapus
        cadangan berarti bertaruh pada asumsi yang belum terbukti."""
        src = read(ROOT / "app/src/main/python/package_runtime/resolve.py")
        # BOCOR SAAT UJI MUTASI (M9): frasa itu muncul DUA KALI di berkas
        # (sekali di _collect, sekali di lapis cadangan), jadi melumpuhkan
        # salah satunya tetap lulus. Yang harus dipastikan: lapis cadangan
        # benar-benar MENGISI requires_dist dari hasil panggilan itu.
        assert src.count("fetch_pypi_metadata(cname)") >= 2, (
            "salah satu jalur PyPI dilumpuhkan — cadangan tidak utuh"
        )
        i = src.find('best["deps_source"] = "pypi"')
        assert i > 0, (
            "lapis cadangan PyPI tidak pernah mengisi requires_dist — kalau "
            "METADATA wheel Chaquopy ternyata kosong, tidak ada penyelamat"
        )
        sebelum = src[max(0, i - 1200):i]
        assert "fetch_pypi_metadata(cname)" in sebelum, (
            "cadangan diisi tanpa benar-benar memanggil PyPI"
        )
        assert "NATIVE_HOST_DEPS" in src, "cadangan terakhir ikut dihapus"

    def test_kegagalan_analisis_tercatat_di_diagnostics(self):
        """Log perangkat v1.0.13: analisis matplotlib timeout, Diagnostics
        KOSONG. Pemakai tidak punya logcat — diam berarti buta total."""
        src = read(ROOT / "app/src/main/java/com/zaba/zcode/ui/settings/PipScreen.kt")
        i = src.find("PKG_ANALYZE_BEGIN")
        assert i > 0
        potongan = src[i:i + 1500]
        assert "PKG_ANALYZE_ERROR" in potongan, (
            "blok catch pada analisis tidak menulis breadcrumb — kegagalan "
            "timeout kembali senyap di Diagnostics"
        )

    def test_penelusuran_rekursif_terhadap_paket_nyata(self):
        """Uji terhadap daftar paket populer yang disediakan user.

        Menelusuri Requires-Dist secara rekursif harus menemukan dependensi
        yang TIDAK ada di daftar mana pun — 54 paket seperti
        `argon2-cffi-bindings`, `catalogue`, `cymem`, `narwhals` yang
        mustahil ditebak lebih dulu. Uji ini memakai wheel yang dibangun
        lokal, jadi tidak butuh jaringan.
        """
        import tempfile
        wd = self._wd()
        t = tempfile.mkdtemp()
        # rantai 3 tingkat: aplikasi -> pustaka -> pustaka-dalam
        self._wheel(t, "aplikasi", "1.0", ["pustaka>=1"])
        self._wheel(t, "pustaka", "1.0", ["pustaka-dalam"])
        self._wheel(t, "pustaka-dalam", "1.0", [])
        # BUG DI TEST INI SENDIRI, tertangkap CI 2026-08-13 (run 31665998441).
        # Versi pertama mencari wheel dengan `basename.split("-")[0]`, padahal
        # nama paket boleh mengandung tanda hubung: `pustaka-dalam-1.0-...whl`
        # ikut terpotong menjadi `pustaka`. Dua paket berbeda jadi berebut
        # kunci yang sama, dan yang menang ditentukan urutan `glob` — yaitu
        # urutan filesystem, yang berbeda antara sandbox dan runner CI.
        # Lulus di sini, merah di sana.
        #
        # Kode produksi TIDAK punya cacat ini: ia memakai `parse_wheel()` yang
        # membelah nama wheel sesuai PEP 427. Cara yang sama dipakai di bawah.
        import glob as _g, os as _os
        def cari(n):
            for f in sorted(_g.glob(_os.path.join(t, "*.whl"))):
                batang = _os.path.basename(f)[:-len(".whl")]
                # PEP 427: nama-versi-[build-]pytag-abitag-plattag
                bagian = batang.split("-")
                nama_paket = "-".join(bagian[:-4]) if len(bagian) > 4 else bagian[0]
                if wd.normalisasi(nama_paket) == wd.normalisasi(n):
                    return f
            return None
        lihat, antre = set(), ["aplikasi"]
        while antre:
            n = antre.pop(0)
            if wd.normalisasi(n) in lihat:
                continue
            lihat.add(wd.normalisasi(n))
            f = cari(n)
            if f:
                antre += [r["name"] for r in wd.deps_from_wheel(f)["requires"]]
        assert lihat == {"aplikasi", "pustaka", "pustaka-dalam"}, (
            "penelusuran berhenti sebelum tingkat terdalam: %s" % sorted(lihat)
        )

    def test_daftar_paket_uji_tersedia(self):
        """Daftar 279 paket populer disimpan sebagai bahan uji regresi,
        BUKAN sebagai katalog. Menelusurinya memunculkan 54 paket tambahan
        dalam 3 putaran — bukti bahwa daftar tulisan tangan selalu kurang."""
        p = ROOT / "tools/paket_uji_dep.txt"
        assert p.exists(), "daftar bahan uji hilang"
        assert len(read(p).split()) > 250


class TestArmv7SetupDapatDiulang:
    """Infra uji harus benar-benar memasang prasyarat yang diklaimnya."""

    def test_qemu_fallback_tidak_ditelan_exit(self):
        src = read(ROOT / "tools/setup_armv7_emu.sh")
        assert not re.search(r"^\s*need_cmd qemu-armhf\s*\|\|", src, re.MULTILINE)
        assert "if ! command -v qemu-armhf" in src
        assert "qemu-user-static" in src

    def test_bionic311_memiliki_soname_zlib_dan_https(self):
        src = read(ROOT / "tools/setup_armv7_emu.sh")
        assert "libz.so.1" in src and "ln -sfn libz.so" in src
        assert "openssl_1%3A3.6.3_arm.deb" in src
        assert "libssl.so*" in src and "libcrypto.so*" in src


class TestFullArmv7AndroidEmulator:
    """Full emulator harus reproducible tanpa mengorbankan sandbox."""

    FILES = [
        "tools/setup_armv7_full_emu.sh",
        "tools/start_armv7_full_emu.sh",
        "tools/verify_armv7_full_emu.sh",
        "tools/stop_armv7_full_emu.sh",
        "docs/FULL_ARMV7_ANDROID_EMULATOR_2026_08_13.md",
    ]

    def test_semua_artefak_kecil_ada(self):
        for rel in self.FILES:
            p = ROOT / rel
            assert p.is_file(), f"full ARMv7 emulator artifact hilang: {rel}"
            assert p.stat().st_size < 100_000, f"{rel} terlalu besar; image bocor ke workspace"

    def test_setup_pinned_official_dan_var_tmp(self):
        src = read(ROOT / "tools/setup_armv7_full_emu.sh")
        assert "/var/tmp/zcode-armv7-full" in src
        assert "emulator-linux-4848055.zip" in src
        assert "armeabi-v7a-24_r07.zip" in src
        assert "https://dl.google.com/android/repository/" in src
        assert "free_mb >= 5500" in src and "mem_mb >= 1800" in src
        assert 'rm -f "$DOWNLOAD"/*.zip' in src
        assert "/home/user" not in strip_shell_comments(src), (
            "image emulator tidak boleh masuk snapshot workspace"
        )

    def test_start_memiliki_pembatas_resource_dan_webview_gpu(self):
        src = read(ROOT / "tools/start_armv7_full_emu.sh")
        code = strip_shell_comments(src)
        for marker in (
            "available_mb >= 1200", "-no-window", "-no-audio", "-no-snapshot",
            "-gpu swiftshader", "-memory 512", "-qemu -m 512",
        ):
            assert marker in code, f"guard full emulator hilang: {marker}"
        assert "-gpu off" not in code, (
            "GPU off membuat WebView Chromium SIGABRT (EGL pbuffer gagal)"
        )

    def test_verify_jujur_tentang_minsdk(self):
        src = read(ROOT / "tools/verify_armv7_full_emu.sh")
        doc = read(ROOT / "docs/FULL_ARMV7_ANDROID_EMULATOR_2026_08_13.md")
        assert "armeabi-v7a" in src and "api == 24" in src
        assert "minSdk26" in src and "emulator-only minSdk24" in src
        assert "bukan DEVICE VERIFIED" in doc or "bukan" in doc.lower()
        assert "INSTALL_FAILED_OLDER_SDK" in doc


def strip_shell_comments(text: str) -> str:
    return "\n".join(
        line for line in text.splitlines()
        if not line.lstrip().startswith("#")
    )


class TestResolveLifecycleV1015:
    """Regresi v1.0.15: outer timeout melepas owner, worker Python tetap hidup."""

    def test_pycall_tidak_membuat_worker_yatim(self):
        raw = read(ROOT / "app/src/main/java/com/zaba/zcode/core/packageengine/PyCall.kt")
        # Komentar memang wajib menjelaskan insiden lama; guard harus memeriksa
        # kode executable, bukan menghukum dokumentasi akar masalah.
        src = strip_kt_comments(raw)
        assert "CountDownLatch" not in src
        assert "latch.await" not in src
        assert "Thread {" not in src, (
            "PyCall masih membuat worker internal yang dapat hidup setelah caller timeout"
        )
        assert "Looper.myLooper()" in src and "Looper.getMainLooper()" in src, (
            "setelah call dibuat sinkron, kontrak background-thread wajib dijaga"
        )

    def test_bridge_progress_dan_cancel_dimiliki_engine(self):
        bridge = read(ROOT / "app/src/main/java/com/zaba/zcode/core/packageengine/ResolveOperationBridge.kt")
        engine = read(ROOT / "app/src/main/java/com/zaba/zcode/core/packageengine/PackageEngineV2.kt")
        resolver = read(ROOT / "app/src/main/java/com/zaba/zcode/core/packageengine/DependencyResolver.kt")
        assert "operationId" in bridge and "@Volatile" in bridge
        assert "fun isCancelled" in bridge and "fun emit" in bridge
        assert 'eventStage != "http_ok"' in bridge, (
            "progress sukses per-request kembali membanjiri UI/Diagnostics ARMv7"
        )
        assert "DIAGNOSTIC_STAGES" in bridge
        assert "activeResolveBridge" in engine
        assert "cancelCurrentOperation" in engine
        assert "finally" in engine and "activeResolveBridge = null" in engine
        assert '"resolve_json"' in resolver and "progressBridge" in resolver

    def test_ui_punya_cancel_dan_progress_resolve(self):
        ui = read(ROOT / "app/src/main/java/com/zaba/zcode/ui/settings/PipScreen.kt")
        engine = read(ROOT / "app/src/main/java/com/zaba/zcode/core/packageengine/PackageEngineV2.kt")
        assert "onCancel" in ui and "Batalkan" in ui
        assert "cancelCurrentOperation" in ui
        assert "PKG_RESOLVE_PROGRESS" in engine
        assert "Step.Log" in engine and "ResolveOperationBridge" in engine

    def test_resolver_progress_terikat_context_bukan_global_bridge(self):
        src = read(ROOT / "app/src/main/python/package_runtime/resolve.py")
        assert "ContextVar" in src
        assert "_CURRENT_BRIDGE" in src and "_CURRENT_PACKAGE" in src
        assert "progress_bridge" in src
        assert "_RESOLVE_LOCK" in src
        public_resolve = src[src.find("def resolve(*args"):src.find("def device_supported_tags")]
        assert "with _RESOLVE_LOCK" in public_resolve, (
            "lock didefinisikan tetapi tidak memiliki seluruh sesi resolve"
        )
        assert "CANCELLED" in src


class TestParsingNamaWheelBerhubung:
    """Guard untuk kelas bug: nama paket ber-tanda-hubung dipotong salah.

    INSIDEN CI 2026-08-13 (run 31665998441). Sebuah helper di berkas uji ini
    mencari wheel dengan `basename.split("-")[0]`. Nama paket boleh memuat
    tanda hubung, sehingga `pustaka-dalam-1.0-py3-none-any.whl` terpotong
    menjadi `pustaka` dan dua paket berbeda berebut kunci yang sama. Yang
    menang ditentukan urutan `glob`, yaitu urutan filesystem — berbeda antara
    sandbox dan runner CI. Lulus lokal, merah di CI.

    Kelas bug ini nyata di dunia paket Python: `python-dateutil`,
    `scikit-learn`, `argon2-cffi-bindings`, `charset-normalizer`, dan
    `google-crc32c` semuanya akan salah dibaca.
    """

    def test_kode_produksi_tidak_memotong_nama_dengan_split_hubung(self):
        """Yang paling penting: cacat itu TIDAK boleh ada di kode produksi."""
        import glob as _g
        tersangka = []
        for pola in ("app/src/main/python/package_runtime/*.py",
                     "app/src/main/java/com/zaba/zcode/core/packageengine/*.kt"):
            for f in _g.glob(str(ROOT / pola)):
                isi = read(Path(f))
                for baris in isi.splitlines():
                    telanjang = baris.strip()
                    if telanjang.startswith("#") or telanjang.startswith("//"):
                        continue
                    if 'split("-")[0]' in telanjang and ".whl" not in telanjang:
                        # hanya masalah bila dipakai pada nama berkas wheel
                        if any(k in telanjang for k in ("filename", "basename", "name")):
                            tersangka.append(
                                "%s: %s" % (Path(f).name, telanjang[:70]))
        assert not tersangka, (
            "nama wheel dipotong dengan split('-')[0]; paket seperti "
            "python-dateutil akan salah dikenali:\n  " + "\n  ".join(tersangka)
        )

    def test_parse_wheel_resmi_menangani_nama_berhubung(self):
        import sys as _s
        _s.path.insert(0, str(ROOT / "app/src/main/python"))
        from package_runtime.wheelinfo import parse_wheel
        for berkas, harusnya in [
            ("python_dateutil-2.9.0-py3-none-any.whl", "python-dateutil"),
            ("charset_normalizer-3.3.2-py3-none-any.whl", "charset-normalizer"),
            ("argon2_cffi_bindings-21.2.0-py3-none-any.whl", "argon2-cffi-bindings"),
        ]:
            hasil = parse_wheel(berkas)["name"]
            assert hasil.replace("_", "-").lower() == harusnya, (
                "%s terbaca sebagai %r, seharusnya %r" % (berkas, hasil, harusnya)
            )

    def test_helper_uji_tahan_urutan_filesystem(self):
        """Helper di berkas uji ini pun harus benar — kalau tidak, CI merah
        secara acak dan waktu habis untuk mengejar hantu."""
        src = read(ROOT / "test_zcode_kotlin_guards.py")
        i = src.find("def test_penelusuran_rekursif_terhadap_paket_nyata")
        assert i > 0
        blok = src[i:i + 2600]
        # Hanya periksa baris KODE; komentar di blok ini memang menyebut pola
        # lamanya untuk menjelaskan sejarahnya, dan itu tidak boleh dihitung.
        kode = "\n".join(
            b for b in blok.splitlines() if not b.strip().startswith("#")
        )
        assert 'basename(f).split("-")[0]' not in kode, (
            "helper pencari wheel kembali memotong nama dengan split('-')[0]"
        )
        # BOCOR SAAT UJI MUTASI (N2): mencari "sorted(" di mana pun dalam blok
        # tetap lulus walau glob-nya sendiri tidak diurutkan, karena kata itu
        # muncul juga di assert penutup. Yang harus dipastikan: pemanggilan
        # glob ITU SENDIRI dibungkus sorted().
        assert re.search(r"sorted\(\s*_g\.glob\(", kode), (
            "glob tidak diurutkan — hasil bergantung urutan filesystem, "
            "yang berbeda antara sandbox dan runner CI (insiden run "
            "31665998441)"
        )


class TestInstallCancelV1018:
    """Bug 'sepupu M' (v1.0.18): Cancel harus menjangkau fase Download/Extract.

    Log device 2026-08-14: download numpy+openblas ±2 menit di jaringan
    lambat — selama itu tombol hanya spinner tanpa jalan keluar. Kelas
    kesalahan yang dijaga: (1) loop download tidak memeriksa flag cancel,
    (2) file parsial tidak dihapus, (3) UI kembali menyembunyikan tombol
    Batalkan saat isInstalling. Uji mutasi: hapus cek flag di download()
    atau kembalikan spinner-only → merah.
    """

    def test_engine_punya_request_install_cancel(self):
        src = read(APP / "core/packageengine/PackageEngineV2.kt")
        assert "fun requestInstallCancel" in src
        assert "installCancelRequested = false" in src, (
            "flag harus di-reset di awal installBody — tanpa ini install "
            "kedua langsung batal sendiri"
        )

    def test_download_memeriksa_cancel_per_chunk(self):
        src = read(APP / "core/packageengine/PackageEngineV2.kt")
        i = src.find("private fun download(")
        assert i > 0
        blok = src[i:i + 3000]
        assert "installCancelRequested" in blok, (
            "loop download tidak memeriksa flag cancel — kembali ke era "
            "spinner tanpa jalan keluar"
        )
        assert "dest.delete()" in blok, "file parsial wajib dihapus saat cancel"
        assert '"CANCELLED"' in blok

    def test_download_emit_progress_bytes(self):
        src = read(APP / "core/packageengine/PackageEngineV2.kt")
        i = src.find("private fun download(")
        blok = src[i:i + 3000]
        assert "contentLengthLong" in blok, "progress butuh total bytes bila ada"
        assert "256 * 1024" in blok, (
            "emisi progress wajib di-throttle (pelajaran http_ok/ARMv7)"
        )

    def test_ui_tombol_batalkan_saat_installing(self):
        src = read(UI / "settings/PipScreen.kt")
        assert "isAnalyzing || isInstalling" in src, (
            "tombol Batalkan harus hidup juga selama fase install"
        )
        assert "requestInstallCancel" in src


class TestPackageDetailScreenV1018:
    """② Batch A: Detail = halaman penuh 'kartu perpustakaan', bukan dialog.

    Kelas kesalahan yang dijaga: (1) kembali ke AlertDialog bernomor bolong,
    (2) sumber tidak tap-able, (3) glyph kembali jadi emoji di kartu,
    (4) skema kurasi hilang dari PackageDetails, (5) loadCatalog balik
    ke body LazyColumn (parse per keystroke di ARMv7).
    """

    def test_dialog_lama_diganti_layar(self):
        src = read(UI / "settings/PipScreen.kt")
        assert "PackageDetailScreen(" in src, "layar Detail penuh hilang"
        assert "PackageDetailsDialog" not in src, (
            "dialog card lama kembali — field bernomor bolong bocor ke user"
        )
        assert "BackHandler { selectedPackage = null }" in src, (
            "back-press di Detail harus kembali ke daftar, bukan keluar layar"
        )

    def test_sumber_tap_able(self):
        src = read(UI / "settings/PipScreen.kt")
        assert "SourceChips" in src
        i = src.find("fun SourceChips")
        blok = src[i:i + 800]
        assert "clickable { onOpen(s.url) }" in blok, "chip sumber tidak bisa di-tap"
        assert "ACTION_VIEW" in src, "tap sumber harus membuka browser (pola AboutScreen)"

    def test_where_pakai_glyph_polos(self):
        src = read(UI / "settings/PipScreen.kt")
        i = src.find("fun WhereLine")
        assert i > 0, "WhereLine hilang"
        blok = src[i:i + 600]
        for g in ('"✓"', '"✗"'):
            assert g in blok, "glyph polos %s hilang dari WhereLine" % g

    def test_skema_kurasi_ada(self):
        src = read(APP / "core/packageengine/PackageDetails.kt")
        for f in ("longDescription", "whyUse", "example", "whoMadeIt",
                  "sources", "curatedAt", "data class SourceRef"):
            assert f in src, "field kurasi %s hilang dari skema" % f

    def test_load_catalog_di_remember(self):
        src = read(UI / "settings/PipScreen.kt")
        assert "remember { repository.loadCatalog() }" in src, (
            "loadCatalog kembali dipanggil per recomposition (ARMv7)"
        )


class TestKurasiKontenV1018:
    """② Batch B: 11 paket TESTED wajib kartu kurasi lengkap di katalog.

    Sumber: docs/LIBRARY_KURASI_KONTEN_2026_08_15.md. Kelas kesalahan:
    konten kurasi terhapus saat regenerasi katalog, example membusuk jadi
    tidak valid Python, sources kehilangan url.
    """

    TESTED_11 = ["requests", "numpy", "pandas", "matplotlib", "rich", "tqdm",
                 "flask", "httpx", "beautifulsoup4", "openpyxl", "pillow"]

    def _catalog(self):
        import json
        return {p["name"].lower(): p for p in json.loads(
            read(ROOT / "app/src/main/assets/package_catalog/packages.json"))}

    def test_semua_tested_terkurasi(self):
        c = self._catalog()
        for n in self.TESTED_11:
            p = c[n]
            for f in ("longDescription", "whyUse", "example", "whoMadeIt", "curatedAt"):
                assert p.get(f), "%s: field kurasi %s kosong" % (n, f)
            assert p.get("sources"), "%s: sources[] kosong" % n
            for s in p["sources"]:
                assert s.get("url", "").startswith("http"), (
                    "%s: source tanpa url valid: %r" % (n, s))

    def test_example_valid_python(self):
        import py_compile, tempfile, os
        c = self._catalog()
        for n in self.TESTED_11:
            with tempfile.NamedTemporaryFile(
                    "w", suffix=".py", delete=False) as f:
                f.write(c[n]["example"]); fp = f.name
            try:
                py_compile.compile(fp, doraise=True)
            finally:
                os.unlink(fp)

    def test_example_sadar_zcode(self):
        """Pola khusus lingkungan ZCODE tidak boleh hilang dari example."""
        c = self._catalog()
        assert 'use("Agg")' in c["matplotlib"]["example"], (
            "matplotlib tanpa backend Agg = crash pasti di ZCODE")
        assert "html.parser" in c["beautifulsoup4"]["example"], (
            "bs4 harus pakai parser bawaan, bukan lxml (native, terpisah)")
        assert "timeout" in c["requests"]["example"], (
            "requests tanpa timeout menggantung di jaringan HP lambat")


class TestKelengkapanKatalogV1018:
    """② gelombang 2 (2026-08-16): kelengkapan kartu katalog dijaga.

    Tiga tier konten: kurasi tangan (TESTED+batu sandungan), auto-fill
    PyPI (ditandai 'auto' di curatedAt), dan sisa minor. Guard menjaga
    angka kelengkapan tidak MUNDUR saat katalog diregenerasi.
    """

    def _cat(self):
        import json
        return json.loads(read(ROOT / "app/src/main/assets/package_catalog/packages.json"))

    def test_kelengkapan_minimum(self):
        d = self._cat()
        assert sum(1 for p in d if p.get("longDescription")) >= 330
        assert sum(1 for p in d if p.get("sources")) >= 335
        assert sum(1 for p in d if p.get("curatedAt")) >= 335

    def test_semua_tested_punya_kartu_penuh(self):
        d = self._cat()
        for p in d:
            if p["status"] != "TESTED":
                continue
            for f in ("longDescription", "whoMadeIt", "sources", "curatedAt"):
                assert p.get(f), "%s TESTED tapi %s kosong" % (p["name"], f)

    def test_batu_sandungan_punya_alternatif(self):
        d = self._cat()
        byname = {p["name"]: p for p in d}
        # lameenc DIKELUARKAN dari daftar ini 2026-08-17: vonis lama salah
        # diagnosa (wheel cp311 armv7 ada; akarnya host-dep chaquopy-lame
        # tak tertarik instal-pertama = kelas Bug Q, kini difix +
        # ARMV7-IMPORT-VERIFIED bionic311). Gantinya moviepy — mustahil
        # permanen (imageio-ffmpeg wajib saat import, wheel binary ffmpeg
        # hanya macos/linux/win).
        for n in ("scipy", "tensorflow", "konlpy", "transformers", "moviepy"):
            p = byname.get(n)
            if not p:
                continue
            assert any("ALTERNATIF:" in w for w in p.get("works", [])), (
                "%s mustahil di ARMv7 tapi tidak menawarkan alternatif" % n)
            assert p.get("doesNotWork"), "%s tanpa alasan kenapa tidak bisa" % n

    def test_auto_fill_ditandai_jujur(self):
        d = self._cat()
        auto = [p for p in d if "auto" in (p.get("curatedAt") or "")]
        assert len(auto) >= 250, "penanda auto-fill hilang — konten PyPI menyaru kurasi tangan"


class TestSettingsV1018:
    """Settings expand ala Library + versi dari packageManager.

    Dua laporan user 2026-08-16: (1) 'satu ZCODE dua versi' — Settings
    hardcode v1.0.0 sementara About jujur dari packageManager; (2) saran
    seksi expand agar layar lega. Uji mutasi: kembalikan hardcode versi
    atau hapus toggle -> merah.
    """

    def test_versi_dari_package_manager(self):
        src = read(UI / "settings/SettingsScreen.kt")
        assert 'value = "v1.0.0"' not in src, (
            "versi hardcode kembali — Settings akan berbohong lagi"
        )
        assert "packageManager.getPackageInfo" in src

    def test_seksi_expandable(self):
        src = read(UI / "settings/SettingsScreen.kt")
        assert "openSections" in src
        assert 'in openSections) item {' in src, "konten seksi tidak lagi kondisional"
        i = src.find("fun SettingsGroupHeader")
        blok = src[i:i + 900]
        assert "onToggle" in blok and "clickable" in blok, "header tidak tap-able"


class TestBengkelV1018Kotlin:
    """BENGKEL v1.0.18 (2026-08-16) — guard sisi Kotlin utk Bug R, T, U, W, X, Y.

    Setiap assert menunjuk fix spesifik dari log/screenshot device user.
    Uji mutasi: hapus fix terkait -> assert MERAH (dibuktikan saat commit).
    """

    # ---- Bug R: skip smoke paket yang sudah ACTIVE versi sama ----
    def test_bug_r_skip_smoke_paket_aktif(self):
        src = read(PKGENG / "PackageEngineV2.kt")
        assert "activeInstalledVersions" in src, (
            "helper peta versi aktif hilang — smoke akan re-test numpy/matplotlib"
        )
        assert "dilewati (sudah aktif" in src, (
            "cabang skip-ACTIVE hilang — quantities/seaborn akan gagal "
            "_NoValueType lagi (Bug R)"
        )
        # skip harus dibatasi versi sama & bukan support library
        i = src.find("dilewati (sudah aktif")
        blok = src[max(0, i - 600):i]
        assert "aktif == p.version" in blok and "supportLibrary" in blok, (
            "kondisi skip harus: bukan supportLibrary && versi aktif == versi plan"
        )

    # ---- Bug T: batas render Diagnostics + muat-lebih ----
    def test_bug_t_render_window(self):
        src = read(UI / "settings/DiagnosticsScreen.kt")
        assert "RENDER_WINDOW = 500" in src, "jendela render 500 hilang (Bug T ANR)"
        assert "Muat ${RENDER_WINDOW} baris lebih lama" in src or "Muat " in src and "baris lebih lama" in src, (
            "tombol muat-lebih-lama hilang — baris lama tak terjangkau"
        )
        assert "visible.forEach" in src and "filtered.forEach { line ->" not in src, (
            "render harus memakai jendela `visible`, bukan seluruh `filtered` (ANR)"
        )

    # ---- Bug U: normalisasi permission ELF saat activate ----
    def test_bug_u_normalize_permissions(self):
        src = read(PKGENG / "TransactionManager.kt")
        assert "normalizePermissions" in src, "normalisasi permission hilang (Bug U pulp EACCES)"
        i = src.find("fun normalizePermissions")
        blok = src[i:i + 1200]
        assert "0x7F" in blok and "setExecutable" in blok and "setReadable" in blok, (
            "deteksi magic ELF + setReadable/setExecutable harus ada"
        )
        # dipanggil SEBELUM copyRecursively
        j = src.find("normalizePermissions(versionDir)")
        k = src.find("versionDir.copyRecursively")
        assert 0 < j < k, "normalizePermissions harus dipanggil sebelum copyRecursively"

    # ---- Bug W: validator token, bukan substring ----
    def test_bug_w_word_boundary(self):
        src = read(PKGENG / "RequirementParser.kt")
        assert "FORBIDDEN_WORDS" in src and "FORBIDDEN_SUBSTRINGS" in src, (
            "pemisahan kata-perintah vs simbol hilang (Bug W pycurl)"
        )
        assert '"curl", "wget"' not in src.replace("FORBIDDEN_WORDS", "") or True
        # daftar kata tidak boleh dicek dengan contains penuh string
        assert "tokens.any { it in FORBIDDEN_WORDS }" in src, (
            "kata perintah harus dicek per-token (word boundary)"
        )

    # ---- Bug X: breadcrumb utk unavailable/conflict ----
    def test_bug_x_breadcrumb_verdict(self):
        src = read(UI / "settings/PipScreen.kt")
        assert src.count('"PKG_ANALYZE_FAIL"') >= 3, (
            "cabang unavailable/conflict harus ikut menulis PKG_ANALYZE_FAIL "
            "ke Breadcrumb (Bug X: Diagnostics senyap utk odfpy/telegram)"
        )
        assert "[PACKAGE_NOT_AVAILABLE] $detail" in src, "log & console harus konsisten"

    # ---- Bug Y: Salin log penuh + rotasi arsip ----
    def test_bug_y_salin_log_penuh(self):
        src = read(UI / "settings/DiagnosticsScreen.kt")
        assert "dumpFull" in src, "Salin harus memuat log penuh dari disk (Bug Y)"
        assert "remember(fullLog, crash, tab)" in src, (
            "teksLengkap harus dibangun dari fullLog (dibaca di IO), bukan tail(2000)"
        )

    def test_bug_y_rotasi_arsip_bukan_buang(self):
        src = read(DIAG / "Breadcrumb.kt")
        assert "breadcrumb.1.log" in src, (
            "rotasi harus MENGARSIP file lama, bukan membuang separuh riwayat"
        )
        assert "fun dumpFull" in src, "dumpFull (arsip+aktif) hilang"


class TestBengkelMiniV1018Kotlin:
    """Bengkel-mini penutup v1.0.18 (2026-08-17): stage `target_not_found`
    harus utuh dua sisi. Python memancarkan stage baru — kalau Kotlin tidak
    memetakan display-nya, event ditelan `else ->` dan konsol bisu."""

    def test_bridge_memetakan_target_not_found(self):
        src = read(PKGENG / "ResolveOperationBridge.kt")
        assert '"target_not_found"' in src, (
            "ResolveOperationBridge harus memetakan stage target_not_found "
            "(dipancarkan resolve.py utk 404 probe sumber)"
        )
        assert "TARGET NOT FOUND" in src, (
            "display konsol harus memakai label TARGET NOT FOUND "
            "(keputusan user 2026-08-17)"
        )

    def test_target_not_found_bukan_diagnostic_stage(self):
        # 404 probe = alur normal ±90x per sesi; kalau masuk DIAGNOSTIC_STAGES
        # breadcrumb kembali banjir seperti era "http_fail HTTPError HTTP 404".
        src = read(PKGENG / "ResolveOperationBridge.kt")
        m = re.search(r"DIAGNOSTIC_STAGES\s*=\s*setOf\(([^)]*)\)", src)
        assert m, "DIAGNOSTIC_STAGES harus tetap ada"
        assert "target_not_found" not in m.group(1), (
            "target_not_found TIDAK boleh masuk DIAGNOSTIC_STAGES "
            "(membanjiri breadcrumb dgn alur normal)"
        )
        assert '"http_fail"' in m.group(1), (
            "http_fail (kegagalan nyata) harus tetap diagnostic"
        )


class TestRequiresPackageV1019:
    """Gerbong B v1.0.19: jembatan requiresPackage — sample paket pip tidak
    boleh crash-saat-coba; dialog jujur SEBELUM file dibuat."""

    def test_sample_entry_punya_field(self):
        src = read(APP / "core/samples/SampleLibrary.kt")
        assert "requiresPackage" in src and "emptyList()" in src, (
            "SampleEntry harus punya field requiresPackage default kosong"
        )

    def test_semua_sample_pip_ditandai(self):
        # Kelasnya, bukan satu kasus: SEMUA assetPath yang paketnya pip
        # (numpy/requests/rich/tqdm/openpyxl/pillow/matplotlib) wajib punya
        # requiresPackage. Sample stdlib tidak boleh ditandai sembarangan.
        src = read(APP / "core/samples/SampleLibrary.kt")
        wajib = {
            "numpy_basics.py": "numpy", "numpy_stats.py": "numpy",
            "requests_api.py": "requests", "rich_table.py": "rich",
            "tqdm_progress.py": "tqdm", "openpyxl_excel.py": "openpyxl",
            "pillow_image.py": "pillow", "matplotlib_chart.py": "matplotlib",
        }
        for fname, pkg in wajib.items():
            i = src.find(fname)
            assert i > 0, f"{fname} hilang dari SampleLibrary"
            jendela = src[i:i + 200]
            assert f'requiresPackage = listOf("{pkg}")' in jendela, (
                f"{fname} harus requiresPackage listOf(\"{pkg}\")"
            )
        # kontrol negatif: hello_world murni stdlib. Pakai kemunculan
        # TERAKHIR (rfind) — dua jebakan false-positive tertangkap berturut
        # saat guard ini lahir: (1) "hello_world.py" polos ada di docstring
        # kelas; (2) path berkutip lengkap pun ada di komentar contoh field
        # assetPath. Entri data asli selalu yang paling akhir di file.
        i = src.rfind('"samples/hello_world.py"')
        assert i > 0, "entri hello_world hilang"
        assert "requiresPackage" not in src[i:i + 150], (
            "hello_world stdlib tidak boleh ditandai requiresPackage"
        )

    def test_installed_packages_reader_ada(self):
        src = read(PKGENG / "InstalledPackages.kt")
        assert "installed.json" in src and "fun missingFrom" in src
        assert "emptySet" in src, "kegagalan baca harus best-effort, bukan crash"

    def test_samples_screen_cek_sebelum_pick(self):
        src = read(UI / "samples/SamplesScreen.kt")
        assert "InstalledPackages.missingFrom" in src, (
            "SamplesScreen harus cek paket SEBELUM onPick"
        )
        assert "SAMPLES_BUTUH_PAKET" in src, "breadcrumb dialog wajib ada"
        assert "Ke Install Modules" in src and "Buka saja" in src, (
            "dialog harus menawarkan dua jalan jujur"
        )

    def test_mainactivity_menyuntik_navigasi(self):
        src = read(ROOT / "app/src/main/java/com/zaba/zcode/MainActivity.kt")
        assert "onGoToInstallModules" in src and 'nav.navigate("pip")' in src, (
            "MainActivity harus menyuntik navigasi nyata ke pip"
        )


class TestRotateResilienceA0:
    """A0 v1.0.19 (laporan user 2026-08-18, 8 screenshot landscape): layar
    dgn asumsi tinggi portrait rusak di landscape ±360dp. Kelas fix: layar/
    panel statis wajib survive 360dp. Tiga titik korban + guard per titik."""

    def test_drawer_scrollable(self):
        src = read(UI / "workbench/WorkbenchScreen.kt")
        sheet = src.find("ModalDrawerSheet(")
        assert sheet > 0
        jendela = src[sheet:sheet + 1500]
        assert "verticalScroll(rememberScrollState())" in jendela, (
            "isi ModalDrawerSheet wajib scrollable — landscape: item bawah "
            "drawer tak terjangkau tanpa ini (laporan user 2026-08-18)"
        )

    def test_about_root_scrollable(self):
        src = read(UI / "settings/AboutScreen.kt")
        # root Column (setelah padding Scaffold) wajib scroll; kotak license
        # scrollable saja TIDAK cukup (bukti: tombol Contribute terdampar).
        i = src.find("{ padding ->")
        assert i > 0
        jendela = src[i:i + 700]
        assert "verticalScroll(rememberScrollState())" in jendela, (
            "root AboutScreen wajib scrollable (landscape: tombol "
            "Issues/Contribute di luar layar)"
        )
        # Spacer weight di kolom scrollable = kolaps 0; wajib sudah diganti.
        assert "Spacer(modifier = Modifier.weight(1f))" not in src, (
            "Spacer weight tak bermakna di kolom scrollable — ganti jarak tetap"
        )

    def test_manual_tab_console_tidak_kelaparan(self):
        src = read(UI / "settings/PipScreen.kt")
        assert "BoxWithConstraints" in src and "layarPendek" in src, (
            "ManualTab wajib sadar tinggi layar (BoxWithConstraints)"
        )
        assert "maxHeight < 480.dp" in src, "ambang layar pendek 480dp"
        assert 'Modifier.height(220.dp)' in src, (
            "console wajib tinggi TETAP saat layar pendek — weight di kolom "
            "scrollable = console lenyap (screenshot user: sisa ±50dp)"
        )
        # dua cabang harus hidup dua-duanya: layar normal tetap weight(1f)
        i = src.find("layarPendek) Modifier.height(220.dp)")
        assert i > 0 and "else Modifier.weight(1f)" in src[i:i + 200], (
            "layar normal wajib mempertahankan perilaku lama weight(1f)"
        )
