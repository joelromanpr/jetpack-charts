package com.joelromanpr.charts.core

import kotlin.math.abs

public fun PointSeries.nearestPoint(x: Double): Point? = nearest(points, x) { it.x }

public fun CandleSeries.nearestCandle(x: Double): Candle? = nearest(candles, x) { it.x }

/** Includes adjacent samples by default so lines remain continuous at viewport edges. */
public fun PointSeries.visiblePoints(minX: Double, maxX: Double, includeNeighbors: Boolean = true): List<Point> =
    visible(points, minX, maxX, includeNeighbors) { it.x }

public fun CandleSeries.visibleCandles(minX: Double, maxX: Double, includeNeighbors: Boolean = true): List<Candle> =
    visible(candles, minX, maxX, includeNeighbors) { it.x }

/** Retains endpoints and each bucket's minimum/maximum in chronological order. */
public fun PointSeries.decimated(maxPoints: Int): PointSeries {
    require(maxPoints >= 2) { "Decimation needs room for at least two endpoints." }
    if (size <= maxPoints) return this
    if (maxPoints == 2) return PointSeries(listOf(points.first(), points.last()), name)
    if (maxPoints == 3) {
        val range = yRange()!!.expanded()
        val xRange = xRange()!!
        val firstY = range.fractionOf(points.first().y)
        val lastY = range.fractionOf(points.last().y)
        val farthest = (1 until size - 1).maxByOrNull {
            val expected = firstY * (1.0 - xRange.fractionOf(points[it].x)) + lastY * xRange.fractionOf(points[it].x)
            abs(range.fractionOf(points[it].y) - expected)
        }!!
        return PointSeries(listOf(points.first(), points[farthest], points.last()), name)
    }
    val bucketCount = (maxPoints - 2) / 2
    val interior = size - 2
    val result = ArrayList<Point>(maxPoints)
    result.add(points.first())
    for (bucket in 0 until bucketCount) {
        val start = 1 + (bucket.toLong() * interior / bucketCount).toInt()
        val end = 1 + ((bucket + 1L) * interior / bucketCount).toInt()
        var minimum = start
        var maximum = start
        for (index in start + 1 until end) {
            if (points[index].y < points[minimum].y) minimum = index
            if (points[index].y > points[maximum].y) maximum = index
        }
        if (minimum == maximum) result.add(points[minimum])
        else {
            result.add(points[minOf(minimum, maximum)])
            result.add(points[maxOf(minimum, maximum)])
        }
    }
    result.add(points.last())
    return PointSeries(result, name)
}

private inline fun <T> nearest(values: List<T>, target: Double, x: (T) -> Double): T? {
    require(target.isFinite()) { "Target X must be finite." }
    if (values.isEmpty()) return null
    val right = lowerBound(values, target, x)
    if (right == 0) return values.first()
    if (right == values.size) return values.last()
    // Scale only when subtraction overflows; keep subnormal distances precise otherwise.
    val left = target - x(values[right - 1])
    val next = x(values[right]) - target
    val leftDistance = if (left.isFinite() && next.isFinite()) left else target * 0.5 - x(values[right - 1]) * 0.5
    val rightDistance = if (left.isFinite() && next.isFinite()) next else x(values[right]) * 0.5 - target * 0.5
    return if (leftDistance <= rightDistance) values[right - 1] else values[right]
}

private inline fun <T> visible(values: List<T>, minX: Double, maxX: Double, neighbors: Boolean, x: (T) -> Double): List<T> {
    require(minX.isFinite() && maxX.isFinite() && minX <= maxX) { "Visible bounds must be finite and ordered." }
    if (values.isEmpty()) return emptyList()
    var start = lowerBound(values, minX, x)
    var end = lowerBound(values, maxX, x)
    if (end < values.size && x(values[end]) == maxX) end++
    if (neighbors) {
        start = maxOf(0, start - 1)
        end = minOf(values.size, end + 1)
    }
    return values.subList(start, end)
}

private inline fun <T> lowerBound(values: List<T>, target: Double, x: (T) -> Double): Int {
    var low = 0
    var high = values.size
    while (low < high) {
        val middle = low + (high - low) / 2
        if (x(values[middle]) < target) low = middle + 1 else high = middle
    }
    return low
}
