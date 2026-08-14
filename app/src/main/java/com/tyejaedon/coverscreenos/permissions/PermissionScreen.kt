package com.tyejaedon.coverscreenos.permissions

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.helpers.AppPermissionHelper
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper

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
    var hasTriggeredGrantedCallback by remember { mutableStateOf(false) }

    fun refreshPermissionState() {
        hasNotificationPermission = AppPermissionHelper.hasNotificationPermission(context)
        hasOverlayPermission = AppPermissionHelper.canDrawOverlays(context)
        hasAccessibilityPermission = AppPermissionHelper.isAccessibilityServiceEnabled(context)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Before overlay launch", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Enable each permission so Cover Screen OS can stay on top and react to navigation changes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PermissionRequirementCard(
            title = "1) Notification permission",
            details = "Required for the persistent foreground notification.",
            granted = hasNotificationPermission,
            actionLabel = "Grant notification permission",
            onAction = {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        )

        PermissionRequirementCard(
            title = "2) Appear on top",
            details = "Allows TYPE_APPLICATION_OVERLAY windows to draw over other apps.",
            granted = hasOverlayPermission,
            actionLabel = "Open overlay settings",
            onAction = {
                openOverlaySettingsLauncher.launch(AppPermissionHelper.createOverlaySettingsIntent(context))
            }
        )

        PermissionRequirementCard(
            title = "3) Accessibility service",
            details = "Lets the app react to window and navigation events needed for cover control.",
            granted = hasAccessibilityPermission,
            actionLabel = "Open accessibility settings",
            onAction = {
                openAccessibilitySettingsLauncher.launch(AppPermissionHelper.createAccessibilitySettingsIntent())
            }
        )

        OutlinedButton(onClick = {
            openAppSettingsLauncher.launch(AppPermissionHelper.createAppDetailsSettingsIntent(context))
        }) {
            Text("Open app settings")
        }

        OutlinedButton(onClick = {
            refreshPermissionState()
        }) {
            Text("Refresh permission status")
        }

        if (ForegroundServiceHelper.isForegroundServiceRunning(context)) {
            Text(
                "Foreground service currently running.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PermissionRequirementCard(
    title: String,
    details: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (granted) "Granted" else "Missing",
                    color = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(details, style = MaterialTheme.typography.bodyMedium)
            if (!granted) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}