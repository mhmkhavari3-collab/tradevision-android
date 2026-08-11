package com.tradevision.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tradevision.app.data.Candle
import com.tradevision.app.data.ChartPoint
import com.tradevision.app.data.Drawing
import com.tradevision.app.data.DrawingTool
import com.tradevision.app.data.IndicatorConfig
import com.tradevision.app.data.IndicatorType
import com.tradevision.app.data.VolumeProfileResult
import com.tradevision.app.ui.theme.TvBg
import com.tradevision.app.ui.theme.TvBorder
import com.tradevision.app.ui.theme.TvGreen
import com.tradevision.app.ui.theme.TvRed
import com.tradevision.app.ui.theme.TvText
import com.tradevision.app.ui.theme.TvTextDim
import kotlin.math.abs
import kotlin.math.min

/** Interactive candlestick chart v3 — TradingView-like:
 *  pinch zoom, pan, double-tap zoom, crosshair (long-press), price/time axes, grid,
 *  LIVE-follow, drawings (trendline/ray/rect/fib/long-short/volume-profile), indicators. */
@Composable
fun CandleChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
    drawings: List<Drawing> = emptyList(),
    indicators: List<IndicatorConfig> = emptyList(),
    liveFollowing: Boolean = true,
    onLiveFollowChange: (Boolean) -> Unit = {},
    onCandleRangeSelected: ((Int, Int) -> Unit)? = null, // for FVRP (first/last index)
    selectedTool: DrawingTool? = null,
    onDrawingCreated: ((Drawing) -> Unit)? = null,
    onDrawingTap: ((Drawing) -> Unit)? = null,
    onIndicatorTap: ((IndicatorConfig) -> Unit)? = null,
    onCrosshair: ((Int) -> Unit)? = null,
    onChartTap: (() -> Unit)? = null,
) {
    var startIndex by remember { mutableIntStateOf(0) }
    var visibleCount by remember { mutableIntStateOf(50) }
    var crosshairIndex by remember { mutableIntStateOf(-1) }
    var drawingStart by remember { mutableStateOf<ChartPoint?>(null) }
    var volumeProfile by remember { mutableStateOf<VolumeProfileResult?>(null) }
    var selectionStart by remember { mutableStateOf<Int?>(null) }

    // Follow latest when live-follow is on
    val lastIndex = (candles.size - 1).coerceAtLeast(0)
    if (liveFollowing && candles.isNotEmpty() && (startIndex + visibleCount) < candles.size) {
        startIndex = (candles.size - visibleCount).coerceAtLeast(0)
    }

    Box(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(candles, visibleCount, startIndex, selectedTool) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // pinch zoom around centroid
                        if (abs(zoom - 1f) > 0.01f) {
                            val newCount = (visibleCount / zoom).toInt().coerceIn(5, candles.size.coerceAtLeast(5))
                            // keep candle under centroid fixed
                            val frac = (centroid.x / size.width).coerceIn(0f, 1f)
                            val anchor = startIndex + (visibleCount * frac).toInt()
                            val anchorFrac = (anchor - startIndex).toFloat() / visibleCount
                            startIndex = (anchor - (newCount * anchorFrac).toInt()).coerceIn(0, (candles.size - newCount).coerceAtLeast(0))
                            visibleCount = newCount
                        }
                        if (abs(pan.x) > 0.5f && selectedTool == null) {
                            val c = 0.8f
                            val shift = (-pan.x * c / (size.width / visibleCount.coerceAtLeast(1))).toInt()
                            if (shift != 0) {
                                startIndex = (startIndex + shift).coerceIn(0, (candles.size - visibleCount).coerceAtLeast(0))
                            }
                        }
                        if (liveFollowing && (startIndex + visibleCount) < candles.size) {
                            onLiveFollowChange(false)
                        }
                        crosshairIndex = -1
                    }
                }
                .pointerInput(candles, visibleCount, startIndex, selectedTool) {
                    detectTapGestures(
                        onDoubleTap = {
                            val newCount = (visibleCount / 1.8f).toInt().coerceIn(5, candles.size.coerceAtLeast(5))
                            startIndex = ((startIndex + visibleCount / 2) - newCount / 2).coerceIn(0, (candles.size - newCount).coerceAtLeast(0))
                            visibleCount = newCount
                        },
                        onTap = {
                            if (selectedTool != null) {
                                // place a drawing point
                                val slot = size.width / visibleCount.coerceAtLeast(1)
                                val idx = startIndex + (it.x / slot).toInt()
                                val price = priceAt(it.y, candles, startIndex, visibleCount)
                                val pt = ChartPoint(idx.toFloat(), price)
                                val first = drawingStart
                                if (first == null) {
                                    drawingStart = pt
                                } else {
                                    onDrawingCreated?.invoke(Drawing(tool = selectedTool, points = listOf(first, pt)))
                                    drawingStart = null
                                }
                                // volume profile tool: select a range
                                if (selectedTool == DrawingTool.VOLUME_PROFILE) {
                                    val s0 = selectionStart
                                    if (s0 == null) selectionStart = idx else {
                                        val lo = min(s0, idx); val hi = maxOf(s0, idx)
                                        onCandleRangeSelected?.invoke(lo, hi)
                                        selectionStart = null
                                    }
                                }
                            } else {
                                onChartTap?.invoke()
                            }
                        },
                        onLongPress = {
                            val slot = size.width / visibleCount.coerceAtLeast(1)
                            val idx = startIndex + (it.x / slot).toInt()
                            if (idx in candles.indices) {
                                crosshairIndex = idx
                                onCrosshair?.invoke(idx)
                            }
                        },
                    )
                }
                .pointerInput(candles, visibleCount, startIndex) {
                    detectDragGestures { change, drag ->
                        // drag with no tool → pan
                        if (selectedTool == null) {
                            val slot = size.width / visibleCount.coerceAtLeast(1)
                            val shift = (-drag.x / slot).toInt()
                            if (shift != 0) {
                                startIndex = (startIndex + shift).coerceIn(0, (candles.size - visibleCount).coerceAtLeast(0))
                            }
                        }
                    }
                },
        ) {
            if (candles.isEmpty()) return@Canvas
            val endIdx = (startIndex + visibleCount).coerceAtMost(candles.size)
            if (startIndex >= endIdx) return@Canvas
            val slice = candles.subList(startIndex, endIdx)

            drawGrid(size)
            drawPriceAxis(size, slice)
            drawTimeAxis(size, slice, startIndex)

            val slot = size.width / slice.size
            val bodyW = (slot * 0.62f).coerceAtMost(11f)
            val hi = slice.maxOf { it.high }
            val lo = slice.minOf { it.low }
            val range = (hi - lo).takeIf { it > 0.0 } ?: 1.0
            val pxPerUnit = (size.height * 0.86f) / range.toFloat()
            val topPad = size.height * 0.07f
            fun yOf(v: Double): Float = topPad + (hi - v).toFloat() * pxPerUnit

            // candles
            for (i in slice.indices) {
                val c = slice[i]
                val x = i * slot + slot / 2f
                val up = c.close >= c.open
                val col = if (up) TvGreen else TvRed
                drawLine(col, Offset(x, yOf(c.high)), Offset(x, yOf(c.low)), strokeWidth = 1.3f)
                val topY = yOf(maxOf(c.open, c.close))
                val botY = yOf(minOf(c.open, c.close))
                drawRect(
                    col,
                    topLeft = Offset(x - bodyW / 2f, topY),
                    size = Size(bodyW, (botY - topY).coerceAtLeast(1.4f)),
                )
            }

            // indicators
            for (ind in indicators) {
                drawIndicator(ind, slice, yOf, slot, startIndex)
            }

            // drawings
            for (d in drawings) {
                drawDrawing(d, candles, startIndex, visibleCount, size, yOf)
            }

            // volume profile histogram
            volumeProfile?.let { vp ->
                drawVolumeProfile(vp, size)
            }

            // crosshair
            if (crosshairIndex in candles.indices && crosshairIndex >= startIndex && crosshairIndex < endIdx) {
                val c = candles[crosshairIndex]
                val i = crosshairIndex - startIndex
                val x = i * slot + slot / 2f
                val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                drawLine(Color(0x66FFFFFF), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f, pathEffect = dash)
                val yc = yOf(c.close)
                drawLine(Color(0x66FFFFFF), Offset(0f, yc), Offset(size.width, yc), strokeWidth = 1f, pathEffect = dash)
                val paint = android.graphics.Paint().apply {
                    color = TvText.toArgb()
                    textSize = 24f
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "%.2f".format(c.close),
                    size.width - 64f,
                    (yc - 6f).coerceAtLeast(26f),
                    paint,
                )
            }
        }

        // LIVE badge overlay (top-right)
        if (liveFollowing) {
            Box(
                Modifier
                    .padding(10.dp)
                    .align(androidx.compose.ui.Alignment.TopEnd)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .background(Color(0x22FFFFFF))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("● LIVE", color = TvGreen, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Helper: convert screen y to price using current scale. */
private fun priceAt(y: Float, candles: List<Candle>, startIndex: Int, visibleCount: Int): Double {
    if (candles.isEmpty()) return 0.0
    val endIdx = (startIndex + visibleCount).coerceAtMost(candles.size)
    if (startIndex >= endIdx) return 0.0
    val slice = candles.subList(startIndex, endIdx)
    val hi = slice.maxOf { it.high }
    val lo = slice.minOf { it.low }
    val range = (hi - lo).takeIf { it > 0.0 } ?: 1.0
    val topPad = 0.07f
    val pxPerUnit = (1f - 2 * topPad) / range.toFloat()
    val price = hi - (y / (sizePlaceholderH * pxPerUnit))  // simplified; real calc in draw
    return price
}

// Placeholder height for priceAt (real height comes from DrawScope)
private const val sizePlaceholderH = 1000f

private fun DrawScope.drawGrid(size: Size) {
    val hLines = 5
    for (i in 0..hLines) {
        val y = size.height * i / hLines
        drawLine(TvBorder.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
    val vLines = 8
    for (i in 0..vLines) {
        val x = size.width * i / vLines
        drawLine(TvBorder.copy(alpha = 0.3f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
    }
}

private fun DrawScope.drawPriceAxis(size: Size, slice: List<Candle>) {
    val hi = slice.maxOf { it.high }
    val lo = slice.minOf { it.low }
    val paint = android.graphics.Paint().apply {
        color = TvTextDim.toArgb()
        textSize = 20f
        isAntiAlias = true
    }
    val n = 4
    for (i in 0..n) {
        val v = lo + (hi - lo) * i / n
        val y = size.height * 0.07f + (size.height * 0.86f) * (1f - i.toFloat() / n)
        drawContext.canvas.nativeCanvas.drawText("%.2f".format(v), size.width - 58f, y, paint)
    }
}

private fun DrawScope.drawTimeAxis(size: Size, slice: List<Candle>, startIndex: Int) {
    val paint = android.graphics.Paint().apply {
        color = TvTextDim.toArgb()
        textSize = 18f
        isAntiAlias = true
    }
    val n = 4
    for (i in 0..n) {
        val idx = i * (slice.size - 1) / n.coerceAtLeast(1)
        if (idx !in slice.indices) continue
        val c = slice[idx]
        val x = idx * size.width / slice.size + size.width / (2 * slice.size)
        val t = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(c.openTime))
        drawContext.canvas.nativeCanvas.drawText(t, x, size.height - 6f, paint)
    }
}

private fun DrawScope.drawIndicator(ind: IndicatorConfig, slice: List<Candle>, yOf: (Double) -> Float, slot: Float, startIndex: Int) {
    when (ind.type) {
        IndicatorType.SMA, IndicatorType.EMA -> {
            val vals = if (ind.type == IndicatorType.SMA) sma(slice, ind.period) else ema(slice, ind.period)
            var prev: Offset? = null
            for (i in vals.indices) {
                if (vals[i] == null) { prev = null; continue }
                val x = i * slot + slot / 2f
                val p = Offset(x, yOf(vals[i]!!))
                prev?.let { drawLine(Color(ind.color), it, p, strokeWidth = 1.6f) }
                prev = p
            }
        }
        IndicatorType.RSI -> {
            val vals = rsi(slice, ind.period)
            var prev: Offset? = null
            for (i in vals.indices) {
                if (vals[i] == null) { prev = null; continue }
                val x = i * slot + slot / 2f
                val r = vals[i]!!.toFloat()
                val y = size.height * 0.07f + (1f - r / 100f) * size.height * 0.86f
                val p = Offset(x, y)
                prev?.let { drawLine(Color(ind.color), it, p, strokeWidth = 1.4f) }
                prev = p
            }
        }
        IndicatorType.VOLUME -> {
            // volume bars at bottom
            val maxV = (slice.maxOfOrNull { it.volume } ?: 1.0).coerceAtLeast(1.0)
            for (i in slice.indices) {
                val c = slice[i]
                val x = i * slot + slot / 2f
                val h = (c.volume / maxV).toFloat() * size.height * 0.12f
                val col = if (c.close >= c.open) TvGreen.copy(alpha = 0.5f) else TvRed.copy(alpha = 0.5f)
                drawRect(col, Offset(x - slot * 0.3f, size.height - h), Size(slot * 0.6f, h))
            }
        }
    }
}

private fun DrawScope.drawDrawing(d: Drawing, candles: List<Candle>, startIndex: Int, visibleCount: Int, size: Size, yOf: (Double) -> Float) {
    if (candles.isEmpty()) return
    val endIdx = (startIndex + visibleCount).coerceAtMost(candles.size)
    if (startIndex >= endIdx) return
    val slice = candles.subList(startIndex, endIdx)
    val slot = size.width / slice.size
    fun xOf(idx: Float): Float = (idx - startIndex) * slot + slot / 2f

    val col = Color(d.color)
    val stroke = 1.6f

    when (d.tool) {
        DrawingTool.TREND, DrawingTool.EXTENDED_LINE, DrawingTool.RAY -> {
            if (d.points.size < 2) return
            val p0 = d.points[0]; val p1 = d.points[1]
            val x0 = xOf(p0.candleIndex); val y0 = yOf(p0.price)
            val x1 = xOf(p1.candleIndex); val y1 = yOf(p1.price)
            if (d.tool == DrawingTool.TREND) {
                drawLine(col, Offset(x0, y0), Offset(x1, y1), strokeWidth = stroke)
            } else if (d.tool == DrawingTool.EXTENDED_LINE) {
                // extend both directions
                val dx = x1 - x0; val dy = y1 - y0
                if (abs(dx) < 0.001f) return
                val m = dy / dx
                val xL = 0f; val yL = y0 + m * (xL - x0)
                val xR = size.width; val yR = y0 + m * (xR - x0)
                drawLine(col, Offset(xL, yL), Offset(xR, yR), strokeWidth = stroke)
            } else { // RAY — extend forward
                val dx = x1 - x0; val dy = y1 - y0
                val m = dy / dx
                val xR = size.width; val yR = y0 + m * (xR - x0)
                drawLine(col, Offset(x0, y0), Offset(xR, yR), strokeWidth = stroke)
            }
        }
        DrawingTool.HORIZONTAL_LINE -> {
            if (d.points.isEmpty()) return
            val y = yOf(d.points[0].price)
            drawLine(col, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        }
        DrawingTool.VERTICAL_LINE -> {
            if (d.points.isEmpty()) return
            val x = xOf(d.points[0].candleIndex)
            drawLine(col, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
        }
        DrawingTool.RECTANGLE -> {
            if (d.points.size < 2) return
            val p0 = d.points[0]; val p1 = d.points[1]
            val x0 = xOf(p0.candleIndex); val x1 = xOf(p1.candleIndex)
            val y0 = yOf(p0.price); val y1 = yOf(p1.price)
            val left = min(x0, x1); val top = min(y0, y1)
            val w = abs(x1 - x0); val h = abs(y1 - y0)
            drawRect(col, topLeft = Offset(left, top), size = Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        }
        DrawingTool.CIRCLE -> {
            if (d.points.size < 2) return
            val p0 = d.points[0]; val p1 = d.points[1]
            val x0 = xOf(p0.candleIndex); val y0 = yOf(p0.price)
            val x1 = xOf(p1.candleIndex); val y1 = yOf(p1.price)
            val r = kotlin.math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
            drawCircle(col, radius = r, center = Offset(x0, y0), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        }
        DrawingTool.FIB_RETRACEMENT, DrawingTool.FIB_EXTENSION -> {
            if (d.points.size < 2) return
            val p0 = d.points[0]; val p1 = d.points[1]
            val y0 = yOf(p0.price); val y1 = yOf(p1.price)
            val levels = listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0)
            val range = y1 - y0
            for (lv in levels) {
                val y = y0 + range * lv.toFloat()
                drawLine(Color(0x44FFFFFF), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)))
                val paint = android.graphics.Paint().apply { color = TvTextDim.toArgb(); textSize = 18f; isAntiAlias = true }
                drawContext.canvas.nativeCanvas.drawText("%.1f%%".format(lv * 100), 4f, y - 4f, paint)
            }
        }
        DrawingTool.LONG_POSITION, DrawingTool.SHORT_POSITION -> {
            if (d.points.size < 2) return
            val p0 = d.points[0]; val p1 = d.points[1]
            val entry = p0.price; val stop = p1.price
            val target = entry + (entry - stop) * (if (d.tool == DrawingTool.LONG_POSITION) 1.5 else -1.5)
            val yE = yOf(entry); val yS = yOf(stop); val yT = yOf(target)
            val loY = min(yE, min(yS, yT)); val hiY = maxOf(yE, maxOf(yS, yT))
            // profit zone
            val greenY = min(yE, yT); val redY = min(yE, yS)
            val gTop = min(greenY, yE); val gBot = maxOf(greenY, yE)
            val rTop = min(redY, yE); val rBot = maxOf(redY, yE)
            drawRect(TvGreen.copy(alpha = 0.12f), topLeft = Offset(0f, gTop), size = Size(size.width, (gBot - gTop).coerceAtLeast(1f)))
            drawRect(TvRed.copy(alpha = 0.12f), topLeft = Offset(0f, rTop), size = Size(size.width, (rBot - rTop).coerceAtLeast(1f)))
            drawLine(col, Offset(0f, yE), Offset(size.width, yE), strokeWidth = stroke)
            drawLine(TvRed, Offset(0f, yS), Offset(size.width, yS), strokeWidth = 1f)
            drawLine(TvGreen, Offset(0f, yT), Offset(size.width, yT), strokeWidth = 1f)
            val paint = android.graphics.Paint().apply { color = TvTextDim.toArgb(); textSize = 18f; isAntiAlias = true }
            drawContext.canvas.nativeCanvas.drawText("Entry %.2f".format(entry), 6f, yE - 4f, paint)
            drawContext.canvas.nativeCanvas.drawText("Stop %.2f".format(stop), 6f, yS - 4f, paint)
            drawContext.canvas.nativeCanvas.drawText("Target %.2f".format(target), 6f, yT - 4f, paint)
        }
        DrawingTool.TEXT -> {
            if (d.points.isEmpty()) return
            val p = d.points[0]
            val x = xOf(p.candleIndex); val y = yOf(p.price)
            val paint = android.graphics.Paint().apply { color = TvText.toArgb(); textSize = 20f; isAntiAlias = true }
            drawContext.canvas.nativeCanvas.drawText(d.text ?: "", x, y, paint)
        }
        else -> {}
    }
}

private fun DrawScope.drawVolumeProfile(vp: VolumeProfileResult, size: Size) {
    val maxVol = vp.bins.maxOfOrNull { it.second } ?: 1.0
    val binW = size.width * 0.16f
    val left = size.width - binW
    for ((price, vol) in vp.bins) {
        val frac = (vol / maxVol).toFloat()
        val x = left + binW * (1f - frac)
        val y = priceToY(price, vp.pocPrice, size)
        drawRect(Color(0x334D8BFF), Offset(x, y), Size(binW * frac, 4f))
    }
    val paint = android.graphics.Paint().apply { color = TvText.toArgb(); textSize = 20f; isAntiAlias = true }
    drawContext.canvas.nativeCanvas.drawText("POC %.2f".format(vp.pocPrice), left, priceToY(vp.pocPrice, vp.pocPrice, size) - 4f, paint)
}

private fun priceToY(price: Double, ref: Double, size: Size): Float =
    size.height * 0.07f + (size.height * 0.86f) * (1f - ((price - ref) / (ref * 0.02f + 1f)).toFloat())

// ---------- indicator math ----------

private fun sma(candles: List<Candle>, period: Int): List<Double?> {
    val out = MutableList<Double?>(candles.size) { null }
    if (candles.size < period) return out
    var sum = 0.0
    for (i in candles.indices) {
        sum += candles[i].close
        if (i >= period) sum -= candles[i - period].close
        if (i >= period - 1) out[i] = sum / period
    }
    return out
}

private fun ema(candles: List<Candle>, period: Int): List<Double?> {
    val out = MutableList<Double?>(candles.size) { null }
    if (candles.isEmpty()) return out
    val k = 2.0 / (period + 1)
    var prev = candles[0].close
    for (i in candles.indices) {
        prev = if (i == 0) candles[i].close else candles[i].close * k + prev * (1 - k)
        out[i] = prev
    }
    return out
}

private fun rsi(candles: List<Candle>, period: Int): List<Double?> {
    val out = MutableList<Double?>(candles.size) { null }
    if (candles.size <= period) return out
    var gain = 0.0; var loss = 0.0
    for (i in 1..period) {
        val d = candles[i].close - candles[i - 1].close
        if (d >= 0) gain += d else loss -= d
    }
    var avgGain = gain / period; var avgLoss = loss / period
    out[period] = if (avgLoss == 0.0) 100.0 else 100 - 100 / (1 + avgGain / avgLoss)
    for (i in period + 1 until candles.size) {
        val d = candles[i].close - candles[i - 1].close
        avgGain = (avgGain * (period - 1) + maxOf(d, 0.0)) / period
        avgLoss = (avgLoss * (period - 1) + maxOf(-d, 0.0)) / period
        out[i] = if (avgLoss == 0.0) 100.0 else 100 - 100 / (1 + avgGain / avgLoss)
    }
    return out
}
