# 🔍 Kenapa ZABACODE tidak pernah kena kasus ini, padahal "fungsinya sama"

Pertanyaan user, 2026-08-13. Dijawab dengan membaca kode ZABACODE
(`zabacode/lib_manager.py`, 531 baris) dan menjalankan ulang logikanya.

**Jawaban singkat: fungsinya TIDAK sama.** ZABACODE tidak punya resolver.
Semua bug ZCODE hidup di lapisan yang ZABACODE tidak pernah bangun.

---

## 1. Sisi ZABACODE: 12 baris, tanpa resolver

Inti installer ZABACODE (`_fallback_pypi_download`, disederhanakan):

```python
data = json.load(urlopen(f"https://pypi.org/pypi/{name}/json"))

for item in data.get("urls", []):                       # ← kunci #1
    if item["filename"].lower().endswith("none-any.whl"):  # ← kunci #2
        target_wheel_url = item["url"]
        break
else:
    return False, "requires a compiled C-extension, add to buildozer.spec"
```

Dua keputusan itu membuatnya kebal terhadap keempat bug ZCODE.

### Kunci #1 — `data["urls"]`, bukan `data["releases"]`

Di PyPI JSON API:

| Field | Isi |
|---|---|
| `urls` | file untuk **versi terbaru saja** |
| `releases` | **seluruh riwayat**, dari rilis pertama sampai sekarang |

ZABACODE memakai `urls`. Versi terbaru sudah dipilihkan oleh PyPI, jadi
**tidak ada yang perlu diurutkan, tidak ada yang perlu difilter.**

ZCODE memakai `releases` → menerima ratusan versi dari 2013 sampai 2026 →
harus memfilter dan mengurutkan sendiri → dan **kedua langkah itu ditulis salah**.

### Kunci #2 — filter berdasarkan NAMA FILE, bukan tag

`endswith("none-any.whl")` adalah pemeriksaan string sederhana. Tidak menyentuh
`packaging.tags`, tidak memanggil `sys_tags()`, tidak peduli ABI perangkat.

Untuk wheel pure-Python hasilnya **selalu benar**, karena `none-any` memang
berarti "jalan di mana saja".

---

## 2. Empat bug ZCODE, diperiksa satu per satu terhadap ZABACODE

| # | Bug ZCODE | Kena ZABACODE? | Sebab |
|---|---|---|---|
| A | `requires_python` dibandingkan dengan versi **paket** | ❌ tidak | tidak pernah membaca `requires_python` |
| B | `optString` → `""` bukan `null` → `File("")` | ❌ tidak | Python murni, tidak ada JSON bridge Kotlin↔Python |
| C | `stdlib.json` tidak pernah dibaca resolver | ❌ tidak | stdlib ada di katalog manual + `is_package_installed()` mengecek `__import__` lebih dulu |
| D | pilih wheel diurutkan **alfabetis** | ❌ tidak | tidak pernah mengurutkan; `urls` hanya berisi versi terbaru |

**4 dari 4 tidak berlaku.** Bukan karena ZABACODE lebih benar, tapi karena
lapisan tempat bug-bug itu hidup **tidak pernah ada di ZABACODE**.

---

## 3. Bukti eksekusi

Logika ZABACODE dijalankan ulang persis (2026-08-13):

| Paket | ZABACODE | ZCODE sekarang |
|---|---|---|
| colorama | **0.4.6** ✅ | 0.3.5 (2015) |
| requests | **2.34.2** ✅ | 2.0.0 (2013) |
| urllib3 | **2.7.0** ✅ | 1.11 |
| pygments | **2.20.0** ✅ | 2.0 |
| mdurl | **0.1.2** ✅ | GAGAL TOTAL |
| rich | **15.0.0** ✅ | gagal (dep) |
| numpy | ❌ "add to buildozer.spec" | ❌ gagal |
| pyyaml | ❌ "add to buildozer.spec" | ❌ gagal |

