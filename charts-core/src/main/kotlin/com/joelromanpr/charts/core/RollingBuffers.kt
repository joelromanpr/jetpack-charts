package com.joelromanpr.charts.core

/** A bounded, thread-safe stream window. Unchanged snapshots reuse the same instance. */
public class RollingPointBuffer(public val capacity: Int, public val name: String = "") {
    private val window = RollingWindow<Point, PointSeries>(capacity, { it.x }) { PointSeries(it, name) }
    public val revision: Long get() = window.revision
    public val size: Int get() = window.size
    public fun append(point: Point): Unit = window.append(point)
    /** Replaces an in-flight sample without changing its X coordinate. */
    public fun replaceLast(point: Point): Unit = window.replaceLast(point)
    public fun snapshot(): PointSeries = window.snapshot()
    public fun clear(): Unit = window.clear()
}

public class RollingCandleBuffer(public val capacity: Int, public val name: String = "") {
    private val window = RollingWindow<Candle, CandleSeries>(capacity, { it.x }) { CandleSeries(it, name) }
    public val revision: Long get() = window.revision
    public val size: Int get() = window.size
    public fun append(candle: Candle): Unit = window.append(candle)
    /** Replaces the forming candle; its X coordinate must match the latest candle. */
    public fun replaceLast(candle: Candle): Unit = window.replaceLast(candle)
    public fun snapshot(): CandleSeries = window.snapshot()
    public fun clear(): Unit = window.clear()
}

private class RollingWindow<T, S>(
    private val capacity: Int,
    private val x: (T) -> Double,
    private val createSnapshot: (List<T>) -> S,
) {
    private val values = ArrayDeque<T>()
    private var cached: S? = null
    private var currentRevision = 0L
    val revision: Long get() = synchronized(this) { currentRevision }
    val size: Int get() = synchronized(this) { values.size }

    init {
        require(capacity > 0) { "Capacity must be positive." }
    }

    @Synchronized
    fun append(value: T) {
        require(values.isEmpty() || x(value) > x(values.last())) { "Append X must be greater than the latest X." }
        if (values.size == capacity) values.removeFirst()
        values.addLast(value)
        invalidate()
    }

    @Synchronized
    fun replaceLast(value: T) {
        check(values.isNotEmpty()) { "Cannot replace a sample in an empty window." }
        require(x(value) == x(values.last())) { "Replacement X must match the latest X." }
        if (value == values.last()) return
        values.removeLast()
        values.addLast(value)
        invalidate()
    }

    @Synchronized
    fun snapshot(): S {
        cached?.let { return it }
        return createSnapshot(values.toList()).also { cached = it }
    }

    @Synchronized
    fun clear() {
        if (values.isEmpty()) return
        values.clear()
        invalidate()
    }

    private fun invalidate() {
        cached = null
        currentRevision++
    }
}
