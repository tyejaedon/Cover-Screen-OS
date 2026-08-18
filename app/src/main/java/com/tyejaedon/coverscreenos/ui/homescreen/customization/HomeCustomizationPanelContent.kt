package com.tyejaedon.coverscreenos.ui.homescreen.customization

import androidx.compose.runtime.Composable
import com.tyejaedon.coverscreenos.datastore.ThemePreference
import com.tyejaedon.coverscreenos.datastore.WallpaperScaleMode
import com.tyejaedon.coverscreenos.ui.settings.AppearanceCustomizationCard
import com.tyejaedon.coverscreenos.ui.settings.DockCustomizationCard
import com.tyejaedon.coverscreenos.ui.settings.WallpaperCustomizationCard

@Composable
internal fun HomeCustomizationPanelContent(
    activePanel: HomeCustomizationPanel,
    dockPackages: List<String?>,
    resolveLabel: (String?) -> String,
    onPreviewReorder: (List<String?>) -> Unit,
    onReorderCommitted: (List<String?>) -> Unit,
    onPickSlot: (Int) -> Unit,
    onClearSlot: (Int) -> Unit,
    themePreference: ThemePreference,
    onThemePreferenceSelected: (ThemePreference) -> Unit,
    wallpaperUri: String?,
    wallpaperScaleMode: WallpaperScaleMode,
    dimAmount: Float,
    blurRadiusDp: Float,
    isWallpaperImportInProgress: Boolean,
    onChooseWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onScaleModeSelected: (WallpaperScaleMode) -> Unit,
    onDimAmountPreviewChanged: (Float) -> Unit,
    onDimAmountCommit: () -> Unit,
    onBlurRadiusPreviewChanged: (Float) -> Unit,
    onBlurRadiusCommit: () -> Unit
) {
    if (activePanel == HomeCustomizationPanel.DOCK) {
        DockCustomizationCard(
            dockPackages = dockPackages,
            resolveLabel = resolveLabel,
            onPreviewReorder = onPreviewReorder,
            onReorderCommitted = onReorderCommitted,
            onPickSlot = onPickSlot,
            onClearSlot = onClearSlot
        )
    }

    if (activePanel == HomeCustomizationPanel.APPEARANCE) {
        AppearanceCustomizationCard(
            themePreference = themePreference,
            onThemePreferenceSelected = onThemePreferenceSelected
        )
    }

    if (activePanel == HomeCustomizationPanel.WALLPAPER) {
        WallpaperCustomizationCard(
            wallpaperUri = wallpaperUri,
            wallpaperScaleMode = wallpaperScaleMode,
            dimAmount = dimAmount,
            blurRadiusDp = blurRadiusDp,
            isWallpaperImportInProgress = isWallpaperImportInProgress,
            onChooseWallpaper = onChooseWallpaper,
            onClearWallpaper = onClearWallpaper,
            onScaleModeSelected = onScaleModeSelected,
            onDimAmountPreviewChanged = onDimAmountPreviewChanged,
            onDimAmountCommit = onDimAmountCommit,
            onBlurRadiusPreviewChanged = onBlurRadiusPreviewChanged,
            onBlurRadiusCommit = onBlurRadiusCommit
        )
    }
}

