package com.tyejaedon.coverscreenos.keyboard.floris

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Upstream FlorisBoard key preview bubble displayed above a pressed character key.
 */
@Composable
fun FlorisKeyPreviewBubble(
    label: String,
    modifier: Modifier = Modifier,
    themePalette: FlorisThemePalette = FlorisThemePalette.AmoledCover
) {
    val density = LocalDensity.current
    val yOffsetPx = with(density) { (-46).dp.roundToPx() }

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, yOffsetPx),
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false
        )
    ) {
        Box(
            modifier = modifier
                .testTag("floris_key_preview_popup")
                .defaultMinSize(minWidth = 36.dp, minHeight = 40.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(themePalette.popupBubbleBackground)
                .border(1.dp, themePalette.popupBubbleBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = themePalette.popupBubbleForeground
            )
        }
    }
}

/**
 * Upstream FlorisBoard extended `PopupSet` menu for selecting accented characters or
 * secondary punctuation options on long-press.
 */
@Composable
fun FlorisExtendedPopupMenu(
    expanded: Boolean,
    options: List<String>,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit
) {
    if (!expanded || options.isEmpty()) return

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option) },
                onClick = {
                    onDismiss()
                    onOptionSelected(option)
                }
            )
        }
    }
}
