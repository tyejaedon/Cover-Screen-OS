package com.tyejaedon.coverscreenos.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.datastore.KeyboardStrategy
import com.tyejaedon.coverscreenos.datastore.InputTelemetryCounters
import com.tyejaedon.coverscreenos.datastore.InputTelemetryStore
import com.tyejaedon.coverscreenos.helpers.InputAvailabilityTier
import com.tyejaedon.coverscreenos.helpers.InputCapabilityDetector
import com.tyejaedon.coverscreenos.helpers.InputCapabilitySnapshot
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusMedium
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding

private data class KeyboardStrategyUiOption(
    val mode: KeyboardStrategy,
    val label: String,
    val summary: String,
    val icon: ImageVector
)

@Composable
internal fun InputCustomizationCard(
    keyboardStrategy: KeyboardStrategy,
    onKeyboardStrategySelected: (KeyboardStrategy) -> Unit,
    onOpenKeyboardPicker: () -> Unit,
    onOpenKeyboardSettings: () -> Unit,
    capabilityRefreshNonce: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val inputTelemetryStore = remember(appContext) { InputTelemetryStore(appContext) }
    val telemetryCounters by inputTelemetryStore.counters.collectAsState(initial = InputTelemetryCounters())
    var localRefreshNonce by remember { mutableIntStateOf(0) }
    val capabilitySnapshot by produceState<InputCapabilitySnapshot?>(
        initialValue = null,
        key1 = context,
        key2 = capabilityRefreshNonce,
        key3 = localRefreshNonce
    ) {
        value = InputCapabilityDetector(context).detect()
    }
    val oemRestrictionHint = capabilitySnapshot?.oemRestrictionHint

    LaunchedEffect(oemRestrictionHint) {
        if (!oemRestrictionHint.isNullOrBlank()) {
            inputTelemetryStore.recordOemBlockedHintShown()
        }
    }

    val options = listOf(
        KeyboardStrategyUiOption(
            mode = KeyboardStrategy.T9,
            label = "T9 keypad",
            summary = "Best for narrow cover screens with large tap targets.",
            icon = Icons.Filled.Dialpad
        ),
        KeyboardStrategyUiOption(
            mode = KeyboardStrategy.SYSTEM_IME,
            label = "System keyboard",
            summary = "Use Gboard/Samsung Keyboard with IME resize handling.",
            icon = Icons.Filled.Keyboard
        )
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(CoverOSCornerRadiusMedium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .coverScreenPadding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Input", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose how app drawer search accepts text on the cover display.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            InputCapabilityStatusCard(
                capabilitySnapshot = capabilitySnapshot,
                telemetryCounters = telemetryCounters,
                onRefreshRequested = {
                    localRefreshNonce += 1
                }
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    val selected = keyboardStrategy == option.mode
                    val buttonBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    if (selected) {
                        Button(
                            onClick = { onKeyboardStrategySelected(option.mode) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .coverMinimumTouchTarget(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = option.icon, contentDescription = null)
                                Column {
                                    Text(option.label)
                                    Text(option.summary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onKeyboardStrategySelected(option.mode) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .coverMinimumTouchTarget(),
                            shape = RoundedCornerShape(12.dp),
                            border = buttonBorder
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = option.icon, contentDescription = null)
                                Column {
                                    Text(option.label)
                                    Text(option.summary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenKeyboardPicker,
                    modifier = Modifier
                        .weight(1f)
                        .coverMinimumTouchTarget(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Keyboard,
                        contentDescription = null
                    )
                    Text(
                        text = "Keyboard picker",
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                OutlinedButton(
                    onClick = onOpenKeyboardSettings,
                    modifier = Modifier
                        .weight(1f)
                        .coverMinimumTouchTarget(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null
                    )
                    Text(
                        text = "Keyboard settings",
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            Text(
                "Tip: voice search requires microphone permission and works with either default strategy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InputCapabilityStatusCard(
    capabilitySnapshot: InputCapabilitySnapshot?,
    telemetryCounters: InputTelemetryCounters,
    onRefreshRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .coverScreenPadding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Keyboard capability status",
                style = MaterialTheme.typography.labelLarge
            )

            if (capabilitySnapshot == null) {
                Text(
                    text = "Loading input-method status...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val defaultImeLabel = capabilitySnapshot.defaultInputMethod?.label
                    ?: "No default IME detected"
                val enabledImeLabels = if (capabilitySnapshot.enabledInputMethods.isEmpty()) {
                    "None detected"
                } else {
                    capabilitySnapshot.enabledInputMethods.joinToString { it.label }
                }

                Text(
                    text = "Default IME: $defaultImeLabel",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Enabled IMEs: $enabledImeLabels",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "IME picker: ${if (capabilitySnapshot.isImePickerLikelyAvailable) "available" else "unavailable"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "CoverScreenOS T9: ${if (capabilitySnapshot.isCoverT9Enabled) "enabled" else "disabled"}" +
                        " | default=${if (capabilitySnapshot.isCoverT9Default) "yes" else "no"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Gboard: ${if (capabilitySnapshot.isGboardEnabled) "enabled" else "disabled"}" +
                        " | default=${if (capabilitySnapshot.isGboardDefault) "yes" else "no"}",
                    style = MaterialTheme.typography.bodySmall
                )

                val availabilityTierLabel = when (capabilitySnapshot.availabilityEstimateTier) {
                    InputAvailabilityTier.HIGH -> "high"
                    InputAvailabilityTier.MODERATE -> "moderate"
                    InputAvailabilityTier.LOW -> "low"
                }
                val observedSuccessRate = telemetryCounters.imeShowSuccessRatePercent
                val hasObservedRuntimeSamples = telemetryCounters.imeShowRequestCount > 0L
                val calibratedAvailabilityPercent = if (hasObservedRuntimeSamples) {
                    ((capabilitySnapshot.availabilityEstimatePercent + observedSuccessRate) / 2)
                } else {
                    capabilitySnapshot.availabilityEstimatePercent
                }
                Text(
                    text = "Estimated cross-app availability: ${capabilitySnapshot.availabilityEstimatePercent}% ($availabilityTierLabel)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Calibrated with runtime picker success: $calibratedAvailabilityPercent%",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Estimate is heuristic from IME settings + OEM risk and is not a runtime guarantee.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Telemetry counters: requests=${telemetryCounters.imeShowRequestCount}, " +
                        "success=${telemetryCounters.imeShowSuccessCount}, failure=${telemetryCounters.imeShowFailureCount}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Observed picker success rate: ${telemetryCounters.imeShowSuccessRatePercent}% | " +
                        "OEM-blocked hints shown=${telemetryCounters.oemBlockedHintShownCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                capabilitySnapshot.oemRestrictionHint?.let { hint ->
                    Text(
                        text = "OEM hint: $hint",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = onRefreshRequested,
                modifier = Modifier
                    .fillMaxWidth()
                    .coverMinimumTouchTarget(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Refresh status")
            }
        }
    }
}

