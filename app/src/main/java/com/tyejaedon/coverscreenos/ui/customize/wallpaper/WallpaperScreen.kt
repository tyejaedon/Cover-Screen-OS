package com.tyejaedon.coverscreenos.ui.customize.wallpaper

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_DIM_AMOUNT
import java.io.IOException

@Composable
internal fun WallpaperScreen(
    store: LauncherSettingsStore,
    settings: LauncherSettings,
    onAction: (String?, suspend () -> Unit) -> Unit
) {
    var importing by remember { mutableStateOf(false) }
    var scale by remember(settings.wallpaperScaleMode) { mutableStateOf(settings.wallpaperScaleMode) }
    var dim by remember(settings.wallpaperDimAmount) {
        mutableFloatStateOf(
            settings.wallpaperDimAmount.takeIf { it.isFinite() }
                ?.coerceIn(MIN_WALLPAPER_DIM_AMOUNT, MAX_WALLPAPER_DIM_AMOUNT)
                ?: DEFAULT_WALLPAPER_DIM_AMOUNT
        )
    }
    var blur by remember(settings.wallpaperBlurRadiusDp) {
        mutableFloatStateOf(
            settings.wallpaperBlurRadiusDp.takeIf { it.isFinite() }
                ?.coerceIn(MIN_WALLPAPER_BLUR_RADIUS_DP, MAX_WALLPAPER_BLUR_RADIUS_DP)
                ?: DEFAULT_WALLPAPER_BLUR_RADIUS_DP
        )
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !importing) {
            importing = true
            onAction("Wallpaper updated") {
                try {
                    if (!store.importWallpaperFromUri(uri)) {
                        throw IOException("Selected image could not be imported")
                    }
                } finally {
                    importing = false
                }
            }
        }
    }

    WallpaperCustomizationCard(
        wallpaperUri = settings.wallpaperUri,
        wallpaperScaleMode = scale,
        dimAmount = dim,
        blurRadiusDp = blur,
        isWallpaperImportInProgress = importing,
        onChooseWallpaper = {
            runCatching { picker.launch("image/*") }
                .onFailure { error ->
                    Log.w("WallpaperScreen", "Unable to open image picker", error)
                    onAction(null) { throw IOException("Unable to open image picker", error) }
                }
        },
        onClearWallpaper = { onAction("Using pure black") { store.clearWallpaper() } },
        onScaleModeSelected = { value ->
            scale = value
            onAction(null) { store.setWallpaperScaleMode(value) }
        },
        onDimAmountPreviewChanged = { dim = it },
        onDimAmountCommit = { onAction(null) { store.setWallpaperDimAmount(dim) } },
        onBlurRadiusPreviewChanged = { blur = it },
        onBlurRadiusCommit = { onAction(null) { store.setWallpaperBlurRadiusDp(blur) } }
    )
    if (settings.wallpaperUri == null) {
        Text("No image selected; the live preview uses the pure black cover background.")
    }
}
