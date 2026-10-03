package com.joelromanpr.charts.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.lerp
import com.joelromanpr.charts.core.Candle
import com.joelromanpr.charts.core.CandleSeries
import com.joelromanpr.charts.core.ChartRange
import com.joelromanpr.charts.core.Point
import com.joelromanpr.charts.core.PointSeries
import com.joelromanpr.charts.core.Slice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChartInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val points = PointSeries(List(101) { Point(it.toDouble(), it * 0.5) }, "Price")

    private fun action(description: String, label: String) {
        val actions = compose.onNodeWithContentDescription(description).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertTrue(actions.first { it.label == label }.action())
        }
    }

    @Test fun linkedChartsSelectOriginalValuesAndUseUpdatedCallback() {
        val state = ChartState()
        var latestHandler by mutableStateOf(false)
        var firstCalls = 0
        var latestCalls = 0
        var selection: ChartSelection? = null
        val candles = CandleSeries(List(101) { Candle(it.toDouble(), 20.0, 24.0, 19.0, 22.0, 300.0) })
        compose.setContent {
            ChartTheme {
                Column {
                    CandlestickChart(candles, Modifier.fillMaxWidth().height(220.dp), state,
                        onSelectionChanged = if (latestHandler) ({ selection = it; latestCalls++ })
                            else ({ selection = it; firstCalls++ }))
                    CartesianChart(listOf(ChartLayer.Columns(points)), Modifier.fillMaxWidth().height(180.dp), state, contentDescription = "Volume")
                }
            }
        }
        action("Chart", "Next data point")
        compose.runOnIdle { assertEquals(0.0, state.selectedX!!, 0.0); latestHandler = true }
        action("Chart", "Next data point")
        compose.runOnIdle {
            assertEquals(1.0, state.selectedX!!, 0.0)
            assertEquals(22.0, selection!!.values.single().candle!!.close, 0.0)
            assertEquals(1, firstCalls)
            assertEquals(1, latestCalls)
        }
        compose.onNodeWithContentDescription("Volume").assert(
            SemanticsMatcher("linked crosshair exposes exact volume value") { it.config[SemanticsProperties.StateDescription].contains("0.5") },
        )
    }

    @Test fun viewportAndSelectionSurviveStateRestoration() {
        val restoration = StateRestorationTester(compose)
        lateinit var state: ChartState
        restoration.setContent {
            state = rememberChartState()
            LineChart(points, state = state)
        }
        compose.runOnIdle { state.setViewport(ChartRange(20.0, 60.0)); state.select(32.0) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(ChartRange(20.0, 60.0), state.visibleXRange)
            assertEquals(32.0, state.selectedX!!, 0.0)
        }
    }

    @Test fun horizontalPanIsBoundedAndVerticalSwipeScrollsParent() {
        val state = ChartState(ChartRange(20.0, 60.0))
        lateinit var scroll: androidx.compose.foundation.ScrollState
        compose.setContent {
            scroll = rememberScrollState()
            Column(Modifier.verticalScroll(scroll)) {
                CartesianChart(listOf(ChartLayer.Line(points)), Modifier.fillMaxWidth().height(260.dp), state, contentDescription = "Price")
                repeat(20) { BasicText("Row $it", Modifier.height(80.dp)) }
            }
        }
        compose.onNodeWithContentDescription("Price").performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertNotNull(state.visibleXRange)
            assertTrue(state.visibleXRange!!.min > 20.0)
            assertTrue(state.visibleXRange!!.max <= 100.0)
        }
        compose.onNodeWithContentDescription("Price").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue("Vertical chart swipes must scroll the host", scroll.value > 0) }
    }

    @Test fun accessibleZoomAndResetSynchronizeViewport() {
        val state = ChartState()
        compose.setContent { LineChart(points, state = state) }
        compose.onNodeWithContentDescription("Chart").performTouchInput {
            down(0, Offset(width * 0.4f, height * 0.5f))
            down(1, Offset(width * 0.6f, height * 0.5f))
            advanceEventTime(16)
            moveTo(0, Offset(width * 0.2f, height * 0.5f))
            moveTo(1, Offset(width * 0.8f, height * 0.5f))
            advanceEventTime(16)
            up(0)
            up(1)
        }
        compose.runOnIdle { assertTrue("Pinch must reduce the visible span", state.visibleXRange!!.max - state.visibleXRange!!.min < 100.0) }
        action("Chart", "Zoom in")
        compose.runOnIdle { assertTrue(state.visibleXRange!!.max - state.visibleXRange!!.min < 100.0) }
        action("Chart", "Next data point")
        action("Chart", "Reset chart")
        compose.runOnIdle { assertEquals(null, state.visibleXRange); assertEquals(null, state.selectedX) }
    }

    @Test fun darkThemeEmptyAndPortfolioSelectionRemainAccessible() {
        var selected: Int? = null
        compose.setContent {
            ChartTheme(ChartColors.dark()) {
                Column {
                    CartesianChart(emptyList(), Modifier.height(180.dp), contentDescription = "Empty chart")
                    DonutChart(listOf(Slice("Equities", 52.0), Slice("Cash", 48.0)), Modifier.height(220.dp), onSliceSelected = { selected = it })
                }
            }
        }
        compose.onNodeWithContentDescription("Empty chart").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "No data"))
        action("Allocation chart", "Next slice")
        compose.runOnIdle { assertEquals(0, selected) }
        action("Allocation chart", "Next slice")
        compose.runOnIdle { assertEquals(1, selected) }
        compose.onNodeWithContentDescription("Allocation chart").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Cash: 48.0"))
    }

    @Test fun liveSnapshotRetainsSelectedTimestampAndUpdatesDescription() {
        val state = ChartState()
        var snapshot by mutableStateOf(points)
        compose.setContent { LineChart(snapshot, state = state) }
        compose.runOnIdle { state.select(100.0) }
        compose.runOnIdle { snapshot = PointSeries(points.points.dropLast(1) + Point(100.0, 73.25), "Price") }
        compose.onNodeWithContentDescription("Chart").assert(
            SemanticsMatcher("forming sample shows the updated value") { it.config[SemanticsProperties.StateDescription].contains("73.25") },
        )
        compose.runOnIdle { assertEquals(100.0, state.selectedX!!, 0.0) }
    }

    @Test fun followingLatestPreservesSpanWhenNewSamplesArrive() {
        val state = ChartState(ChartRange(75.0, 100.0))
        var snapshot by mutableStateOf(points)
        compose.setContent { LineChart(snapshot, state = state) }
        compose.runOnIdle { state.followLatest(); snapshot = PointSeries(points.points + Point(101.0, 52.0)) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(ChartRange(76.0, 101.0), state.visibleXRange) }
    }

    @Test fun mixedValueScalesKeepPriceAndVolumeIndependent() {
        val state = ChartState()
        val volume = PointSeries(List(101) { Point(it.toDouble(), 100_000.0 + it) }, "Volume")
        var leftRange: ChartRange? = null
        var rightRange: ChartRange? = null
        compose.setContent {
            CartesianChart(
                listOf(ChartLayer.Line(points), ChartLayer.Columns(volume, axis = AxisSide.Right)),
                Modifier.fillMaxWidth().height(260.dp), state,
                axes = ChartAxes(yFormatter = AxisFormatter { "$$it" }, rightYFormatter = AxisFormatter { "${it} shares" }),
                decoration = { coordinates -> leftRange = coordinates.yRange; rightRange = coordinates.rightYRange },
            )
        }
        compose.runOnIdle { state.select(100.0) }
        compose.onNodeWithContentDescription("Chart").assert(
            SemanticsMatcher("selection uses each layer's formatter") {
                val description = it.config[SemanticsProperties.StateDescription]
                description.contains("$50.0") && description.contains("100100.0 shares")
            },
        )
        compose.runOnIdle {
            assertTrue(leftRange!!.max < 100.0)
            assertTrue(rightRange!!.max > 100_000.0)
        }
    }

    @Test fun denseHeatmapStaysVisibleAndClearsSelectionForNewLabels() {
        val palette = ChartColors.dark()
        var data by mutableStateOf(HeatmapData(List(200) { "X$it" }, List(200) { "Y$it" }, List(200) { List(200) { 1.0 } }))
        compose.setContent { ChartTheme(palette) { HeatmapChart(data, Modifier.fillMaxWidth().height(240.dp)) } }
        val pixels = compose.onNodeWithContentDescription("Heatmap").captureToImage().toPixelMap()
        val actual = pixels[pixels.width / 2, pixels.height / 2]
        val expected = lerp(palette.surface, palette.primary, 0.5f)
        assertEquals("Dense cells must retain their fill", expected.red, actual.red, 0.03f)
        assertEquals(expected.blue, actual.blue, 0.03f)
        action("Heatmap", "Next cell")
        compose.runOnIdle { data = HeatmapData(listOf("Open", "Close"), listOf("Monday"), listOf(listOf(2.0, 4.0))) }
        compose.onNodeWithContentDescription("Heatmap").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 rows, 2 columns"))
    }
}
