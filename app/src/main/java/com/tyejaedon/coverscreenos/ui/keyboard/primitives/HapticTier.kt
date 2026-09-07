package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Tiered haptic feedback for keyboard primitives. Kept independent of any
 * `Vibrator` service so that hosts without VIBRATE permission still get
 * best-effort feedback through the platform's `View.performHapticFeedback`.
 */
enum class HapticTier {
    /** No feedback. */
    None,
    /** Very light tap — used for soft-key touches. */
    Light,
    /** Default keyboard tap — matches system IME behaviour. */
    Standard,
    /** Long-press / repeat threshold cue. */
    Strong;

    internal fun toHapticConstant(): Int? = when (this) {
        None -> null
        Light -> HapticFeedbackConstants.CLOCK_TICK
        Standard -> HapticFeedbackConstants.KEYBOARD_TAP
        Strong -> HapticFeedbackConstants.LONG_PRESS
    }
}

/**
 * Returns a callable that triggers the given haptic tier on the nearest
 * host `View`. Safe to call in composables outside the compose preview
 * (returns a no-op there).
 */
@Composable
internal fun rememberHapticPerformer(tier: HapticTier): () -> Unit {
    val view: View = LocalView.current
    return remember(view, tier) {
        val code = tier.toHapticConstant() ?: return@remember { /* no-op */ }
        {
            // performHapticFeedback returns false silently when unsupported.
            view.performHapticFeedback(code)
        }
    }
}

