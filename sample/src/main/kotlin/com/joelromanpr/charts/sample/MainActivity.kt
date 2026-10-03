package com.joelromanpr.charts.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.joelromanpr.charts.compose.AxisFormatter
import com.joelromanpr.charts.compose.CartesianChart
import com.joelromanpr.charts.compose.ChartAxes
import com.joelromanpr.charts.compose.ChartColors
import com.joelromanpr.charts.compose.ChartLayer
import com.joelromanpr.charts.compose.ChartTheme
import com.joelromanpr.charts.compose.DepthChart
import com.joelromanpr.charts.compose.DonutChart
import com.joelromanpr.charts.compose.HeatmapChart
import com.joelromanpr.charts.compose.HeatmapData
import com.joelromanpr.charts.compose.LineInterpolation
import com.joelromanpr.charts.compose.RadarChart
import com.joelromanpr.charts.compose.RadarSeries
import com.joelromanpr.charts.compose.ReferenceLine
import com.joelromanpr.charts.compose.rememberChartState
import com.joelromanpr.charts.core.Candle
import com.joelromanpr.charts.core.Indicators
import com.joelromanpr.charts.core.Point
import com.joelromanpr.charts.core.PointSeries
import com.joelromanpr.charts.core.RollingCandleBuffer
import com.joelromanpr.charts.core.Slice
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private val foreground = mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Gallery(foreground.value) { dark ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    override fun onResume() { super.onResume(); foreground.value = true }
    override fun onPause() { foreground.value = false; super.onPause() }
}

