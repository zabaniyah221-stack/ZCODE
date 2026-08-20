# Matplotlib — dua grafik dalam satu gambar
# Butuh: install matplotlib
# Hasil: subplots.png di workspace

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

bulan = ["Jan", "Feb", "Mar", "Apr"]
penjualan = [12, 19, 15, 24]
biaya = [9, 11, 10, 14]

fig, (grafik_1, grafik_2) = plt.subplots(1, 2, figsize=(8, 3.5))
grafik_1.plot(bulan, penjualan, marker="o")
grafik_1.set_title("Penjualan")
grafik_1.grid(alpha=0.3)

grafik_2.bar(bulan, biaya, color="#4C78A8")
grafik_2.set_title("Biaya")

fig.tight_layout()
fig.savefig("subplots.png", dpi=120)
plt.close(fig)
print("Tersimpan: subplots.png")
