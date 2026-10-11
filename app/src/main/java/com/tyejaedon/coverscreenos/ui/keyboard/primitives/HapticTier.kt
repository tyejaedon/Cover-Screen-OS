package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import android.view.HapticFeedbackConstants
import android.view.View
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.collectAsState
import com.tyejaedon.coverscreenos.datastore.HapticStrength
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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

    internal fun toVibrationEffect(): Int? = when (this) {
        None -> null
        Light -> VibrationEffect.EFFECT_TICK
        Standard -> VibrationEffect.EFFECT_CLICK
        Strong -> VibrationEffect.EFFECT_HEAVY_CLICK
    }
}

/**
 * Scales a resolved [HapticTier] by the user's [HapticStrength] preference:
 * `Off` disables all feedback, `Light` caps every tier at [HapticTier.Light],
 * `Standard` is a no-op (tiers fire as resolved), and `Strong` bumps each
 * non-[HapticTier.None] tier up one notch (`Light` -> `Standard`,
 * `Standard`/`Strong` -> `Strong`).
 */
internal fun HapticTier.scaledBy(strength: HapticStrength): HapticTier = when (strength) {
    HapticStrength.Off -> HapticTier.None
    HapticStrength.Light -> if (this == HapticTier.None) HapticTier.None else HapticTier.Light
    HapticStrength.Standard -> this
    HapticStrength.Strong -> when (this) {
        HapticTier.None -> HapticTier.None
        HapticTier.Light -> HapticTier.Standard
        HapticTier.Standard, HapticTier.Strong -> HapticTier.Strong
    }
}

/**
 * Returns a callable that triggers the given haptic tier on the nearest
 * host `View`, scaled by the user's persisted [HapticStrength] setting.
 * Safe to call in composables outside the compose preview (returns a no-op
 * there).
 */
@Composable
internal fun rememberHapticPerformer(tier: HapticTier): () -> Unit {
    val view: View = LocalView.current
    val context = LocalContext.current
    val settingsStore = remember(context) { LauncherSettingsStore(context.applicationContext) }
    val hapticStrength by remember(settingsStore) {
        settingsStore.settings.map { it.hapticStrength }.distinctUntilChanged()
    }.collectAsState(initial = LauncherSettings().hapticStrength)
    val effectiveTier = remember(tier, hapticStrength) { tier.scaledBy(hapticStrength) }
    return remember(view, effectiveTier) {
        val code = effectiveTier.toHapticConstant() ?: return@remember { /* no-op */ }
        val effect = effectiveTier.toVibrationEffect() ?: return@remember { /* no-op */ }
        val vibrator = view.context.getSystemService(Vibrator::class.java)
        return@remember {
            // performHapticFeedback returns false silently when unsupported.
            if (Settings.System.getInt(
                    view.context.contentResolver,
                    Settings.System.HAPTIC_FEEDBACK_ENABLED,
                    1
                ) != 0
            ) {
                if (vibrator?.hasVibrator() == true) {
                    vibrator.vibrate(VibrationEffect.createPredefined(effect))
                } else {
                    view.performHapticFeedback(code)
                }
            }
        }
    }
}
