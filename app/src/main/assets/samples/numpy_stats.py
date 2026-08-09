# Numpy — Quick Stats 📊
# BUTUH: install numpy dulu lewat sidebar → INSTALL MODULES.

import numpy as np

# Nilai ujian satu kelas (20 siswa fiktif)
nilai = np.array([72, 85, 60, 91, 77, 68, 88, 55, 79, 83,
                  90, 66, 74, 81, 95, 58, 70, 86, 63, 78])

print("Jumlah data   :", nilai.size)
print("Rata-rata     :", round(np.mean(nilai), 2))
print("Median        :", np.median(nilai))
print("Nilai tertinggi:", nilai.max())
print("Nilai terendah :", nilai.min())
print("Standar deviasi:", round(np.std(nilai), 2))

lulus = nilai[nilai >= 70]
print()
print("Yang lulus (>= 70):", lulus.size, "siswa")
