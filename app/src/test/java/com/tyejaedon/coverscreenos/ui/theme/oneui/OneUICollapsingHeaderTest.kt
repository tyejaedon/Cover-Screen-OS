package com.tyejaedon.coverscreenos.ui.theme.oneui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUICollapsingHeaderState
import org.junit.Assert.assertEquals
import org.junit.Test

class OneUICollapsingHeaderTest {

    @Test
    fun `header state initializes fully expanded at 0 scroll offset`() {
        val state = OneUICollapsingHeaderState(
            maxExpandedHeightPx = 600f,
            minCollapsedHeightPx = 168f
        )
        assertEquals(0f, state.scrollOffset, 0.001f)
        assertEquals(0f, state.collapseFraction, 0.001f)
        assertEquals(600f, state.currentHeightPx, 0.001f)
        assertEquals(432f, state.collapseRangePx, 0.001f)
    }

    @Test
    fun `scrolling up smoothly interpolates collapse fraction and height`() {
        val state = OneUICollapsingHeaderState(
            maxExpandedHeightPx = 600f,
            minCollapsedHeightPx = 168f
        )

        // Scroll up by 216px (exactly 50% of 432px range)
        val available = Offset(0f, -216f)
        val consumed = state.nestedScrollConnection.onPreScroll(available, NestedScrollSource.UserInput)

        assertEquals(216f, state.scrollOffset, 0.001f)
        assertEquals(0.5f, state.collapseFraction, 0.001f)
        assertEquals(384f, state.currentHeightPx, 0.001f)
        assertEquals(Offset(0f, -216f), consumed)
    }

    @Test
    fun `overscroll clamps height to compact pinned 56dp equivalent`() {
        val state = OneUICollapsingHeaderState(
            maxExpandedHeightPx = 600f,
            minCollapsedHeightPx = 168f
        )

        // Scroll up by 1000px (exceeds 432px range)
        state.nestedScrollConnection.onPreScroll(Offset(0f, -1000f), NestedScrollSource.UserInput)

        assertEquals(432f, state.scrollOffset, 0.001f)
        assertEquals(1f, state.collapseFraction, 0.001f)
        assertEquals(168f, state.currentHeightPx, 0.001f)

        // Scroll back down by 200px
        state.nestedScrollConnection.onPreScroll(Offset(0f, 200f), NestedScrollSource.UserInput)

        assertEquals(232f, state.scrollOffset, 0.001f)
        assertEquals(232f / 432f, state.collapseFraction, 0.001f)
        assertEquals(168f + (432f - 232f), state.currentHeightPx, 0.001f)
    }
}
