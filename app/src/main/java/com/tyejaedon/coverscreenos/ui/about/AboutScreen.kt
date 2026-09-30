package com.tyejaedon.coverscreenos.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.BuildConfig
import com.tyejaedon.coverscreenos.R
import com.tyejaedon.coverscreenos.services.overlay.ForegroundService
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    onDashboard: () -> Unit = {},
    onCustomize: () -> Unit = {}
) {
    val context = LocalContext.current
    val runtime by ForegroundService.runtime.collectAsState()
    var showLicense by rememberSaveable { mutableStateOf(false) }
    val license = remember(context) {
        context.assets.open("CMU_DICT_LICENSE.txt").bufferedReader().use { it.readText() }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("How it works", style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(listOf(
                Triple(Icons.Filled.VerifiedUser, "Grant access",
                    "Permissions allow the launcher to run and show notifications."),
                Triple(Icons.Filled.Palette, "Make it yours",
                    "Customize the cover wallpaper, dock, appearance, and keyboard."),
                Triple(Icons.Filled.DashboardCustomize, "Use your cover screen",
                    "Dashboard shows the service state and lets you start or stop it.")
            )) { (icon, title, description) ->
                AboutStepCard(icon, title, description)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Diagnostics", style = MaterialTheme.typography.titleLarge)
                Text("Service: ${if (runtime.serviceActive) "Running" else "Stopped"}")
                Text("Accessibility launcher host: ${if (runtime.launcherHostActive) "Active" else "Inactive"}")
                Text("Cover overlay: ${if (runtime.overlayActive) "Visible" else "Inactive"}")
                Text("Last runtime event: ${runtime.events.firstOrNull()?.description ?: "None recorded"}")
                Text("Crash history: Not collected", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Legal", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(
                    onClick = { showLicense = !showLicense },
                    modifier = Modifier.fillMaxWidth().coverMinimumTouchTarget()
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                    Text(if (showLicense) " Hide CMU Pronouncing Dictionary license"
                        else " CMU Pronouncing Dictionary license")
                }
                if (showLicense) {
                    Text(license, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (BuildConfig.DEBUG) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Debug tools", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = onDashboard, modifier = Modifier.fillMaxWidth().coverMinimumTouchTarget()) {
                        Icon(Icons.Filled.BugReport, contentDescription = null)
                        Text(" Dashboard preview controls")
                    }
                    OutlinedButton(onClick = onCustomize, modifier = Modifier.fillMaxWidth().coverMinimumTouchTarget()) {
                        Icon(Icons.Filled.Palette, contentDescription = null)
                        Text(" Customize import/export")
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutStepCard(icon: ImageVector, title: String, description: String) {
    Card(Modifier.width(240.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
