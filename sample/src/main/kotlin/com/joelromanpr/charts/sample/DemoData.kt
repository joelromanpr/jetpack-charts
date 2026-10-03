package com.joelromanpr.charts.sample

import com.joelromanpr.charts.core.Candle
import com.joelromanpr.charts.core.CandleSeries
import com.joelromanpr.charts.core.DepthLevel
import com.joelromanpr.charts.core.OrderBook
import com.joelromanpr.charts.core.Point
import com.joelromanpr.charts.core.PointSeries
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

internal object DemoData {
    val candles: CandleSeries by lazy {
        val random = Random(41)
        var previous = 184.0
        CandleSeries(List(180) { index ->
            val open = previous
            val close = open + random.nextDouble(-1.3, 1.6)
            previous = close
            Candle(
                x = 1_790_000_000.0 + index * 60.0,
                open = open,
                high = max(open, close) + random.nextDouble(0.1, 1.1),
                low = min(open, close) - random.nextDouble(0.1, 1.1),
                close = close,
                volume = random.nextDouble(1500.0, 9000.0),
            )
        }, "Meridian")
    }
    val orderBook: OrderBook by lazy {
        OrderBook(
            bids = List(32) { DepthLevel(199.9 - it * 0.2, 12.0 + (it * 17 % 23)) },
            asks = List(32) { DepthLevel(200.1 + it * 0.2, 9.0 + (it * 13 % 29)) },
        )
    }
    fun close(series: CandleSeries): PointSeries = PointSeries(series.candles.map { Point(it.x, it.close) }, "Price")
    fun volume(series: CandleSeries): PointSeries = PointSeries(series.candles.map { Point(it.x, it.volume) }, "Volume")
    val revenue = PointSeries(List(12) { Point(it.toDouble(), 32.0 + it * 4 + (it * 13 % 17)) }, "Online")
    val retail = PointSeries(List(12) { Point(it.toDouble(), 20.0 + it * 2 + (it * 7 % 13)) }, "Retail")
}
