package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import kotlin.math.min

/**
 * Collapsible, scrollable read-out for a live typed-text buffer (the cover overlay's
 * composing field, the launcher search preview, etc.).
 *
 * Collapsed (default), it shows up to [maxCollapsedLines] lines, scrolled to the *end*
 * of [text] — the caret normally sits at the end while actively typing, so the most
 * recently typed characters stay visible instead of being pushed off-screen. Tapping
 * expands it into a scrollable, up-to-[expandedMaxLines]-line view so the whole message
 * can be reviewed; tapping again collapses it back.
 *
 * Unlike a plain `Text(maxLines = 1, overflow = TextOverflow.Ellipsis)` — which truncates
 * the *end* of long content — this keeps the tail visible at every state, which is what a
 * composing-text preview needs.
 */
@Composable
fun ComposingTextPreview(
    text: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    maxCollapsedLines: Int = 2,
    expandedMaxLines: Int = 5,
    textStyle: TextStyle = LocalTextStyle.current,
    textColor: Color = LocalContentColor.current,
    placeholderColor: Color = textColor.copy(alpha = 0.5f),
    counterColor: Color = textColor.copy(alpha = 0.6f),
    showCharCount: Boolean = true
) {
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    var totalLineCount by remember { mutableIntStateOf(1) }
    // Seeded from the text style so the very first frame already renders close to the
    // correct clamped height; onTextLayout below refines it to the true measured value.
    var lineHeightPx by remember(textStyle) {
        mutableFloatStateOf(
            with(density) {
                if (textStyle.lineHeight.isSpecified) textStyle.lineHeight.toPx()
                else textStyle.fontSize.toPx() * 1.2f
            }
        )
    }

    val displayText = if (isPassword) "•".repeat(text.length) else text
    val isEmpty = displayText.isEmpty()
    val shown = if (isEmpty) placeholder else displayText
    val isTruncatable = totalLineCount > maxCollapsedLines
    val visibleLines = if (expanded) expandedMaxLines else maxCollapsedLines
    val scrollState = rememberScrollState()

    // Keep the viewport scrolled to the end (where the caret normally sits) whenever the
    // text changes, or the view expands/collapses to a new line budget.
    LaunchedEffect(text, visibleLines) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = with(density) { (lineHeightPx * visibleLines).toDp() })
                .verticalScroll(scrollState)
                .then(
                    if (isTruncatable) {
                        Modifier.clickable(role = Role.Button) { expanded = !expanded }
                    } else Modifier
                )
        ) {
            Text(
                text = shown,
                style = textStyle,
                color = if (isEmpty) placeholderColor else textColor,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth(),
                onTextLayout = { result: TextLayoutResult ->
                    totalLineCount = result.lineCount
                    if (result.lineCount > 0) {
                        lineHeightPx = result.size.height / result.lineCount.toFloat()
                    }
                }
            )
        }

        if (!isEmpty && (showCharCount || isTruncatable)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (showCharCount) {
                    Text(
                        text = "${text.length} chars",
                        color = counterColor,
                        fontSize = CounterFontSize
                    )
                }
                if (isTruncatable) {
                    Text(
                        text = "${min(visibleLines, totalLineCount)}/$totalLineCount lines" +
                            if (expanded) " · tap to collapse" else " · tap to expand",
                        color = counterColor,
                        fontSize = CounterFontSize
                    )
                }
            }
        }
    }
}

private val CounterFontSize = 10.sp

@Preview(showBackground = true, name = "ComposingTextPreview — short")
@Composable
private fun ComposingTextPreviewShortPreview() {
    CoverOSTheme {
        ComposingTextPreview(text = "hello world", modifier = Modifier.fillMaxWidth().padding(16.dp))
    }
}

@Preview(showBackground = true, name = "ComposingTextPreview — long, collapsed")
@Composable
private fun ComposingTextPreviewLongPreview() {
    CoverOSTheme {
        ComposingTextPreview(
            text = (1..10).joinToString(" ") { "line$it sample text wraps across the tile width" },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
    }
}
