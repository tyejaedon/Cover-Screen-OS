package com.tyejaedon.coverscreenos.ui.customize.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.BuildConfig
import com.tyejaedon.coverscreenos.datastore.KeyboardStrategy
import com.tyejaedon.coverscreenos.datastore.HapticStrength
import com.tyejaedon.coverscreenos.datastore.KeyboardAnchor
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.keyboard.floris.CoverEditorBridge
import com.tyejaedon.coverscreenos.keyboard.floris.CoverFlorisKeyboardSurface
import com.tyejaedon.coverscreenos.keyboard.floris.CoverLayoutMode
import com.tyejaedon.coverscreenos.keyboard.nlp.CoverKeyboardNlpManager
import com.tyejaedon.coverscreenos.ui.keyboard.CoverBufferOverlayCallbacks

@Composable
internal fun InputScreen(
    settings: LauncherSettings,
    onStrategy: (KeyboardStrategy) -> Unit,
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
    if (BuildConfig.DEBUG) {
        var showPreview by rememberSaveable { mutableStateOf(false) }
        var sampleText by rememberSaveable { mutableStateOf("") }
        OutlinedButton(onClick = { showPreview = !showPreview }) {
            Text(if (showPreview) "Hide QA keyboard preview" else "Show QA keyboard preview")
        }
        if (showPreview) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("FlorisBoard preview (does not change your input method)")
                Text("Sample: $sampleText")
                val callbacks = remember {
                    CoverBufferOverlayCallbacks(
                        getText = { sampleText },
                        setText = { sampleText = it },
                        onDoneAction = { showPreview = false }
                    )
                }
                val bridge = remember(callbacks) {
                    CoverEditorBridge(overlayCallbacks = callbacks)
                }
                var activeLayoutMode by remember { mutableStateOf(CoverLayoutMode.QWERTY) }
                var candidates by remember { mutableStateOf<List<String>>(emptyList()) }
                LaunchedEffect(sampleText, activeLayoutMode) {
                    val word = sampleText.takeLastWhile { it.isLetterOrDigit() }
                    candidates = if (word.isNotBlank()) {
                        CoverKeyboardNlpManager.getCandidates(activeLayoutMode, word)
                    } else {
                        emptyList()
                    }
                }
                CoverFlorisKeyboardSurface(
                    bridge = bridge,
                    initialMode = activeLayoutMode,
                    candidates = candidates,
                    onCandidateSelected = { candidate ->
                        callbacks.onCommitCandidate(candidate)
                    },
                    onDismissRequest = { showPreview = false },
                    onModeChanged = { activeLayoutMode = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
