# Generators — deretan kuadrat tanpa menampung list di memori 🌀
# Generator itu "malas": angka dihitung pas diminta saja (yield).

def kuadrat(maks):
    angka = 1
    while angka <= maks:
        yield angka * angka
        angka += 1


print("10 kuadrat pertama:")
for nilai in kuadrat(10):
    print(nilai, end=" ")
print()

# Generator juga bisa dijumlahkan langsung tanpa list:
total = sum(kuadrat(10))
print("Jumlahnya:", total)
