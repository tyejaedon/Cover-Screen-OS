package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

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
            pressedBackground = lerp(MaterialTheme.colorScheme.surfaceVariant, Color.White, 0.08f),
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
    onHorizontalDragStart: ((Float) -> Unit)? = null,
    onHorizontalDrag: ((Float) -> Unit)? = null,
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
    val onDragStartState = rememberUpdatedState(onHorizontalDragStart)
    val onDragState = rememberUpdatedState(onHorizontalDrag)
    val accessibleLabel = contentDescription ?: label.orEmpty()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1f,
        animationSpec = tween(60),
        label = "KeyboardKeyScale"
    )

    val targetBackground = when {
        !enabled -> colors.disabledBackground
        isPressed -> colors.pressedBackground
        else -> colors.idleBackground
    }
    val bg by animateColorAsState(targetBackground, animationSpec = tween(60), label = "KeyboardKeyColor")
    val content = if (enabled) colors.idleContent else colors.disabledContent

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 44.dp)
            .scale(scale)
            .clip(shape)
            .background(bg)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.contentDescription = accessibleLabel
                if (enabled) onClick {
                    onClickState.value.invoke()
                    true
                }
            }
            .pointerInput(enabled, repeatOnHold, onHorizontalDrag != null, onHorizontalDragStart != null,
                longPressThresholdMillis,
                repeatInitialDelayMillis, repeatIntervalMillis) {
                if (!enabled) return@pointerInput
                val scope = CoroutineScope(currentCoroutineContext())
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    performHaptic()
                    var held = false
                    var dragged = false
                    var emitted = false
                    var released = false
                    var lastX = down.position.x
                    if (repeatOnHold && onHorizontalDragStart == null) {
                        emitted = true
                        onClickState.value.invoke()
                    }
                    val holdJob: Job = scope.launch {
                        if (repeatOnHold) {
                            delay(repeatInitialDelayMillis)
                            while (isPressed && !dragged) {
                                emitted = true
                                performHaptic()
                                onClickState.value.invoke()
                                delay(repeatIntervalMillis)
                            }
                        } else if (onLongPressState.value != null) {
                            delay(longPressThresholdMillis)
                            if (isPressed && !dragged) {
                                held = true
                                performHaptic()
                                onLongPressState.value?.invoke()
                            }
                        }
                    }
                    while (!released) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (pointer == null || !pointer.pressed) {
                            released = true
                            val wasTap = pointer?.previousPressed == true && !pointer.isConsumed
                            isPressed = false
                            holdJob.cancel()
                            if (wasTap && !held && !dragged && !repeatOnHold) {
                                onClickState.value.invoke()
                            } else if (wasTap && !dragged && repeatOnHold && !emitted) {
                                onClickState.value.invoke()
                            }
                        } else if (onHorizontalDrag != null || onHorizontalDragStart != null) {
                            if (kotlin.math.abs(pointer.position.x - down.position.x) >= 24f) {
                                if (!dragged) onDragStartState.value?.invoke(
                                    pointer.position.x - down.position.x
                                )
                                dragged = true
                                holdJob.cancel()
                                onDragState.value?.invoke(pointer.position.x - lastX)
                            }
                            lastX = pointer.position.x
                        }
                    }
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
                contentDescription = null,
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
