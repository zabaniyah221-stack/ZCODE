# Audit Akar ZABACODE → Penanganan ZCODE (Kotlin)
### Bedah total 667 baris AUDIT_REPORT + 640 baris CHANGELOG + 1196 baris web_app + 2281 baris oracle + 541 baris executor + 4846 baris index.html + 316 tests

**Tanggal:** 2026-08-07  
**Repo acuan:** `muzape28-blip/ZABACODE` main @ `0a78140` (181 commits) — clone `/tmp/ZABACODE`  
**Metode:** baca manual semua file `zabacode/core/*.py`, `templates/index.html`, `SECURITY.md`, `AUDIT_REPORT.md`, `CHANGELOG.md`, `buildozer.spec`, `tools/check.sh` + grep `fetchApi`, `PROOT_NO_SECCOMP`, `Content-Type`, `is_success`, plus run `wc -l`, `grep` 316 tests.  
**Rule tim:** honest & aware — tidak ada yang ditutup-tutupi.

> Dokumen ini adalah **daftar bug terlengkap** ZABACODE — bukan cuma F-01 s/d F-09, tapi semua jejak di CHANGELOG Unreleased, SECURITY #18-#27, Issue #50, dan temuan static grep. Tiap bug ada **akar + bukti + dampak + penanganan ZCODE Kotlin** biar ZCODE tidak ngulang.

---

## Ringkasan — Peta Bug dalam 1 Tabel

