package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Visual + haptic + gesture colours for a [KeyboardKey].
 */
data class KeyboardKeyColors(
    val idleBackground: Color,
    val pressedBackground: Color,
    val idleContent: Color,
    val disabledBackground: Color,
    val disabledContent: Color
) {
    companion object {
        @Composable
        fun default(): KeyboardKeyColors = KeyboardKeyColors(
            idleBackground = MaterialTheme.colorScheme.surfaceVariant,
            pressedBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            idleContent = MaterialTheme.colorScheme.onSurface,
            disabledBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            disabledContent = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )

        @Composable
        fun accent(): KeyboardKeyColors = default().copy(
            idleBackground = MaterialTheme.colorScheme.primary,
            idleContent = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/**
 * Shared keyboard-key primitive used by every cover-screen keypad (QWERTY,
 * numeric PIN, T9, IME service, launcher search).
 *
 * Features:
 *  - Text label or [ImageVector] icon (mutually exclusive; label wins if both
 *    are supplied).
 *  - Press-state scale-down + colour swap animation via
 *    [animateFloatAsState].
 *  - Optional [onLongPress].
 *  - Optional [repeatOnHold] that re-fires [onClick] on an interval while the
 *    finger stays down (backspace / arrow-key semantics).
 *  - Tiered haptic feedback ([HapticTier]) fired on every discrete emission.
 *  - Optional built-in [KeyPreview] popup shown while pressed
 *    ([showKeyPreview]). Requires [label] to render — icons skip the popup.
 *
 * @param label text label; also used as the key-preview text.
 * @param icon icon; ignored if [label] is non-null.
 * @param onClick invoked on tap release AND on each hold-repeat tick.
 * @param onLongPress invoked once when the long-press threshold expires;
 *   suppresses the release [onClick] if consumed.
 * @param repeatOnHold when true, [onClick] fires repeatedly while pressed
 *   after [repeatInitialDelayMillis], every [repeatIntervalMillis].
 * @param hapticTier tier for the tap / hold / long-press haptic events.
 * @param showKeyPreview when true and [label] is non-null, a [KeyPreview]
 *   popup is shown while the key is pressed.
 * @param enabled disables the key visually and drops all gestures when false.
 */
@Composable
fun KeyboardKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    contentDescription: String? = label,
    enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    repeatOnHold: Boolean = false,
    repeatInitialDelayMillis: Long = DefaultRepeatInitialDelayMillis,
    repeatIntervalMillis: Long = DefaultRepeatIntervalMillis,
    longPressThresholdMillis: Long = DefaultLongPressThresholdMillis,
    hapticTier: HapticTier = HapticTier.Standard,
    showKeyPreview: Boolean = false,
    shape: Shape = RoundedCornerShape(8.dp),
    colors: KeyboardKeyColors = KeyboardKeyColors.default()
) {
    require(label != null || icon != null) {
        "KeyboardKey requires either a label or an icon."
    }

    var isPressed by remember { mutableStateOf(false) }
    val performHaptic = rememberHapticPerformer(hapticTier)

    val onClickState = rememberUpdatedState(onClick)
    val onLongPressState = rememberUpdatedState(onLongPress)

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1f,
        animationSpec = spring(),
        label = "KeyboardKeyScale"
    )

    val bg = when {
        !enabled -> colors.disabledBackground
        isPressed -> colors.pressedBackground
        else -> colors.idleBackground
    }
    val content = if (enabled) colors.idleContent else colors.disabledContent

    // Repeat + long-press coroutine, driven by press state.
    LaunchedEffect(enabled, repeatOnHold, onLongPress != null) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow { isPressed }.collectLatest { pressed ->
            if (!pressed) return@collectLatest
            var longPressFired = false
            if (onLongPressState.value != null) {
                delay(longPressThresholdMillis)
                if (!isPressed) return@collectLatest
                longPressFired = true
                performHaptic()
                onLongPressState.value?.invoke()
            }
            if (repeatOnHold && !longPressFired) {
                delay(repeatInitialDelayMillis - longPressThresholdMillis.coerceAtMost(repeatInitialDelayMillis))
                while (isPressed) {
                    performHaptic()
                    onClickState.value.invoke()
                    delay(repeatIntervalMillis)
                }
            } else if (repeatOnHold && longPressFired) {
                // Long-press consumed the initial fire; continue repeating.
                delay(repeatIntervalMillis)
                while (isPressed) {
                    performHaptic()
                    onClickState.value.invoke()
                    delay(repeatIntervalMillis)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .scale(scale)
            .clip(shape)
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .pointerInput(enabled, repeatOnHold) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    performHaptic()

                    // Track finger up / cancel.
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val stillDown = event.changes.any { change -> change.pressed }
                        if (!stillDown) {
                            released = true
                            val pointer = event.changes.firstOrNull { change -> !change.pressed }
                            val wasTap = pointer?.previousPressed == true &&
                                !pointer.isConsumed
                            isPressed = false
                            if (wasTap && !repeatOnHold) {
                                // Only fire click on release when repeatOnHold
                                // is off — repeat mode already fires clicks
                                // during hold.
                                onClickState.value.invoke()
                            }
                        }
                    }
                    down.consume()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (label != null) {
            Text(
                text = label,
                style = LocalTextStyle.current.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = content
                )
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = content
            )
        }

        if (showKeyPreview && label != null && isPressed && enabled) {
            KeyPreview(label = label, autoDismissMillis = 0L)
        }
    }
}

internal const val DefaultLongPressThresholdMillis: Long = 400L
internal const val DefaultRepeatInitialDelayMillis: Long = 450L
internal const val DefaultRepeatIntervalMillis: Long = 55L

@Preview(showBackground = true, name = "Key — text label")
@Composable
private fun KeyboardKeyLabelPreview() {
    CoverOSTheme {
        Box(modifier = Modifier.padding(32.dp)) {
            KeyboardKey(
                label = "Q",
                onClick = {},
                showKeyPreview = false
            )
        }
    }
}

@Preview(showBackground = true, name = "Key — accent")
@Composable
private fun KeyboardKeyAccentPreview() {
    CoverOSTheme {
        Box(modifier = Modifier.padding(32.dp)) {
            KeyboardKey(
                label = "Send",
                onClick = {},
                colors = KeyboardKeyColors.accent()
            )
        }
    }
}

