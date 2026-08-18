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
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper
import com.tyejaedon.coverscreenos.permissions.PermissionScreen
import com.tyejaedon.coverscreenos.ui.homescreen.HomeScreen
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsStore = remember { LauncherSettingsStore(applicationContext) }
            val settings by settingsStore.settings.collectAsState(initial = LauncherSettings())

            CoverOSTheme(themePreference = settings.themePreference) {
                PermissionScreen(
                    modifier = Modifier.fillMaxSize(),
                    onPermissionsGranted = {
                        ForegroundServiceHelper.startForegroundService(this)
                    },
                    grantedContent = {
                        HomeScreen(modifier = Modifier.fillMaxSize())
                    }
                )
            }
        }
    }
}