package com.tyejaedon.coverscreenos.ui.theme.oneui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.tyejaedon.coverscreenos.ui.theme.LocalCoverAccentIsCustomized
import com.tyejaedon.coverscreenos.ui.theme.LocalCoverOverlayAccent
import androidx.compose.ui.unit.dp

/**
 * Design tokens for the One UI 8 restyle (see
 * `docs/architecture/plan-overlayOneUI8Restyle.md` section 4.1).
 *
 * Every overlay surface pulls its corner radius, spacing rhythm, depth-tier
 * alpha, and motion constants from here instead of re-parameterising ad-hoc
 * values at each call site.
 */
object CoverOneUITokens {
    // Continuous corner curves — three canonical radii, no more.
    val radiusHero = 32.dp
    val radiusPanel = 24.dp
    val radiusChip = 14.dp

    // Spacing rhythm — 4dp base, One UI uses generous exhalation.
    val gutter = 20.dp
    val gapLarge = 16.dp
    val gapMedium = 12.dp
    val gapSmall = 8.dp

    // Depth — three tiers only.
    // Tier 0 = ambient wallpaper.
    // Tier 1 = frosted panel (dock, notif tile, media card).
    // Tier 2 = elevated actionable (pressed key, expanded media, dialog).
    const val tier1FillAlpha = 0.22f
    const val tier1BorderAlpha = 0.28f
    const val tier2FillAlpha = 0.38f
    const val tier2BorderAlpha = 0.55f

    // Motion — One UI's signature soft spring.
    const val motionSpringDamping = 0.82f
    const val motionSpringStiffness = 380f
    const val motionEnterMillis = 260
    const val motionExitMillis = 200
}

/**
 * One UI's "one signal color per page" pillar: every overlay page gets exactly one
 * dominant accent, everything else stays neutral. See plan section 4.4.
 */
enum class OverlayPageAccent(val color: Color) {
    NOTIFICATIONS(Color(0xFF7C9CFF)),
    LOCK_DOCK(Color(0xFF6EE7C0)),
    APP_GRID(Color(0xFFFFB870)),
    MEDIA(Color(0xFFE879F9))
}

/** Per-page signal accent. Provide via `CompositionLocalProvider(LocalOverlayAccent provides ...)`. */
val LocalOverlayAccent = compositionLocalOf { OverlayPageAccent.APP_GRID }

/**
 * Resolves the effective accent for the current page: the user's explicitly chosen accent
 * color (if they customized it in settings) always wins, otherwise falls back to the page's
 * default One UI signal color from [LocalOverlayAccent].
 */
@Composable
fun oneUiEffectiveAccent(): Color =
    if (LocalCoverAccentIsCustomized.current) LocalCoverOverlayAccent.current else LocalOverlayAccent.current.color
