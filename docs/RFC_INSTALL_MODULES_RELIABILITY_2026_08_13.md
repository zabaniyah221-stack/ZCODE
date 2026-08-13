# RFC — Install Modules Reliability: timeout, retry, ownership, progress

**Status:** IMPLEMENTED + LOCAL/BIONIC/FULL-EMULATOR VERIFIED; menunggu CI v1.0.17 dan HP nyata

**Target:** v1.0.17
**Scope:** Analyze/Resolve di PackageEngineV2, bukan redesign seluruh installer

## 1. Insiden dan bukti

Log perangkat ARMv7/API 34 pada v1.0.15:

```text
numpy      20:19:23.216 → 20:20:53.509 = 90.293 s
matplotlib 20:21:05.550 → 20:22:35.587 = 90.037 s
pandas     20:22:44.132 → 20:24:14.160 = 90.028 s
```

Ketiganya berhenti tepat di `PyCall.kt:latch.await(90, TimeUnit.SECONDS)`, bukan
pada error paket tertentu. Commit `5eecef3` menaikkan `_http_get` dari satu
attempt 20 detik menjadi tiga attempt, sehingga satu URL dapat menghabiskan
~60 detik sementara seluruh dependency graph hanya diberi 90 detik.

`CountDownLatch.await(timeout)` hanya mengembalikan `false` saat waktu tunggu
habis; ia tidak membatalkan worker. Rujukan Java:
<https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CountDownLatch.html>.

Akibat tambahan: `PackageEngineV2.analyze` keluar dari `finally` dan melepas
`busyFlag`, padahal thread Python lama dapat tetap hidup. Analyze berikutnya
dapat berjalan tumpang-tindih dan `clear_metadata_cache()` menyentuh cache global.

**Confidence akar:** ~95%. URL yang lambat di HP belum diketahui karena versi
sekarang tidak men-stream stage/request ke Diagnostics.

## 2. Riset pembanding dan keputusan

### 2.1 pip

- pip memisahkan network `--timeout` dan `--retries`; bukan memberi seluruh
  dependency graph deadline 90 detik:
  <https://pip.pypa.io/en/latest/cli/pip/>.
- Resolver dapat backtrack sangat lama; pip menampilkan pekerjaan dan mengizinkan
  user menginterupsi:
  <https://pip.pypa.io/en/stable/topics/dependency-resolution/>.
- pip memperbaiki performa resolver dengan cache, bukan menonaktifkan cache:
  <https://pip.pypa.io/en/stable/topics/caching/>.
- Implementasi pip hanya mengulang status transient terpilih; issue sumber
  memperlihatkan 500/502/503/520/527, bukan semua 4xx:
  <https://github.com/pypa/pip/issues/11843>.

**Diambil:** timeout per I/O, retry terklasifikasi, progress, cache.  
**Tidak diambil mentah:** jumlah default lima retry; ZCODE adalah satu HP di
jaringan seluler dan setiap retry menahan worker in-process.

### 2.2 Pydroid/QPython/Termux

- Pydroid/QPython memakai repository paket native terkurasi plus pip untuk jalur
  umum; Termux menjalankan package manager sebagai proses dengan output dan
  interrupt.
- ZCODE belum punya proses terpisah untuk Chaquopy dan tidak boleh berpura-pura
  `Thread.interrupt()` setara membunuh proses.

**Diambil:** pekerjaan panjang harus terlihat dan dapat dibatalkan kooperatif.  
**Ditunda:** curated lock/repository fast path; itu optimasi build terpisah agar
reliability tidak tercampur perubahan sumber dependency.

### 2.3 Chaquopy/Kotlin/Android

- Chaquopy thread-safe tetapi tetap tunduk pada GIL:
  <https://chaquo.com/chaquopy/doc/current/cross.html>.
- Java/Kotlin object dapat dipass ke Python dan dipanggil balik:
  <https://chaquo.com/chaquopy/doc/current/faq.html>.
- Cancellation coroutine bersifat kooperatif dan `cancelAndJoin` menunggu
  completion:
  <https://kotlinlang.org/docs/cancellation-and-timeouts.html>.
- Android long-running work menekankan progress dan cancellation, tetapi
  WorkManager tidak langsung dipilih: Analyze bersifat user-initiated,
  foreground-screen, dan memakai runtime Chaquopy in-process.

**Diambil:** satu owner, operation ID, cooperative cancellation, cleanup sebelum
terminal state.  
**Ditolak saat ini:** migrasi Analyze ke WorkManager/foreground service.

## 3. Invariant

