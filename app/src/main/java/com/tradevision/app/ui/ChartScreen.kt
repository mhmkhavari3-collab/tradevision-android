package com.tradevision.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tradevision.app.data.Candle
import com.tradevision.app.data.Instrument
import com.tradevision.app.data.Timeframe
import com.tradevision.app.ui.chart.CandleChart

@Composable
fun ChartScreen(viewModel: ChartViewModel, onOpenSettings: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val symbol by viewModel.symbol.collectAsStateWithLifecycle()
    val timeframe by viewModel.timeframe.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Color(0xFF0E1116)).padding(8.dp)) {
        // Symbol row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TradeVision", color = Color.White, style = MaterialTheme.typography.titleMedium)
            androidx.compose.material3.TextButton(onClick = onOpenSettings) {
                Text("⚙ Settings", color = Color(0xFF26A69A))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Instrument.entries.toList()) { ins ->
                FilterChip(
                    selected = symbol == ins.symbol,
                    onClick = { viewModel.selectSymbol(ins.symbol) },
                    label = { Text(ins.label, color = Color.White) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // Timeframe row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Timeframe.entries.toList()) { tf ->
                FilterChip(
                    selected = timeframe == tf.apiValue,
                    onClick = { viewModel.selectTimeframe(tf.apiValue) },
                    label = { Text(tf.label, color = Color.White) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Chart area
        Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                is ChartUiState.Idle -> Unit
                is ChartUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color(0xFF26A69A))
                is ChartUiState.Error -> ErrorRetry(s.message, Modifier.align(Alignment.Center)) { viewModel.load() }
                is ChartUiState.Success -> {
                    if (s.candles.isEmpty()) {
                        Text("No candles", color = Color.White, modifier = Modifier.align(Alignment.Center))
                    } else {
                        CandleChart(s.candles, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorRetry(message: String, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}