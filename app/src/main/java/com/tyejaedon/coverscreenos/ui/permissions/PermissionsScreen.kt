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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
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

// Log tag used when an OEM device fails to resolve a System Settings Intent.
private const val LOG_TAG = "PermissionsScreen"

/**
 * Categorizes permissions into onboarding priority tiers shown as distinct cards.
 */
internal enum class PermissionGroup { REQUIRED, RECOMMENDED, OPTIONAL }

/**
 * Identifiers for every runtime permission and special system access tracked by [PermissionsScreen].
 */
internal enum class PermissionId(
    /** Priority section under which this permission appears in the onboarding list. */
    val group: PermissionGroup
) {
    NOTIFICATIONS(PermissionGroup.REQUIRED),
    LAUNCHER_ACCESSIBILITY(PermissionGroup.REQUIRED),
    NOTIFICATION_LISTENER(PermissionGroup.REQUIRED),
    BATTERY_EXEMPTION(PermissionGroup.REQUIRED),
    INPUT_ACCESSIBILITY(PermissionGroup.RECOMMENDED),
    MEDIA(PermissionGroup.OPTIONAL),
    MICROPHONE(PermissionGroup.OPTIONAL),
    BLUETOOTH(PermissionGroup.OPTIONAL)
}

/**
 * Complete snapshot of all permission and special-access grant states evaluated by [PermissionsScreen].
 */
internal data class PermissionGrants(
    /** Required (Android 13+) to keep the foreground cover service notification visible. */
    val notifications: Boolean,
    /** Required so the cover launcher AccessibilityService can host the outer-display overlay. */
    val launcherAccessibility: Boolean,
    /** Required to receive active notifications and media metadata for cover-screen display. */
    val notificationListener: Boolean,
    /** Required to prevent Android from throttling or killing the background cover service. */
    val batteryExemption: Boolean,
    /** Recommended so cover-screen keyboard fallback and text injection work reliably. */
    val inputAccessibility: Boolean,
    /** Optional gallery/photo read access for custom wallpaper imports. */
    val media: Boolean,
    /** Optional audio recording access for voice search on the cover screen. */
    val microphone: Boolean,
    /** Optional Bluetooth Connect access for Quick Settings Bluetooth status and toggling. */
    val bluetooth: Boolean
) {
    /** Looks up the grant status corresponding to [id]. */
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

    /** `true` if any [PermissionGroup.REQUIRED] capability is still missing. */
    val requiredMissing: Boolean get() = PermissionId.entries.any {
        it.group == PermissionGroup.REQUIRED && !granted(it)
    }

    /** Percentage (`0..100`) of [PermissionGroup.REQUIRED] and [PermissionGroup.RECOMMENDED] grants enabled. */
    val healthPercent: Int get() {
        val counted = PermissionId.entries.filter { it.group != PermissionGroup.OPTIONAL }
        return counted.count(::granted) * 100 / counted.size
    }

    companion object {
        /** Reads live grant status across runtime permissions and system settings via [AppPermissionHelper]. */
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

/**
 * UI display model for a single interactive row in [PermissionsScreen].
 */
internal data class PermissionRowModel(
    val id: PermissionId,
    val title: String,
    val rationale: String,
    val icon: ImageVector,
    val granted: Boolean,
    val onAction: () -> Unit
)

/**
 * Permission onboarding and diagnostics screen.
 *
 * Automatically re-polls permission grants via a [LifecycleEventObserver] (`ON_RESUME`) when the
 * user returns from external Android Settings screens (Accessibility, Notification Access, Battery
 * Optimization, or App Info) and registers [ActivityResultContracts] launchers for both runtime
 * permissions (`POST_NOTIFICATIONS`, media, `RECORD_AUDIO`, `BLUETOOTH_CONNECT`) and settings
 * activities, notifying [onPermissionsChanged] on each refresh.
 */
@Composable
fun PermissionsScreen(
    onContinue: () -> Unit,
    onPermissionsChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Android Context used to read permission states and construct Settings intents.
    val context = LocalContext.current
    // Lifecycle owner observed for ON_RESUME so changes made in System Settings take effect on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    // Latest permission snapshot driving the health ring, guidance card, and every checklist row.
    var grants by remember(context) { mutableStateOf(PermissionGrants.read(context)) }
    fun refresh() {
        grants = PermissionGrants.read(context)
        onPermissionsChanged()
    }

    // Re-read permission states whenever the host resumes from an external System Settings screen.
    DisposableEffect(context, lifecycleOwner) {
        // Lifecycle listener that runs refresh() automatically when the user navigates back into the app.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Launcher for external Android Settings screens (Accessibility, Notification Listener, Battery, App Details); refreshes on return.
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }
    // Launcher for the Android 13+ POST_NOTIFICATIONS runtime dialog.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }
    // Launcher for gallery/selected-photos runtime permissions (may request multiple permissions depending on API level).
    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }
    // Launcher for the RECORD_AUDIO runtime permission dialog used by cover-screen voice search.
    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }
    // Launcher for the BLUETOOTH_CONNECT runtime permission dialog used by Quick Settings Bluetooth controls.
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    // Guard settings Intent launches against OEM builds that omit or rename a target settings screen.
    fun openSettings(intent: Intent) {
        try {
            settingsLauncher.launch(intent)
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "Failed launching settings intent $intent", t)
        }
    }

    // Ordered checklist of all 8 tracked permissions; runtime ones prompt directly until granted, then open App Details, while special-access rows open System Settings.
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
        actions = {
            IconButton(onClick = ::refresh) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Refresh permissions",
                    tint = OneUITheme.colors.textSecondary
                )
            }
            TextButton(onClick = onContinue) {
                Text(
                    if (grants.requiredMissing) "Skip" else "Done",
                    color = OneUITheme.colors.accent
                )
            }
            IconButton(onClick = onContinue) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss permissions",
                    tint = OneUITheme.colors.textSecondary
                )
            }
        },
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

