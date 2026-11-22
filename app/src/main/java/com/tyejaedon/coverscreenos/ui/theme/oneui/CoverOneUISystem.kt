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
    /** Holds the largest (`32.dp`) corner curve used for hero containers, dialogs, and expanded media cards. */
    val radiusHero = 32.dp
    /** Holds the standard (`24.dp`) corner curve used for frosted panels, docks, and notification cards. */
    val radiusPanel = 24.dp
    /** Holds the compact (`14.dp`) corner curve used for small status pills, toggles, and suggestion chips. */
    val radiusChip = 14.dp

    // Spacing rhythm — 4dp base, One UI uses generous exhalation.
    /** Holds the `20.dp` outer screen-edge margin keeping overlay content away from the display bezel. */
    val gutter = 20.dp
    /** Holds the `16.dp` generous gap separating major sections or card groups. */
    val gapLarge = 16.dp
    /** Holds the `12.dp` medium gap between neighboring panels or rows inside a section. */
    val gapMedium = 12.dp
    /** Holds the `8.dp` tight gap between paired items such as an icon and its label. */
    val gapSmall = 8.dp

    // Depth — three tiers only.
    // Tier 0 = ambient wallpaper.
    // Tier 1 = frosted panel (dock, notif tile, media card).
    // Tier 2 = elevated actionable (pressed key, expanded media, dialog).
    /** Holds the 22% fill opacity used by standard Tier-1 cards when live wallpaper blur is unavailable. */
    const val tier1FillAlpha = 0.22f
    /** Holds the 28% outline opacity drawing a subtle glass edge around Tier-1 panels. */
    const val tier1BorderAlpha = 0.28f
    /** Holds the 38% fill opacity making Tier-2 popups and dialogs visibly denser than background cards. */
    const val tier2FillAlpha = 0.38f
    /** Holds the 55% outline opacity giving Tier-2 interactive surfaces a crisper border highlight. */
    const val tier2BorderAlpha = 0.55f

    // Motion — One UI's signature soft spring.
    /** Holds the spring damping (`0.82f`) that settles animations smoothly without exaggerated bouncing. */
    const val motionSpringDamping = 0.82f
    /** Holds the spring stiffness (`380f`) controlling how quickly animations snap toward their destination. */
    const val motionSpringStiffness = 380f
    /** Holds the `260ms` entrance animation duration used for panels fading or sliding in. */
    const val motionEnterMillis = 260
    /** Holds the `200ms` exit animation duration so dismissing panels clears slightly faster than opening them. */
    const val motionExitMillis = 200
}

/**
 * One UI's "one signal color per page" pillar: every overlay page gets exactly one
 * dominant accent, everything else stays neutral. See plan section 4.4.
 */
enum class OverlayPageAccent(val color: Color) {
    /** Holds the cool indigo highlight color (`0xFF7C9CFF`) assigned to the notifications overlay page. */
    NOTIFICATIONS(Color(0xFF7C9CFF)),
    /** Holds the mint-green highlight color (`0xFF6EE7C0`) assigned to the lock-screen dock page. */
    LOCK_DOCK(Color(0xFF6EE7C0)),
    /** Holds the warm amber highlight color (`0xFFFFB870`) assigned to the cover-screen app grid page. */
    APP_GRID(Color(0xFFFFB870)),
    /** Holds the vibrant magenta highlight color (`0xFFE879F9`) assigned to the media player overlay page. */
    MEDIA(Color(0xFFE879F9))
}

/**
 * Holds the active page's default [OverlayPageAccent] broadcast via `CompositionLocalProvider`.
 *
 * Uses [compositionLocalOf] (instead of `staticCompositionLocalOf`) so switching accents on one
 * nested page only refreshes the widgets that read `LocalOverlayAccent.current`.
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