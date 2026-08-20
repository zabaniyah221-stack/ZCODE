# qrcode — generator QR code offline (TESTED di ZCODE)
# Butuh: install qrcode. Pillow opsional untuk output PNG.

import qrcode

teks = input("Isi QR (link/teks): ") or "https://github.com/muzape28-blip/ZCODE"

try:
    import PIL  # noqa: F401 — cek apakah backend PNG tersedia
except ModuleNotFoundError:
    from qrcode.image.svg import SvgPathImage

    nama_file = "qr.svg"
    gambar = qrcode.make(teks, image_factory=SvgPathImage)
else:
    nama_file = "qr.png"
    gambar = qrcode.make(teks)

gambar.save(nama_file)
print(f"QR untuk '{teks}' tersimpan: {nama_file}")
