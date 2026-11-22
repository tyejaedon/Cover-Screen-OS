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
    /** Largest continuous corner radius reserved for hero panels, expanded media cards, and sheets. */
    val radiusHero = 32.dp
    /** Standard continuous corner radius for frosted tier-1 panels and notification tiles. */
    val radiusPanel = 24.dp
    /** Compact pill/chip corner radius for status badges, toggles, and suggestion strips. */
    val radiusChip = 14.dp

    // Spacing rhythm — 4dp base, One UI uses generous exhalation.
    /** Outer horizontal screen edge margin for overlay pages. */
    val gutter = 20.dp
    /** Generous gap between major overlay sections or card groups. */
    val gapLarge = 16.dp
    /** Standard spacing between sibling panels or rows inside a section. */
    val gapMedium = 12.dp
    /** Tight spacing between closely related elements such as icon + label pairs. */
    val gapSmall = 8.dp

    // Depth — three tiers only.
    // Tier 0 = ambient wallpaper.
    // Tier 1 = frosted panel (dock, notif tile, media card).
    // Tier 2 = elevated actionable (pressed key, expanded media, dialog).
    /** Translucent surface fill opacity for tier-1 panels when live backdrop blur is absent. */
    const val tier1FillAlpha = 0.22f
    /** Subtle hairline border opacity framing tier-1 panels. */
    const val tier1BorderAlpha = 0.28f
    /** Higher surface fill opacity for tier-2 elevated dialogs and expanded sheets. */
    const val tier2FillAlpha = 0.38f
    /** Stronger border opacity distinguishing tier-2 actionable surfaces from tier-1 cards. */
    const val tier2BorderAlpha = 0.55f

    // Motion — One UI's signature soft spring.
    /** Spring damping ratio (< 1.0 gives gentle settling without bouncy overshoot). */
    const val motionSpringDamping = 0.82f
    /** Spring stiffness controlling snap responsiveness across interactive One UI motion. */
    const val motionSpringStiffness = 380f
    /** Standard tween duration (ms) for entering overlay transitions. */
    const val motionEnterMillis = 260
    /** Slightly brisker tween duration (ms) for exiting/dismissing overlay transitions. */
    const val motionExitMillis = 200
}

/**
 * One UI's "one signal color per page" pillar: every overlay page gets exactly one
 * dominant accent, everything else stays neutral. See plan section 4.4.
 */
enum class OverlayPageAccent(val color: Color) {
    /** Cool indigo signal color for the notifications overlay page. */
    NOTIFICATIONS(Color(0xFF7C9CFF)),
    /** Mint green signal color for the lock-screen dock page. */
    LOCK_DOCK(Color(0xFF6EE7C0)),
    /** Warm amber signal color for the cover app launcher grid. */
    APP_GRID(Color(0xFFFFB870)),
    /** Vibrant magenta signal color for active media playback surfaces. */
    MEDIA(Color(0xFFE879F9))
}

/**
 * Per-page signal accent passed down subtrees via `CompositionLocalProvider(LocalOverlayAccent provides ...)`.
 *
 * Uses [compositionLocalOf] (rather than `staticCompositionLocalOf`) so switching accents on one
 * nested page only invalidates composables that actually read `.current`.
 */
val LocalOverlayAccent = compositionLocalOf { OverlayPageAccent.APP_GRID }

/**
 * Resolves the effective accent for the current page: the user's explicitly chosen accent
 * color (if they customized it in settings) always wins, otherwise falls back to the page's
 * default One UI signal color from [LocalOverlayAccent].
 */
@Composable
fun oneUiEffectiveAccent(): Color =
    // Explicit user accent selection in settings overrides per-page One UI signal hues.
    if (LocalCoverAccentIsCustomized.current) LocalCoverOverlayAccent.current else LocalOverlayAccent.current.color