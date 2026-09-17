# SESSION — ringkasan obrolan 15-16 Sep 2026 (sesi HERMES x ZAQI)

## Alur besar

1. Browser ringan buat Celeron: coba Epiphany (46.5) → GitHub OK, tapi
   search bawaan DuckDuckGo = diblokir ISP (Foxline) → ganti default ke
   Google → Google CAPTCHA (IP di-flag). Epiphany ukur 1.2-1.5GB/4+ tab
   → purge. Ganti Falkon (QtWebEngine, ~515MB/1 tab, UI kalem) = harian.
   Midori baru = Gecko seberat Firefox (skip). IE = mati 2022 (skip).
2. Bersih-bersih RAM: hermes tua kesuspend 634MB swap + ChatGPT 370MB
   (force kill PID persis, free 853M→1.2G).
3. ZCODE desktop dual-instance + PID tua dirangkum ke vault.
4. Download GitHub Copilot AppImage v1.1.21 555MB (github/app releases):
   sempat gagal 2x (purge Epiphany kecepetan = pelajaran no 12) →
   curl resume → verified byte-pas → chmod +x di ~/Unduhan.
5. ZCODE desktop bug-hunt + fix (5+3 commit, semua CI hijau):
   autocomplete mati, blok putih, parser bawah, dobel ikon, blink.

## Temuan kunci (dengan bukti)

- Popup mati total: flag autoActivateAfterLetters default FALSE, kita tak
  panggil setAutoActivationRules(true, null). Manual Ctrl+Spasi lolos
  karena bypass cek (temuan USER = kunci). Fix 1 baris.
- RSTA single-match = silent auto-insert by design (source 3.3.2:842-886).
  Fix: setAutoCompleteSingleChoices(false).
- Blok putih = markOccurrencesColor default 224 (probe). User mau
  transparan → markOccurrences=false.
- Squiggle full-width bawah: deterministik single-line doc; multi-line
  benar. Mekanisme kandidat: SquiggleUnderlineHighlightPainter fast-path
  (offs==span view). Fix: span presisi dari caret '^' py_compile.
- JFrame+UTILITY diabaikan X11 (NORMAL); JDialog+UTILITY = SKIP_TASKBAR
  (probe TypeProbe2). setType OK di AWT tapi tak tembus X11 untuk Frame.
- Komunitas: issue #83 (mati abis read, FIX 3.1.6), #82 embed JavaFX
  Ctrl+Space mati (OPEN, dev minta SSCCE). Dev responsif.
- X focus nyangkut ID sampah pasca kill-churn → jendela baru tak map;
  Compiz --replace tak mempan; reboot menyembuhkan.
- Monitor-timeout + aksi destruktif = eksekusi buta (Pelajaran no 12).

## Keputusan user (jangan dilanggar)

- Editor start kosong = DISENGAJA (fokus logika dulu).
- Mark occurrences = MATI/transparan.
- Jeda autocomplete 160ms.
- Daemon Jedi + kasta 1 = KEEP (tahan dulu).
- Squiggle workaround status-bar = KEEP (tahan dulu).
- Semua build di CI, DILARANG lokal. Refactor besar = ACC dulu.
- Jangan pernah tawarkan tutup sesi.

## Obrolan penuh

Ada di Hermes session 20260911_085108_6fe906 (1426+ pesan, state.db).
Topik bisa dicari via session_search. Ringkasan harian di VAULT/Harian/.
