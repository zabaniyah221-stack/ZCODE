# Web — Fetch JSON dari API publik 🌐
# BUTUH: koneksi internet. Pakai urllib bawaan Python (tanpa install apapun).
import json
import urllib.request

URL = "https://official-joke-api.appspot.com/random_joke"

print("Lagi ngambil joke dari internet...")
try:
    with urllib.request.urlopen(URL, timeout=15) as respon:
        data = json.loads(respon.read().decode("utf-8"))
    print()
    print("Satu joke buat kamu:")
    print("🎤", data["setup"])
    print("😂", data["punchline"])
except Exception as e:
    print("Gagal ngambil data:", e)
    print("Cek koneksi internet kamu ya 📶")
