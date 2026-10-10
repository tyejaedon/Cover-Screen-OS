package com.tyejaedon.coverscreenos.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.services.overlay.OverlayEvent

@Composable
fun RecentActivityCard(events: List<OverlayEvent>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Card(onClick = { expanded = !expanded }, modifier = modifier.fillMaxWidth()) {
        Column {
            Text(
                "Recent activity ${if (expanded) "-" else "+"}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            if (expanded) {
                if (events.isEmpty()) Text("No overlay events recorded yet.", Modifier.padding(16.dp))
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(events) { event ->
                        Text(event.description, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
