# Try / Except — menangkap error tanpa membuat program mati 🛡️
# Pelajaran Python paling penting setelah loop!

angka_str = input("Ketik sebuah angka: ")

try:
    angka = float(angka_str)
    hasil = 100 / angka
    print(f"100 dibagi {angka} = {hasil:.2f}")
except ValueError:
    print(f"'{angka_str}' itu bukan angka njiir 😅")
except ZeroDivisionError:
    print("Membagi dengan nol? Alam semesta menolak. 🌌")
finally:
    print("Blok finally SELALU jalan — cocok buat beres-beres.")
