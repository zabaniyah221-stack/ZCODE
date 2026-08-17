# Requests — ambil data dari API internet 🌍
# BUTUH: install requests dulu (INSTALL MODULES -> cari "requests")

import requests

r = requests.get("https://api.github.com/repos/python/cpython", timeout=10)
data = r.json()

print("Status  :", r.status_code)
print("Repo    :", data["full_name"])
print("Bintang :", data["stargazers_count"])
print("Bahasa  :", data["language"])
