package com.joelromanpr.charts.core

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.system.measureNanoTime

/** A repeatable host-JVM workload, not an Android frame-time benchmark. */
internal object CoreBenchmark {
    @Volatile private var consumed: Any? = null

    @JvmStatic
    fun main(args: Array<String>) {
        val count = 100_000
        val series = PointSeries(List(count) { index ->
            Point(index.toDouble(), 100.0 + sin(index * 0.013) * 8.0 + if (index % 997 == 0) 20.0 else 0.0)
        })
        println("Host JVM ${System.getProperty("java.version")}; $count immutable samples; 10 warmups and 30 measured runs.")
        report("Decimate 100000 to at most 2000 points") { series.decimated(2_000) }
        report("1000 viewport slices plus nearest lookups") {
            var checksum = 0.0
            repeat(1_000) { index ->
                val start = 1_000.0 + (index * 73 % 90_000)
                checksum += series.visiblePoints(start, start + 120.0).size
                checksum += series.nearestPoint(start + 60.25)!!.y
            }
            checksum
        }
    }

    private fun report(label: String, operation: () -> Any) {
        repeat(10) { consumed = operation() }
        val timings = LongArray(30) { measureNanoTime { consumed = operation() } }.sorted()
        val p50 = timings[ceil(timings.size * 0.50).toInt() - 1] / 1_000_000.0
        val p95 = timings[ceil(timings.size * 0.95).toInt() - 1] / 1_000_000.0
        println(String.format(Locale.ROOT, "%s: p50 %.3f ms, p95 %.3f ms", label, p50, p95))
    }
}
