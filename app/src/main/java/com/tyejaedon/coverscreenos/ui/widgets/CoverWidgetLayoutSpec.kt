package com.tyejaedon.coverscreenos.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import kotlin.math.roundToInt

/** Minimum tile height in dp so compact 1-row widgets remain legible on cover displays. */
internal const val WIDGET_MIN_HEIGHT_DP = 82
/** Default maximum slot height in dp to prevent tall widgets from monopolizing the viewport. */
internal const val WIDGET_DEFAULT_MAX_HEIGHT_DP = 252
/** Horizontal and vertical gutter spacing in dp between adjacent widget tiles. */
internal const val WIDGET_GRID_GAP_DP = 10
// Fully resizable widgets occupying >=85% of both slot dimensions snap cleanly to the tile edges.
private const val SNAP_TO_FILL_THRESHOLD = 0.85f

/**
 * Density-normalized sizing and resizability metadata extracted from [AppWidgetProviderInfo].
 */
internal data class WidgetGridMetadata(
    val targetCellWidth: Int,
    val targetCellHeight: Int,
    val minWidthDp: Int,
    val minHeightDp: Int,
    val maxResizeWidthDp: Int,
    val maxResizeHeightDp: Int,
    val resizeMode: Int
)

/**
 * Resolved grid slot dimensions and inner [android.appwidget.AppWidgetHostView] viewport sizes
 * for a single widget on the cover screen.
 *
 * @property colSpan Number of grid columns occupied (`1` or `2`).
 * @property slotWidthDp Outer card width in dp.
 * @property slotHeightDp Outer card height in dp.
 * @property widgetWidthDp Inner hosted widget width in dp (respecting resize and aspect constraints).
 * @property widgetHeightDp Inner hosted widget height in dp.
 */
internal data class WidgetPlacementSpec(
    val colSpan: Int,
    val slotWidthDp: Int,
    val slotHeightDp: Int,
    val widgetWidthDp: Int,
    val widgetHeightDp: Int
)

/** Pixel-coordinate rectangle assigned to a single widget within [PackedTwoColumnGrid]. */
internal data class GridItemPlacement(
    val xPx: Int,
    val yPx: Int,
    val widthPx: Int,
    val heightPx: Int
)

/** Result of packing widget slots into a two-column vertical masonry grid. */
internal data class PackedTwoColumnGrid(
    val placements: List<GridItemPlacement>,
    val totalHeightPx: Int
)

/** Unfilled vertical pocket left behind in a shorter column above a full-width 2-column row. */
private data class OpenColumnGap(
    val col: Int,
    var startY: Int,
    val maxBottomY: Int
)

/**
 * Converts [AppWidgetProviderInfo] pixel dimensions (`minWidth`, `maxResizeWidth`, etc.) into
 * dp-scaled [WidgetGridMetadata], falling back to a default 4x2 resizable footprint if null.
 */
internal fun AppWidgetProviderInfo?.toWidgetGridMetadata(density: Float): WidgetGridMetadata {
    if (this == null) {
        return WidgetGridMetadata(
            targetCellWidth = 4,
            targetCellHeight = 2,
            minWidthDp = 0,
            minHeightDp = 0,
            maxResizeWidthDp = 0,
            maxResizeHeightDp = 0,
            resizeMode = AppWidgetProviderInfo.RESIZE_BOTH
        )
    }
    val safeDensity = density.coerceAtLeast(1f)
    // AppWidgetProviderInfo stores pixel dimensions for the current display density, whereas targetCell* are grid cell counts.
    return WidgetGridMetadata(
        targetCellWidth = targetCellWidth,
        targetCellHeight = targetCellHeight,
        minWidthDp = (minWidth / safeDensity).roundToInt(),
        minHeightDp = (minHeight / safeDensity).roundToInt(),
        maxResizeWidthDp = (maxResizeWidth / safeDensity).roundToInt(),
        maxResizeHeightDp = (maxResizeHeight / safeDensity).roundToInt(),
        resizeMode = resizeMode
    )
}

/**
 * Computes the column span, outer tile slot dimensions, and fitted inner widget viewport for
 * a widget given [containerWidthDp] and [maxSlotHeightDp].
 */
