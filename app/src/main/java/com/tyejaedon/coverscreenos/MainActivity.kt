package com.tyejaedon.coverscreenos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper
import com.tyejaedon.coverscreenos.permissions.PermissionScreen
import com.tyejaedon.coverscreenos.ui.DeploymentStatusScreen
import com.tyejaedon.coverscreenos.ui.theme.CoverScreenOSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CoverScreenOSTheme {
                PermissionScreen(
                    modifier = Modifier.fillMaxSize(),
                    onPermissionsGranted = {
                        ForegroundServiceHelper.startForegroundService(this)
                    },
                    grantedContent = {
                        DeploymentStatusScreen(modifier = Modifier.fillMaxSize())
                    }
                )
            }
        }
    }
}