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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.models.CoverMediaTransportAction
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
import com.tyejaedon.coverscreenos.ui.theme.oneui.CoverOneUITokens
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiChip
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Primary lockscreen and home page rendered at the center pager slot (`LOCK_PAGER_PAGE_INDEX`).
 *
 * Combines the top lock/battery status bar, highlighted notification badges, clock/date readout,
 * search/widget trigger button, compact media card, optional One UI dock row, and an expandable
 * modal [ExpandedNowPlayingPanel] routed to [CoverNotificationListenerService] media transport
 * controls.
 */
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
    // Pick the most recent active media session to drive both the compact card and expanded panel.
    val mediaNotification = remember(notifications) {
        notifications.filter { it.isMediaNotification }.maxByOrNull { it.postTime }
    }
    // Reset expanded state whenever the active media session changes or disappears.
    var showExpandedMedia by remember(mediaNotification?.notificationKey) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(
                    start = displayPolishSpec.cutoutStartPadding,
                    end = displayPolishSpec.cutoutEndPadding
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .coverScreenPadding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isDeviceLocked || batteryStatus != null) {
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
                        Spacer(modifier = Modifier.weight(1f))
                        if (batteryStatus != null) {
                            BatteryStatusPill(status = batteryStatus)
                        }
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

                    SearchAffordanceButton(
                        onClick = onOpenSearch,
                        minHeight = displayPolishSpec.statusChipMinHeight
                    )
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
            onAction = { media, action ->
                CoverNotificationListenerService.performMediaTransportAction(
                    media.notificationKey,
                    action
                )
            },
            onOpenNotification = { media ->
                CoverNotificationListenerService.openNotificationFromOverlay(context, media)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private val LOCK_PILL_DIM_RGB_COLORS = listOf(
    Color(0xFFD66A78),
    Color(0xFF5BB58B),
    Color(0xFF698EE5),
    Color(0xFF9374CC),
    Color(0xFFD66A78)
)

/**
 * Draws a subtle two-pass RGB gradient halo and crisp inner rim behind a chip via [drawBehind].
 */
private fun Modifier.dimRgbGlow(): Modifier = this.drawBehind {
    val cornerPx = CoverOneUITokens.radiusChip.toPx()
    val start = Offset(0f, 0f)
    val end = Offset(size.width, size.height)
    val outerColors = LOCK_PILL_DIM_RGB_COLORS.map { it.copy(alpha = 0.16f) }
    val innerColors = LOCK_PILL_DIM_RGB_COLORS.map { it.copy(alpha = 0.34f) }
    // Soft outer halo stroke followed by a tighter, brighter inner rim stroke.
    val outerExpandPx = 3.dp.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(colors = outerColors, start = start, end = end),
        topLeft = Offset(-outerExpandPx, -outerExpandPx),
        size = Size(size.width + outerExpandPx * 2f, size.height + outerExpandPx * 2f),
        cornerRadius = CornerRadius(cornerPx + outerExpandPx, cornerPx + outerExpandPx),
        style = Stroke(width = 3.dp.toPx())
    )
    val borderExpandPx = 0.75.dp.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(colors = innerColors, start = start, end = end),
        topLeft = Offset(-borderExpandPx, -borderExpandPx),
        size = Size(size.width + borderExpandPx * 2f, size.height + borderExpandPx * 2f),
        cornerRadius = CornerRadius(cornerPx + borderExpandPx, cornerPx + borderExpandPx),
        style = Stroke(width = 1.2.dp.toPx())
    )
}

/**
 * Interactive lock indicator chip shown when the cover display is locked; tapping invokes
 * [onClick] to request device unlock.
 */
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
            .dimRgbGlow()
            .oneUiChip()
            .widthIn(min = 56.dp)
            .heightIn(min = minHeight + 4.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Open search",
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
    }
}

private const val MAX_LOCK_ONGOING_HIGHLIGHTS = 1
private const val MAX_LOCK_NORMAL_HIGHLIGHTS = 2

private val LOCK_HIGHLIGHT_BANNED_PACKAGES = setOf(
    "com.android.systemui",
    "com.samsung.systemui"
)

/**
 * Filters active notifications down to prioritized lockscreen highlight badges.
 *
 * Excludes media sessions and SystemUI noise, then returns up to [MAX_LOCK_ONGOING_HIGHLIGHTS]
 * ongoing notification (e.g., active calls/timers) followed by up to [MAX_LOCK_NORMAL_HIGHLIGHTS]
 * newest standard notifications deduplicated by package name.
 */
internal fun selectLockHighlightNotifications(
    notifications: List<CoverNotificationModel>
): List<CoverNotificationModel> {
    val eligible = notifications.filterNot { model ->
        val pkg = model.packageName.trim().lowercase(Locale.ROOT)
        model.isMediaNotification ||
            pkg in LOCK_HIGHLIGHT_BANNED_PACKAGES ||
            pkg.startsWith("com.android.systemui.") ||
            pkg.startsWith("com.samsung.systemui.")
    }
    val ongoing = eligible
        .filter { it.isOngoing }
        .sortedByDescending { it.postTime }
        .take(MAX_LOCK_ONGOING_HIGHLIGHTS)
    val normal = eligible
        .filterNot { it.isOngoing }
        .sortedByDescending { it.postTime }
        .distinctBy { it.packageName.trim().lowercase(Locale.ROOT) }
        .take(MAX_LOCK_NORMAL_HIGHLIGHTS)
    return ongoing + normal
}

/**
 * Row of circular app-icon badges rendered above the lockscreen clock for high-priority
 * ongoing and recent notifications; tapping a badge dispatches [onNotificationOpen].
 */
@Composable
private fun LockTopNotificationHighlights(
    notifications: List<CoverNotificationModel>,
    onNotificationOpen: (CoverNotificationModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val highlightNotifications = remember(notifications) {
        selectLockHighlightNotifications(notifications)
    }
    if (highlightNotifications.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        highlightNotifications.forEach { notification ->
            val iconBitmap = rememberPackageIconBitmap(notification.packageName)
            val badgeColor = if (notification.isOngoing) {
                CoverOSCallActiveColor.copy(alpha = 0.6f)
            } else {
                Color.White.copy(alpha = 0.16f)
            }
            Surface(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable { onNotificationOpen(notification) },
                color = badgeColor,
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

/** Returns the formatted local wall-clock time string (`h:mm a`) for the lockscreen clock. */
internal fun currentCoverTime(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

/** Returns the formatted short local date string (`EEE, MMM d`) shown beneath the lockscreen clock. */
internal fun currentCoverDate(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
