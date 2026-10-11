package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val HISTORY_CHIP_MAX_CHARS = 18

/**
 * Phase H: persistent copy/cut/paste toolbar rendered above the suggestion strip, plus a
 * horizontally-scrollable clipboard-history row for quick re-paste of recent snippets.
 * Copy/cut are always tappable (a no-op when there's no active selection); paste is disabled
 * when [canPaste] is false (nothing on the system clipboard).
 */
@Composable
fun ClipboardToolbar(
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    canPaste: Boolean,
    history: List<String>,
    onHistorySnippetSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyboardKey(
                icon = Icons.Filled.ContentCopy,
                contentDescription = "Copy",
                onClick = onCopy,
                hapticTier = HapticTier.Light,
                modifier = Modifier.weight(1f).testTag("clipboard_copy")
            )
            KeyboardKey(
                icon = Icons.Filled.ContentCut,
                contentDescription = "Cut",
                onClick = onCut,
                hapticTier = HapticTier.Light,
                modifier = Modifier.weight(1f).testTag("clipboard_cut")
            )
            KeyboardKey(
                icon = Icons.Filled.ContentPaste,
                contentDescription = "Paste",
                onClick = onPaste,
                enabled = canPaste,
                hapticTier = HapticTier.Light,
                modifier = Modifier.weight(1f).testTag("clipboard_paste")
            )
        }

        if (history.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag("clipboard_history_row")
            ) {
                items(items = history, key = { it }) { snippet ->
                    ClipboardHistoryChip(
                        snippet = snippet,
                        onClick = { onHistorySnippetSelected(snippet) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipboardHistoryChip(snippet: String, onClick: () -> Unit) {
    val truncated = if (snippet.length > HISTORY_CHIP_MAX_CHARS) {
        snippet.take(HISTORY_CHIP_MAX_CHARS) + "…"
    } else {
        snippet
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, LocalContentColor.current.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = truncated,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
