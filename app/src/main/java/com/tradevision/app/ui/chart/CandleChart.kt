package com.tradevision.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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

// ── Gesture constants ─────────────────────────────────────────────
private const val TOUCH_SLOP = 8f          // px movement before a drag is a pan
private const val LONG_PRESS_MS = 400L     // held-still → crosshair
private const val DOUBLE_TAP_MS = 300L     // between-tap window
private const val PREFETCH_THRESHOLD = 5   // candles from left edge → loadOlder()

/** Interactive candlestick chart v3 — TradingView-like:
 *  pinch zoom, pan, double-tap zoom, crosshair (long-press), price/time axes, grid,
 *  LIVE-follow, drawings (trendline/ray/rect/fib/long-short/volume-profile), indicators. */
@Composable
fun CandleChart(
    candles: List<Candle>,
    symbol: String,
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
    onNeedOlder: (() -> Unit)? = null,
    startIndexShift: Int = 0, // +N when older candles were prepended (viewport preservation)
) {
    var startIndex by remember { mutableIntStateOf(0) }
    var visibleCount by remember { mutableIntStateOf(50) }
    var crosshairIndex by remember { mutableIntStateOf(-1) }
    var drawingStart by remember { mutableStateOf<ChartPoint?>(null) }
    var volumeProfile by remember { mutableStateOf<VolumeProfileResult?>(null) }
    var selectionStart by remember { mutableStateOf<Int?>(null) }
    var verticalPanOffset by remember { mutableFloatStateOf(0f) }
    var consumedShift by remember { mutableIntStateOf(0) }
    // Sub-pixel pan accumulator: slow drags produce events smaller than one candle
    // slot. Truncating per-event gives shift=0 forever (pan glues to the edge).
    // We accumulate fractionally and only shift when a full slot accumulates.
    var panAccumulator by remember { mutableFloatStateOf(0f) }

    // Effective visible count — CRITICAL: when candles.size <= visibleCount (e.g. the
    // initial 50-candle load), we must NOT show all of them. Showing 50/50 gives
    // maxStart = 0 → zero panning room → chart looks glued to the right edge and
    // cannot move. Cap at ~80% so there is always room to pan + right-edge margin.
    val effVisible = if (candles.size > visibleCount) {
        visibleCount
    } else {
        (candles.size * 4 / 5).coerceAtLeast(5)
    }

    // When older candles are prepended, shift startIndex so the SAME candles stay visible.
    LaunchedEffect(startIndexShift) {
        if (startIndexShift > consumedShift) {
            val delta = startIndexShift - consumedShift
            startIndex += delta
            consumedShift = startIndexShift
        }
    }

    // Follow latest when live-follow is on.
    // Keyed ONLY on liveFollowing — not on candles.size — so a WS tick
    // (new/changed candle) never re-runs this and never yanks the view.
    LaunchedEffect(liveFollowing) {
        if (liveFollowing) {
            // jump to the last candle when live-follow turns on.
            // IMPORTANT: land on the RIGHT-EDGE anchor (show only ~80% of the
            // candles so the newest candle rests at ~79% width and the right-edge
            // margin is real). startIndex = size - rightEdgeCount, NOT size - effVisible,
            // otherwise the newest candles are cut off and the right side is empty.
            val rightEdgeCount = (effVisible * 4 / 5).coerceAtLeast(5)
            startIndex = (candles.size - rightEdgeCount).coerceAtLeast(0)
            // then track new candles as they arrive, without touching startIndex
            // when the user has panned away (liveFollowing will have been set false)
            snapshotFlow { candles.size }
                .collect { size ->
                    if (liveFollowing && size > 0) {
                        startIndex = (size - rightEdgeCount).coerceAtLeast(0)
                    }
                }
        }
    }

    // Stable state references for gesture handlers — pointerInput must NOT restart on
    // every WS tick (candles change) or mid-gesture pan/zoom gets cancelled.
    val candlesState = rememberUpdatedState(candles)
    val visibleCountState = rememberUpdatedState(visibleCount)
    val effVisibleState = rememberUpdatedState(effVisible)
    val startIndexState = rememberUpdatedState(startIndex)
    val selectedToolState = rememberUpdatedState(selectedTool)
    val onNeedOlderState = rememberUpdatedState(onNeedOlder)
    val verticalPanState = rememberUpdatedState(verticalPanOffset)
    val liveFollowingState = rememberUpdatedState(liveFollowing)
    val onLiveFollowChangeState = rememberUpdatedState(onLiveFollowChange)

    // cross-gesture tap bookkeeping (survives across gesture sessions)
    var lastTapTime by remember { mutableLongStateOf(0L) }

    // ── Shared pan/zoom logic (used by the central gesture pipeline) ──────────
    fun applyPan(dx: Float, dy: Float, chartSize: androidx.compose.ui.unit.IntSize) {
        val cds = candlesState.value
        if (cds.isEmpty()) return
        // read ALL inputs from states so the first-run closure stays fresh
        val vc = effVisibleState.value
        val si = startIndexState.value
        val slot = chartSize.width / vc.coerceAtLeast(1)
        if (slot <= 0f) return
        // horizontal: sub-pixel accumulator — slow drags accumulate fractional slots
        // and shift only when a full slot is crossed.
        // SIGN: drag LEFT (dx<0) should show OLDER candles → startIndex DECREASES.
        // (Earlier builds had -dx here, which inverted the direction: dragging left
        //  increased startIndex toward maxStart — at the right edge (live-follow)
        //  that clamped to maxStart and the chart appeared frozen/glued.)
        panAccumulator += dx / slot
        val wholeShifts = panAccumulator.toInt()
        if (wholeShifts != 0) {
            panAccumulator -= wholeShifts.toFloat()
            // maxStart respects the right-edge margin: the view can sit with the
            // newest candle at ~79% width (startIndex = size - rightEdgeCount).
            // Using size - vc here would clamp startIndex=18 (right-edge anchor)
            // back down to 10 and make the chart jump.
            val maxStart = (cds.size - (vc * 4 / 5).coerceAtLeast(5)).coerceAtLeast(0)
            val newSi = (si + wholeShifts).coerceIn(0, maxStart)
            // if clamped at an edge, drop the leftover accumulator so a later
            // reversal doesn't "jump" suddenly
            if (newSi == si) panAccumulator = 0f
            startIndex = newSi
        }
        // vertical: shift price scale
        if (abs(dy) > 0.05f) {
            verticalPanOffset += dy * 0.8f
        }
        // any pan → leave live-follow immediately (even at the edge)
        if (liveFollowingState.value) onLiveFollowChangeState.value(false)
        crosshairIndex = -1
        // pagination: near left edge → request older candles
        if (startIndex <= PREFETCH_THRESHOLD && onNeedOlderState.value != null) {
            onNeedOlderState.value?.invoke()
        }
    }

    fun applyZoom(zoom: Float, pan: Offset, centroid: Offset, chartSize: androidx.compose.ui.unit.IntSize) {
        val cds = candlesState.value
        if (cds.isEmpty()) return
        val vc = visibleCountState.value
        val si = startIndexState.value
        var newCount = vc
        var newStart = si
        if (abs(zoom - 1f) > 0.01f) {
            newCount = (vc / zoom).toInt().coerceIn(5, cds.size.coerceAtLeast(5))
            // keep the candle under the centroid fixed
            val frac = (centroid.x / chartSize.width).coerceIn(0f, 1f)
            val anchor = si + (vc * frac).toInt()
            if (newCount > 0) {
                val anchorFrac = (anchor - si).toFloat() / vc
                newStart = (anchor - (newCount * anchorFrac).toInt())
                    .coerceIn(0, (cds.size - newCount).coerceAtLeast(0))
            }
        }
        // two-finger pan along with zoom — use the LOCAL newStart/newCount so both
        // apply in the same frame (no stale-state lag between zoom and pan)
        // SIGN: same as applyPan — drag left (pan.x<0) → older candles (startIndex down)
        if (abs(pan.x) > 0.5f) {
            val slot = chartSize.width / newCount.coerceAtLeast(1)
            val shift = (pan.x / slot).toInt()
            if (shift != 0) {
                // same right-edge-aware maxStart as applyPan
                val maxStart = (cds.size - (newCount * 4 / 5).coerceAtLeast(5)).coerceAtLeast(0)
                newStart = (newStart + shift).coerceIn(0, maxStart)
            }
        }
        startIndex = newStart
        visibleCount = newCount
        if (abs(pan.y) > 0.5f) {
            verticalPanOffset += pan.y * 0.8f
        }
        if (liveFollowingState.value) onLiveFollowChangeState.value(false)
        crosshairIndex = -1
        if (startIndex <= PREFETCH_THRESHOLD && onNeedOlderState.value != null) {
            onNeedOlderState.value?.invoke()
        }
    }

    fun handleDoubleTap() {
        val cds = candlesState.value
        if (cds.isEmpty()) return
        val vc = visibleCountState.value
        val si = startIndexState.value
        val newCount = (vc / 1.8f).toInt().coerceIn(5, cds.size.coerceAtLeast(5))
        startIndex = ((si + vc / 2) - newCount / 2).coerceIn(0, (cds.size - newCount).coerceAtLeast(0))
        visibleCount = newCount
        if (liveFollowingState.value) onLiveFollowChangeState.value(false)
    }

    // per-symbol precision for tap drawing / crosshair
    Box(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // ── SINGLE CENTRAL GESTURE PIPELINE ─────────────────────────
                    // 1 finger  → PAN (horizontal candles + vertical price scale)
                    // 2+ fingers → PINCH ZOOM around centroid (plus pan)
                    // tap / double-tap / long-press handled when no movement occurs.
                    // pointerInput(Unit) — never restarted by WS ticks or candle changes.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var gestureMode = 0 // 0=unset, 1=pan, 2=zoom
                        var lastCentroid = down.position
                        var startPos = down.position
                        var moved = false
                        var panAccumX = 0f
                        var panAccumY = 0f
                        var suppressTap = false
                        val downTime = java.lang.System.currentTimeMillis()

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if (pressed == 0) {
                                // gesture ended — disambiguate tap vs double-tap.
                                // Only zoom on double-tap when NO drawing tool is active
                                // (CURSOR / plain chart tap); a drawing tool wants both
                                // taps to place points, not zoom.
                                val now = java.lang.System.currentTimeMillis()
                                if (!moved && !suppressTap) {
                                    val tool = selectedToolState.value
                                    val cursorMode = tool == null || tool == DrawingTool.CURSOR
                                    if (cursorMode && now - lastTapTime < DOUBLE_TAP_MS) {
                                        handleDoubleTap()
                                        lastTapTime = 0L
                                        suppressTap = true
                                    } else {
                                        lastTapTime = now
                                    }
                                }
                                break
                            }

                            val pos = event.changes[0].position
                            moved = moved || abs(pos.x - startPos.x) > TOUCH_SLOP ||
                                abs(pos.y - startPos.y) > TOUCH_SLOP

                            // long-press (finger held still) → crosshair
                            if (!moved && !suppressTap &&
                                java.lang.System.currentTimeMillis() - downTime > LONG_PRESS_MS
                            ) {
                                suppressTap = true
                                val cds = candlesState.value
                                if (cds.isNotEmpty()) {
                                    val vc = visibleCountState.value
                                    val si = startIndexState.value
                                    val slot = size.width / vc.coerceAtLeast(1)
                                    val idx = si + (pos.x / slot).toInt()
                                    if (idx in cds.indices) {
                                        crosshairIndex = idx
                                        onCrosshair?.invoke(idx)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            // choose mode on first significant event
                            if (gestureMode == 0) {
                                if (pressed >= 2) {
                                    gestureMode = 2
                                    lastCentroid = event.calculateCentroid(useCurrent = true)
                                } else if (moved) {
                                    gestureMode = 1
                                    // anchor to the DOWN position (not current) so the
                                    // first movement delta is not lost
                                    lastCentroid = startPos
                                    suppressTap = true
                                }
                            }

                            if (gestureMode == 2) {
                                // PINCH ZOOM + 2-finger pan
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (abs(zoom - 1f) > 0.01f || abs(pan.x) > 0.5f || abs(pan.y) > 0.5f) {
                                    applyZoom(zoom, pan, centroid, size)
                                }
                            } else if (gestureMode == 1) {
                                // PAN (1 finger) — accumulate px so slow drags still move
                                panAccumX += pos.x - lastCentroid.x
                                panAccumY += pos.y - lastCentroid.y
                                lastCentroid = pos
                                val dx = panAccumX
                                val dy = panAccumY
                                panAccumX = 0f
                                panAccumY = 0f
                                applyPan(dx, dy, size)
                            }

                            if (pressed >= 2) lastCentroid = event.calculateCentroid(useCurrent = true)
                            else lastCentroid = event.changes[0].position
                            event.changes.forEach { it.consume() }
                        }

                        // TAP (no movement, no long-press): delayed so a double-tap
                        // is not misinterpreted as two single taps.
                        if (!moved && !suppressTap) {
                            val cds = candlesState.value
                            val vc = visibleCountState.value
                            val si = startIndexState.value
                            val tool = selectedToolState.value
                            val cursorMode = tool == null || tool == DrawingTool.CURSOR
                            if (!cursorMode) {
                                val slot = size.width / vc.coerceAtLeast(1)
                                val idx = si + (startPos.x / slot).toInt()
                                val price = priceAt(startPos.y, size, verticalPanState.value, cds, si, vc)
                                val pt = ChartPoint(idx.toFloat(), price)
                                val first = drawingStart
                                if (first == null) {
                                    drawingStart = pt
                                } else {
                                    onDrawingCreated?.invoke(Drawing(tool = tool, points = listOf(first, pt)))
                                    drawingStart = null
                                }
                                if (tool == DrawingTool.VOLUME_PROFILE) {
                                    val s0 = selectionStart
                                    if (s0 == null) selectionStart = idx else {
                                        val lo = min(s0, idx); val hi = maxOf(s0, idx)
                                        onCandleRangeSelected?.invoke(lo, hi)
                                        selectionStart = null
                                    }
                                }
                            } else {
                                // CURSOR / no-tool tap: don't force live-follow back on
                                onChartTap?.invoke()
                            }
                        }
                    }
                },
        ) {
            if (candles.isEmpty()) return@Canvas
            // Real right-edge margin: when the view is at the last candles
            // (startIndex + effVisible reaches the end), show only ~80% of them so
            // the newest candle rests at ~79% of the width and there is visible
            // empty space on the right (scroll affordance). Otherwise show effVisible.
            val atRightEdge = startIndex + effVisible >= candles.size
            val targetCount = if (atRightEdge) {
                (effVisible * 4 / 5).coerceAtLeast(5)
            } else {
                effVisible
            }
            val showCount = targetCount.coerceAtMost(candles.size - startIndex)
            val endIdx = startIndex + showCount
            if (startIndex >= endIdx) return@Canvas
            val slice = candles.subList(startIndex, endIdx)

            drawGrid(size)
            drawPriceAxis(size, slice, symbol)
            drawTimeAxis(size, slice, startIndex, effVisible)

            // slot based on effVisible (fixed), NOT slice.size — so when the
            // live-follow margin leaves empty space on the right, the last candle
            // keeps its position and candles don't stretch to fill the screen.
            val slot = size.width / effVisible.coerceAtLeast(1)
            val bodyW = (slot * 0.62f).coerceAtMost(11f)
            val hi = slice.maxOf { it.high }
            val lo = slice.minOf { it.low }
            val range = (hi - lo).takeIf { it > 0.0 } ?: 1.0
            val pxPerUnit = (size.height * 0.86f) / range.toFloat()
            val topPad = size.height * 0.07f
            // vertical pan: shift the whole price scale by accumulated offset
            fun yOf(v: Double): Float = topPad + (hi - v).toFloat() * pxPerUnit + verticalPanOffset

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
                drawIndicator(ind, slice, ::yOf, slot, startIndex)
            }

            // drawings
            for (d in drawings) {
                drawDrawing(d, candles, startIndex, visibleCount, size, ::yOf, symbol)
            }

            // volume profile histogram
            volumeProfile?.let { vp ->
                drawVolumeProfile(vp, size, symbol)
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
                    com.tradevision.app.data.Instrument.formatPrice(symbol, c.close),
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

/** Helper: convert screen y to price using the real chart scale. */
private fun priceAt(
    y: Float,
    size: androidx.compose.ui.unit.IntSize,
    verticalPan: Float,
    candles: List<Candle>,
    startIndex: Int,
    visibleCount: Int,
): Double {
    if (candles.isEmpty()) return 0.0
    val endIdx = (startIndex + visibleCount).coerceAtMost(candles.size)
    if (startIndex >= endIdx) return 0.0
    val slice = candles.subList(startIndex, endIdx)
    val hi = slice.maxOf { it.high }
    val lo = slice.minOf { it.low }
    val range = (hi - lo).takeIf { it > 0.0 } ?: 1.0
    val topPad = size.height * 0.07f
    val pxPerUnit = (size.height * 0.86f) / range.toFloat()
    return hi - (y - topPad - verticalPan) / pxPerUnit
}

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

private fun DrawScope.drawPriceAxis(size: Size, slice: List<Candle>, symbol: String) {
    if (slice.isEmpty()) return
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
        drawContext.canvas.nativeCanvas.drawText(
            com.tradevision.app.data.Instrument.formatPrice(symbol, v),
            size.width - 58f, y, paint,
        )
    }
}

private fun DrawScope.drawTimeAxis(size: Size, slice: List<Candle>, startIndex: Int, visibleCount: Int) {
    val paint = android.graphics.Paint().apply {
        color = TvTextDim.toArgb()
        textSize = 18f
        isAntiAlias = true
    }
    if (slice.isEmpty()) return
    val slot = size.width / visibleCount.coerceAtLeast(1)
    val n = 4
    for (i in 0..n) {
        val idx = i * (slice.size - 1) / n.coerceAtLeast(1)
        if (idx !in slice.indices) continue
        val c = slice[idx]
        val x = idx * slot + slot / 2f
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

private fun DrawScope.drawDrawing(d: Drawing, candles: List<Candle>, startIndex: Int, visibleCount: Int, size: Size, yOf: (Double) -> Float, symbol: String) {
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
                drawContext.canvas.nativeCanvas.drawText(
                    String.format(java.util.Locale.US, "%.1f%%", lv * 100), 4f, y - 4f, paint)
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
            drawContext.canvas.nativeCanvas.drawText(
                "Entry " + com.tradevision.app.data.Instrument.formatPrice(symbol, entry), 6f, yE - 4f, paint)
            drawContext.canvas.nativeCanvas.drawText(
                "Stop " + com.tradevision.app.data.Instrument.formatPrice(symbol, stop), 6f, yS - 4f, paint)
            drawContext.canvas.nativeCanvas.drawText(
                "Target " + com.tradevision.app.data.Instrument.formatPrice(symbol, target), 6f, yT - 4f, paint)
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

private fun DrawScope.drawVolumeProfile(vp: VolumeProfileResult, size: Size, symbol: String) {
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
    drawContext.canvas.nativeCanvas.drawText(
        "POC " + com.tradevision.app.data.Instrument.formatPrice(symbol, vp.pocPrice),
        left, priceToY(vp.pocPrice, vp.pocPrice, size) - 4f, paint)
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
