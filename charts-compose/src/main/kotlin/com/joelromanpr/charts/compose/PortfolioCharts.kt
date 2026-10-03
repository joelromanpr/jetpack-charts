package com.joelromanpr.charts.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.joelromanpr.charts.core.Slice
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

@Composable
public fun PieChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    colors: List<Color> = LocalChartColors.current.series,
    contentDescription: String = "Pie chart",
    valueFormatter: (Double) -> String = { it.toString() },
    onSliceSelected: ((Int?) -> Unit)? = null,
) {
    AllocationChart(slices, modifier, 0f, colors, contentDescription, valueFormatter, onSliceSelected)
}

@Composable
public fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    holeFraction: Float = 0.68f,
    colors: List<Color> = LocalChartColors.current.series,
    contentDescription: String = "Allocation chart",
    valueFormatter: (Double) -> String = { it.toString() },
    onSliceSelected: ((Int?) -> Unit)? = null,
    center: @Composable () -> Unit = {},
) {
    require(holeFraction.isFinite() && holeFraction in 0f..0.9f)
    Box(modifier, contentAlignment = Alignment.Center) {
        AllocationChart(slices, Modifier, holeFraction, colors, contentDescription, valueFormatter, onSliceSelected)
        center()
    }
}

@Composable
private fun AllocationChart(
    slices: List<Slice>,
    modifier: Modifier,
    holeFraction: Float,
    colors: List<Color>,
    description: String,
    formatter: (Double) -> String,
    onSelected: ((Int?) -> Unit)?,
) {
    require(colors.isNotEmpty()) { "Provide at least one slice color" }
    val palette = LocalChartColors.current
    val entries = remember(slices) { slices.toList() }
    val weights = remember(entries) {
        val largest = entries.maxOfOrNull { it.value } ?: 0.0
        val scaled = entries.map { if (largest > 0.0) it.value / largest else 0.0 }
        val total = scaled.sum()
        scaled.map { if (total > 0.0) it / total * 360.0 else 0.0 }
    }
    val selectedState = rememberSaveable(entries.map { it.label }) { mutableIntStateOf(-1) }
    var selected by selectedState
    val density = LocalDensity.current
    val sliceColors = remember(colors) { colors.toList() }
    val callback by rememberUpdatedState(onSelected)
    val active = selected.takeIf { it in entries.indices && weights[it] > 0.0 }
    fun select(index: Int?) {
        val valid = index?.takeIf { it in entries.indices && weights[it] > 0.0 }
        selected = valid ?: -1
        callback?.invoke(valid)
    }
    fun moveSelection(forward: Boolean): Boolean {
        val available = entries.indices.filter { weights[it] > 0.0 }
        if (available.isEmpty()) return false
        val next = if (forward) available.firstOrNull { it > (active ?: -1) } ?: available.first()
        else available.lastOrNull { it < (active ?: entries.size) } ?: available.last()
        select(next)
        return true
    }
    val empty = weights.none { it > 0.0 }
    val drawing = remember(weights, sliceColors, palette, holeFraction, density, selectedState) {
        Modifier.drawWithCache {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (min(size.width, size.height) / 2f - 8.dp.toPx()).coerceAtLeast(0f)
            val thickness = radius * (1f - holeFraction)
            val outer = Size(radius * 2f, radius * 2f)
            val outerOffset = center - Offset(radius, radius)
            val ringRadius = radius - thickness / 2f
            val ringSize = Size(ringRadius * 2f, ringRadius * 2f)
            val ringOffset = center - Offset(ringRadius, ringRadius)
            onDrawBehind {
                if (radius <= 0f) return@onDrawBehind
                if (empty) {
                    if (holeFraction == 0f) drawCircle(palette.grid, radius)
                    else drawCircle(palette.grid, ringRadius, style = Stroke(thickness))
                }
                var angle = -90.0
                val lastSlice = weights.indexOfLast { it > 0.0 }
                weights.forEachIndexed { index, sweep ->
                    if (sweep > 0.0) {
                        val color = sliceColors[index % sliceColors.size]
                        val sweepAngle = if (index == lastSlice) 270.0 - angle else sweep
                        if (holeFraction == 0f) {
                            drawArc(color, angle.toFloat(), sweepAngle.toFloat(), true, outerOffset, outer)
                        } else {
                            drawArc(color, angle.toFloat(), sweepAngle.toFloat(), false, ringOffset, ringSize, style = Stroke(thickness))
                        }
                        if (selectedState.intValue == index) {
                            drawArc(palette.text, angle.toFloat(), sweepAngle.toFloat(), false, outerOffset, outer, style = Stroke(3.dp.toPx()))
                        }
                    }
                    angle += sweep
                }
            }
        }
    }
    Box(modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.matchParentSize()
                .semantics {
                    contentDescription = description
                    role = Role.Image
                    stateDescription = active?.let { "${entries[it].label}: ${formatter(entries[it].value)}" }
                        ?: if (empty) "No data" else "${entries.size} slices"
                    if (!empty) {
                        customActions = listOf(
                            CustomAccessibilityAction("Next slice") { moveSelection(true) },
                            CustomAccessibilityAction("Previous slice") { moveSelection(false) },
                        )
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.DirectionRight, Key.DirectionDown -> moveSelection(true)
                        Key.DirectionLeft, Key.DirectionUp -> moveSelection(false)
                        Key.Escape -> { select(null); true }
                        else -> false
                    }
                }.focusable()
                .pointerInput(weights, holeFraction, density, selectedState) {
                    detectTapGestures { position ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = min(size.width, size.height) / 2f - 8.dp.toPx()
                        val distance = hypot(position.x - center.x, position.y - center.y)
                        if (radius <= 0f || distance < radius * holeFraction || distance > radius || empty) {
                            select(null)
                        } else {
                            val degrees = ((Math.toDegrees(atan2((position.y - center.y).toDouble(), (position.x - center.x).toDouble())) + 450.0) % 360.0)
                            var cumulative = 0.0
                            select(weights.indices.firstOrNull {
                                cumulative += weights[it]
                                weights[it] > 0.0 && degrees < cumulative
                            } ?: weights.indices.lastOrNull { weights[it] > 0.0 })
                        }
                    }
                }
                .then(drawing),
        ) {}
        if (empty) BasicText("No data", style = TextStyle(color = palette.mutedText))
    }
}
