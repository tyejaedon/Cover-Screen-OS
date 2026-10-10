package com.tyejaedon.coverscreenos.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tyejaedon.coverscreenos.helpers.AppPermissionHelper
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget

private const val LOG_TAG = "PermissionsScreen"

internal enum class PermissionGroup { REQUIRED, RECOMMENDED, OPTIONAL }

internal enum class PermissionId(val group: PermissionGroup) {
    NOTIFICATIONS(PermissionGroup.REQUIRED),
    LAUNCHER_ACCESSIBILITY(PermissionGroup.REQUIRED),
    NOTIFICATION_LISTENER(PermissionGroup.REQUIRED),
    BATTERY_EXEMPTION(PermissionGroup.REQUIRED),
    INPUT_ACCESSIBILITY(PermissionGroup.RECOMMENDED),
    MEDIA(PermissionGroup.OPTIONAL),
    MICROPHONE(PermissionGroup.OPTIONAL)
}

internal data class PermissionGrants(
    val notifications: Boolean,
    val launcherAccessibility: Boolean,
    val notificationListener: Boolean,
    val batteryExemption: Boolean,
    val inputAccessibility: Boolean,
    val media: Boolean,
    val microphone: Boolean
) {
    fun granted(id: PermissionId): Boolean = when (id) {
        PermissionId.NOTIFICATIONS -> notifications
        PermissionId.LAUNCHER_ACCESSIBILITY -> launcherAccessibility
        PermissionId.NOTIFICATION_LISTENER -> notificationListener
        PermissionId.BATTERY_EXEMPTION -> batteryExemption
        PermissionId.INPUT_ACCESSIBILITY -> inputAccessibility
        PermissionId.MEDIA -> media
        PermissionId.MICROPHONE -> microphone
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
            microphone = AppPermissionHelper.hasMicrophonePermission(context)
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

    fun openSettings(intent: Intent) {
        runCatching { settingsLauncher.launch(intent) }
            .onFailure { Log.w(LOG_TAG, "Unable to open ${intent.action}", it) }
    }

    val rows = listOf(
        PermissionRowModel(
            PermissionId.NOTIFICATIONS, "Notification permission",
            "Allows the cover launcher's foreground notification.",
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
            PermissionId.LAUNCHER_ACCESSIBILITY, "Launcher accessibility",
            "Hosts the cover launcher on the outer display.",
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
        )
    )

    PermissionsContent(
        grants = grants,
        rows = rows,
        onContinue = onContinue,
        onOpenAppSettings = { openSettings(AppPermissionHelper.createAppDetailsSettingsIntent(context)) },
        onRefresh = ::refresh,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PermissionHealthRing(grants.healthPercent)
            }
            PermissionGroup.entries.forEach { group ->
                stickyHeader {
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            group.name.lowercase().replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                items(rows.filter { it.id.group == group }, key = { it.id }) { row ->
                    PermissionRow(row)
                }
            }
            item {
                PermissionSupportActions(
                    onOpenAppSettings = onOpenAppSettings,
                    onRefresh = onRefresh
                )
            }
        }
        if (grants.requiredMissing) {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).coverMinimumTouchTarget()
                ) {
                    Text("Continue with limited features")
                }
            }
        }
    }
}

@Composable
private fun PermissionHealthRing(percentage: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier.size(88.dp).semantics {
                    contentDescription = "Permission health"
                    stateDescription = "$percentage percent"
                },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { percentage / 100f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 7.dp
                )
                Text("$percentage%", style = MaterialTheme.typography.titleLarge)
            }
            Column {
                Text("Permission health", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Required and recommended setup. Optional access does not affect this score.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(row: PermissionRowModel) {
    Card(
        onClick = row.onAction,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            stateDescription = "${row.title} -- ${if (row.granted) "granted" else "denied"} -- tap to change"
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(row.icon, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(row.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        row.rationale, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (row.granted) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        if (row.granted) "Granted" else "Denied",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            OutlinedButton(
                onClick = row.onAction,
                modifier = Modifier.align(Alignment.End).coverMinimumTouchTarget()
                    .semantics { contentDescription = "${row.title} -- ${if (row.granted) "granted" else "denied"} -- tap to change" },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(if (row.granted) "Change" else "Enable")
            }
        }
    }
}
