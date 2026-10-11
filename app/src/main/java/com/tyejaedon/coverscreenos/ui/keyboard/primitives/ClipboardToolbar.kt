package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val HISTORY_CHIP_MAX_CHARS = 18
private val CLIPBOARD_ICON_BUTTON_WIDTH = 32.dp
private val CLIPBOARD_ICON_BUTTON_HEIGHT = 26.dp

/**
 * Phase H: small copy/cut/paste icons. They sit at the end of the keyboard's top control
 * row (next to the one-handed toggle) so they don't take a full row of key-sized buttons.
 * Copy/cut are always tappable (a no-op when there's no active selection); paste is
 * disabled when [canPaste] is false (nothing on the system clipboard).
 */
@Composable
fun ClipboardActionIcons(
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    canPaste: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClipboardIconButton(Icons.Filled.ContentCopy, "Copy", onCopy, tag = "clipboard_copy")
        ClipboardIconButton(Icons.Filled.ContentCut, "Cut", onCut, tag = "clipboard_cut")
        ClipboardIconButton(
            Icons.Filled.ContentPaste,
            "Paste",
            onPaste,
            enabled = canPaste,
            tag = "clipboard_paste"
        )
    }
}

/**
 * Horizontally-scrollable row of recent clipboard snippets for quick re-paste.
 * Renders nothing when [history] is empty so it takes no space.
 */
@Composable
fun ClipboardHistoryRow(
    history: List<String>,
    onHistorySnippetSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth().testTag("clipboard_history_row")
    ) {
        items(items = history, key = { it }) { snippet ->
            ClipboardHistoryChip(
                snippet = snippet,
                onClick = { onHistorySnippetSelected(snippet) }
            )
        }
    }
}

@Composable
private fun ClipboardIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tag: String,
    enabled: Boolean = true
) {
    KeyboardKey(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        enabled = enabled,
        hapticTier = HapticTier.Light,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .size(width = CLIPBOARD_ICON_BUTTON_WIDTH, height = CLIPBOARD_ICON_BUTTON_HEIGHT)
            .testTag(tag)
    )
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
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = truncated,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
