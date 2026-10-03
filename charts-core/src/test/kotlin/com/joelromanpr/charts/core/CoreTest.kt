package com.joelromanpr.charts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CoreTest {
    @Test fun snapshotsDefensivelyCopyAndRejectInvalidMarketData() {
        val input = mutableListOf(Point(1.0, 2.0))
        val snapshot = PointSeries(input)
        input.clear()
        assertEquals(1, snapshot.size)
        assertThrows(UnsupportedOperationException::class.java) { (snapshot.points as MutableList<Point>).clear() }
        assertThrows(IllegalArgumentException::class.java) { PointSeries(listOf(Point(1.0, 2.0), Point(1.0, 3.0))) }
        assertThrows(IllegalArgumentException::class.java) { Point(Double.NaN, 2.0) }
        assertThrows(IllegalArgumentException::class.java) { Candle(0.0, 3.0, 2.0, 1.0, 2.0) }
        assertThrows(IllegalArgumentException::class.java) { Candle(0.0, 2.0, 3.0, 1.0, 2.0, -1.0) }
    }

    @Test fun viewportIncludesTheSamplesNeededToConnectItsEdges() {
        val series = PointSeries((0..10).map { Point(it.toDouble(), it.toDouble()) })
        assertEquals(listOf(2.0, 3.0, 4.0, 5.0, 6.0), series.visiblePoints(2.5, 5.5).map { it.x })
        assertEquals(listOf(3.0, 4.0, 5.0), series.visiblePoints(2.5, 5.5, false).map { it.x })
        assertEquals(listOf(0.0), series.visiblePoints(-10.0, -5.0).map { it.x })
        assertEquals(listOf(10.0), series.visiblePoints(20.0, 30.0).map { it.x })
        assertTrue(series.visiblePoints(20.0, 30.0, false).isEmpty())
        assertEquals(Point(4.0, 4.0), series.nearestPoint(4.5))
    }

    @Test fun decimationPreservesEndpointsAndBothTradingSpikes() {
        val series = PointSeries((0..999).map { Point(it.toDouble(), when (it) { 102 -> 500.0; 103 -> -500.0; else -> 1.0 }) })
        val sampled = series.decimated(64)
        assertTrue(sampled.size <= 64)
        assertEquals(series.points.first(), sampled.points.first())
        assertEquals(series.points.last(), sampled.points.last())
        assertTrue(sampled.points.contains(series.points[102]))
        assertTrue(sampled.points.contains(series.points[103]))
        assertSame(series, series.decimated(1000))
        assertEquals(2, series.decimated(2).size)
        assertEquals(3, series.decimated(3).size)
    }

    @Test fun rangesAndNearestLookupHandleExtremeAndFlatInputs() {
        for (value in listOf(0.0, Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE)) {
            val expanded = ChartRange(value, value).expanded()
            assertTrue(expanded.min.isFinite() && expanded.max.isFinite() && expanded.min < expanded.max)
            assertTrue(value in expanded.min..expanded.max)
        }
        val range = ChartRange(-Double.MAX_VALUE, Double.MAX_VALUE)
        assertEquals(0.5, range.fractionOf(0.0), 0.0)
        assertEquals(0.0, range.valueAt(0.5), 0.0)
        val series = PointSeries(listOf(Point(-Double.MAX_VALUE, 0.0), Point(Double.MAX_VALUE, 1.0)))
        assertEquals(series.points.last(), series.nearestPoint(Double.MAX_VALUE * 0.5))
        val tiny = PointSeries(listOf(Point(0.0, 0.0), Point(Double.MIN_VALUE, 1.0)))
        assertEquals(tiny.points.last(), tiny.nearestPoint(Double.MIN_VALUE))
    }

    @Test fun orderBookProducesCumulativeBestFirstDepthInChartOrder() {
        val book = OrderBook(
            listOf(DepthLevel(100.0, 2.0), DepthLevel(99.0, 3.0)),
            listOf(DepthLevel(101.0, 4.0), DepthLevel(102.0, 1.0)),
        )
        assertEquals(listOf(Point(99.0, 5.0), Point(100.0, 2.0)), book.cumulativeBids().points)
        assertEquals(listOf(Point(101.0, 4.0), Point(102.0, 5.0)), book.cumulativeAsks().points)
        assertThrows(IllegalArgumentException::class.java) { OrderBook(listOf(DepthLevel(102.0, 1.0)), book.asks) }
        assertThrows(IllegalArgumentException::class.java) { OrderBook(book.bids.asReversed(), book.asks) }
        assertThrows(IllegalArgumentException::class.java) { OrderBook(book.bids, listOf(DepthLevel(101.0, 1.0), DepthLevel(101.0, 2.0))) }
    }
}
