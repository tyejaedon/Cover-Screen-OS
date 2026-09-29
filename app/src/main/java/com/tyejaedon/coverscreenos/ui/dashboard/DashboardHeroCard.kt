package com.tyejaedon.coverscreenos.ui.dashboard

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun DashboardHeroCard(state: DashboardState, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth().semantics {
            stateDescription = "${state.title}, ${if (expanded) "expanded" else "collapsed"}"
        }
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = when (state.status) {
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
                Column(Modifier.weight(1f)) {
                    Text(state.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${state.permissions.grantedCount} of ${state.permissions.totalCount} required permissions ready",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Text(
                when {
                    state.permissions.missing.isNotEmpty() -> "Complete setup to run the cover launcher."
                    !state.runtime.serviceActive -> "Start the service to run the cover launcher."
                    !state.runtime.launcherHostActive -> "Waiting for the launcher accessibility host."
                    !state.runtime.overlayActive -> "Runtime ready; overlay is not currently visible."
                    else -> "Launcher runtime and accessibility host are connected."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (expanded) DashboardStatusDetails(state)
            Text(
                if (expanded) "Hide details" else "Show details",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun DashboardStatusDetails(state: DashboardState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Missing: ${state.permissions.missing.joinToString().ifEmpty { "None" }}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Service: ${if (state.runtime.serviceActive) "Running" else "Stopped"}; " +
                "Launcher host: ${if (state.runtime.launcherHostActive) "Connected" else "Disconnected"}; " +
                "Overlay: ${if (state.runtime.overlayActive) "Visible" else "Not visible"}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Uptime: ${state.runtime.startedElapsedRealtimeMs?.let {
                "${((SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) / 1000L)} seconds at last update"
            } ?: "Not running"}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Last event: ${state.runtime.events.firstOrNull()?.description ?: "No runtime events yet"}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
