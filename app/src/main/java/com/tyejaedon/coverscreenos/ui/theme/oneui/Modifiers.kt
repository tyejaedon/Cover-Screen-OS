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
    // Active Material 3 color palette providing fallback surface and outline tokens.
    val scheme = MaterialTheme.colorScheme
    // Resolved 1dp border color: explicit per-panel signal > user accent border > neutral translucent outline.
    val borderColor = signal?.copy(alpha = 0.9f)
        ?: LocalCoverOverlayPanelBorder.current
        ?: scheme.outline.copy(alpha = CoverOneUITokens.tier1BorderAlpha)

    // Background fill modifier: chooses live Haze frosted-glass blur when hazeState is provided,
    // or falls back to a flat translucent surface tint when blurring is unavailable (e.g. Previews).
    val fillModifier = if (hazeState != null) {
        // Clip before hazeEffect so the live GPU backdrop blur stays confined to the rounded card bounds.
        Modifier
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    // Dark translucent tint ensures foreground typography stays legible over bright wallpapers.
                    tints = listOf(HazeTint(Color.Black.copy(alpha = 0.40f))),
                    blurRadius = blurRadius,
                    // Subtle grain/dithering reduces color banding across smooth dark blur gradients.
                    noiseFactor = 0.04f
                )
            )
    } else {
        // Flat translucent fallback when no HazeState source layer is available (e.g., previews).
        Modifier
            .clip(shape)
            .background(scheme.surface.copy(alpha = CoverOneUITokens.tier1FillAlpha))
    }

    // Apply the 1dp hairline border after clipping/filling so the stroke frames the outer perimeter cleanly.
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
    // Active color scheme supplying the base surface and neutral outline colors.
    val scheme = MaterialTheme.colorScheme
    // Higher-opacity border stroke (tier2BorderAlpha) helping dialogs stand out above Tier-1 cards.
    val borderColor = signal?.copy(alpha = 0.95f)
        ?: LocalCoverOverlayPanelBorder.current
        ?: scheme.outline.copy(alpha = CoverOneUITokens.tier2BorderAlpha)

    // Clip before drawing the tier-2 translucent fill, then frame with the higher-contrast border.
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
    // Active color palette providing neutral surface and outline fallbacks for unselected chips.
    val scheme = MaterialTheme.colorScheme
    // Single resolved accent color (user override or current page signal hue) for selected state.
    val accent = oneUiEffectiveAccent()

    // Chip background fill: highlighted with 90% accent when selected, otherwise subtle Tier-1 frost.
    val fillColor = if (selected) accent.copy(alpha = 0.9f) else scheme.surface.copy(alpha = CoverOneUITokens.tier1FillAlpha)
    // Chip border color: crisp solid accent when selected, otherwise low-opacity neutral outline.
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