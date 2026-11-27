package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
 * Observes [Settings.System.HAPTIC_FEEDBACK_ENABLED] via a [ContentObserver] so key presses
 * read a cached boolean in memory instead of issuing synchronous Binder IPC on every tap.
 */
@Composable
internal fun rememberSystemHapticFeedbackEnabled(context: Context = LocalContext.current): Boolean {
    val appContext = remember(context) { context.applicationContext }
    fun readSystemSetting(): Boolean = runCatching {
        Settings.System.getInt(
            appContext.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1
        ) != 0
    }.getOrDefault(true)

    var enabled by remember(appContext) { mutableStateOf(readSystemSetting()) }

    DisposableEffect(appContext) {
        val uri = Settings.System.getUriFor(Settings.System.HAPTIC_FEEDBACK_ENABLED)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = readSystemSetting()
            }
        }
        runCatching {
            if (uri != null) {
                appContext.contentResolver.registerContentObserver(uri, false, observer)
            }
        }
        onDispose {
            runCatching { appContext.contentResolver.unregisterContentObserver(observer) }
        }
    }
    return enabled
}

/**
 * Returns a callable that triggers the given haptic tier, scaled by the user's persisted
 * [HapticStrength] setting in [LauncherSettingsStore] and gated by the cached system-wide
 * [Settings.System.HAPTIC_FEEDBACK_ENABLED] toggle.
 */
@Composable
internal fun rememberHapticPerformer(tier: HapticTier): () -> Unit {
    val context = LocalContext.current
    val settingsStore = remember(context) { LauncherSettingsStore(context.applicationContext) }
    val hapticStrength by remember(settingsStore) {
        settingsStore.settings.map { it.hapticStrength }.distinctUntilChanged()
    }.collectAsState(initial = LauncherSettings().hapticStrength)
    return rememberHapticPerformer(tier = tier, hapticStrength = hapticStrength)
}

/**
 * Hoisted overload accepting a pre-collected [hapticStrength] so callers at the keyboard root
 * do not spawn additional [LauncherSettingsStore] instances or DataStore Flow collectors.
 */
@Composable
internal fun rememberHapticPerformer(
    tier: HapticTier,
    hapticStrength: HapticStrength
): () -> Unit {
    val view: View = LocalView.current
    val context = LocalContext.current
    val systemHapticsEnabled = rememberSystemHapticFeedbackEnabled(context)
    val effectiveTier = remember(tier, hapticStrength) { tier.scaledBy(hapticStrength) }
    return remember(view, effectiveTier, systemHapticsEnabled) {
        if (!systemHapticsEnabled) return@remember { /* no-op */ }
        val code = effectiveTier.toHapticConstant() ?: return@remember { /* no-op */ }
        val effect = effectiveTier.toVibrationEffect() ?: return@remember { /* no-op */ }
        val vibrator = view.context.getSystemService(Vibrator::class.java)
        return@remember {
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createPredefined(effect))
            } else {
                view.performHapticFeedback(code)
            }
        }
    }
}
