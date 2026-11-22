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
 * Tiered haptic feedback for keyboard primitives.
 *
 * Maps each semantic tier to both an Android [VibrationEffect] predefined waveform constant
 * (for direct [Vibrator] playback when hardware vibration is available) and a
 * [HapticFeedbackConstants] flag so hosts without a vibrator or `VIBRATE` capability still
 * receive best-effort feedback through the platform's `View.performHapticFeedback`.
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

    /** Maps this tier to its fallback [HapticFeedbackConstants] constant, or `null` for [None]. */
    internal fun toHapticConstant(): Int? = when (this) {
        None -> null
        Light -> HapticFeedbackConstants.CLOCK_TICK
        Standard -> HapticFeedbackConstants.KEYBOARD_TAP
        Strong -> HapticFeedbackConstants.LONG_PRESS
    }

    /** Maps this tier to its predefined [VibrationEffect] effect ID, or `null` for [None]. */
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
 * Returns a callable that triggers the given haptic tier, scaled by the user's persisted
 * [HapticStrength] setting in [LauncherSettingsStore] and gated by the system-wide
 * [Settings.System.HAPTIC_FEEDBACK_ENABLED] toggle.
 *
 * Uses `Vibrator` (`getSystemService(Vibrator::class.java)` — compatible down to minSdk 28 without
 * requiring API 31's `VibratorManager`) with [VibrationEffect.createPredefined] when hardware
 * vibration is present, falling back to `View.performHapticFeedback` on the host [View].
 * Safe to call in composables outside the compose preview (returns a no-op there).
 */
@Composable
internal fun rememberHapticPerformer(tier: HapticTier): () -> Unit {
    // Host View used for fallback View.performHapticFeedback when no hardware Vibrator is active.
    val view: View = LocalView.current
    val context = LocalContext.current
    // DataStore preferences repository holding the user's haptic strength setting.
    val settingsStore = remember(context) { LauncherSettingsStore(context.applicationContext) }
    // Collect only hapticStrength with distinctUntilChanged so unrelated settings edits do not re-allocate.
    val hapticStrength by remember(settingsStore) {
        settingsStore.settings.map { it.hapticStrength }.distinctUntilChanged()
    }.collectAsState(initial = LauncherSettings().hapticStrength)
    // Final haptic intensity after applying the user's Off / Light / Standard / Strong preference.
    val effectiveTier = remember(tier, hapticStrength) { tier.scaledBy(hapticStrength) }
    return remember(view, effectiveTier) {
        // Fallback HapticFeedbackConstants flag (or null when scaled down to None).
        val code = effectiveTier.toHapticConstant() ?: return@remember { /* no-op */ }
        // Predefined VibrationEffect ID for hardware vibrator playback.
        val effect = effectiveTier.toVibrationEffect() ?: return@remember { /* no-op */ }
        // Query Vibrator directly rather than VibratorManager (API 31+) to support minSdk 28.
        val vibrator = view.context.getSystemService(Vibrator::class.java)
        return@remember {
            // Respect the Android OS "Touch vibration" system setting before triggering hardware haptics.
            if (Settings.System.getInt(
                    view.context.contentResolver,
                    Settings.System.HAPTIC_FEEDBACK_ENABLED,
                    1
                ) != 0
            ) {
                if (vibrator?.hasVibrator() == true) {
                    vibrator.vibrate(VibrationEffect.createPredefined(effect))
                } else {
                    // performHapticFeedback returns false silently when unsupported.
                    view.performHapticFeedback(code)
                }
            }
        }
    }
}
