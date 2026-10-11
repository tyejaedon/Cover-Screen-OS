package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.datastore.COMPACT_EXTRA_KEY_HEIGHT_DP
import com.tyejaedon.coverscreenos.datastore.KeyboardAnchor
import com.tyejaedon.coverscreenos.datastore.STANDARD_EXTRA_KEY_HEIGHT_DP
import com.tyejaedon.coverscreenos.datastore.TALL_EXTRA_KEY_HEIGHT_DP
import com.tyejaedon.coverscreenos.datastore.normalizeExtraKeyHeightDp
import kotlin.math.abs

// Used only for resize haptic ticks. The preset buttons live in Customize > Input.
private val HEIGHT_PRESETS = listOf(
    COMPACT_EXTRA_KEY_HEIGHT_DP,
    STANDARD_EXTRA_KEY_HEIGHT_DP,
    TALL_EXTRA_KEY_HEIGHT_DP
)
private const val ONE_HANDED_WIDTH_FRACTION = 0.8f

/**
 * Phase D: the slim control row rendered above a keyboard's key rows. It holds the resize
 * grip, the one-handed toggle and an optional [trailingContent] slot (used for the small
 * copy/cut/paste icons). Dragging the grip vertically free-resizes [extraHeightDp] within
 * `[MIN_EXTRA_KEY_HEIGHT_DP, MAX_EXTRA_KEY_HEIGHT_DP]`, firing [onHaptic] whenever the drag
 * crosses a preset boundary (mirroring Samsung Keyboard's resize-tick feedback).
 */
@Composable
fun ResizableKeyboardSurfaceControls(
    extraHeightDp: Float,
    onExtraHeightChanged: (Float) -> Unit,
    oneHandedEnabled: Boolean,
    onOneHandedToggle: () -> Unit,
    oneHandedAnchor: KeyboardAnchor,
    onAnchorToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onHaptic: () -> Unit = {},
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val density = LocalDensity.current
    val onExtraHeightChangedState = rememberUpdatedState(onExtraHeightChanged)
    val onHapticState = rememberUpdatedState(onHaptic)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Grip handle: a short horizontal bar; vertical drag resizes the keyboard.
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(16.dp)
                .testTag("keyboard_resize_grip")
                .pointerInput(Unit) {
                    var lastSnappedPreset = extraHeightDp
                    detectVerticalDragGestures(
                        onDragStart = { lastSnappedPreset = roundToNearestPreset(extraHeightDp) },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            // Dragging the grip *up* should grow the keyboard (standard
                            // bottom-sheet drag-handle convention), hence the negation.
                            val deltaDp = with(density) { -dragAmount.toDp().value }
                            val next = normalizeExtraKeyHeightDp(extraHeightDp + deltaDp)
                            onExtraHeightChangedState.value(next)
                            val nearestPreset = roundToNearestPreset(next)
                            if (abs(nearestPreset - lastSnappedPreset) >= STANDARD_EXTRA_KEY_HEIGHT_DP / 2) {
                                lastSnappedPreset = nearestPreset
                                onHapticState.value()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LocalContentColor.current.copy(alpha = 0.4f))
            )
        }

        ControlChip(
            label = if (oneHandedEnabled) "1-hand: on" else "1-hand",
            selected = oneHandedEnabled,
            onClick = {
                onOneHandedToggle()
                onHaptic()
            },
            modifier = Modifier.testTag("keyboard_one_handed_toggle")
        )

        if (oneHandedEnabled) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = "Swap one-handed side (currently ${oneHandedAnchor.name})",
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable {
                        onAnchorToggle()
                        onHaptic()
                    }
                    .testTag("keyboard_one_handed_anchor_swap")
            )
        }

        Box(modifier = Modifier.weight(1f))

        trailingContent?.invoke(this)
    }
}

@Composable
private fun ControlChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Wraps [content] (the actual key rows) so that when [oneHandedEnabled] is true it shrinks to
 * ~80% width and docks to [anchor]; otherwise fills the available width unchanged.
 */
@Composable
fun OneHandedKeyboardBox(
    oneHandedEnabled: Boolean,
    anchor: KeyboardAnchor,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (!oneHandedEnabled) {
            Alignment.Center
        } else if (anchor == KeyboardAnchor.Left) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        }
    ) {
        Box(
            modifier = if (oneHandedEnabled) {
                Modifier.fillMaxWidth(ONE_HANDED_WIDTH_FRACTION)
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            content()
        }
    }
}

private fun roundToNearestPreset(extraDp: Float): Float =
    HEIGHT_PRESETS.minByOrNull { abs(it - extraDp) } ?: COMPACT_EXTRA_KEY_HEIGHT_DP
