"""zcode_run — runner subprocess minimal ZCODE Desktop v0.0.1.

Beda dari zcode_runner.py (Chaquopy/in-process): skrip ini DIJALANKAN sebagai
proses python3 terpisah oleh Runner.kt via ProcessBuilder.

Protokol: argv[1] = path skrip user. stdout/stderr diteruskan apa adanya
(pemisahan stream oleh panel UI, bukan di sini). stdin diwariskan (input()
interaktif). Exit code = exit code skrip. cwd = folder skrip (import
antar-file natural, open() relatif bekerja).
"""
import runpy
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) < 2:
        print("pakai: zcode_run.py <skrip.py> [arg...]", file=sys.stderr)
        return 2
    target = Path(sys.argv[1]).resolve()
    if not target.is_file():
        print(f"file tidak ada: {target}", file=sys.stderr)
        return 2
    sys.argv = [str(target), *sys.argv[2:]]
    sys.path.insert(0, str(target.parent))
    try:
        runpy.run_path(str(target), run_name="__main__")
    except SystemExit as e:
        code = e.code
        return code if isinstance(code, int) else 0
    except BrokenPipeError:
        return 0
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
