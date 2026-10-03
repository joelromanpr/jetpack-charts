package com.joelromanpr.charts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.sqrt

class IndicatorsTest {
    @Test fun averagesUseFullWindowsAndAnSmaSeed() {
        val input = candles(1.0, 2.0, 3.0, 8.0, 1.0)
        assertValues(listOf(2.0, 13.0 / 3.0, 4.0), Indicators.sma(input, 3))
        assertValues(listOf(2.0, 5.0, 3.0), Indicators.ema(input, 3))
        assertEquals(listOf(2.0, 3.0, 4.0), Indicators.ema(input, 3).points.map { it.x })
        assertTrue(Indicators.sma(input, 6).points.isEmpty())
        val maximum = candles(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
        assertEquals(Double.MAX_VALUE, Indicators.sma(maximum, 3).points.single().y, 0.0)
        assertEquals(Double.MAX_VALUE, Indicators.ema(maximum, 3).points.single().y, 0.0)
        assertThrows(IllegalArgumentException::class.java) {
            Indicators.sma(candles(Double.MIN_VALUE, Double.MIN_VALUE, Double.MAX_VALUE), 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Indicators.bollingerBands(candles(-Double.MAX_VALUE, Double.MAX_VALUE), 2)
        }
    }

    @Test fun bandsUsePopulationDeviationAndTrackWindowRemoval() {
        val bands = Indicators.bollingerBands(candles(1.0, 2.0, 3.0, 8.0), 3)
        assertValues(listOf(2.0, 13.0 / 3.0), bands.middle)
        assertValues(listOf(2.0 + 2.0 * sqrt(2.0 / 3.0), (13.0 + 2.0 * sqrt(62.0)) / 3.0), bands.upper)
        assertValues(listOf(2.0 - 2.0 * sqrt(2.0 / 3.0), (13.0 - 2.0 * sqrt(62.0)) / 3.0), bands.lower)
        assertValues(listOf(1.0, 2.0), Indicators.bollingerBands(candles(1.0, 2.0), 1).upper)
    }

    @Test fun rsiDefinesFlatAndOneSidedWindowsWithoutDivisionErrors() {
        assertValues(listOf(100.0, 100.0), Indicators.rsi(candles(1.0, 2.0, 3.0, 4.0), 2))
        assertValues(listOf(0.0, 0.0), Indicators.rsi(candles(4.0, 3.0, 2.0, 1.0), 2))
        assertValues(listOf(50.0, 50.0), Indicators.rsi(candles(3.0, 3.0, 3.0, 3.0), 2))
        assertValues(listOf(0.0, 100.0), Indicators.rsi(candles(Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE), 1))
    }

    @Test fun macdAlignsTheSignalAfterItsOwnWarmup() {
        val result = Indicators.macd(candles(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), 2, 3, 2)
        assertValues(listOf(0.5, 0.5, 0.5, 0.5), result.macd)
        assertValues(listOf(0.5, 0.5, 0.5), result.signal)
        assertValues(listOf(0.0, 0.0, 0.0), result.histogram)
        assertEquals(listOf(3.0, 4.0, 5.0), result.signal.points.map { it.x })
        assertTrue(Indicators.macd(candles(1.0), 2, 3, 2).macd.points.isEmpty())
    }

    @Test fun vwapUsesTypicalPriceAndSkipsOnlyZeroVolumePrefixes() {
        val input = CandleSeries(listOf(
            Candle(0.0, 3.0, 6.0, 0.0, 3.0, 0.0),
            Candle(1.0, 3.0, 6.0, 0.0, 3.0, 2.0),
            Candle(2.0, 6.0, 9.0, 3.0, 6.0, 1.0),
            Candle(3.0, 9.0, 12.0, 6.0, 9.0, 0.0),
        ))
        assertValues(listOf(3.0, 4.0, 4.0), Indicators.vwap(input))
        val extremes = CandleSeries(listOf(
            Candle(0.0, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE),
            Candle(1.0, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE),
        ))
        assertEquals(Double.MIN_VALUE, Indicators.vwap(extremes).points.first().y, 0.0)
        assertEquals(Double.MAX_VALUE, Indicators.vwap(extremes).points.last().y, 0.0)
    }

    private fun candles(vararg values: Double): CandleSeries = CandleSeries(values.mapIndexed { index, value ->
        Candle(index.toDouble(), value, value, value, value)
    })

    private fun assertValues(expected: List<Double>, actual: PointSeries) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual.points).forEach { (value, point) -> assertEquals(value, point.y, 1e-10) }
    }
}
