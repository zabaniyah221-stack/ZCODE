# File I/O — tulis & baca file di workspace ZCODE 📄
# File yang dibuat muncul di file manager ZCODE (ikon folder)!

catatan = ["belajar python", "install numpy", "bikin project"]

with open("todo.txt", "w", encoding="utf-8") as f:
    for i, c in enumerate(catatan, 1):
        f.write(f"{i}. {c}\n")
print("todo.txt tersimpan!")

with open("todo.txt", encoding="utf-8") as f:
    print("\nIsi file:")
    print(f.read())

import os
print(f"Ukuran file: {os.path.getsize('todo.txt')} byte")
