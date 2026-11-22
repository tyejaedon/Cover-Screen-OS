package com.tyejaedon.coverscreenos.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import kotlin.math.roundToInt

internal const val WIDGET_MIN_HEIGHT_DP = 82
internal const val WIDGET_DEFAULT_MAX_HEIGHT_DP = 252
internal const val WIDGET_GRID_GAP_DP = 10
private const val SNAP_TO_FILL_THRESHOLD = 0.85f

internal data class WidgetGridMetadata(
    val targetCellWidth: Int,
    val targetCellHeight: Int,
    val minWidthDp: Int,
    val minHeightDp: Int,
    val maxResizeWidthDp: Int,
    val maxResizeHeightDp: Int,
    val resizeMode: Int
)

internal data class WidgetPlacementSpec(
    val colSpan: Int,
    val slotWidthDp: Int,
    val slotHeightDp: Int,
    val widgetWidthDp: Int,
    val widgetHeightDp: Int
)

internal data class GridItemPlacement(
    val xPx: Int,
    val yPx: Int,
    val widthPx: Int,
    val heightPx: Int
)

internal data class PackedTwoColumnGrid(
    val placements: List<GridItemPlacement>,
    val totalHeightPx: Int
)

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

internal fun computeWidgetPlacementSpec(
    meta: WidgetGridMetadata,
    containerWidthDp: Int,
    maxSlotHeightDp: Int = WIDGET_DEFAULT_MAX_HEIGHT_DP,
    columnGapDp: Int = WIDGET_GRID_GAP_DP
): WidgetPlacementSpec {
    val safeContainerWidth = containerWidthDp.coerceAtLeast(180)
    val singleColumnWidth = ((safeContainerWidth - columnGapDp) / 2).coerceAtLeast(80)

    val colSpan = resolveColumnSpan(meta, safeContainerWidth)
    val slotWidthDp = if (colSpan == 1) singleColumnWidth else safeContainerWidth
    val ratio = resolveAspectRatio(meta, colSpan)
    val slotHeightDp = (slotWidthDp / ratio)
        .roundToInt()
        .coerceIn(WIDGET_MIN_HEIGHT_DP, maxSlotHeightDp.coerceAtLeast(WIDGET_MIN_HEIGHT_DP))

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

private fun resolveAspectRatio(meta: WidgetGridMetadata, colSpan: Int): Float {
    if (meta.targetCellWidth > 0 && meta.targetCellHeight > 0) {
        return (meta.targetCellWidth.toFloat() / meta.targetCellHeight.toFloat()).coerceIn(0.4f, 4.5f)
    }
    if (meta.minWidthDp > 0 && meta.minHeightDp > 0) {
        return (meta.minWidthDp.toFloat() / meta.minHeightDp.toFloat()).coerceIn(0.4f, 4.5f)
    }
    return if (colSpan == 1) 1f else 2f
}

private fun fitWidgetViewport(
    meta: WidgetGridMetadata,
    ratio: Float,
    slotWidthDp: Int,
    slotHeightDp: Int
): Pair<Int, Int> {
    val isResizableH = (meta.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
    val isResizableV = (meta.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0

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

    val fittedW: Int
    val fittedH: Int
    if (maxW.toFloat() / maxH.toFloat() > ratio) {
        fittedH = maxH
        fittedW = (maxH * ratio).roundToInt().coerceIn(1, maxW)
    } else {
        fittedW = maxW
        fittedH = (maxW / ratio).roundToInt().coerceIn(1, maxH)
    }

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

    val colHeights = intArrayOf(0, 0)
    val placements = ArrayList<GridItemPlacement>(specs.size)

    for (spec in specs) {
        val heightPx = dpToPx(spec.slotHeightDp).coerceAtLeast(1)
        if (spec.colSpan >= 2) {
            val y = maxOf(colHeights[0], colHeights[1])
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

    val totalHeightPx = (maxOf(colHeights[0], colHeights[1]) - rowGapPx).coerceAtLeast(0)
    return PackedTwoColumnGrid(
        placements = placements,
        totalHeightPx = totalHeightPx
    )
}