1. Hanya satu operasi package engine aktif dalam satu proses.
2. Caller tidak boleh berhenti menunggu sementara worker anonim tetap hidup.
3. `busyFlag` dilepas hanya setelah Python kembali dan cleanup selesai.
4. Timeout jaringan tidak sama dengan timeout seluruh resolve.
5. Retry hanya untuk kegagalan transient, maksimal dua total attempt per URL.
6. HTTP 4xx permanen tidak diretry; 408/429 dapat diretry dengan batas lokal.
7. Analyze memberi progress source/package/attempt kepada UI dan Diagnostics.
8. Cancel bersifat kooperatif; status terminal hanya setelah Python mengakui.
9. Callback stale tidak boleh mengubah operasi baru.
10. Dependency native dan metadata per-versi Bug K tidak boleh regresi.

## 4. Desain

### 4.1 PyCall ownership

Hapus thread internal + `CountDownLatch` + hard timeout 90 detik dari `PyCall`.
`callJson` memanggil Chaquopy secara sinkron pada thread pemanggil. Semua call
package engine saat ini dikontrak “panggil dari background thread”; PipScreen
sudah memakai `Dispatchers.Default`.

Keuntungan:

- tidak ada worker kedua yang kehilangan owner;
- coroutine/package engine tidak selesai sebelum Python selesai;
- `busyFlag` benar-benar merepresentasikan lifecycle operasi.

Guard tambahan menolak panggilan di main thread agar kontrak tidak dilanggar
oleh caller baru.

### 4.2 ResolveOperationBridge

Kotlin membuat bridge per resolve:

```text
operationId
cancelRequested
onProgress(event JSON)
```

Python menerima bridge sebagai argumen opsional terakhir `resolve_json`, jadi
caller/test lama tetap kompatibel. Python memakai `ContextVar`, bukan global
bridge, sehingga callback terikat thread/operasi yang benar.

Progress event minimal:

```json
{"stage":"http_begin","package":"numpy","source":"pypi",
 "attempt":1,"max_attempts":2,"detail":"metadata"}
```

URL penuh tidak masuk UI; hanya host/source agar query/credential tidak bocor.

### 4.3 HTTP policy

- per-attempt blocking timeout tetap 20 detik (nilai yang sudah diuji);
- total attempts = 2, bukan 3;
- retry: socket timeout, transient `URLError`, HTTP 408, 429, 500, 502, 503,
  504, 520, 527;
- non-retry: HTTP 400/401/403/404 dan parsing/hash/compatibility error;
- capped short backoff + jitter sebelum attempt kedua;
- cancellation diperiksa sebelum request, setelah failure, selama backoff, dan
  setelah body dibaca.

`urllib.request.urlopen(timeout=...)` adalah timeout blocking operations, bukan
deadline dependency graph:
<https://docs.python.org/3.6/library/urllib.request.html>.

### 4.4 State dan UI

Build ini tidak melakukan refactor penuh seluruh PipScreen ke sealed state
karena blast radius besar. Perubahan terbatas:

- selama `isInstalling`, tombol utama berubah menjadi **Batalkan**;
- `PackageEngineV2.cancelCurrentOperation()` meneruskan token ke active bridge;
- UI menampilkan progress resolve yang sudah di-throttle/dedup;
- Cancel menghasilkan code `CANCELLED`, bukan `RUNTIME`;
- start baru tetap ditolak sampai worker lama terminal.

Refactor boolean UI menjadi state machine penuh dicatat sebagai follow-up, bukan
syarat menyembuhkan regresi v1.0.15.

### 4.5 Cache

Resolver tetap serial lewat engine ownership. Selain itu, seluruh `resolve()`
dilindungi lock Python agar caller di luar UI tidak dapat menjalankan dua
resolver yang membersihkan cache global bersamaan.

Migrasi cache menjadi object per-session ditunda: perubahan itu menyentuh banyak
signature dan tidak diperlukan setelah orphan/overlap ditutup. RFC mencatat ini
sebagai utang dengan test serialization.

## 5. Failure contract

| Kondisi | code | Perilaku |
|---|---|---|
| user cancel | `CANCELLED` | cleanup, bukan error merah generik |
| semua attempt network gagal | `NETWORK` | host/source/attempt terakhir tersedia |
| HTTP permanen pada source opsional | fallback | source lain tetap dicoba |
| worker masih aktif | `BUSY` | start baru ditolak |
| callback operation lama | diabaikan | tidak menimpa UI baru |
| unexpected Python error | `RESOLUTION` | technical detail tersalin |

## 6. Test dan uji mutasi

### Python deterministic

Gunakan fake `urlopen`, fake sleeper, dan fake bridge:

1. sukses attempt pertama;
2. timeout lalu sukses;
3. 404 tidak retry;
4. 503 retry lalu sukses;
5. cancel sebelum request;
6. cancel selama retry wait;
7. progress memiliki package/source/attempt;
8. bridge selalu di-reset di `finally`;
9. concurrent resolve diserialisasi;
10. numpy/pandas/matplotlib guard existing tetap hijau.

Mutasi wajib: `attempts=3`, retry 404, hapus cancel check, hapus lock, dan
kembalikan latch 90 detik harus ditangkap guard.

