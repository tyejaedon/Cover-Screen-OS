package com.tyejaedon.coverscreenos.ui.theme.oneui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme

/**
 * Preview gallery for the One UI 8 design system: every depth tier against the three
 * wallpaper-luminance worst/typical cases called out in the plan's testing strategy
 * (solid black, solid white, mid-tone photo-like gradient). No live device required to sanity
 * check legibility/contrast while iterating on tokens.
 */
private val PreviewWallpaperDark = Color(0xFF000000)
private val PreviewWallpaperBright = Color(0xFFF5F5F5)
private val PreviewWallpaperMid = Color(0xFF5B6B8C)

@Composable
private fun OneUiPreviewGallery(backgroundColor: Color) {
    CoverOSTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("oneUiPanel", style = CoverOneUIType.sectionTitle, color = Color.White)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .size(width = 220.dp, height = 64.dp)
                        .oneUiPanel()
                )

                Text("oneUiElevatedPanel", style = CoverOneUIType.sectionTitle, color = Color.White)
                Box(
                    modifier = Modifier
                        .size(width = 220.dp, height = 64.dp)
                        .oneUiElevatedPanel()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayPageAccent.entries.forEach { accent ->
                        CompositionLocalProvider(LocalOverlayAccent provides accent) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp, 32.dp)
                                    .oneUiChip(selected = true),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(accent.name, style = CoverOneUIType.chipLabel, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "One UI tokens — dark wallpaper", widthDp = 360, heightDp = 420)
@Composable
private fun OneUiPreviewDarkWallpaper() {
    OneUiPreviewGallery(PreviewWallpaperDark)
}

@Preview(name = "One UI tokens — bright wallpaper", widthDp = 360, heightDp = 420)
@Composable
private fun OneUiPreviewBrightWallpaper() {
    OneUiPreviewGallery(PreviewWallpaperBright)
}

@Preview(name = "One UI tokens — mid-tone wallpaper", widthDp = 360, heightDp = 420)
@Composable
private fun OneUiPreviewMidWallpaper() {
    OneUiPreviewGallery(PreviewWallpaperMid)
}
