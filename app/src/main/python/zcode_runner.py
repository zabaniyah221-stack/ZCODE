"""
zcode_runner — runner dalam proses Chaquopy untuk ZCODE.

Alur: Kotlin memanggil `run_script(bridge, script_path)`.
- sys.stdout / sys.stderr diarahkan ke bridge (muncul di Terminal UI),
  DIBEDAKAN stream-nya (SPEC-001 Phase 0: "Separate stdout/stderr"):
  stdout → bridge.write(s, "out"), stderr → bridge.write(s, "err")
- sys.stdin diganti BridgeStdin: input() membaca baris dari queue Kotlin
  (ketik langsung + Enter, tanpa tombol Send — sesuai keputusan tim)
- Ctrl+C: bridge.isInterrupted() dicek BridgeStdin → KeyboardInterrupt
  deterministik untuk script yang sedang nge-blok di input()
- cwd = folder workspace, jadi plt.savefig("out.png") / open("data.txt")
  relatif bekerja seperti di desktop
- sys.path menyertakan workspace + workspace/user_packages (legacy pip
  install) + python-env (PackageEngineV2, lihat package_runtime.envpaths)
- bridge.waitingInput(True/False) memberitahu Kotlin state WAITING_FOR_INPUT
  (SPEC-001: process lifecycle eksplisit)
"""
import os
import runpy
import socket
import sys
import threading

# Script user SELALU berjalan di background thread (main thread Chaquopy =
# UI thread Android). Tanpa shim ini, `import pycurl`/paket lain yang
# memanggil signal.signal() mati ValueError "signal only works in main
# thread" — bukti device 2026-08-17. Idempoten; smoke.py memasang shim yang
# sama untuk jalur smoke test. Desain: package_runtime/signalshim.py.
from package_runtime import signalshim

signalshim.install()

# Batas waktu default untuk SEMUA koneksi jaringan yang dibuat script user
# (urllib.request, http.client, requests, socket) — fix 2026-08-12.
#
# Kenapa perlu: hard timeout 120 detik pada session interaktif sudah dihapus
# (SPEC-001 §17) karena dulu ia membunuh script yang menunggu input() — script
# chat AI user berhenti sendiri dengan "exit code 0" setelah ~2 menit. Efek
# sampingnya, script yang menunggu server yang tidak pernah menjawab kini bisa
# menggantung SELAMANYA tanpa ada yang menyelamatkan.
#
# Ini memutus koneksi yang mati, BUKAN membunuh script: script tetap berjalan
# dan menerima socket.timeout yang bisa ditangani sendiri. Script boleh
# menimpanya kapan saja dengan socket.setdefaulttimeout() atau argumen
# timeout= per panggilan.
DEFAULT_SOCKET_TIMEOUT_S = 30.0


class BridgeStdout:
    """Alihkan print()/traceback ke Terminal UI lewat bridge Java (stream 'out')."""

    def __init__(self, bridge, stream="out"):
        self._bridge = bridge
        self._stream = stream

    def write(self, s):
        if s:
            self._bridge.write(str(s), self._stream)
        return len(s)

    def flush(self):
        pass

    def isatty(self):
        return True


class BridgeStderr(BridgeStdout):
    """Stream 'err' — dipakai sys.stderr supaya log bisa membedakan stderr."""

    def __init__(self, bridge):
        super().__init__(bridge, stream="err")


class BridgeStdin:
    """input() membaca baris dari antrian Kotlin; bisa di-interupsi."""

    def __init__(self, bridge):
        self._bridge = bridge
        self._buffer = ""

    def readline(self, limit=-1):
        # polling terus sampai: ada baris dari Kotlin, atau interrupt (Ctrl+C)
        while "\n" not in self._buffer:
            if self._bridge.isInterrupted():
                raise KeyboardInterrupt
            self._bridge.waitingInput(True)
            chunk = self._bridge.readLine()
            self._bridge.waitingInput(False)
            if chunk is None:
                # tidak ada input & belum di-interupsi → tetap menunggu (bukan EOF palsu)
                continue
            self._buffer += chunk
        line, self._buffer = self._buffer.split("\n", 1)
        if limit >= 0:
            line = line[:limit]
        return line + "\n"

    def read(self, size=-1):
        line = self.readline()
        if size >= 0:
            line = line[:size]
        return line

    def isatty(self):
        return True


def _activate_package_env(bridge):
    """Aktivasi package dari PackageEngineV2 (python-env) + legacy user_packages."""
    try:
        from package_runtime.envpaths import activate
        activate(bridge.workspaceDir())
    except Exception:
        # kalau package_runtime belum tersedia (dev), lewati tanpa crash
        pass


def run_script(bridge, script_path):
    try:
        # cwd = workspace filesDir → path relatif script berfungsi
        try:
            os.chdir(bridge.workspaceDir())
        except OSError:
            pass
        # Jaring pengaman jaringan (lihat DEFAULT_SOCKET_TIMEOUT_S di atas).
        try:
            if socket.getdefaulttimeout() is None:
                socket.setdefaulttimeout(DEFAULT_SOCKET_TIMEOUT_S)
        except Exception:
            pass

        sys.stdin = BridgeStdin(bridge)
        sys.stdout = BridgeStdout(bridge, stream="out")
        sys.stderr = BridgeStderr(bridge)
        sys.path.insert(0, bridge.workspaceDir())
        # user_packages: target `pip install` legacy (--target <workspace>/user_packages)
        user_pkg = os.path.join(bridge.workspaceDir(), "user_packages")
        if user_pkg not in sys.path:
            sys.path.insert(0, user_pkg)
        # python-env: package hasil PackageEngineV2
        _activate_package_env(bridge)

        # best-effort: daftarkan thread worker agar Kotlin bisa coba interrupt()
        worker = threading.current_thread()
        try:
            bridge.setWorkerThread(worker)
        except Exception:
            pass

        runpy.run_path(script_path, run_name="__main__")
        bridge.onExit(0, None)
    except SystemExit as e:
        code = e.code if isinstance(e.code, int) else (0 if e.code is None else 1)
        bridge.onExit(code, None)
    except KeyboardInterrupt:
        # Ctrl+C: exit code 130 ala shell, tanpa traceback berisik
        bridge.onExit(130, None)
    except BaseException as e:
        import traceback
        bridge.onExit(1, traceback.format_exc())
