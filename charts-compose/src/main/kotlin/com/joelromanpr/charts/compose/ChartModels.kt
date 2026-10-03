package com.joelromanpr.charts.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joelromanpr.charts.core.Candle
import com.joelromanpr.charts.core.CandleSeries
import com.joelromanpr.charts.core.ChartRange
import com.joelromanpr.charts.core.Point
import com.joelromanpr.charts.core.PointSeries
import java.text.DecimalFormat

public enum class LineInterpolation { Linear, Step, StepBefore, Cubic }
public enum class AxisSide { Left, Right }

public sealed interface ChartAnimation {
    public data object None : ChartAnimation
    public data class Reveal(public val durationMillis: Int = 300) : ChartAnimation {
        init { require(durationMillis >= 0) { "Animation duration cannot be negative." } }
    }
}

public sealed interface ChartLayer {
    public val name: String
    public val axis: AxisSide

    public data class Line(
        public val series: PointSeries,
        public val color: Color? = null,
        public val fill: Color? = null,
        public val strokeWidth: Dp = 2.dp,
        public val interpolation: LineInterpolation = LineInterpolation.Linear,
        override val name: String = series.name,
        override val axis: AxisSide = AxisSide.Left,
    ) : ChartLayer {
        init { require(strokeWidth > 0.dp) { "Line width must be positive." } }
    }

    /** Layers with the same non-null stack key stack; other column layers are grouped. */
    public data class Columns(
        public val series: PointSeries,
        public val color: Color? = null,
        public val stackKey: String? = null,
        public val widthFraction: Float = 0.75f,
        override val name: String = series.name,
        override val axis: AxisSide = AxisSide.Left,
    ) : ChartLayer {
        init { require(widthFraction in 0f..1f) { "Column width must be between zero and one." } }
    }

    public data class Candles(
        public val series: CandleSeries,
        public val risingColor: Color? = null,
        public val fallingColor: Color? = null,
        public val widthFraction: Float = 0.7f,
        override val name: String = series.name.ifBlank { "Price" },
        override val axis: AxisSide = AxisSide.Left,
    ) : ChartLayer {
        init { require(widthFraction in 0f..1f) { "Candle width must be between zero and one." } }
    }

    public data class Scatter(
        public val series: PointSeries,
        public val color: Color? = null,
        public val radius: Dp = 3.dp,
        override val name: String = series.name,
        override val axis: AxisSide = AxisSide.Left,
    ) : ChartLayer {
        init { require(radius > 0.dp) { "Scatter radius must be positive." } }
    }
}

public fun interface AxisFormatter {
    public fun format(value: Double): String

    public companion object {
        /** Create once and reuse; decimal formatters are confined to the UI thread. */
        public fun decimal(pattern: String = "#,##0.##"): AxisFormatter {
            val formatter = DecimalFormat(pattern)
            return AxisFormatter { formatter.format(it) }
        }
    }
}

public data class ChartAxes(
    public val showX: Boolean = true,
    public val showY: Boolean = true,
    public val showGrid: Boolean = true,
    public val tickCount: Int = 5,
    public val xFormatter: AxisFormatter = AxisFormatter.decimal(),
    public val yFormatter: AxisFormatter = AxisFormatter.decimal(),
    public val labelStyle: TextStyle = TextStyle(fontSize = 11.sp),
    public val yAxisWidth: Dp = 58.dp,
    public val xAxisHeight: Dp = 26.dp,
    public val showRightY: Boolean = true,
    public val rightYFormatter: AxisFormatter = yFormatter,
    public val rightYAxisWidth: Dp = 58.dp,
) {
    init {
        require(tickCount in 2..12) { "Tick count must be between 2 and 12." }
        require(yAxisWidth >= 0.dp && xAxisHeight >= 0.dp && rightYAxisWidth >= 0.dp) { "Axis sizes cannot be negative." }
    }

    public companion object {
        public val Default: ChartAxes = ChartAxes()
        public val Hidden: ChartAxes = ChartAxes(showX = false, showY = false, showGrid = false, showRightY = false)
    }
}

@Immutable
public data class ReferenceLine(
    public val y: Double,
    public val label: String = "",
    public val color: Color? = null,
    public val dashed: Boolean = true,
    public val axis: AxisSide = AxisSide.Left,
) {
    init { require(y.isFinite()) { "Reference line must be finite." } }
}

public data class ChartSelectedValue(
    public val layerIndex: Int,
    public val name: String,
    public val point: Point? = null,
    public val candle: Candle? = null,
    public val axis: AxisSide = AxisSide.Left,
)

public data class ChartSelection(public val x: Double, public val values: List<ChartSelectedValue>)

/** Coordinates passed to a decoration drawn after the chart layers and before selection. */
public interface ChartCoordinates {
    public val xRange: ChartRange
    public val yRange: ChartRange
    public val rightYRange: ChartRange?
    public fun x(value: Double): Float
    public fun y(value: Double, axis: AxisSide = AxisSide.Left): Float
}

public typealias ChartDecoration = DrawScope.(ChartCoordinates) -> Unit
