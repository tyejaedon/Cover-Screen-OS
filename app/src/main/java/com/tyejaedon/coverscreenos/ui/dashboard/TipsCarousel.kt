package com.tyejaedon.coverscreenos.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val tips = listOf(
    "Set your favorite apps in the dock from Customize.",
    "Choose a wallpaper that stays readable on the cover display.",
    "Enable keyboard input accessibility for cover-screen typing.",
    "Check notification access to see alerts on the cover screen."
)

@Composable
fun TipsCarousel(modifier: Modifier = Modifier) {
    var dismissed by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val available = tips.indices.filter { dismissed and (1 shl it) == 0 }
    if (available.isEmpty()) return
    val index = available.firstOrNull { it >= selected } ?: available.first()
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tip ${available.indexOf(index) + 1} of ${available.size}",
                style = MaterialTheme.typography.titleMedium)
            Text(tips[index], style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { selected = available.firstOrNull { it > index } ?: available.first() }) {
                    Text("Next tip")
                }
                TextButton(onClick = { dismissed = dismissed or (1 shl index); selected = index + 1 }) {
                    Text("Dismiss tip")
                }
            }
        }
    }
}