| # | Nama | Severity | Status Zabacode | Apakah ZCODE Rawan? |
|---|---|---|---|---|
| **F-01** | `fetchApi` tanpa `Content-Type` → body dibuang Flask | 🔴 CRITICAL | Sudah fix global di `fetchApi` (commit audit) | **Tidak kalau pakai JS Bridge** |
| **F-02** | Auto-Fix merusak runtime error (`print(prices[9])` → `print("prices[9]")`) | 🔴 CRITICAL | Sudah fix gate `if parses → jangan sentuh` | **Tidak kalau port rule** |
| **F-03** | `is_success` dihitung lalu dibuang, `ok = len(fixes)>0` | 🟠 HIGH | Sudah fix `ok = is_success and fixes` | Tidak |
| **F-04** | Regex `=` → `==` merusak kwarg `f(a=1)` → `f(a==1)` | 🟠 HIGH | Sudah fix `_replace_bare_equals` depth-aware | Tidak |
| **F-05** | Test pakai `json=` auto-header, tidak pernah test jalur browser `text/plain` | 🟠 HIGH | Belum ada test Content-Type negatif | **Rawan kalau nggak bikin test Kotlin** |
| **F-06** | CI cuma `pytest test_main.py`, `test_hardening_regressions.py` (6 tests) mati | 🟡 MEDIUM | Belum fix di `build_apk.yml` | Rawan |
| **F-07** | `check_code()` off-by-9 karena `SAFE_INPUT_PATCH` 9 baris | 🟡 MEDIUM | Sudah fix `code.replace` tanpa prelude | Tidak kalau ZCODE tanpa prelude |
| **F-08** | Traceback interaktif bocor `_active_run.py` (hanya batch di-mask) | 🟢 LOW | Sudah fix `_mask_runner_filename` | Tidak |
| **F-09** | Dead code + versi ngaco (`main.py` v1.0.0 vs web_app v1.2.1) | 🟢 LOW | Sebagian masih ada | Rawan version drift |
| **B-10** | Beautifier `def f() -> int:` → `def f() - > int:` | 🟠 HIGH | Sudah fix `longest-first` | Tidak |
| **B-11** | Syntax Guard hitung `(` `)` tanpa strip string → `print(' :)')` diblock | 🟠 HIGH | Sudah fix pakai `/api/check` | Tidak |
| **B-12** | Oracle klaim palsu: “bypass TLS” & “use Interactive Run mode” | 🟡 MEDIUM | Sudah fix di `offline_reply` | Tidak |
| **B-13** | Matplotlib tidak muncul di RUN button (interactive tidak collect image) | 🟠 HIGH | Sudah fix `collect_new_images` shared | Rawan kalau ZCODE bikin 2 path lagi |
| **B-14** | `fix my code` pakai regex `hello world` bukan `auto_fix_code()` | 🟠 HIGH | Sudah fix | Tidak |
| **B-15** | Oracle bilang editor kosong padahal ada kode (`notes` falsy) | 🟡 MEDIUM | Sudah fix | Tidak |
| **B-16** | Kartu Oracle buang line number yang baru di-resolve | 🟡 MEDIUM | Sudah fix echo source line tappable | Tidak |
| **B-17** | Styling kartu Oracle tidak kepakai | 🟢 LOW | Sudah fix | Tidak |
| **B-18** | Error card line salah (PRELUDE), chained traceback salah, substring match | 🟠 HIGH | Sudah fix | Tidak |
| **B-19** | `async def` invisible di `analyze_buffer` | 🟡 MEDIUM | Sudah fix | Tidak |
| **B-20** | Auto-Fix freeze UI di file besar | 🟡 MEDIUM | Sudah fix bounded diff | Rawan di Kotlin kalau nggak debounce |
| **S-18** | Execution tanpa bound (512KB, queue, timeout) | 🔴 CRITICAL | Sudah fix #18 | **Rawan kalau Kotlin lupa bound** |
| **S-19** | `custom` hilang dari keystore (6 provider hardcode) | 🟠 HIGH | Sudah fix central `ALLOWED_PROVIDERS` | Tidak |
| **S-20** | `files/files` double nesting + `.gitignore` bocor | 🟡 MEDIUM | Sudah fix `paths.py` | Tidak |
| **S-21** | Draft localStorage tanpa consent, plain text | 🟡 MEDIUM | Sudah fix Privacy card | **Rawan kalau pakai DataStore tanpa consent** |
| **S-22** | `pip --trusted-host` matikan TLS | 🔴 CRITICAL | Sudah fix hapus flag | **Rawan kalau Gradle pakai http** |
| **S-23** | JSON tidak valid → 500, bukan 400 | 🟡 MEDIUM | Sudah fix `_get_json_payload` | Tidak |
| **S-24** | Custom endpoint URL di field API key, HTTP cleartext tanpa warning | 🟡 MEDIUM | Sudah fix `endpoint_url` terpisah | Tidak |
| **S-25** | Docs klaim fallback AES-GCM padahal HMAC stream | 🟢 LOW | Sudah fix docs | Tidak |
| **S-26** | Build inputs floating (`flask>=`, Actions `@v4`) | 🟡 MEDIUM | Sudah fix `requirements.lock` + pin SHA | **Rawan kalau Kotlin pakai `+` version** |
| **S-27** | Loopback token di JS, port 5000 ketuker Zmux | 🟠 HIGH | Sudah fix range 5000-5010 + docs | **Tidak kalau ZCODE pakai JS Bridge** |
| **C-50** | Zmux ↔ Zabacode ketuker (port + taskAffinity) | 🟠 HIGH | Fix PR #50 (belum merge) | **Wajib fix di ZCODE manifest** |
| **E-01** | `MAX_FILENAME_LEN` 128 tapi OSError 500 kalau 255 | 🟡 MEDIUM | Sudah fix `try/except OSError` | Tidak |
| **E-02** | `User-Agent: Zabacode/1.2.0` masih hardcode | 🟢 LOW | Belum fix | Rawan version drift |
| **E-03** | `buildProot` wajib NDK, tapi CI tidak check | 🟡 MEDIUM | Sudah fix `requireProotArtifacts` di ZMUX | Rawan di ZCODE kalau pakai proot |

Total **33 bug/utang** — semua di bawah.

---

## 1. Keluarga CRITICAL — yang pernah bikin fitur mati / data rusak

### F-01 — `fetchApi` tanpa `Content-Type` → Oracle mati total
- **Akar:** `function fetchApi` cuma inject `X-Zabacode-Token`, tidak inject `Content-Type`. 18 `fetchApi POST` ada 3 yang lupa tulis header manual (`/api/oracle/explain` baris 2904, `/api/oracle/fix` 2928, `/api/run/interactive/stop` 2155). Browser kirim `text/plain`, Flask `request.get_json(silent=True)` return `None` → `_get_json_payload` lama `return {}, None` (diam-diam anggap kosong) → `auto_fix_code("")` → “editor kosong”.
- **Bukti:** `AUDIT_REPORT.md` reproduksi: tanpa header → `{'ok': False, 'message': 'The editor is empty...'}` ; dengan header → `{'ok': True, 'fixed_code': 'print("prices[9]")'}`. Grep sekarang: masih 21 `fetchApi POST` tanpa `Content-Type` eksplisit di file (tapi sekarang selamat karena `fetchApi` global sudah inject — lihat `templates/index.html:961`).
- **Dampak:** Kartu Oracle di terminal selalu abu-abu “could not auto-diagnose”, Auto-Fix toast generik.
- **Penanganan ZCODE:** **Hapus kelas bug ini sekalian.** ZCODE pakai **JS Bridge `file://` + `addJavascriptInterface`**, bukan HTTP loopback — jadi tidak ada `Content-Type` yang bisa lupa. Kalau tetap butuh HTTP untuk AI, pakai `OkHttp` interceptor wajib `application/json` + backend `ContentType` strict 400 (copy `_get_json_payload` baru yang sudah `if get_data().strip(): return 400 invalid_json`).

