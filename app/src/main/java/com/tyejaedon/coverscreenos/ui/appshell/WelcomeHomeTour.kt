package com.tyejaedon.coverscreenos.ui.appshell

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.R

/**
 * Describes a single page in the first-run [WelcomeHomeTour] sheet, pairing a tab title with
 * explanatory body copy string resources.
 */
private data class TourStep(@StringRes val title: Int, @StringRes val description: Int)

// Ordered tour cards matching the four main tabs inside AppShell (Dashboard, Customize, Permissions, About).
private val steps = listOf(
    TourStep(R.string.home_dashboard, R.string.home_tour_dashboard),
    TourStep(R.string.home_customize, R.string.home_tour_customize),
    TourStep(R.string.home_permissions, R.string.home_tour_permissions),
    TourStep(R.string.home_about, R.string.home_tour_about)
)

/**
 * First-run Material3 [ModalBottomSheet] introducing the four main areas of Cover Screen OS.
 *
 * Uses a [PrimaryScrollableTabRow] so users can jump between tabs directly or advance sequentially,
 * and compacts padding/typography when `screenHeightDp <= 480` (for example in landscape or
 * half-folded postures).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WelcomeHomeTour(onDismiss: () -> Unit) {
    // Index (0..3) of the tour page currently shown; survives rotation or fold-state changes.
    var selectedStep by rememberSaveable { mutableIntStateOf(0) }
    // True on short displays (<= 480dp tall) so text and spacing shrink to stay comfortable.
    val isCompactHeight = LocalConfiguration.current.screenHeightDp <= 480
    // Shared left/right inset used across the title, tab strip, description, and action row.
    val horizontalPad = if (isCompactHeight) 16.dp else 24.dp
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Allow scrolling inside the sheet on compact-height displays so buttons stay reachable.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 10.dp else 16.dp)
        ) {
            Text(
                text = stringResource(R.string.home_tour_welcome),
                style = if (isCompactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = horizontalPad)
            )
            PrimaryScrollableTabRow(selectedTabIndex = selectedStep, edgePadding = horizontalPad) {
                steps.forEachIndexed { index, step ->
                    Tab(
                        selected = selectedStep == index,
                        onClick = { selectedStep = index },
                        text = { Text(stringResource(step.title), maxLines = 1) }
                    )
                }
            }
            Text(
                text = stringResource(steps[selectedStep].description),
                style = if (isCompactHeight) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = horizontalPad)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPad, vertical = if (isCompactHeight) 8.dp else 16.dp),
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
