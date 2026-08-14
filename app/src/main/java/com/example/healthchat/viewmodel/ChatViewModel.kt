package com.example.healthchat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthchat.data.HealthConnectManager
import com.example.healthchat.data.SettingsRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.FunctionDeclaration
import com.google.ai.client.generativeai.type.FunctionResponsePart
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.defineFunction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val isError: Boolean = false
)

class ChatViewModel(
    private val settingsRepository: SettingsRepository,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isFetchingHealthData = MutableStateFlow(false)
    val isFetchingHealthData: StateFlow<Boolean> = _isFetchingHealthData.asStateFlow()

    private var chat: com.google.ai.client.generativeai.Chat? = null

    private val getHealthDataTool = Tool(
        listOf(
            defineFunction(
                name = "get_health_data",
                description = "Fetches the user's health and fitness data from their device hardware for a specific day.",
                parameters = listOf(
                    Schema.str("dataType", "The type of health data to fetch. Allowed values: 'steps', 'heart_rate', 'sleep', 'calories'"),
                    Schema.int("daysAgo", "The number of days ago to fetch data for. 0 for today, 1 for yesterday, etc.")
                )
            )
        )
    )

    private fun initChat() {
        if (chat != null) return
        val apiKey = settingsRepository.apiKey.value
        if (apiKey.isEmpty()) return

        val systemPrompt = settingsRepository.systemPrompt.value

        val generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey,
            systemInstruction = content { text(systemPrompt) },
            tools = listOf(getHealthDataTool)
        )

        chat = generativeModel.startChat()
    }

    fun clearChat() {
        _messages.value = emptyList()
        chat = null
        initChat()
    }

    fun sendMessage(userText: String) {
        val apiKey = settingsRepository.apiKey.value
        if (apiKey.isEmpty()) {
            _messages.value += ChatMessage(text = "Please set your Gemini API Key in Settings first.", isFromUser = false, isError = true)
            return
        }

        if (chat == null) {
            initChat()
        }

        _messages.value += ChatMessage(text = userText, isFromUser = true)
        _isLoading.value = true

        viewModelScope.launch {
            try {
                var response = chat?.sendMessage(userText)

                // Handle Function Calls
                while (response != null && response.functionCalls.isNotEmpty()) {
                    _isFetchingHealthData.value = true

                    val functionCall = response.functionCalls.first()
                    val functionName = functionCall.name

                    val functionResponsePart = if (functionName == "get_health_data") {
                        val args = functionCall.args
                        val dataType = args["dataType"] as? String ?: ""
                        // handle Double/Integer safely
                        val daysAgoRaw = args["daysAgo"]
                        val daysAgo = when (daysAgoRaw) {
                            is Double -> daysAgoRaw.toInt()
                            is Int -> daysAgoRaw
                            is String -> daysAgoRaw.toIntOrNull() ?: 0
                            else -> 0
                        }

                        val jsonResult = handleGetHealthData(dataType, daysAgo)

                        FunctionResponsePart(functionName, JSONObject(jsonResult))
                    } else {
                        FunctionResponsePart(functionName, JSONObject(mapOf("error" to "Unknown function")))
                    }

                    // Send function response back to the model
                    val content = content {
                        part(functionResponsePart)
                    }
                    response = chat?.sendMessage(content)
                }

                _isFetchingHealthData.value = false

                val responseText = response?.text
                if (responseText != null) {
                    _messages.value += ChatMessage(text = responseText, isFromUser = false)
                }

            } catch (e: Exception) {
                _isFetchingHealthData.value = false
                _messages.value += ChatMessage(
                    text = "Error: ${e.localizedMessage}",
                    isFromUser = false,
                    isError = true
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun handleGetHealthData(dataType: String, daysAgo: Int): Map<String, Any> {
        return try {
            if (!healthConnectManager.hasAllPermissions()) {
                return mapOf("error" to "Missing Health Connect Permissions. Tell the user to grant them.")
            }

            // Calculate time range
            val now = Instant.now()
            val zoneId = ZoneId.systemDefault()
            val endOfDay = now.atZone(zoneId)
                .minusDays(daysAgo.toLong())
                .withHour(23).withMinute(59).withSecond(59)
                .toInstant()

            val startOfDay = now.atZone(zoneId)
                .minusDays(daysAgo.toLong())
                .withHour(0).withMinute(0).withSecond(0)
                .toInstant()

            when (dataType.lowercase()) {
                "steps" -> {
                    val steps = healthConnectManager.getStepsBetween(startOfDay, endOfDay)
                    mapOf("dataType" to "steps", "value" to steps, "date" to startOfDay.toString())
                }
                "heart_rate" -> {
                    val hr = healthConnectManager.getHeartRateBetween(startOfDay, endOfDay)
                    if (hr != null) {
                        mapOf("dataType" to "heart_rate", "value" to hr, "date" to startOfDay.toString())
                    } else {
                        mapOf("error" to "No heart rate data found for this date")
                    }
                }
                "sleep" -> {
                    val sleepHours = healthConnectManager.getSleepBetween(startOfDay, endOfDay)
                    mapOf("dataType" to "sleep", "value_hours" to sleepHours, "date" to startOfDay.toString())
                }
                "calories" -> {
                    val cals = healthConnectManager.getCaloriesBetween(startOfDay, endOfDay)
                    mapOf("dataType" to "calories", "value_kcal" to cals, "date" to startOfDay.toString())
                }
                else -> {
                    mapOf("error" to "Unknown data type: $dataType")
                }
            }
        } catch (e: Exception) {
            mapOf("error" to "Failed to fetch data: ${e.message}")
        }
    }
}