internal fun computeWidgetPlacementSpec(
    meta: WidgetGridMetadata,
    containerWidthDp: Int,
    maxSlotHeightDp: Int = WIDGET_DEFAULT_MAX_HEIGHT_DP,
    columnGapDp: Int = WIDGET_GRID_GAP_DP
): WidgetPlacementSpec {
    // Clamped grid container width and width of one half-row column after subtracting the center gutter.
    val safeContainerWidth = containerWidthDp.coerceAtLeast(180)
    val singleColumnWidth = ((safeContainerWidth - columnGapDp) / 2).coerceAtLeast(80)

    // Resolved span (1 or 2 columns), outer card width, target aspect ratio, and clamped slot height.
    val colSpan = resolveColumnSpan(meta, safeContainerWidth)
    val slotWidthDp = if (colSpan == 1) singleColumnWidth else safeContainerWidth
    val ratio = resolveAspectRatio(meta, colSpan)
    val slotHeightDp = (slotWidthDp / ratio)
        .roundToInt()
        .coerceIn(WIDGET_MIN_HEIGHT_DP, maxSlotHeightDp.coerceAtLeast(WIDGET_MIN_HEIGHT_DP))

    // Final inner viewport bounds honoring widget resizability and max-resize caps.
    val (widgetWidthDp, widgetHeightDp) = fitWidgetViewport(
        meta = meta,
        ratio = ratio,
        slotWidthDp = slotWidthDp,
        slotHeightDp = slotHeightDp
    )

    return WidgetPlacementSpec(
        colSpan = colSpan,
        slotWidthDp = slotWidthDp,
        slotHeightDp = slotHeightDp,
        widgetWidthDp = widgetWidthDp,
        widgetHeightDp = widgetHeightDp
    )
}

/**
 * Decides whether a widget occupies 1 or 2 columns using Android 12+ `targetCell*` hints first,
 * then falling back to comparing `minWidthDp` against 55% of [containerWidthDp].
 */
private fun resolveColumnSpan(meta: WidgetGridMetadata, containerWidthDp: Int): Int {
    if (meta.targetCellWidth > 0) {
        return when {
            meta.targetCellWidth <= 2 -> 1
            // 3x4+ portrait widgets overflow the cover screen height if given full width.
            meta.targetCellWidth == 3 && meta.targetCellHeight >= 4 -> 1
            else -> 2
        }
    }
    if (meta.minWidthDp > 0 && meta.minWidthDp < (containerWidthDp * 0.55f)) {
        return 1
    }
    return 2
}

/**
 * Determines the widget's target width-to-height aspect ratio clamped to `[0.4, 4.5]` so extreme
 * phone-sized ratios do not collapse into unusable slivers on compact cover displays.
 */
private fun resolveAspectRatio(meta: WidgetGridMetadata, colSpan: Int): Float {
    if (meta.targetCellWidth > 0 && meta.targetCellHeight > 0) {
        return (meta.targetCellWidth.toFloat() / meta.targetCellHeight.toFloat()).coerceIn(0.4f, 4.5f)
    }
    if (meta.minWidthDp > 0 && meta.minHeightDp > 0) {
        return (meta.minWidthDp.toFloat() / meta.minHeightDp.toFloat()).coerceIn(0.4f, 4.5f)
    }
    return if (colSpan == 1) 1f else 2f
}

/**
 * Fits the inner widget viewport inside `[slotWidthDp, slotHeightDp]` while honoring
 * [AppWidgetProviderInfo.resizeMode] bitmasks, `maxResize*` bounds, and aspect ratio.
 */
