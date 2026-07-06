package com.example.nongkanvelaassistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureSettingsStore(context: Context) {
    companion object {
        private const val SECURE_PREFS_NAME = "nongkanvela_secure_prefs"
        private const val KEY_GROQ_KEYS = "groq_keys"
    }

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getGroqKeysCsv(): String = securePrefs.getString(KEY_GROQ_KEYS, "") ?: ""

    fun setGroqKeysCsv(keysCsv: String) {
        securePrefs.edit().putString(KEY_GROQ_KEYS, keysCsv).apply()
    }

    fun migrateGroqKeysFrom(legacyPrefs: SharedPreferences) {
        val secureValue = getGroqKeysCsv()
        if (secureValue.isNotBlank()) {
            legacyPrefs.edit().remove(KEY_GROQ_KEYS).apply()
            return
        }

        val legacyValue = legacyPrefs.getString(KEY_GROQ_KEYS, "")?.trim().orEmpty()
        if (legacyValue.isBlank()) return

        setGroqKeysCsv(legacyValue)
        legacyPrefs.edit().remove(KEY_GROQ_KEYS).apply()
    }
}
