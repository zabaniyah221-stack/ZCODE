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
    NYATA = {"numpy": "1.26.2", "pillow": "9.2.0", "matplotlib": "3.6.0"}

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

    def test_paste_hanya_ambil_baris_pertama(self):
        """Menempelkan seluruh requirements.txt ke field satu-baris membuat
        parser gagal dengan pesan yang membingungkan."""
        src = strip_kt_comments(read(UI / "settings/PipScreen.kt"))
        i = src.find('"Paste"')
        jendela = src[max(0, i - 1800):i + 200]
        assert re.search(r"lineSequence\(\)\s*\.\s*firstOrNull", jendela), (
            "isi clipboard dipakai mentah-mentah; kata 'lines()' saja tidak "
            "cukup karena masih muncul di cabang peringatan multi-baris "
            "(bocor uji mutasi 2026-08-13)"
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
        i = src.find('fail("SMOKE_TEST"')
        assert i > 0
        jendela = src[max(0, i - 1200):i + 200]
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
