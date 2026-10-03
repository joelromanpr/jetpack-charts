package com.joelromanpr.charts.compose

import com.joelromanpr.charts.core.nearestCandle
import com.joelromanpr.charts.core.nearestPoint
import kotlin.math.abs

internal fun selectionAt(x: Double, layers: List<ChartLayer>): ChartSelection? {
    val nearest = layers.mapIndexedNotNull { index, layer -> selectedValue(layer, index, x) }
    if (nearest.isEmpty()) return null
    val selectedX = nearest.minBy { abs((it.point?.x ?: it.candle!!.x) - x) }.let { it.point?.x ?: it.candle!!.x }
    val values = layers.mapIndexedNotNull { index, layer ->
        val range = when (layer) {
            is ChartLayer.Line -> layer.series.xRange()
            is ChartLayer.Columns -> layer.series.xRange()
            is ChartLayer.Scatter -> layer.series.xRange()
            is ChartLayer.Candles -> layer.series.xRange()
        }
        if (range == null || selectedX !in range.min..range.max) null else selectedValue(layer, index, selectedX)
    }
    return ChartSelection(selectedX, values)
}

private fun selectedValue(layer: ChartLayer, index: Int, x: Double): ChartSelectedValue? = when (layer) {
    is ChartLayer.Line -> layer.series.nearestPoint(x)?.let { ChartSelectedValue(index, layer.name, point = it, axis = layer.axis) }
    is ChartLayer.Columns -> layer.series.nearestPoint(x)?.let { ChartSelectedValue(index, layer.name, point = it, axis = layer.axis) }
    is ChartLayer.Scatter -> layer.series.nearestPoint(x)?.let { ChartSelectedValue(index, layer.name, point = it, axis = layer.axis) }
    is ChartLayer.Candles -> layer.series.nearestCandle(x)?.let { ChartSelectedValue(index, layer.name, candle = it, axis = layer.axis) }
}

internal fun selectionDescription(selection: ChartSelection, axes: ChartAxes): String = buildString {
    append(axes.xFormatter.format(selection.x))
    selection.values.forEach { value ->
        append('\n')
        if (value.name.isNotBlank()) { append(value.name); append(": ") }
        val formatter = if (value.axis == AxisSide.Right) axes.rightYFormatter else axes.yFormatter
        value.point?.let { append(formatter.format(it.y)) }
        value.candle?.let {
            append("O "); append(formatter.format(it.open)); append("  H "); append(formatter.format(it.high))
            append("  L "); append(formatter.format(it.low)); append("  C "); append(formatter.format(it.close))
        }
    }
}

internal fun adjacentX(layers: List<ChartLayer>, selectedX: Double?, forward: Boolean): Double? {
    var candidate: Double? = null
    layers.forEach { layer ->
        val count: Int
        val xAt: (Int) -> Double
        when (layer) {
            is ChartLayer.Candles -> { count = layer.series.candles.size; xAt = { layer.series.candles[it].x } }
            is ChartLayer.Line -> { count = layer.series.points.size; xAt = { layer.series.points[it].x } }
            is ChartLayer.Columns -> { count = layer.series.points.size; xAt = { layer.series.points[it].x } }
            is ChartLayer.Scatter -> { count = layer.series.points.size; xAt = { layer.series.points[it].x } }
        }
        if (count == 0) return@forEach
        var low = 0; var high = count
        if (selectedX != null) {
            while (low < high) {
                val middle = (low + high) ushr 1
                if (xAt(middle) < selectedX || (forward && xAt(middle) == selectedX)) low = middle + 1 else high = middle
            }
        } else low = if (forward) 0 else count
        val index = if (forward) low else low - 1
        if (index in 0 until count) {
            val next = xAt(index)
            val previous = candidate
            if (previous == null || if (forward) next < previous else next > previous) candidate = next
        }
    }
    return candidate
}
