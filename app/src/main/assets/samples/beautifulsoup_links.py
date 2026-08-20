# Beautiful Soup — ambil judul dan link dari HTML offline
# Butuh: install beautifulsoup4

from bs4 import BeautifulSoup

html = """
<article>
  <h1>Belajar Python dari HP</h1>
  <p>Pilih materi:</p>
  <a href="/dasar">Dasar Python</a>
  <a href="/data">Data dan Grafik</a>
</article>
"""

soup = BeautifulSoup(html, "html.parser")
print("Judul:", soup.h1.get_text(strip=True))
print("Daftar link:")
for link in soup.find_all("a"):
    print("-", link.get_text(strip=True), "->", link.get("href"))
