package com.tyejaedon.coverscreenos.ui.widgets

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tyejaedon.coverscreenos.ui.CoverSearchUiTestTags
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.roundToInt

@Composable
fun CoverAppWidgetHostTile(
    widgetIds: List<Int>,
    widgetHostManager: CoverAppWidgetHostManager,
    onRemoveWidget: (Int) -> Unit,
    onAddWidgetRequest: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onConfigureWidget: (Int) -> Unit = {},
    onReorderWidgets: (List<Int>) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(CoverSearchUiTestTags.WIDGET_HOST_PAGE)
    ) {
        val widgetWidthDp = (maxWidth.value - 40f).roundToInt().coerceAtLeast(160)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "widgets_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Widgets",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        onClick = onAddWidgetRequest
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Widget",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (widgetIds.isEmpty()) {
                item(key = "widgets_empty_state") {
                    Column(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .fillParentMaxHeight(0.8f)
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

                        AddWidgetPillButton(onClick = onAddWidgetRequest)
                    }
                }
            } else {
                itemsIndexed(widgetIds, key = { _, id -> id }) { index, widgetId ->
                    val providerInfo = remember(widgetId) {
                        widgetHostManager.getAppWidgetInfo(widgetId)
                    }
                    val widgetHeightDp = remember(providerInfo, density) {
                        CoverAppWidgetHostManager.resolveWidgetHeightDp(providerInfo, density)
                    }
                    val widgetLabel = remember(providerInfo) {
                        runCatching {
                            providerInfo?.loadLabel(context.packageManager)
                        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Widget"
                    }
                    val isReconfigurable = remember(providerInfo) {
                        CoverAppWidgetHostManager.isReconfigurable(providerInfo)
                    }
                    val widgetView = remember(widgetId, widgetWidthDp, widgetHeightDp) {
                        widgetHostManager.createWidgetView(
                            context = context,
                            appWidgetId = widgetId,
                            widthDp = widgetWidthDp,
                            heightDp = widgetHeightDp
                        )
                    }
                    val canMoveUp = index > 0
                    val canMoveDown = index < widgetIds.lastIndex

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .clip(RoundedCornerShape(16.dp))
                            .hazeEffect(state = hazeState)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Slim header acts as a drag-safe handle and hosts widget controls.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp)
                            ) {
                                Text(
                                    text = widgetLabel,
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (widgetIds.size > 1) {
                                    IconButton(
                                        onClick = { onReorderWidgets(moveWidgetId(widgetIds, index, -1)) },
                                        enabled = canMoveUp,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move Widget Up",
                                            tint = Color.White.copy(alpha = if (canMoveUp) 0.7f else 0.22f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onReorderWidgets(moveWidgetId(widgetIds, index, 1)) },
                                        enabled = canMoveDown,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move Widget Down",
                                            tint = Color.White.copy(alpha = if (canMoveDown) 0.7f else 0.22f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (isReconfigurable) {
                                    IconButton(
                                        onClick = { onConfigureWidget(widgetId) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Configure Widget",
                                            tint = Color.White.copy(alpha = 0.55f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveWidget(widgetId) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Widget",
                                        tint = Color.White.copy(alpha = 0.55f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            if (widgetView != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        FrameLayout(ctx).apply {
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                            (widgetView.parent as? ViewGroup)?.removeView(widgetView)
                                            addView(
                                                widgetView,
                                                FrameLayout.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.MATCH_PARENT
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(widgetHeightDp.dp)
                                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                                )
                            } else {
                                Text(
                                    text = "Widget unavailable ($widgetId)",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                                )
                            }
                        }
                    }
                }

                item(key = "widgets_footer_add") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        AddWidgetPillButton(onClick = onAddWidgetRequest)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddWidgetPillButton(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
        onClick = onClick,
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

internal fun moveWidgetId(widgetIds: List<Int>, index: Int, delta: Int): List<Int> {
    val targetIndex = index + delta
    if (index !in widgetIds.indices || targetIndex !in widgetIds.indices || index == targetIndex) {
        return widgetIds
    }
    return widgetIds.toMutableList().apply {
        val item = removeAt(index)
        add(targetIndex, item)
    }
}
