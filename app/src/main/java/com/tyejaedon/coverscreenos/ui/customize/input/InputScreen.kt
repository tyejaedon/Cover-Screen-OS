package com.tyejaedon.coverscreenos.ui.customize.input

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tyejaedon.coverscreenos.BuildConfig
import com.tyejaedon.coverscreenos.datastore.CoverAppKeyboardStrategy
import com.tyejaedon.coverscreenos.datastore.KeyboardStrategy
import com.tyejaedon.coverscreenos.datastore.HapticStrength
import com.tyejaedon.coverscreenos.datastore.KeyboardAnchor
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.keyboard.floris.FlorisSmartbar

/**
 * Input customization category screen forwarding persisted [LauncherSettings] and update callbacks
 * to [InputCustomizationCard].
 *
 * In [BuildConfig.DEBUG] builds, also exposes an isolated sandbox preview of [FlorisSmartbar]
 * for QA verification without modifying the active system IME.
 */
@Composable
internal fun InputScreen(
    settings: LauncherSettings,
    onStrategy: (KeyboardStrategy) -> Unit,
    onDefaultCoverAppStrategy: (CoverAppKeyboardStrategy) -> Unit,
    onCoverAppStrategyForPackage: (String, CoverAppKeyboardStrategy?) -> Unit,
    onClearCoverAppStrategies: () -> Unit,
    onPicker: () -> Unit,
    onSettings: () -> Unit,
    onResetModes: () -> Unit,
    onHapticStrength: (HapticStrength) -> Unit,
    onKeyPressSoundEnabledChanged: (Boolean) -> Unit,
    onSuggestionStripVisibleChanged: (Boolean) -> Unit,
    onAutocorrectEnabledChanged: (Boolean) -> Unit,
    onPopupKeyPreviewEnabledChanged: (Boolean) -> Unit,
    onKeyboardExtraHeightChanged: (Float) -> Unit,
    onKeyboardOneHandedEnabledChanged: (Boolean) -> Unit,
    onKeyboardOneHandedAnchorChanged: (KeyboardAnchor) -> Unit,
    refreshNonce: Int
) {
    InputCustomizationCard(
        keyboardStrategy = settings.keyboardStrategy,
        onKeyboardStrategySelected = onStrategy,
        defaultCoverAppKeyboardStrategy = settings.defaultCoverAppKeyboardStrategy,
        onDefaultCoverAppKeyboardStrategySelected = onDefaultCoverAppStrategy,
        coverKeyboardStrategyByPackage = settings.coverKeyboardStrategyByPackage,
        onCoverAppKeyboardStrategyChanged = onCoverAppStrategyForPackage,
        onClearCoverAppKeyboardStrategies = onClearCoverAppStrategies,
        onOpenKeyboardPicker = onPicker,
        onOpenKeyboardSettings = onSettings,
        onResetSavedKeyboardModes = onResetModes,
        hapticStrength = settings.hapticStrength,
        onHapticStrengthSelected = onHapticStrength,
        keyPressSoundEnabled = settings.keyPressSoundEnabled,
        onKeyPressSoundEnabledChanged = onKeyPressSoundEnabledChanged,
        suggestionStripVisible = settings.suggestionStripVisible,
        onSuggestionStripVisibleChanged = onSuggestionStripVisibleChanged,
        autocorrectEnabled = settings.autocorrectEnabled,
        onAutocorrectEnabledChanged = onAutocorrectEnabledChanged,
        popupKeyPreviewEnabled = settings.popupKeyPreviewEnabled,
        onPopupKeyPreviewEnabledChanged = onPopupKeyPreviewEnabledChanged,
        keyboardExtraHeightDp = settings.keyboardExtraHeightDp,
        onKeyboardExtraHeightChanged = onKeyboardExtraHeightChanged,
        keyboardOneHandedEnabled = settings.keyboardOneHandedEnabled,
        onKeyboardOneHandedEnabledChanged = onKeyboardOneHandedEnabledChanged,
        keyboardOneHandedAnchor = settings.keyboardOneHandedAnchor,
        onKeyboardOneHandedAnchorChanged = onKeyboardOneHandedAnchorChanged,
        capabilityRefreshNonce = refreshNonce
    )
    // Debug-only interactive sandbox for testing keyboard primitives directly inside Customize.
    if (BuildConfig.DEBUG) {
        // Expands/collapses the QA preview strip below the input settings card.
        var showPreview by rememberSaveable { mutableStateOf(false) }
        // Scratch buffer collecting tapped keys and suggestion words inside the QA sandbox.
        var sampleText by rememberSaveable { mutableStateOf("") }
        OutlinedButton(onClick = { showPreview = !showPreview }) {
            Text(if (showPreview) "Hide QA keyboard preview" else "Show QA keyboard preview")
        }
        if (showPreview) {
            Column {
                Text("Keyboard smartbar preview (does not change your input method)")
                Text("Sample: $sampleText")
                FlorisSmartbar(
                    candidates = listOf("hello", "help", "held"),
                    onCandidateSelected = { _, word -> sampleText = word }
                )
            }
        }
    }
}
