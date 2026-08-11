package com.tradevision.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradevision.app.network.LiveCandleClient
import com.tradevision.app.ui.theme.Glass
import com.tradevision.app.ui.theme.TvBg
import com.tradevision.app.ui.theme.TvGreen
import com.tradevision.app.ui.theme.TvRed
import com.tradevision.app.ui.theme.TvText
import com.tradevision.app.ui.theme.TvTextDim

@Composable
fun SettingsScreen(
    initialBaseUrl: String,
    initialApiKey: String,
    wsStatus: LiveCandleClient.WsStatus,
    onSave: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    // keyed on the persisted values so the fields refresh when settings change
    var baseUrl by remember(initialBaseUrl) { mutableStateOf(initialBaseUrl) }
    var apiKey by remember(initialApiKey) { mutableStateOf(initialApiKey) }

    Column(
        Modifier.fillMaxSize().background(TvBg).padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = TvText, fontSize = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Transparent))
            Spacer(Modifier.width(8.dp))
            Text("Settings", color = TvText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        // ---- Connection ----
        SectionHeader("Connection")
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Backend URL", color = TvTextDim) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key", color = TvTextDim) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                StatusRow("Backend", status = if (baseUrl.isNotBlank()) "Connected" else "Disconnected",
                    ok = baseUrl.isNotBlank())
                StatusRow("WebSocket",
                    status = when (wsStatus) {
                        LiveCandleClient.WsStatus.CONNECTED -> "Connected"
                        LiveCandleClient.WsStatus.CONNECTING -> "Connecting"
                        LiveCandleClient.WsStatus.RECONNECTING -> "Reconnecting"
                        LiveCandleClient.WsStatus.CLOSED -> "Disconnected"
                    },
                    ok = wsStatus == LiveCandleClient.WsStatus.CONNECTED,
                    warn = wsStatus == LiveCandleClient.WsStatus.CONNECTING || wsStatus == LiveCandleClient.WsStatus.RECONNECTING,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- Chart ----
        SectionHeader("Chart")
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("• Theme: Dark (fixed)", color = TvTextDim)
                Text("• Candle style: Hollow/Standard", color = TvTextDim)
                Text("• Grid: subtle", color = TvTextDim)
                Text("• Auto scale: ON", color = TvTextDim)
                Text("• Live follow: enabled (tap chart to resume)", color = TvTextDim)
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- Watchlist ----
        SectionHeader("Watchlist")
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("• 12 symbols (3 crypto + 9 forex)", color = TvTextDim)
                Text("• Sort: Gainers / Losers / Symbol / Change %", color = TvTextDim)
                Text("• Manage symbols: coming in a later phase", color = TvTextDim)
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- About ----
        SectionHeader("About")
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("TradeVision", color = TvText, fontWeight = FontWeight.SemiBold)
                Text("Version 0.4.0 (Phase 4)", color = TvTextDim)
                Text("Native Android + Cloudflare Tunnel backend", color = TvTextDim)
            }
        }
        Spacer(Modifier.height(18.dp))

        Button(
            onClick = { onSave(baseUrl.trim(), apiKey.trim()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save", color = Color.White) }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = TvTextDim,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun StatusRow(label: String, status: String, ok: Boolean, warn: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TvTextDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            "● $status",
            color = when {
                ok -> TvGreen
                warn -> Color(0xFFFFB300)
                else -> TvRed
            },
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
