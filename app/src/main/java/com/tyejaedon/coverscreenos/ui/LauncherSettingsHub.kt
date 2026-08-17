package com.tyejaedon.coverscreenos.ui

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.datastore.COVER_DOCK_SLOT_COUNT
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_SCALE_MODE
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.ThemePreference
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import com.tyejaedon.coverscreenos.ui.settings.AppearanceCustomizationCard
import com.tyejaedon.coverscreenos.ui.settings.DockAppPickerDialog
import com.tyejaedon.coverscreenos.ui.settings.DockCustomizationCard
import com.tyejaedon.coverscreenos.ui.settings.LauncherSettingsHeaderCard
import com.tyejaedon.coverscreenos.ui.settings.SettingsMenuItem
import com.tyejaedon.coverscreenos.ui.settings.SettingsQuickMenuCard
import com.tyejaedon.coverscreenos.ui.settings.WallpaperCustomizationCard
import com.tyejaedon.coverscreenos.ui.settings.normalizeDockPackageSlots
import com.tyejaedon.coverscreenos.ui.settings.updateDockSlotSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LauncherSettingsPanel {
    DOCK,
    WALLPAPER,
    APPEARANCE
}

private const val SETTINGS_HUB_LOG_TAG = "LauncherSettingsHub"

@Composable
fun LauncherSettingsHub(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val settingsStore = remember(appContext) { LauncherSettingsStore(appContext) }
    val repository = remember(appContext) { PackageManagerAppScannerRepository(appContext) }

    val settings by settingsStore.settings.collectAsState(initial = LauncherSettings())
    val appsState = produceState(initialValue = emptyList<AppModel>(), key1 = repository) {
        value = withContext(Dispatchers.Default) {
            runCatching { repository.scanInstalledApplications() }.getOrDefault(emptyList())
        }
    }
    val apps = appsState.value
    val appNameByPackage = remember(apps) {
        apps.associate { app -> app.packageName to app.name }
    }

    val persistedDockPackages = remember(settings.dockPackages) {
        normalizeDockPackageSlots(settings.dockPackages)
    }
    val defaultDockPackages = remember(apps) {
        normalizeDockPackageSlots(List(COVER_DOCK_SLOT_COUNT) { index -> apps.getOrNull(index)?.packageName })
    }
    val hasCustomDockSelection = persistedDockPackages.any { !it.isNullOrBlank() }
    val dockPackagesForEditor = if (hasCustomDockSelection) persistedDockPackages else defaultDockPackages

    var dockPackagePreview by remember(dockPackagesForEditor) {
        mutableStateOf(dockPackagesForEditor)
    }
    var wallpaperScaleModePreview by remember(settings.wallpaperScaleMode) {
        mutableStateOf(settings.wallpaperScaleMode)
    }
    var wallpaperDimPreview by remember(settings.wallpaperDimAmount) {
        val sanitizedDimAmount = settings.wallpaperDimAmount
            .takeIf { it.isFinite() }
            ?.coerceIn(MIN_WALLPAPER_DIM_AMOUNT, MAX_WALLPAPER_DIM_AMOUNT)
            ?: DEFAULT_WALLPAPER_DIM_AMOUNT
        mutableFloatStateOf(sanitizedDimAmount)
    }
    var wallpaperBlurPreview by remember(settings.wallpaperBlurRadiusDp) {
        val sanitizedBlurRadius = settings.wallpaperBlurRadiusDp
            .takeIf { it.isFinite() }
            ?.coerceIn(MIN_WALLPAPER_BLUR_RADIUS_DP, MAX_WALLPAPER_BLUR_RADIUS_DP)
            ?: DEFAULT_WALLPAPER_BLUR_RADIUS_DP
        mutableFloatStateOf(sanitizedBlurRadius)
    }
    var themePreferencePreview by remember(settings.themePreference) {
        mutableStateOf(settings.themePreference)
    }

    var activeDockSlotIndex by remember { mutableStateOf<Int?>(null) }
    var activePanel by remember { mutableStateOf(LauncherSettingsPanel.DOCK) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var isWallpaperImportInProgress by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (isWallpaperImportInProgress) return@rememberLauncherForActivityResult
        scope.launch {
            isWallpaperImportInProgress = true
            try {
                val wallpaperImported = settingsStore.importWallpaperFromUri(uri)
                if (!wallpaperImported) {
                    snackbarHostState.showSnackbar(
                        message = "Selected wallpaper could not be imported. Try another image."
                    )
                } else {
                    snackbarHostState.showSnackbar(message = "Wallpaper updated")
                }
            } finally {
                isWallpaperImportInProgress = false
            }
        }
    }

    fun launchWallpaperPicker() {
        if (isWallpaperImportInProgress) return

        runCatching {
            wallpaperPickerLauncher.launch("image/*")
        }.onFailure { error ->
            Log.w(SETTINGS_HUB_LOG_TAG, "Unable to launch wallpaper picker: ${error.message}")
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Unable to open image picker on this device."
                )
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LauncherSettingsHeaderCard(modifier = Modifier.fillMaxWidth())

        val dockFilledCount = dockPackagePreview.count { !it.isNullOrBlank() }
        val wallpaperSummary = if (settings.wallpaperUri.isNullOrBlank()) {
            "Pure black background"
        } else {
            "Custom image | Dim ${(wallpaperDimPreview * 100f).toInt()}% | Blur ${wallpaperBlurPreview.toInt()}dp"
        }
        val themeSummary = when (themePreferencePreview) {
            ThemePreference.SYSTEM -> "Follow system"
            ThemePreference.LIGHT -> "Always light"
            ThemePreference.DARK -> "Always dark"
        }

        SettingsQuickMenuCard(
            items = listOf(
                SettingsMenuItem(
                    key = "dock",
                    title = "Dock apps",
                    summary = "$dockFilledCount of $COVER_DOCK_SLOT_COUNT slots filled",
                    icon = Icons.Filled.Layers,
                    selected = activePanel == LauncherSettingsPanel.DOCK,
                    onClick = { activePanel = LauncherSettingsPanel.DOCK }
                ),
                SettingsMenuItem(
                    key = "wallpaper",
                    title = "Wallpaper",
                    summary = wallpaperSummary,
                    icon = Icons.Filled.Image,
                    selected = activePanel == LauncherSettingsPanel.WALLPAPER,
                    onClick = { activePanel = LauncherSettingsPanel.WALLPAPER }
                ),
                SettingsMenuItem(
                    key = "appearance",
                    title = "Appearance",
                    summary = themeSummary,
                    icon = Icons.Filled.DarkMode,
                    selected = activePanel == LauncherSettingsPanel.APPEARANCE,
                    onClick = { activePanel = LauncherSettingsPanel.APPEARANCE }
                ),
                SettingsMenuItem(
                    key = "reset",
                    title = "Reset layout",
                    summary = "Restore dock and wallpaper defaults",
                    icon = Icons.Filled.Restore,
                    selected = false,
                    onClick = { showResetConfirmDialog = true }
                )
            )
        )

        if (activePanel == LauncherSettingsPanel.DOCK) {
            DockCustomizationCard(
                dockPackages = dockPackagePreview,
                resolveLabel = { packageName ->
                    packageName?.let { appNameByPackage[it] ?: packageName } ?: "Empty"
                },
                onPreviewReorder = { reorderedDock ->
                    dockPackagePreview = reorderedDock
                },
                onReorderCommitted = { committedDock ->
                    scope.launch {
                        settingsStore.setDockPackages(committedDock)
                    }
                },
                onPickSlot = { slotIndex -> activeDockSlotIndex = slotIndex },
                onClearSlot = { slotIndex ->
                    val updatedDock = dockPackagePreview.toMutableList().also { slots ->
                        slots[slotIndex] = null
                    }
                    val normalizedDock = normalizeDockPackageSlots(updatedDock)
                    dockPackagePreview = normalizedDock
                    scope.launch {
                        settingsStore.setDockPackages(normalizedDock)
                    }
                }
            )
        }

        if (activePanel == LauncherSettingsPanel.APPEARANCE) {
            AppearanceCustomizationCard(
                themePreference = themePreferencePreview,
                onThemePreferenceSelected = { selectedPreference ->
                    themePreferencePreview = selectedPreference
                    scope.launch {
                        settingsStore.setThemePreference(selectedPreference)
                    }
                }
            )
        }

        if (activePanel == LauncherSettingsPanel.WALLPAPER) {
            WallpaperCustomizationCard(
                wallpaperUri = settings.wallpaperUri,
                wallpaperScaleMode = wallpaperScaleModePreview,
                dimAmount = wallpaperDimPreview,
                blurRadiusDp = wallpaperBlurPreview,
                isWallpaperImportInProgress = isWallpaperImportInProgress,
                onChooseWallpaper = {
                    if (!isWallpaperImportInProgress) {
                        launchWallpaperPicker()
                    }
                },
                onClearWallpaper = {
                    scope.launch {
                        settingsStore.clearWallpaper()
                    }
                },
                onScaleModeSelected = { selectedMode ->
                    wallpaperScaleModePreview = selectedMode
                    scope.launch {
                        settingsStore.setWallpaperScaleMode(selectedMode)
                    }
                },
                onDimAmountPreviewChanged = { dimAmount ->
                    wallpaperDimPreview = dimAmount
                },
                onDimAmountCommit = {
                    scope.launch {
                        settingsStore.setWallpaperDimAmount(wallpaperDimPreview)
                    }
                },
                onBlurRadiusPreviewChanged = { blurRadius ->
                    wallpaperBlurPreview = blurRadius
                },
                onBlurRadiusCommit = {
                    scope.launch {
                        settingsStore.setWallpaperBlurRadiusDp(wallpaperBlurPreview)
                    }
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (activeDockSlotIndex != null) {
        DockAppPickerDialog(
            apps = apps,
            onDismiss = { activeDockSlotIndex = null },
            onAppSelected = { app ->
                val targetIndex = activeDockSlotIndex ?: return@DockAppPickerDialog
                val updatedDock = updateDockSlotSelection(
                    dockPackages = dockPackagePreview,
                    slotIndex = targetIndex,
                    packageName = app.packageName
                )
                dockPackagePreview = updatedDock
                scope.launch {
                    settingsStore.setDockPackages(updatedDock)
                }
                activeDockSlotIndex = null
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset launcher layout?") },
            text = {
                Text(
                    "This will clear dock customizations and wallpaper selection, and restore default wallpaper style controls."
                )
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val launcherSnapshotBeforeReset = LauncherSettings(
                            dockPackages = dockPackagePreview,
                            wallpaperUri = settings.wallpaperUri,
                            wallpaperScaleMode = wallpaperScaleModePreview,
                            wallpaperDimAmount = wallpaperDimPreview,
                            wallpaperBlurRadiusDp = wallpaperBlurPreview,
                            isDockVisible = settings.isDockVisible,
                            themePreference = themePreferencePreview
                        )

                        showResetConfirmDialog = false
                        activeDockSlotIndex = null
                        dockPackagePreview = defaultDockPackages
                        wallpaperScaleModePreview = DEFAULT_WALLPAPER_SCALE_MODE
                        wallpaperDimPreview = DEFAULT_WALLPAPER_DIM_AMOUNT
                        wallpaperBlurPreview = DEFAULT_WALLPAPER_BLUR_RADIUS_DP
                        scope.launch {
                            settingsStore.resetLauncherLayout()

                            snackbarHostState.currentSnackbarData?.dismiss()
                            val snackbarResult = snackbarHostState.showSnackbar(
                                message = "Launcher layout reset",
                                actionLabel = "Undo",
                                withDismissAction = true,
                                duration = SnackbarDuration.Long
                            )

                            if (snackbarResult == SnackbarResult.ActionPerformed) {
                                settingsStore.setLauncherLayout(launcherSnapshotBeforeReset)

                                val restoredDockPackages = normalizeDockPackageSlots(
                                    launcherSnapshotBeforeReset.dockPackages
                                )
                                dockPackagePreview = if (restoredDockPackages.any { !it.isNullOrBlank() }) {
                                    restoredDockPackages
                                } else {
                                    defaultDockPackages
                                }
                                wallpaperScaleModePreview = launcherSnapshotBeforeReset.wallpaperScaleMode
                                wallpaperDimPreview = launcherSnapshotBeforeReset.wallpaperDimAmount
                                    .coerceIn(MIN_WALLPAPER_DIM_AMOUNT, MAX_WALLPAPER_DIM_AMOUNT)
                                wallpaperBlurPreview = launcherSnapshotBeforeReset.wallpaperBlurRadiusDp
                                    .coerceIn(MIN_WALLPAPER_BLUR_RADIUS_DP, MAX_WALLPAPER_BLUR_RADIUS_DP)
                                themePreferencePreview = launcherSnapshotBeforeReset.themePreference
                            }
                        }
                    }
                ) {
                    Text("Reset")
                }
            }
        )
    }
}


