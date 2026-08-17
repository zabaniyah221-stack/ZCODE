# Pillow — generate gambar PNG dari kode 🎨
# BUTUH: install pillow dulu (INSTALL MODULES -> cari "pillow")

from PIL import Image, ImageDraw

img = Image.new("RGB", (300, 150), "#0d1117")
d = ImageDraw.Draw(img)
d.rectangle([10, 10, 290, 140], outline="#58a6ff", width=3)
d.text((40, 60), "Dibuat di ZCODE!", fill="#3fb950")
img.save("karya.png")

print("karya.png tersimpan!")
print(f"Ukuran: {img.size[0]}x{img.size[1]} px")
