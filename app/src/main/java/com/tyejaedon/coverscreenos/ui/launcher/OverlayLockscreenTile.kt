package com.tyejaedon.coverscreenos.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.models.CoverNotificationModel
import com.tyejaedon.coverscreenos.notifications.CoverNotificationListenerService
import com.tyejaedon.coverscreenos.ui.CompactNowPlayingCard
import com.tyejaedon.coverscreenos.ui.CoverSearchUiTestTags
import com.tyejaedon.coverscreenos.ui.ExpandedNowPlayingPanel
import com.tyejaedon.coverscreenos.ui.OneUiDockRow
import com.tyejaedon.coverscreenos.ui.openMediaApplicationFromOverlay
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCallActiveColor
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTextStyles
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiChip
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun LockAndDockTile(
    notifications: List<CoverNotificationModel>,
    timeLabel: String,
    dateLabel: String,
    batteryStatus: BatteryStatusSnapshot?,
    displayPolishSpec: CoverDisplayPolishSpec,
    isDeviceLocked: Boolean,
    isDockVisible: Boolean,
    dockSlots: List<AppModel?>,
    dockSlotFourAllApps: Boolean,
    onOpenAllApps: () -> Unit,
    onNotificationOpen: (CoverNotificationModel) -> Unit,
    onAppSelected: (AppModel) -> Unit,
    onOpenSearch: () -> Unit,
    onRequestUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaNotification = remember(notifications) {
        notifications.filter { it.isMediaNotification }.maxByOrNull { it.postTime }
    }
    var showExpandedMedia by remember(mediaNotification?.notificationKey) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                .padding(
                    top = displayPolishSpec.cutoutTopPadding,
                    start = displayPolishSpec.cutoutStartPadding,
                    end = displayPolishSpec.cutoutEndPadding
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .coverScreenPadding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDeviceLocked) {
                        LockStatusPill(
                            isDeviceLocked = true,
                            minHeight = displayPolishSpec.statusChipMinHeight,
                            onClick = onRequestUnlock
                        )
                    }
                    SearchAffordanceButton(
                        onClick = onOpenSearch,
                        minHeight = displayPolishSpec.statusChipMinHeight,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (batteryStatus != null) {
                        BatteryStatusPill(status = batteryStatus)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(0.95f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        LockTopNotificationHighlights(
                            notifications = notifications,
                            onNotificationOpen = onNotificationOpen,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = timeLabel,
                            style = CoverOSTextStyles.ClockText.copy(fontSize = 28.sp),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                        Text(
                            text = dateLabel,
                            style = CoverOSTextStyles.DateText,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 8.dp)
                    .padding(bottom = displayPolishSpec.dockBottomPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactNowPlayingCard(
                    mediaNotification = mediaNotification,
                    onOpenExpanded = { showExpandedMedia = true },
                    onOpenMediaApp = { openMediaApplicationFromOverlay(context, notifications) },
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
                if (isDockVisible) {
                    OneUiDockRow(
                        dockSlots = dockSlots,
                        onAppSelected = onAppSelected,
                        slotFourAllApps = dockSlotFourAllApps,
                        onOpenAllApps = onOpenAllApps
                    )
                }
            }
        }

        ExpandedNowPlayingPanel(
            mediaNotification = mediaNotification,
            visible = showExpandedMedia,
            onDismiss = { showExpandedMedia = false },
            onLaunchSourceApp = { media ->
                CoverNotificationListenerService.launchNotificationSourceApp(
                    context,
                    media.packageName
                )
            },
            onAction = { media, actionLabel ->
                CoverNotificationListenerService.performNotificationAction(
                    media.notificationKey,
                    actionLabel
                )
            },
            onOpenNotification = { media ->
                CoverNotificationListenerService.openNotificationFromOverlay(context, media)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun LockStatusPill(isDeviceLocked: Boolean, minHeight: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .oneUiChip()
            .heightIn(min = minHeight)
            .clickable(
                onClickLabel = if (isDeviceLocked) "Unlock" else null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isDeviceLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = if (isDeviceLocked) "Locked" else "Unlocked",
            tint = Color.White,
            modifier = Modifier.size(12.dp)
        )
    }
}

/**
 * Small, discrete affordance that opens the search/widget tile via tap.
 */
@Composable
private fun SearchAffordanceButton(onClick: () -> Unit, minHeight: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .testTag(CoverSearchUiTestTags.SEARCH_OPEN_AFFORDANCE_BUTTON)
            .oneUiChip()
            .heightIn(min = minHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Open search",
            tint = Color.White,
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
private fun LockTopNotificationHighlights(
    notifications: List<CoverNotificationModel>,
    onNotificationOpen: (CoverNotificationModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val callNotifications = remember(notifications) {
        notifications.filter { it.isOngoing && isCallNotification(it) }.sortedByDescending { it.postTime }.take(3)
    }
    if (callNotifications.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        callNotifications.forEach { notification ->
            val iconBitmap = rememberPackageIconBitmap(notification.packageName)
            Surface(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable { onNotificationOpen(notification) },
                color = CoverOSCallActiveColor.copy(alpha = 0.6f),
                shape = CircleShape
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun isCallNotification(notification: CoverNotificationModel): Boolean {
    val text = "${notification.title} ${notification.previewText} ${notification.packageName}".lowercase(Locale.getDefault())
    return text.contains("call") || text.contains("dial") || text.contains("phone")
}

internal fun currentCoverTime(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

internal fun currentCoverDate(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
