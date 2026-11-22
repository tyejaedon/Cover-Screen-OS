package com.tyejaedon.coverscreenos.ui.theme.oneui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUITheme

/**
 * Samsung One UI 8 Card Group Container.
 *
 * Implements strict Card Containment:
 * - Never places raw items on the root canvas background.
 * - Renders an optional external section header 8dp above and outside the card start edge.
 * - Wraps content in an elevated rounded card (default 24dp radius) with 16dp internal padding.
 */
@Composable
fun OneUICardGroup(
    modifier: Modifier = Modifier,
    // Optional group title placed above the card rather than inside it.
    header: String? = null,
    // Rounded card outline shape; defaults to One UI's 24dp squircle-style curve.
    shape: Shape = OneUITheme.shapes.card,
    // Card background fill lifted above the outer screen canvas.
    containerColor: Color = OneUITheme.colors.cardSurface,
    // Default text/icon tint passed down to child elements inside the card.
    contentColor: Color = OneUITheme.colors.textPrimary,
    // Optional custom border override; null falls back to a crisp 1dp hairline stroke.
    border: BorderStroke? = null,
    // Inner breathing room between the card border and its child rows.
    internalPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        // Section header sits outside the rounded card container, aligned with the card's internal text column.
        if (!header.isNullOrBlank()) {
            Text(
                text = header.uppercase(),
                style = OneUITheme.typography.labelMedium,
                color = OneUITheme.colors.textSecondary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }

        // Material 3 Surface handles shape clipping, background fill, hairline border, and LocalContentColor propagation.
        Surface(
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            border = border ?: BorderStroke(1.dp, OneUITheme.colors.hairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(internalPadding),
                content = content
            )
        }
    }
}

/**
 * Standard One UI indented divider for separating items within a card.
 * Never full-width; indents 16dp from start and end edges.
 */
@Composable
fun OneUIInternalDivider(
    modifier: Modifier = Modifier,
    // Left/right inset matching card padding so the rule lines up neatly with row text.
    indent: Dp = OneUITheme.dimensions.internalDividerIndent,
    // Subtle translucent line color matching the card's outer hairline stroke.
    color: Color = OneUITheme.colors.hairlineBorder
) {
    // Inset horizontally so dividers align with row content instead of touching the card's rounded border.
    HorizontalDivider(
        modifier = modifier.padding(horizontal = indent),
        thickness = 1.dp,
        color = color
    )
}

/**
 * Dual-mode Android Studio `@Preview` comparing [OneUICardGroup] and [OneUIInternalDivider]
 * across Light Mode (`UI_MODE_NIGHT_NO`) and OLED Dark Mode (`UI_MODE_NIGHT_YES`).
 */
@Preview(name = "Light Mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "OLED Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun OneUICardGroupPreview() {
    OneUITheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OneUICardGroup(header = "Launcher Settings") {
                Text("Item One", style = OneUITheme.typography.bodyLarge)
                OneUIInternalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text("Item Two", style = OneUITheme.typography.bodyLarge)
            }
        }
    }
}