package com.tyejaedon.coverscreenos.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget

@Composable
fun FeatureDegradedNotice(message: String, onPermissions: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Limited features", style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onPermissions, modifier = Modifier.coverMinimumTouchTarget()) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = null)
                Text(" Review permissions")
            }
        }
    }
}
