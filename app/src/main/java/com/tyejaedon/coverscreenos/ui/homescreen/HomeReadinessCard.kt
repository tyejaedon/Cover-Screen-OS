package com.tyejaedon.coverscreenos.ui.homescreen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal fun hasFullHomeReadiness(
    notificationReady: Boolean,
    accessibilityReady: Boolean,
    inputAccessibilityReady: Boolean,
    notificationListenerReady: Boolean,
    batteryOptimizationReady: Boolean,
    serviceRunning: Boolean
): Boolean = notificationReady && accessibilityReady && inputAccessibilityReady &&
    notificationListenerReady && batteryOptimizationReady && serviceRunning

@Composable
internal fun HomeReadinessCard(
    notificationReady: Boolean,
    accessibilityReady: Boolean,
    inputAccessibilityReady: Boolean,
    notificationListenerReady: Boolean,
    batteryOptimizationReady: Boolean,
    serviceRunning: Boolean,
    onEnableNotifications: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableInputAccessibility: () -> Unit,
    onEnableNotificationListener: () -> Unit,
    onDisableBatteryOptimization: () -> Unit,
    onStartService: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val checksReadyCount = listOf(
        notificationReady,
        accessibilityReady,
        inputAccessibilityReady,
        notificationListenerReady,
        batteryOptimizationReady,
        serviceRunning
    ).count { it }
    val totalChecks = 6
    val isFullyReady = hasFullHomeReadiness(
        notificationReady = notificationReady,
        accessibilityReady = accessibilityReady,
        inputAccessibilityReady = inputAccessibilityReady,
        notificationListenerReady = notificationListenerReady,
        batteryOptimizationReady = batteryOptimizationReady,
        serviceRunning = serviceRunning
    )
    val launcherReady = notificationReady && accessibilityReady &&
        notificationListenerReady && batteryOptimizationReady && serviceRunning
    val statusLabel = when {
        isFullyReady -> "Ready"
        launcherReady -> "Keyboard setup optional"
        checksReadyCount >= 2 -> "Action needed"
        else -> "Setup required"
    }

    val statusDescription = when {
        !notificationReady -> "Grant notification permission to allow reliable foreground-service operation."
        !accessibilityReady -> "Enable Cover Screen OS Launcher in Accessibility settings to host the launcher."
        !notificationListenerReady -> "Enable notification listener so cover notification controls can work."
        !batteryOptimizationReady -> "Disable battery optimization so OEM power management does not stop home screen runtime."
        !serviceRunning -> "Start the foreground service to activate home screen runtime."
        !inputAccessibilityReady -> "Launcher ready. Enable the separate Cover Screen OS input service for the cover keyboard fallback."
        else -> "All setup checks are passing. Your home screen is active."
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp), clip = false),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
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
                        text = "$checksReadyCount of $totalChecks checks ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(
                    label = statusLabel,
                    ready = launcherReady
                )
            }

            Text(
                text = statusDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupCheckRow(
                    title = "Notification permission",
                    details = "Required so foreground runtime can post its persistent status notification.",
                    ready = notificationReady,
                    actionLabel = "Grant",
                    onAction = onEnableNotifications
                )
                SetupCheckRow(
                    title = "Cover Screen OS Launcher accessibility",
                    details = "Hosts the cover launcher and handles navigation.",
                    ready = accessibilityReady,
                    actionLabel = "Enable",
                    onAction = onEnableAccessibility
                )
                SetupCheckRow(
                    title = "Cover Screen OS input accessibility",
                    details = "Recommended for cover keyboard fallback; separate from the launcher service.",
                    ready = inputAccessibilityReady,
                    actionLabel = "Enable",
                    onAction = onEnableInputAccessibility
                )
                SetupCheckRow(
                    title = "Notification listener",
                    details = "Allows cover notification actions and notification-center updates.",
                    ready = notificationListenerReady,
                    actionLabel = "Enable",
                    onAction = onEnableNotificationListener
                )
                SetupCheckRow(
                    title = "Battery optimization",
                    details = "Prevents OEM battery policies from reclaiming home screen runtime.",
                    ready = batteryOptimizationReady,
                    actionLabel = "Disable",
                    onAction = onDisableBatteryOptimization
                )
                SetupCheckRow(
                    title = "Foreground service",
                    details = "Keeps home screen runtime active in the background.",
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
                            !accessibilityReady -> onEnableAccessibility()
                            !notificationListenerReady -> onEnableNotificationListener()
                            !batteryOptimizationReady -> onDisableBatteryOptimization()
                            !serviceRunning -> onStartService()
                            !inputAccessibilityReady -> onEnableInputAccessibility()
                            else -> onRefresh()
                        }
                    }
                ) {
                    Text(
                        when {
                            !notificationReady -> "Grant notifications"
                            !accessibilityReady -> "Enable accessibility"
                            !notificationListenerReady -> "Enable notif listener"
                            !batteryOptimizationReady -> "Disable battery optimization"
                            !serviceRunning -> "Start home screen"
                            !inputAccessibilityReady -> "Enable keyboard input"
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
private fun SetupCheckRow(
    title: String,
    details: String,
    ready: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
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