### F-02 — Auto-Fix merusak kode valid
- **Akar:** `oracle.py:825` regex `print\( ... \)` ngebungkusi **ekspresi valid** jadi string. Tidak ada `ast.parse(inner, mode="eval")` check. Ditambah tidak ada gate `if parses(original): jangan sentuh`.
- **Bukti:** 5 dari 8 kode valid dirusak: `print(x+1)` → `print("x + 1")`, `print(math.pi)` → `print("math.pi")`. Tombol `Apply Fix` langsung `setEditorValue()+saveActiveTab()` tanpa undo.
- **Penanganan ZCODE:** Port 1:1 rule baru: `if _is_valid_python(code): return runtime_error=True` (ada di `auto_fix_code` sekarang). Patch cuma keep kalau `parses(before)==False and parses(after)==True`. Di Kotlin, `OracleEngine.autoFix()` harus return `Result` dengan `isParses` boolean, dan UI wajib `Diff preview` + `Undo guarded` (cek `tabId` + `revision` sebelum apply — lihat `templates/index.html:3616`).

### F-03 — `ok = len(fixes)>0` padahal masih SyntaxError
- **Akar:** `oracle.py:945` hitung `is_success = True` kalau `ast.parse(fixed)` sukses, tapi `return {"ok": len(applied_fixes)>0}` — nilai dibuang.
- **Bukti:** Input `def f(:\n    print("a"` → output `def f(:):\n    print("a"` masih SyntaxError tapi `ok=True`.
- **Penanganan ZCODE:** `ok = is_success and applied_fixes.isNotEmpty()` (sudah fix). Di Kotlin, pakai `sealed class FixResult { data class Success(val fixed: String, val diff: Diff)` vs `Failure` — tidak ada boolean terpisah yang bisa lupa.

### S-18 — Execution tanpa bound
- **Akar:** `InteractiveSession` awal tanpa `MAX_CODE_BYTES`, `MAX_INTERACTIVE_QUEUE`, `MAX_OUTPUT_CHARS`, `120s/60s` timeout.
- **Penanganan ZCODE:** Kotlin `ExecutionEngine` wajib `MAX_CODE_BYTES 512KB`, `MAX_INTERACTIVE_BYTES 8KB`, `Queue 10k`, `totalChars 256KB + truncated flag`, `withTimeout(30s)` + `destroyForcibly() + PGID`. Copy persis `executor.py` yang sudah bounded.

### S-22 — `--trusted-host` matikan TLS
- **Akar:** `lib_manager.py` lama `pip install --trusted-host pypi.org --trusted-host files.pythonhosted.org`.
- **Penanganan ZCODE:** Jangan pernah pakai `http` atau `trustAllCerts` di OkHttp. Pakai `get_ssl_context()` → `conscrypt + certifi` (port `net.py`). Kalau `SSLHandshakeException`, tampilkan `TLS_HELP_MESSAGE` (“cek jam device, update certifi”) bukan bypass.

---

## 2. Keluarga HIGH — fitur jalan tapi hasil salah

### F-04 — `=` → `==` merusak kwarg
- **Akar:** `re.sub(r"(?<![!=<>+\\-*/%])=(?![=])", "==", line)` tanpa cek depth kurung.
- **Bukti:** `if d.get('k', default=1):` → `if d.get('k', default==1):`
- **Fix:** `_replace_bare_equals` depth-aware (hanya depth 0). ZCODE: jangan pakai regex, pakai AST rewrite atau depth scan yang sama.

### B-10 — Beautifier `->` → `- >`
- **Akar:** Operator dipad satu per satu, prefix menang: `//` sebelum `//=`.
- **Bukti:** `def f() -> int:` → `def f() - > int:`, `n //=2` → `n // =2`.
- **Fix:** `OPERATORS` longest-first (`//=` sebelum `//` sebelum `/`) + test `AST identical` (125 kombinasi). ZCODE: copy tuple `_OPERATORS` 1:1.

