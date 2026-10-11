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
 */
fun <T> oneUiSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
    dampingRatio = CoverOneUITokens.motionSpringDamping,
    stiffness = CoverOneUITokens.motionSpringStiffness,
    visibilityThreshold = visibilityThreshold
)

/** Ambient fade-in matched to the One UI enter duration. */
fun oneUiFadeIn(): EnterTransition = fadeIn(animationSpec = tween(CoverOneUITokens.motionEnterMillis))

/** Ambient fade-out matched to the One UI exit duration. */
fun oneUiFadeOut(): ExitTransition = fadeOut(animationSpec = tween(CoverOneUITokens.motionExitMillis))

/** Slide-up + fade entrance for bottom-anchored sheets/choosers/expanded panels. */
fun oneUiSlideUp(): EnterTransition =
    slideInVertically(
        initialOffsetY = { fullHeight -> fullHeight / 4 },
        animationSpec = tween(CoverOneUITokens.motionEnterMillis)
    ) + fadeIn(animationSpec = tween(CoverOneUITokens.motionEnterMillis))

/** Slide-down + fade exit, pairs with [oneUiSlideUp]. */
fun oneUiSlideDown(): ExitTransition =
    slideOutVertically(
        targetOffsetY = { fullHeight -> fullHeight / 4 },
        animationSpec = tween(CoverOneUITokens.motionExitMillis)
    ) + fadeOut(animationSpec = tween(CoverOneUITokens.motionExitMillis))
