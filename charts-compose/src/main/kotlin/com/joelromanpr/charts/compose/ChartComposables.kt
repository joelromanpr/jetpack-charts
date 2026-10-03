package com.joelromanpr.charts.compose

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joelromanpr.charts.core.CandleSeries
import com.joelromanpr.charts.core.PointSeries

@Composable
public fun LineChart(
    series: PointSeries,
    modifier: Modifier = Modifier,
    state: ChartState = rememberChartState(),
    axes: ChartAxes = ChartAxes.Default,
    color: androidx.compose.ui.graphics.Color? = null,
    fill: androidx.compose.ui.graphics.Color? = null,
    interpolation: LineInterpolation = LineInterpolation.Linear,
    onSelectionChanged: ((ChartSelection?) -> Unit)? = null,
) {
    val layers = remember(series, color, fill, interpolation) { listOf(ChartLayer.Line(series, color, fill, interpolation = interpolation)) }
    CartesianChart(layers, modifier, state, axes, onSelectionChanged = onSelectionChanged)
}

@Composable
public fun ColumnChart(
    series: PointSeries,
    modifier: Modifier = Modifier,
    state: ChartState = rememberChartState(),
    axes: ChartAxes = ChartAxes.Default,
    color: androidx.compose.ui.graphics.Color? = null,
    onSelectionChanged: ((ChartSelection?) -> Unit)? = null,
) {
    val layers = remember(series, color) { listOf(ChartLayer.Columns(series, color)) }
    CartesianChart(layers, modifier, state, axes, onSelectionChanged = onSelectionChanged)
}

@Composable
public fun CandlestickChart(
    series: CandleSeries,
    modifier: Modifier = Modifier,
    state: ChartState = rememberChartState(),
    axes: ChartAxes = ChartAxes.Default,
    onSelectionChanged: ((ChartSelection?) -> Unit)? = null,
) {
    val layers = remember(series) { listOf(ChartLayer.Candles(series)) }
    CartesianChart(layers, modifier, state, axes, onSelectionChanged = onSelectionChanged)
}

@Composable
public fun ScatterChart(
    series: PointSeries,
    modifier: Modifier = Modifier,
    state: ChartState = rememberChartState(),
    axes: ChartAxes = ChartAxes.Default,
    color: androidx.compose.ui.graphics.Color? = null,
    onSelectionChanged: ((ChartSelection?) -> Unit)? = null,
) {
    val layers = remember(series, color) { listOf(ChartLayer.Scatter(series, color)) }
    CartesianChart(layers, modifier, state, axes, onSelectionChanged = onSelectionChanged)
}

@Composable
public fun Sparkline(
    series: PointSeries,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
    fill: androidx.compose.ui.graphics.Color? = null,
    contentDescription: String = "Sparkline",
) {
    val layers = remember(series, color, fill) { listOf(ChartLayer.Line(series, color, fill)) }
    CartesianChart(layers, modifier.defaultMinSize(minHeight = 48.dp), axes = ChartAxes.Hidden, interactionEnabled = false,
        showSelectionTooltip = false, contentDescription = contentDescription)
}