@Composable
private fun Gallery(foreground: Boolean, onThemeChanged: (Boolean) -> Unit) {
    val systemDark = isSystemInDarkTheme()
    var dark by rememberSaveable { mutableStateOf(systemDark) }
    SideEffect { onThemeChanged(dark) }
    val palette = remember(dark) { if (dark) ChartColors.dark() else ChartColors.light() }
    val material = if (dark) darkColorScheme(primary = palette.primary, background = palette.background, surface = palette.surface)
        else lightColorScheme(primary = palette.primary, background = palette.background, surface = palette.surface)
    MaterialTheme(colorScheme = material) {
        ChartTheme(palette) {
            Scaffold { insets ->
                Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(Modifier.widthIn(max = 1000.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = palette.primary, shape = RoundedCornerShape(12.dp)) {
                                    Text("↗", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text("Jetpack Charts", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                    Text("COMPOSE · 2.0.0", color = palette.mutedText, style = MaterialTheme.typography.labelSmall)
                                }
                                Switch(checked = dark, onCheckedChange = { dark = it }, modifier = Modifier.semantics { contentDescription = "Dark theme" })
                            }
                        }
                        item {
                            Column(Modifier.padding(horizontal = 24.dp)) {
                                Text("Every signal. Clearly.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                                Text("Markets, portfolios, and commerce. Explore the charts with simulated data.", Modifier.padding(top = 8.dp), color = palette.mutedText)
                            }
                        }
                        item { MarketCard(palette, foreground) }
                        item {
                            ChartCard("Order book", "Cumulative bid and ask liquidity") {
                                DepthChart(DemoData.orderBook, Modifier.fillMaxWidth().height(240.dp))
                                Legend(listOf("Bids" to palette.positive, "Asks" to palette.negative))
                            }
                        }
                        item {
                            ChartCard("Portfolio allocation", "Tap a segment to inspect its weight") {
                                var selected by remember { mutableStateOf<String?>(null) }
                                val slices = remember { listOf(Slice("Equities", 52.0), Slice("Bonds", 24.0), Slice("Crypto", 14.0), Slice("Cash", 10.0)) }
                                DonutChart(slices, Modifier.height(240.dp), onSliceSelected = { index -> selected = index?.let { "${slices[it].label} · ${slices[it].value.toInt()}%" } }) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(selected ?: "$24,860", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                        Text("Portfolio", color = palette.mutedText, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Legend(slices.mapIndexed { index, slice -> "${slice.label} ${slice.value.toInt()}%" to palette.series[index] })
                            }
                        }
                        item {
                            ChartCard("Revenue by channel", "Stacked monthly sales · USD thousands") {
                                val layers = remember(palette) { listOf(ChartLayer.Columns(DemoData.revenue, color = palette.primary, stackKey = "sales"), ChartLayer.Columns(DemoData.retail, color = palette.secondary, stackKey = "sales")) }
                                CartesianChart(layers, Modifier.fillMaxWidth().height(240.dp), axes = remember { ChartAxes(xFormatter = AxisFormatter { "${it.toInt() + 1}" }) })
                                Legend(listOf("Online" to palette.primary, "Retail" to palette.secondary))
                            }
                        }
                        item {
                            ChartCard("Market activity", "Relative volume by weekday and session") {
                                val data = remember { HeatmapData(listOf("09h", "11h", "13h", "15h", "17h"), listOf("Mon", "Tue", "Wed", "Thu", "Fri"), List(5) { row -> List(5) { column -> ((row * 19 + column * 31) % 100).toDouble() } }) }
                                HeatmapChart(data, Modifier.fillMaxWidth().height(210.dp))
                            }
                        }
                        item {
                            ChartCard("Portfolio profile", "Normalized factors · comparison") {
                                val series = remember { listOf(RadarSeries("Portfolio", listOf(0.8, 0.6, 0.9, 0.55, 0.7)), RadarSeries("Benchmark", listOf(0.65, 0.85, 0.6, 0.75, 0.5))) }
                                RadarChart(listOf("Growth", "Value", "Quality", "Yield", "Momentum"), series, Modifier.fillMaxWidth().height(260.dp))
                                Legend(listOf("Portfolio" to palette.primary, "Benchmark" to palette.secondary))
                            }
                        }
                        item {
                            ChartCard("Risk and return", "Scatter plot · synthetic observations") {
                                val data = remember { PointSeries(List(60) { Point(it.toDouble(), 8.0 + it * 0.16 + (it * 17 % 11)) }, "Assets") }
                                CartesianChart(listOf(ChartLayer.Scatter(data)), Modifier.fillMaxWidth().height(220.dp))
                            }
                        }
                        item { Text("Apache 2.0 · com.joelromanpr.charts", Modifier.padding(24.dp), color = palette.mutedText, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketCard(palette: ChartColors, foreground: Boolean) {
    val buffer = remember { RollingCandleBuffer(256, "Meridian").apply { DemoData.candles.candles.forEach(::append) } }
    var candles by remember { mutableStateOf(buffer.snapshot()) }
    var live by rememberSaveable { mutableStateOf(false) }
    var candlesticks by rememberSaveable { mutableStateOf(false) }
    val state = rememberChartState()
    val priceFormat = remember { DecimalFormat("$#,##0.00") }
    val axes = remember {
        val time = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        ChartAxes(xFormatter = AxisFormatter { time.format(Date((it * 1000).toLong())) }, yFormatter = AxisFormatter { priceFormat.format(it) })
    }
    LaunchedEffect(live, foreground) {
        val random = Random(67)
        var ticks = 0
        while (live && foreground) {
            delay(500)
            val latest = buffer.snapshot().candles.last()
            val next = latest.close + random.nextDouble(-0.3, 0.4)
            if (++ticks % 4 == 0) {
                buffer.append(Candle(latest.x + 60, latest.close, max(latest.close, next), min(latest.close, next), next, 300.0))
            } else {
                buffer.replaceLast(latest.copy(high = max(latest.high, next), low = min(latest.low, next), close = next, volume = latest.volume + 200))
            }
            candles = buffer.snapshot()
        }
    }
    val close = remember(candles) { DemoData.close(candles) }
    val volume = remember(candles) { DemoData.volume(candles) }
    val movingAverage = remember(candles) { Indicators.sma(candles) }
    val rsi = remember(candles) { Indicators.rsi(candles) }
    val layers = remember(candles, close, movingAverage, candlesticks, palette) {
        listOf(
            if (candlesticks) ChartLayer.Candles(candles) else ChartLayer.Line(close, color = palette.positive, fill = palette.positive.copy(alpha = 0.1f), interpolation = LineInterpolation.Cubic),
            ChartLayer.Line(movingAverage, color = palette.secondary, strokeWidth = 1.dp, name = "SMA 20"),
        )
    }
    ChartCard("Meridian", "Simulated market · USD · UTC") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(priceFormat.format(candles.candles.last().close), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("+${priceFormat.format(candles.candles.last().close - candles.candles.first().open)} today", color = palette.positive)
            }
            FilterChip(selected = live, onClick = { live = !live }, label = { Text("Live demo") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !candlesticks, onClick = { candlesticks = false }, label = { Text("Line") })
            FilterChip(selected = candlesticks, onClick = { candlesticks = true }, label = { Text("Candles") })
            OutlinedButton(onClick = { state.reset() }) { Text("Reset") }
        }
        CartesianChart(layers, Modifier.fillMaxWidth().height(280.dp), state = state, axes = axes, referenceLines = listOf(ReferenceLine(candles.candles.first().open, "Open")), contentDescription = "Meridian price")
        Text("Volume", color = palette.mutedText, style = MaterialTheme.typography.labelMedium)
        CartesianChart(listOf(ChartLayer.Columns(volume, palette.primary.copy(alpha = 0.6f))), Modifier.fillMaxWidth().height(110.dp), state = state, axes = remember { ChartAxes(showX = false, yFormatter = AxisFormatter { "${(it / 1000).toInt()}k" }, tickCount = 3) }, contentDescription = "Meridian volume")
        Text("RSI · 14", color = palette.mutedText, style = MaterialTheme.typography.labelMedium)
        CartesianChart(listOf(ChartLayer.Line(rsi, color = palette.secondary)), Modifier.fillMaxWidth().height(110.dp), state = state, axes = remember { ChartAxes(showX = false, tickCount = 3) }, referenceLines = listOf(ReferenceLine(30.0), ReferenceLine(70.0)), contentDescription = "Relative strength index")
        Text("Pinch to zoom · drag to pan · tap or hold to inspect", color = palette.mutedText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun Legend(entries: List<Pair<String, Color>>) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
