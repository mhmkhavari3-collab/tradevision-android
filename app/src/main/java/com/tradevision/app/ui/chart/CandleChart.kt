package com.tradevision.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tradevision.app.data.Candle
import kotlin.math.abs

/** Candlestick chart: pinch-to-zoom, drag-to-pan, tap for crosshair + OHLC tooltip. */
@Composable
fun CandleChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
) {
    var startIndex by remember { mutableIntStateOf(0) }
    var visibleCount by remember { mutableIntStateOf(50) }
    var crosshairIndex by remember { mutableIntStateOf(-1) }
    var touchX by remember { mutableFloatStateOf(0f) }

    Box(modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(candles, visibleCount, startIndex) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (abs(zoom - 1f) > 0.01f) {
                            val newCount = (visibleCount / zoom).toInt().coerceIn(5, candles.size.coerceAtLeast(5))
                            visibleCount = newCount
                        }
                        if (abs(pan.x) > 0.5f) {
                            val c = 0.6f
                            val shift = (-pan.x * c / (size.width / visibleCount.coerceAtLeast(1))).toInt()
                            if (shift != 0) {
                                startIndex = (startIndex + shift).coerceIn(
                                    0,
                                    (candles.size - visibleCount).coerceAtLeast(0),
                                )
                            }
                        }
                        crosshairIndex = -1
                    }
                }
                .pointerInput(candles, visibleCount, startIndex) {
                    detectTapGestures { pos ->
                        val slot = this.size.width / visibleCount.coerceAtLeast(1)
                        val i = startIndex + (pos.x / slot).toInt()
                        if (i in candles.indices) {
                            crosshairIndex = i
                            touchX = pos.x
                        }
                    }
                },
        ) {
            if (candles.isEmpty()) return@Canvas
            val endIdx = (startIndex + visibleCount).coerceAtMost(candles.size)
            if (startIndex >= endIdx) return@Canvas
            val slice = candles.subList(startIndex, endIdx)

            drawGrid(size)

            val slot = size.width / slice.size
            val bodyW = (slot * 0.62f).coerceAtMost(11f)
            val hi = slice.maxOf { it.high }
            val lo = slice.minOf { it.low }
            val range = (hi - lo).takeIf { it > 0.0 } ?: 1.0
            val pxPerUnit = (size.height * 0.88f) / range.toFloat()
            val topPad = size.height * 0.06f
            fun yOf(v: Double): Float = topPad + (hi - v).toFloat() * pxPerUnit

            val upColor = Color(0xFF26A69A)
            val downColor = Color(0xFFEF5350)

            for (i in slice.indices) {
                val c = slice[i]
                val x = i * slot + slot / 2f
                val up = c.close >= c.open
                val col = if (up) upColor else downColor
                drawLine(col, Offset(x, yOf(c.high)), Offset(x, yOf(c.low)), strokeWidth = 1.5f)
                val topY = yOf(maxOf(c.open, c.close))
                val botY = yOf(minOf(c.open, c.close))
                drawRect(
                    col,
                    topLeft = Offset(x - bodyW / 2f, topY),
                    size = Size(bodyW, (botY - topY).coerceAtLeast(1.6f)),
                )
            }

            // crosshair
            if (crosshairIndex in candles.indices && crosshairIndex >= startIndex && crosshairIndex < endIdx) {
                val c = candles[crosshairIndex]
                val i = crosshairIndex - startIndex
                val x = i * slot + slot / 2f
                val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                drawLine(Color(0x88FFFFFF), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f, pathEffect = dash)
                val yc = yOf(c.close)
                drawLine(Color(0x88FFFFFF), Offset(0f, yc), Offset(size.width, yc), strokeWidth = 1f, pathEffect = dash)
                val paint = android.graphics.Paint().apply {
                    color = Color.White.toArgb()
                    textSize = 26f
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.2f", c.close),
                    size.width - 64f,
                    (yc - 6f).coerceAtLeast(28f),
                    paint,
                )
            }
        }

        if (crosshairIndex in candles.indices) {
            val c = candles[crosshairIndex]
            OhlcTooltip(c, Modifier.padding(8.dp).align(Alignment.TopStart))
        }
    }
}

private fun DrawScope.drawGrid(size: Size) {
    val hLines = 5
    for (i in 0..hLines) {
        val y = size.height * i / hLines
        drawLine(Color(0x22FFFFFF), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
    val vLines = 8
    for (i in 0..vLines) {
        val x = size.width * i / vLines
        drawLine(Color(0x11FFFFFF), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
    }
}

@Composable
private fun OhlcTooltip(c: Candle, modifier: Modifier) {
    Surface(modifier, color = Color(0xDD111111), shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("O ${"%.2f".format(c.open)}   H ${"%.2f".format(c.high)}",
                color = Color.White, style = MaterialTheme.typography.labelSmall)
            Text("L ${"%.2f".format(c.low)}   C ${"%.2f".format(c.close)}",
                color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}