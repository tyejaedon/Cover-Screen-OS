package com.tyejaedon.coverscreenos.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun QuickActionsRow(
    running: Boolean,
    onToggleService: () -> Unit,
    onPermissions: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction(if (running) "Stop service" else "Start service",
            if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, onToggleService, Modifier.weight(1f))
        QuickAction("Grant permissions", Icons.Filled.VerifiedUser, onPermissions, Modifier.weight(1f))
        QuickAction("Preview overlay", Icons.Filled.Visibility, onPreview, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}
