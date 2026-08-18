# cryptography — enkripsi pesan sungguhan (TESTED di ZCODE)
# Butuh: install cryptography

from cryptography.fernet import Fernet

kunci = Fernet.generate_key()
f = Fernet(kunci)

pesan = input("Pesan rahasia: ") or "ZCODE keren"
terkunci = f.encrypt(pesan.encode())

print("\nTerenkripsi :", terkunci.decode()[:60], "...")
print("Kunci       :", kunci.decode())
print("Didekripsi  :", f.decrypt(terkunci).decode())
print("\nTanpa kunci, pesan mustahil dibaca — ini kripto beneran.")
