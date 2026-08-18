package com.tyejaedon.coverscreenos.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.datastore.SearchInputMode
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusMedium
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding

private data class SearchInputModeUiOption(
    val mode: SearchInputMode,
    val label: String,
    val summary: String,
    val icon: ImageVector
)

@Composable
internal fun InputCustomizationCard(
    searchInputMode: SearchInputMode,
    onSearchInputModeSelected: (SearchInputMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        SearchInputModeUiOption(
            mode = SearchInputMode.T9,
            label = "T9 keypad",
            summary = "Best for narrow cover screens with large tap targets.",
            icon = Icons.Filled.Dialpad
        ),
        SearchInputModeUiOption(
            mode = SearchInputMode.SYSTEM_IME,
            label = "System keyboard",
            summary = "Use Gboard/Samsung Keyboard with IME resize handling.",
            icon = Icons.Filled.Keyboard
        )
    )

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
            Text("Input", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose how app drawer search accepts text on the cover display.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    val selected = searchInputMode == option.mode
                    val buttonBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    if (selected) {
                        Button(
                            onClick = { onSearchInputModeSelected(option.mode) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .coverMinimumTouchTarget(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = option.icon, contentDescription = null)
                                Column {
                                    Text(option.label)
                                    Text(option.summary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSearchInputModeSelected(option.mode) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .coverMinimumTouchTarget(),
                            shape = RoundedCornerShape(12.dp),
                            border = buttonBorder
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = option.icon, contentDescription = null)
                                Column {
                                    Text(option.label)
                                    Text(option.summary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Text(
                "Tip: voice search requires microphone permission and can be used in either mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

