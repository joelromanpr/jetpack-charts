package com.joelromanpr.charts.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joelromanpr.charts.core.ChartRange
import kotlin.math.abs

/** A shared rendering and interaction engine for price, volume, and indicator layers. */
@Composable
public fun CartesianChart(
    layers: List<ChartLayer>,
    modifier: Modifier = Modifier,
    state: ChartState = rememberChartState(),
    axes: ChartAxes = ChartAxes.Default,
    yRange: ChartRange? = null,
    referenceLines: List<ReferenceLine> = emptyList(),
    showLegend: Boolean = false,
    showSelectionTooltip: Boolean = true,
    interactionEnabled: Boolean = true,
    contentDescription: String = "Chart",
    onSelectionChanged: ((ChartSelection?) -> Unit)? = null,
    decoration: ChartDecoration? = null,
    rightYRange: ChartRange? = null,
    animation: ChartAnimation = ChartAnimation.None,
) {
    val colors = LocalChartColors.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val domain = remember(layers) { chartDomain(layers) }
    LaunchedEffect(state, domain) { domain?.let(state::updateDomain) }
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(domain != null, animation) {
        if (domain == null || animation !is ChartAnimation.Reveal) reveal.snapTo(1f)
        else {
            reveal.snapTo(0f)
            // Compose's animation clock honors the system animator duration scale.
            reveal.animateTo(1f, tween(animation.durationMillis))
        }
    }
    val viewport = domain?.let(state::viewport)
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    val currentLayers by rememberUpdatedState(layers)
    val currentDomain by rememberUpdatedState(domain)
    val currentAxes by rememberUpdatedState(axes)
    val currentLegend by rememberUpdatedState(showLegend)
    val hasRightAxis = layers.any { it.axis == AxisSide.Right }
    val currentRightAxis by rememberUpdatedState(hasRightAxis)
    val currentOnSelection by rememberUpdatedState(onSelectionChanged)
    val currentSize by rememberUpdatedState(measuredSize)

    fun publishSelection(x: Double?) {
        val selection = x?.let { selectionAt(it, currentLayers) }
        state.select(selection?.x)
        currentOnSelection?.invoke(selection)
    }

    fun selectPosition(offset: Offset) {
        val bounds = currentDomain ?: return
        val visible = state.viewport(bounds)
        val plot = plotRect(Size(currentSize.width.toFloat(), currentSize.height.toFloat()), currentAxes, currentLegend, density, currentRightAxis)
        if (plot.width <= 0f || offset.x !in plot.left..plot.right || offset.y !in plot.top..plot.bottom) return
        publishSelection(visible.valueAt(((offset.x - plot.left) / plot.width).toDouble().coerceIn(0.0, 1.0)))
    }

    fun moveSelection(forward: Boolean): Boolean {
        val bounds = currentDomain ?: return false
        val current = state.selectedX
        val next = adjacentX(currentLayers, current, forward)
            ?: if (current == null) if (forward) bounds.min else bounds.max else return false
        publishSelection(next)
        return true
    }

    // Keep this modifier instance stable during selection updates. Only viewport/data/style changes rebuild paths.
    val drawing = remember(layers, viewport, yRange, rightYRange, axes, referenceLines, colors, density, textMeasurer, state, decoration, showLegend, hasRightAxis, reveal) {
        Modifier.drawWithCache {
            val geometry = viewport?.let {
                ChartGeometry.build(layers, it, yRange, rightYRange, plotRect(size, axes, showLegend, this, hasRightAxis), axes, referenceLines, colors, this, textMeasurer)
            }
            onDrawBehind {
                geometry?.draw(this, decoration, reveal.value)
                val selectionX = state.selectedX
                if (geometry != null && selectionX != null && selectionX in geometry.coordinates.xRange.min..geometry.coordinates.xRange.max) {
                    val coordinates = geometry.coordinates
                    val plot = coordinates.plot
                    clipRect(plot.left, plot.top, plot.right, plot.bottom) {
                        val x = coordinates.x(selectionX)
                        drawLine(colors.mutedText, Offset(x, plot.top), Offset(x, plot.bottom), 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                        selectionAt(selectionX, layers)?.values?.forEach { selected ->
                            selected.point?.let { point ->
                                val axis = layers[selected.layerIndex].axis
                                drawCircle(colors.background, 5.dp.toPx(), Offset(coordinates.x(point.x), coordinates.y(point.y, axis)))
                                drawCircle(colors.primary, 3.dp.toPx(), Offset(coordinates.x(point.x), coordinates.y(point.y, axis)))
                            }
                        }
                    }
                }
            }
        }
    }

    val selected = state.selectedX?.takeIf { domain != null && it in domain.min..domain.max }?.let { selectionAt(it, layers) }
    val selectedDescription = selected?.let { selectionDescription(it, axes) }
    val accessibility = Modifier.semantics(mergeDescendants = true) {
        this.contentDescription = contentDescription
        role = Role.Image
        stateDescription = selectedDescription ?: if (domain == null) "No data" else
            "${layers.size} series. Values from ${axes.xFormatter.format(domain.min)} to ${axes.xFormatter.format(domain.max)}."
        if (interactionEnabled && domain != null) customActions = listOf(
            CustomAccessibilityAction("Next data point") { moveSelection(true) },
            CustomAccessibilityAction("Previous data point") { moveSelection(false) },
            CustomAccessibilityAction("Zoom in") { state.transform(domain, 1.5f, 0f, 0.5f); true },
            CustomAccessibilityAction("Zoom out") { state.transform(domain, 1 / 1.5f, 0f, 0.5f); true },
            CustomAccessibilityAction("Reset chart") { state.reset(); currentOnSelection?.invoke(null); true },
        )
    }
    val gestures = if (interactionEnabled) Modifier
        .pointerInput(state, density) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var accumulated = Offset.Zero
                var transforming = false
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.isConsumed }) break
                    if (event.changes.none { it.pressed }) break
                    val pan = event.calculatePan()
                    accumulated += pan
                    val pinch = event.changes.count { it.pressed } > 1
                    if (pinch || (abs(accumulated.x) > viewConfiguration.touchSlop && abs(accumulated.x) > abs(accumulated.y))) transforming = true
                    if (!transforming && abs(accumulated.y) > viewConfiguration.touchSlop) break
                    if (transforming) {
                        val bounds = currentDomain
                        val plot = plotRect(Size(currentSize.width.toFloat(), currentSize.height.toFloat()), currentAxes, currentLegend, density, currentRightAxis)
                        if (bounds != null && plot.width > 0f) {
                            val centroidX = event.changes.filter { it.pressed }.map { it.position.x }.average().toFloat()
                            state.transform(bounds, event.calculateZoom(), pan.x / plot.width, (centroidX - plot.left) / plot.width)
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .pointerInput(state, density) {
            detectTapGestures(onTap = ::selectPosition, onDoubleTap = { state.reset(); currentOnSelection?.invoke(null) })
        }
        .pointerInput(state, density) {
            detectDragGesturesAfterLongPress(onDragStart = ::selectPosition) { change, _ ->
                change.consume()
                selectPosition(change.position)
            }
        }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                Key.DirectionRight -> moveSelection(true)
                Key.DirectionLeft -> moveSelection(false)
                Key.Plus, Key.Equals -> domain?.let { state.transform(it, 1.5f, 0f, 0.5f); true } ?: false
                Key.Minus -> domain?.let { state.transform(it, 1 / 1.5f, 0f, 0.5f); true } ?: false
                Key.Escape, Key.Home -> { state.reset(); currentOnSelection?.invoke(null); true }
                else -> false
            }
        }.focusable() else Modifier

    Box(modifier.defaultMinSize(minWidth = 120.dp, minHeight = 180.dp)
        .background(colors.background).onSizeChanged { measuredSize = it }.then(accessibility).then(gestures).then(drawing)) {
        if (domain == null) BasicText("No data", Modifier.align(Alignment.Center), style = TextStyle(color = colors.mutedText, fontSize = 13.sp))
        if (showLegend && domain != null) ChartLegend(layers, Modifier.align(Alignment.TopStart).padding(horizontal = 8.dp, vertical = 4.dp))
        if (showSelectionTooltip && selectedDescription != null) BasicText(
            selectedDescription,
            Modifier.align(Alignment.TopEnd).padding(6.dp).background(colors.surface).padding(horizontal = 8.dp, vertical = 5.dp),
            style = TextStyle(color = colors.text, fontSize = 11.sp),
        )
    }
}

@Composable
private fun ChartLegend(layers: List<ChartLayer>, modifier: Modifier) {
    val colors = LocalChartColors.current
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        layers.forEachIndexed { index, layer ->
            val color = when (layer) {
                is ChartLayer.Line -> layer.color
                is ChartLayer.Columns -> layer.color
                is ChartLayer.Scatter -> layer.color
                is ChartLayer.Candles -> layer.risingColor
            } ?: colors.series[index % colors.series.size]
            BasicText("● ${layer.name.ifBlank { "Series ${index + 1}" }}", style = TextStyle(color = color, fontSize = 12.sp))
        }
    }
}
