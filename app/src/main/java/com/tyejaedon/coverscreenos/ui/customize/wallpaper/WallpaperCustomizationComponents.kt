package com.tyejaedon.coverscreenos.ui.customize.wallpaper

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_CROP_OFFSET
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_ZOOM
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_CROP_OFFSET
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_ZOOM
import com.tyejaedon.coverscreenos.datastore.WallpaperScaleMode
import com.tyejaedon.coverscreenos.ui.WallpaperFrame
import com.tyejaedon.coverscreenos.ui.WallpaperMediaContent
import com.tyejaedon.coverscreenos.ui.decodeWallpaperPreviewFrame
import com.tyejaedon.coverscreenos.helpers.CoverDisplayHelper
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusMedium
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusSmall
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Result wrapper for asynchronous wallpaper preview decoding. */
private data class WallpaperPreviewLoad(
    val frame: WallpaperFrame? = null,
    val failureMessage: String? = null
)

/**
 * Card exposing wallpaper selection, framing mode, rotation, zoom, dim, blur, and crop controls.
 *
 * Continuous controls (zoom, dim, blur, and inline pan) split live preview callbacks from commit
 * callbacks so the preview updates smoothly at frame rate before persisting to DataStore.
 */
@Composable
internal fun WallpaperCustomizationCard(
    wallpaperUri: String?,
    wallpaperScaleMode: WallpaperScaleMode,
    cropX: Float,
    cropY: Float,
    zoom: Float,
    rotation: Int,
    dimAmount: Float,
    blurRadiusDp: Float,
    isWallpaperImportInProgress: Boolean,
    showCropDialog: Boolean,
    onOpenCropDialog: () -> Unit,
    onDismissCropDialog: () -> Unit,
    onApplyCrop: (WallpaperScaleMode, Float, Float, Float, Int) -> Unit,
    onChooseWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onScaleModeSelected: (WallpaperScaleMode) -> Unit,
    onCropPreviewChanged: (Float, Float) -> Unit,
    onCropCommit: (Float, Float) -> Unit,
    onZoomPreviewChanged: (Float) -> Unit,
    onZoomCommit: () -> Unit,
    onRotate: () -> Unit,
    onResetCrop: () -> Unit,
    onDimAmountPreviewChanged: (Float) -> Unit,
    onDimAmountCommit: () -> Unit,
    onBlurRadiusPreviewChanged: (Float) -> Unit,
    onBlurRadiusCommit: () -> Unit,
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
            Text("Wallpaper customization", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose an image, animated GIF, or video. The preview matches the cover wallpaper.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            WallpaperPreviewCard(
                wallpaperUri = wallpaperUri,
                wallpaperScaleMode = wallpaperScaleMode,
                cropX = cropX,
                cropY = cropY,
                zoom = zoom,
                rotation = rotation,
                dimAmount = dimAmount,
                blurRadiusDp = blurRadiusDp,
                showCropDialog = showCropDialog,
                onOpenCropDialog = onOpenCropDialog,
                onDismissCropDialog = onDismissCropDialog,
                onApplyCrop = onApplyCrop,
                onCropPreviewChanged = onCropPreviewChanged,
                onCropCommit = onCropCommit,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = !isWallpaperImportInProgress,
                    onClick = onChooseWallpaper,
                    modifier = Modifier.coverMinimumTouchTarget()
                ) {
                    Text(
                        when {
                            isWallpaperImportInProgress -> "Importing..."
                            wallpaperUri == null -> "Choose wallpaper"
                            else -> "Change wallpaper"
                        }
                    )
                }
                OutlinedButton(
                    enabled = wallpaperUri != null && !isWallpaperImportInProgress,
                    onClick = onOpenCropDialog,
                    modifier = Modifier.coverMinimumTouchTarget()
                ) {
                    Icon(Icons.Filled.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Zoom & Crop")
                }
                OutlinedButton(
                    enabled = wallpaperUri != null && !isWallpaperImportInProgress,
                    onClick = onClearWallpaper,
                    modifier = Modifier.coverMinimumTouchTarget()
                ) {
                    Text("Use pure black")
                }
            }
            Text("Framing", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(WallpaperScaleMode.CROP, WallpaperScaleMode.FIT).forEach { mode ->
                    if (wallpaperScaleMode == mode) {
                        Button(onClick = { onScaleModeSelected(mode) }) {
                            Text(if (mode == WallpaperScaleMode.CROP) "Crop" else "Fit")
                        }
                    } else {
                        OutlinedButton(onClick = { onScaleModeSelected(mode) }) {
                            Text(if (mode == WallpaperScaleMode.CROP) "Crop" else "Fit")
                        }
                    }
                }
                OutlinedButton(onClick = onRotate, modifier = Modifier.coverMinimumTouchTarget()) {
                    Text("Rotate 90°")
                }
                OutlinedButton(onClick = onResetCrop, modifier = Modifier.coverMinimumTouchTarget()) {
                    Text("Reset")
                }
            }
            // Zoom factor rounded to one decimal place for the label above the zoom slider.
            val displayZoom = (zoom * 10f).roundToInt() / 10f
            Text("Zoom: ${displayZoom}x", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = zoom,
                onValueChange = onZoomPreviewChanged,
                onValueChangeFinished = onZoomCommit,
                valueRange = MIN_WALLPAPER_ZOOM..MAX_WALLPAPER_ZOOM
            )
            Text("Dim: ${(dimAmount * 100f).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = dimAmount,
                onValueChange = onDimAmountPreviewChanged,
                onValueChangeFinished = onDimAmountCommit,
                valueRange = MIN_WALLPAPER_DIM_AMOUNT..MAX_WALLPAPER_DIM_AMOUNT
            )
            Text("Blur: ${blurRadiusDp.roundToInt()} dp", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = blurRadiusDp,
                onValueChange = onBlurRadiusPreviewChanged,
                onValueChangeFinished = onBlurRadiusCommit,
                valueRange = MIN_WALLPAPER_BLUR_RADIUS_DP..MAX_WALLPAPER_BLUR_RADIUS_DP
            )
            Text(
                "Limits: 64 MiB; images up to 8192×8192 and 40 MP; GIF/video up to 2048×2048 and 4.19 MP. Animation: 10 seconds, 30 fps, 300 frames. Videos: MP4 or WebM with H.264/AVC, HEVC, or VP9.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Animated wallpapers pause when hidden or the screen is off. Battery saver shows a still poster.")
        }
    }
}

