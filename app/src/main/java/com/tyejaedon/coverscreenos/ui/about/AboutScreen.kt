package com.tyejaedon.coverscreenos.ui.about

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.BuildConfig
import com.tyejaedon.coverscreenos.R
import com.tyejaedon.coverscreenos.services.overlay.ForegroundService
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUITheme
import com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUICardGroup
import com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUIScaffold

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
        context.assets.open("DICTIONARY_LICENSE.txt").bufferedReader().use { it.readText() }
    }

    OneUIScaffold(
        title = "About",
        subtitle = "Cover Screen OS • Version ${BuildConfig.VERSION_NAME}",
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(OneUITheme.dimensions.cardSpacing)
        ) {
            item {
                OneUICardGroup(header = "Application Information") {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = OneUITheme.colors.accent
                    )
                    Text(
                        stringResource(R.string.app_name),
                        style = OneUITheme.typography.titleLarge,
                        color = OneUITheme.colors.textPrimary
                    )
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})",
                        style = OneUITheme.typography.bodyMedium,
                        color = OneUITheme.colors.textSecondary
                    )
                }
            }

            item {
                OneUICardGroup(header = "How It Works") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(listOf(
                            Triple(Icons.Filled.VerifiedUser, "Grant Access",
                                "Permissions allow the launcher to host on the cover screen and show notifications."),
                            Triple(Icons.Filled.Palette, "Make It Yours",
                                "Customize cover wallpaper, dock apps, panel corner radius, and keyboard."),
                            Triple(Icons.Filled.DashboardCustomize, "Cover Display",
                                "Manage service state and live widgets on your outer screen.")
                        )) { (icon, title, description) ->
                            AboutStepCard(icon, title, description)
                        }
                    }
                }
            }

            item {
                OneUICardGroup(header = "Diagnostics & Runtime") {
                    Text(
                        "Service State: ${if (runtime.serviceActive) "Running" else "Stopped"}",
                        style = OneUITheme.typography.bodyLarge
                    )
                    Text(
                        "Launcher Host: ${if (runtime.launcherHostActive) "Active" else "Inactive"}",
                        style = OneUITheme.typography.bodyMedium,
                        color = OneUITheme.colors.textSecondary
                    )
                    Text(
                        "Cover Surface: ${if (runtime.overlayActive) "Visible" else "Inactive"}",
                        style = OneUITheme.typography.bodyMedium,
                        color = OneUITheme.colors.textSecondary
                    )
                    Text(
                        "Last Event: ${runtime.events.firstOrNull()?.description ?: "None recorded"}",
                        style = OneUITheme.typography.bodyMedium,
                        color = OneUITheme.colors.textSecondary
                    )
                }
            }

            item {
                OneUICardGroup(header = "Legal & Open Source") {
                    OutlinedButton(
                        onClick = { showLicense = !showLicense },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().coverMinimumTouchTarget()
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = OneUITheme.colors.accent)
                        Text(
                            if (showLicense) " Hide dictionary license" else " View dictionary license",
                            color = OneUITheme.colors.textPrimary
                        )
                    }
                    if (showLicense) {
                        Text(
                            license,
                            style = OneUITheme.typography.bodyMedium,
                            color = OneUITheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                item {
                    OneUICardGroup(header = "Developer Tools") {
                        OutlinedButton(
                            onClick = onDashboard,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth().coverMinimumTouchTarget()
                        ) {
                            Icon(Icons.Filled.BugReport, contentDescription = null, tint = OneUITheme.colors.accent)
                            Text(" Dashboard controls", color = OneUITheme.colors.textPrimary)
                        }
                        OutlinedButton(
                            onClick = onCustomize,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth().coverMinimumTouchTarget()
                        ) {
                            Icon(Icons.Filled.Palette, contentDescription = null, tint = OneUITheme.colors.accent)
                            Text(" Customize import/export", color = OneUITheme.colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutStepCard(icon: ImageVector, title: String, description: String) {
    Surface(
        shape = OneUITheme.shapes.card,
        color = OneUITheme.colors.cardSurfaceVariant,
        modifier = Modifier.width(220.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = OneUITheme.colors.accent)
            Text(title, style = OneUITheme.typography.titleLarge, color = OneUITheme.colors.textPrimary)
            Text(
                description,
                style = OneUITheme.typography.bodyMedium,
                color = OneUITheme.colors.textSecondary
            )
        }
    }
}

@Preview(name = "Light Mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "OLED Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun AboutScreenPreview() {
    OneUITheme {
        AboutScreen()
    }
}
