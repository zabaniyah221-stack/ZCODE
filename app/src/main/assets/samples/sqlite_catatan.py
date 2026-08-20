# sqlite3 (BAWAAN Python — tanpa install!) — database catatan persisten
# Data bertahan walau app ditutup: file catatan.db di workspace

import sqlite3

db = sqlite3.connect("catatan.db")
db.execute("CREATE TABLE IF NOT EXISTS catatan (id INTEGER PRIMARY KEY, isi TEXT)")

isi = input("Catatan baru (kosong = lihat saja): ").strip()
if isi:
    db.execute("INSERT INTO catatan (isi) VALUES (?)", (isi,))
    db.commit()
    print("Tersimpan!")

print("\n=== Semua catatan ===")
for cid, teks in db.execute("SELECT id, isi FROM catatan"):
    print(f"{cid}. {teks}")
db.close()
