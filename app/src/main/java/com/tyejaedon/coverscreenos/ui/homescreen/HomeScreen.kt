package com.tyejaedon.coverscreenos.ui.homescreen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.helpers.AppPermissionHelper
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper
import com.tyejaedon.coverscreenos.ui.homescreen.customization.HomeCustomizationHub
import com.tyejaedon.coverscreenos.ui.launcher.OverlayLayoutSpec
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding
import com.tyejaedon.coverscreenos.ui.theme.coverTopLevelSafeInsets
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
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
    val openBatteryOptimizationSettingsLauncher = rememberLauncherForActivityResult(
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
    var batteryOptimizationReady by remember(refreshTicker) {
        mutableStateOf(AppPermissionHelper.isBatteryOptimizationDisabled(context))
    }
    var serviceRunning by remember(refreshTicker) {
        mutableStateOf(ForegroundServiceHelper.isForegroundServiceRunning())
    }

    fun refreshStatus() {
        notificationReady = AppPermissionHelper.hasNotificationPermission(context)
        overlayReady = AppPermissionHelper.canDrawOverlays(context)
        accessibilityReady = AppPermissionHelper.isAccessibilityServiceEnabled(context)
        notificationListenerReady = AppPermissionHelper.isNotificationListenerEnabled(context)
        batteryOptimizationReady = AppPermissionHelper.isBatteryOptimizationDisabled(context)
        serviceRunning = ForegroundServiceHelper.isForegroundServiceRunning()
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
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Home Screen Setup",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Use this screen to check home screen readiness, run setup actions, and confirm results.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HomeReadinessCard(
                    notificationReady = notificationReady,
                    overlayReady = overlayReady,
                    accessibilityReady = accessibilityReady,
                    notificationListenerReady = notificationListenerReady,
                    batteryOptimizationReady = batteryOptimizationReady,
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
                    onDisableBatteryOptimization = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        openBatteryOptimizationSettingsLauncher.launch(
                            AppPermissionHelper.createBatteryOptimizationSettingsIntent(context)
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

                HomeRuntimeControls()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Home customization",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "After setup is stable, tune dock apps, wallpaper, and appearance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HomeCustomizationHub(modifier = Modifier.fillMaxWidth())

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))
            }

            HomeRuntimeBanner(
                isServiceRunning = serviceRunning,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(overlayWidth)
                    .padding(top = 8.dp)
            )
        }
    }
}

