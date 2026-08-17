# tqdm — progress bar satu baris 📈
# BUTUH: install tqdm dulu (INSTALL MODULES -> cari "tqdm")

import time
from tqdm import tqdm

total = 0
for i in tqdm(range(40), desc="memproses"):
    total += i * i
    time.sleep(0.05)

print("Jumlah kuadrat:", total)