/**
 * Stateless scrollable body of [PermissionsScreen].
 *
 * Renders [PermissionHealthRing], grouped [OneUICardGroup] sections per [PermissionGroup], an
 * inline [RestrictedAccessibilityHelp] card when the launcher accessibility service is still
 * disabled (guiding users past Android 13+ restricted-settings restrictions),
 * [PermissionSupportActions], and a `"Continue with limited features"` CTA when required grants
 * remain incomplete.
 */
@Composable
internal fun PermissionsContent(
    grants: PermissionGrants,
    rows: List<PermissionRowModel>,
    onContinue: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    // True on short viewports (<= 480dp) to tighten vertical card spacing so more rows fit on screen.
    val isCompactHeight = LocalConfiguration.current.screenHeightDp <= 480
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 10.dp else OneUITheme.dimensions.cardSpacing)
    ) {
        item {
            OneUICardGroup(header = "Overview") {
                PermissionHealthRing(grants.healthPercent, compact = isCompactHeight)
            }
        }

        PermissionGroup.entries.forEach { group ->
            // Subset of permission rows belonging to the current tier (Required, Recommended, or Optional).
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

                // Surface restricted-settings unlock guidance right below Required Access until enabled.
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

        if (grants.requiredMissing) {
            item(key = "continue-limited") {
                Button(
                    onClick = onContinue,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = OneUITheme.colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .coverMinimumTouchTarget()
                ) {
                    Text("Continue with limited features", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

/**
 * Guidance card explaining how to unlock restricted Accessibility settings on Android 13+
 * sideloaded/non-Play installs via App Info ([onOpenAppSettings]).
 */
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

/**
 * Circular setup-progress ring displaying [percentage] of required and recommended grants.
 *
 * Scales down ring size and typography when [compact] height (`<= 480.dp`) is active.
 */
@Composable
private fun PermissionHealthRing(percentage: Int, compact: Boolean = false) {
    // Diameter of the progress ring: scaled down on compact-height layouts so the Overview card stays slim.
    val ringSize = if (compact) 54.dp else 80.dp
    Row(
        modifier = Modifier.fillMaxWidth().padding(if (compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp)
    ) {
        Box(
            modifier = Modifier.size(ringSize).semantics {
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
                strokeWidth = if (compact) 5.dp else 6.dp
            )
            Text(
                "$percentage%",
                style = if (compact) OneUITheme.typography.labelMedium else OneUITheme.typography.titleLarge,
                color = OneUITheme.colors.textPrimary
            )
        }
        Column {
            Text(
                "Permission health",
                style = if (compact) OneUITheme.typography.bodyLarge else OneUITheme.typography.titleLarge,
                color = OneUITheme.colors.textPrimary
            )
            Text(
                "Required and recommended setup for cover screen operation.",
                style = OneUITheme.typography.bodyMedium,
                color = OneUITheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Interactive OneUI permission list row showing an encircled icon, title, rationale, and
 * `"Ready"` / `"Set Up"` status pill with merged TalkBack semantics.
 */
@Composable
private fun PermissionRow(row: PermissionRowModel) {
    // Shared press-interaction stream driving OneUI's subtle squeeze effect without Material ripple.
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
            // Merge child text into one accessible node announcing title and grant status together.
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
                maxLines = 2,
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

/**
 * Light and OLED Dark Mode Compose previews for [PermissionsContent] with mixed grant states.
 */
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
