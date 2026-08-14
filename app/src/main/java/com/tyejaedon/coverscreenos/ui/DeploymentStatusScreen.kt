package com.tyejaedon.coverscreenos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.helpers.AppPermissionHelper
import com.tyejaedon.coverscreenos.ui.controllers.ForegroundServiceController

@Composable
fun DeploymentStatusScreen(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
            .padding(horizontal = OverlayLayoutSpec.horizontalMargin, vertical = OverlayLayoutSpec.verticalPadding)
    ) {
        val overlayWidth = OverlayLayoutSpec.overlayWidth(maxWidth)

        Box(modifier = Modifier.fillMaxSize()) {
            LauncherRunningOverlay(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(overlayWidth)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = OverlayLayoutSpec.contentTopOffset),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val overlayReady = AppPermissionHelper.canDrawOverlays(context)
                val accessibilityReady = AppPermissionHelper.isAccessibilityServiceEnabled(context)

                Text("Cover Screen OS Deployment", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Overlay runtime is active. Use these controls to manage service behavior and validate readiness.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PermissionStatusRow("Notification permission", true)
                        PermissionStatusRow("Appear on top", overlayReady)
                        PermissionStatusRow("Accessibility service", accessibilityReady)
                    }
                }

                ForegroundServiceController()
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(
            text = if (granted) "Ready" else "Missing",
            color = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                Color(0xFFCF6679)
            },
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun LauncherRunningOverlay(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }

    // Soft fade-in so the overlay feels less abrupt when permissions are granted.
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 500))
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
            )
        ) {
            Text(
                text = "Cover OS launcher is running.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

