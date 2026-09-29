package com.tyejaedon.coverscreenos.ui.keyboard

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.HapticTier
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.KeyboardKey
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.KeyboardKeyColors

@Composable
internal fun CoverT9PredictionToggle(
    isPredictive: Boolean,
    onModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    KeyboardKey(
        label = if (isPredictive) "T9" else "ABC",
        contentDescription = if (isPredictive) "Predictive T9 on" else "Predictive T9 off",
        onClick = { onModeChanged(!isPredictive) },
        hapticTier = HapticTier.Strong,
        colors = if (isPredictive) KeyboardKeyColors.accent() else KeyboardKeyColors.default(),
        modifier = modifier.height(44.dp).testTag("keyboard_t9_prediction_toggle")
    )
}
