package com.tyejaedon.coverscreenos.ui.customize.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.BuildConfig
import com.tyejaedon.coverscreenos.datastore.KeyboardStrategy
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.KeyboardKey
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.SuggestionStrip
import com.tyejaedon.coverscreenos.ui.settings.InputCustomizationCard

@Composable
internal fun InputScreen(
    settings: LauncherSettings,
    onStrategy: (KeyboardStrategy) -> Unit,
    onPicker: () -> Unit,
    onSettings: () -> Unit,
    onResetModes: () -> Unit,
    refreshNonce: Int
) {
    InputCustomizationCard(
        keyboardStrategy = settings.keyboardStrategy,
        onKeyboardStrategySelected = onStrategy,
        onOpenKeyboardPicker = onPicker,
        onOpenKeyboardSettings = onSettings,
        onResetSavedKeyboardModes = onResetModes,
        capabilityRefreshNonce = refreshNonce
    )
    if (BuildConfig.DEBUG) {
        var showPreview by rememberSaveable { mutableStateOf(false) }
        var sampleText by rememberSaveable { mutableStateOf("") }
        OutlinedButton(onClick = { showPreview = !showPreview }) {
            Text(if (showPreview) "Hide QA keyboard preview" else "Show QA keyboard preview")
        }
        if (showPreview) {
            Column {
                Text("Keyboard primitives preview (does not change your input method)")
                Text("Sample: $sampleText")
                SuggestionStrip(
                    suggestions = listOf("hello", "help", "held"),
                    onSelected = { _, word -> sampleText = word }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("2", "A", "B", "C").forEach { label ->
                        KeyboardKey(
                            label = label,
                            onClick = { sampleText += label },
                            showKeyPreview = true
                        )
                    }
                }
            }
        }
    }
}