### Kotlin lexical/contract

- PyCall tidak membuat raw `Thread`/`CountDownLatch`;
- main-thread guard ada;
- bridge punya operation ID + volatile cancel;
- engine menyimpan active bridge sampai `finally`;
- PipScreen menampilkan progress dan Cancel.

### Integration

- seluruh pytest + `tools/check.sh`;
- endpoint nyata host;
- `bionic311 resolve_json` untuk numpy/pandas/matplotlib bila environment siap;
- CI APK;
- satu UAT HP ARMv7.

## 7. UAT satu build

1. Analyze/install paket kecil (`colorama`) — progress terlihat.
2. Analyze `numpy` — tidak berhenti tepat 90 detik.
3. Analyze `matplotlib` — plan selesai atau error menyebut source, bukan wrapper timeout.
4. Analyze `pandas` — dependency per-versi tetap ada.
5. Saat analyze, tekan Batalkan — tunggu status dibatalkan; analyze berikutnya bisa mulai.
6. Export Diagnostics.

## 8. Non-goals

- tidak membuat repository wheel sendiri;
- tidak mengganti Chaquopy/Python 3.11;
- tidak memasang WorkManager/foreground service;
- tidak menyelesaikan semua backtracking ala pip;
- tidak mengklaim semua PyPI kompatibel;
- tidak menambah curated dependency lock pada build ini;
- tidak mengubah download/transaction install kecuali plumbing cancel/progress yang perlu.

## 9. Hasil implementasi sementara

- Test penuh setelah full-emulator tooling: **418 passed**.
- `tools/check.sh`: hijau.
- Kotlin lexical sanity: 53 file hijau.
- Uji mutasi terbukti merah untuk: retry 2→3, retry HTTP 404, cancellation
  check dihapus, resolve lock dilepas, primitive orphan PyCall dikembalikan,
  fallback pemasangan qemu dilumpuhkan, dan dependency HTTPS bionic dihapus.
- Infra `bionic311` ikut diralat: fallback qemu lama tak terjangkau karena
  `need_cmd` langsung `exit`; ditambah SONAME `libz.so.1` dan OpenSSL ARM agar
  probe HTTPS benar-benar dapat diulang dari sandbox baru.
- `bionic311` (Python 3.11.15, ARMv7, bionic; bukan JVM/HP):

| Requirement | Waktu | Plan |
|---|---:|---:|
| numpy | 12.04s | 4 package |
| pandas | 15.64s | 9 package |
| matplotlib | 34.27s | 17 package |

Seluruhnya `ok=true`; pandas membawa dateutil/six/pytz/tzdata dan matplotlib
membawa dependency native + Python. Raw progress matplotlib berjumlah 156 event;
`http_ok` sengaja tidak diteruskan ke Compose/Diagnostics agar tidak membuat
156 coroutine scroll + flush disk di ARMv7. Event begin/retry/fail/chosen/cancel
tetap terlihat.

### Verifikasi full Android ARMv7 (lanjutan v1.0.17)

Emulator klasik Android API 24/armeabi-v7a berhasil boot secara headless di
sandbox tanpa KVM, dengan QEMU TCG 512 MB + SwiftShader. APK test-only minSdk24
(dari source yang sama; production tetap minSdk26) berhasil memasang dan
menjalankan Compose, WebView, Chaquopy Python 3.11.14, dan Install Modules.

Uji pertama menemukan Bug M: Cancel diterima saat resolve matplotlib, tetapi
`ResolveError(CANCELLED)` ditelan fallback source dan hasil akhir menjadi
`COMPATIBILITY`. Setelah `_propagate_cancel` diwajibkan di semua catch fallback,
uji ulang menghasilkan:

```text
16:05:29.568 PKG_RESOLVE_CANCEL_REQUEST
16:05:30.800 stage=cancelled package=numpy
16:05:30.943 PKG_RESOLVE_WORKER_END
16:05:31.339 PKG_ANALYZE_CANCELLED matplotlib
```

Negative metadata failure cache juga ditambah. Sebelum fix, PyPI 404 untuk satu
support library tampil sampai 4 kali; sesudahnya full metadata URL yang sama
hanya diminta sekali per resolve (endpoint per-versi tetap sumber berbeda).

**Batas bukti:** artifact production minSdk26 tidak dapat dipasang di image
ARMv7 resmi terakhir (API24/25). Maka status ini FULL-EMULATOR VERIFIED untuk
Android/JVM/Chaquopy/ARMv7, bukan DEVICE VERIFIED untuk artifact production.
UAT HP ARMv7 API26+ tetap gate akhir.

## 10. Rollback

Perubahan tidak mengubah format environment atau installed database. Commit
dapat direvert tanpa migrasi data. Wheel/cache yang sudah ada tetap kompatibel.
