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
 * Floating key-preview label. Renders [label] at roughly 1.6× the pressed
 * key's visual scale and offsets it ~40dp above the anchor position — the
 * ergonomic sweet spot on a cover-screen that keeps the fingertip clear of
 * the label. Auto-dismisses after [autoDismissMillis] via [onDismiss].
 *
 * Positioning uses a custom [PopupPositionProvider] that centers the popup
 * horizontally over the anchor and offsets it upward by
 * [verticalOffsetDp]. The caller is expected to anchor the [Popup] to the
 * pressed key by wrapping this composable inside the key's layout.
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
                val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                val y = anchorBounds.top - popupContentSize.height - verticalOffsetPx
                return IntOffset(x, y)
            }
        }
    }

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
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
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
    var label: String? by mutableStateOf(null)
        private set

    fun show(label: String) { this.label = label }
    fun dismiss() { this.label = null }
}

@Composable
fun rememberKeyPreviewController(): KeyPreviewController = remember { KeyPreviewController() }

internal const val DefaultVerticalOffsetDp: Int = 40
internal const val DefaultAutoDismissMillis: Long = 550L

@Preview(showBackground = true)
@Composable
private fun KeyPreviewPreview() {
    CoverOSTheme {
        Box(modifier = Modifier.padding(48.dp)) {
            KeyPreview(label = "Q", autoDismissMillis = 0L)
        }
    }
}

