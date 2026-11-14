package com.tyejaedon.coverscreenos.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tyejaedon.coverscreenos.R
import com.tyejaedon.coverscreenos.helpers.AppPermissionHelper
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUITheme
import com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUICardGroup
import com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUIInternalDivider
import com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUIScaffold
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUIPressable

private const val LOG_TAG = "PermissionsScreen"

internal enum class PermissionGroup { REQUIRED, RECOMMENDED, OPTIONAL }

internal enum class PermissionId(val group: PermissionGroup) {
    NOTIFICATIONS(PermissionGroup.REQUIRED),
    LAUNCHER_ACCESSIBILITY(PermissionGroup.REQUIRED),
    NOTIFICATION_LISTENER(PermissionGroup.REQUIRED),
    BATTERY_EXEMPTION(PermissionGroup.REQUIRED),
    INPUT_ACCESSIBILITY(PermissionGroup.RECOMMENDED),
    MEDIA(PermissionGroup.OPTIONAL),
    MICROPHONE(PermissionGroup.OPTIONAL),
    BLUETOOTH(PermissionGroup.OPTIONAL)
}

internal data class PermissionGrants(
    val notifications: Boolean,
    val launcherAccessibility: Boolean,
    val notificationListener: Boolean,
    val batteryExemption: Boolean,
    val inputAccessibility: Boolean,
    val media: Boolean,
    val microphone: Boolean,
    val bluetooth: Boolean
) {
    fun granted(id: PermissionId): Boolean = when (id) {
        PermissionId.NOTIFICATIONS -> notifications
        PermissionId.LAUNCHER_ACCESSIBILITY -> launcherAccessibility
        PermissionId.NOTIFICATION_LISTENER -> notificationListener
        PermissionId.BATTERY_EXEMPTION -> batteryExemption
        PermissionId.INPUT_ACCESSIBILITY -> inputAccessibility
        PermissionId.MEDIA -> media
        PermissionId.MICROPHONE -> microphone
        PermissionId.BLUETOOTH -> bluetooth
    }

    val requiredMissing: Boolean get() = PermissionId.entries.any {
        it.group == PermissionGroup.REQUIRED && !granted(it)
    }
    val healthPercent: Int get() {
        val counted = PermissionId.entries.filter { it.group != PermissionGroup.OPTIONAL }
        return counted.count(::granted) * 100 / counted.size
    }

    companion object {
        fun read(context: Context): PermissionGrants = PermissionGrants(
            notifications = AppPermissionHelper.hasNotificationPermission(context),
            launcherAccessibility = AppPermissionHelper.isAccessibilityServiceEnabled(context),
            notificationListener = AppPermissionHelper.isNotificationListenerEnabled(context),
            batteryExemption = AppPermissionHelper.isBatteryOptimizationDisabled(context),
            inputAccessibility = AppPermissionHelper.isInputAccessibilityServiceEnabled(context),
            media = AppPermissionHelper.hasGalleryMediaPermissions(context),
            microphone = AppPermissionHelper.hasMicrophonePermission(context),
            bluetooth = AppPermissionHelper.hasBluetoothConnectPermission(context)
        )
    }
}

internal data class PermissionRowModel(
    val id: PermissionId,
    val title: String,
    val rationale: String,
    val icon: ImageVector,
    val granted: Boolean,
    val onAction: () -> Unit
)

