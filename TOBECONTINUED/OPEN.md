# OPEN — loop terbuka + langkah berikutnya

Status saat paket dibuat: semua commit CI hijau, 3 verifikasi device
menunggu mata user (1 ikon panel JDialog, blink F5 drawer instan,
garis W292/span presisi).

## 1. Verifikasi device (prioritas 1, tanpa kode)

Launch /opt/zcode/bin/zcode, cek:
- Panel: 1 ikon saat splash (bukan 2). Commit 26a3b2a.
- F5: drawer buka instan, blink <2ms masih ada/tidak. Commit 4dda3e9.
- Ketik `def foo(` 1 baris: squiggle KECIL tepat di char (bukan
  full-width bawah). Commit f972495. Ketik `pr`: popup ~160ms.

## 2. KEEP (tahan, desain sudah ada)

- Daemon Jedi persisten + kasta 1 (kata dokumen/lokal scope-aware).
  Lihat SESSION.md + ~/vault catatan Jedi (dingin 6.2s/hangat 3s).
- Workaround status-bar mirror buat single-line (opsi B).
- Jedi-on-EDT = cacat by desain di Celeron; cache-async opsi B bertahan.

## 3. Upstream (opsional)

- SSCCE squiggle single-line → bobbylight/RSyntaxTextArea.
  Bukti + probe ada di SCRIPTS/. Kelas embed (#82) masih open.

## 4. Rutin kembali berlayar

- Beban 34 paket apt tertunda (per 15 Sep, belum ACC upgrade).
- NOTEZ backlog: status bar biru, uji device ke-2, About/lisensi.
- ZMUX + repo muzape28-blip: maintenance tertunda (kata user).
- Firefox di-purge; default browser = Falkon.

## Perintah cepat

- CI: `gh run list --limit 3` (cabang arena/zcode-desktop).
- Unduh artifact: curl resume + Bearer $(tr -d '\n\r ' < ~/Dokumen/ZPAT.env)
  ke api.github.com/.../actions/artifacts/<ID>/zip, cocokkan size,
  unzip, `sudo dpkg -i *.deb`.
- Ukur blink: SCRIPTS/blink2.py (butuh PIL + wmctrl).
- Probe headless: javac/java + jar /opt/zcode/lib/app (lihat SCRIPTS).
