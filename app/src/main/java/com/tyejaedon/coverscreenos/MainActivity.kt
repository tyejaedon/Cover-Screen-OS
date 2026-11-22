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
            // Holds the single settings store handle across UI recompositions; uses applicationContext
            // so retaining this store never keeps the short-lived Activity in memory.
            val settingsStore = remember { LauncherSettingsStore(applicationContext) }
            // Live Compose state mirroring the user's saved preferences, starting with defaults
            // for the first frame until DataStore finishes reading from disk.
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
