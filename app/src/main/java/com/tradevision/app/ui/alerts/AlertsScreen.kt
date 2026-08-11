package com.tradevision.app.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tradevision.app.data.AlertCondition
import com.tradevision.app.data.Instrument
import com.tradevision.app.data.PriceAlert
import com.tradevision.app.ui.theme.Glass
import com.tradevision.app.ui.theme.TvAccent
import com.tradevision.app.ui.theme.TvBg
import com.tradevision.app.ui.theme.TvBorder
import com.tradevision.app.ui.theme.TvGreen
import com.tradevision.app.ui.theme.TvRed
import com.tradevision.app.ui.theme.TvText
import com.tradevision.app.ui.theme.TvTextDim

@Composable
fun AlertsScreen(viewModel: AlertsViewModel) {
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    var symbol by remember { mutableStateOf(Instrument.BTCUSDT.label) }
    var condition by remember { mutableStateOf(AlertCondition.PRICE_ABOVE) }
    var price by remember { mutableStateOf("") }
    var showCondMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(TvBg).padding(14.dp)) {
        Text("Alerts", color = TvText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        // Create alert card
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // symbol dropdown
                var showSymMenu by remember { mutableStateOf(false) }
                Box {
                    Text(
                        symbol,
                        color = TvText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x14FFFFFF))
                            .clickable { showSymMenu = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    DropdownMenu(expanded = showSymMenu, onDismissRequest = { showSymMenu = false }) {
                        Instrument.entries.forEach { ins ->
                            DropdownMenuItem(text = { Text(ins.label, color = TvText) }, onClick = {
                                symbol = ins.label
                                showSymMenu = false
                            })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Text(
                            condition.name.replace("_", " "),
                            color = TvAccent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(TvAccent.copy(alpha = 0.15f))
                                .clickable { showCondMenu = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                        DropdownMenu(expanded = showCondMenu, onDismissRequest = { showCondMenu = false }) {
                            AlertCondition.entries.forEach { c ->
                                DropdownMenuItem(text = { Text(c.name.replace("_", " "), color = TvText) }, onClick = {
                                    condition = c
                                    showCondMenu = false
                                })
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price", color = TvTextDim) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Button(
                    onClick = {
                        val p = price.toDoubleOrNull()
                        if (p != null) {
                            viewModel.addAlert(symbol, condition, p)
                            price = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Create Alert", color = Color.White) }
            }
        }
        Spacer(Modifier.height(14.dp))

        // List
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (alerts.isEmpty()) {
                item { Text("No alerts yet — create one above.", color = TvTextDim, modifier = Modifier.padding(8.dp)) }
            }
            items(alerts, key = { it.id }) { a ->
                AlertRow(a) { viewModel.toggleAlert(a.id) }
            }
        }
    }
}

@Composable
private fun AlertRow(a: PriceAlert, onToggle: () -> Unit) {
    Glass(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(a.symbol, color = TvText, fontWeight = FontWeight.SemiBold)
                Text(
                    "${a.condition.name.replace("_", " ")} ${a.price}",
                    color = TvTextDim,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (a.active) "ACTIVE" else "OFF", color = if (a.active) TvGreen else TvTextDim, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(6.dp))
                Switch(checked = a.active, onCheckedChange = { onToggle() })
            }
        }
    }
}
