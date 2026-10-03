package com.joelromanpr.charts.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import java.util.Collections
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Immutable
public class RadarSeries(
    public val name: String,
    values: List<Double>,
    public val color: Color? = null,
) {
    public val values: List<Double> = Collections.unmodifiableList(values.toList())
    init {
        require(this.values.all { it.isFinite() && it >= 0.0 }) { "Radar values must be finite and nonnegative" }
    }
}

@Composable
public fun RadarChart(
    labels: List<String>,
    series: List<RadarSeries>,
    modifier: Modifier = Modifier,
    maxValue: Double = 1.0,
    contentDescription: String = "Radar chart",
) {
    require(maxValue.isFinite() && maxValue > 0.0)
    require(labels.size >= 3 || labels.isEmpty()) { "Radar charts need at least three axes" }
    require(series.all { it.values.size == labels.size && it.values.all { value -> value <= maxValue } })
    val palette = LocalChartColors.current
    val measurer = rememberTextMeasurer()
    val empty = labels.isEmpty() || series.isEmpty()
    Box(modifier.fillMaxWidth().heightIn(min = 240.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.matchParentSize()
                .semantics {
                    this.contentDescription = contentDescription
                    role = Role.Image
                    stateDescription = if (empty) "No data" else series.joinToString("; ") { entry ->
                        entry.name + ": " + labels.zip(entry.values).joinToString { "${it.first} ${it.second}" }
                    }
                }
                .drawWithCache {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = (min(size.width, size.height) / 2f - 36.dp.toPx()).coerceAtLeast(0f)
                    fun position(index: Int, fraction: Double): Offset {
                        val angle = index.toDouble() / labels.size * PI * 2 - PI / 2
                        return center + Offset((cos(angle) * radius * fraction).toFloat(), (sin(angle) * radius * fraction).toFloat())
                    }
                    fun polygon(values: List<Double>): Path = Path().apply {
                        values.forEachIndexed { index, fraction ->
                            val point = position(index, fraction)
                            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                        }
                        close()
                    }
                    val grids = if (empty) emptyList() else (1..4).map { level -> polygon(List(labels.size) { level / 4.0 }) }
                    val paths = series.map { polygon(it.values.map { value -> value / maxValue }) }
                    val text = labels.map { measurer.measure(it, TextStyle(color = palette.mutedText, fontSize = 11.sp)) }
                    onDrawBehind {
                        if (empty || radius <= 0f) return@onDrawBehind
                        grids.forEach { drawPath(it, palette.grid, style = Stroke(1.dp.toPx())) }
                        labels.indices.forEach { index ->
                            drawLine(palette.grid, center, position(index, 1.0))
                            val label = text[index]
                            val target = position(index, 1.18)
                            drawText(label, topLeft = Offset(
                                (target.x - label.size.width / 2f).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f)),
                                (target.y - label.size.height / 2f).coerceIn(0f, (size.height - label.size.height).coerceAtLeast(0f)),
                            ))
                        }
                        paths.forEachIndexed { index, path ->
                            val color = series[index].color ?: palette.series[index % palette.series.size]
                            drawPath(path, color.copy(alpha = 0.14f))
                            drawPath(path, color, style = Stroke(2.dp.toPx()))
                        }
                    }
                },
        ) {}
        if (empty) BasicText("No data", style = TextStyle(color = palette.mutedText))
    }
}

@Immutable
public class HeatmapData(xLabels: List<String>, yLabels: List<String>, values: List<List<Double>>) {
    public val xLabels: List<String> = Collections.unmodifiableList(xLabels.toList())
    public val yLabels: List<String> = Collections.unmodifiableList(yLabels.toList())
    /** Rows correspond to y labels; columns correspond to x labels. */
    public val values: List<List<Double>> = Collections.unmodifiableList(values.map { Collections.unmodifiableList(it.toList()) })
    init {
        require(this.xLabels.size.toLong() * this.yLabels.size <= Int.MAX_VALUE) { "Heatmap dimensions exceed the supported cell count" }
        require(this.values.size == this.yLabels.size && this.values.all { it.size == this.xLabels.size })
        require(this.values.all { row -> row.all { it.isFinite() } })
    }
}

