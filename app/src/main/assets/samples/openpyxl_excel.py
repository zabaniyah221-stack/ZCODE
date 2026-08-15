# openpyxl — bikin file Excel asli dari HP 😎
# BUTUH: install openpyxl dulu (INSTALL MODULES -> cari "openpyxl")

from openpyxl import Workbook

wb = Workbook()
ws = wb.active
ws.title = "Rekap"
ws.append(["Nama", "Nilai", "Status"])
for nama, nilai in [("Andi", 85), ("Budi", 62), ("Citra", 91)]:
    ws.append([nama, nilai, "LULUS" if nilai >= 70 else "REMEDIAL"])

wb.save("rekap_nilai.xlsx")
print("rekap_nilai.xlsx tersimpan!")
print("Buka lewat app Excel/WPS di HP — file Excel beneran 📊")
