package com.joelromanpr.charts.compose

import com.joelromanpr.charts.core.Point
import com.joelromanpr.charts.core.PointSeries
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionTest {
    @Test fun indicatorValuesAreAbsentBeforeTheirWarmup() {
        val price = PointSeries(List(10) { Point(it.toDouble(), it + 10.0) }, "Price")
        val indicator = PointSeries(List(6) { Point(it + 4.0, it + 20.0) }, "SMA")
        val layers = listOf(ChartLayer.Line(price), ChartLayer.Line(indicator))
        assertEquals(listOf("Price"), selectionAt(0.0, layers)!!.values.map { it.name })
        assertEquals(listOf("Price", "SMA"), selectionAt(5.0, layers)!!.values.map { it.name })
    }

    @Test fun denseHistorySelectionKeepsTheOriginalSpikeValue() {
        val data = PointSeries(List(100_000) { Point(it.toDouble(), if (it == 54_321) 917.25 else 1.0) })
        val selection = selectionAt(54_321.1, listOf(ChartLayer.Line(data)))!!
        assertEquals(54_321.0, selection.x, 0.0)
        assertEquals(917.25, selection.values.single().point!!.y, 0.0)
    }
}
