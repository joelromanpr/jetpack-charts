package com.joelromanpr.charts.core

import kotlin.math.abs

public data class ChartRange(public val min: Double, public val max: Double) {
    init {
        require(min.isFinite() && max.isFinite() && min <= max) { "Range bounds must be finite and ordered." }
    }

    /** Gives a flat range a finite drawable span, including at the limits of Double. */
    public fun expanded(minimumSpan: Double = 1e-9): ChartRange {
        require(minimumSpan.isFinite() && minimumSpan > 0.0) { "Minimum span must be finite and positive." }
        if (min != max) return this
        val padding = maxOf(abs(min) * 0.01, minimumSpan * 0.5)
        val lower = (min - padding).coerceAtLeast(-Double.MAX_VALUE)
        val upper = (max + padding).coerceAtMost(Double.MAX_VALUE)
        return ChartRange(
            if (lower < min) lower else Math.nextAfter(min, Double.NEGATIVE_INFINITY).coerceAtLeast(-Double.MAX_VALUE),
            if (upper > max) upper else Math.nextAfter(max, Double.POSITIVE_INFINITY).coerceAtMost(Double.MAX_VALUE),
        )
    }

    /** Maps into [0, 1] without overflowing when the range straddles extreme values. */
    public fun fractionOf(value: Double): Double {
        require(value.isFinite()) { "Value must be finite." }
        if (min == max) return 0.5
        if (value <= min) return 0.0
        if (value >= max) return 1.0
        val span = max - min
        return if (span.isFinite()) (value - min) / span else (value * 0.5 - min * 0.5) / (max * 0.5 - min * 0.5)
    }

    public fun valueAt(fraction: Double): Double {
        require(fraction.isFinite() && fraction in 0.0..1.0) { "Fraction must be between zero and one." }
        if (fraction == 0.0) return min
        if (fraction == 1.0) return max
        return (min * (1.0 - fraction) + max * fraction).coerceIn(min, max)
    }

    public companion object {
        public fun rangeOf(values: Iterable<Double>): ChartRange? {
            val iterator = values.iterator()
            if (!iterator.hasNext()) return null
            var minimum = iterator.next()
            require(minimum.isFinite()) { "Range values must be finite." }
            var maximum = minimum
            while (iterator.hasNext()) {
                val value = iterator.next()
                require(value.isFinite()) { "Range values must be finite." }
                minimum = minOf(minimum, value)
                maximum = maxOf(maximum, value)
            }
            return ChartRange(minimum, maximum)
        }
    }
}
