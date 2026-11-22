package com.tyejaedon.coverscreenos.ui.customize.wallpaper

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.WallpaperScaleMode
import java.io.IOException

/**
 * Wallpaper customization category screen backed by [LauncherSettingsStore].
 *
 * Launches a Storage Access Framework document picker ([ActivityResultContracts.OpenDocument]) for
 * images, GIFs, and videos, imports the chosen URI into app-managed storage via
 * `store.importWallpaperFromUri`, and coordinates draft preview state with DataStore commits for
 * crop, zoom, rotation, dim, and blur controls.
 */
@Composable
internal fun WallpaperScreen(
    store: LauncherSettingsStore,
    settings: LauncherSettings,
    onAction: (String?, suspend () -> Unit) -> Unit
) {
    var importing by remember { mutableStateOf(false) }
    // Local draft states mirror persisted settings so dragging/sliding stays responsive without disk churn.
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
    var cropX by remember(settings.wallpaperCropX) { mutableFloatStateOf(settings.wallpaperCropX) }
    var cropY by remember(settings.wallpaperCropY) { mutableFloatStateOf(settings.wallpaperCropY) }
    var zoom by remember(settings.wallpaperZoom) { mutableFloatStateOf(settings.wallpaperZoom) }
    var rotation by remember(settings.wallpaperRotation) { mutableIntStateOf(settings.wallpaperRotation) }
    var showCropDialog by rememberSaveable { mutableStateOf(false) }
    // OpenDocument lets the user select images, animated GIFs, or video files via the system file picker.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importing) {
            importing = true
            onAction("Wallpaper updated") {
                try {
                    store.importWallpaperFromUri(uri)
                    // Open the interactive crop editor right after a newly imported wallpaper succeeds.
                    showCropDialog = true
                } finally {
                    importing = false
                }
            }
        }
    }

    WallpaperCustomizationCard(
        wallpaperUri = settings.wallpaperUri,
        wallpaperScaleMode = scale,
        cropX = cropX,
        cropY = cropY,
        zoom = zoom,
        rotation = rotation,
        dimAmount = dim,
        blurRadiusDp = blur,
        isWallpaperImportInProgress = importing,
        showCropDialog = showCropDialog,
        onOpenCropDialog = { showCropDialog = true },
        onDismissCropDialog = { showCropDialog = false },
        onApplyCrop = { nextScale, nextCropX, nextCropY, nextZoom, nextRotation ->
            showCropDialog = false
            scale = nextScale
            cropX = nextCropX
            cropY = nextCropY
            zoom = nextZoom
            rotation = nextRotation
            onAction("Wallpaper crop saved") {
                store.setWallpaperScaleMode(nextScale)
                store.setWallpaperCrop(nextCropX, nextCropY, nextZoom, nextRotation)
            }
        },
        onChooseWallpaper = {
            runCatching { picker.launch(arrayOf("image/*", "video/*")) }
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
        onCropPreviewChanged = { x, y ->
            cropX = x
            cropY = y
        },
        // Commit crop/zoom/dim/blur to DataStore only when the user finishes dragging or sliding.
        onCropCommit = { x, y ->
            cropX = x
            cropY = y
            onAction(null) { store.setWallpaperCrop(x, y, zoom, rotation) }
        },
        onZoomPreviewChanged = { zoom = it },
        onZoomCommit = { onAction(null) { store.setWallpaperCrop(cropX, cropY, zoom, rotation) } },
        onRotate = {
            rotation = (rotation + 90) % 360
            onAction(null) { store.setWallpaperRotation(rotation) }
        },
        onResetCrop = {
            cropX = 0f
            cropY = 0f
            zoom = 1f
            rotation = 0
            scale = WallpaperScaleMode.CROP
            onAction(null) {
                store.setWallpaperScaleMode(WallpaperScaleMode.CROP)
                store.setWallpaperCrop(0f, 0f, 1f, 0)
            }
        },
        onDimAmountPreviewChanged = { dim = it },
        onDimAmountCommit = { onAction(null) { store.setWallpaperDimAmount(dim) } },
        onBlurRadiusPreviewChanged = { blur = it },
        onBlurRadiusCommit = { onAction(null) { store.setWallpaperBlurRadiusDp(blur) } }
    )
    if (settings.wallpaperUri == null) {
        Text("No wallpaper selected; the live preview uses the pure black cover background.")
    }
}
