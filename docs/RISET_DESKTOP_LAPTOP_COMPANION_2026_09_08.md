# RISET DESKTOP VARIANT & MULTI-ARTIFACT CI — KEPUTUSAN PARKIR (2026-09-08)

**Status: DECISION RECORDED (PARKIR) — dokumen keputusan, tanpa kode,
tanpa perubahan workflow.**
Trigger: user kini punya laptop (neofetch 2026-09-08) → dua usulan dibahas:
(1) ZCODE versi desktop native "sekaligus", (2) satu CI menghasilkan
`.apk`/`.deb`/`.exe` sekaligus. Dokumen ini merekam analisis + vonis +
syarat membangunkan, agar diskusi tidak diulang dari nol.

---

## 1. Fakta perangkat baru (sumber: neofetch user, 2026-09-08)

- **Lenovo YOGA 300-11IBY** (80M0), Intel Celeron N2840 (2 core, Bay Trail),
  RAM 4 GB (±2,6 GB terpakai idle), Linux Mint 22.3 Xfce, kernel 6.14,
  disk longgar (`/` 92 G bebas; `sdb4` 136 G bebas).
- Konsekuensi: **emulator Android / Waydroid tidak realistis** (image
  x86_64 butuh ±2 GB RAM vs sisa ±1,1 GB; CPU 2-core) — analysis resource,
  confidence ~90%, bukan percobaan langsung. Sebaliknya `adb`, pytest,
  browser harness = sangat ringan.
- Catatan terpisah untuk user: partisi `/dev/sdb2` **penuh 100%**
  (63G/67G, sisa 0) — jika masih aktif dipakai, rawan perilaku aneh;
  perlu pemeriksaan mandiri.

## 2. Perubahan premise penting (governance — belum diedit ke SKILLS/PRD)

SKILL 1, SKILL 6, dan PRD dibangun di atas premis **"user tanpa PC —
tidak ada `adb logcat`, QA tester tunggal buta log sistem"**. Premise itu
**tidak berlaku penuh sejak 2026-09-08**: user punya laptop yang mampu
menjalankan `adb`. Dampak positif: bukti kelas log sistem (stack trace FC
native/Java, ANR, WebView crash) kini dapat diperoleh saat UAT; install
APK rilis tanpa unduh manual di HP; pull artefak workspace. Yang TIDAK
berubah: label **DEVICE VERIFIED tetap milik Infinix ARMv7** — bukti
x86_64/laptop tidak pernah menggantikan label device (AGENTS.md §11).
Pemutakhiran SKILLS.md/PRD menunggu approval user; dokumen ini catatan
antaranya.

## 3. Pertanyaan 1 — ZCODE desktop native: **PARKIR**

Tidak ada switch/build-variant APK→desktop. Porting = menulis produk kedua:

| Lapisan | Keterikatan | Port desktop |
|---|---|---|
| UI | Compose **Android** (bukan Multiplatform) | rewrite (jalur: Compose Multiplatform) |
| Editor | WebView Android + bridge JS↔Kotlin | isi CM6 portabel (bundle jalan byte-exact di browser — SKILL 24); pembungkus diganti |
| Python | Chaquopy (plugin+runtime Android-only) | ganti (CPython subprocess — pola dual-backend sudah ada di ExecutionEngine/PluginRunner) |
| Fitur | FGS updater v1.0.22, process rebirth, manifest, FileProvider | redesign per-platform |

Vonis: proyek skala **v2.0** (PRD baru + RFC), bukan tweak. Dibangunkan
bila: (a) ada kebutuhan user nyata di desktop yang tak terlayani
pytest/harness, (b) v1.0.23–v1.0.24 (intelligence + terminal) selesai &
stabil, (c) user eksplisit memilih arah produk ini.

## 4. Pertanyaan 2 — multi-artifact CI (`.apk`+`.deb`+`.exe`): **PARKIR**

