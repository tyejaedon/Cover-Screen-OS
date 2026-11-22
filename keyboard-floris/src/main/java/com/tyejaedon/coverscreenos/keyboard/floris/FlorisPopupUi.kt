package com.tyejaedon.coverscreenos.keyboard.floris

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Gboard / One UI style key preview bubble displayed above a pressed character key.
 */
@Composable
fun FlorisKeyPreviewBubble(
    label: String,
    modifier: Modifier = Modifier,
    themePalette: FlorisThemePalette = FlorisThemePalette.AmoledCover
) {
    val density = LocalDensity.current
    // Vertical pixel offset (-48dp) floating the preview above the pressed key and the user's thumb.
    val yOffsetPx = with(density) { (-48).dp.roundToPx() }

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, yOffsetPx),
        properties = PopupProperties(
            // Keep non-focusable and non-dismissing so underlying key-press tracking continues cleanly.
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false
        )
    ) {
        Box(
            modifier = modifier
                .testTag("floris_key_preview_popup")
                .defaultMinSize(minWidth = 38.dp, minHeight = 44.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(themePalette.popupBubbleBackground)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                color = themePalette.popupBubbleForeground
            )
        }
    }
}

/**
 * Compact horizontal popup strip for selecting accented characters or secondary punctuation
 * on long-press (Gboard / One UI style).
 */
@Composable
fun FlorisExtendedPopupMenu(
    expanded: Boolean,
    options: List<String>,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit,
    themePalette: FlorisThemePalette = FlorisThemePalette.AmoledCover
) {
    if (!expanded || options.isEmpty()) return

    val density = LocalDensity.current
    // Vertical pixel offset (-46dp) placing the character chooser strip right above the long-pressed key.
    val yOffsetPx = with(density) { (-46).dp.roundToPx() }

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, yOffsetPx),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            // Focusable + outside-tap dismissible so the user can cancel by tapping anywhere else or Back.
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = true
        )
    ) {
        Row(
            modifier = Modifier
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(themePalette.popupBubbleBackground)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { option ->
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 32.dp, minHeight = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onDismiss()
                            onOptionSelected(option)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = themePalette.popupBubbleForeground
                    )
                }
            }
        }
    }
}
