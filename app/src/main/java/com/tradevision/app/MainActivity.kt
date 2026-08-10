package com.tradevision.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradevision.app.data.SettingsRepository
import com.tradevision.app.ui.ChartScreen
import com.tradevision.app.ui.ChartViewModel
import com.tradevision.app.ui.theme.TradeVisionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepo = SettingsRepository(this)
        val savedSettings = settingsRepo.blockingSettings()

        setContent {
            TradeVisionTheme {
                val vm: ChartViewModel = viewModel(
                    factory = ChartViewModelFactory(savedSettings),
                )
                ChartScreen(vm)
            }
        }
    }
}

class ChartViewModelFactory(
    private val settings: com.tradevision.app.data.AppSettings,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChartViewModel::class.java)) {
            return ChartViewModel(settings) as T
        }
        throw IllegalArgumentException("Unknown VM: ${modelClass.name}")
    }
}