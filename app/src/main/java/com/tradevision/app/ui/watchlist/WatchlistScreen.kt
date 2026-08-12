package com.tradevision.app.ui.watchlist

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.tradevision.app.data.Category
import com.tradevision.app.data.Instrument
import com.tradevision.app.data.MarketQuote
import com.tradevision.app.ui.theme.Glass
import com.tradevision.app.ui.theme.TvAccent
import com.tradevision.app.ui.theme.TvBg
import com.tradevision.app.ui.theme.TvBorder
import com.tradevision.app.ui.theme.TvGreen
import com.tradevision.app.ui.theme.TvRed
import com.tradevision.app.ui.theme.TvText
import com.tradevision.app.ui.theme.TvTextDim
import com.tradevision.app.ui.theme.changeColor
import com.tradevision.app.ui.theme.formatPrice
import com.tradevision.app.ui.theme.formatSigned

@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    onOpenSymbol: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val quotes by viewModel.quotes.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(TvBg).padding(horizontal = 14.dp)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TradeVision", color = TvText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onOpenSettings) { Text("⚙", color = TvAccent) }
        }

        // Sort chips (Gainers / Losers / Symbol / Change%)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortChip("Gainers", sort == WatchSort.GAINERS) { viewModel.setSort(WatchSort.GAINERS) }
            SortChip("Losers", sort == WatchSort.LOSERS) { viewModel.setSort(WatchSort.LOSERS) }
            SortChip("Symbol", sort == WatchSort.SYMBOL) { viewModel.setSort(WatchSort.SYMBOL) }
            SortChip("Change %", sort == WatchSort.CHANGE_PCT) { viewModel.setSort(WatchSort.CHANGE_PCT) }
        }
        Spacer(Modifier.height(10.dp))

        // Sections: Crypto, Forex
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (cat in Category.entries) {
                item(key = "header-$cat") {
                    Text(
                        cat.label,
                        color = TvTextDim,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }
                val symbols = Instrument.entries.filter { it.category == cat }.map { it.symbol }
                items(symbols, key = { it }) { sym ->
                    val q = quotes[sym]
                    WatchlistRow(
                        symbol = sym,
                        quote = q,
                        onClick = { onOpenSymbol(sym) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SortChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(if (active) TvAccent.copy(alpha = 0.3f) else Color(0x12FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = if (active) TvText else TvTextDim, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun WatchlistRow(
    symbol: String,
    quote: MarketQuote?,
    onClick: () -> Unit,
) {
    val ins = Instrument.fromSymbol(symbol)
    val label = ins?.label ?: symbol

    Glass(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Symbol + sublabel
            Column(Modifier.weight(1f)) {
                Text(label, color = TvText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(symbol, color = TvTextDim, style = MaterialTheme.typography.labelSmall)
            }

            if (quote == null) {
                Text("—", color = TvTextDim)
            } else {
                // Price + change
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatPrice(quote.last, symbol),
                        color = TvText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatSigned(quote.change),
                            color = changeColor(quote.isPositive),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatSigned(quote.changePercent, "%"),
                            color = changeColor(quote.isPositive),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
