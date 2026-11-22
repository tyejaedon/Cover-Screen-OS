package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Phase G: returns a callable that plays the standard key-click sound effect
 * ([AudioManager.FX_KEYPRESS_STANDARD]) via [AudioManager.playSoundEffect] when the user's
 * "Key-press sound" preference is enabled; a no-op otherwise.
 *
 * Delegating to [AudioManager.playSoundEffect] honors system audio routing, ringer state, and
 * UI sound-effect volume automatically without managing custom `SoundPool` assets. Independent of
 * [HapticTier] — sound and haptics are separate user-facing toggles.
 */
@Composable
internal fun rememberKeySoundPerformer(): () -> Unit {
    val context = LocalContext.current
    // Preferences store providing the user's "Key-press sound" toggle.
    val settingsStore = remember(context) { LauncherSettingsStore(context.applicationContext) }
    // Track only the key-press sound boolean to avoid recomposing on unrelated preference changes.
    val soundEnabled by remember(settingsStore) {
        settingsStore.settings.map { it.keyPressSoundEnabled }.distinctUntilChanged()
    }.collectAsState(initial = LauncherSettings().keyPressSoundEnabled)
    // Cache AudioManager from applicationContext to prevent retaining Activity/Overlay references.
    val audioManager = remember(context) {
        context.applicationContext.getSystemService(AudioManager::class.java)
    }
    return remember(soundEnabled, audioManager) {
        if (!soundEnabled || audioManager == null) {
            { /* no-op */ }
        } else {
            { audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD) }
        }
    }
}
