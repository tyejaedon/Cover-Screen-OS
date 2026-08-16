package com.tyejaedon.coverscreenos.permissions

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.helpers.AppPermissionHelper
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding
import com.tyejaedon.coverscreenos.ui.theme.coverTopLevelSafeInsets
import com.tyejaedon.coverscreenos.ui.theme.navbarPadding

private data class PermissionRequirementUiModel(
    val title: String,
    val details: String,
    val granted: Boolean,
    val actionLabel: String,
    val icon: ImageVector,
    val onAction: () -> Unit
)

@Composable
fun PermissionScreen(
    modifier: Modifier = Modifier,
    onPermissionsGranted: () -> Unit,
    grantedContent: @Composable () -> Unit
) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(AppPermissionHelper.hasNotificationPermission(context))
    }
    var hasOverlayPermission by remember { mutableStateOf(AppPermissionHelper.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember {
        mutableStateOf(AppPermissionHelper.isAccessibilityServiceEnabled(context))
    }
    var hasBatteryOptimizationExemption by remember {
        mutableStateOf(AppPermissionHelper.isBatteryOptimizationDisabled(context))
    }
    var isForegroundServiceRunning by remember {
        mutableStateOf(ForegroundServiceHelper.isForegroundServiceRunning(context))
    }
    var hasTriggeredGrantedCallback by remember { mutableStateOf(false) }

    fun refreshPermissionState() {
        hasNotificationPermission = AppPermissionHelper.hasNotificationPermission(context)
        hasOverlayPermission = AppPermissionHelper.canDrawOverlays(context)
        hasAccessibilityPermission = AppPermissionHelper.isAccessibilityServiceEnabled(context)
        hasBatteryOptimizationExemption = AppPermissionHelper.isBatteryOptimizationDisabled(context)
        isForegroundServiceRunning = ForegroundServiceHelper.isForegroundServiceRunning(context)
    }

    val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    val openOverlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissionState()
    }

    val openAccessibilitySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissionState()
    }

    val openAppSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissionState()
    }

    val openBatteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissionState()
    }

    val allPermissionsGranted = hasNotificationPermission && hasOverlayPermission && hasAccessibilityPermission

    LaunchedEffect(allPermissionsGranted) {
        if (allPermissionsGranted && !hasTriggeredGrantedCallback) {
            hasTriggeredGrantedCallback = true
            onPermissionsGranted()
        }
    }

    if (allPermissionsGranted) {
        grantedContent()
        return
    }

    val permissionRequirements = listOf(
        PermissionRequirementUiModel(
            title = "Notification permission",
            details = "Required for the persistent foreground notification.",
            granted = hasNotificationPermission,
            actionLabel = "Grant notification permission",
            icon = Icons.Filled.Notifications,
            onAction = {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        ),
        PermissionRequirementUiModel(
            title = "Appear on top",
            details = "Allows TYPE_APPLICATION_OVERLAY windows to draw over other apps.",
            granted = hasOverlayPermission,
            actionLabel = "Open overlay settings",
            icon = Icons.Filled.Layers,
            onAction = {
                openOverlaySettingsLauncher.launch(AppPermissionHelper.createOverlaySettingsIntent(context))
            }
        ),
        PermissionRequirementUiModel(
            title = "Accessibility service",
            details = "Lets the app react to window and navigation events needed for cover control.",
            granted = hasAccessibilityPermission,
            actionLabel = "Open accessibility settings",
            icon = Icons.Filled.Accessibility,
            onAction = {
                openAccessibilitySettingsLauncher.launch(AppPermissionHelper.createAccessibilitySettingsIntent())
            }
        )
    )
    val grantedCount = permissionRequirements.count { it.granted }
    val totalCount = permissionRequirements.size
    val nextMissingRequirement = permissionRequirements.firstOrNull { !it.granted }

    Box(
        modifier = modifier
            .fillMaxSize()
            .navbarPadding()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .coverScreenPadding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PermissionHeaderCard(grantedCount = grantedCount, totalCount = totalCount)

            nextMissingRequirement?.let { requirement ->
                PermissionPriorityActionCard(
                    requirementTitle = requirement.title,
                    actionLabel = requirement.actionLabel,
                    onAction = requirement.onAction
                )
            }

            permissionRequirements.forEachIndexed { index, requirement ->
                PermissionRequirementCard(
                    title = "${index + 1}) ${requirement.title}",
                    details = requirement.details,
                    granted = requirement.granted,
                    actionLabel = requirement.actionLabel,
                    icon = requirement.icon,
                    onAction = requirement.onAction
                )
            }

            PermissionRequirementCard(
                title = "Battery optimization",
                details = "Recommended: disabling optimization helps keep the launcher service alive reliably.",
                granted = hasBatteryOptimizationExemption,
                actionLabel = "Disable battery optimization",
                icon = Icons.Filled.BatterySaver,
                onAction = {
                    openBatteryOptimizationLauncher.launch(
                        AppPermissionHelper.createBatteryOptimizationSettingsIntent(context)
                    )
                }
            )

            PermissionSupportActions(
                onOpenAppSettings = {
                    openAppSettingsLauncher.launch(AppPermissionHelper.createAppDetailsSettingsIntent(context))
                },
                onRefresh = { refreshPermissionState() }
            )

            if (isForegroundServiceRunning) {
                PermissionInfoBanner(message = "Foreground service currently running.")
            }
        }
    }
}
