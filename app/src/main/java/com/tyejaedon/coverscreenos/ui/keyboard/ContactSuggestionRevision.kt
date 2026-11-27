package com.tyejaedon.coverscreenos.ui.keyboard

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Tracks a monotonically increasing revision key that invalidates contact-based keyboard
 * suggestions whenever the `"include_contact_names"` preference or `READ_CONTACTS` runtime
 * permission state changes.
 *
 * Uses event-driven observers while [enabled] is `true` (zero background polling):
 * - [SharedPreferences.OnSharedPreferenceChangeListener] on `"keyboard_words"` for consent toggles.
 * - [LifecycleEventObserver] watching [Lifecycle.Event.ON_RESUME] to catch `READ_CONTACTS`
 *   grant/revocation changes made in System Settings without any periodic polling loop.
 *
 * @return A revision integer suitable as a `remember` / `LaunchedEffect` key.
 */
@Composable
internal fun rememberContactSuggestionRevision(enabled: Boolean): Int {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var revision by remember(context) { mutableIntStateOf(0) }
    val preferences = remember(context) {
        context.getSharedPreferences("keyboard_words", Context.MODE_PRIVATE)
    }
    var lastKnownGranted by remember(context) {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    DisposableEffect(preferences, lifecycleOwner, enabled) {
        if (!enabled) return@DisposableEffect onDispose {}

        fun checkPermissionChange() {
            val isGranted = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
            if (isGranted != lastKnownGranted) {
                lastKnownGranted = isGranted
                revision++
            }
        }

        checkPermissionChange()

        val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "include_contact_names") {
                lastKnownGranted = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                    PackageManager.PERMISSION_GRANTED
                revision++
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissionChange()
            }
        }

        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return revision
}
