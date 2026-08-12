package com.zaba.zcode.core.files

import android.content.Context
import java.io.File

/**
 * Paths — port of zabacode/core/paths.py (F-07, S-20 fixed)
 * S-20: no files/files double nesting — APP_DIR is filesDir directly, FILES_DIR = filesDir/files is WRONG on Android
 * Fase 0: use filesDir as APP_DIR, avoid double nesting
 */
object Paths {
    fun appDir(context: Context): File = context.filesDir // APP_DIR = filesDir, not filesDir/files

    fun filesDir(context: Context): File {
        val app = appDir(context)
        // S-20 fix: if APP_DIR.name == "files", FILES_DIR = APP_DIR else APP_DIR/files
        // On Android filesDir is /data/data/com.zaba.zcode/files — name is "files" so FILES_DIR = APP_DIR
        return if (app.name == "files") app else File(app, "files").apply { mkdirs() }
    }

    fun cacheDir(context: Context): File = File(appDir(context), "cache").apply { mkdirs() }
    fun userPackagesDir(context: Context): File = File(appDir(context), "user_packages").apply { mkdirs() }
    fun keysFile(context: Context): File = File(appDir(context), ".zabacode_keys_encrypted.json")
    fun tokenFile(context: Context): File = File(appDir(context), ".zabacode_auth_token")

    // ------------------------------------------------------------------
    // python-env — environment package ZCODE (SPEC-001 §2)
    //   python-env/
    //     site-packages/<normalized>/<version>/   ← versi terpasang
    //     transactions/<tx-id>/                   ← staging install
    //     wheels/                                 ← cache wheel (offline reuse)
    //     metadata/                               ← metadata package lokal
    //     logs/<tx-id>.log                        ← log install
    //     state/                                  ← runtime.json, installed.json, dll
    // ------------------------------------------------------------------
    fun pythonEnvDir(context: Context): File = File(appDir(context), "python-env").apply { mkdirs() }
    fun pythonSitePackages(context: Context): File =
        File(pythonEnvDir(context), "site-packages").apply { mkdirs() }
    fun pythonTransactions(context: Context): File =
        File(pythonEnvDir(context), "transactions").apply { mkdirs() }
    /**
     * Cache wheel, DIPISAH PER-ABI (BUG G — fix 2026-08-13).
     *
     * Sebelumnya semua wheel ditumpuk di satu folder `wheels/`. Nama file wheel
     * Chaquopy memang memuat ABI-nya, tetapi cache itu ikut terbawa saat user
     * mem-backup lalu me-restore ZCODE ke perangkat dengan ABI berbeda
     * (ARMv7 → ARM64 atau sebaliknya). Wheel ABI asing yang tersisa di cache
     * bisa terpilih sebagai "sumber lokal", dan memuat .so ABI salah berakhir
     * **SIGSEGV native** — crash tanpa traceback yang mustahil didiagnosis di
     * perangkat tanpa logcat.
     *
     * Pola ini ditiru dari espressif/idf-python-wheels yang memisahkan indeks
     * ARMv7 vs lainnya dan menambahkan `check_wheel_collisions`:
     * https://github.com/espressif/idf-python-wheels
     */
    fun pythonWheels(context: Context): File =
        File(pythonWheelsRoot(context), currentAbi()).apply { mkdirs() }

    /** Akar cache wheel (berisi satu subfolder per-ABI). */
    fun pythonWheelsRoot(context: Context): File =
        File(pythonEnvDir(context), "wheels").apply { mkdirs() }

    /**
     * ABI utama perangkat, dinormalkan untuk dipakai sebagai nama folder.
     * Contoh: `armeabi-v7a` → `armeabi_v7a`. Fallback `unknown_abi`.
     */
    fun currentAbi(): String {
        val raw = try {
            android.os.Build.SUPPORTED_ABIS.firstOrNull()
        } catch (e: Throwable) {
            null
        }
        val name = (raw ?: "").trim().lowercase().replace('-', '_')
        // Hanya izinkan karakter aman untuk nama folder.
        val safe = name.filter { it.isLetterOrDigit() || it == '_' }
        return if (safe.isEmpty()) "unknown_abi" else safe
    }
    fun pythonMetadata(context: Context): File =
        File(pythonEnvDir(context), "metadata").apply { mkdirs() }
    fun pythonLogs(context: Context): File =
        File(pythonEnvDir(context), "logs").apply { mkdirs() }
    fun pythonState(context: Context): File =
        File(pythonEnvDir(context), "state").apply { mkdirs() }

    /** Log full-output terminal per run: <filesDir>/logs/runs/<run-id>.log */
    fun runLogsDir(context: Context): File =
        File(appDir(context), "logs/runs").apply { mkdirs() }
}
