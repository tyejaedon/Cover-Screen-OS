package com.tyejaedon.coverscreenos.ui.theme.oneui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Standard One UI 8 spring physics spec for interactive components, bottom sheets,
 * tab pills, and card expansions.
 */
val OneUISpringSpec = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = 400f
)

/**
 * Applies Samsung One UI 8 tactile press feedback to clickable cards and pill buttons.
 *
 * - Compresses scale to [pressedScale] (default 0.97f) over 100ms on press.
 * - Springs back with signature One UI physics (damping 0.82, stiffness 400) upon release.
 * - Draws a subtle luminance overlay during the pressed state.
 */
@Composable
fun Modifier.oneUIPressable(
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    overlayColor: Color = Color.White.copy(alpha = 0.08f)
): Modifier {
    if (!enabled) return this

    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = if (isPressed) {
            tween(durationMillis = 100)
        } else {
            spring(dampingRatio = 0.82f, stiffness = 400f)
        },
        label = "oneUiPressScale"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (isPressed) {
                drawRect(overlayColor)
            }
        }
}
