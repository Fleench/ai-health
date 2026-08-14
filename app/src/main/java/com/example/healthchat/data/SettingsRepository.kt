package com.example.healthchat.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "secret_shared_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val standardPrefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(encryptedPrefs.getString(KEY_API_KEY, "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _systemPrompt = MutableStateFlow(
        standardPrefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_PROMPT) ?: DEFAULT_PROMPT
    )
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    fun saveApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_API_KEY, key).apply()
        _apiKey.value = key
    }

    fun saveSystemPrompt(prompt: String) {
        standardPrefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply()
        _systemPrompt.value = prompt
    }

    companion object {
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val DEFAULT_PROMPT = "You are a helpful, intelligent health and fitness companion."
    }
}