private fun fitWidgetViewport(
    meta: WidgetGridMetadata,
    ratio: Float,
    slotWidthDp: Int,
    slotHeightDp: Int
): Pair<Int, Int> {
    // Axis resizability flags decoded from AppWidgetProviderInfo.resizeMode.
    val isResizableH = (meta.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
    val isResizableV = (meta.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0

    // Maximum permitted viewport bounds after honoring fixed-size axes and maxResize caps.
    var maxW = slotWidthDp
    var maxH = slotHeightDp

    if (meta.resizeMode == AppWidgetProviderInfo.RESIZE_NONE) {
        if (meta.minWidthDp > 0) maxW = minOf(maxW, meta.minWidthDp)
        if (meta.minHeightDp > 0) maxH = minOf(maxH, meta.minHeightDp)
    } else {
        if (!isResizableH && meta.minWidthDp > 0) maxW = minOf(maxW, meta.minWidthDp)
        if (!isResizableV && meta.minHeightDp > 0) maxH = minOf(maxH, meta.minHeightDp)
        if (meta.maxResizeWidthDp > 0) maxW = minOf(maxW, meta.maxResizeWidthDp)
        if (meta.maxResizeHeightDp > 0) maxH = minOf(maxH, meta.maxResizeHeightDp)
    }

    maxW = maxW.coerceAtLeast(1)
    maxH = maxH.coerceAtLeast(1)

    // Letterbox or pillarbox inside [maxW, maxH] to preserve the provider's aspect ratio.
    val fittedW: Int
    val fittedH: Int
    if (maxW.toFloat() / maxH.toFloat() > ratio) {
        fittedH = maxH
        fittedW = (maxH * ratio).roundToInt().coerceIn(1, maxW)
    } else {
        fittedW = maxW
        fittedH = (maxW / ratio).roundToInt().coerceIn(1, maxH)
    }

    // True when a fully resizable widget fills >=85% of both slot dimensions, allowing clean edge snapping.
    val canSnapToSlot = isResizableH &&
        isResizableV &&
        maxW == slotWidthDp &&
        maxH == slotHeightDp &&
        fittedW >= (slotWidthDp * SNAP_TO_FILL_THRESHOLD) &&
        fittedH >= (slotHeightDp * SNAP_TO_FILL_THRESHOLD)

    return if (canSnapToSlot) {
        slotWidthDp to slotHeightDp
    } else {
        fittedW to fittedH
    }
}

/**
 * Packs a list of [WidgetPlacementSpec] entries into a two-column masonry grid.
 *
 * Full-width (`colSpan >= 2`) widgets synchronize both column Y cursors before spanning across
 * the container and record any leftover vertical pocket in the shorter column; single-column
 * widgets first check those open pockets for a fit before dropping into whichever active column
 * is currently shorter.
 */
internal fun packTwoColumnGrid(
    specs: List<WidgetPlacementSpec>,
    singleColWidthPx: Int,
    fullWidthPx: Int,
    columnGapPx: Int,
    rowGapPx: Int,
    dpToPx: (Int) -> Int
): PackedTwoColumnGrid {
    if (specs.isEmpty()) {
        return PackedTwoColumnGrid(emptyList(), 0)
    }

    // Running next-available Y cursor (in pixels) for column 0 (left) and column 1 (right).
    val colHeights = intArrayOf(0, 0)
    // Output pixel bounds for each widget in the same order as specs.
    val placements = ArrayList<GridItemPlacement>(specs.size)
    // Open single-column pockets above earlier 2-column barriers that later 1-col widgets can backfill.
    val openGaps = mutableListOf<OpenColumnGap>()

    for (spec in specs) {
        val heightPx = dpToPx(spec.slotHeightDp).coerceAtLeast(1)
        if (spec.colSpan >= 2) {
            // Full-width rows start below whichever column currently extends further down.
            val y = maxOf(colHeights[0], colHeights[1])
            val shorterCol = if (colHeights[0] <= colHeights[1]) 0 else 1
            val gapStartY = colHeights[shorterCol]
            val gapMaxBottomY = y - rowGapPx
            if (gapMaxBottomY > gapStartY) {
                openGaps.add(
                    OpenColumnGap(
                        col = shorterCol,
                        startY = gapStartY,
                        maxBottomY = gapMaxBottomY
                    )
                )
            }
            placements.add(
                GridItemPlacement(
                    xPx = 0,
                    yPx = y,
                    widthPx = fullWidthPx,
                    heightPx = heightPx
                )
            )
            val nextY = y + heightPx + rowGapPx
            colHeights[0] = nextY
            colHeights[1] = nextY
        } else {
            // First check earlier open single-column gaps above 2-col widgets before extending the grid.
            val gapIndex = openGaps.indexOfFirst { gap ->
                heightPx <= (gap.maxBottomY - gap.startY)
            }
            if (gapIndex >= 0) {
                val gap = openGaps[gapIndex]
                val x = gap.col * (singleColWidthPx + columnGapPx)
                val y = gap.startY
                placements.add(
                    GridItemPlacement(
                        xPx = x,
                        yPx = y,
                        widthPx = singleColWidthPx,
                        heightPx = heightPx
                    )
                )
                gap.startY = y + heightPx + rowGapPx
                if (gap.startY >= gap.maxBottomY) {
                    openGaps.removeAt(gapIndex)
                }
            } else {
                // Drop single-column cards into the shorter active column to keep the waterfall balanced.
                val targetCol = if (colHeights[0] <= colHeights[1]) 0 else 1
                val x = targetCol * (singleColWidthPx + columnGapPx)
                val y = colHeights[targetCol]
                placements.add(
                    GridItemPlacement(
                        xPx = x,
                        yPx = y,
                        widthPx = singleColWidthPx,
                        heightPx = heightPx
                    )
                )
                colHeights[targetCol] = y + heightPx + rowGapPx
            }
        }
    }

    // Subtract the trailing row gap added after the last item so totalHeightPx wraps tightly.
    val totalHeightPx = (maxOf(colHeights[0], colHeights[1]) - rowGapPx).coerceAtLeast(0)
    return PackedTwoColumnGrid(
        placements = placements,
        totalHeightPx = totalHeightPx
    )
}
