# Matplotlib — grafik PNG di HP 📉
# BUTUH: install matplotlib dulu (agak besar ±20MB, sabar yaa)

import matplotlib
matplotlib.use("Agg")  # WAJIB di ZCODE: render ke file, bukan window
import matplotlib.pyplot as plt

bulan = ["Jan", "Feb", "Mar", "Apr", "Mei"]
penjualan = [12, 19, 14, 25, 22]

plt.figure(figsize=(6, 3.5))
plt.bar(bulan, penjualan, color="#58a6ff")
plt.title("Penjualan per Bulan")
plt.ylabel("Unit")
plt.tight_layout()
plt.savefig("grafik.png", dpi=100)
print("grafik.png tersimpan! Buka di galeri/file manager 📊")
