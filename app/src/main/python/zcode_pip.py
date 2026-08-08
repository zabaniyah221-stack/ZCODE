"""
zcode_pip — instalasi package Python via pip in-process (Chaquopy).

Alur: Kotlin memanggil `install_package(bridge, pkg)`.
- stdout/stderr pip dialihkan ke bridge → log streaming di PipScreen
- paket di-install ke site-packages privat aplikasi (offline-first tetap terjaga)
"""
import sys

from zcode_runner import BridgeStdout


def install_package(bridge, pkg):
    old_out, old_err = sys.stdout, sys.stderr
    out = BridgeStdout(bridge)
    sys.stdout = out
    sys.stderr = out
    try:
        from pip._internal.cli.main import main as pip_main
        sys.argv = ["pip", "install", pkg]
        code = pip_main()
        bridge.onExit(code if isinstance(code, int) else 0, None)
    except SystemExit as e:
        code = e.code if isinstance(e.code, int) else (0 if e.code is None else 1)
        bridge.onExit(code, None)
    except BaseException as e:
        import traceback
        bridge.onExit(1, traceback.format_exc())
    finally:
        sys.stdout, sys.stderr = old_out, old_err
