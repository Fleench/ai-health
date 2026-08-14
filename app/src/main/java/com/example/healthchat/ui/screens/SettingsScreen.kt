package com.example.healthchat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthchat.data.SettingsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onNavigateBack: () -> Unit,
    onSettingsChanged: () -> Unit
) {
    val initialApiKey by settingsRepository.apiKey.collectAsState()
    val initialPrompt by settingsRepository.systemPrompt.collectAsState()

    var apiKey by remember { mutableStateOf(initialApiKey) }
    var prompt by remember { mutableStateOf(initialPrompt) }
    var hasChanges by remember { mutableStateOf(false) }

    LaunchedEffect(apiKey, prompt) {
        hasChanges = apiKey != initialApiKey || prompt != initialPrompt
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("System Prompt") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                maxLines = 10
            )

            Button(
                onClick = {
                    settingsRepository.saveApiKey(apiKey)
                    settingsRepository.saveSystemPrompt(prompt)
                    hasChanges = false
                    onSettingsChanged()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasChanges
            ) {
                Text("Save Settings")
            }
        }
    }
}
