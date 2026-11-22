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
 * Visual color tokens for a [KeyboardKey] across idle, pressed, and disabled states.
 *
 * @property idleBackground Resting keycap fill color.
 * @property pressedBackground Highlighted keycap fill while a finger is down.
 * @property idleContent Text/icon foreground color when enabled.
 * @property disabledBackground Muted fill color when the key is disabled.
 * @property disabledContent Dimmed text/icon color when the key is disabled.
 */
data class KeyboardKeyColors(
    val idleBackground: Color,
    val pressedBackground: Color,
    val idleContent: Color,
    val disabledBackground: Color,
    val disabledContent: Color
) {
    /** Factory helpers returning Material 3 theme-aligned [KeyboardKeyColors] presets. */
    companion object {
        /** Standard surface-variant key colors with subtle white press highlight. */
        @Composable
        fun default(): KeyboardKeyColors = KeyboardKeyColors(
            idleBackground = MaterialTheme.colorScheme.surfaceVariant,
            pressedBackground = lerp(MaterialTheme.colorScheme.surfaceVariant, Color.White, 0.08f),
            idleContent = MaterialTheme.colorScheme.onSurface,
            disabledBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            disabledContent = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )

        /** Primary-tinted accent colors used for action keys (Enter/Send, active toggles). */
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
 * Uses a custom `pointerInput` / `awaitEachGesture` loop instead of `detectTapGestures` so
 * feedback, hold-repeat, long-press, and horizontal drag scrubbing (e.g. spacebar cursor control)
 * all start immediately on pointer-down without waiting for tap confirmation.
 *
 * Features:
 *  - Text [label] or [ImageVector] [icon] (mutually exclusive; [label] wins if both are supplied).
 *  - Press-state scale-down + colour swap animation via [animateFloatAsState] / [animateColorAsState].
 *  - Optional [onLongPress].
 *  - Optional [repeatOnHold] that re-fires [onClick] on an interval while the finger stays down
 *    (backspace / arrow-key semantics).
 *  - Optional [onHorizontalDragStart] / [onHorizontalDrag] for trackpad-like drag gestures.
 *  - Tiered haptic feedback ([HapticTier]) and key-click sound ([rememberKeySoundPerformer]) fired
 *    on every discrete emission.
 *  - Optional built-in [KeyPreview] [androidx.compose.ui.window.Popup] anchored while pressed
 *    ([showKeyPreview]). Requires [label] to render — icons skip the popup.
 *
 * @param onClick invoked on tap release AND on each hold-repeat tick.
 * @param modifier layout and semantics modifier applied to the key container.
 * @param label text label; also used as the key-preview text.
 * @param icon vector icon; ignored if [label] is non-null.
 * @param contentDescription accessibility label exposed to TalkBack; defaults to [label].
 * @param enabled disables the key visually and drops all gestures when `false`.
 * @param onLongPress invoked once when [longPressThresholdMillis] expires; suppresses the release
 *   [onClick] if consumed.
 * @param onHorizontalDragStart invoked once when horizontal movement exceeds the 24px drag slop.
 * @param onHorizontalDrag invoked on each subsequent move event with the incremental X delta (px).
 * @param repeatOnHold when `true`, [onClick] fires repeatedly while pressed after
 *   [repeatInitialDelayMillis], every [repeatIntervalMillis].
 * @param repeatInitialDelayMillis delay before the first hold-repeat emission begins.
 * @param repeatIntervalMillis interval between repeated emissions while held.
 * @param longPressThresholdMillis duration required to trigger [onLongPress].
 * @param hapticTier tier for the tap / hold / long-press haptic events.
 * @param showKeyPreview when `true` and [label] is non-null, a [KeyPreview] popup is shown while
 *   the key is pressed.
 * @param shape background clipping shape for the key tile.
 * @param colors color palette across idle, pressed, and disabled states.
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

    // Live press state driving visual scale/color animation and the KeyPreview popup.
    var isPressed by remember { mutableStateOf(false) }
    // Separate haptic and sound performers combined into one keystroke feedback trigger.
    val performHaptic = rememberHapticPerformer(hapticTier)
    val playKeySound = rememberKeySoundPerformer()
    val performFeedback = remember(performHaptic, playKeySound) {
        {
            performHaptic()
            playKeySound()
        }
    }

    // Keep latest callbacks accessible inside pointerInput without restarting active gestures.
    val onClickState = rememberUpdatedState(onClick)
    val onLongPressState = rememberUpdatedState(onLongPress)
    val onDragStartState = rememberUpdatedState(onHorizontalDragStart)
    val onDragState = rememberUpdatedState(onHorizontalDrag)
    // Spoken accessibility label falling back to visible text when no override is supplied.
    val accessibleLabel = contentDescription ?: label.orEmpty()

    // Quick 60ms spring-like press shrink down to 92% scale for crisp tactile response.
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1f,
        animationSpec = tween(60),
        label = "KeyboardKeyScale"
    )

    // Resolved resting/pressed/disabled background color fed into the 60ms color transition.
    val targetBackground = when {
        !enabled -> colors.disabledBackground
        isPressed -> colors.pressedBackground
        else -> colors.idleBackground
    }
    val bg by animateColorAsState(targetBackground, animationSpec = tween(60), label = "KeyboardKeyColor")
    // Foreground tint for the key label or icon.
    val content = if (enabled) colors.idleContent else colors.disabledContent

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 44.dp)
            .scale(scale)
            .clip(shape)
            .background(bg)
            // Merge child text/icon into a single accessibility Button node for TalkBack & UI tests.
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.contentDescription = accessibleLabel
                if (enabled) onClick {
                    onClickState.value.invoke()
                    true
                }
            }
            // Re-key gesture detector only on structural behavior flags, not lambda identities.
            .pointerInput(enabled, repeatOnHold, onHorizontalDrag != null, onHorizontalDragStart != null,
                longPressThresholdMillis,
                repeatInitialDelayMillis, repeatIntervalMillis) {
                if (!enabled) return@pointerInput
                val scope = CoroutineScope(currentCoroutineContext())
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Trigger press state, haptics, and key sound immediately on touch-down.
                    isPressed = true
                    performFeedback()
                    var held = false
                    var dragged = false
                    var emitted = false
                    var released = false
                    var lastX = down.position.x
                    // Repeatable keys without drag (e.g. Backspace) emit once immediately on press.
                    if (repeatOnHold && onHorizontalDragStart == null) {
                        emitted = true
                        onClickState.value.invoke()
                    }
                    // Background timer driving either hold-to-repeat (e.g. key deletion) or long-press.
                    val holdJob: Job = scope.launch {
                        if (repeatOnHold) {
                            delay(repeatInitialDelayMillis)
                            while (isPressed && !dragged) {
                                emitted = true
                                performFeedback()
                                onClickState.value.invoke()
                                delay(repeatIntervalMillis)
                            }
                        } else if (onLongPressState.value != null) {
                            delay(longPressThresholdMillis)
                            if (isPressed && !dragged) {
                                held = true
                                performFeedback()
                                onLongPressState.value?.invoke()
                            }
                        }
                    }
                    // Track the active pointer until release or cancellation.
                    while (!released) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (pointer == null || !pointer.pressed) {
                            released = true
                            val inBoundsOnRelease = pointer != null &&
                                pointer.position.x in 0f..size.width.toFloat() &&
                                pointer.position.y in 0f..size.height.toFloat()
                            // Only treat release as a tap if the finger lifted inside key bounds unconsumed.
                            val wasTap = inBoundsOnRelease && pointer.previousPressed && !pointer.isConsumed
                            isPressed = false
                            holdJob.cancel()
                            if (wasTap && !held && !dragged && !repeatOnHold) {
                                onClickState.value.invoke()
                            } else if (wasTap && !dragged && repeatOnHold && !emitted) {
                                // Fires deferred tap for repeat keys that also support horizontal drag (e.g. Space).
                                onClickState.value.invoke()
                            }
                        } else {
                            val inBounds = pointer.position.x in 0f..size.width.toFloat() &&
                                pointer.position.y in 0f..size.height.toFloat()
                            if (onHorizontalDrag != null || onHorizontalDragStart != null) {
                                // Enter horizontal drag mode after 24px slop and cancel pending hold/repeat jobs.
                                if (kotlin.math.abs(pointer.position.x - down.position.x) >= 24f) {
                                    if (!dragged) onDragStartState.value?.invoke(
                                        pointer.position.x - down.position.x
                                    )
                                    dragged = true
                                    holdJob.cancel()
                                    onDragState.value?.invoke(pointer.position.x - lastX)
                                }
                                lastX = pointer.position.x
                                isPressed = inBounds || dragged
                            } else {
                                isPressed = inBounds
                            }
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

        // Anchor the floating KeyPreview Popup to this key's bounds while pressed; autoDismiss=0L
        // binds dismissal directly to isPressed rather than a timer.
        if (showKeyPreview && label != null && isPressed && enabled) {
            KeyPreview(label = label, autoDismissMillis = 0L)
        }
    }
}

/** Default hold duration (in ms) before [KeyboardKey] triggers `onLongPress`. */
internal const val DefaultLongPressThresholdMillis: Long = 400L

/** Default initial hold delay (in ms) before `repeatOnHold` begins repeating. */
internal const val DefaultRepeatInitialDelayMillis: Long = 450L

/** Default interval (in ms) between consecutive `repeatOnHold` emissions. */
internal const val DefaultRepeatIntervalMillis: Long = 55L

/** Compose preview of a standard text-labeled [KeyboardKey]. */
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

/** Compose preview of an accent-colored action [KeyboardKey]. */
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
