#!/usr/bin/env python3
"""Fake Android netd /dev/socket/dnsproxyd (protokol klien Nougat / API 24).

qemu-user membuka path HOST /dev/socket/dnsproxyd (bukan QEMU_LD_PREFIX).
Tanpa ini, bionic getaddrinfo buta kecuali /system/etc/hosts.
Sumber protokol: platform/bionic nougat-release libc/dns/net/getaddrinfo.c
"""
import os
import socket
import struct
import threading
import traceback

SOCK = os.environ.get("DNSPROXYD_PATH", "/dev/socket/dnsproxyd")


def be32(n: int) -> bytes:
    return struct.pack("!I", n & 0xFFFFFFFF)


def handle(conn: socket.socket) -> None:
    try:
        conn.settimeout(8)
        buf = b""
        while b"\x00" not in buf:
            chunk = conn.recv(4096)
            if not chunk:
                return
            buf += chunk
        req = buf.split(b"\x00", 1)[0].decode("utf-8", "replace")
        parts = req.split()
        if not parts or parts[0] != "getaddrinfo":
            conn.sendall(b"500 ")
            return
        host = parts[1] if len(parts) > 1 else "^"
        serv = parts[2] if len(parts) > 2 else "^"
        if host == "^":
            conn.sendall(b"222 ")
            conn.sendall(be32(0))
            return
        port = 0
        if serv not in ("^", ""):
            try:
                port = int(serv)
            except ValueError:
                try:
                    port = socket.getservbyname(serv)
                except OSError:
                    port = 0
        try:
            infos = socket.getaddrinfo(host, port or None, socket.AF_INET, socket.SOCK_STREAM)
        except OSError:
            conn.sendall(b"500 ")
            return
        conn.sendall(b"222 ")
        seen = set()
        for fam, _typ, _proto, canon, sa in infos:
            if fam != socket.AF_INET:
                continue
            ip = sa[0]
            if ip in seen:
                continue
            seen.add(ip)
            p = sa[1] if len(sa) > 1 else port
            addr = struct.pack("<H", socket.AF_INET) + struct.pack("!H", int(p or 0))
            addr += socket.inet_aton(ip) + b"\x00" * 8
            conn.sendall(be32(1))
            conn.sendall(be32(0))
            conn.sendall(be32(socket.AF_INET))
            conn.sendall(be32(socket.SOCK_STREAM))
            conn.sendall(be32(socket.IPPROTO_TCP))
            conn.sendall(be32(len(addr)))
            conn.sendall(addr)
            name = (canon or host).encode() + b"\x00"
            conn.sendall(be32(len(name)))
            conn.sendall(name)
        conn.sendall(be32(0))
    except Exception:
        traceback.print_exc()
    finally:
        try:
            conn.close()
        except OSError:
            pass


def main() -> None:
    os.makedirs(os.path.dirname(SOCK), exist_ok=True)
    try:
        os.unlink(SOCK)
    except FileNotFoundError:
        pass
    s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    s.bind(SOCK)
    os.chmod(SOCK, 0o777)
    s.listen(32)
    while True:
        c, _ = s.accept()
        threading.Thread(target=handle, args=(c,), daemon=True).start()


if __name__ == "__main__":
    main()
