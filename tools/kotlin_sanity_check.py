#!/usr/bin/env python3
"""Kotlin lexical sanity check — ZCODE.

Kenapa script ini ada (insiden CI 2026-08-09, WorkbenchScreen.kt:1100:1):
Kotlin block comment itu BERSARANG (nested) — berbeda dengan Java/C.
Artinya urutan pembuka comment apa pun yang tertulis APA ADANYA di dalam
sebuah block comment (contoh nyata: glob MIME yang kami tulis mentah di
doc comment, "text/" diikuti bintang) akan MINBUKA comment bersarang baru.
Penutup asli `*/` kemudian hanya menutup comment nested tersebut, sehingga
comment luar tetap nganga sampai EOF → compiler error "Unclosed comment".

Tes structural lokal kami (grep-based) buta terhadap ini; script ini lexer
mini (bukan parser penuh) yang memahami:
  - string "..." beserta escape (\\, \", \n, dst.)
  - raw string tiga-kutip \"\"\"...\"\"\"
  - char literal '.'
  - line comment // ...
  - block comment /* ... */ DENGAN penghitung kedalaman (nesting)

Gagal (exit 1) bila: EOF tercapai di dalam block comment / string / raw
string, atau string/char single-line menemui newline sebelum tertutup.

Batas jujur (hukum #1): ekspresi template "${ ... }" DI DALAM string tidak
di-lex sebagai kode — bila suatu hari ada comment di dalam template, lexer
ini tidak melihatnya (false-negative, bukan false-positive). Praktiknya
konstruksi seperti itu tidak ada di codebase ini.
"""

from __future__ import annotations

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
KT_ROOT = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "java"))

NORMAL, STRING, RAW, CHAR, LINE, BLOCK = range(6)


def scan(path: str) -> list[str]:
    """Kembalikan daftar pesan error leksikal untuk satu file .kt."""
    with open(path, "r", encoding="utf-8") as fh:
        src = fh.read()

    errs: list[str] = []
    state = NORMAL
    depth = 0                      # kedalaman block comment (Kotlin nesting!)
    opener_line = 0                # baris tempat state saat ini dibuka
    line, col = 1, 0
    i, n = 0, len(src)

    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        nxt2 = src[i + 2] if i + 2 < n else ""
        if c == "\n":
            line += 1
            col = 1
        else:
            col += 1

        if state == NORMAL:
            if c == "/" and nxt == "/":
                state = LINE
                i += 2
                col += 1
                continue
            if c == "/" and nxt == "*":
                state = BLOCK
                depth = 1
                opener_line = line
                i += 2
                col += 1
                continue
            if c == '"' and nxt == '"' and nxt2 == '"':
                state = RAW
                opener_line = line
                i += 3
                col += 2
                continue
            if c == '"':
                state = STRING
                opener_line = line
                i += 1
                continue
            if c == "'":
                state = CHAR
                i += 1
                continue
            i += 1
            continue

        if state == LINE:
            if c == "\n":
                state = NORMAL
            i += 1
            continue

        if state == BLOCK:
            if c == "/" and nxt == "*":
                depth += 1          # nested!
                i += 2
                col += 1
                continue
            if c == "*" and nxt == "/":
                depth -= 1
                i += 2
                col += 1
                if depth == 0:
                    state = NORMAL
                continue
            i += 1
            continue

        if state == STRING:
            if c == "\\":
                if nxt == "\n":
                    line += 1
                i += 2
                col += 1
                continue
            if c == "\n":
                errs.append(f"{path}: string literal tidak tertutup sebelum baris {line} (dibuka baris {opener_line})")
                state = NORMAL
                i += 1
                continue
            if c == '"':
                state = NORMAL
            i += 1
            continue

        if state == RAW:
            if c == '"' and nxt == '"' and nxt2 == '"':
                state = NORMAL
                i += 3
                col += 2
                continue
            i += 1
            continue

        if state == CHAR:
            if c == "\\":
                i += 2
                col += 1
                continue
            if c == "\n":
                errs.append(f"{path}: char literal tidak tertutup sebelum baris {line}")
                state = NORMAL
                i += 1
                continue
            if c == "'":
                state = NORMAL
            i += 1
            continue

    if state == BLOCK:
        errs.append(
            f"{path}: BLOCK COMMENT TIDAK TERTUTUP (dibuka baris {opener_line}, "
            f"kedalaman sisa {depth} saat EOF). Ingat: Kotlin block comment bersarang — "
            f"glob bintang mentah (mis. MIME teks-slash-bintang) yang ditulis apa adanya "
            f"di dalam doc comment ikut membuka comment nested!"
        )
    elif state == RAW:
        errs.append(f"{path}: raw string tidak tertutup (dibuka baris {opener_line})")
    elif state == STRING:
        errs.append(f"{path}: string tidak tertutup sampai EOF (dibuka baris {opener_line})")
    return errs


def main() -> int:
    all_errs: list[str] = []
    count = 0
    for root, _dirs, files in os.walk(KT_ROOT):
        for name in sorted(files):
            if name.endswith(".kt"):
                count += 1
                all_errs.extend(scan(os.path.join(root, name)))
    if all_errs:
        print("❌ Kotlin lexical sanity GAGAL:")
        for e in all_errs:
            print("   " + e)
        return 1
    print(f"✅ {count} file Kotlin lolos lexical sanity (block comment nesting + string seimbang)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
