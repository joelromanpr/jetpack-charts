package com.joelromanpr.charts.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.joelromanpr.charts.core.Candle
import com.joelromanpr.charts.core.ChartRange
import com.joelromanpr.charts.core.Point
import com.joelromanpr.charts.core.PointSeries
import com.joelromanpr.charts.core.decimated
import com.joelromanpr.charts.core.visibleCandles
import com.joelromanpr.charts.core.visiblePoints
import kotlin.math.abs

internal fun chartDomain(layers: List<ChartLayer>): ChartRange? {
    val ranges = layers.mapNotNull {
        when (it) {
            is ChartLayer.Line -> it.series.xRange()
            is ChartLayer.Columns -> it.series.xRange()
            is ChartLayer.Candles -> it.series.xRange()
            is ChartLayer.Scatter -> it.series.xRange()
        }
    }
    return if (ranges.isEmpty()) null else ChartRange(ranges.minOf { it.min }, ranges.maxOf { it.max }).expanded()
}

internal fun plotRect(size: Size, axes: ChartAxes, legend: Boolean, density: Density, rightAxis: Boolean = false): Rect = with(density) {
    val left = (if (axes.showY) axes.yAxisWidth.toPx() else 8.dp.toPx()).coerceIn(0f, size.width)
    val top = (if (legend) 32.dp.toPx() else 8.dp.toPx()).coerceIn(0f, size.height)
    val right = (size.width - if (rightAxis && axes.showRightY) axes.rightYAxisWidth.toPx() else 8.dp.toPx()).coerceIn(left, size.width)
    val bottom = (size.height - if (axes.showX) axes.xAxisHeight.toPx() else 8.dp.toPx()).coerceIn(top, size.height)
    Rect(left, top, right, bottom)
}

internal class PlotCoordinates(
    override val xRange: ChartRange,
    override val yRange: ChartRange,
    val plot: Rect,
    override val rightYRange: ChartRange? = null,
) : ChartCoordinates {
    override fun x(value: Double): Float = plot.left + fraction(value, xRange).toFloat() * plot.width
    override fun y(value: Double, axis: AxisSide): Float = plot.bottom - fraction(value, if (axis == AxisSide.Right) rightYRange ?: yRange else yRange).toFloat() * plot.height

    private fun fraction(value: Double, range: ChartRange): Double {
        val span = range.max - range.min
        return (if (span.isFinite()) (value - range.min) / span
        else (value * 0.5 - range.min * 0.5) / (range.max * 0.5 - range.min * 0.5)).coerceIn(-1e6, 1e6)
    }
}

private data class CachedLabel(val layout: TextLayoutResult, val offset: Offset)
private data class CachedLine(val path: Path, val fill: Path?, val color: Color, val fillColor: Color?, val width: Float)
private data class CachedMark(val rect: Rect, val color: Color, val wick: Pair<Offset, Offset>? = null)
private data class CachedDot(val center: Offset, val radius: Float, val color: Color)

