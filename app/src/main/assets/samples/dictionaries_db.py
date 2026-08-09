# Dictionaries — database key-value mini pakai dict 🗂️
# Nyimpen data kontak sederhana: nama → nomor.

kontak = {
    "Rani": "0812-3456-7890",
    "Budi": "0813-7777-2222",
    "Sari": "0857-0000-1111",
}

print("SEMUA KONTAK:")
for nama, nomor in kontak.items():
    print(" -", nama, "→", nomor)

print()
print("CARI SATU ORANG:")
nama_dicari = "Budi"
if nama_dicari in kontak:
    print(nama_dicari, "nomornya", kontak[nama_dicari])
else:
    print(nama_dicari, "nggak ada di kontak 😅")

# Tambah & hapus juga gampang:
kontak["Joko"] = "0899-1234-5678"
del kontak["Sari"]
print()
print("Setelah Joko masuk & Sari keluar:", list(kontak.keys()))
