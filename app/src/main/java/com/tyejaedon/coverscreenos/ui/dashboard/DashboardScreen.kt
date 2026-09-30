package com.tyejaedon.coverscreenos.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.BuildConfig
import com.tyejaedon.coverscreenos.ui.permissions.FeatureDegradedNotice

@Composable
fun DashboardScreen(
    state: DashboardState,
    onPermissions: () -> Unit,
    onToggleService: () -> Boolean,
    modifier: Modifier = Modifier
) {
    var previewVisible by remember { mutableStateOf(false) }
    var startBlocked by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DashboardHeroCard(state)
        if (state.permissions.missing.isNotEmpty()) {
            FeatureDegradedNotice(
                "Cover launcher unavailable until ${state.permissions.missing.joinToString()} are enabled. " +
                    "You can still use the other tabs.",
                onPermissions
            )
        }
        QuickActionsRow(
            running = state.runtime.serviceActive,
            onToggleService = { startBlocked = !onToggleService() },
            onPermissions = onPermissions,
            onPreview = { previewVisible = true }
        )
        if (startBlocked) {
            Text(
                "Start blocked: grant required permissions first.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (BuildConfig.DEBUG) RecentActivityCard(state.runtime.events)
        TipsCarousel()
    }
    if (previewVisible) {
        AlertDialog(
            onDismissRequest = { previewVisible = false },
            title = { Text("Cover surface preview") },
            text = {
                Column(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Preview only - not the live cover display",
                        style = MaterialTheme.typography.labelMedium)
                    Text("Cover Screen OS", style = MaterialTheme.typography.headlineSmall)
                    Text("Dock  |  Apps  |  Notifications",
                        style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = {
                TextButton(onClick = { previewVisible = false }) { Text("Close preview") }
            }
        )
    }
}
