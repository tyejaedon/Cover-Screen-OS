package com.tyejaedon.coverscreenos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.services.overlay.OverlayHostMode
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusMedium
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding

/**
 * Developer/debug card exposing the Phase 3 [OverlayHostMode] toggle.
 *
 * Visible only from the home customization hub's "Developer" panel.
 * Flipping the selection persists through
 * [com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore.setOverlayHostMode];
 * [com.tyejaedon.coverscreenos.services.overlay.OverlayWindowController]
 * picks up the change on the next dispatch and tears down / rebuilds
 * hosts as needed.
 *
 * See `docs/architecture/Overlay-architecture-shift-plan.md` §6.3.
 */
object DeveloperCustomizationUiTestTags {
    const val CARD_ROOT = "developer_customization_card_root"
    const val OVERLAY_HOST_MODE_ACCESSIBILITY = "developer_overlay_host_mode_accessibility"
    const val OVERLAY_HOST_MODE_LEGACY_WINDOW = "developer_overlay_host_mode_legacy_window"
}

@Composable
internal fun DeveloperCustomizationCard(
    overlayHostMode: OverlayHostMode,
    onOverlayHostModeSelected: (OverlayHostMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(DeveloperCustomizationUiTestTags.CARD_ROOT),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(CoverOSCornerRadiusMedium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .coverScreenPadding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Overlay host (debug)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Selects how the cover launcher window is attached. Accessibility uses " +
                    "TYPE_ACCESSIBILITY_OVERLAY via the accessibility service. Legacy uses " +
                    "TYPE_APPLICATION_OVERLAY via the foreground service and requires the " +
                    "\"Display over other apps\" permission.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverlayHostMode.entries.forEach { option ->
                    val label = when (option) {
                        OverlayHostMode.ACCESSIBILITY -> "Accessibility"
                        OverlayHostMode.LEGACY_WINDOW -> "Legacy window"
                    }
                    val optionTag = when (option) {
                        OverlayHostMode.ACCESSIBILITY ->
                            DeveloperCustomizationUiTestTags.OVERLAY_HOST_MODE_ACCESSIBILITY
                        OverlayHostMode.LEGACY_WINDOW ->
                            DeveloperCustomizationUiTestTags.OVERLAY_HOST_MODE_LEGACY_WINDOW
                    }
                    val selected = overlayHostMode == option
                    if (selected) {
                        Button(
                            onClick = { onOverlayHostModeSelected(option) },
                            modifier = Modifier
                                .weight(1f)
                                .coverMinimumTouchTarget()
                                .testTag(optionTag)
                        ) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onOverlayHostModeSelected(option) },
                            modifier = Modifier
                                .weight(1f)
                                .coverMinimumTouchTarget()
                                .testTag(optionTag)
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}

