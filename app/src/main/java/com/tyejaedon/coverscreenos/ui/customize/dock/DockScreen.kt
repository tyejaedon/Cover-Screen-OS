package com.tyejaedon.coverscreenos.ui.customize.dock

import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tyejaedon.coverscreenos.datastore.COVER_DOCK_SLOT_COUNT
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dock customization category screen.
 *
 * Asynchronously scans installed applications via [PackageManagerAppScannerRepository], lets the
 * user toggle dock visibility and the "Slot 4: All apps" override, and binds drag-to-reorder and
 * slot-selection state to [LauncherSettingsStore].
 */
@Composable
internal fun DockScreen(
    store: LauncherSettingsStore,
    settings: LauncherSettings,
    onAction: (String?, suspend () -> Unit) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { PackageManagerAppScannerRepository(context) }
    // Scan installed launchable apps off the main thread when the repository instance initializes.
    val scan by produceState<Result<List<AppModel>>?>(initialValue = null, key1 = repository) {
        value = withContext(Dispatchers.Default) { runCatching { repository.scanInstalledApplications() } }
    }
    val apps = scan?.getOrNull().orEmpty()
    val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }
    val stored = remember(settings.dockPackages) { normalizeDockPackageSlots(settings.dockPackages) }
    val defaults = remember(apps) {
        normalizeDockPackageSlots(List(COVER_DOCK_SLOT_COUNT) { apps.getOrNull(it)?.packageName })
    }
    // Seed the editor with the first scanned apps until the user has at least one persisted slot.
    val editorSlots = if (stored.any { it != null }) stored else defaults
    // Local preview mirrors drag changes immediately and commits to DataStore when the gesture ends.
    var preview by remember(editorSlots) { mutableStateOf(editorSlots) }
    var activeSlot by rememberSaveable { mutableStateOf<Int?>(null) }

    if (scan?.isFailure == true || (scan?.isSuccess == true && apps.isEmpty())) {
        Text("Installed apps are unavailable. Existing dock slots can still be reordered or cleared.")
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Show dock on cover screen")
        Switch(
            checked = settings.isDockVisible,
            onCheckedChange = { visible -> onAction(null) { store.setDockVisible(visible) } }
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Slot 4: All apps")
        Switch(
            checked = settings.dockSlotFourAllApps,
            onCheckedChange = { enabled -> onAction(null) { store.setDockSlotFourAllApps(enabled) } }
        )
    }
    if (settings.dockSlotFourAllApps) {
        Text("Slot 4 opens the app grid. Your pinned app is saved and returns when this is turned off.")
    }
    val dockContext = androidx.compose.ui.platform.LocalContext.current
    DockCustomizationCard(
        dockPackages = preview,
        resolveLabel = { name -> name?.let { appsByPackage[it]?.name ?: it } ?: "Empty" },
        resolveIcon = { pkg ->
            // Fall back to PackageManager directly if a persisted package is not in the scanned map.
            appsByPackage[pkg]?.iconDrawable ?: runCatching {
                dockContext.packageManager.getApplicationIcon(pkg)
            }.getOrNull()
        },
        longPressToClear = true,
        onPreviewReorder = { preview = it },
        onReorderCommitted = { reordered -> onAction(null) { store.setDockPackages(reordered) } },
        onPickSlot = { activeSlot = it },
        onClearSlot = { index ->
            val updated = normalizeDockPackageSlots(preview.toMutableList().also { it[index] = null })
            preview = updated
            onAction(null) { store.setDockPackages(updated) }
        }
    )
    if (activeSlot != null) {
        DockAppPickerDialog(
            apps = apps,
            onDismiss = { activeSlot = null },
            onAppSelected = { app ->
                val index = activeSlot
                if (index != null) {
                    val updated = updateDockSlotSelection(preview, index, app.packageName)
                    preview = updated
                    onAction(null) { store.setDockPackages(updated) }
                }
                activeSlot = null
            }
        )
    }
}
