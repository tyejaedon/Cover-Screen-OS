package com.tyejaedon.coverscreenos.ui.controllers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    refreshIntervalMs: Long = 3_000L
) {
    val context = LocalContext.current
    var isServiceRunning by remember {
        mutableStateOf(ForegroundServiceHelper.isForegroundServiceRunning(context))
    }
    var lastCheckedAt by remember { mutableStateOf(currentStatusTimestamp()) }

    fun refreshServiceState() {
        isServiceRunning = ForegroundServiceHelper.isForegroundServiceRunning(context)
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
                Text(if (isServiceRunning) "Foreground service is running" else "Foreground service is stopped")
            }

            Text("Last checked: $lastCheckedAt")
            Text("Auto-refresh: every ${refreshIntervalMs / 1000}s")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !isServiceRunning,
                    onClick = {
                        ForegroundServiceHelper.startForegroundService(context)
                        refreshServiceState()
                    }
                ) {
                    Text("Start")
                }

                OutlinedButton(
                    enabled = isServiceRunning,
                    onClick = {
                        ForegroundServiceHelper.stopForegroundService(context)
                        refreshServiceState()
                    }
                ) {
                    Text("Stop")
                }

                OutlinedButton(onClick = { refreshServiceState() }) {
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

