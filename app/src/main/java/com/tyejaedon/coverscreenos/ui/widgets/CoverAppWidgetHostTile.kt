package com.tyejaedon.coverscreenos.ui.widgets

import android.appwidget.AppWidgetHostView
import android.graphics.Outline
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tyejaedon.coverscreenos.ui.CoverSearchUiTestTags
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.roundToInt

private const val WIDGET_MIN_HEIGHT_DP = 84
private const val WIDGET_MAX_HEIGHT_DP = 160
private val WIDGET_CORNER_RADIUS = 18.dp

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
            // Populated widget container
            val availableWidthDp = (LocalConfiguration.current.screenWidthDp - 24).coerceAtLeast(180)
            val cornerShape = RoundedCornerShape(WIDGET_CORNER_RADIUS)
            val cornerRadiusPx = with(density) { WIDGET_CORNER_RADIUS.toPx() }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(widgetIds, key = { it }) { widgetId ->
                    val widgetView = remember(widgetId, widgetHostManager) {
                        widgetHostManager.createWidgetView(context, widgetId)?.also { view ->
                            if (view is AppWidgetHostView) {
                                val rawMinHeightDp = ((view.appWidgetInfo?.minHeight ?: 0) / density.density)
                                    .roundToInt()
                                val targetHeightDp = if (rawMinHeightDp > 0) {
                                    rawMinHeightDp.coerceIn(WIDGET_MIN_HEIGHT_DP, WIDGET_MAX_HEIGHT_DP)
                                } else {
                                    120
                                }
                                view.updateAppWidgetSize(
                                    Bundle(),
                                    availableWidthDp,
                                    targetHeightDp,
                                    availableWidthDp,
                                    WIDGET_MAX_HEIGHT_DP
                                )
                            }
                        }
                    }

                    Surface(
                        shape = cornerShape,
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = WIDGET_MAX_HEIGHT_DP.dp)
                            .clip(cornerShape)
                            .hazeEffect(state = hazeState)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (widgetView != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        FrameLayout(ctx).apply {
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                            )
                                            clipToOutline = true
                                            outlineProvider = RoundedWidgetOutlineProvider(cornerRadiusPx)
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
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = WIDGET_MAX_HEIGHT_DP.dp)
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

                            // Remove affordance
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

                item(key = "add_widget_footer") {
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
}

private class RoundedWidgetOutlineProvider(
    val radiusPx: Float
) : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
    }
}
