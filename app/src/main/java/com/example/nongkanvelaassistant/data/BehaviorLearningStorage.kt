package com.example.nongkanvelaassistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Local-only, encrypted learning signals. Raw microphone audio is never stored. */
data class LearnedPhrase(val text: String, val count: Int, val lastUsedAt: Long)

class BehaviorLearningStorage(context: Context) {
    private val gson = Gson()
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nongkanvela_behavior_learning",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun recordRecognizedPhrase(text: String) {
        val phrase = text.trim().replace(Regex("\\s+"), " ")
        if (phrase.isBlank()) return
        val current = load().toMutableList()
        val index = current.indexOfFirst { it.text.equals(phrase, ignoreCase = true) }
        val next = if (index >= 0) {
            current[index].copy(count = current[index].count + 1, lastUsedAt = System.currentTimeMillis())
        } else {
            LearnedPhrase(phrase, 1, System.currentTimeMillis())
        }
        if (index >= 0) current[index] = next else current.add(next)
        save(current.sortedByDescending { it.lastUsedAt }.take(MAX_PHRASES))
    }

    fun frequentPhrases(limit: Int = 12): List<LearnedPhrase> =
        load().sortedWith(compareByDescending<LearnedPhrase> { it.count }.thenByDescending { it.lastUsedAt }).take(limit)

    /**
     * A successful app choice is a useful correction signal: the next matching
     * spoken name can be handled locally instead of asking AI to guess again.
     */
    fun saveAppVoiceAlias(alias: String, packageName: String) {
        val cleanAlias = alias.trim().replace(Regex("\\s+"), " ")
        if (cleanAlias.isBlank() || packageName.isBlank()) return
        val updated = loadAppVoiceAliases().toMutableMap()
        updated[cleanAlias] = packageName
        prefs.edit().putString(KEY_APP_VOICE_ALIASES, gson.toJson(updated)).apply()
    }

    fun loadAppVoiceAliases(): Map<String, String> = runCatching {
        val type = object : TypeToken<Map<String, String>>() {}.type
        gson.fromJson<Map<String, String>>(prefs.getString(KEY_APP_VOICE_ALIASES, "{}"), type) ?: emptyMap()
    }.getOrDefault(emptyMap())

    private fun load(): List<LearnedPhrase> = runCatching {
        val type = object : TypeToken<List<LearnedPhrase>>() {}.type
        gson.fromJson<List<LearnedPhrase>>(prefs.getString(KEY_PHRASES, "[]"), type) ?: emptyList()
    }.getOrDefault(emptyList())

    private fun save(items: List<LearnedPhrase>) {
        prefs.edit().putString(KEY_PHRASES, gson.toJson(items)).apply()
    }

    private companion object {
        const val KEY_PHRASES = "phrases"
        const val KEY_APP_VOICE_ALIASES = "app_voice_aliases"
        const val MAX_PHRASES = 200
    }
}
