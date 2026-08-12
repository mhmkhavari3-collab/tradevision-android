package com.tradevision.app.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.tradevision.app.data.Candle
import com.tradevision.app.data.Drawing
import com.tradevision.app.data.DrawingTool
import com.tradevision.app.data.Instrument
import com.tradevision.app.data.Timeframe
import com.tradevision.app.network.LiveCandleClient
import com.tradevision.app.ui.chart.CandleChart
import com.tradevision.app.ui.theme.Glass
import com.tradevision.app.ui.theme.GlassPill
import com.tradevision.app.ui.theme.TvAccent
import com.tradevision.app.ui.theme.TvBg
import com.tradevision.app.ui.theme.TvBorder
import com.tradevision.app.ui.theme.TvGreen
import com.tradevision.app.ui.theme.TvRed
import com.tradevision.app.ui.theme.TvText
import com.tradevision.app.ui.theme.TvTextDim
import com.tradevision.app.ui.theme.changeColor
import com.tradevision.app.ui.theme.formatPrice

@Composable
fun ChartScreen(
    viewModel: ChartViewModel,
    onBackToWatchlist: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val symbol by viewModel.symbol.collectAsStateWithLifecycle()
    val timeframe by viewModel.timeframe.collectAsStateWithLifecycle()
    val wsStatus by viewModel.wsStatus.collectAsStateWithLifecycle()
    val drawings by viewModel.drawings.collectAsStateWithLifecycle()
    val indicators by viewModel.indicators.collectAsStateWithLifecycle()
    val liveFollow by viewModel.liveFollow.collectAsStateWithLifecycle()
    val selectedTool by viewModel.selectedTool.collectAsStateWithLifecycle()

    val ins = Instrument.fromSymbol(symbol)

    Column(Modifier.fillMaxSize().background(TvBg)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", color = TvText, fontSize = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp),
                modifier = Modifier.clickable(onClick = onBackToWatchlist))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(ins?.label ?: symbol, color = TvText, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(
                        when (wsStatus) {
                            LiveCandleClient.WsStatus.CONNECTED -> TvGreen
                            LiveCandleClient.WsStatus.CONNECTING, LiveCandleClient.WsStatus.RECONNECTING -> Color(0xFFFFB300)
                            LiveCandleClient.WsStatus.CLOSED -> TvRed
                        }
                    ))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        when (wsStatus) {
                            LiveCandleClient.WsStatus.CONNECTED -> "● LIVE"
                            LiveCandleClient.WsStatus.CONNECTING -> "connecting"
                            LiveCandleClient.WsStatus.RECONNECTING -> "reconnecting"
                            LiveCandleClient.WsStatus.CLOSED -> "offline"
                        },
                        color = TvTextDim,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text("⚙", color = TvAccent, modifier = Modifier.clickable(onClick = onOpenSettings))
        }

        // OHLC row
        val last = (state as? ChartUiState.Success)?.candles?.lastOrNull()
        if (last != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OhlcItem("O", last.open, symbol)
                OhlcItem("H", last.high, symbol)
                OhlcItem("L", last.low, symbol)
                OhlcItem("C", last.close, symbol)
                Spacer(Modifier.weight(1f))
                val dayOpen = (state as? ChartUiState.Success)?.candles?.firstOrNull()?.open
                if (dayOpen != null && dayOpen != 0.0) {
                    val chg = ((last.close - dayOpen) / dayOpen * 100)
                    Text(formatPrice(chg, symbol) + "%", color = changeColor(chg >= 0), fontWeight = FontWeight.Bold,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Timeframe bar
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(Timeframe.entries.toList()) { tf ->
                GlassPill(
                    tf.label,
                    active = timeframe == tf.apiValue,
                    modifier = Modifier.clickable { viewModel.selectTimeframe(tf.apiValue) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        // Chart area (fills remaining space)
        Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                is ChartUiState.Idle, is ChartUiState.Loading -> Text("Loading…", color = TvTextDim, modifier = Modifier.align(Alignment.Center))
                is ChartUiState.Error -> Column(Modifier.align(Alignment.Center)) {
                    Text(s.message, color = TvRed, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.TextButton(onClick = { viewModel.load() }) { Text("Retry", color = TvAccent) }
                }
                is ChartUiState.Success -> {
                    if (s.candles.isEmpty()) {
                        Text("No candles", color = TvTextDim, modifier = Modifier.align(Alignment.Center))
                    } else {
                        CandleChart(
                            candles = s.candles,
                            symbol = symbol,
                            modifier = Modifier.fillMaxSize(),
                            drawings = drawings,
                            indicators = indicators,
                            liveFollowing = liveFollow,
                            onLiveFollowChange = { viewModel.setLiveFollow(it) },
                            onCandleRangeSelected = { a, b -> viewModel.computeVolumeProfile(a, b) },
                            selectedTool = selectedTool,
                            onDrawingCreated = { viewModel.addDrawing(it) },
                            onChartTap = { viewModel.setLiveFollow(true) },
                            onNeedOlder = { viewModel.loadOlder() },
                            startIndexShift = viewModel.startIndexShift.collectAsStateWithLifecycle().value,
                        )
                    }
                }
            }

            // Drawing toolbar (vertical, left) — glass
            DrawingToolbar(
                selectedTool = selectedTool,
                onSelect = { viewModel.selectTool(it) },
                onUndo = { viewModel.undoDrawing() },
                onDeleteAll = { viewModel.clearDrawings() },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp),
            )

            // LIVE button (bottom right)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (liveFollow) TvGreen.copy(alpha = 0.25f) else Color(0x22FFFFFF))
                    .clickable { viewModel.setLiveFollow(true) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(if (liveFollow) "● LIVE" else "LIVE OFF", color = if (liveFollow) TvGreen else TvTextDim, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun OhlcItem(label: String, v: Double, symbol: String) {
    Column {
        Text(label, color = TvTextDim, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Text(formatPrice(v, symbol), color = TvText, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DrawingToolbar(
    selectedTool: DrawingTool?,
    onSelect: (DrawingTool) -> Unit,
    onUndo: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools = listOf(
        DrawingTool.CURSOR to "⌖",
        DrawingTool.TREND to "╱",
        DrawingTool.FIB_RETRACEMENT to "ƒ",
        DrawingTool.RECTANGLE to "▭",
        DrawingTool.CIRCLE to "○",
        DrawingTool.LONG_POSITION to "▲",
        DrawingTool.SHORT_POSITION to "▼",
        DrawingTool.HORIZONTAL_LINE to "―",
        DrawingTool.VERTICAL_LINE to "│",
        DrawingTool.TEXT to "T",
        DrawingTool.VOLUME_PROFILE to "▮",
    )
    Glass(modifier, corner = 12.dp) {
        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((tool, icon) in tools) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedTool == tool) TvAccent.copy(alpha = 0.4f) else Color.Transparent)
                        .clickable { onSelect(tool) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(icon, color = if (selectedTool == tool) TvText else TvTextDim, fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp))
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onUndo)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) { Text("↶", color = TvTextDim) }
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDeleteAll)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) { Text("✕", color = TvRed) }
        }
    }
}
