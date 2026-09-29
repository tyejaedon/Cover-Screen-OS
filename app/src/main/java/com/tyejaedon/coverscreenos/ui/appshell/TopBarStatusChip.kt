package com.tyejaedon.coverscreenos.ui.appshell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.ui.dashboard.DashboardState
import com.tyejaedon.coverscreenos.ui.dashboard.DashboardStatus
import com.tyejaedon.coverscreenos.ui.dashboard.DashboardStatusDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarStatusChip(state: DashboardState) {
    var showDetails by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { showDetails = true },
        modifier = Modifier.semantics { contentDescription = "Runtime status: ${state.title}" },
        label = {
            Text(
                when (state.status) {
                    DashboardStatus.ACTIVE -> "Active"
                    DashboardStatus.SETUP_NEEDED -> "Setup"
                    DashboardStatus.STOPPED -> "Stopped"
                },
                style = MaterialTheme.typography.labelSmall
            )
        },
        leadingIcon = {
            Icon(
                when (state.status) {
                    DashboardStatus.ACTIVE -> Icons.Filled.CheckCircle
                    DashboardStatus.SETUP_NEEDED -> Icons.Filled.PendingActions
                    DashboardStatus.STOPPED -> Icons.Filled.Error
                },
                contentDescription = null,
                tint = when (state.status) {
                    DashboardStatus.ACTIVE -> MaterialTheme.colorScheme.secondary
                    DashboardStatus.SETUP_NEEDED -> MaterialTheme.colorScheme.tertiary
                    DashboardStatus.STOPPED -> MaterialTheme.colorScheme.error
                }
            )
        }
    )
    if (showDetails) {
        ModalBottomSheet(onDismissRequest = { showDetails = false }) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(state.title, style = MaterialTheme.typography.titleLarge)
                DashboardStatusDetails(state)
            }
        }
    }
}
