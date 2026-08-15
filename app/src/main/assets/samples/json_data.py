# JSON — bahasa universal pertukaran data 🌐

import json

profil = {
    "nama": "ZCODE User",
    "hp": "Infinix Smart 9 HD",
    "bahasa": ["python"],
    "level": 7,
}

teks = json.dumps(profil, indent=2, ensure_ascii=False)
print("Python dict -> JSON:")
print(teks)

balik = json.loads(teks)
print(f"\nJSON -> dict lagi: level = {balik['level']}")
balik["level"] += 1
print(f"Naik level! -> {balik['level']} 🎉")
