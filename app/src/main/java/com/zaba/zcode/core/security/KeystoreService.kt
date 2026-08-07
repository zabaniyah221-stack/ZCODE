package com.zaba.zcode.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * KeystoreService — port of zabacode/core/security.py + keystore.py (S-19, S-25)
 * Primary: EncryptedSharedPreferences AES256_GCM + AES256_SIV
 * Fallback: DataStore encrypted file (not in Fase 0, but contract ready)
 * Central ALLOWED_PROVIDERS to avoid missing custom (S-19)
 */
object KeystoreService {
    val allowedProviders = setOf("openrouter", "gemini", "groq", "mistral", "deepseek", "ollama", "custom")

    fun saveKey(context: Context, provider: String, apiKey: String): Boolean {
        if (provider !in allowedProviders) return false
        return try {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val prefs = EncryptedSharedPreferences.create(
                context, "zabacode_secure_keys", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            prefs.edit().putString(provider, apiKey).apply()
            true
        } catch (e: Exception) { false }
    }

    fun loadKeys(context: Context): Map<String, String> {
        return try {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val prefs = EncryptedSharedPreferences.create(
                context, "zabacode_secure_keys", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            allowedProviders.mapNotNull { p -> prefs.getString(p, null)?.let { p to it } }.toMap()
        } catch (e: Exception) { emptyMap() }
    }
}