/**
 * Live cover-screen aspect-ratio wallpaper preview card with inline long-press pan and tap-to-crop.
 *
 * Resolves the hardware cover display aspect ratio via [CoverDisplayHelper], decodes a scaled
 * [WallpaperFrame] on [Dispatchers.IO] via [decodeWallpaperPreviewFrame], overlays sample lock/cover
 * clock typography, and conditionally launches [WallpaperCropDialog] when [showCropDialog] is true.
 */
@Composable
private fun WallpaperPreviewCard(
    wallpaperUri: String?,
    wallpaperScaleMode: WallpaperScaleMode,
    cropX: Float,
    cropY: Float,
    zoom: Float,
    rotation: Int,
    dimAmount: Float,
    blurRadiusDp: Float,
    showCropDialog: Boolean,
    onOpenCropDialog: () -> Unit,
    onDismissCropDialog: () -> Unit,
    onApplyCrop: (WallpaperScaleMode, Float, Float, Float, Int) -> Unit,
    onCropPreviewChanged: (Float, Float) -> Unit,
    onCropCommit: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Window width in pixels used as a bounded target size when decoding preview thumbnails.
    val fallbackWidth = LocalWindowInfo.current.containerSize.width.coerceAtLeast(1)
    // Derive the preview frame aspect ratio from the secondary cover display's physical mode.
    val coverAspectRatio = remember(context) {
        val display = CoverDisplayHelper(context).getCoverDisplay() ?: context.display
        val mode = display.mode
        if (mode.physicalWidth > 0 && mode.physicalHeight > 0)
            mode.physicalWidth.toFloat() / mode.physicalHeight
        else 9f / 19f
    }
    // Pixel width and proportional height used to decode a right-sized preview bitmap off the main thread.
    val availableWidth = fallbackWidth
    val previewHeight = (availableWidth / coverAspectRatio).toInt().coerceAtLeast(1)
    // Cleaned URI string and parsed Android Uri (null when no wallpaper is chosen / pure black mode).
    val normalizedUri = wallpaperUri?.trim().takeUnless { it.isNullOrEmpty() }
    val uri = remember(normalizedUri) { normalizedUri?.toUri() }
    // Decode a preview frame sized to cover bounds off the main thread whenever source or framing changes.
    val preview by produceState(
        initialValue = WallpaperPreviewLoad(),
        uri,
        availableWidth,
        previewHeight,
        rotation,
        wallpaperScaleMode
    ) {
        value = if (uri == null) {
            WallpaperPreviewLoad()
        } else {
            withContext(Dispatchers.IO) {
                try {
                    WallpaperPreviewLoad(
                        frame = decodeWallpaperPreviewFrame(
                            context,
                            uri,
                            availableWidth,
                            previewHeight,
                            MAX_WALLPAPER_ZOOM,
                            rotation,
                            wallpaperScaleMode
                        )
                    )
                } catch (error: CancellationException) {
                    // Always propagate coroutine cancellation cleanly when keys change or composition leaves.
                    throw error
                } catch (error: Exception) {
                    Log.w("WallpaperPreview", "Unable to decode selected wallpaper", error)
                    WallpaperPreviewLoad(failureMessage = error.message ?: "Wallpaper preview could not be loaded.")
                }
            }
        }
    }
    // Keep latest crop coordinates/callbacks fresh inside long-lived pointerInput gesture handlers.
    val latestCropX by rememberUpdatedState(cropX)
    val latestCropY by rememberUpdatedState(cropY)
    val latestCropPreviewChanged by rememberUpdatedState(onCropPreviewChanged)
    val latestCropCommit by rememberUpdatedState(onCropCommit)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(CoverOSCornerRadiusSmall),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f))
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cover preview — tap to zoom & crop, or long-press to pan", style = MaterialTheme.typography.labelLarge)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .clickable(enabled = uri != null && preview.frame != null) { onOpenCropDialog() }
                    .pointerInput(zoom) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = { latestCropCommit(latestCropX, latestCropY) },
                            onDragCancel = { latestCropCommit(latestCropX, latestCropY) }
                        ) { change, amount ->
                            change.consume()
                            latestCropPreviewChanged(
                                (latestCropX + amount.x / size.width.coerceAtLeast(1) * 2f)
                                    .coerceIn(MIN_WALLPAPER_CROP_OFFSET, MAX_WALLPAPER_CROP_OFFSET),
                                (latestCropY + amount.y / size.height.coerceAtLeast(1) * 2f)
                                    .coerceIn(MIN_WALLPAPER_CROP_OFFSET, MAX_WALLPAPER_CROP_OFFSET)
                            )
                        }
                    }
            ) {
                // Decoded frame ready for rendering inside the cover preview card (null while loading or on error).
                val previewFrame = preview.frame
                if (previewFrame != null && uri != null) {
                    WallpaperMediaContent(
                        uri = uri,
                        frame = previewFrame,
                        scaleMode = wallpaperScaleMode,
                        cropX = cropX,
                        cropY = cropY,
                        zoom = zoom,
                        rotation = rotation,
                        dimAmount = dimAmount,
                        blurRadiusDp = blurRadiusDp,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("9:41", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                            Text("Tue, Aug 16", color = Color.White.copy(alpha = 0.9f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cover wallpaper", color = Color.White.copy(alpha = 0.9f))
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Crop,
                                        contentDescription = "Zoom and crop wallpaper",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "Zoom & Crop",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        when {
                            uri == null -> "Using a pure black cover background"
                            preview.failureMessage != null ->
                                preview.failureMessage ?: "Wallpaper preview could not be loaded."
                            else -> "Loading wallpaper preview…"
                        },
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
            }
            Text("Rotation: $rotation°", style = MaterialTheme.typography.bodySmall)
        }
    }

    // Decoded frame passed to the full-screen crop modal once both URI and preview frame are ready.
    val cropFrame = preview.frame
    if (showCropDialog && uri != null && cropFrame != null) {
        WallpaperCropDialog(
            uri = uri,
            frame = cropFrame,
            coverAspectRatio = coverAspectRatio,
            initialScaleMode = wallpaperScaleMode,
            initialCropX = cropX,
            initialCropY = cropY,
            initialZoom = zoom,
            initialRotation = rotation,
            onDismiss = onDismissCropDialog,
            onApply = onApplyCrop
        )
    }
}
