package com.tyejaedon.coverscreenos.ui.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.BuildConfig
import com.tyejaedon.coverscreenos.ui.appshell.TopBarStatusChip
import com.tyejaedon.coverscreenos.ui.permissions.FeatureDegradedNotice
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUITheme
import com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUICardGroup
import com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUINowPill
import com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUIScaffold

@Composable
fun DashboardScreen(
    state: DashboardState,
    onPermissions: () -> Unit,
    onToggleService: () -> Boolean,
    modifier: Modifier = Modifier,
    topBarActions: @Composable () -> Unit = {}
) {
    var previewVisible by remember { mutableStateOf(false) }
    var startBlocked by remember { mutableStateOf(false) }

    OneUIScaffold(
        title = "Cover Screen OS",
        subtitle = if (state.runtime.serviceActive) "Running • Clamshell Outer Display" else "Dormant • Tap to Start",
        actions = {
            topBarActions()
            TopBarStatusChip(state)
        },
        nowPill = {
            OneUINowPill(
                text = if (state.runtime.serviceActive) "Cover Active" else "Cover Dormant",
                icon = Icons.Filled.FiberManualRecord,
                iconTint = if (state.runtime.serviceActive) Color(0xFF6EE7C0) else Color(0xFFFFB870),
                onClick = { startBlocked = !onToggleService() }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(OneUITheme.dimensions.cardSpacing)
        ) {
            item {
                OneUICardGroup(header = "Status Overview") {
                    DashboardHeroCard(state)
                }
            }

            if (state.permissions.missing.isNotEmpty()) {
                item {
                    FeatureDegradedNotice(
                        "Cover launcher unavailable until ${state.permissions.missing.joinToString()} are enabled. " +
                            "You can still use the other tabs.",
                        onPermissions
                    )
                }
            }

            item {
                OneUICardGroup(header = "Quick Controls") {
                    QuickActionsRow(
                        running = state.runtime.serviceActive,
                        onToggleService = { startBlocked = !onToggleService() },
                        onPermissions = onPermissions,
                        onPreview = { previewVisible = true }
                    )
                }
            }

            if (startBlocked) {
                item {
                    Text(
                        "Start blocked: grant required permissions first.",
                        color = MaterialTheme.colorScheme.error,
                        style = OneUITheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            if (BuildConfig.DEBUG) {
                item {
                    OneUICardGroup(header = "Recent Activity") {
                        RecentActivityCard(state.runtime.events)
                    }
                }
            }

            item {
                OneUICardGroup(header = "Tips & Guidance") {
                    TipsCarousel()
                }
            }
        }
    }

    if (previewVisible) {
        AlertDialog(
            onDismissRequest = { previewVisible = false },
            shape = OneUITheme.shapes.card,
            containerColor = OneUITheme.colors.cardSurface,
            title = {
                Text(
                    "Cover Surface Preview",
                    style = OneUITheme.typography.titleLarge,
                    color = OneUITheme.colors.textPrimary
                )
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(OneUITheme.colors.cardSurfaceVariant, shape = OneUITheme.shapes.card)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Preview only - not the live cover display",
                        style = OneUITheme.typography.labelMedium,
                        color = OneUITheme.colors.textSecondary
                    )
                    Text(
                        "Cover Screen OS",
                        style = OneUITheme.typography.displayLarge,
                        color = OneUITheme.colors.textPrimary
                    )
                    Text(
                        "Dock  |  Apps  |  Notifications",
                        style = OneUITheme.typography.bodyLarge,
                        color = OneUITheme.colors.textSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { previewVisible = false }) {
                    Text("Close", color = OneUITheme.colors.accent)
                }
            }
        )
    }
}

@Preview(name = "Light Mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "OLED Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun DashboardScreenPreview() {
    OneUITheme {
        DashboardScreen(
            state = DashboardState(
                permissions = DashboardPermissions(
                    notifications = true,
                    launcherAccessibility = true,
                    notificationListener = true,
                    batteryExemption = true,
                    inputAccessibility = true
                ),
                runtime = com.tyejaedon.coverscreenos.services.overlay.RuntimeSnapshot(
                    serviceActive = true,
                    launcherHostActive = true,
                    overlayActive = true,
                    events = emptyList()
                )
            ),
            onPermissions = {},
            onToggleService = { true }
        )
    }
}
