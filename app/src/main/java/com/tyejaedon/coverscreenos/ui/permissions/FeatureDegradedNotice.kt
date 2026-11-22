package com.tyejaedon.coverscreenos.ui.permissions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUITheme

/**
 * OneUI-styled banner surfaced when missing required permissions limit cover-screen functionality.
 *
 * Displays [message], a touch-target-safe `"Review permissions"` action invoking [onPermissions],
 * and an optional close button when [onDismiss] is supplied.
 */
@Composable
fun FeatureDegradedNotice(
    message: String,
    onPermissions: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = OneUITheme.shapes.card,
        colors = CardDefaults.cardColors(containerColor = OneUITheme.colors.cardSurface),
        border = BorderStroke(1.dp, OneUITheme.colors.hairlineBorder)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Limited features",
                    style = MaterialTheme.typography.titleSmall,
                    color = OneUITheme.colors.textPrimary
                )
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss notice",
                            tint = OneUITheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = OneUITheme.colors.textSecondary
            )
            TextButton(onClick = onPermissions, modifier = Modifier.coverMinimumTouchTarget()) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" Review permissions")
            }
        }
    }
}
