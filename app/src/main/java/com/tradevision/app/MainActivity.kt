package com.tradevision.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradevision.app.data.AppSettings
import com.tradevision.app.data.SettingsRepository
import com.tradevision.app.ui.ChartScreen
import com.tradevision.app.ui.ChartViewModel
import com.tradevision.app.ui.alerts.AlertsScreen
import com.tradevision.app.ui.alerts.AlertsViewModel
import com.tradevision.app.ui.settings.SettingsScreen
import com.tradevision.app.ui.theme.TradeVisionTheme
import com.tradevision.app.ui.theme.TvAccent
import com.tradevision.app.ui.theme.TvBg
import com.tradevision.app.ui.theme.TvBorder
import com.tradevision.app.ui.theme.TvText
import com.tradevision.app.ui.theme.TvTextDim
import com.tradevision.app.ui.watchlist.WatchlistScreen
import com.tradevision.app.ui.watchlist.WatchlistViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: String) {
    WATCHLIST("Watchlist", "◧"),
    CHART("Chart", "◫"),
    ALERTS("Alerts", "🔔"),
    SETTINGS("Settings", "⚙"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepo = SettingsRepository(this)
        val savedSettings = settingsRepo.blockingSettings()

        setContent {
            TradeVisionTheme {
                MainScaffold(savedSettings, settingsRepo)
            }
        }
    }
}

@Composable
private fun MainScaffold(
    savedSettings: AppSettings,
    settingsRepo: SettingsRepository,
) {
    var currentTab by remember { mutableIntStateOf(Tab.WATCHLIST.ordinal) }
    var selectedSymbol by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(savedSettings) }

    // VMs (recreated when settings change)
    val watchlistVm: WatchlistViewModel = viewModel(factory = ChartViewModelFactory(settings))
    val chartVm: ChartViewModel = viewModel(factory = ChartViewModelFactory(settings))
    val alertsVm: AlertsViewModel = viewModel(factory = ChartViewModelFactory(settings))

    fun saveSettings(url: String, key: String) {
        CoroutineScope(Dispatchers.IO).launch { settingsRepo.save(url, key) }
        settings = AppSettings(url, key)
        showSettings = false
    }

    Column(Modifier.fillMaxSize().background(TvBg)) {
        // Content
        Box(Modifier.weight(1f)) {
            when {
                showSettings -> SettingsScreen(
                    initialBaseUrl = settings.baseUrl,
                    initialApiKey = settings.apiKey,
                    wsStatus = chartVm.wsStatus.value,
                    onSave = ::saveSettings,
                    onBack = { showSettings = false },
                )
                currentTab == Tab.WATCHLIST.ordinal -> WatchlistScreen(
                    viewModel = watchlistVm,
                    onOpenSymbol = { sym ->
                        selectedSymbol = sym
                        currentTab = Tab.CHART.ordinal
                    },
                    onOpenSettings = {
                        showSettings = true
                    },
                )
                currentTab == Tab.CHART.ordinal -> {
                    // sync symbol into chart VM when opened from watchlist
                    val target = selectedSymbol
                    if (target != null) {
                        chartVm.selectSymbol(target)
                        selectedSymbol = null
                    }
                    ChartScreen(
                        viewModel = chartVm,
                        onBackToWatchlist = { currentTab = Tab.WATCHLIST.ordinal },
                        onOpenSettings = { showSettings = true },
                    )
                }
                currentTab == Tab.ALERTS.ordinal -> AlertsScreen(alertsVm)
                currentTab == Tab.SETTINGS.ordinal -> SettingsScreen(
                    initialBaseUrl = settings.baseUrl,
                    initialApiKey = settings.apiKey,
                    wsStatus = chartVm.wsStatus.value,
                    onSave = ::saveSettings,
                    onBack = { currentTab = Tab.WATCHLIST.ordinal },
                )
            }
        }

        // Bottom navigation — glass bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xE60F141B))
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { tab ->
                val active = currentTab == tab.ordinal
                Column(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            currentTab = tab.ordinal
                            showSettings = false
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(tab.icon, color = if (active) TvAccent else TvTextDim)
                    Text(
                        tab.label,
                        color = if (active) TvText else TvTextDim,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    )
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
        return when {
            modelClass.isAssignableFrom(ChartViewModel::class.java) -> ChartViewModel(settings) as T
            modelClass.isAssignableFrom(WatchlistViewModel::class.java) -> WatchlistViewModel(settings) as T
            modelClass.isAssignableFrom(AlertsViewModel::class.java) -> AlertsViewModel() as T
            else -> throw IllegalArgumentException("Unknown VM: ${modelClass.name}")
        }
    }
}
