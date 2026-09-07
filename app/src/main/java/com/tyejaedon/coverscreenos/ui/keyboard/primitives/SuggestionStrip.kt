package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme

/**
 * Horizontally scrollable strip of candidate suggestions. Fixed ~32dp tall,
 * one chip per candidate. Highlighting the selected index gives predictive
 * input engines a way to render "hero" candidates without duplicating the
 * chip layout.
 *
 * The strip renders a stable container even when [suggestions] is empty so
 * that the surrounding keyboard layout does not reflow.
 */
@Composable
fun SuggestionStrip(
    suggestions: List<String>,
    onSelected: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
    selectedIndex: Int = -1,
    hapticTier: HapticTier = HapticTier.Light,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp),
    stripHeight: androidx.compose.ui.unit.Dp = DefaultStripHeightDp.dp
) {
    val listState = rememberLazyListState()
    val performHaptic = rememberHapticPerformer(hapticTier)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(stripHeight)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (suggestions.isEmpty()) return@Box

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items = suggestions) { candidate ->
                val index = suggestions.indexOf(candidate)
                SuggestionChip(
                    label = candidate,
                    selected = index == selectedIndex,
                    onClick = {
                        performHaptic()
                        onSelected(index, candidate)
                    }
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 32.dp, minHeight = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pointerInput(label) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var released = false
                    while (!released) {
                        val ev = awaitPointerEvent()
                        val stillDown = ev.changes.any { change -> change.pressed }
                        if (!stillDown) {
                            released = true
                            val tap = ev.changes.firstOrNull { change -> !change.pressed }
                            if (tap?.previousPressed == true && !tap.isConsumed) {
                                onClick()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = LocalTextStyle.current.copy(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = fg
            )
        )
    }
}

internal const val DefaultStripHeightDp: Int = 32

@Preview(showBackground = true, name = "SuggestionStrip — populated")
@Composable
private fun SuggestionStripPreview() {
    CoverOSTheme {
        SuggestionStrip(
            suggestions = listOf("the", "then", "them", "there", "these"),
            selectedIndex = 1,
            onSelected = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "SuggestionStrip — empty")
@Composable
private fun SuggestionStripEmptyPreview() {
    CoverOSTheme {
        SuggestionStrip(
            suggestions = emptyList(),
            onSelected = { _, _ -> }
        )
    }
}

