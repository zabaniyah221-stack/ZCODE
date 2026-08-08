"""
zcode_pip — instalasi package Python via pip in-process (Chaquopy).

Alur: Kotlin memanggil `install_package(bridge, pkg)`.
- pip di-bundle build-time lewat `pip { install("pip==23.3.1") }` di
  app/build.gradle.kts — Chaquopy TIDAK menyertakan pip secara default, jadi
  tanpa blok itu runtime selalu gagal: ModuleNotFoundError: No module named 'pip'
- 3 lapis monkey-patch untuk bug Chaquopy AssetPath
  ("'AssetPath' object has no attribute 'parent'") yang muncul saat
  importlib.metadata / pip memindai distribution di assets (read-only):
    1. pip._internal Environment._iter_distributions → skip dist rusak
    2. importlib.metadata.distributions → skip dist rusak (lapis stdlib)
    3. pip._internal.network.session.user_agent → string statis, supaya pip
       tidak memindai semua dist hanya untuk menyusun header User-Agent
- paket di-install ke <workspace>/user_packages (writable) via --target —
  site-packages bawaan Chaquopy tinggal di assets dan read-only
- stdout/stderr pip dialihkan ke bridge → log streaming di PipScreen
"""
import os
import sys

from zcode_runner import BridgeStdout

_PATCHES_APPLIED = False
USER_AGENT = "zcode-pip/1.0"


# ---------------------------------------------------------------------------
# Monkey-patches (defensif: tiap lapis batal sendiri kalau target tidak ada,
# karena path internal pip beda antar versi)
# ---------------------------------------------------------------------------

def _patch_iter_distributions():
    """Lapis 1: generator pip tahan distribution rusak (AssetPath.parent)."""
    pip_envs = None
    try:
        from pip._internal.metadata.importlib import _envs as pip_envs  # pip 22+
    except Exception:
        try:
            from pip._internal.metadata.importlib import envs as pip_envs  # pip lama
        except Exception:
            return "skipped (module envs tidak ada)"

    env_cls = getattr(pip_envs, "Environment", None)
    orig = getattr(env_cls, "_iter_distributions", None) if env_cls else None
    if orig is None:
        return "skipped (_iter_distributions tidak ada)"
    if getattr(orig, "_zcode_guard", False):
        return "ok (sudah dipatch)"

    def _safe_iter(self):
        it = orig(self)
        while True:
            try:
                yield next(it)
            except StopIteration:
                return
            except AttributeError:
                # AssetPath tidak punya .parent → dist di assets → lewati saja
                continue

    _safe_iter._zcode_guard = True
    env_cls._iter_distributions = _safe_iter
    return "ok"


def _patch_importlib_metadata():
    """Lapis 2: stdlib importlib.metadata.distributions tahan dist rusak."""
    try:
        import importlib.metadata as ilmd
    except Exception:
        return "skipped (module tidak ada)"

    orig = ilmd.distributions
    if getattr(orig, "_zcode_guard", False):
        return "ok (sudah dipatch)"

    def _safe_distributions(**kwargs):
        it = orig(**kwargs)
        while True:
            try:
                yield next(it)
            except StopIteration:
                return
            except AttributeError:
                continue

    _safe_distributions._zcode_guard = True
    ilmd.distributions = _safe_distributions
    return "ok"


def _patch_user_agent():
    """Lapis 3: User-Agent statis — stop pemindaian dist saat bikin session."""
    try:
        from pip._internal.network import session as pip_session
    except Exception:
        return "skipped (module tidak ada)"

    orig = getattr(pip_session, "user_agent", None)
    if orig is None:
        return "skipped (user_agent tidak ada)"
    if getattr(orig, "_zcode_guard", False):
        return "ok (sudah dipatch)"

    def _static_user_agent():
        return USER_AGENT

    _static_user_agent._zcode_guard = True
    pip_session.user_agent = _static_user_agent
    return "ok"


def _apply_patches(bridge):
    global _PATCHES_APPLIED
    if _PATCHES_APPLIED:
        return
    _PATCHES_APPLIED = True
    for label, fn in (
        ("pip_envs.Environment._iter_distributions", _patch_iter_distributions),
        ("importlib.metadata.distributions", _patch_importlib_metadata),
        ("pip_session.user_agent", _patch_user_agent),
    ):
        try:
            result = fn()
            bridge.write("[patch] %s patched (%s)\n" % (label, result))
        except Exception as e:
            bridge.write("[patch] %s skipped: %s\n" % (label, e))


# ---------------------------------------------------------------------------
# Entry point dipanggil Kotlin
# ---------------------------------------------------------------------------

_PIP_MISSING_HINT = (
    "\n\u274c Modul 'pip' tidak ditemukan di runtime Chaquopy.\n"
    "   Chaquopy tidak menyertakan pip secara default — pastikan\n"
    "   app/build.gradle.kts berisi:\n"
    "     chaquopy {\n"
    "       defaultConfig {\n"
    "         pip { install(\"pip==23.3.1\") }\n"
    "       }\n"
    "     }\n"
    "   lalu rebuild APK.\n"
)


def install_package(bridge, pkg):
    workspace = bridge.workspaceDir()
    user_pkg = os.path.join(workspace, "user_packages")
    try:
        os.makedirs(user_pkg, exist_ok=True)
        # import package yang baru di-install langsung bisa di proses ini
        # (runner tetap menyuntikkan path sendiri saat run_script)
        if user_pkg not in sys.path:
            sys.path.insert(0, user_pkg)
    except OSError:
        pass

    old_out, old_err = sys.stdout, sys.stderr
    out = BridgeStdout(bridge)
    sys.stdout = out
    sys.stderr = out
    try:
        bridge.write("[info] workspace: %s\n" % workspace)
        bridge.write("[info] target: %s\n" % user_pkg)
        _apply_patches(bridge)

        argv = [
            "pip", "install",
            "--no-input",
            "--disable-pip-version-check",
            "--target", user_pkg,
            "--upgrade",
            pkg,
        ]
        bridge.write("[cmd] %s\n\n" % " ".join(argv))

        try:
            from pip._internal.cli.main import main as pip_main
        except ModuleNotFoundError as e:
            if e.name == "pip" or (e.name and e.name.startswith("pip.")):
                bridge.write(_PIP_MISSING_HINT)
                bridge.onExit(1, None)
                return
            raise

        sys.argv = argv
        code = pip_main(argv[1:])
        code = code if isinstance(code, int) else 0
        if code == 0:
            import importlib
            importlib.invalidate_caches()
            bridge.write("\n[ok] %s installed to %s\n" % (pkg, user_pkg))
        bridge.onExit(code, None)
    except SystemExit as e:
        code = e.code if isinstance(e.code, int) else (0 if e.code is None else 1)
        bridge.onExit(code, None)
    except BaseException:
        import traceback
        bridge.onExit(1, traceback.format_exc())
    finally:
        sys.stdout, sys.stderr = old_out, old_err
