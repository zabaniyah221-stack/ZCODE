# helper_util.py — modul pendamping Project Mini (A7).
# File biasa di workspace; bisa diimpor file lain karena folder workspace
# ada di sys.path. Edit bebas — Run project_mini*.py untuk melihat efeknya.

def sapa(nama):
    return f"Halo, {nama}! Salam dari modul sebelah."

def urutkan(daftar):
    return sorted(daftar)

def rata_rata(daftar):
    return sum(daftar) / len(daftar) if daftar else 0.0
