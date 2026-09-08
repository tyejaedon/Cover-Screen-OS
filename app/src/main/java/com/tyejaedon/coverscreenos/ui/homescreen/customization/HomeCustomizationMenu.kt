package com.tyejaedon.coverscreenos.ui.homescreen.customization

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Restore
import com.tyejaedon.coverscreenos.datastore.COVER_DOCK_SLOT_COUNT
import com.tyejaedon.coverscreenos.datastore.ThemePreference
import com.tyejaedon.coverscreenos.services.overlay.OverlayHostMode
import com.tyejaedon.coverscreenos.ui.settings.SettingsMenuItem

internal fun buildHomeCustomizationMenuItems(
    activePanel: HomeCustomizationPanel,
    dockFilledCount: Int,
    wallpaperSummary: String,
    themePreference: ThemePreference,
    overlayHostMode: OverlayHostMode,
    onPanelSelected: (HomeCustomizationPanel) -> Unit,
    onResetRequested: () -> Unit
): List<SettingsMenuItem> {
    val themeSummary = when (themePreference) {
        ThemePreference.SYSTEM -> "Follow system"
        ThemePreference.LIGHT -> "Always light"
        ThemePreference.DARK -> "Always dark"
    }
    val overlayHostModeSummary = when (overlayHostMode) {
        OverlayHostMode.ACCESSIBILITY -> "Accessibility overlay (TYPE_ACCESSIBILITY_OVERLAY)"
        OverlayHostMode.LEGACY_WINDOW -> "Legacy window (TYPE_APPLICATION_OVERLAY)"
    }

    return listOf(
        SettingsMenuItem(
            key = "dock",
            title = "Dock apps",
            summary = "$dockFilledCount of $COVER_DOCK_SLOT_COUNT slots filled",
            icon = Icons.Filled.Layers,
            selected = activePanel == HomeCustomizationPanel.DOCK,
            onClick = { onPanelSelected(HomeCustomizationPanel.DOCK) }
        ),
        SettingsMenuItem(
            key = "wallpaper",
            title = "Wallpaper customization",
            summary = wallpaperSummary,
            icon = Icons.Filled.Image,
            selected = activePanel == HomeCustomizationPanel.WALLPAPER,
            onClick = { onPanelSelected(HomeCustomizationPanel.WALLPAPER) }
        ),
        SettingsMenuItem(
            key = "input",
            title = "Input",
            summary = "Accessibility overlay keyboard",
            icon = Icons.Filled.Keyboard,
            selected = activePanel == HomeCustomizationPanel.INPUT,
            onClick = { onPanelSelected(HomeCustomizationPanel.INPUT) }
        ),
        SettingsMenuItem(
            key = "appearance",
            title = "Appearance",
            summary = themeSummary,
            icon = Icons.Filled.DarkMode,
            selected = activePanel == HomeCustomizationPanel.APPEARANCE,
            onClick = { onPanelSelected(HomeCustomizationPanel.APPEARANCE) }
        ),
        SettingsMenuItem(
            key = "developer",
            title = "Developer",
            summary = overlayHostModeSummary,
            icon = Icons.Filled.BugReport,
            selected = activePanel == HomeCustomizationPanel.DEVELOPER,
            onClick = { onPanelSelected(HomeCustomizationPanel.DEVELOPER) }
        ),
        SettingsMenuItem(
            key = "reset",
            title = "Reset layout",
            summary = "Restore dock and wallpaper defaults",
            icon = Icons.Filled.Restore,
            selected = false,
            onClick = onResetRequested
        )
    )
}

