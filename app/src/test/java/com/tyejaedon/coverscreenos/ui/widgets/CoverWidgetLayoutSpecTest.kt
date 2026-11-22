package com.tyejaedon.coverscreenos.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverWidgetLayoutSpecTest {

    private fun meta(
        cellW: Int,
        cellH: Int,
        minW: Int = 0,
        minH: Int = 0,
        maxResizeW: Int = 0,
        maxResizeH: Int = 0,
        resizeMode: Int = AppWidgetProviderInfo.RESIZE_BOTH
    ) = WidgetGridMetadata(
        targetCellWidth = cellW,
        targetCellHeight = cellH,
        minWidthDp = minW,
        minHeightDp = minH,
        maxResizeWidthDp = maxResizeW,
        maxResizeHeightDp = maxResizeH,
        resizeMode = resizeMode
    )

    @Test
    fun `2x2 spans 1 column and stays square`() {
        val spec = computeWidgetPlacementSpec(meta(2, 2), containerWidthDp = 336)
        assertEquals(1, spec.colSpan)
        assertEquals(163, spec.slotWidthDp)
        assertEquals(163, spec.slotHeightDp)
        assertEquals(163, spec.widgetWidthDp)
        assertEquals(163, spec.widgetHeightDp)
    }

    @Test
    fun `4x2 spans 2 columns with 2 to 1 ratio`() {
        val spec = computeWidgetPlacementSpec(meta(4, 2), containerWidthDp = 336)
        assertEquals(2, spec.colSpan)
        assertEquals(336, spec.slotWidthDp)
        assertEquals(168, spec.slotHeightDp)
    }

    @Test
    fun `4x3 spans 2 columns and respects max height`() {
        val spec = computeWidgetPlacementSpec(meta(4, 3), containerWidthDp = 336, maxSlotHeightDp = 252)
        assertEquals(2, spec.colSpan)
        assertEquals(336, spec.slotWidthDp)
        assertEquals(252, spec.slotHeightDp)
    }

    @Test
    fun `3x4 portrait widget drops to 1 column to prevent cover screen overflow`() {
        val spec = computeWidgetPlacementSpec(meta(3, 4), containerWidthDp = 336, maxSlotHeightDp = 252)
        assertEquals(1, spec.colSpan)
        assertEquals(163, spec.slotWidthDp)
        assertEquals(217, spec.slotHeightDp)
    }

    @Test
    fun `legacy widget without targetCell falls back to minWidth and minHeight`() {
        val narrow = computeWidgetPlacementSpec(meta(0, 0, minW = 140, minH = 140), containerWidthDp = 336)
        assertEquals(1, narrow.colSpan)
        assertEquals(163, narrow.slotHeightDp)

        val wide = computeWidgetPlacementSpec(meta(0, 0, minW = 280, minH = 140), containerWidthDp = 336)
        assertEquals(2, wide.colSpan)
        assertEquals(168, wide.slotHeightDp)
    }

    @Test
    fun `RESIZE_NONE clamps inner viewport without stretching`() {
        val spec = computeWidgetPlacementSpec(
            meta(4, 2, minW = 200, minH = 100, resizeMode = AppWidgetProviderInfo.RESIZE_NONE),
            containerWidthDp = 336
        )
        assertEquals(2, spec.colSpan)
        assertEquals(336, spec.slotWidthDp)
        assertEquals(168, spec.slotHeightDp)
        assertEquals(200, spec.widgetWidthDp)
        assertEquals(100, spec.widgetHeightDp)
    }

    @Test
    fun `packTwoColumnGrid packs tall 1-col widget beside two shorter 1-col widgets and drops 2-col below`() {
        val specs = listOf(
            computeWidgetPlacementSpec(meta(3, 4), containerWidthDp = 336), // 1 col, 217dp
            computeWidgetPlacementSpec(meta(2, 1), containerWidthDp = 336), // 1 col, 82dp
            computeWidgetPlacementSpec(meta(2, 1), containerWidthDp = 336), // 1 col, 82dp (stacks in col 1)
            computeWidgetPlacementSpec(meta(4, 2), containerWidthDp = 336)  // 2 cols, 168dp (below both)
        )
        val packed = packTwoColumnGrid(
            specs = specs,
            singleColWidthPx = 163,
            fullWidthPx = 336,
            columnGapPx = 10,
            rowGapPx = 10,
            dpToPx = { it }
        )

        assertEquals(4, packed.placements.size)
        // Item 0 in col 0
        assertEquals(0, packed.placements[0].xPx)
        assertEquals(0, packed.placements[0].yPx)
        // Item 1 in col 1
        assertEquals(173, packed.placements[1].xPx)
        assertEquals(0, packed.placements[1].yPx)
        // Item 2 also in col 1 (since col 1 height 92 < col 0 height 227)
        assertEquals(173, packed.placements[2].xPx)
        assertEquals(92, packed.placements[2].yPx)
        // Item 3 spans 2 cols below col 0 (y = 227)
        assertEquals(0, packed.placements[3].xPx)
        assertEquals(227, packed.placements[3].yPx)
        assertEquals(336, packed.placements[3].widthPx)
        assertTrue(packed.totalHeightPx == 227 + 168)
    }

    @Test
    fun `packTwoColumnGrid backfills fitting 1-col widget into open gap beside earlier 1-col widget`() {
        val specs = listOf(
            computeWidgetPlacementSpec(meta(2, 2), containerWidthDp = 336), // 1 col, 163dp
            computeWidgetPlacementSpec(meta(4, 2), containerWidthDp = 336), // 2 cols, 168dp at y = 173
            computeWidgetPlacementSpec(meta(2, 2), containerWidthDp = 336)  // 1 col, 163dp -> fits in col 1 at y = 0
        )
        val packed = packTwoColumnGrid(
            specs = specs,
            singleColWidthPx = 163,
            fullWidthPx = 336,
            columnGapPx = 10,
            rowGapPx = 10,
            dpToPx = { it }
        )

        assertEquals(3, packed.placements.size)
        // Item 0 in col 0 at y = 0
        assertEquals(0, packed.placements[0].xPx)
        assertEquals(0, packed.placements[0].yPx)
        // Item 1 spans 2 cols at y = 173
        assertEquals(0, packed.placements[1].xPx)
        assertEquals(173, packed.placements[1].yPx)
        // Item 2 backfills col 1 side-by-side with Item 0 at y = 0
        assertEquals(173, packed.placements[2].xPx)
        assertEquals(0, packed.placements[2].yPx)
        assertEquals(173 + 168, packed.totalHeightPx)
    }

    @Test
    fun `packTwoColumnGrid does not backfill oversized 1-col widget into too-short open gap`() {
        val specs = listOf(
            computeWidgetPlacementSpec(meta(2, 2), containerWidthDp = 336), // 1 col, 163dp -> gap max height 163dp
            computeWidgetPlacementSpec(meta(4, 2), containerWidthDp = 336), // 2 cols, 168dp at y = 173
            computeWidgetPlacementSpec(meta(3, 4), containerWidthDp = 336)  // 1 col, 217dp -> too tall for 163dp gap
        )
        val packed = packTwoColumnGrid(
            specs = specs,
            singleColWidthPx = 163,
            fullWidthPx = 336,
            columnGapPx = 10,
            rowGapPx = 10,
            dpToPx = { it }
        )

        assertEquals(3, packed.placements.size)
        // Item 2 cannot fit in the 163dp gap, so it places below Item 1 at y = 173 + 168 + 10 = 351
        assertEquals(0, packed.placements[2].xPx)
        assertEquals(351, packed.placements[2].yPx)
        assertEquals(351 + 217, packed.totalHeightPx)
    }
}
