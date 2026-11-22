package com.tyejaedon.coverscreenos.ui.keyboard

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Tracks a monotonically increasing revision key that invalidates contact-based keyboard
 * suggestions whenever the `"include_contact_names"` preference or `READ_CONTACTS` runtime
 * permission state changes.
 *
 * Uses two Compose side-effect primitives while [enabled] is `true`:
 * - [DisposableEffect] registers a [SharedPreferences.OnSharedPreferenceChangeListener] on
 *   `"keyboard_words"` and cleanly unregisters it on disposal.
 * - [LaunchedEffect] periodically re-checks [Manifest.permission.READ_CONTACTS] because
 *   Android does not deliver a broadcast when the user revokes a permission in System Settings
 *   while an overlay remains mounted.
 *
 * @return A revision integer suitable as a `remember` / `LaunchedEffect` key.
 */
@Composable
internal fun rememberContactSuggestionRevision(enabled: Boolean): Int {
    val context = LocalContext.current
    var revision by remember(context) { mutableIntStateOf(0) }
    val preferences = remember(context) {
        context.getSharedPreferences("keyboard_words", Context.MODE_PRIVATE)
    }
    // React immediately when the user toggles contact-name suggestions in preferences.
    DisposableEffect(preferences, enabled) {
        if (!enabled) return@DisposableEffect onDispose {}

        val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "include_contact_names") revision++
        }
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
    }
    LaunchedEffect(context, enabled, revision) {
        if (!enabled || !preferences.getBoolean("include_contact_names", false)) return@LaunchedEffect
        val wasGranted = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        // Permission can be revoked while this overlay remains mounted.
        while (true) {
            delay(5_000)
            val isGranted = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
            if (isGranted != wasGranted) {
                revision++
                break
            }
        }
    }
    return revision
}
