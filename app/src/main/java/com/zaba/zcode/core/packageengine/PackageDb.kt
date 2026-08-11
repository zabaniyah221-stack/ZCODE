package com.zaba.zcode.core.packageengine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * PackageDb — database package lokal (SPEC-001 §3 Package Database Schema).
 *
 * SQLite murni (android.database.sqlite, tanpa dependency baru) dengan schema
 * persis dari SPEC: packages, package_versions, artifacts, dependencies,
 * installed_packages, transactions.
 *
 * Catatan jujur: runtime state yang dipakai sys.path injection adalah
 * state/installed.json (baca cepat + atomic rename); SQLite di sini adalah
 * lapisan query/laporan (UI, dashboard telemetry).
 */
class PackageDb(context: Context) : SQLiteOpenHelper(context, "zcode_packages.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS packages (
                id INTEGER PRIMARY KEY,
                normalized_name TEXT NOT NULL UNIQUE,
                display_name TEXT NOT NULL,
                source TEXT NOT NULL,
                description TEXT,
                category TEXT,
                import_name TEXT,
                package_type TEXT,
                status TEXT NOT NULL,
                curated INTEGER NOT NULL DEFAULT 0,
                metadata_version INTEGER NOT NULL DEFAULT 1,
                updated_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS package_versions (
                id INTEGER PRIMARY KEY,
                package_id INTEGER NOT NULL,
                version TEXT NOT NULL,
                python_min TEXT,
                python_max TEXT,
                tested INTEGER NOT NULL DEFAULT 0,
                tested_at INTEGER,
                UNIQUE(package_id, version),
                FOREIGN KEY(package_id) REFERENCES packages(id)
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS artifacts (
                id INTEGER PRIMARY KEY,
                package_version_id INTEGER NOT NULL,
                filename TEXT NOT NULL,
                url TEXT NOT NULL,
                sha256 TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                python_tag TEXT,
                abi_tag TEXT,
                platform_tag TEXT,
                artifact_type TEXT NOT NULL,
                FOREIGN KEY(package_version_id) REFERENCES package_versions(id)
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS dependencies (
                id INTEGER PRIMARY KEY,
                package_version_id INTEGER NOT NULL,
                requirement TEXT NOT NULL,
                marker TEXT,
                FOREIGN KEY(package_version_id) REFERENCES package_versions(id)
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS installed_packages (
                id INTEGER PRIMARY KEY,
                normalized_name TEXT NOT NULL UNIQUE,
                version TEXT NOT NULL,
                dist_info_path TEXT NOT NULL,
                install_state TEXT NOT NULL,
                installed_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS transactions (
                id TEXT PRIMARY KEY,
                operation TEXT NOT NULL,
                state TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                completed_at INTEGER,
                error_code TEXT,
                error_message TEXT
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 — belum ada migrasi
    }

    fun upsertInstalled(
        normalizedName: String,
        version: String,
        distInfoPath: String,
        source: String,
        sha256: String?
    ) {
        try {
            val db = writableDatabase
            db.execSQL(
                "INSERT OR REPLACE INTO installed_packages " +
                    "(normalized_name, version, dist_info_path, install_state, installed_at) " +
                    "VALUES (?, ?, ?, 'INSTALLED', ?)",
                arrayOf(normalizedName, version, distInfoPath, System.currentTimeMillis())
            )
        } catch (e: Exception) {
            // DB error tidak boleh menggagalkan install
        }
    }

    fun deleteInstalled(normalizedName: String) {
        try {
            writableDatabase.delete("installed_packages", "normalized_name = ?", arrayOf(normalizedName))
        } catch (e: Exception) {
            // abaikan
        }
    }

    fun recordTransaction(id: String, operation: String, state: String, errorCode: String?, errorMessage: String?) {
        try {
            val db = writableDatabase
            db.execSQL(
                "INSERT OR REPLACE INTO transactions (id, operation, state, started_at, completed_at, error_code, error_message) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    id, operation, state, System.currentTimeMillis(),
                    if (state == "SUCCESS" || state == "ROLLED_BACK" || state == "ABORTED") System.currentTimeMillis() else null,
                    errorCode, errorMessage
                )
            )
        } catch (e: Exception) {
            // abaikan
        }
    }

    fun installedCount(): Int = try {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM installed_packages", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    } catch (e: Exception) {
        0
    }
}
