package com.tyejaedon.coverscreenos.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.models.CoverNotificationModel
import com.tyejaedon.coverscreenos.ui.CoverSearchUiTestTags
import com.tyejaedon.coverscreenos.ui.theme.LocalCoverPanelCornerRadius
import com.tyejaedon.coverscreenos.ui.theme.oneui.CoverOneUIType
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiPanel
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

// Upward drag distance on a recent-app card required to dismiss it from Recents history.
private val RECENTS_CARD_DISMISS_THRESHOLD = 48.dp

/**
 * Cover Recents Task Switcher carousel displayed inside the cover launcher overlay.
 *
 * Shows a horizontal carousel of recently active cover apps paired with their latest active
 * notification snippet, allowing tap-to-resume, swipe-up-to-dismiss, and Clear All.
 */
@Composable
internal fun CoverRecentsPanelTile(
    recentApps: List<AppModel>,
    notifications: List<CoverNotificationModel>,
    onAppSelected: (AppModel) -> Unit,
    onDismissApp: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismissPanel: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(LocalCoverPanelCornerRadius.current)
    val dismissThresholdPx = with(LocalDensity.current) { RECENTS_CARD_DISMISS_THRESHOLD.toPx() }
    val latestNotificationByPackage = remember(notifications) {
        notifications
            .sortedByDescending { it.postTime }
            .associateBy { it.packageName }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(CoverSearchUiTestTags.RECENTS_PANEL)
    ) {
        // Scrim behind the task switcher; tapping outside closes the Recents sheet.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissPanel
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .oneUiPanel(shape = cardShape, hazeState = hazeState, blurRadius = 26.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recent Apps",
                    style = CoverOneUIType.sectionTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (recentApps.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .clickable(onClick = onClearAll)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Clear All",
                                style = CoverOneUIType.chipLabel,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissPanel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Recents",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (recentApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent cover apps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = recentApps, key = { it.packageName }) { app ->
                        RecentTaskCard(
                            app = app,
                            notification = latestNotificationByPackage[app.packageName],
                            dismissThresholdPx = dismissThresholdPx,
                            onSelect = { onAppSelected(app) },
                            onDismiss = { onDismissApp(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentTaskCard(
    app: AppModel,
    notification: CoverNotificationModel?,
    dismissThresholdPx: Float,
    onSelect: () -> Unit,
    onDismiss: () -> Unit
) {
    val iconBitmap = rememberPackageIconBitmap(app.packageName)
    var dragOffsetY by remember(app.packageName) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .width(116.dp)
            .height(126.dp)
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            .pointerInput(app.packageName, dismissThresholdPx) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        dragOffsetY = (dragOffsetY + dragAmount).coerceAtMost(0f)
                    },
                    onDragEnd = {
                        if (dragOffsetY <= -dismissThresholdPx) {
                            onDismiss()
                        } else {
                            dragOffsetY = 0f
                        }
                    },
                    onDragCancel = { dragOffsetY = 0f }
                )
            }
            .clickable(onClick = onSelect)
            .padding(8.dp)
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss ${app.name}",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(12.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = app.name,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = app.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            val snippet = notification?.previewText?.takeIf { it.isNotBlank() }
                ?: notification?.title?.takeIf { it.isNotBlank() }
            if (snippet != null) {
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
