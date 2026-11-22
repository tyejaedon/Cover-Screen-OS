package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Maximum snippet characters shown per history chip before appending an ellipsis.
private const val HISTORY_CHIP_MAX_CHARS = 18

/**
 * Clean, borderless One UI / Gboard-style copy/cut/paste action icons for the keyboard top bar.
 *
 * Callers bridge [onCopy], [onCut], [onPaste], and [canPaste] to Android's system
 * `ClipboardManager` (or editor `InputConnection` / overlay selection state). Exposes stable
 * Compose `testTag`s (`"clipboard_copy"`, `"clipboard_cut"`, `"clipboard_paste"`) for UI tests.
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
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClipboardIconButton(Icons.Rounded.ContentCopy, "Copy", onCopy, tag = "clipboard_copy")
        ClipboardIconButton(Icons.Rounded.ContentCut, "Cut", onCut, tag = "clipboard_cut")
        ClipboardIconButton(
            Icons.Rounded.ContentPaste,
            "Paste",
            onPaste,
            enabled = canPaste,
            tag = "clipboard_paste"
        )
    }
}

/**
 * Horizontally-scrollable row of recent clipboard snippets for quick re-paste.
 *
 * Uses a [LazyRow] keyed by snippet string (deduplicated upstream by [ClipboardHistory]) with
 * test tag `"clipboard_history_row"`. Renders nothing when [history] is empty so it takes no
 * vertical space.
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag("clipboard_history_row")
    ) {
        // Snippets in ClipboardHistory are unique, making each string a stable LazyRow item key.
        items(items = history, key = { it }) { snippet ->
            ClipboardHistoryChip(
                snippet = snippet,
                onClick = { onHistorySnippetSelected(snippet) }
            )
        }
    }
}

/**
 * Circular icon action button used in [ClipboardActionIcons].
 *
 * Triggers [HapticTier.Light] on tap and omits the `clickable` modifier when [enabled] is
 * `false` so accessibility services do not announce an interactive button while paste is empty.
 */
@Composable
private fun ClipboardIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tag: String,
    enabled: Boolean = true
) {
    // Light tap feedback fired when Copy, Cut, or enabled Paste is tapped.
    val performHaptic = rememberHapticPerformer(HapticTier.Light)
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .then(
                // Attach clickable semantics only when enabled so disabled Paste is non-focusable.
                if (enabled) Modifier.clickable(role = Role.Button, onClickLabel = description) {
                    performHaptic()
                    onClick()
                } else Modifier
            )
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) Color(0xFFC8CDD6) else Color(0xFFC8CDD6).copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Pill chip displaying a single truncated snippet inside [ClipboardHistoryRow].
 */
@Composable
private fun ClipboardHistoryChip(snippet: String, onClick: () -> Unit) {
    // Display label capped at 18 chars + ellipsis so long clips never crowd the narrow cover strip.
    val truncated = if (snippet.length > HISTORY_CHIP_MAX_CHARS) {
        snippet.take(HISTORY_CHIP_MAX_CHARS) + "…"
    } else {
        snippet
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1E2026))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = truncated,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFE5E7EB),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
