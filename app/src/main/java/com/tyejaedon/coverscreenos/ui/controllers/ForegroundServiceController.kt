package com.tyejaedon.coverscreenos.ui.controllers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ForegroundServiceController(
    modifier: Modifier = Modifier,
    refreshIntervalMs: Long = 6_000L
) {
    val context = LocalContext.current
    var isServiceRunning by remember {
        mutableStateOf(ForegroundServiceHelper.isForegroundServiceRunning())
    }
    var lastCheckedAt by remember { mutableStateOf(currentStatusTimestamp()) }
    var lastActionFeedback by remember { mutableStateOf<String?>(null) }

    fun refreshServiceState() {
        isServiceRunning = ForegroundServiceHelper.isForegroundServiceRunning()
        lastCheckedAt = currentStatusTimestamp()
    }

    // Keeps UI state in sync even when service state changes outside this screen.
    LaunchedEffect(refreshIntervalMs) {
        while (true) {
            refreshServiceState()
            delay(refreshIntervalMs.milliseconds)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Service Controls", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Start or stop the launcher runtime. Status updates every ${refreshIntervalMs / 1000}s.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (isServiceRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isServiceRunning) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = if (isServiceRunning) "Running" else "Stopped",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = if (isServiceRunning) "Launcher runtime is active" else "Launcher runtime is inactive",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text("Last checked: $lastCheckedAt")

            lastActionFeedback?.let { feedback ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = feedback,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (isServiceRunning) {
                            ForegroundServiceHelper.stopForegroundService(context)
                            lastActionFeedback = "Stopped service. Result: overlay runtime is now inactive."
                        } else {
                            val started = ForegroundServiceHelper.startForegroundService(context)
                            lastActionFeedback = if (started) {
                                "Started service. Result: overlay runtime should become active shortly."
                            } else {
                                "Start blocked. Grant notification, overlay, and accessibility permissions first."
                            }
                        }
                        refreshServiceState()
                    }
                ) {
                    Text(if (isServiceRunning) "Stop launcher" else "Start launcher")
                }

                OutlinedButton(
                    onClick = {
                        refreshServiceState()
                        lastActionFeedback = "Status refreshed."
                    }
                ) {
                    Text("Refresh")
                }
            }
        }
    }
}

private fun currentStatusTimestamp(): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
    return LocalTime.now().format(formatter)
}

