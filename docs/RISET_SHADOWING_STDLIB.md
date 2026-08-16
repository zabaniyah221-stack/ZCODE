# RISET: Shadowing stdlib/beku Chaquopy — tox, setuptools, zope-interface

Tanggal: 2026-08-17 · Status: **RISET SELESAI SEBAGIAN** — mekanisme tox
TERBUKTI (repro sandbox), setuptools SETENGAH TERBUKTI (butuh bionic311),
obat dijadwalkan v1.0.19. Dokumen ini = bekal replay, jangan riset dari nol.

## Gejala di device (log breadcrumb 2026-08-17, Infinix ARMv7)

```
tox:        [SMOKE_TEST] ModuleNotFoundError: No module named 'packaging.pylock'
setuptools: [SMOKE_TEST] AssertionError: .../chaquopy/stdlib-common.imy/distutils/core.pyc
zope-interface: gagal karena deps-nya menyeret setuptools 84.0.0 (korban tabrak lari)
```

## Fakta dasar (dibaca langsung dari kode, 2026-08-17)

1. APK membekukan via `app/build.gradle.kts` pip section: pip 23.3.1,
   **setuptools 68.2.2**, wheel 0.41.2, **packaging 24.1** (WAJIB — resolver
   kita sendiri `import packaging`).
2. `smoke.py` berjalan **satu proses** dengan resolver (in-process, bukan
   subprocess). Cleanup `finally` hanya membuang modul yang BARU muncul
   selama smoke — modul yang sudah terimpor sebelumnya tidak disentuh.
3. ZCODE **tidak pernah mengeksekusi file `.pth`** (grep TransactionManager +
   package_runtime: nol). CPython asli menjalankan `.pth` site-packages saat
   startup; kita hanya menyuntik `sys.path`.

## Kasus tox — MEKANISME TERBUKTI (repro sandbox 2026-08-17)

`sys.modules` menang atas `sys.path`. Kronologi:

1. Resolver `import packaging` (24.1 beku) → nyangkut di `sys.modules`.
2. Smoke tox menyuntik staging packaging 26.3 ke `sys.path[0]` (benar!).
3. `import packaging.pylock` → Python lihat cache dulu → dapat 24.1 →
   `pylock` tidak ada di 24.1 (baru ada di packaging ≥25) → BOOM.

Skrip repro (jalankan di direktori berisi `old/`=packaging 24.1 extracted,
`new/`=packaging 26.3 extracted):

```python
import sys
sys.path.insert(0, "old"); import packaging          # fase resolver
sys.path.insert(0, "new")                             # fase smoke menyuntik
import packaging.pylock                               # -> ModuleNotFoundError
# kontrol: proses BARU dgn hanya new/ di depan -> pylock OK (26.3)
```

Hasil nyata sandbox: fase3 GAGAL, `packaging.__version__` tetap 24.1,
kontrol proses segar sukses. **BUKAN misteri Chaquopy — hukum dasar Python.**

### Twist: tox tetap mustahil walau smoke diakali

Kerja tox = bikin virtualenv + spawn subprocess `python`. Chaquopy tidak
punya binary python untuk di-spawn. **Vonis UNAVAILABLE dikunci** (kartu
katalog 2026-08-17), alternatif: pytest langsung (TESTED di device).
Berlaku juga untuk virtualenv.

## Kasus setuptools 84 — SETENGAH TERBUKTI (jujur: repro sandbox gagal)

Fakta terverifikasi:
- setuptools membawa `distutils-precedence.pth` → memasang shim
  `import distutils` → `setuptools._distutils`. ZCODE tak mengeksekusi
  `.pth` → shim tak terpasang.
- Error device = `_distutils_hack/__init__.py:76`:
  `assert '_distutils' in core.__file__` — distutils yang ke-load adalah
  **stdlib beku Chaquopy** (`stdlib-common.imy`), bukan milik setuptools.

Yang belum terbukti: repro di sandbox **gagal** karena sandbox = Python
3.13 (distutils sudah DIHAPUS dari stdlib 3.12+ → tak ada yang menshadow).
Konflik hanya bisa muncul di Python 3.11 (distutils stdlib masih ada).
**Lab yang benar = bionic311.** Replay:

```bash
bash tools/setup_armv7_emu.sh   # + resep SKILL 16 (LD_LIBRARY_PATH, libffi, libz)
# extract setuptools 84 wheel ke /var/tmp/st84, lalu:
/var/tmp/bionic311.sh -c 'import sys; sys.path.insert(0,"/var/tmp/st84"); import setuptools'
# hipotesis: AssertionError persis seperti device (stdlib 3.11 punya distutils)
```

## Peta obat (v1.0.19 — belum dieksekusi, keputusan user 2026-08-17)

| Obat | Menyembuhkan | Risiko | Catatan |
|---|---|---|---|
| **"Provided packages"**: resolver tahu setuptools 68.2.2/wheel/pip/packaging 24.1 sudah dibawa APK → requirement `setuptools` dari deps paket lain dianggap terpenuhi, skip download+smoke | zope-interface + seluruh kelas korban deps setuptools | Rendah | Kalau specifier eksplisit minta >=80: vonis jujur "runtime menyediakan 68.2.2", jangan pura-pura |
| Vonis tox/virtualenv | kejujuran katalog | Nol | SUDAH dieksekusi di v1.0.18-polish |
| Eviction `sys.modules` saat smoke paket pure-Python | kelas tox/packaging umum | Sedang-tinggi | Bahaya bangunkan Bug R utk paket ber-.so; wajib dibatasi staged-pure + restore finally; uji bionic311 dulu |
| Proses `.pth` saat aktivasi | setuptools modern | Tinggi | `.pth` = eksekusi kode arbitrer saat startup; jangan tanpa riset dalam |

## Batas kejujuran

- Repro tox = sandbox x86_64 Python 3.13 — mekanismenya universal Python,
  tapi belum di-replay bionic311 (blocker: tidak ada; hanya waktu).
- setuptools belum direpro di 3.11 mana pun. Jangan klaim "terbukti penuh".
- Tidak ada satu pun obat yang sudah dites — semua status DESIGNED.