@Composable
fun PermissionsScreen(
    onContinue: () -> Unit,
    onPermissionsChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var grants by remember(context) { mutableStateOf(PermissionGrants.read(context)) }
    fun refresh() {
        grants = PermissionGrants.read(context)
        onPermissionsChanged()
    }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }
    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }
    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    fun openSettings(intent: Intent) {
        try {
            settingsLauncher.launch(intent)
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "Failed launching settings intent $intent", t)
        }
    }

    val rows = listOf(
        PermissionRowModel(
            PermissionId.NOTIFICATIONS, "Post notifications",
            "Required on Android 13+ to keep the service running.",
            Icons.Filled.Notifications, grants.notifications,
            {
                if (grants.notifications) {
                    openSettings(AppPermissionHelper.createAppDetailsSettingsIntent(context))
                } else {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        ),
        PermissionRowModel(
            PermissionId.LAUNCHER_ACCESSIBILITY, "Cover screen launcher service",
            "Anchors the launcher to the cover screen.",
            Icons.Filled.Accessibility, grants.launcherAccessibility,
            { openSettings(AppPermissionHelper.createAccessibilitySettingsIntent()) }
        ),
        PermissionRowModel(
            PermissionId.NOTIFICATION_LISTENER, "Notification access",
            "Provides notification callbacks for the launcher.",
            Icons.Filled.Notifications, grants.notificationListener,
            { openSettings(AppPermissionHelper.createNotificationListenerSettingsIntent()) }
        ),
        PermissionRowModel(
            PermissionId.BATTERY_EXEMPTION, "Battery optimization exemption",
            "Keeps the launcher service running reliably.",
            Icons.Filled.BatterySaver, grants.batteryExemption,
            { openSettings(AppPermissionHelper.createBatteryOptimizationSettingsIntent(context)) }
        ),
        PermissionRowModel(
            PermissionId.INPUT_ACCESSIBILITY, "Cover keyboard input",
            "Enables keyboard fallback and text injection.",
            Icons.Filled.Accessibility, grants.inputAccessibility,
            { openSettings(AppPermissionHelper.createAccessibilitySettingsIntent()) }
        ),
        PermissionRowModel(
            PermissionId.MEDIA, "Selected photos access",
            "Helps wallpaper imports on some devices.",
            Icons.Filled.PhotoLibrary, grants.media,
            {
                if (grants.media) {
                    openSettings(AppPermissionHelper.createAppDetailsSettingsIntent(context))
                } else {
                    mediaLauncher.launch(AppPermissionHelper.galleryMediaPermissionsToRequest())
                }
            }
        ),
        PermissionRowModel(
            PermissionId.MICROPHONE, "Microphone access",
            "Enables voice search on the cover screen.",
            Icons.Filled.Mic, grants.microphone,
            {
                if (grants.microphone) {
                    openSettings(AppPermissionHelper.createAppDetailsSettingsIntent(context))
                } else {
                    microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        ),
        PermissionRowModel(
            PermissionId.BLUETOOTH, "Bluetooth access",
            "Lets the Quick Settings panel read and toggle Bluetooth directly.",
            Icons.Filled.Bluetooth, grants.bluetooth,
            {
                if (grants.bluetooth) {
                    openSettings(AppPermissionHelper.createAppDetailsSettingsIntent(context))
                } else {
                    bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
        )
    )

    OneUIScaffold(
        title = "Permissions",
        subtitle = "Hardware & Accessibility Setup",
        modifier = modifier
    ) { paddingValues ->
        PermissionsContent(
            grants = grants,
            rows = rows,
            onContinue = onContinue,
            onOpenAppSettings = { openSettings(AppPermissionHelper.createAppDetailsSettingsIntent(context)) },
            onRefresh = ::refresh,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
internal fun PermissionsContent(
    grants: PermissionGrants,
    rows: List<PermissionRowModel>,
    onContinue: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OneUITheme.dimensions.cardSpacing)
        ) {
            item {
                OneUICardGroup(header = "Overview") {
                    PermissionHealthRing(grants.healthPercent)
                }
            }

            PermissionGroup.entries.forEach { group ->
                val groupRows = rows.filter { it.id.group == group }
                if (groupRows.isNotEmpty()) {
                    item(key = group.name) {
                        OneUICardGroup(
                            header = when (group) {
                                PermissionGroup.REQUIRED -> "Required Access"
                                PermissionGroup.RECOMMENDED -> "Recommended Access"
                                PermissionGroup.OPTIONAL -> "Optional Access"
                            }
                        ) {
                            groupRows.forEachIndexed { index, row ->
                                if (index > 0) {
                                    OneUIInternalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                                PermissionRow(row)
                            }
                        }
                    }

                    if (group == PermissionGroup.REQUIRED && !grants.launcherAccessibility) {
                        item(key = "restricted-accessibility-help") {
                            OneUICardGroup(header = "Setup Guidance") {
                                RestrictedAccessibilityHelp(onOpenAppSettings)
                            }
                        }
                    }
                }
            }

            item {
                OneUICardGroup(header = "Support & Diagnostics") {
                    PermissionSupportActions(
                        onOpenAppSettings = onOpenAppSettings,
                        onRefresh = onRefresh
                    )
                }
            }
        }

        if (grants.requiredMissing) {
            Surface(
                shape = CircleShape,
                color = OneUITheme.colors.cardSurface,
                border = BorderStroke(1.dp, OneUITheme.colors.hairlineBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onContinue,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = OneUITheme.colors.accent),
                    modifier = Modifier.fillMaxWidth().coverMinimumTouchTarget()
                ) {
                    Text("Continue with limited features", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

@Composable
private fun RestrictedAccessibilityHelp(onOpenAppSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.restricted_accessibility_title),
            style = OneUITheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.restricted_accessibility_message),
            style = OneUITheme.typography.bodyMedium,
            color = OneUITheme.colors.textSecondary
        )
        OutlinedButton(
            onClick = onOpenAppSettings,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.End).coverMinimumTouchTarget()
        ) {
            Text(stringResource(R.string.restricted_accessibility_open_settings))
        }
    }
}

@Composable
private fun PermissionHealthRing(percentage: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier.size(80.dp).semantics {
                contentDescription = "Permission health"
                stateDescription = "$percentage percent"
            },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { percentage / 100f },
                color = OneUITheme.colors.accent,
                trackColor = OneUITheme.colors.cardSurfaceVariant,
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 6.dp
            )
            Text(
                "$percentage%",
                style = OneUITheme.typography.titleLarge,
                color = OneUITheme.colors.textPrimary
            )
        }
        Column {
            Text(
                "Permission health",
                style = OneUITheme.typography.titleLarge,
                color = OneUITheme.colors.textPrimary
            )
            Text(
                "Required and recommended setup for smooth cover screen operation.",
                style = OneUITheme.typography.bodyMedium,
                color = OneUITheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun PermissionRow(row: PermissionRowModel) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = row.onAction
            )
            .oneUIPressable(interactionSource = interactionSource)
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                stateDescription = "${row.title} -- ${if (row.granted) "granted" else "denied"} -- tap to change"
            }
    ) {
        Box(
            modifier = Modifier
                .size(OneUITheme.dimensions.encircledIconSize)
                .oneUIPressable()
        ) {
            Surface(
                shape = CircleShape,
                color = if (row.granted) OneUITheme.colors.accent.copy(alpha = 0.12f) else OneUITheme.colors.cardSurfaceVariant,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        row.icon,
                        contentDescription = null,
                        tint = if (row.granted) OneUITheme.colors.accent else OneUITheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = OneUITheme.typography.bodyLarge,
                color = OneUITheme.colors.textPrimary
            )
            Text(
                row.rationale,
                style = OneUITheme.typography.bodyMedium,
                color = OneUITheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            shape = CircleShape,
            color = if (row.granted) OneUITheme.colors.accent.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        ) {
            Text(
                if (row.granted) "Ready" else "Set Up",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = OneUITheme.typography.labelMedium,
                color = if (row.granted) OneUITheme.colors.accent else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Preview(name = "Light Mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "OLED Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun PermissionsScreenPreview() {
    OneUITheme {
        PermissionsContent(
            grants = PermissionGrants(
                notifications = true,
                launcherAccessibility = false,
                notificationListener = true,
                batteryExemption = true,
                inputAccessibility = false,
                media = false,
                microphone = false,
                bluetooth = false
            ),
            rows = emptyList(),
            onContinue = {},
            onOpenAppSettings = {},
            onRefresh = {}
        )
    }
}
