package com.example.healthchat

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.healthchat.data.HealthConnectManager
import com.example.healthchat.data.SettingsRepository
import com.example.healthchat.ui.screens.ChatScreen
import com.example.healthchat.ui.screens.SettingsScreen
import com.example.healthchat.viewmodel.ChatViewModel
import com.example.healthchat.viewmodel.ChatViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var chatViewModel: ChatViewModel

    private val requestPermissionActivityContract = registerForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(healthConnectManager.permissions)) {
            Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions denied.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsRepository = SettingsRepository(applicationContext)
        healthConnectManager = HealthConnectManager(applicationContext)

        val factory = ChatViewModelFactory(settingsRepository, healthConnectManager)
        chatViewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "chat") {
                        composable("chat") {
                            ChatScreen(
                                viewModel = chatViewModel,
                                onNavigateToSettings = { navController.navigate("settings") },
                                onRequestPermissions = { checkAndRequestPermissions() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                settingsRepository = settingsRepository,
                                onNavigateBack = { navController.popBackStack() },
                                onSettingsChanged = { chatViewModel.clearChat() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        lifecycleScope.launch {
            if (!healthConnectManager.hasAllPermissions()) {
                requestPermissionActivityContract.launch(healthConnectManager.permissions)
            } else {
                Toast.makeText(this@MainActivity, "Permissions already granted", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
