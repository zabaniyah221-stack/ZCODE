# HTTPX — ambil JSON dari API dengan timeout
# Butuh: install httpx dan koneksi internet

import httpx

url = "https://httpbin.org/get"

try:
    respons = httpx.get(url, timeout=10.0, follow_redirects=True)
    respons.raise_for_status()
    data = respons.json()
    print("Status:", respons.status_code)
    print("URL dari server:", data.get("url"))
    print("Origin:", data.get("origin"))
except httpx.HTTPError as error:
    print("Request gagal:", error)
