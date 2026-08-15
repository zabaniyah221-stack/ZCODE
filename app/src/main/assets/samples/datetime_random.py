# Datetime & Random — dua modul stdlib paling sering dipakai 🎲🕐

import random
from datetime import datetime, timedelta

sekarang = datetime.now()
print("Sekarang :", sekarang.strftime("%A, %d %B %Y %H:%M"))
print("7 hari lg:", (sekarang + timedelta(days=7)).strftime("%d %B %Y"))

print("\nLempar dadu 5x:", [random.randint(1, 6) for _ in range(5)])
peserta = ["Andi", "Budi", "Citra", "Dewi"]
print("Pemenang undian:", random.choice(peserta), "🏆")