@Composable
public fun HeatmapChart(
    data: HeatmapData,
    modifier: Modifier = Modifier,
    lowColor: Color = LocalChartColors.current.surface,
    highColor: Color = LocalChartColors.current.primary,
    contentDescription: String = "Heatmap",
    valueFormatter: (Double) -> String = { it.toString() },
    onCellSelected: ((row: Int, column: Int) -> Unit)? = null,
) {
    val palette = LocalChartColors.current
    val measurer = rememberTextMeasurer()
    val selectedState = rememberSaveable(data.xLabels, data.yLabels) { mutableIntStateOf(-1) }
    var selected by selectedState
    var measuredSize by remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
    val callback by rememberUpdatedState(onCellSelected)
    val density = LocalDensity.current
    val rows = data.yLabels.size
    val columns = data.xLabels.size
    val count = rows * columns
    val active = selected.takeIf { it in 0 until count }
    val left = 48.dp
    val bottom = 28.dp
    fun select(index: Int) {
        if (count == 0) return
        selected = index.coerceIn(0, count - 1)
        callback?.invoke(selected / columns, selected % columns)
    }
    val drawing = remember(data, palette, lowColor, highColor, density, measurer, selectedState) {
        Modifier.drawWithCache {
            val origin = left.toPx().coerceAtMost(size.width)
            val width = (size.width - origin).coerceAtLeast(0f)
            val height = (size.height - bottom.toPx()).coerceAtLeast(0f)
            val cellWidth = if (columns > 0) width / columns else 0f
            val cellHeight = if (rows > 0) height / rows else 0f
            var minimum = Double.POSITIVE_INFINITY
            var maximum = Double.NEGATIVE_INFINITY
            data.values.forEach { row -> row.forEach { value -> minimum = minOf(minimum, value); maximum = maxOf(maximum, value) } }
            if (count == 0) { minimum = 0.0; maximum = 0.0 }
            val scale = maxOf(kotlin.math.abs(minimum), kotlin.math.abs(maximum), 1.0)
            val span = maximum / scale - minimum / scale
            // Dense matrices aggregate to pixel-sized tiles instead of drawing invisible cells.
            val drawRows = if (count > 0) minOf(rows, maxOf(1, kotlin.math.ceil(height).toInt())) else 0
            val drawColumns = if (count > 0) minOf(columns, maxOf(1, kotlin.math.ceil(width).toInt())) else 0
            val tileWidth = if (drawColumns > 0) width / drawColumns else 0f
            val tileHeight = if (drawRows > 0) height / drawRows else 0f
            val fills = List(drawRows) { row -> List(drawColumns) { column ->
                val firstRow = (row.toLong() * rows / drawRows).toInt()
                val lastRow = ((row + 1L) * rows / drawRows).toInt()
                val firstColumn = (column.toLong() * columns / drawColumns).toInt()
                val lastColumn = ((column + 1L) * columns / drawColumns).toInt()
                var sum = 0.0
                var samples = 0L
                for (sourceRow in firstRow until lastRow) for (sourceColumn in firstColumn until lastColumn) {
                    sum += data.values[sourceRow][sourceColumn] / scale
                    samples++
                }
                val normalized = if (samples > 0) sum / samples else 0.0
                lerp(lowColor, highColor, if (span > 0.0) ((normalized - minimum / scale) / span).toFloat().coerceIn(0f, 1f) else 0.5f)
            } }
            val textStyle = TextStyle(color = palette.mutedText, fontSize = 10.sp)
            val xStride = if (cellWidth > 0f) kotlin.math.ceil(40.dp.toPx() / cellWidth).toInt().coerceAtLeast(1) else 1
            val yStride = if (cellHeight > 0f) kotlin.math.ceil(20.dp.toPx() / cellHeight).toInt().coerceAtLeast(1) else 1
            val xText = data.xLabels.mapIndexedNotNull { index, label ->
                if (count > 0 && index % xStride == 0) index to measurer.measure(label, textStyle) else null
            }
            val yText = data.yLabels.mapIndexedNotNull { index, label ->
                if (count > 0 && index % yStride == 0) index to measurer.measure(label, textStyle) else null
            }
            onDrawBehind {
                if (width <= 0f || height <= 0f || count == 0) return@onDrawBehind
                clipRect(origin, 0f, size.width, height) {
                    val gap = minOf(2.dp.toPx(), tileWidth * 0.12f, tileHeight * 0.12f)
                    fills.forEachIndexed { row, colors -> colors.forEachIndexed { column, color ->
                        drawRect(color, Offset(origin + column * tileWidth, row * tileHeight), Size(tileWidth - gap, tileHeight - gap))
                    } }
                    val selection = selectedState.intValue
                    if (selection in 0 until count) {
                        drawRect(palette.text, Offset(origin + selection % columns * cellWidth, selection / columns * cellHeight),
                            Size(cellWidth.coerceAtLeast(1f), cellHeight.coerceAtLeast(1f)), style = Stroke(2.dp.toPx()))
                    }
                }
                xText.forEach { (index, text) ->
                    val x = (origin + index * cellWidth + (cellWidth - text.size.width) / 2f).coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
                    drawText(text, topLeft = Offset(x, height + 4.dp.toPx()))
                }
                yText.forEach { (index, text) ->
                    drawText(text, topLeft = Offset(0f, (index * cellHeight + (cellHeight - text.size.height) / 2f).coerceAtLeast(0f)))
                }
            }
        }
    }
    Box(modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.matchParentSize().onSizeChanged { measuredSize = it }
                .semantics {
                    this.contentDescription = contentDescription
                    role = Role.Image
                    stateDescription = active?.let {
                        val row = it / columns
                        val column = it % columns
                        "${data.yLabels[row]}, ${data.xLabels[column]}: ${valueFormatter(data.values[row][column])}"
                    } ?: if (count == 0) "No data" else "$rows rows, $columns columns"
                    if (count > 0) customActions = listOf(
                        CustomAccessibilityAction("Next cell") { select(((active ?: -1) + 1) % count); true },
                        CustomAccessibilityAction("Previous cell") { select(((active ?: count) - 1 + count) % count); true },
                    )
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || count == 0) false else when (event.key) {
                        Key.DirectionRight -> { select(((active ?: -1) + 1) % count); true }
                        Key.DirectionLeft -> { select(((active ?: count) - 1 + count) % count); true }
                        Key.DirectionDown -> { select((active ?: -columns) + columns); true }
                        Key.DirectionUp -> { select((active ?: count) - columns); true }
                        else -> false
                    }
                }.focusable()
                .pointerInput(data, density) {
                    detectTapGestures { point ->
                        val width = (measuredSize.width - left.toPx()).coerceAtLeast(0f)
                        val height = (measuredSize.height - bottom.toPx()).coerceAtLeast(0f)
                        if (count > 0 && width > 0f && height > 0f && point.x >= left.toPx() && point.x < measuredSize.width && point.y >= 0f && point.y < height) {
                            val column = ((point.x - left.toPx()) / width * columns).toInt().coerceIn(0, columns - 1)
                            val row = (point.y / height * rows).toInt().coerceIn(0, rows - 1)
                            select(row * columns + column)
                        }
                    }
                }
                .then(drawing),
        ) {}
        if (count == 0) BasicText("No data", style = TextStyle(color = palette.mutedText))
    }
}