ZABACODE benar di **semua** paket pure-Python. Persis seperti yang user alami.

---

## 4. Harga yang dibayar ZABACODE

Ini bukan cerita "ZABACODE lebih baik". Kesederhanaan itu ada ongkosnya, dan
justru **ZCODE dibuat untuk memperbaiki ongkos-ongkos ini**:

1. **Tidak ada resolusi dependensi.** `install rich` hanya mengunduh `rich`.
   `markdown-it-py` dan `pygments` — dua dependensi wajibnya — **tidak ikut**.
   Install dilaporkan "berhasil", lalu `import rich` gagal saat dijalankan.
   ZCODE benar secara desain di sini: ia memang menemukan `mdurl`+`pygments`.

2. **Tidak ada paket native sama sekali.** numpy, pandas, pillow, matplotlib
   semuanya ditolak dengan "add to buildozer.spec & rebuild the APK" — yang
   **mustahil dilakukan user tanpa PC**. Di ZCODE paket-paket itu berpeluang
   hidup lewat wheel Chaquopy (lihat `ARMV7_COMPAT_2026_08_13.md`).

3. **Tidak bisa memilih versi.** `install rich==13.7.1` tidak didukung —
   `urls` hanya berisi versi terbaru.

4. **Tidak ada transaksi/rollback/smoke test.** Ekstrak langsung ke
   `USER_PACKAGES_DIR`. Gagal di tengah = folder setengah jadi.
   (Sisi baiknya: ada verifikasi SHA-256 dan proteksi zip-slip — dua hal yang
   memang dikerjakan dengan benar.)

5. **Katalog hanya ±60 paket hardcoded** di dalam file `.py`.
   ZCODE punya 300 di JSON aset.

---

## 5. Pelajaran yang diambil untuk ZCODE

Yang harus **ditiru**:

- **Utamakan `data["urls"]`** untuk kasus umum "install nama paket tanpa
  versi". Tidak perlu memfilter 82 versi urllib3 kalau user cuma mau yang
  terbaru. Pakai `releases` **hanya** saat ada specifier (`rich==13.7.1`)
  atau saat menyelesaikan dependensi transitif.
  → Efek samping: lebih cepat, dan seluruh kelas Bug A & D lenyap dari
  jalur yang paling sering dipakai.

- **Sediakan jalur sederhana yang tidak bisa salah.** Kompleksitas ZCODE
  (resolver, transaksi, smoke test) bernilai — tapi tidak boleh menjadi
  **satu-satunya** jalan. Bug A & D membuktikan: satu salah tanda pada
  perbandingan versi mematikan seluruh fitur.

Yang harus **dipertahankan** dari ZCODE:

- resolusi dependensi (ZABACODE bohong soal `rich` yang "berhasil")
- dukungan wheel native lewat Chaquopy (satu-satunya jalan bagi user tanpa PC)
- transaksi + rollback + smoke test
- verifikasi SHA-256 (ZABACODE juga punya — pertahankan)

---

## 6. Kesimpulan jujur

ZCODE tidak kalah dari ZABACODE. ZCODE mencoba melakukan hal yang **jauh lebih
sulit** — resolusi dependensi nyata, wheel native, instalasi transaksional —
dan tersandung pada empat kesalahan kecil di lapisan tambahan itu.

Empat-empatnya adalah **salah tulis, bukan salah arsitektur**:

- membandingkan versi paket dengan syarat versi Python (satu variabel tertukar)
- mengira `optString` mengembalikan `null` (satu asumsi API keliru)
- mengurutkan versi sebagai teks (satu `key=` kurang tepat)
- tidak membaca `stdlib.json` yang sudah tersedia (satu pemanggilan hilang)

Tidak ada yang perlu dirombak. Yang perlu adalah memperbaiki empat titik itu —
dan menambahkan test guard permanen agar tidak pernah kembali.
