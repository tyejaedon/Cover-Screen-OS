package com.tyejaedon.coverscreenos.ui.launcher

import android.content.Context
import android.hardware.camera2.CameraManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tyejaedon.coverscreenos.helpers.QuickSettingsController
import com.tyejaedon.coverscreenos.ui.CoverSearchUiTestTags
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTextStyles
import com.tyejaedon.coverscreenos.ui.theme.LocalCoverPanelCornerRadius
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.oneui.CoverOneUIType
import com.tyejaedon.coverscreenos.ui.theme.oneui.CoverOneUITokens
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiChip
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiPanel
import dev.chrisbanes.haze.HazeState

/**
 * One-UI-style "pull down" Quick Settings sheet: WiFi / Bluetooth / Flashlight toggles that
 * reflect live system state, plus a brightness slider. Anchored to the top of the lock page
 * and revealed by a vertical swipe-down (see `openQuickSettingsFromLockscreen` in
 * `CoverAppGridOverlay`).
 */
@Composable
internal fun QuickSettingsPanelTile(
    onDismiss: () -> Unit,
    swipeUpThresholdPx: Float,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var wifiEnabled by remember { mutableStateOf(QuickSettingsController.isWifiEnabled(context)) }
    var bluetoothEnabled by remember { mutableStateOf(QuickSettingsController.isBluetoothEnabled(context)) }
    var flashlightEnabled by remember { mutableStateOf(false) }
    var canWriteSettings by remember { mutableStateOf(QuickSettingsController.canWriteSystemSettings(context)) }
    var brightnessPercent by remember { mutableFloatStateOf(QuickSettingsController.getBrightnessPercent(context)) }
    val torchCameraId = remember { QuickSettingsController.resolveTorchCameraId(context) }

    // The overlay stays alive (just hidden) while a system Settings fallback screen is on
    // top, so re-read live state whenever this composition resumes to the foreground.
    fun refreshSystemState() {
        wifiEnabled = QuickSettingsController.isWifiEnabled(context)
        bluetoothEnabled = QuickSettingsController.isBluetoothEnabled(context)
        canWriteSettings = QuickSettingsController.canWriteSystemSettings(context)
        brightnessPercent = QuickSettingsController.getBrightnessPercent(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshSystemState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keep the flashlight tile honest if the torch is toggled elsewhere (e.g. camera app).
    DisposableEffect(torchCameraId) {
        val cameraManager = QuickSettingsController.cameraManager(context)
        if (cameraManager == null || torchCameraId == null) {
            onDispose {}
        } else {
            val callback = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchCameraId) flashlightEnabled = enabled
                }
            }
            cameraManager.registerTorchCallback(callback, null)
            onDispose { cameraManager.unregisterTorchCallback(callback) }
        }
    }

    val cardShape = RoundedCornerShape(LocalCoverPanelCornerRadius.current)

    Box(modifier = modifier.fillMaxSize().testTag(CoverSearchUiTestTags.QUICK_SETTINGS_PANEL)) {
        // Scrim: tapping anywhere outside the sheet dismisses it, mirroring the system
        // quick-settings/notification shade pattern.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(12.dp)
                .oneUiPanel(shape = cardShape, hazeState = hazeState, blurRadius = 26.dp)
                // Swallow taps on the sheet itself so they don't fall through to the scrim.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .pointerInput(swipeUpThresholdPx) {
                    var cumulativeDrag = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount -> cumulativeDrag += dragAmount },
                        onDragEnd = {
                            if (cumulativeDrag <= -swipeUpThresholdPx) onDismiss()
                            cumulativeDrag = 0f
                        },
                        onDragCancel = { cumulativeDrag = 0f }
                    )
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Settings",
                    style = CoverOSTextStyles.SectionLabelText,
                    color = Color.White
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close quick settings",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickSettingsToggleTile(
                    label = "WiFi",
                    icon = if (wifiEnabled) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    isEnabled = wifiEnabled,
                    isAvailable = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        QuickSettingsController.toggleWifi(context)
                        wifiEnabled = QuickSettingsController.isWifiEnabled(context)
                    }
                )
                QuickSettingsToggleTile(
                    label = "Bluetooth",
                    icon = if (bluetoothEnabled) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
                    isEnabled = bluetoothEnabled,
                    isAvailable = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        QuickSettingsController.toggleBluetooth(context)
                        bluetoothEnabled = QuickSettingsController.isBluetoothEnabled(context)
                    }
                )
                QuickSettingsToggleTile(
                    label = "Flashlight",
                    icon = if (flashlightEnabled) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                    isEnabled = flashlightEnabled,
                    isAvailable = torchCameraId != null,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val cameraId = torchCameraId ?: return@QuickSettingsToggleTile
                        val targetEnabled = !flashlightEnabled
                        if (QuickSettingsController.setTorchEnabled(context, cameraId, targetEnabled)) {
                            flashlightEnabled = targetEnabled
                        }
                    }
                )
            }

            QuickSettingsBrightnessControl(
                brightnessPercent = brightnessPercent,
                canWriteSettings = canWriteSettings,
                onBrightnessChanged = { updatedPercent ->
                    brightnessPercent = updatedPercent
                    QuickSettingsController.setBrightnessPercent(context, updatedPercent)
                },
                onRequestWritePermission = {
                    QuickSettingsController.launchSettingsIntent(
                        context,
                        QuickSettingsController.createWriteSettingsPermissionIntent(context)
                    )
                }
            )
        }
    }
}

@Composable
private fun QuickSettingsToggleTile(
    label: String,
    icon: ImageVector,
    isEnabled: Boolean,
    isAvailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(CoverOneUITokens.radiusChip)
    val fillAlpha = if (!isAvailable) 0.04f else if (isEnabled) 0.30f else 0.08f
    val borderAlpha = if (!isAvailable) 0.08f else if (isEnabled) 0.4f else 0.14f
    val contentTint = when {
        !isAvailable -> Color.White.copy(alpha = 0.3f)
        isEnabled -> Color.White
        else -> Color.White.copy(alpha = 0.75f)
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = fillAlpha))
            .border(1.dp, Color.White.copy(alpha = borderAlpha), shape)
            .then(
                if (isAvailable) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .coverMinimumTouchTarget()
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentTint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickSettingsBrightnessControl(
    brightnessPercent: Float,
    canWriteSettings: Boolean,
    onBrightnessChanged: (Float) -> Unit,
    onRequestWritePermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.BrightnessHigh,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Brightness",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        if (canWriteSettings) {
            Slider(
                value = brightnessPercent,
                onValueChange = onBrightnessChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .coverMinimumTouchTarget(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .oneUiChip()
                    .clickable(onClick = onRequestWritePermission)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap to allow brightness control",
                    style = CoverOneUIType.chipLabel,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}
