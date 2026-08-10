package com.tradevision.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradevision.app.data.AppSettings
import com.tradevision.app.data.SettingsRepository
import com.tradevision.app.ui.ChartScreen
import com.tradevision.app.ui.ChartViewModel
import com.tradevision.app.ui.SettingsScreen
import com.tradevision.app.ui.theme.TradeVisionTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepo = SettingsRepository(this)
        val savedSettings = settingsRepo.blockingSettings()

        setContent {
            TradeVisionTheme {
                var showSettings by remember { mutableStateOf(false) }

                if (showSettings) {
                    SettingsScreen(
                        initialBaseUrl = savedSettings.baseUrl,
                        initialApiKey = savedSettings.apiKey,
                        onSave = { url, key ->
                            CoroutineScope(Dispatchers.IO).launch {
                                settingsRepo.save(url, key)
                            }
                            // reflect in current VM by recreating activity state
                            savedSettings.baseUrl = url
                            savedSettings.apiKey = key
                            showSettings = false
                        },
                        onBack = { showSettings = false },
                    )
                } else {
                    val vm: ChartViewModel = viewModel(
                        factory = ChartViewModelFactory(savedSettings),
                    )
                    ChartScreen(vm, onOpenSettings = { showSettings = true })
                }
            }
        }
    }
}

class ChartViewModelFactory(
    private val settings: AppSettings,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChartViewModel::class.java)) {
            return ChartViewModel(settings) as T
        }
        throw IllegalArgumentException("Unknown VM: ${modelClass.name}")
    }
}