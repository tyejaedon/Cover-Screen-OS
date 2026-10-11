package com.tyejaedon.coverscreenos.ui.theme.oneui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.ui.theme.LocalCoverOverlayPanelBorder
import com.tyejaedon.coverscreenos.ui.theme.LocalCoverPanelCornerRadius
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Tier-1 frosted panel: the default surface for dock, notification tiles, compact media
 * cards, and search. Respects the user's custom panel-corner-radius preference by default
 * ([LocalCoverPanelCornerRadius]) rather than forcing the canonical [CoverOneUITokens.radiusPanel]
 * on every caller, and defers to [LocalCoverOverlayPanelBorder] when the user has picked a
 * custom accent so their chosen hue keeps showing up as a panel border.
 *
 * When [hazeState] is supplied, the fill is a real-time backdrop blur sampled from it
 * (typically the wallpaper layer) instead of a flat translucent color, so panels read as
 * frosted glass over whatever is behind them.
 */
@Composable
fun Modifier.oneUiPanel(
    shape: Shape = RoundedCornerShape(LocalCoverPanelCornerRadius.current),
    signal: Color? = null,
    hazeState: HazeState? = null,
    blurRadius: Dp = 24.dp
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val borderColor = signal?.copy(alpha = 0.9f)
        ?: LocalCoverOverlayPanelBorder.current
        ?: scheme.outline.copy(alpha = CoverOneUITokens.tier1BorderAlpha)

    val fillModifier = if (hazeState != null) {
        Modifier
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    tints = listOf(HazeTint(Color.Black.copy(alpha = 0.40f))),
                    blurRadius = blurRadius,
                    noiseFactor = 0.04f
                )
            )
    } else {
        Modifier
            .clip(shape)
            .background(scheme.surface.copy(alpha = CoverOneUITokens.tier1FillAlpha))
    }

    return this
        .then(fillModifier)
        .border(1.dp, borderColor, shape)
}

/**
 * Tier-2 elevated, actionable surface: expanded media player, dialogs, bottom-sheet choosers.
 * Heavier fill/border alpha than [oneUiPanel] so it visually "lifts" above the rest of the page.
 */
@Composable
fun Modifier.oneUiElevatedPanel(
    shape: Shape = RoundedCornerShape(CoverOneUITokens.radiusHero),
    signal: Color? = null
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val borderColor = signal?.copy(alpha = 0.95f)
        ?: LocalCoverOverlayPanelBorder.current
        ?: scheme.outline.copy(alpha = CoverOneUITokens.tier2BorderAlpha)

    return this
        .clip(shape)
        .background(scheme.surface.copy(alpha = CoverOneUITokens.tier2FillAlpha))
        .border(1.dp, borderColor, shape)
}

/**
 * One UI chip/pill: status indicators, mode toggles, suggestion strips. [selected] swaps the
 * neutral tier-1 fill for the resolved page accent ([oneUiEffectiveAccent]) so selection state
 * reads clearly without introducing a second accent hue.
 */
@Composable
fun Modifier.oneUiChip(
    selected: Boolean = false,
    shape: Shape = RoundedCornerShape(CoverOneUITokens.radiusChip)
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val accent = oneUiEffectiveAccent()

    val fillColor = if (selected) accent.copy(alpha = 0.9f) else scheme.surface.copy(alpha = CoverOneUITokens.tier1FillAlpha)
    val borderColor = if (selected) accent else scheme.outline.copy(alpha = CoverOneUITokens.tier1BorderAlpha)

    return this
        .clip(shape)
        .background(fillColor)
        .border(1.dp, borderColor, shape)
}

/**
 * One UI's "squircle" icon mask — a rounded-square rather than a full circle. [cornerRadius]
 * scales with icon size per the plan (12dp for 40dp notification icons, 14dp for 56dp dock
 * icons).
 */
fun Modifier.oneUiIconSquircle(cornerRadius: Dp = 12.dp): Modifier =
    this.clip(RoundedCornerShape(cornerRadius))
