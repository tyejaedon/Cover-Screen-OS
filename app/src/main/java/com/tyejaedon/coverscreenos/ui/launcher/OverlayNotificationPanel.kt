package com.tyejaedon.coverscreenos.ui

import com.tyejaedon.coverscreenos.ui.launcher.rememberPackageIconBitmap
import com.tyejaedon.coverscreenos.ui.launcher.rememberPackageLabel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyejaedon.coverscreenos.models.CoverNotificationModel
import com.tyejaedon.coverscreenos.services.notifications.CoverNotificationListenerService
import com.tyejaedon.coverscreenos.ui.theme.CoverOSDestructiveColor
import com.tyejaedon.coverscreenos.ui.theme.LocalCoverPanelCornerRadius
import com.tyejaedon.coverscreenos.ui.theme.oneui.CoverOneUIType
import com.tyejaedon.coverscreenos.ui.theme.oneui.CoverOneUITokens
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiChip
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiIconSquircle
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiPanel
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiSpring
import com.tyejaedon.coverscreenos.ui.theme.coverScreenContentPadding
import dev.chrisbanes.haze.HazeState

private const val NOTIFICATION_RECENT_WINDOW_MS = 60L * 60L * 1000L // "Now" vs "Earlier" boundary.

private data class NotificationProviderGroup(
    val packageName: String,
    val notifications: List<CoverNotificationModel>
)

private fun groupNotificationsByProvider(notifications: List<CoverNotificationModel>): List<NotificationProviderGroup> {
    return notifications
        .sortedByDescending { it.postTime }
        .groupBy { it.packageName }
        .map { (pkg, list) -> NotificationProviderGroup(pkg, list.sortedByDescending { it.postTime }) }
        .sortedByDescending { it.notifications.firstOrNull()?.postTime ?: 0L }
}

