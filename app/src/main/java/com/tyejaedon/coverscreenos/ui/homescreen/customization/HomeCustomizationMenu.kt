package com.tyejaedon.coverscreenos.ui.homescreen.customization

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Restore
import com.tyejaedon.coverscreenos.datastore.COVER_DOCK_SLOT_COUNT
import com.tyejaedon.coverscreenos.datastore.SearchInputMode
import com.tyejaedon.coverscreenos.datastore.ThemePreference
import com.tyejaedon.coverscreenos.ui.settings.SettingsMenuItem

internal fun buildHomeCustomizationMenuItems(
    activePanel: HomeCustomizationPanel,
    dockFilledCount: Int,
    wallpaperSummary: String,
    searchInputMode: SearchInputMode,
    themePreference: ThemePreference,
    onPanelSelected: (HomeCustomizationPanel) -> Unit,
    onResetRequested: () -> Unit
): List<SettingsMenuItem> {
    val themeSummary = when (themePreference) {
        ThemePreference.SYSTEM -> "Follow system"
        ThemePreference.LIGHT -> "Always light"
        ThemePreference.DARK -> "Always dark"
    }

    val inputSummary = when (searchInputMode) {
        SearchInputMode.T9 -> "Default: T9 keypad"
        SearchInputMode.SYSTEM_IME -> "Default: system keyboard"
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
            summary = inputSummary,
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
            key = "reset",
            title = "Reset layout",
            summary = "Restore dock and wallpaper defaults",
            icon = Icons.Filled.Restore,
            selected = false,
            onClick = onResetRequested
        )
    )
}

