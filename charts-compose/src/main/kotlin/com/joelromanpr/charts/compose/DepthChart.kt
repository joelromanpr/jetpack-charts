package com.joelromanpr.charts.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.joelromanpr.charts.core.OrderBook
import com.joelromanpr.charts.core.ChartRange

/** Cumulative quantity by price. Input levels are raw quantities, not cumulative totals. */
@Composable
public fun DepthChart(
    orderBook: OrderBook,
    modifier: Modifier = Modifier,
    state: ChartState = rememberChartState(),
    axes: ChartAxes = ChartAxes.Default,
    contentDescription: String = "Order book depth",
    onSelectionChanged: ((ChartSelection?) -> Unit)? = null,
) {
    val colors = LocalChartColors.current
    val bids = remember(orderBook) { orderBook.cumulativeBids() }
    val asks = remember(orderBook) { orderBook.cumulativeAsks() }
    val quantityRange = remember(bids, asks) {
        val maximum = maxOf(bids.yRange()?.max ?: 0.0, asks.yRange()?.max ?: 0.0)
        ChartRange(0.0, maximum.takeIf { it > 0.0 } ?: 1.0)
    }
    val layers = remember(bids, asks, colors) {
        listOf(
            ChartLayer.Line(bids, color = colors.positive, fill = colors.positive.copy(alpha = 0.18f), interpolation = LineInterpolation.StepBefore, name = "Bids"),
            ChartLayer.Line(asks, color = colors.negative, fill = colors.negative.copy(alpha = 0.18f), interpolation = LineInterpolation.Step, name = "Asks"),
        )
    }
    CartesianChart(
        layers = layers,
        modifier = modifier,
        state = state,
        axes = axes,
        yRange = quantityRange,
        contentDescription = contentDescription,
        onSelectionChanged = onSelectionChanged,
    )
}
