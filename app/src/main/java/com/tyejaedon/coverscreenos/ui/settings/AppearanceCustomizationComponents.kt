package com.tyejaedon.coverscreenos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.datastore.ThemePreference
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusMedium
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding

@Composable
internal fun AppearanceCustomizationCard(
    themePreference: ThemePreference,
    onThemePreferenceSelected: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(CoverOSCornerRadiusMedium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .coverScreenPadding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Text(
                "Use system theme or force light/dark for better readability in your environment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemePreference.entries.forEach { option ->
                    val label = when (option) {
                        ThemePreference.SYSTEM -> "System"
                        ThemePreference.LIGHT -> "Light"
                        ThemePreference.DARK -> "Dark"
                    }
                    val selected = themePreference == option
                    if (selected) {
                        Button(
                            onClick = { onThemePreferenceSelected(option) },
                            modifier = Modifier
                                .weight(1f)
                                .coverMinimumTouchTarget()
                        ) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onThemePreferenceSelected(option) },
                            modifier = Modifier
                                .weight(1f)
                                .coverMinimumTouchTarget()
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}

