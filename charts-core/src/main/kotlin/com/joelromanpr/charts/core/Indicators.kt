package com.joelromanpr.charts.core

import kotlin.math.abs
import kotlin.math.sqrt

public data class BollingerBands(public val middle: PointSeries, public val upper: PointSeries, public val lower: PointSeries)
public data class MacdResult(public val macd: PointSeries, public val signal: PointSeries, public val histogram: PointSeries)

/** Indicators omit warm-up samples and reject numeric ranges that cannot retain finite precision. */
public object Indicators {
    public fun sma(series: CandleSeries, period: Int = 20): PointSeries {
        requirePeriod(period)
        val prices = scaledCloses(series)
        val result = ArrayList<Point>()
        val sum = CompensatedSum()
        for (index in prices.values.indices) {
            sum.add(prices.values[index])
            if (index >= period) sum.add(-prices.values[index - period])
            if (index >= period - 1) result.add(Point(series.candles[index].x, scaled(sum.value / period, prices.scale)))
        }
        return PointSeries(result, "SMA ($period)")
    }

    /** Seeds the exponential average with a full-period simple average. */
    public fun ema(series: CandleSeries, period: Int = 20): PointSeries {
        requirePeriod(period)
        val prices = scaledCloses(series)
        val averages = emaValues(prices.values, period)
        return PointSeries(series.candles.indices.mapNotNull {
            if (averages[it].isNaN()) null else Point(series.candles[it].x, scaled(averages[it], prices.scale))
        }, "EMA ($period)")
    }

    /** Uses Wilder smoothing; unchanged prices yield a neutral RSI of 50. */
    public fun rsi(series: CandleSeries, period: Int = 14): PointSeries {
        requirePeriod(period)
        if (series.size <= period) return PointSeries(emptyList(), "RSI ($period)")
        val prices = scaledCloses(series).values
        var gain = 0.0
        var loss = 0.0
        for (index in 1..period) {
            val change = prices[index] - prices[index - 1]
            gain += maxOf(change, 0.0) / period
            loss += maxOf(-change, 0.0) / period
        }
        val result = ArrayList<Point>(series.size - period)
        result.add(Point(series.candles[period].x, relativeStrength(gain, loss)))
        for (index in period + 1 until series.size) {
            val change = prices[index] - prices[index - 1]
            gain = gain * ((period - 1.0) / period) + maxOf(change, 0.0) / period
            loss = loss * ((period - 1.0) / period) + maxOf(-change, 0.0) / period
            result.add(Point(series.candles[index].x, relativeStrength(gain, loss)))
        }
        return PointSeries(result, "RSI ($period)")
    }

    /** Population standard deviation is computed over each complete window. */
    public fun bollingerBands(series: CandleSeries, period: Int = 20, standardDeviations: Double = 2.0): BollingerBands {
        requirePeriod(period)
        require(standardDeviations.isFinite() && standardDeviations >= 0.0) { "Standard deviations must be finite and nonnegative." }
        val prices = scaledCloses(series)
        val middle = ArrayList<Point>()
        val upper = ArrayList<Point>()
        val lower = ArrayList<Point>()
        var count = 0
        var mean = 0.0
        var squaredDeviations = 0.0
        for (index in prices.values.indices) {
            if (count == period) {
                val removed = prices.values[index - period]
                if (period == 1) {
                    count = 0
                    mean = 0.0
                    squaredDeviations = 0.0
                } else {
                    val adjusted = mean + (mean - removed) / (period - 1)
                    squaredDeviations = maxOf(0.0, squaredDeviations - (removed - mean) * (removed - adjusted))
                    mean = adjusted
                    count--
                }
            }
            count++
            val value = prices.values[index]
            val delta = value - mean
            mean += delta / count
            squaredDeviations += delta * (value - mean)
            if (count == period) {
                val deviation = sqrt(maxOf(0.0, squaredDeviations) / period) * standardDeviations
                val x = series.candles[index].x
                middle.add(Point(x, scaled(mean, prices.scale)))
                upper.add(Point(x, scaled(mean + deviation, prices.scale)))
                lower.add(Point(x, scaled(mean - deviation, prices.scale)))
            }
        }
        return BollingerBands(PointSeries(middle, "Middle"), PointSeries(upper, "Upper"), PointSeries(lower, "Lower"))
    }

