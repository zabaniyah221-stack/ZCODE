# PyYAML — baca dan tulis konfigurasi dengan aman
# Butuh: install pyyaml
# Gunakan safe_load untuk data yang bukan buatanmu sendiri.

import yaml

teks_yaml = """
aplikasi: ZCODE
tema: oled
fitur:
  - editor
  - terminal
  - samples
"""

konfigurasi = yaml.safe_load(teks_yaml)
konfigurasi["tema"] = "dark"
konfigurasi["versi_konfigurasi"] = 1

with open("config_hasil.yaml", "w", encoding="utf-8") as file:
    yaml.safe_dump(konfigurasi, file, allow_unicode=True, sort_keys=False)

print("Aplikasi:", konfigurasi["aplikasi"])
print("Fitur:", ", ".join(konfigurasi["fitur"]))
print("Tersimpan: config_hasil.yaml")
