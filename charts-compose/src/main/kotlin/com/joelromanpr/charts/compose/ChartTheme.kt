package com.joelromanpr.charts.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colors shared by every chart. No application theme dependency is required. */
public data class ChartColors(
    public val background: Color,
    public val surface: Color,
    public val text: Color,
    public val mutedText: Color,
    public val grid: Color,
    public val primary: Color,
    public val secondary: Color,
    public val positive: Color,
    public val negative: Color,
    public val series: List<Color>,
) {
    init { require(series.isNotEmpty()) { "A chart palette needs at least one series color." } }

    public companion object {
        public fun light(): ChartColors = ChartColors(
            background = Color(0xFFFFFFFF), surface = Color(0xFFF4F6FA),
            text = Color(0xFF172035), mutedText = Color(0xFF6B7280), grid = Color(0xFFE7EBF2),
            primary = Color(0xFF3864EA), secondary = Color(0xFF8651D9),
            positive = Color(0xFF07866C), negative = Color(0xFFD63D58),
            series = listOf(Color(0xFF3864EA), Color(0xFF8651D9), Color(0xFF07866C), Color(0xFFE89920), Color(0xFFD63D58)),
        )

        public fun dark(): ChartColors = ChartColors(
            background = Color(0xFF111723), surface = Color(0xFF1B2433),
            text = Color(0xFFF0F3FA), mutedText = Color(0xFF9CA9BD), grid = Color(0xFF293447),
            primary = Color(0xFF7B9CFF), secondary = Color(0xFFB99AFF),
            positive = Color(0xFF48CBA4), negative = Color(0xFFFF7A90),
            series = listOf(Color(0xFF7B9CFF), Color(0xFFB99AFF), Color(0xFF48CBA4), Color(0xFFFFC66D), Color(0xFFFF7A90)),
        )
    }
}

public val LocalChartColors: ProvidableCompositionLocal<ChartColors> = staticCompositionLocalOf { ChartColors.light() }

@Composable
public fun ChartTheme(
    colors: ChartColors = if (isSystemInDarkTheme()) ChartColors.dark() else ChartColors.light(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalChartColors provides colors, content = content)
}
