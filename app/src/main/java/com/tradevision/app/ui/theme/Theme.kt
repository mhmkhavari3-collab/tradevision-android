package com.tradevision.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight

// ---------- TradeVision palette ----------
val TvBg = Color(0xFF07090D)        // page background (near black)
val TvSurface = Color(0xFF0F141B)   // cards
val TvSurfaceAlt = Color(0xFF151C26) // raised
val TvBorder = Color(0xFF232B38)    // hairline borders
val TvGreen = Color(0xFF00C853)     // up / buy
val TvRed = Color(0xFFFF3B5C)       // down / sell
val TvAccent = Color(0xFF7C6CFF)    // purple accent (selection)
val TvAccentSoft = Color(0xFF3D3A6B)
val TvText = Color(0xFFE8ECF1)      // primary text
val TvTextDim = Color(0xFF8A94A6)   // secondary text
val TvGlass = Color(0x1FFFFFFF)     // glass fill
val TvGlassBorder = Color(0x33FFFFFF) // glass hairline

private val TvColors = darkColorScheme(
    primary = TvAccent,
    secondary = TvGreen,
    background = TvBg,
    surface = TvSurface,
    surfaceVariant = TvSurfaceAlt,
    onPrimary = Color.White,
    onBackground = TvText,
    onSurface = TvText,
    outline = TvBorder,
)

@Composable
fun TradeVisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvColors,
        content = content,
    )
}

// ---------- Glass components ----------

/** Semi-transparent rounded surface with thin border (glassmorphism). */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x26FFFFFF), Color(0x12FFFFFF)),
                )
            )
            .border(1.dp, TvGlassBorder, RoundedCornerShape(corner)),
    ) {
        content()
    }
}

/** Small glass pill used for toolbar buttons / chips. */
@Composable
fun GlassPill(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    accent: Boolean = false,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) TvAccent.copy(alpha = 0.35f) else Color(0x14FFFFFF))
            .border(1.dp, if (active) TvAccent.copy(alpha = 0.6f) else TvGlassBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            color = when {
                accent -> TvGreen
                active -> TvText
                else -> TvTextDim
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Price/change color helper — green for positive, red for negative. */
fun changeColor(positive: Boolean) = if (positive) TvGreen else TvRed

/**
 * Format price with instrument-specific precision (display only — raw value never changes).
 * Uses [Instrument.formatPrice] when symbol is known; falls back to crypto/fx default.
 */
fun formatPrice(v: Double, symbol: String? = null, crypto: Boolean = false): String {
    if (symbol != null) return com.tradevision.app.data.Instrument.formatPrice(symbol, v)
    return if (crypto) {
        if (v >= 1000) "%,.2f".format(v) else "%,.4f".format(v)
    } else {
        "%,.5f".format(v)
    }
}

/** Format signed change (+/-). */
fun formatSigned(v: Double, suffix: String = ""): String {
    val s = if (v >= 0) "+" else "-"
    return s + "%,.2f".format(kotlin.math.abs(v)) + suffix
}
