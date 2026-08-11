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
