# Code Health Checkup — kenalan dengan fitur "editor paham Python" 🩺
# v1.0.23: ZCODE kini memeriksa kode SEBELUM di-Run. File ini sengaja
# dibuat sehat & bersih — semua eksperimen di bawah aman dicoba tanpa
# install apa pun. (Opsional: pasang pack "Editor Intelligence" via
# INSTALL MODULES untuk pemeriksaan semantik penuh oleh pyflakes.)


def sapa(nama):
    """Sapa user — fungsi paling sederhana (complexity 1)."""
    return f"Halo, {nama}!"


def kategori_nilai(nilai):
    """Ubah angka nilai jadi grade — punya beberapa cabang."""
    if nilai >= 90:
        return "A"
    elif nilai >= 80:
        return "B"
    elif nilai >= 70:
        return "C"
    return "D"


def analisis_daftar(angka):
    """Statistik ringkas isi list — sedikit lebih berkelok."""
    if not angka:
        return {"kosong": True}
    tertinggi = angka[0]
    for a in angka:
        if a > tertinggi:
            tertinggi = a
    return {
        "jumlah": len(angka),
        "total": sum(angka),
        "rata_rata": sum(angka) / len(angka),
        "tertinggi": tertinggi,
    }


print(sapa("Kode Sehat"))
print("Grade nilai 87:", kategori_nilai(87))
print("Analisis [7, 3, 9, 5]:", analisis_daftar([7, 3, 9, 5]))

# ── EKSPERIMEN (pilih satu, lalu lihat banner Problems di atas editor) ──
# 1. Hapus satu tanda kutip pada baris print di atas → banner merah
#    muncul SENDIRINYA bahkan sebelum kamu menekan Run
#    (pemeriksa string/kurung/indent bawaan — tanpa pack).
# 2. Di fungsi kategori_nilai, ganti kata `nilai` jadi `nilia` di satu
#    tempat saja. Dengan pack Editor Intelligence: pyflakes langsung
#    menandai "undefined name 'nilia'" SEBELUM Run. Tanpa pack: tetap
#    ketahuan saat Run sebagai NameError — beda lapisan, sama jujurnya.
# 3. Buka Command Palette (🔍) → "Complexity Report (mccabe)" → lihat
#    skor kompleksitas tiap fungsi di file ini; tap nama fungsi untuk
#    lompat ke barisnya. Fungsi yang terlalu berkelok ditandai "!".
# 4. Selesai bereksperimen? Undo (↩) mengembalikan semuanya.
