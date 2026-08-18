# qrcode — generator QR code offline (TESTED di ZCODE)
# Butuh: install qrcode (otomatis menarik pillow)

import qrcode

teks = input("Isi QR (link/teks): ") or "https://github.com/muzape28-blip/ZCODE"
img = qrcode.make(teks)
img.save("qr.png")
print(f"QR untuk '{teks}' tersimpan: qr.png")
