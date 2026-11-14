package com.tyejaedon.coverscreenos.ui.theme.oneui.components

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUITheme
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUIPressable

/**
 * Signature Samsung One UI 8 Now Pill.
 *
 * Compact 44dp pill badge (CircleShape) anchored near the bottom interaction zone
 * with a subtle animated gradient border for live ongoing status, background tasks,
 * or fold posture state.
 */
@Composable
fun OneUINowPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Filled.FiberManualRecord,
    iconTint: Color = OneUITheme.colors.accent,
    onClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nowPillBorderTransition")
    val borderOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientBorderOffset"
    )

    val animatedBrush = Brush.linearGradient(
        colors = listOf(
            OneUITheme.colors.accent.copy(alpha = 0.8f),
            OneUITheme.colors.accent.copy(alpha = 0.2f),
            OneUITheme.colors.accent.copy(alpha = 0.9f)
        )
    )

    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        shape = CircleShape,
        color = OneUITheme.colors.cardSurface.copy(alpha = 0.92f),
        shadowElevation = 0.dp,
        modifier = modifier
            .height(OneUITheme.dimensions.nowPillHeight)
            .clip(CircleShape)
            .border(1.5.dp, animatedBrush, CircleShape)
            .then(
                if (onClick != null) {
                    Modifier
                        .oneUIPressable(interactionSource = interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = OneUITheme.typography.bodyMedium,
                color = OneUITheme.colors.textPrimary,
                maxLines = 1
            )
        }
    }
}

/**
 * Encircled Action Icon: Standalone 40dp CircleShape container with subtle tinted fill.
 */
@Composable
fun OneUIEncircledActionIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = OneUITheme.colors.textPrimary,
    containerColor: Color = OneUITheme.colors.cardSurfaceVariant.copy(alpha = 0.7f),
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(OneUITheme.dimensions.encircledIconSize)
            .clip(CircleShape)
            .background(containerColor)
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
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Preview(name = "Light Mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "OLED Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun OneUINowPillPreview() {
    OneUITheme {
        Box(modifier = Modifier.padding(16.dp)) {
            OneUINowPill(text = "Cover Screen Active • Flex Mode")
        }
    }
}