### B-11 — Syntax Guard block `print(' :)')`
- **Akar:** Guard hitung `(` vs `)` mentah, tidak strip string/comment.
- **Fix:** Pakai `/api/check` yang sudah strip, atau ZCODE pakai `checker.py` yang sudah `strip_comments_and_strings` + `check_code`.

### B-13 — Matplotlib tidak muncul di RUN
- **Akar:** ZABACODE punya 2 path: `batch` (`/api/run` collect image) vs `interactive` (`/api/run/interactive/*` tidak collect). RUN button pakai interactive → image tidak pernah diambil. Android `plt.show()` no-op, jadi `savefig` satu-satunya jalan.
- **Fix:** `collect_new_images(baseline)` shared, `snapshot before run`, `dedup baseline`, `skip >8MB`, `retry half-written`. ZCODE: jangan bikin 2 path terpisah lagi; kalau harus, share `ImageCollector` dengan baseline (copy `executor.py:101`).

### B-14 — `fix my code` pakai regex `hello world`
- **Akar:** Chat branch `offline_reply` re-implement fixer inline, tidak manggil `auto_fix_code()`.
- **Fix:** Panggil engine beneran. ZCODE: satu sumber `OracleEngine.autoFix()`.

### B-18 — Error card salah baris
- **Akar ZABACODE lama:** `humanize_traceback` salah offset `PRELUDE_LINE_COUNT` 9, chained traceback salah exception, `in` substring match.
- **Fix:** `line_offset` param, `cause` chain handling, `full word` match. ZCODE: jangan inject `SAFE_INPUT_PATCH` 9 baris ke file user — pakai wrapper process yang trim stack, jadi line number akurat tanpa offset.

---

## 3. Keluarga MEDIUM — UX & keamanan yang diam-diam bocor

### S-19 — `custom` hilang dari keystore
- **Akar:** `_try_keystore_load` hardcode 6 provider, tidak include `custom`.
- **Fix:** Central `_get_all_providers()` dari `ALLOWED_PROVIDERS` + merge fallback. ZCODE: `KeystoreService` pakai `PROVIDER_INFO.keys` yang sama.

### S-21 — Draft localStorage tanpa consent
- **Akar:** `saveTabsToStorage()` simpan semua `tabs` ke `localStorage` `zabacode-tabs` plain text.
- **Fix:** `zabacode-persist-drafts` toggle + Privacy card + Clear button + disclosure. ZCODE: pakai `DataStore` dengan `persistDrafts: Boolean` default `false` untuk fresh install (lebih jujur), plus `Encrypted` kalau mau.

### S-23 — JSON invalid → 500
- **Akar:** `_get_json_payload` lama return `{}` kalau `get_json` None.
- **Fix:** `if get_data().strip(): return 400 invalid_json` + `if not dict: 400 invalid_json_type` + `_validate_string_field`. ZCODE: `kotlinx.serialization` strict, reject array/primitive.

### S-27 + C-50 — Loopback & coexistence Zmux
- **Akar:** `run_webview_server()` range `5000-5010` tapi `p4a.port=5000` hardcode di `WebViewLoader.tmpl.java` → app kedua bind 5001 tapi UI load app pertama. `singleTask` + `PythonActivity` sama.
- **Fix ZABACODE:** range scan + log. **Fix final ZMUX PR #50:** `android.manifest.launch_mode=singleTop` + `p4a_hook.py` inject `taskAffinity=com.zaba.zabacode` + `documentLaunchMode=intoExisting`.
- **Penanganan ZCODE:** Tidak pakai loopback. `AndroidManifest.xml`: `taskAffinity="com.zaba.zcode" singleTop documentLaunchMode=intoExisting`. Jika butuh server (AI custom), pakai `ServerSocket(0)` (random free port) + discovery via `Intent`, bukan hardcode 5000.

### B-19 — `async def` invisible
- **Akar:** `analyze_buffer` visitor tidak kunjungi `AsyncFunctionDef`.
- **Fix:** Tambah `visit_AsyncFunctionDef`. ZCODE: pakai `tree-sitter` atau `ast` yang handle async.

### S-20 — `files/files` double nesting
- **Akar:** `ANDROID_PRIVATE` sudah `.../files`, `FILES_DIR = APP_DIR / "files"` → double.
- **Fix:** `if APP_DIR.name == "files": FILES_DIR = APP_DIR else: APP_DIR / "files"` + `.gitignore` semua runtime dir. ZCODE: langsung `context.filesDir` sebagai `APP_DIR`, jangan tambah `/files` lagi.

