package com.tyejaedon.coverscreenos.ui.customize.wallpaper

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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

private data class WallpaperPreviewLoad(
    val frame: WallpaperFrame? = null,
    val failureMessage: String? = null
)

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
    onCropPreviewChanged: (Float, Float) -> Unit,
    onCropCommit: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fallbackWidth = LocalWindowInfo.current.containerSize.width.coerceAtLeast(1)
    val coverAspectRatio = remember(context) {
        val display = CoverDisplayHelper(context).getCoverDisplay() ?: context.display
        val mode = display.mode
        if (mode.physicalWidth > 0 && mode.physicalHeight > 0)
            mode.physicalWidth.toFloat() / mode.physicalHeight
        else 9f / 19f
    }
    val availableWidth = fallbackWidth
    val previewHeight = (availableWidth / coverAspectRatio).toInt().coerceAtLeast(1)
    val normalizedUri = wallpaperUri?.trim().takeUnless { it.isNullOrEmpty() }
    val uri = remember(normalizedUri) { normalizedUri?.toUri() }
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
                    throw error
                } catch (error: Exception) {
                    Log.w("WallpaperPreview", "Unable to decode selected wallpaper", error)
                    WallpaperPreviewLoad(failureMessage = error.message ?: "Wallpaper preview could not be loaded.")
                }
            }
        }
    }
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
            Text("Cover preview — drag to reposition", style = MaterialTheme.typography.labelLarge)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .pointerInput(zoom) {
                        detectDragGestures(
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
                        Text("Cover wallpaper", color = Color.White.copy(alpha = 0.9f))
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
}
