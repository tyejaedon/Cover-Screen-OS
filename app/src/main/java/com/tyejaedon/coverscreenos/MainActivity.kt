package com.tyejaedon.coverscreenos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.ui.appshell.AppShell
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme

/**
 * Companion phone-screen entry point for Cover-Screen-OS.
 *
 * While the cover display desktop is rendered independently inside an overlay service, this
 * [ComponentActivity] hosts the main-screen companion UI ([AppShell]) where users complete
 * permission onboarding, customize dock/wallpaper/keyboard settings, and manage preferences.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opt into edge-to-edge drawing so Compose layouts extend behind system status and
        // navigation bars instead of being letterboxed by legacy system-bar decor fits.
        enableEdgeToEdge()

        setContent {
            // Retain one DataStore repository instance across recompositions using applicationContext
            // to avoid leaking this Activity reference.
            val settingsStore = remember { LauncherSettingsStore(applicationContext) }
            // Observe persisted preferences reactively; emit default LauncherSettings on first
            // frame while DataStore finishes its initial asynchronous disk read.
            val settings by settingsStore.settings.collectAsState(initial = LauncherSettings())

            CoverOSTheme(
                themePreference = settings.themePreference,
                accentColor = settings.accentColor,
                panelCornerRadiusDp = settings.panelCornerRadiusDp
            ) {
                AppShell(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