---

## 4. Keluarga LOW — yang bikin sinyal review tenggelam

- **F-09 / E-02:** Versi ngaco `main.py` bilang v1.0.0, `web_app.py` 1.2.1, `lib_manager.py` User-Agent `Zabacode/1.2.0`. Ruff `E501 347 hits`, `W293 129`. ZCODE: `versionCatalog` + `BuildConfig.VERSION_NAME` satu sumber, `detekt` line-length 120, `ktlint` strict.
- **F-06:** CI mati 6 tests. ZCODE: `gradlew test` (bukan `test_main.py` doang) + `check.sh` yang mirror CI (sudah ada di ZABACODE `tools/check.sh`).
- **E-01:** `MAX_FILENAME_LEN 128` tapi `files/foo...255` → OSError 500. Sudah fix `try/except OSError` di `read_file/delete_file`. ZCODE: `secure_filename` + `try/catch`.
- **B-17:** Styling kartu Oracle tidak kepakai — sudah fix. ZCODE: Compose `Card` + `AnnotatedString`.

---

## 5. Daftar Lengkap CHANGELOG Unreleased yang Sudah Di-fix (tapi wajib dijaga di ZCODE)

1. **Beautifier longest-first** (sudah)
2. **Syntax Guard** via `/api/check` (sudah)
3. **Oracle pip claim** `bypass TLS` → `verified TLS via certifi` (sudah)
4. **Oracle `input()` claim** `use Interactive Run mode` → `press RUN, terminal pauses at input()` (sudah)
5. **Image capture** shared `collect_new_images` (sudah)
6. **`fix my code` pakai `auto_fix_code`** (sudah)
7. **Editor empty vs valid** (sudah)
8. **Line echo tappable** (sudah)
9. **Chained traceback, substring, async def, review notes** (sudah)
10. **Auto-Fix safety:** fingerprint `tabId+revision`, bounded diff, single Ace transaction, guarded Undo (sudah — lihat `renderDiffView` + `applyFix` di `index.html:3483-3646`)

Semua ini ada test penahan (272 tests) — ZCODE wajib port jadi `junit` biar tidak regresi.

---

## 6. Penanganan ZCODE — Prinsip Honest & Aware

**Arsitektur yang menghapus kelas bug:**
- **No loopback server** (hapus F-01, S-27, C-50) → JS Bridge `file://`
- **No prelude injection** (hapus F-07, B-18) → wrapper process, bukan inject 9 baris
- **No `--trusted-host`** (hapus S-22) → OkHttp + certifi pinned
- **No `files/files`** (hapus S-20) → `APP_DIR = filesDir` langsung
- **No version drift** (hapus F-09, E-02) → `versionCatalog` + `BuildConfig`

**Guard yang harus di-copy 1:1:**
- `MAX_CODE_BYTES 512KB`, `MAX_OUTPUT 256KB`, `queue 10k`, `120s/60s`, `8KB input`
- `is_success = ast.parse(after)` + `ok = is_success and fixes`
- `longest-first OPERATORS` + `AST identical` test
- `depth-aware '=' → '=='`
- `collect_new_images(baseline)` + `dedup` + `8MB skip`
- `taskAffinity + singleTop` + debounce `100ms` resize (pelajaran ZMUX)
- `Privacy persist toggle + clear` + disclosure plain text

**Testing yang menutup F-05/F-06:**
- Test negatif `Content-Type` missing → 400 (port `AUDIT_REPORT` F-05)
- `atomically` `tab revision` test untuk Auto-Fix fingerprint
- `beautifier` 125 kombinasi AST identical
- CI `gradlew assembleDebug + lint + test` + `no unverified SSL` grep + `Ace bundled` check (copy `tools/check.sh`)

---

## 7. Apa yang Selanjutnya Buat ZCODE?

Kalau dokumen ini di-ACC, next commit:
1. Update `docs/PLAN_ZCODE.md` v0.2 tambah section “3 jebakan ZMUX (wrap/pembatas/resize)” + checklist anti-regresi di atas
2. `gradlew init` Fase 0 dengan skeleton yang langsung mengandung guard `MAX_CODE_BYTES`, `debounce resize`, `taskAffinity`, `versionCatalog` — jadi bug nggak sempat lahir lagi.

*— Audit akar selesai, 33 bug terdokumentasi, siap di-review bareng. Kalau ada yang mau ditambahin (misal kamu pernah nemu bug lain di HP yang belum ke-capture), drop aja — kita tambahin sebelum coding.*
