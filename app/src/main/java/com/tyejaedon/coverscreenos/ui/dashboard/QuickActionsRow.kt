package com.tyejaedon.coverscreenos.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUITheme
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUIPressable

@Composable
fun QuickActionsRow(
    running: Boolean,
    onToggleService: () -> Unit,
    onPermissions: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickAction(
            label = if (running) "Stop service" else "Start service",
            icon = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            onClick = onToggleService,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            label = "Grant permissions",
            icon = Icons.Filled.VerifiedUser,
            onClick = onPermissions,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            label = "Preview overlay",
            icon = Icons.Filled.Visibility,
            onClick = onPreview,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(OneUITheme.dimensions.encircledIconSize)
                .clip(CircleShape)
                .background(OneUITheme.colors.cardSurfaceVariant.copy(alpha = 0.7f))
                .oneUIPressable(interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = OneUITheme.colors.accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = OneUITheme.typography.bodyMedium,
            color = OneUITheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
    }
}