internal class ChartGeometry private constructor(
    val coordinates: PlotCoordinates,
    private val lines: List<CachedLine>,
    private val marks: List<CachedMark>,
    private val dots: List<CachedDot>,
    private val labels: List<CachedLabel>,
    private val referenceLabels: List<CachedLabel>,
    private val references: List<ReferenceLine>,
    private val axes: ChartAxes,
    private val colors: ChartColors,
) {
    fun draw(scope: DrawScope, decoration: ChartDecoration?, reveal: Float = 1f) = with(scope) {
        val plot = coordinates.plot
        if (plot.width <= 0f || plot.height <= 0f) return@with
        if (axes.showGrid) {
            repeat(axes.tickCount) { index ->
                val y = plot.bottom - plot.height * index / (axes.tickCount - 1)
                drawLine(colors.grid, Offset(plot.left, y), Offset(plot.right, y), 1.dp.toPx())
            }
        }
        clipRect(plot.left, plot.top, plot.left + plot.width * reveal.coerceIn(0f, 1f), plot.bottom) {
            marks.forEach { mark ->
                mark.wick?.let { (start, end) -> drawLine(mark.color, start, end, 1.dp.toPx()) }
                drawRect(mark.color, mark.rect.topLeft, mark.rect.size)
            }
            lines.forEach { line ->
                if (line.fill != null && line.fillColor != null) drawPath(line.fill, line.fillColor)
                drawPath(line.path, line.color, style = Stroke(line.width, cap = StrokeCap.Round))
            }
            dots.forEach { dot -> drawCircle(dot.color, dot.radius, dot.center) }
            references.forEach { reference ->
                val y = coordinates.y(reference.y, reference.axis)
                drawLine(reference.color ?: colors.mutedText, Offset(plot.left, y), Offset(plot.right, y), 1.dp.toPx(),
                    pathEffect = if (reference.dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null)
            }
            decoration?.invoke(this, coordinates)
        }
        labels.forEach { drawText(it.layout, topLeft = it.offset) }
        referenceLabels.forEach { drawText(it.layout, topLeft = it.offset) }
    }

    companion object {
        fun build(
            layers: List<ChartLayer>, xRange: ChartRange, requestedYRange: ChartRange?, requestedRightYRange: ChartRange?, plot: Rect,
            axes: ChartAxes, references: List<ReferenceLine>, colors: ChartColors,
            density: Density, textMeasurer: TextMeasurer,
        ): ChartGeometry {
            val rawVisible = layers.map { layer ->
                when (layer) {
                    is ChartLayer.Line -> layer.series.visiblePoints(xRange.min, xRange.max)
                    is ChartLayer.Columns -> layer.series.visiblePoints(xRange.min, xRange.max, false)
                    is ChartLayer.Scatter -> layer.series.visiblePoints(xRange.min, xRange.max, false)
                    is ChartLayer.Candles -> emptyList()
                }
            }
            val pixelBudget = maxOf(1, plot.width.toInt())
            val denseColumns = layers.indices.any { layers[it] is ChartLayer.Columns && rawVisible[it].size > pixelBudget }
            val visible = rawVisible.mapIndexed { index, points ->
                when {
                    layers[index] is ChartLayer.Columns && denseColumns -> aggregateColumns(points, xRange, pixelBudget)
                    layers[index] is ChartLayer.Scatter && points.size > pixelBudget * 2 -> PointSeries(points).decimated(maxOf(4, pixelBudget * 2)).points
                    else -> points
                }
            }
            val candles = layers.map { if (it is ChartLayer.Candles) it.series.visibleCandles(xRange.min, xRange.max, false) else emptyList() }
            val stackOffsets = HashMap<Triple<AxisSide, String, Double>, Pair<Double, Double>>()
            val columnExtents = ArrayList<Triple<AxisSide, Double, Double>>()
            val columnBases = layers.mapIndexed { index, layer ->
                if (layer !is ChartLayer.Columns) emptyList() else visible[index].map { point ->
                    val key = layer.stackKey?.let { Triple(layer.axis, it, point.x) }
                    val previous = key?.let { stackOffsets[it] } ?: (0.0 to 0.0)
                    val base = if (point.y >= 0.0) previous.first else previous.second
                    val end = (base + point.y).coerceIn(-Double.MAX_VALUE, Double.MAX_VALUE)
                    if (key != null) stackOffsets[key] = if (point.y >= 0.0) end to previous.second else previous.first to end
                    columnExtents += Triple(layer.axis, base, end)
                    base
                }
            }
            fun yValues(axis: AxisSide): List<Double> = buildList {
                layers.forEachIndexed { index, layer ->
                    if (layer.axis == axis) {
                        if (layer is ChartLayer.Candles) candles[index].forEach { add(it.low); add(it.high) }
                        else if (layer !is ChartLayer.Columns) visible[index].forEach { add(it.y) }
                    }
                }
                columnExtents.forEach { (side, base, end) -> if (side == axis) { if (base.isFinite()) add(base); if (end.isFinite()) add(end) } }
            }
            fun paddedRange(values: List<Double>, requested: ChartRange?): ChartRange {
                val raw = requested ?: ChartRange.rangeOf(values)?.expanded() ?: ChartRange(0.0, 1.0)
                val padding = if (requested == null) (raw.max * 0.06 - raw.min * 0.06) else 0.0
                return ChartRange((raw.min - padding).coerceAtLeast(-Double.MAX_VALUE), (raw.max + padding).coerceAtMost(Double.MAX_VALUE)).expanded()
            }
            val yRange = paddedRange(yValues(AxisSide.Left), requestedYRange)
            val rightYRange = if (layers.any { it.axis == AxisSide.Right }) paddedRange(yValues(AxisSide.Right), requestedRightYRange) else null
            val coordinates = PlotCoordinates(xRange, yRange, plot, rightYRange)
            val lines = ArrayList<CachedLine>()
            val marks = ArrayList<CachedMark>()
            val dots = ArrayList<CachedDot>()
            val columnSlots = layers.mapIndexedNotNull { index, layer ->
                if (layer is ChartLayer.Columns) layer.stackKey ?: "\u0000$index" else null
            }.distinct()
            val budget = maxOf(4, (plot.width * 2).toInt())
            layers.forEachIndexed { index, layer ->
                val layerCoordinates = if (layer.axis == AxisSide.Right) PlotCoordinates(xRange, rightYRange ?: yRange, plot) else coordinates
                val defaultColor = colors.series.getOrNull(index % colors.series.size.coerceAtLeast(1)) ?: colors.primary
                when (layer) {
                    is ChartLayer.Line -> {
                        val points = if (visible[index].size > budget) PointSeries(visible[index]).decimated(budget).points else visible[index]
                        val path = linePath(points, layerCoordinates, layer.interpolation)
                        val fill = if (layer.fill != null && points.isNotEmpty()) Path().apply {
                            addPath(path)
                            lineTo(layerCoordinates.x(points.last().x), plot.bottom)
                            lineTo(layerCoordinates.x(points.first().x), plot.bottom)
                            close()
                        } else null
                        lines += CachedLine(path, fill, layer.color ?: defaultColor, layer.fill, with(density) { layer.strokeWidth.toPx() })
                        if (points.size == 1) dots += CachedDot(Offset(layerCoordinates.x(points[0].x), layerCoordinates.y(points[0].y)), with(density) { 3.dp.toPx() }, layer.color ?: defaultColor)
                    }
                    is ChartLayer.Columns -> {
                        val points = visible[index]
                        val slot = columnSlots.indexOf(layer.stackKey ?: "\u0000$index")
                        val interval = intervalWidth(points.map { it.x }, layerCoordinates)
                        val width = interval * layer.widthFraction / columnSlots.size.coerceAtLeast(1)
                        points.forEachIndexed { pointIndex, point ->
                            val center = layerCoordinates.x(point.x) + (slot - (columnSlots.size - 1) * 0.5f) * width
                            val base = columnBases[index][pointIndex]
                            val end = (base + point.y).coerceIn(-Double.MAX_VALUE, Double.MAX_VALUE)
                            if (end.isFinite()) {
                                val y1 = layerCoordinates.y(base); val y2 = layerCoordinates.y(end)
                                marks += CachedMark(Rect(center - width / 2, minOf(y1, y2), center + width / 2, maxOf(y1, y2).coerceAtLeast(minOf(y1, y2) + 1f)), layer.color ?: defaultColor)
                            }
                        }
                    }
                    is ChartLayer.Candles -> {
                        val items = compressCandles(candles[index], maxOf(1, plot.width.toInt()))
                        val width = (intervalWidth(items.map { it.x }, layerCoordinates) * layer.widthFraction).coerceAtLeast(1f)
                        items.forEach { candle ->
                            val x = layerCoordinates.x(candle.x)
                            val top = minOf(layerCoordinates.y(candle.open), layerCoordinates.y(candle.close))
                            val bottom = maxOf(layerCoordinates.y(candle.open), layerCoordinates.y(candle.close)).coerceAtLeast(top + 1f)
                            marks += CachedMark(Rect(x - width / 2, top, x + width / 2, bottom),
                                if (candle.close >= candle.open) layer.risingColor ?: colors.positive else layer.fallingColor ?: colors.negative,
                                Offset(x, layerCoordinates.y(candle.high)) to Offset(x, layerCoordinates.y(candle.low)))
                        }
                    }
                    is ChartLayer.Scatter -> {
                        visible[index].forEach { point -> dots += CachedDot(Offset(layerCoordinates.x(point.x), layerCoordinates.y(point.y)), with(density) { layer.radius.toPx() }, layer.color ?: defaultColor) }
                    }
                }
            }
            val labelStyle = axes.labelStyle.copy(color = colors.mutedText)
            val labels = buildList {
                repeat(axes.tickCount) { index ->
                    val fraction = index.toDouble() / (axes.tickCount - 1)
                    if (axes.showY) {
                        val label = textMeasurer.measure(axes.yFormatter.format(yRange.valueAt(fraction)), labelStyle)
                        add(CachedLabel(label, Offset((plot.left - label.size.width - with(density) { 6.dp.toPx() }).coerceAtLeast(0f), (plot.bottom - plot.height * fraction.toFloat() - label.size.height / 2f).coerceIn(0f, plot.bottom))))
                    }
                    if (axes.showX) {
                        val label = textMeasurer.measure(axes.xFormatter.format(xRange.valueAt(fraction)), labelStyle)
                        val x = (plot.left + plot.width * fraction.toFloat() - label.size.width / 2f).coerceIn(plot.left, maxOf(plot.left, plot.right - label.size.width))
                        add(CachedLabel(label, Offset(x, plot.bottom + with(density) { 6.dp.toPx() })))
                    }
                    if (rightYRange != null && axes.showRightY) {
                        val label = textMeasurer.measure(axes.rightYFormatter.format(rightYRange.valueAt(fraction)), labelStyle)
                        add(CachedLabel(label, Offset(plot.right + with(density) { 6.dp.toPx() }, (plot.bottom - plot.height * fraction.toFloat() - label.size.height / 2f).coerceIn(0f, plot.bottom))))
                    }
                }
            }
            val referenceLabels = references.filter { reference ->
                val range = if (reference.axis == AxisSide.Right) rightYRange ?: yRange else yRange
                reference.label.isNotBlank() && reference.y in range.min..range.max
            }.map {
                val label = textMeasurer.measure(it.label, labelStyle.copy(color = it.color ?: colors.mutedText))
                CachedLabel(label, Offset((plot.right - label.size.width).coerceAtLeast(plot.left), (coordinates.y(it.y, it.axis) - label.size.height - 2f).coerceAtLeast(plot.top)))
            }
            return ChartGeometry(coordinates, lines, marks, dots, labels, referenceLabels, references, axes, colors)
        }
    }
}

private fun linePath(points: List<Point>, coordinates: PlotCoordinates, interpolation: LineInterpolation): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(coordinates.x(points.first().x), coordinates.y(points.first().y))
    for (index in 1 until points.size) {
        val previous = points[index - 1]
        val current = points[index]
        val x0 = coordinates.x(previous.x); val y0 = coordinates.y(previous.y)
        val x1 = coordinates.x(current.x); val y1 = coordinates.y(current.y)
        when (interpolation) {
            LineInterpolation.Linear -> lineTo(x1, y1)
            LineInterpolation.Step -> { lineTo(x1, y0); lineTo(x1, y1) }
            LineInterpolation.StepBefore -> { lineTo(x0, y1); lineTo(x1, y1) }
            // Horizontal controls keep the curve within each segment's value range.
            LineInterpolation.Cubic -> { val middle = (x0 + x1) / 2; cubicTo(middle, y0, middle, y1, x1, y1) }
        }
    }
}

