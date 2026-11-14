package com.tyejaedon.coverscreenos.ui.theme.oneui.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUITheme
import kotlin.math.roundToInt

/**
 * Signature Samsung One UI 8 Pill Slider.
 *
 * Thick 24dp stadium-shaped track (CircleShape) with an embedded leading icon
 * inside the active accent track and tactile gesture dragging.
 */
@Composable
fun OneUIPillSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val normalizedFraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }

    val animatedThumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f),
        label = "pillSliderThumbScale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(OneUITheme.dimensions.sliderHeight)
            .clip(CircleShape)
            .background(OneUITheme.colors.cardSurfaceVariant)
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    val newValue = valueRange.start + fraction * rangeSpan
                    onValueChange(newValue)
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        val newValue = valueRange.start + fraction * rangeSpan
                        onValueChange(newValue)
                    }
                )
            }
    ) {
        val totalWidth = maxWidth
        val activeWidth = totalWidth * normalizedFraction

        // Active Track (Accent)
        Box(
            modifier = Modifier
                .width(activeWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(if (enabled) OneUITheme.colors.accent else OneUITheme.colors.textSecondary.copy(alpha = 0.4f))
        )

        // Embedded Leading Icon
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Tactile Thumb Indicator (Flush pill handle)
        val thumbOffset = ((totalWidth - 20.dp) * normalizedFraction)
        Box(
            modifier = Modifier
                .offset { IntOffset(x = (thumbOffset.value * density).roundToInt(), y = 0) }
                .size(24.dp)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(Color.White)
                .shadow(2.dp, CircleShape)
        )
    }
}

@Preview(name = "Light Mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "OLED Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun OneUIPillSliderPreview() {
    var sliderVal by remember { mutableFloatStateOf(0.65f) }
    OneUITheme {
        Box(modifier = Modifier.padding(24.dp)) {
            OneUIPillSlider(
                value = sliderVal,
                onValueChange = { sliderVal = it },
                leadingIcon = Icons.Filled.RoundedCorner
            )
        }
    }
}