    public fun macd(series: CandleSeries, fastPeriod: Int = 12, slowPeriod: Int = 26, signalPeriod: Int = 9): MacdResult {
        requirePeriod(fastPeriod)
        requirePeriod(slowPeriod)
        requirePeriod(signalPeriod)
        require(fastPeriod < slowPeriod) { "Fast period must be smaller than slow period." }
        val prices = scaledCloses(series)
        val fast = emaValues(prices.values, fastPeriod)
        val slow = emaValues(prices.values, slowPeriod)
        val start = minOf(series.size, slowPeriod - 1)
        val differences = DoubleArray(series.size - start) { fast[start + it] - slow[start + it] }
        val signals = emaValues(differences, signalPeriod)
        val macd = ArrayList<Point>()
        val signal = ArrayList<Point>()
        val histogram = ArrayList<Point>()
        for (index in differences.indices) {
            val x = series.candles[start + index].x
            macd.add(Point(x, scaled(differences[index], prices.scale)))
            if (!signals[index].isNaN()) {
                signal.add(Point(x, scaled(signals[index], prices.scale)))
                histogram.add(Point(x, scaled(differences[index] - signals[index], prices.scale)))
            }
        }
        return MacdResult(PointSeries(macd, "MACD"), PointSeries(signal, "Signal"), PointSeries(histogram, "Histogram"))
    }

    /** Cumulative typical-price VWAP. Start a new series to reset the trading session. */
    public fun vwap(series: CandleSeries): PointSeries {
        var priceScale = 0.0
        var volumeScale = 0.0
        val quantity = CompensatedSum()
        val weighted = CompensatedSum()
        val result = ArrayList<Point>()
        for (candle in series.candles) {
            if (candle.volume > 0.0) {
                if (candle.volume > volumeScale) {
                    val factor = volumeScale / candle.volume
                    quantity.rescale(factor)
                    weighted.rescale(factor)
                    volumeScale = candle.volume
                }
                val candleScale = maxOf(abs(candle.high), abs(candle.low), abs(candle.close))
                val typicalPrice = if (candleScale == 0.0) 0.0 else
                    ((candle.high / candleScale + candle.low / candleScale + candle.close / candleScale) / 3.0).coerceIn(-1.0, 1.0) * candleScale
                if (abs(typicalPrice) > priceScale) {
                    weighted.rescale(priceScale / abs(typicalPrice))
                    priceScale = abs(typicalPrice)
                }
                val volume = candle.volume / volumeScale
                quantity.add(volume)
                weighted.add(if (priceScale == 0.0) 0.0 else (typicalPrice / priceScale) * volume)
            }
            if (quantity.value > 0.0) result.add(Point(candle.x, scaled((weighted.value / quantity.value).coerceIn(-1.0, 1.0), priceScale)))
        }
        return PointSeries(result, "VWAP")
    }

    private fun relativeStrength(gain: Double, loss: Double): Double =
        if (gain == 0.0 && loss == 0.0) 50.0 else (100.0 * gain / (gain + loss)).coerceIn(0.0, 100.0)

    private fun requirePeriod(period: Int) {
        require(period > 0) { "Period must be positive." }
    }

    private fun emaValues(values: DoubleArray, period: Int): DoubleArray {
        val result = DoubleArray(values.size) { Double.NaN }
        if (values.size < period) return result
        val sum = CompensatedSum()
        for (index in 0 until period) sum.add(values[index])
        var average = sum.value / period
        result[period - 1] = average
        val alpha = 2.0 / (period + 1.0)
        for (index in period until values.size) {
            average = alpha * values[index] + (1.0 - alpha) * average
            result[index] = average
        }
        return result
    }

    private fun scaledCloses(series: CandleSeries): ScaledPrices {
        val scale = series.candles.maxOfOrNull { abs(it.close) } ?: 0.0
        return ScaledPrices(DoubleArray(series.size) {
            val price = series.candles[it].close
            val normalized = if (scale == 0.0) 0.0 else price / scale
            require(price == 0.0 || normalized != 0.0) { "Price range is too wide to preserve indicator precision." }
            normalized
        }, scale)
    }

    private fun scaled(value: Double, scale: Double): Double {
        val result = value * scale
        require(result.isFinite()) { "Indicator exceeds the supported numeric range." }
        return result
    }

    private data class ScaledPrices(public val values: DoubleArray, public val scale: Double)
}

private class CompensatedSum {
    var value: Double = 0.0
        private set
    private var compensation = 0.0

    public fun add(value: Double) {
        val adjusted = value - compensation
        val next = this.value + adjusted
        compensation = (next - this.value) - adjusted
        this.value = next
    }

    public fun rescale(factor: Double) {
        value *= factor
        compensation *= factor
    }
}
