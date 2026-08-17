package com.tyejaedon.coverscreenos.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.helpers.AppPermissionHelper
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper
import com.tyejaedon.coverscreenos.ui.controllers.ForegroundServiceController
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding
import com.tyejaedon.coverscreenos.ui.theme.coverTopLevelSafeInsets
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun DeploymentStatusScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    var refreshTicker by remember { mutableIntStateOf(0) }

    val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshTicker += 1
    }

    val openOverlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshTicker += 1
    }
    val openAccessibilitySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshTicker += 1
    }
    val openNotificationListenerSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshTicker += 1
    }

    var overlayReady by remember(refreshTicker) { mutableStateOf(AppPermissionHelper.canDrawOverlays(context)) }
    var accessibilityReady by remember(refreshTicker) {
        mutableStateOf(AppPermissionHelper.isAccessibilityServiceEnabled(context))
    }
    var notificationReady by remember(refreshTicker) {
        mutableStateOf(AppPermissionHelper.hasNotificationPermission(context))
    }
    var notificationListenerReady by remember(refreshTicker) {
        mutableStateOf(AppPermissionHelper.isNotificationListenerEnabled(context))
    }
    var serviceRunning by remember(refreshTicker) {
        mutableStateOf(ForegroundServiceHelper.isForegroundServiceRunning(context))
    }

    fun refreshStatus() {
        notificationReady = AppPermissionHelper.hasNotificationPermission(context)
        overlayReady = AppPermissionHelper.canDrawOverlays(context)
        accessibilityReady = AppPermissionHelper.isAccessibilityServiceEnabled(context)
        notificationListenerReady = AppPermissionHelper.isNotificationListenerEnabled(context)
        serviceRunning = ForegroundServiceHelper.isForegroundServiceRunning(context)
    }

    // Keep status cards fresh even when users toggle settings outside this screen.
    LaunchedEffect(Unit) {
        while (true) {
            refreshStatus()
            delay(5.seconds)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .coverTopLevelSafeInsets()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f) // Richer, glassier gradient
                    )
                )
            )
            .coverScreenPadding(
                horizontal = OverlayLayoutSpec.horizontalMargin,
                vertical = OverlayLayoutSpec.verticalPadding
            )
    ) {
        val overlayWidth = OverlayLayoutSpec.overlayWidth(maxWidth)

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = OverlayLayoutSpec.contentTopOffset)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp) // Increased spacing for breathability
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Deployment Center",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Use this screen to check launcher readiness, run deployment actions, and confirm results.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DeploymentReadinessCard(
                    notificationReady = notificationReady,
                    overlayReady = overlayReady,
                    accessibilityReady = accessibilityReady,
                    notificationListenerReady = notificationListenerReady,
                    serviceRunning = serviceRunning,
                    onEnableNotifications = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onEnableOverlay = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        openOverlaySettingsLauncher.launch(AppPermissionHelper.createOverlaySettingsIntent(context))
                    },
                    onEnableAccessibility = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        openAccessibilitySettingsLauncher.launch(AppPermissionHelper.createAccessibilitySettingsIntent())
                    },
                    onEnableNotificationListener = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        openNotificationListenerSettingsLauncher.launch(
                            AppPermissionHelper.createNotificationListenerSettingsIntent()
                        )
                    },
                    onStartService = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        ForegroundServiceHelper.startForegroundService(context)
                        refreshStatus()
                    },
                    onRefresh = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        refreshStatus()
                    }
                )

                ForegroundServiceController()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Personalization (optional)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Once deployment is stable, tune dock apps, wallpaper, and appearance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LauncherSettingsHub(modifier = Modifier.fillMaxWidth())

                // Bottom padding spacer
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))
            }

            // Floating banner
            LauncherRunningOverlay(
                isServiceRunning = serviceRunning,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(overlayWidth)
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DeploymentReadinessCard(
    notificationReady: Boolean,
    overlayReady: Boolean,
    accessibilityReady: Boolean,
    notificationListenerReady: Boolean,
    serviceRunning: Boolean,
    onEnableNotifications: () -> Unit,
    onEnableOverlay: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableNotificationListener: () -> Unit,
    onStartService: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val checksReadyCount = listOf(
        notificationReady,
        overlayReady,
        accessibilityReady,
        notificationListenerReady,
        serviceRunning
    ).count { it }
    val isFullyReady = checksReadyCount == 5
    val statusLabel = when {
        isFullyReady -> "Ready"
        checksReadyCount >= 2 -> "Action needed"
        else -> "Setup required"
    }

    val statusDescription = when {
        !notificationReady -> "Grant notification permission to allow reliable foreground-service operation."
        !overlayReady -> "Enable Appear on top to allow the launcher overlay to render."
        !accessibilityReady -> "Enable Accessibility service so navigation events can be handled."
        !notificationListenerReady -> "Enable notification listener so cover notification controls can work."
        !serviceRunning -> "Start the foreground service to activate launcher runtime."
        else -> "All deployment checks are passing. Your launcher is active."
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp), clip = false),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) // Frosted glass feel
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "System readiness",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "$checksReadyCount of 5 checks ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(
                    label = statusLabel,
                    ready = isFullyReady
                )
            }

            Text(
                text = statusDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeploymentCheckRow(
                    title = "Notification permission",
                    details = "Required so foreground runtime can post its persistent status notification.",
                    ready = notificationReady,
                    actionLabel = "Grant",
                    onAction = onEnableNotifications
                )
                DeploymentCheckRow(
                    title = "Appear on top",
                    details = "Allows the overlay launcher UI to stay visible.",
                    ready = overlayReady,
                    actionLabel = "Enable",
                    onAction = onEnableOverlay
                )
                DeploymentCheckRow(
                    title = "Accessibility service",
                    details = "Lets launcher react to navigation and window changes.",
                    ready = accessibilityReady,
                    actionLabel = "Enable",
                    onAction = onEnableAccessibility
                )
                DeploymentCheckRow(
                    title = "Notification listener",
                    details = "Allows cover notification actions and notification-center updates.",
                    ready = notificationListenerReady,
                    actionLabel = "Enable",
                    onAction = onEnableNotificationListener
                )
                DeploymentCheckRow(
                    title = "Foreground service",
                    details = "Keeps launcher runtime active in the background.",
                    ready = serviceRunning,
                    actionLabel = "Start",
                    onAction = onStartService
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        when {
                            !notificationReady -> onEnableNotifications()
                            !overlayReady -> onEnableOverlay()
                            !accessibilityReady -> onEnableAccessibility()
                            !notificationListenerReady -> onEnableNotificationListener()
                            !serviceRunning -> onStartService()
                            else -> onRefresh()
                        }
                    }
                ) {
                    Text(
                        when {
                            !notificationReady -> "Grant notifications"
                            !overlayReady -> "Enable overlay"
                            !accessibilityReady -> "Enable accessibility"
                            !notificationListenerReady -> "Enable notif listener"
                            !serviceRunning -> "Start launcher"
                            else -> "All checks ready"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
private fun DeploymentCheckRow(
    title: String,
    details: String,
    ready: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    // Smoothly transition background and borders based on state using Spring physics
    val backgroundColor by animateColorAsState(
        targetValue = if (ready) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rowBackground"
    )

    val borderColor by animateColorAsState(
        targetValue = if (ready) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rowBorder"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp, 36.dp)) {
            Crossfade(
                targetState = ready,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "readyCrossfade"
            ) { isReady ->
                if (isReady) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Ready",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onAction,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, ready: Boolean) {
    val containerColor by animateColorAsState(
        targetValue = if (ready) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipContent"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = contentColor
        )
    }
}

@Composable
private fun LauncherRunningOverlay(
    isServiceRunning: Boolean,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            initialOffsetY = { -it }
        ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = slideOutVertically(
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            targetOffsetY = { -it }
        ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        modifier = modifier
    ) {
        val bannerContainer by animateColorAsState(
            targetValue = if (isServiceRunning) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
            },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "bannerBackground"
        )

        val contentColor by animateColorAsState(
            targetValue = if (isServiceRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "bannerContent"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(50)),
            shape = RoundedCornerShape(50), // Fully rounded pill shape for a floating top banner
            colors = CardDefaults.cardColors(containerColor = bannerContainer),
            border = BorderStroke(1.dp, contentColor.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(contentColor)
                )
                Text(
                    text = if (isServiceRunning) {
                        "Launcher runtime is active."
                    } else {
                        "Launcher runtime is inactive."
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}