private fun intervalWidth(xs: List<Double>, coordinates: PlotCoordinates): Float {
    if (xs.size < 2) return minOf(24f, coordinates.plot.width * 0.4f)
    var minimum = Float.POSITIVE_INFINITY
    for (index in 1 until xs.size) minimum = minOf(minimum, abs(coordinates.x(xs[index]) - coordinates.x(xs[index - 1])))
    return minimum.coerceAtLeast(1f)
}

private fun compressCandles(candles: List<Candle>, budget: Int): List<Candle> {
    if (candles.size <= budget) return candles
    return List(budget) { bucket ->
        val start = (bucket.toLong() * candles.size / budget).toInt()
        val end = ((bucket + 1L) * candles.size / budget).toInt()
        val first = candles[start]; val last = candles[end - 1]
        var high = first.high; var low = first.low; var volume = 0.0
        for (index in start until end) { high = maxOf(high, candles[index].high); low = minOf(low, candles[index].low); volume += candles[index].volume }
        Candle(last.x, first.open, high, low, last.close, volume.coerceAtMost(Double.MAX_VALUE))
    }
}

private fun aggregateColumns(points: List<Point>, range: ChartRange, budget: Int): List<Point> {
    if (points.isEmpty()) return points
    val sums = LinkedHashMap<Int, Double>()
    points.forEach { point ->
        val bucket = (range.fractionOf(point.x) * budget).toInt().coerceIn(0, budget - 1)
        sums[bucket] = ((sums[bucket] ?: 0.0) + point.y).coerceIn(-Double.MAX_VALUE, Double.MAX_VALUE)
    }
    return sums.map { (bucket, y) -> Point(range.valueAt((bucket + 0.5) / budget), y) }
}
