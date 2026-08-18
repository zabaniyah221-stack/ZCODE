# Project Mini — bukti multi-file import di ZCODE (A7 v1.0.19)
# File ini mengimpor helper_util.py yang ada DI SEBELAHNYA (workspace).
# Multi-file bukan fitur baru: workspace sudah masuk sys.path sejak dulu —
# sample ini meresmikannya supaya kamu tahu polanya.
#
# Coba: ubah fungsi di helper_util.py, lalu Run lagi file ini.

import helper_util

print("=== Project Mini: dua file, satu program ===")
print(helper_util.sapa("ZCODE"))

angka = [3, 1, 4, 1, 5, 9, 2, 6]
print(f"data     : {angka}")
print(f"terurut  : {helper_util.urutkan(angka)}")
print(f"rata-rata: {helper_util.rata_rata(angka):.2f}")
