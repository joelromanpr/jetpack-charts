package com.joelromanpr.charts.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.joelromanpr.charts.core.ChartRange
import kotlin.math.abs

/** Hoist one state across price, volume, and indicator charts to synchronize their viewport. */
@Stable
public class ChartState public constructor(initialXRange: ChartRange? = null) {
    public var visibleXRange: ChartRange? by mutableStateOf(initialXRange)
        private set

    public var selectedX: Double? by mutableStateOf(null)
        private set

    public var isFollowingLatest: Boolean by mutableStateOf(initialXRange == null)
        private set

    private var previousDomainMax: Double? = null

    public fun setViewport(range: ChartRange?) {
        visibleXRange = range
        isFollowingLatest = range == null
    }

    /** Preserve the current visible span as newly appended data extends the domain. */
    public fun followLatest() {
        isFollowingLatest = true
        previousDomainMax = null
    }

    public fun select(x: Double?) {
        require(x == null || x.isFinite()) { "Selection must be finite." }
        selectedX = x
    }

    public fun reset() {
        visibleXRange = null
        selectedX = null
        isFollowingLatest = true
    }

    internal fun viewport(domain: ChartRange): ChartRange {
        val requested = visibleXRange ?: return domain
        val span = requested.max - requested.min
        val total = domain.max - domain.min
        if (!span.isFinite() || span >= total || span <= 0.0) return domain
        val start = requested.min.coerceIn(domain.min, domain.max - span)
        return ChartRange(start, start + span)
    }

    internal fun updateDomain(domain: ChartRange) {
        val previous = previousDomainMax
        previousDomainMax = domain.max
        val requested = visibleXRange ?: return
        if (!isFollowingLatest) return
        val span = requested.max - requested.min
        if (span <= 0.0 || !span.isFinite()) return
        if (previous == null || domain.max > previous) {
            val total = domain.max - domain.min
            visibleXRange = if (span >= total) null else ChartRange(maxOf(domain.min, domain.max - span), domain.max)
        }
    }

    internal fun transform(domain: ChartRange, zoom: Float, panFraction: Float, anchorFraction: Float) {
        if (!zoom.isFinite() || zoom <= 0f || !panFraction.isFinite()) return
        val current = viewport(domain)
        val total = domain.max - domain.min
        if (!total.isFinite() || total <= 0.0) return
        val oldSpan = current.max - current.min
        // Avoid zooming below representable precision for timestamp domains.
        val minimumSpan = maxOf(total / 1_000.0, Math.ulp(maxOf(abs(domain.min), abs(domain.max))) * 16)
        val newSpan = (oldSpan / zoom).coerceIn(minimumSpan.coerceAtMost(total), total)
        val anchor = anchorFraction.coerceIn(0f, 1f).toDouble()
        val start = (current.min + oldSpan * anchor - newSpan * anchor - panFraction * oldSpan)
            .coerceIn(domain.min, domain.max - newSpan)
        visibleXRange = if (newSpan >= total) null else ChartRange(start, start + newSpan)
        if (panFraction != 0f) isFollowingLatest = false
    }

    public companion object {
        public val Saver: Saver<ChartState, List<Double>> = Saver(
            save = { listOf(it.visibleXRange?.min ?: Double.NaN, it.visibleXRange?.max ?: Double.NaN, it.selectedX ?: Double.NaN, if (it.isFollowingLatest) 1.0 else 0.0) },
            restore = { saved ->
                ChartState(if (saved[0].isNaN()) null else ChartRange(saved[0], saved[1])).apply {
                    select(saved[2].takeUnless(Double::isNaN))
                    if (saved.getOrNull(3) == 1.0) followLatest()
                }
            },
        )
    }
}

@Composable
public fun rememberChartState(initialXRange: ChartRange? = null): ChartState =
    rememberSaveable(saver = ChartState.Saver) { ChartState(initialXRange) }
