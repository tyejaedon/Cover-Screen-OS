package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import kotlinx.coroutines.delay

/**
 * Floating key-preview label rendered inside a non-focusable Compose [Popup].
 *
 * Displays [label] at roughly 1.6× the pressed key's visual scale and offsets it ~40dp above the
 * anchor position — the ergonomic sweet spot on a cover-screen that keeps the fingertip clear of
 * the label. Auto-dismisses after [autoDismissMillis] via [onDismiss] (pass `0L` when the
 * caller manages visibility directly via pressed state, such as [KeyboardKey]).
 *
 * Positioning uses a custom [PopupPositionProvider] that centers the popup horizontally over the
 * anchor and offsets it upward by [verticalOffsetDp]. [PopupProperties] keeps `focusable = false`
 * so the popup never steals IME/editor focus or interrupts pointer tracking, and
 * `clippingEnabled = false` so the bubble can rise above the top edge of the keyboard container.
 */
@Composable
fun KeyPreview(
    label: String,
    modifier: Modifier = Modifier,
    verticalOffsetDp: Int = DefaultVerticalOffsetDp,
    autoDismissMillis: Long = DefaultAutoDismissMillis,
    onDismiss: () -> Unit = {}
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val verticalOffsetPx = with(density) { verticalOffsetDp.dp.roundToPx() }
    val positionProvider = remember(verticalOffsetPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: androidx.compose.ui.unit.IntRect,
                windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                popupContentSize: androidx.compose.ui.unit.IntSize
            ): IntOffset {
                // Center horizontally over the key and position completely above it by verticalOffsetPx.
                val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                val y = anchorBounds.top - popupContentSize.height - verticalOffsetPx
                return IntOffset(x, y)
            }
        }
    }

    // Optional timed dismissal; restarted if the previewed label changes while visible.
    if (autoDismissMillis > 0) {
        LaunchedEffect(label) {
            delay(autoDismissMillis)
            onDismiss()
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            // Non-focusable + non-dismissing so touch tracking on the underlying key is undisturbed.
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            // Allow the popup to overhang above the keyboard root bounds on compact cover screens.
            clippingEnabled = false
        )
    ) {
        Box(
            modifier = modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = LocalTextStyle.current.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

/**
 * Lightweight controller that remembers the currently-previewed label so
 * a caller can drive one shared [KeyPreview] from many keys.
 */
class KeyPreviewController internal constructor() {
    /** Currently active preview label, or `null` when hidden. */
    var label: String? by mutableStateOf(null)
        private set

    /** Shows [KeyPreview] for [label]. */
    fun show(label: String) { this.label = label }

    /** Hides any active [KeyPreview]. */
    fun dismiss() { this.label = null }
}

/** Creates and remembers a [KeyPreviewController] across recompositions. */
@Composable
fun rememberKeyPreviewController(): KeyPreviewController = remember { KeyPreviewController() }

/** Default vertical clearance (in dp) between the key top edge and the preview bubble bottom. */
internal const val DefaultVerticalOffsetDp: Int = 40

/** Default duration (in ms) before a standalone [KeyPreview] auto-dismisses. */
internal const val DefaultAutoDismissMillis: Long = 550L

/** Compose preview showing a static [KeyPreview] bubble. */
@Preview(showBackground = true)
@Composable
private fun KeyPreviewPreview() {
    CoverOSTheme {
        Box(modifier = Modifier.padding(48.dp)) {
            KeyPreview(label = "Q", autoDismissMillis = 0L)
        }
    }
}

