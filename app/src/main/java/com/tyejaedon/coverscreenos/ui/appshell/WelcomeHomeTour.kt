package com.tyejaedon.coverscreenos.ui.appshell

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.R

private data class TourStep(@StringRes val title: Int, @StringRes val description: Int)

private val steps = listOf(
    TourStep(R.string.home_dashboard, R.string.home_tour_dashboard),
    TourStep(R.string.home_customize, R.string.home_tour_customize),
    TourStep(R.string.home_permissions, R.string.home_tour_permissions),
    TourStep(R.string.home_about, R.string.home_tour_about)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WelcomeHomeTour(onDismiss: () -> Unit) {
    var selectedStep by rememberSaveable { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(R.string.home_tour_welcome),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            ScrollableTabRow(selectedTabIndex = selectedStep, edgePadding = 16.dp) {
                steps.forEachIndexed { index, step ->
                    Tab(
                        selected = selectedStep == index,
                        onClick = { selectedStep = index },
                        text = { Text(stringResource(step.title)) }
                    )
                }
            }
            Text(
                text = stringResource(steps[selectedStep].description),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_tour_skip)) }
                Button(onClick = {
                    if (selectedStep == steps.lastIndex) onDismiss() else selectedStep++
                }) {
                    Text(stringResource(if (selectedStep == steps.lastIndex) R.string.home_tour_done else R.string.home_tour_next))
                }
            }
        }
    }
}