@Composable
private fun OngoingCollapseToggle(collapseOngoing: Boolean, ongoingCount: Int, onToggle: () -> Unit) {
    val label = if (collapseOngoing) "Show ongoing ($ongoingCount)" else "Hide ongoing"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(
            modifier = Modifier
                .oneUiChip()
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = CoverOneUIType.chipLabel,
                color = Color.White.copy(alpha = 0.9f)
            )
            Icon(
                imageVector = if (collapseOngoing) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun NotificationSectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = CoverOneUIType.sectionTitle.copy(fontSize = 14.sp),
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun NotificationClearAllButton(
    enabled: Boolean,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text = "Clear all",
            style = CoverOneUIType.chipLabel,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
            modifier = Modifier
                .oneUiChip()
                .clickable(enabled = enabled, onClick = onClearAll)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotificationsPanelTile(
    notifications: List<CoverNotificationModel>,
    onNotificationOpen: (CoverNotificationModel) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    var collapseOngoing by remember { mutableStateOf(false) }

    val ongoingNotifications = remember(notifications) {
        notifications.filter { it.isOngoing || !it.isClearable }
    }
    val importantNotifications = remember(notifications) {
        notifications.filterNot { it.isOngoing || !it.isClearable }
    }
    val ongoingGroupedNotifications = remember(ongoingNotifications) {
        groupNotificationsByProvider(ongoingNotifications)
    }
    val importantGroupedNotifications = remember(importantNotifications) {
        groupNotificationsByProvider(importantNotifications)
    }
    val nowGroups = remember(importantGroupedNotifications) {
        importantGroupedNotifications.filter { isRecentGroup(it) }
    }
    val earlierGroups = remember(importantGroupedNotifications) {
        importantGroupedNotifications.filterNot { isRecentGroup(it) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsNone,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "No new notifications",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = coverScreenContentPadding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (notifications.any { it.isClearable }) {
                item(key = "clear_all") {
                    NotificationClearAllButton(
                        enabled = true,
                        onClearAll = {
                            CoverNotificationListenerService.clearAllNotificationsFromOverlay()
                        },
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            if (ongoingNotifications.isNotEmpty()) {
                item(key = "ongoing_toggle") {
                    OngoingCollapseToggle(
                        collapseOngoing = collapseOngoing,
                        ongoingCount = ongoingNotifications.size,
                        onToggle = { collapseOngoing = !collapseOngoing }
                    )
                }

                if (!collapseOngoing) {
                    items(
                        items = ongoingGroupedNotifications,
                        key = { "ongoing_${it.packageName}" }
                    ) { group ->
                        NotificationProviderGroupCard(
                            group = group,
                            onNotificationOpen = onNotificationOpen,
                            hazeState = hazeState
                        )
                    }
                }
            }

            if (nowGroups.isNotEmpty()) {
                stickyHeader(key = "header_now") {
                    NotificationSectionHeader("Now")
                }
                items(
                    items = nowGroups,
                    key = { "important_${it.packageName}" }
                ) { group ->
                    NotificationProviderGroupCard(
                        group = group,
                        onNotificationOpen = onNotificationOpen,
                        hazeState = hazeState
                    )
                }
            }

            if (earlierGroups.isNotEmpty()) {
                stickyHeader(key = "header_earlier") {
                    NotificationSectionHeader("Earlier")
                }
                items(
                    items = earlierGroups,
                    key = { "important_earlier_${it.packageName}" }
                ) { group ->
                    NotificationProviderGroupCard(
                        group = group,
                        onNotificationOpen = onNotificationOpen,
                        hazeState = hazeState
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

private fun isRecentGroup(group: NotificationProviderGroup): Boolean {
    val latestPostTime = group.notifications.maxOfOrNull { it.postTime } ?: return true
    return (System.currentTimeMillis() - latestPostTime) <= NOTIFICATION_RECENT_WINDOW_MS
}

@Composable
private fun NotificationProviderGroupCard(
    group: NotificationProviderGroup,
    onNotificationOpen: (CoverNotificationModel) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    val cardShape = RoundedCornerShape(LocalCoverPanelCornerRadius.current)
    val providerIcon = rememberPackageIconBitmap(group.packageName)
    val providerLabel = rememberPackageLabel(group.packageName)
    var isExpanded by remember(group.packageName) { mutableStateOf(false) }
    val providerNotifications = group.notifications

    val visibleNotifications = if (isExpanded || providerNotifications.size <= 2) {
        providerNotifications
    } else {
        providerNotifications.take(2)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .oneUiPanel(shape = cardShape, hazeState = hazeState)
            .animateContentSize(animationSpec = oneUiSpring()),
        color = Color.Transparent,
        shape = cardShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .oneUiIconSquircle(12.dp)
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (providerIcon != null) {
                        androidx.compose.foundation.Image(
                            bitmap = providerIcon,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Text(
                    text = providerLabel,
                    style = CoverOneUIType.tileTitle,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (providerNotifications.size > 1) {
                    Text(
                        text = providerNotifications.size.toString(),
                        style = CoverOneUIType.chipLabel,
                        color = Color.White,
                        modifier = Modifier
                            .oneUiChip()
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                visibleNotifications.forEachIndexed { index, notification ->
                    NotificationGroupRow(
                        notification = notification,
                        onOpen = { onNotificationOpen(notification) },
                        onDismiss = {
                            CoverNotificationListenerService.dismissNotificationFromOverlay(
                                notification.notificationKey
                            )
                        }
                    )

                    if (index < visibleNotifications.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.05f))
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = providerNotifications.size > 2,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val hiddenCount = (providerNotifications.size - visibleNotifications.size).coerceAtLeast(0)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isExpanded) "Show less" else "Show $hiddenCount more",
                        style = CoverOneUIType.chipLabel,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .oneUiChip()
                            .clickable { isExpanded = !isExpanded }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationGroupRow(
    notification: CoverNotificationModel,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        initialValue = SwipeToDismissBoxValue.Settled,
        positionalThreshold = { totalDistance -> totalDistance * 0.6f }
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
            onDismiss()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        enableDismissFromStartToEnd = notification.isClearable,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> CoverOSDestructiveColor.copy(alpha = 0.5f)
                    else -> Color.Transparent
                },
                animationSpec = oneUiSpring(),
                label = "dismissColor"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(CoverOneUITokens.radiusChip))
                    .background(color)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Dismiss",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CoverOneUITokens.radiusChip))
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = notification.title,
                        style = CoverOneUIType.tileTitle,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = notification.previewText.ifBlank { "Tap to open" },
                        style = CoverOneUIType.tileSubtitle,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (notification.isOngoing || !notification.isClearable) {
                    Box(
                        modifier = Modifier
                            .oneUiChip()
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Ongoing",
                            style = CoverOneUIType.chipLabel,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    )
}

