package com.joelromanpr.charts.core

import java.util.Collections

public data class Point(public val x: Double, public val y: Double) {
    init {
        require(x.isFinite() && y.isFinite()) { "Point coordinates must be finite." }
    }
}

/** An immutable snapshot ordered by strictly increasing X coordinates. */
public class PointSeries(points: List<Point>, public val name: String = "") {
    public val points: List<Point> = immutableCopy(points)
    public val size: Int get() = points.size
    private val horizontalRange: ChartRange?
    private val verticalRange: ChartRange?

    init {
        requireIncreasing(this.points) { it.x }
        horizontalRange = this.points.firstOrNull()?.let { ChartRange(it.x, this.points.last().x) }
        verticalRange = this.points.firstOrNull()?.let {
            var minimum = it.y
            var maximum = it.y
            for (point in this.points) {
                minimum = minOf(minimum, point.y)
                maximum = maxOf(maximum, point.y)
            }
            ChartRange(minimum, maximum)
        }
    }

    public fun xRange(): ChartRange? = horizontalRange
    public fun yRange(): ChartRange? = verticalRange
    public override fun equals(other: Any?): Boolean = other is PointSeries && name == other.name && points == other.points
    public override fun hashCode(): Int = 31 * points.hashCode() + name.hashCode()
    public override fun toString(): String = "PointSeries(name=$name, size=$size)"
}

public data class Candle(
    public val x: Double,
    public val open: Double,
    public val high: Double,
    public val low: Double,
    public val close: Double,
    public val volume: Double = 0.0,
) {
    init {
        require(x.isFinite() && open.isFinite() && high.isFinite() && low.isFinite() && close.isFinite()) {
            "Candle coordinates and prices must be finite."
        }
        require(volume.isFinite() && volume >= 0.0) { "Volume must be finite and nonnegative." }
        require(low <= open && low <= close && high >= open && high >= close && low <= high) {
            "Candle high and low must contain the open and close."
        }
    }
}

/** An immutable candle snapshot ordered by strictly increasing X coordinates. */
public class CandleSeries(candles: List<Candle>, public val name: String = "") {
    public val candles: List<Candle> = immutableCopy(candles)
    public val size: Int get() = candles.size
    private val horizontalRange: ChartRange?
    private val prices: ChartRange?

    init {
        requireIncreasing(this.candles) { it.x }
        horizontalRange = this.candles.firstOrNull()?.let { ChartRange(it.x, this.candles.last().x) }
        prices = this.candles.firstOrNull()?.let {
            var minimum = it.low
            var maximum = it.high
            for (candle in this.candles) {
                minimum = minOf(minimum, candle.low)
                maximum = maxOf(maximum, candle.high)
            }
            ChartRange(minimum, maximum)
        }
    }

    public fun xRange(): ChartRange? = horizontalRange
    public fun priceRange(): ChartRange? = prices
    public override fun equals(other: Any?): Boolean = other is CandleSeries && name == other.name && candles == other.candles
    public override fun hashCode(): Int = 31 * candles.hashCode() + name.hashCode()
    public override fun toString(): String = "CandleSeries(name=$name, size=$size)"
}

public data class Slice(public val label: String, public val value: Double) {
    init {
        require(value.isFinite() && value >= 0.0) { "Slice values must be finite and nonnegative." }
    }
}

public data class DepthLevel(public val price: Double, public val quantity: Double) {
    init {
        require(price.isFinite() && price > 0.0) { "Depth prices must be finite and positive." }
        require(quantity.isFinite() && quantity >= 0.0) { "Depth quantities must be finite and nonnegative." }
    }
}

/** Levels are supplied best-first: descending bids and ascending asks. */
public class OrderBook(bids: List<DepthLevel>, asks: List<DepthLevel>) {
    public val bids: List<DepthLevel> = immutableCopy(bids)
    public val asks: List<DepthLevel> = immutableCopy(asks)

    init {
        requireIncreasing(this.bids) { -it.price }
        requireIncreasing(this.asks) { it.price }
        require(this.bids.isEmpty() || this.asks.isEmpty() || this.bids.first().price <= this.asks.first().price) {
            "The best bid cannot exceed the best ask."
        }
    }

    public fun cumulativeBids(): PointSeries = cumulative(bids, "Bids", reverse = true)
    public fun cumulativeAsks(): PointSeries = cumulative(asks, "Asks", reverse = false)

    public override fun equals(other: Any?): Boolean = other is OrderBook && bids == other.bids && asks == other.asks
    public override fun hashCode(): Int = 31 * bids.hashCode() + asks.hashCode()
    public override fun toString(): String = "OrderBook(bids=${bids.size}, asks=${asks.size})"

    private fun cumulative(levels: List<DepthLevel>, name: String, reverse: Boolean): PointSeries {
        var quantity = 0.0
        val points = levels.map {
            quantity += it.quantity
            require(quantity.isFinite()) { "Cumulative depth exceeds the supported numeric range." }
            Point(it.price, quantity)
        }
        return PointSeries(if (reverse) points.asReversed() else points, name)
    }
}

internal fun <T> immutableCopy(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

internal inline fun <T> requireIncreasing(values: List<T>, x: (T) -> Double) {
    for (index in 1 until values.size) {
        require(x(values[index - 1]) < x(values[index])) { "X coordinates must be strictly increasing." }
    }
}
