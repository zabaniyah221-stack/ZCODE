"""
package_runtime — ZCODE Package Runtime Platform (SPEC-001).

Semua logika package-management yang bisa diuji lintas platform (sandbox/CI/Android)
diletakkan di sini, dieksekusi di dalam Chaquopy CPython 3.11 saat runtime.
Kotlin (PackageEngineV2) hanya orkestrasi + lapisan keamanan file (Verifier.kt).

Aturan SPEC-001 yang dipegang di sini:
- wheel-first: MVP TIDAK menerima sdist / source build / VCS / editable.
- self-contained: TIDAK mengandalkan pip.main() sebagai public API untuk install.
- false success dilarang: resolusi + verifikasi + smoke test adalah satu rantai.
- unsupported adalah hasil yang valid (INCOMPATIBLE / UNAVAILABLE), bukan install palsu.
"""
