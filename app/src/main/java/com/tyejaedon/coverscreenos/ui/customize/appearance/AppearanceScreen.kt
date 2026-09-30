package com.tyejaedon.coverscreenos.ui.customize.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.datastore.AccentColor
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.datastore.MAX_PANEL_CORNER_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.MIN_PANEL_CORNER_RADIUS_DP
import com.tyejaedon.coverscreenos.ui.theme.LocalCoverPanelCornerRadius
import com.tyejaedon.coverscreenos.ui.theme.coverAccentScheme
import androidx.compose.foundation.isSystemInDarkTheme
import com.tyejaedon.coverscreenos.datastore.ThemePreference
import kotlin.math.roundToInt

@Composable
internal fun AppearanceScreen(
    store: LauncherSettingsStore,
    settings: LauncherSettings,
    onAction: (String?, suspend () -> Unit) -> Unit
) {
    val dark = when (settings.themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
    }
    var previewRadius by remember(settings.panelCornerRadiusDp) { mutableFloatStateOf(settings.panelCornerRadiusDp) }
    AppearanceCustomizationCard(
        themePreference = settings.themePreference,
        onThemePreferenceSelected = { choice -> onAction(null) { store.setThemePreference(choice) } },
        useSegmentedButtons = true
    )
    Card(shape = RoundedCornerShape(LocalCoverPanelCornerRadius.current)) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Accent color", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentColor.entries.forEach { accent ->
                    val selected = settings.accentColor == accent
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier.size(48.dp)
                            .background(coverAccentScheme(accent, dark).primary, shape)
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, shape)
                            .semantics { contentDescription = "${accent.name.lowercase()} accent"; this.selected = selected }
                            .clickable { onAction(null) { store.setAccentColor(accent) } }
                    )
                }
            }
            Text("Overlay panel corners: ${previewRadius.roundToInt()} dp")
            Slider(
                value = previewRadius,
                onValueChange = { previewRadius = it },
                onValueChangeFinished = {
                    val chosen = previewRadius
                    onAction(null) { store.setPanelCornerRadiusDp(chosen) }
                },
                valueRange = MIN_PANEL_CORNER_RADIUS_DP..MAX_PANEL_CORNER_RADIUS_DP
            )
            Text("Panel preview", style = MaterialTheme.typography.labelMedium)
            Box(
                Modifier.size(width = 160.dp, height = 68.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(previewRadius.dp))
            )
        }
    }
}
