# NumPy — indexing dan slicing array 2D
# Butuh: install numpy

import numpy as np

matriks = np.arange(1, 13).reshape(3, 4)

print("Matriks:\n", matriks)
print("\nBaris kedua:", matriks[1])
print("Kolom terakhir:", matriks[:, -1])
print("Blok 2x2 kiri atas:\n", matriks[:2, :2])
print("Angka genap:", matriks[matriks % 2 == 0])
