package com.tyejaedon.coverscreenos.ui.widgets

import android.appwidget.AppWidgetHostView
import android.graphics.Outline
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tyejaedon.coverscreenos.ui.CoverSearchUiTestTags
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.roundToInt

private val WIDGET_CORNER_RADIUS = 18.dp

/**
 * Scrollable cover-screen tile hosting user-pinned Android `AppWidget` views in a two-column
 * masonry grid ([CoverWidgetTwoColumnGrid]) over a Haze glassmorphic backdrop.
 *
 * Bridges legacy [AppWidgetHostView] instances into Compose via [AndroidView], clips them with a
 * hardware-accelerated [ViewOutlineProvider], and updates each widget's responsive dp size bounds
 * via [AppWidgetHostView.updateAppWidgetSize].
 */
@Composable
fun CoverAppWidgetHostTile(
    widgetIds: List<Int>,
    widgetHostManager: CoverAppWidgetHostManager,
    onRemoveWidget: (Int) -> Unit,
    onAddWidgetRequest: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(CoverSearchUiTestTags.WIDGET_HOST_PAGE)
    ) {
        if (widgetIds.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = "Widgets",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "No Widgets Added",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Add weather, calendar, and media widgets to your cover screen.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onAddWidgetRequest,
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = "Add Widget",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            val configuration = LocalConfiguration.current
            // Account for 12dp horizontal grid padding on each side and cap widget height at ~68% of cover screen.
            val availableWidthDp = (configuration.screenWidthDp - 24).coerceAtLeast(180)
            val maxSlotHeightDp = (configuration.screenHeightDp * 0.68f)
                .roundToInt()
                .coerceIn(160, WIDGET_DEFAULT_MAX_HEIGHT_DP)
            val cornerShape = RoundedCornerShape(WIDGET_CORNER_RADIUS)
            val cornerRadiusPx = with(density) { WIDGET_CORNER_RADIUS.toPx() }
            val specs = remember(widgetIds, availableWidthDp, maxSlotHeightDp, density.density) {
                widgetIds.map { widgetId ->
                    val meta = widgetHostManager.getAppWidgetInfo(widgetId)
                        .toWidgetGridMetadata(density.density)
                    computeWidgetPlacementSpec(
                        meta = meta,
                        containerWidthDp = availableWidthDp,
                        maxSlotHeightDp = maxSlotHeightDp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(WIDGET_GRID_GAP_DP.dp)
            ) {
                CoverWidgetTwoColumnGrid(specs = specs) {
                    widgetIds.forEachIndexed { index, widgetId ->
                        val spec = specs[index]
                        val widgetView = remember(widgetId, widgetHostManager) {
                            widgetHostManager.createWidgetView(context, widgetId)
                        }

                        Surface(
                            shape = cornerShape,
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier
                                .clip(cornerShape)
                                .hazeEffect(state = hazeState)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (widgetView != null) {
                                    AndroidView(
                                        factory = { ctx ->
                                            FrameLayout(ctx).apply {
                                                layoutParams = ViewGroup.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.MATCH_PARENT
                                                )
                                                // Clip RemoteViews at the Android View layer because Compose clip alone does not clip native children cleanly on all API levels.
                                                clipToOutline = true
                                                outlineProvider = RoundedWidgetOutlineProvider(cornerRadiusPx)
                                                // Detach from any previous wrapper if Compose recreates the AndroidView node.
                                                (widgetView.parent as? ViewGroup)?.removeView(widgetView)
                                                addView(widgetView)
                                            }
                                        },
                                        update = { container ->
                                            val provider = container.outlineProvider as? RoundedWidgetOutlineProvider
                                            if (provider == null || provider.radiusPx != cornerRadiusPx) {
                                                container.outlineProvider = RoundedWidgetOutlineProvider(cornerRadiusPx)
                                                container.clipToOutline = true
                                                container.invalidateOutline()
                                            }
                                            if (widgetView is AppWidgetHostView) {
                                                val targetSize = SizeF(
                                                    spec.widgetWidthDp.toFloat(),
                                                    spec.widgetHeightDp.toFloat()
                                                )
                                                // Deduplicate size updates via View.tag to avoid spamming AppWidgetService IPC on recomposition.
                                                if (widgetView.tag != targetSize) {
                                                    widgetView.tag = targetSize
                                                    widgetView.updateAppWidgetSize(Bundle(), listOf(targetSize))
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(spec.widgetWidthDp.dp, spec.widgetHeightDp.dp)
                                            .clip(cornerShape)
                                    )
                                } else {
                                    Text(
                                        text = "Widget unavailable ($widgetId)",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onRemoveWidget(widgetId) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(32.dp)
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Widget",
                                        tint = Color.White.copy(alpha = 0.55f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        onClick = onAddWidgetRequest,
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "Add Widget",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Compose [Layout] implementing a two-column masonry grid driven by [packTwoColumnGrid].
 *
 * Each child composable is measured with exact [Constraints.fixed] bounds and positioned using
 * RTL-aware [placeRelative] coordinates.
 */
@Composable
private fun CoverWidgetTwoColumnGrid(
    specs: List<WidgetPlacementSpec>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val fullWidthPx = constraints.maxWidth
        val gapPx = WIDGET_GRID_GAP_DP.dp.roundToPx()
        val singleColWidthPx = ((fullWidthPx - gapPx) / 2).coerceAtLeast(1)
        val packed = packTwoColumnGrid(
            specs = specs,
            singleColWidthPx = singleColWidthPx,
            fullWidthPx = fullWidthPx,
            columnGapPx = gapPx,
            rowGapPx = gapPx,
            dpToPx = { dp -> dp.dp.roundToPx() }
        )

        val placeables = measurables.mapIndexed { index, measurable ->
            val placement = packed.placements[index]
            measurable.measure(Constraints.fixed(placement.widthPx, placement.heightPx))
        }

        layout(fullWidthPx, packed.totalHeightPx) {
            placeables.forEachIndexed { index, placeable ->
                val placement = packed.placements[index]
                placeable.placeRelative(placement.xPx, placement.yPx)
            }
        }
    }
}

/** Clips legacy `AppWidgetHostView` containers to a rounded rectangle matching Compose tiles. */
private class RoundedWidgetOutlineProvider(
    val radiusPx: Float
) : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
    }
}
