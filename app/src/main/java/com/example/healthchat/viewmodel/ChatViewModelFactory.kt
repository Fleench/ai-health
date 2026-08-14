package com.example.healthchat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.healthchat.data.HealthConnectManager
import com.example.healthchat.data.SettingsRepository

class ChatViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val healthConnectManager: HealthConnectManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(settingsRepository, healthConnectManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
