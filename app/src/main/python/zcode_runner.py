"""
zcode_runner — runner dalam proses Chaquopy untuk ZCODE.

Alur: Kotlin memanggil `run_script(bridge, script_path)`.
- sys.stdout / sys.stderr diarahkan ke bridge (muncul di Terminal UI)
- sys.stdin diganti BridgeStdin: input() membaca baris dari queue Kotlin
  (ketik langsung + Enter, tanpa tombol Send — sesuai keputusan tim)
- Ctrl+C: bridge.isInterrupted() dicek BridgeStdin → KeyboardInterrupt
  deterministik untuk script yang sedang nge-blok di input()
- cwd = folder workspace, jadi plt.savefig("out.png") / open("data.txt")
  relatif bekerja seperti di desktop
"""
import os
import runpy
import sys
import threading


class BridgeStdout:
    """Alihkan print()/traceback ke Terminal UI lewat bridge Java."""

    def __init__(self, bridge):
        self._bridge = bridge

    def write(self, s):
        if s:
            self._bridge.write(str(s))
        return len(s)

    def flush(self):
        pass

    def isatty(self):
        return True


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
            chunk = self._bridge.readLine()
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


def run_script(bridge, script_path):
    try:
        # cwd = workspace filesDir → path relatif script berfungsi
        try:
            os.chdir(bridge.workspaceDir())
        except OSError:
            pass
        sys.stdin = BridgeStdin(bridge)
        sys.stdout = BridgeStdout(bridge)
        sys.stderr = BridgeStdout(bridge)
        sys.path.insert(0, bridge.workspaceDir())

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
