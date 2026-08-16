package com.tyejaedon.coverscreenos.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
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
    val context = androidx.compose.ui.platform.LocalContext.current
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

    var overlayReady by remember(refreshTicker) { mutableStateOf(AppPermissionHelper.canDrawOverlays(context)) }
    var accessibilityReady by remember(refreshTicker) {
        mutableStateOf(AppPermissionHelper.isAccessibilityServiceEnabled(context))
    }
    var notificationReady by remember(refreshTicker) {
        mutableStateOf(AppPermissionHelper.hasNotificationPermission(context))
    }
    var serviceRunning by remember(refreshTicker) {
        mutableStateOf(ForegroundServiceHelper.isForegroundServiceRunning(context))
    }

    fun refreshStatus() {
        notificationReady = AppPermissionHelper.hasNotificationPermission(context)
        overlayReady = AppPermissionHelper.canDrawOverlays(context)
        accessibilityReady = AppPermissionHelper.isAccessibilityServiceEnabled(context)
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
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
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
            LauncherRunningOverlay(
                isServiceRunning = serviceRunning,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(overlayWidth)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = OverlayLayoutSpec.contentTopOffset)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Deployment Center", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Use this screen to check launcher readiness, run deployment actions, and confirm results.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                DeploymentReadinessCard(
                    notificationReady = notificationReady,
                    overlayReady = overlayReady,
                    accessibilityReady = accessibilityReady,
                    serviceRunning = serviceRunning,
                    onEnableNotifications = {
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onEnableOverlay = {
                        openOverlaySettingsLauncher.launch(AppPermissionHelper.createOverlaySettingsIntent(context))
                    },
                    onEnableAccessibility = {
                        openAccessibilitySettingsLauncher.launch(AppPermissionHelper.createAccessibilitySettingsIntent())
                    },
                    onStartService = {
                        ForegroundServiceHelper.startForegroundService(context)
                        refreshStatus()
                    },
                    onRefresh = { refreshStatus() }
                )

                ForegroundServiceController()

                Text(
                    text = "Personalization (optional)",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Once deployment is stable, tune dock apps, wallpaper, and appearance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LauncherSettingsHub(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DeploymentReadinessCard(
    notificationReady: Boolean,
    overlayReady: Boolean,
    accessibilityReady: Boolean,
    serviceRunning: Boolean,
    onEnableNotifications: () -> Unit,
    onEnableOverlay: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onStartService: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val checksReadyCount = listOf(notificationReady, overlayReady, accessibilityReady, serviceRunning).count { it }
    val statusLabel = when {
        checksReadyCount == 4 -> "Ready"
        checksReadyCount >= 2 -> "Action needed"
        else -> "Setup required"
    }

    val statusDescription = when {
        !notificationReady -> "Grant notification permission to allow reliable foreground-service operation."
        !overlayReady -> "Enable Appear on top to allow the launcher overlay to render."
        !accessibilityReady -> "Enable Accessibility service so navigation events can be handled."
        !serviceRunning -> "Start the foreground service to activate launcher runtime."
        else -> "All deployment checks are passing."
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("System readiness", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "$checksReadyCount of 4 checks ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(
                    label = statusLabel,
                    ready = checksReadyCount == 4
                )
            }

            Text(
                text = statusDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                title = "Foreground service",
                details = "Keeps launcher runtime active in the background.",
                ready = serviceRunning,
                actionLabel = "Start",
                onAction = onStartService
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        when {
                            !notificationReady -> onEnableNotifications()
                            !overlayReady -> onEnableOverlay()
                            !accessibilityReady -> onEnableAccessibility()
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
                            !serviceRunning -> "Start launcher"
                            else -> "All checks ready"
                        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (ready) {
            StatusChip(label = "Ready", ready = true)
        } else {
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, ready: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (ready) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        },
        border = BorderStroke(
            1.dp,
            if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
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
        enter = fadeIn(animationSpec = tween(durationMillis = 500))
    ) {
        val bannerContainer = if (isServiceRunning) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f)
        }

        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = bannerContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isServiceRunning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                )
                Text(
                    text = if (isServiceRunning) {
                        "Launcher runtime is active."
                    } else {
                        "Launcher runtime is inactive."
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

