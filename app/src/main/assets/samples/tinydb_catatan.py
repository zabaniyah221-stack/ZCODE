# TinyDB — database catatan berbentuk JSON
# Butuh: install tinydb
# Hasil: catatan_tinydb.json yang tetap ada setelah program selesai

from tinydb import Query, TinyDB

with TinyDB("catatan_tinydb.json") as database:
    catatan = Query()
    database.upsert(
        {"id": 1, "judul": "Belajar ZCODE", "selesai": False},
        catatan.id == 1,
    )
    database.upsert(
        {"id": 2, "judul": "Coba TinyDB", "selesai": True},
        catatan.id == 2,
    )

    print("Semua catatan:")
    for item in database.all():
        tanda = "selesai" if item["selesai"] else "belum"
        print(f"- {item['judul']} ({tanda})")

    print("\nYang selesai:", database.search(catatan.selesai == True))
