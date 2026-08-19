# PyOTP — membuat dan memverifikasi kode TOTP untuk belajar 2FA
# Butuh: install pyotp
# Jangan cetak atau simpan secret asli seperti contoh ini di aplikasi produksi.

import pyotp

secret_demo = pyotp.random_base32()
totp = pyotp.TOTP(secret_demo)
kode = totp.now()

print("Secret DEMO:", secret_demo)
print("Kode saat ini:", kode)
print("Verifikasi:", totp.verify(kode))
print("Kode berubah setiap 30 detik dan seharusnya hanya jadi faktor kedua.")
