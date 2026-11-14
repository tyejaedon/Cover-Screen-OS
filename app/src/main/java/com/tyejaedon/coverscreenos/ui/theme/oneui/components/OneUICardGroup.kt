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
    header: String? = null,
    shape: Shape = OneUITheme.shapes.card,
    containerColor: Color = OneUITheme.colors.cardSurface,
    contentColor: Color = OneUITheme.colors.textPrimary,
    border: BorderStroke? = null,
    internalPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        if (!header.isNullOrBlank()) {
            Text(
                text = header.uppercase(),
                style = OneUITheme.typography.labelMedium,
                color = OneUITheme.colors.textSecondary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }

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
    indent: Dp = OneUITheme.dimensions.internalDividerIndent,
    color: Color = OneUITheme.colors.hairlineBorder
) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = indent),
        thickness = 1.dp,
        color = color
    )
}

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
