package com.tyejaedon.coverscreenos.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chrisbanes.haze.HazeState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class CoverAppWidgetHostTileTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `empty state shows Add Widget button and triggers callback`() {
        val host = mockk<AppWidgetHost>(relaxed = true)
        val manager = mockk<AppWidgetManager>(relaxed = true)
        val hostManager = CoverAppWidgetHostManager(context, appWidgetHost = host, appWidgetManager = manager)
        var addClicks = 0

        composeRule.setContent {
            CoverAppWidgetHostTile(
                widgetIds = emptyList(),
                widgetHostManager = hostManager,
                onRemoveWidget = {},
                onAddWidgetRequest = { addClicks++ },
                hazeState = HazeState()
            )
        }

        composeRule.onNodeWithText("No Widgets Added").assertIsDisplayed()
        composeRule.onNodeWithText("Add Widget").performClick()
        assertEquals(1, addClicks)

        composeRule.onNodeWithText("Add").performClick()
        assertEquals(2, addClicks)
    }

    @Test
    fun `populated state shows widgets, Add button, and triggers remove callback`() {
        val host = mockk<AppWidgetHost>(relaxed = true)
        val manager = mockk<AppWidgetManager>(relaxed = true)
        every { manager.getAppWidgetInfo(any()) } returns null
        val hostManager = CoverAppWidgetHostManager(context, appWidgetHost = host, appWidgetManager = manager)
        val removedIds = mutableListOf<Int>()
        var addClicks = 0

        composeRule.setContent {
            CoverAppWidgetHostTile(
                widgetIds = listOf(101),
                widgetHostManager = hostManager,
                onRemoveWidget = { removedIds += it },
                onAddWidgetRequest = { addClicks++ },
                hazeState = HazeState()
            )
        }

        composeRule.onNodeWithText("Widget unavailable (101)").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove Widget").performClick()
        assertEquals(listOf(101), removedIds)

        composeRule.onAllNodesWithText("Add Widget")[0].performClick()
        assertEquals(1, addClicks)
    }

    @Test
    fun `moveWidgetId swaps adjacent items and respects bounds`() {
        assertEquals(listOf(20, 10, 30), moveWidgetId(listOf(10, 20, 30), index = 0, delta = 1))
        assertEquals(listOf(10, 30, 20), moveWidgetId(listOf(10, 20, 30), index = 2, delta = -1))
        assertEquals(listOf(10, 20, 30), moveWidgetId(listOf(10, 20, 30), index = 0, delta = -1))
        assertEquals(listOf(10, 20, 30), moveWidgetId(listOf(10, 20, 30), index = 2, delta = 1))
    }

    @Test
    fun `up and down buttons reorder widgets and disable at boundaries`() {
        val host = mockk<AppWidgetHost>(relaxed = true)
        val manager = mockk<AppWidgetManager>(relaxed = true)
        every { manager.getAppWidgetInfo(any()) } returns null
        val hostManager = CoverAppWidgetHostManager(context, appWidgetHost = host, appWidgetManager = manager)
        val reordered = mutableListOf<List<Int>>()

        composeRule.setContent {
            CoverAppWidgetHostTile(
                widgetIds = listOf(101, 202),
                widgetHostManager = hostManager,
                onRemoveWidget = {},
                onAddWidgetRequest = {},
                onReorderWidgets = { reordered += it },
                hazeState = HazeState()
            )
        }

        val upButtons = composeRule.onAllNodesWithContentDescription("Move Widget Up")
        val downButtons = composeRule.onAllNodesWithContentDescription("Move Widget Down")

        upButtons[0].assertIsNotEnabled()
        downButtons[0].assertIsEnabled()
        upButtons[1].assertIsEnabled()
        downButtons[1].assertIsNotEnabled()

        downButtons[0].performClick()
        upButtons[1].performClick()

        assertEquals(listOf(listOf(202, 101), listOf(202, 101)), reordered)
    }
}
