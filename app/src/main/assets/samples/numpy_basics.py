# Numpy — Array Basics 🧮
# BUTUH: install numpy dulu lewat sidebar → INSTALL MODULES.

import numpy as np

a = np.array([1, 2, 3, 4, 5])
b = np.array([10, 20, 30, 40, 50])

print("Array a :", a)
print("Array b :", b)
print()
print("a + b   :", a + b)      # dijumlahkan per-elemen!
print("a * 3   :", a * 3)      # semua elemen dikali 3
print("a * b   :", a * b)      # perkalian per-elemen
print("a kuadrat:", a ** 2)
print()
print("List biasa nggak bisa gini — ini kekuatan numpy 💪")