- Mekanisme tersedia & gratis untuk repo publik: hosted runner arm64
  (`ubuntu-24.04-arm`, `windows-11-arm`) — preview Feb 2025
  (https://www.infoq.com/news/2025/02/github-actions-linux-arm64/),
  GA Agustus 2025
  (https://blockchain.news/news/github-arm64-hosted-runners-public-repositories).
- **CI hanya mengemas yang ada**: `.deb`/`.exe` butuh aplikasi desktop
  (§3) — hari ini hanya APK yang ada. Menambah job YAML = murah; barang
  yang dikemas = bulan-bulan.
- **Artifact ≠ uji**: `.deb` arm64 baru teruji bila ada mesin arm64;
  inventaris user = laptop x86_64 + HP armv7 (nol arm64). APK ZCODE sendiri
  sudah mengirim lib arm64 (compile-verified oleh CI) — begitu ada HP
  arm64, alat ujinya = HP itu + one-tap updater.
- **Ekonomi UAT**: QA tester tunggal; kontrak satu-artifact-user-per-rilis
  (SKILL 25/26) dibangun dengan susah payah di v1.0.20–22. Tiga installer =
  tiga jalur UAT + tiga mekanisme update (updater one-tap = Android-only).
- Yang diadopsi sekarang: **tidak ada perubahan workflow**. Opsi murah
  kapan saja (keputusan terpisah): artifact bukti (test report, hasil
  harness) dan/atau cek compile arm64 di runner arm64.

## 5. Peran laptop yang DIADOPSI (mulai hari ini)

1. **adb companion** (nilai terbesar): `adb logcat -b crash` saat FC,
   `adb install -r` APK rilis CI, `uiautomator dump`, `screencap`,
   pull workspace/backup. Checklist keamanan: USB debugging OFF saat tidak
   dipakai; jangan colok di PC publik.
2. **Runner lokal ringan**: `tools/check.sh` + suite pytest penuh +
   browser harness editor (bundle shipped, jalankan di Chrome/Firefox).
3. **BUKAN**: runner Gradle/JDK (RAM 4 GB dual-core = penyiksaan; CI tetap
   hakim kompilasi), emulator, pengganti UAT device.

## 6. Batas jujur

- Verdict "emulator tidak realistis" = analisis angka resource, bukan
  percobaan langsung di mesin tersebut (~90%).
- Dokumen ini belum mengubah kode, workflow, SKILLS, PRD, maupun rilis
  apa pun; seluruh parkir dapat dibuka ulang dengan premis/bukti baru.


---

## 7. Visi jembatan & tangga desktop — RECORD (diskusi user 2026-09-09)

**Status: VISION RECORDED — parkir aktif.** Keputusan user: ekspansi
arm64/desktop menunggu perangkat & resource nyata; fokus perang tetap
runtime armv7 — "user device ampas harus bisa merakit projectnya tanpa
embel-embel apa pun".

### 7.1 Reframing posisi

ZCODE untuk siapa punA, tak terkecuali; ARMv7 = titik berangkat perang,
bukan plafon. Ekspansi bukan "bersaing kemegahan" dengan VS Code/PyCharm,
melainkan axis **gratis-vs-bayar** (pola LibreOffice) + **kontinuitas**
("lanjutkan persis pekerjaan dari HP-mu") — moat yang tidak dimiliki
peserta mana pun di bawah.

### 7.2 Tiga wujud desktop (dipilih: A dulu)

| Wujud | Bentuk | Catatan |
|---|---|---|
| **A. ZCODE Web companion** (PILIHAN) | server Python lokal + bundle CM6 sama + browser sebagai renderer | termurah; browser harness SKILL 24 sudah membuktikan bundle jalan byte-exact; workbench web = biaya terbesar, bertahap editor-first |
| B. App Compose Multiplatform | JVM + WebView via JCEF/KCEF | RAM besar; status WebView desktop = experimental (klibs.io / parkwoocheol/compose-webview); hanya bila A kurang |
| C. Fork VSCodium/Theia | — | DITOLAK: maintenance harian upstream + Electron berat + moat ZCODE menguap di desktop (pip/ruff/ty tersedia; kurasi tak diperlukan) |

Pesaing nyata di niche ringan: **Thonny** (raja pemula-laptop-kentang),
Geany (ultringan generik), Spyder (scientific, Qt berat). Positioning
menang-kalah: kontinuitas + gratis + UI modern CM6; kalah di: ekosistem
plugin, debugger matang (diakui jujur).

### 7.3 Kunci teknis: Python per-ABI (inferensi — wajib dibuktikan di T2)

Chaquopy men-stack SATU versi Python per APK ⇒ selama armv7 dilayani,
arm64 ikut terpasung 3.11. Jalur keluar = runtime pada process terpisah
(T2 `:python` / T3 standalone): versi Python menjadi variabel per ABI —
armv7 tetap 3.11 (janji jembatan), arm64 bebas naik. **Ini inferensi
arsitektur dari desain T2/T3, belum dibuktikan eksperimen** — jadikan
pertanyaan riset pertama saat T2 dieksekusi.

### 7.4 Skema update desktop (reuse logika v1.0.22)

Cek `releases/latest` + compare numerik + never-downgrade = reuse ~90%.
Yang beda hanya langkah pasang: AppImage/tar.gz = replace file + restart;
`.deb` = install file baru. Lebih sederhana dari Android (tanpa dialog
izin sistem). Format artifact menyusul keputusan D4.

### 7.5 Aset naratif

"War history" ARMv7 (SKILLS.md = arsip perang yang jujur) adalah aset
branding yang sudah dimiliki: user perangkat epic/legend memahami bahwa
produk yang mereka pakai ditempa di medan tersulit. Kandidat penyajian:
satu baris di About / halaman riset publik — saat waktunya, bukan janji.

### 7.6 Tangga D0–D4 (urutan saat dibangunkan)

```text
D0  kontrak & riset user desktop (siapa, apa yang tak tergantikan)
D1  productize browser harness -> "ZCODE Web" dev mode (dobel fungsi: alat QA + fondasi)
D2  workbench web bertahap (drawer/terminal/palette versi web)
D3  app native CMP — hanya bila web terbukti kurang (bayar JCEF + RAM)
D4  packaging + updater desktop (cek-update logic reuse, §7.4)
```
