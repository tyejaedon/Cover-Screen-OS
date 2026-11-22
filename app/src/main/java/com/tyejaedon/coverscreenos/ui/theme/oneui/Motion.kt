package com.tyejaedon.coverscreenos.ui.theme.oneui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

/**
 * One UI's signature soft spring — use for every `animateFloatAsState` / `animateContentSize`
 * / `AnimatedVisibility` in overlay code instead of `tween(_, LinearEasing)`.
 *
 * Uses [CoverOneUITokens.motionSpringDamping] (`0.82f`) and [CoverOneUITokens.motionSpringStiffness]
 * (`380f`) for gentle, responsive settling. Pass [visibilityThreshold] when animating typed units
 * (such as `Dp` or `IntOffset`) so Compose knows when the spring is close enough to snap to rest.
 */
fun <T> oneUiSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
    dampingRatio = CoverOneUITokens.motionSpringDamping,
    stiffness = CoverOneUITokens.motionSpringStiffness,
    visibilityThreshold = visibilityThreshold
)

/** Ambient fade-in [EnterTransition] matched to the One UI enter duration (`260ms`). */
fun oneUiFadeIn(): EnterTransition = fadeIn(animationSpec = tween(CoverOneUITokens.motionEnterMillis))

/** Ambient fade-out [ExitTransition] matched to the One UI exit duration (`200ms`). */
fun oneUiFadeOut(): ExitTransition = fadeOut(animationSpec = tween(CoverOneUITokens.motionExitMillis))

/**
 * Slide-up + fade entrance for bottom-anchored sheets, choosers, and expanded panels.
 *
 * Travels 25% of the container height (`fullHeight / 4`) rather than a full-screen slide so
 * sheets appear brisk and grounded, combined with [fadeIn] via Compose's `+` transition operator.
 */
fun oneUiSlideUp(): EnterTransition =
    slideInVertically(
        initialOffsetY = { fullHeight -> fullHeight / 4 },
        animationSpec = tween(CoverOneUITokens.motionEnterMillis)
    ) + fadeIn(animationSpec = tween(CoverOneUITokens.motionEnterMillis))

/**
 * Slide-down + fade [ExitTransition] pairing with [oneUiSlideUp], easing out over the slightly
 * shorter exit window (`200ms`).
 */
fun oneUiSlideDown(): ExitTransition =
    slideOutVertically(
        targetOffsetY = { fullHeight -> fullHeight / 4 },
        animationSpec = tween(CoverOneUITokens.motionExitMillis)
    ) + fadeOut(animationSpec = tween(CoverOneUITokens.motionExitMillis))