package com.tradevision.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradevision.app.data.AppSettings
import com.tradevision.app.data.HealthStatus
import com.tradevision.app.data.SettingsRepository
import com.tradevision.app.network.RestClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val vm: MainViewModel = viewModel(factory = MainViewModelFactory(ctx))
    val ui by vm.ui.collectAsStateWithLifecycle()

    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("TradeVision", style = MaterialTheme.typography.headlineMedium)
            Text("Phase 1 — Backend connection", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            // Settings card
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Backend URL") },
                        placeholder = { Text("http://10.0.2.2:8000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { vm.onSettingsChanged(baseUrl, apiKey) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Connect") }
                }
            }
            Spacer(Modifier.height(12.dp))

            when (val s = ui) {
                is HealthUiState.Idle -> InfoCard("Set URL + key, tap Connect")
                is HealthUiState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is HealthUiState.Ok -> HealthCard(s.health, s.ts)
                is HealthUiState.Error -> ErrorCard(s.message, s.ts)
            }
        }
    }
}

@Composable
private fun HealthCard(h: HealthStatus, ts: Long) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Backend", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Dot(h.status == "ok", Color(0xFF00C853), Color(0xFFE53935))
                Text(" ${h.status}")
            }
            Text(
                "base: ${h.sourceUrl}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("OANDA", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Dot(h.oanda == "connected", Color(0xFF00C853), Color(0xFFE53935))
                Text(" ${h.oanda}")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Binance", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Dot(h.binance == "connected", Color(0xFF00C853), Color(0xFFE53935))
                Text(" ${h.binance}")
            }
            Spacer(Modifier.height(4.dp))
            Text("Supabase candles: ${h.candleCount}  (${"%.2f".format(h.storageMb)} MB)", style = MaterialTheme.typography.bodySmall)
            Text("history: cache ${h.cacheHits} · fetch ${h.brokerFetches} · invalid ${h.invalid}", style = MaterialTheme.typography.bodySmall)
            Text("uptime: ${fmtUptime(h.uptimeSeconds)}", style = MaterialTheme.typography.bodySmall)
            Text("updated ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(ts))}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(16.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorCard(message: String, ts: Long) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
        Column(Modifier.padding(16.dp)) {
            Text("Connection failed", color = Color(0xFFB71C1C), style = MaterialTheme.typography.titleMedium)
            Text(
                message,
                color = Color(0xFFB71C1C),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun Dot(ok: Boolean, onColor: Color = Color(0xFF00C853), offColor: Color = Color(0xFFE53935)) {
    Box(
        Modifier
            .size(10.dp)
            .background(if (ok) onColor else offColor, RoundedCornerShape(50)),
    )
}

private fun fmtUptime(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return "%dh %02dm %02ds".format(h, m, s)
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        val app = context.applicationContext
        val settingsRepo = SettingsRepository(app)
        val restClient = RestClient(
            baseUrlProvider = { runBlockingGet(settingsRepo.settings).baseUrl },
            apiKeyProvider = { runBlockingGet(settingsRepo.settings).apiKey },
        )
        return MainViewModel(settingsRepo, restClient) as T
    }
}

private fun runBlockingGet(flow: Flow<AppSettings>): AppSettings =
    try {
        runBlocking { flow.first() }
    } catch (e: Exception) {
        AppSettings()
    }