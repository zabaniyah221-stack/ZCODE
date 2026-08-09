# For Loop — hitung faktorial pakai perulangan for 🔁
# 5! = 5 × 4 × 3 × 2 × 1 = 120

n = 5
hasil = 1

for angka in range(1, n + 1):
    hasil = hasil * angka
    print("Langkah", angka, "→ sementara:", hasil)

print()
print("Jadi", str(n) + "!", "=", hasil)
