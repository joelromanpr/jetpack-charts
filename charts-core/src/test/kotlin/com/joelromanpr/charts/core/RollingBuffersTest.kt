package com.joelromanpr.charts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RollingBuffersTest {
    @Test fun streamWindowsEvictOldSamplesAndKeepPublishedSnapshotsStable() {
        val buffer = RollingPointBuffer(2)
        buffer.append(Point(0.0, 1.0))
        buffer.append(Point(1.0, 2.0))
        val old = buffer.snapshot()
        assertSame(old, buffer.snapshot())
        buffer.replaceLast(Point(1.0, 3.0))
        val replacement = buffer.snapshot()
        assertNotSame(old, replacement)
        assertEquals(2.0, old.points.last().y, 0.0)
        assertEquals(3.0, replacement.points.last().y, 0.0)
        buffer.append(Point(2.0, 4.0))
        assertEquals(listOf(1.0, 2.0), buffer.snapshot().points.map { it.x })
        assertEquals(4L, buffer.revision)
        assertThrows(IllegalArgumentException::class.java) { buffer.append(Point(2.0, 5.0)) }
        assertThrows(IllegalArgumentException::class.java) { buffer.replaceLast(Point(3.0, 5.0)) }
        buffer.clear()
        assertTrue(buffer.snapshot().points.isEmpty())
        assertEquals(2, old.size)
    }

    @Test fun aFormingCandleCanBeUpdatedWithoutAppendingADuplicateTimestamp() {
        val buffer = RollingCandleBuffer(1)
        buffer.append(Candle(0.0, 2.0, 3.0, 1.0, 2.0))
        val before = buffer.snapshot()
        buffer.replaceLast(Candle(0.0, 2.0, 4.0, 1.0, 4.0, 2.0))
        assertEquals(4.0, buffer.snapshot().candles.single().close, 0.0)
        assertEquals(2.0, before.candles.single().close, 0.0)
        buffer.replaceLast(buffer.snapshot().candles.single())
        assertEquals(2L, buffer.revision)
    }

    @Test fun readersSeeCompleteOrderedSnapshotsDuringWrites() {
        val buffer = RollingPointBuffer(64)
        val executor = Executors.newFixedThreadPool(2)
        val writer = executor.submit { repeat(2000) { buffer.append(Point(it.toDouble(), it.toDouble())) } }
        val reader = executor.submit {
            repeat(2000) {
                val points = buffer.snapshot().points
                assertTrue(points.size <= 64)
                points.zipWithNext().forEach { (first, second) -> assertTrue(first.x < second.x) }
            }
        }
        try {
            writer.get(10, TimeUnit.SECONDS)
            reader.get(10, TimeUnit.SECONDS)
            assertEquals(64, buffer.size)
            assertEquals(1999.0, buffer.snapshot().points.last().x, 0.0)
        } finally {
            executor.shutdownNow()
        }
    }
}
