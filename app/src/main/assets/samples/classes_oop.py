# Classes (OOP) — cetak biru objek 🏗️

class Siswa:
    def __init__(self, nama, nilai):
        self.nama = nama
        self.nilai = nilai

    def status(self):
        return "LULUS ✓" if self.nilai >= 70 else "REMEDIAL"

    def __repr__(self):
        return f"Siswa({self.nama}, {self.nilai})"

kelas = [Siswa("Andi", 85), Siswa("Budi", 62), Siswa("Citra", 91)]

for s in kelas:
    print(f"{s.nama:8s} nilai={s.nilai:3d}  {s.status()}")

rata = sum(s.nilai for s in kelas) / len(kelas)
print(f"\nRata-rata kelas: {rata:.1f}")
