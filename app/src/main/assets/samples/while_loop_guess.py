# While Loop — Game Tebak Angka 1-100 🎯
# ZCODE mikirin satu angka, kamu tebak terus sampai bener.
# Game ini butuh input() — terminal ZCODE dukung penuh. Selamat bermain!
import random

target = random.randint(1, 100)
percobaan = 0

print("Aku sudah mikirin angka 1 sampai 100...")
print("Coba tebak! (ketik 0 buat nyerah)")

while True:
    tebakan = input("Tebakan kamu: ")

    if not tebakan.isdigit():
        print("Itu bukan angka, coba lagi ya 😅")
        continue

    tebakan = int(tebakan)
    if tebakan == 0:
        print("Nyerah ya? Angkanya", target, "kok 😝")
        break

    percobaan += 1
    if tebakan < target:
        print("Kecil banget... naikin dong ⬆️")
    elif tebakan > target:
        print("Kebanyakan hehe, turunin ⬇️")
    else:
        print("BENER! 🎉 Angkanya", target)
        print("Kamu nebak dalam", percobaan, "percobaan. GG!")
        break
