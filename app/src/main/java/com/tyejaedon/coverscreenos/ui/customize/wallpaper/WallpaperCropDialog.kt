package com.tyejaedon.coverscreenos.ui.customize.wallpaper

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_CROP_OFFSET
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_ZOOM
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_CROP_OFFSET
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_ZOOM
import com.tyejaedon.coverscreenos.datastore.WallpaperScaleMode
import com.tyejaedon.coverscreenos.ui.WallpaperFrame
import com.tyejaedon.coverscreenos.ui.WallpaperMediaContent
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUITheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Computes the next normalized (cropX, cropY) after a pan gesture in pixels,
 * matching the exact scaling math used by WallpaperTransformFrame.
 */
internal fun computeNextWallpaperCrop(
    currentCropX: Float,
    currentCropY: Float,
    panDeltaX: Float,
    panDeltaY: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    sourceWidth: Int,
    sourceHeight: Int,
    scaleMode: WallpaperScaleMode,
    zoom: Float,
    rotation: Int
): Pair<Float, Float> {
    val widthPx = containerWidthPx.coerceAtLeast(1f)
    val heightPx = containerHeightPx.coerceAtLeast(1f)
    val rotatedWidth = if (rotation % 180 == 0) sourceWidth else sourceHeight
    val rotatedHeight = if (rotation % 180 == 0) sourceHeight else sourceWidth
    val widthScale = widthPx / rotatedWidth.coerceAtLeast(1)
    val heightScale = heightPx / rotatedHeight.coerceAtLeast(1)
    val baseScale = if (scaleMode == WallpaperScaleMode.CROP) {
        max(widthScale, heightScale)
    } else {
        min(widthScale, heightScale)
    }
    val scaledWidth = rotatedWidth * baseScale * zoom
    val scaledHeight = rotatedHeight * baseScale * zoom
    val maxPanX = ((scaledWidth - widthPx) / 2f)
    val maxPanY = ((scaledHeight - heightPx) / 2f)

    val nextX = if (maxPanX > 0.5f) {
        (currentCropX + panDeltaX / maxPanX).coerceIn(MIN_WALLPAPER_CROP_OFFSET, MAX_WALLPAPER_CROP_OFFSET)
    } else {
        0f
    }
    val nextY = if (maxPanY > 0.5f) {
        (currentCropY + panDeltaY / maxPanY).coerceIn(MIN_WALLPAPER_CROP_OFFSET, MAX_WALLPAPER_CROP_OFFSET)
    } else {
        0f
    }
    return nextX to nextY
}

@Composable
internal fun WallpaperCropDialog(
    uri: Uri,
    frame: WallpaperFrame,
    coverAspectRatio: Float,
    initialScaleMode: WallpaperScaleMode,
    initialCropX: Float,
    initialCropY: Float,
    initialZoom: Float,
    initialRotation: Int,
    onDismiss: () -> Unit,
    onApply: (
        scaleMode: WallpaperScaleMode,
        cropX: Float,
        cropY: Float,
        zoom: Float,
        rotation: Int
    ) -> Unit
) {
    var draftScaleMode by remember { mutableStateOf(initialScaleMode) }
    var draftCropX by remember { mutableFloatStateOf(initialCropX) }
    var draftCropY by remember { mutableFloatStateOf(initialCropY) }
    var draftZoom by remember { mutableFloatStateOf(initialZoom.coerceIn(MIN_WALLPAPER_ZOOM, MAX_WALLPAPER_ZOOM)) }
    var draftRotation by remember { mutableIntStateOf(initialRotation) }

    val isCompactHeight = LocalConfiguration.current.screenHeightDp <= 480

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
                .padding(horizontal = if (isCompactHeight) 10.dp else 16.dp, vertical = if (isCompactHeight) 6.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 6.dp else 12.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompactHeight) 40.dp else 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
                }
                Text(
                    text = "Zoom & Crop",
                    style = if (isCompactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                TextButton(
                    onClick = {
                        draftScaleMode = WallpaperScaleMode.CROP
                        draftCropX = 0f
                        draftCropY = 0f
                        draftZoom = 1f
                        draftRotation = 0
                    }
                ) {
                    Icon(
                        Icons.Filled.RestartAlt,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Reset", color = Color.White.copy(alpha = 0.85f))
                }
                Button(
                    onClick = { onApply(draftScaleMode, draftCropX, draftCropY, draftZoom, draftRotation) },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = OneUITheme.colors.accent)
                ) {
                    Text("Apply", color = Color.White)
                }
            }

            // Interactive Pinch-to-Zoom & Drag Crop Stage
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val frameModifier = if (maxWidth / maxHeight.coerceAtLeast(1.dp) > coverAspectRatio) {
                    Modifier.fillMaxHeight().aspectRatio(coverAspectRatio)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(coverAspectRatio)
                }

                Box(
                    modifier = frameModifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .pointerInput(draftScaleMode, draftRotation, frame.width, frame.height) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                val nextZoom = (draftZoom * zoomChange)
                                    .coerceIn(MIN_WALLPAPER_ZOOM, MAX_WALLPAPER_ZOOM)
                                val (nextX, nextY) = computeNextWallpaperCrop(
                                    currentCropX = draftCropX,
                                    currentCropY = draftCropY,
                                    panDeltaX = pan.x,
                                    panDeltaY = pan.y,
                                    containerWidthPx = size.width.toFloat(),
                                    containerHeightPx = size.height.toFloat(),
                                    sourceWidth = frame.width,
                                    sourceHeight = frame.height,
                                    scaleMode = draftScaleMode,
                                    zoom = nextZoom,
                                    rotation = draftRotation
                                )
                                draftZoom = nextZoom
                                draftCropX = nextX
                                draftCropY = nextY
                            }
                        }
                ) {
                    WallpaperMediaContent(
                        uri = uri,
                        frame = frame,
                        scaleMode = draftScaleMode,
                        cropX = draftCropX,
                        cropY = draftCropY,
                        zoom = draftZoom,
                        rotation = draftRotation,
                        dimAmount = 0f,
                        blurRadiusDp = 0f,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Subtle rule-of-thirds crop grid
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gridColor = Color.White.copy(alpha = 0.25f)
                        val stroke = 1.dp.toPx()
                        val thirdW = size.width / 3f
                        val thirdH = size.height / 3f
                        for (i in 1..2) {
                            drawLine(
                                color = gridColor,
                                start = Offset(thirdW * i, 0f),
                                end = Offset(thirdW * i, size.height),
                                strokeWidth = stroke
                            )
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, thirdH * i),
                                end = Offset(size.width, thirdH * i),
                                strokeWidth = stroke
                            )
                        }
                    }

                    if (!isCompactHeight) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = "Pinch to zoom • Drag to reposition",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Zoom & Framing Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 4.dp else 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val displayZoom = (draftZoom * 10f).roundToInt() / 10f
                    Text(
                        text = "${displayZoom}x",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.width(38.dp)
                    )
                    Slider(
                        value = draftZoom,
                        onValueChange = { nextZoom ->
                            draftZoom = nextZoom.coerceIn(MIN_WALLPAPER_ZOOM, MAX_WALLPAPER_ZOOM)
                        },
                        valueRange = MIN_WALLPAPER_ZOOM..MAX_WALLPAPER_ZOOM,
                        colors = SliderDefaults.colors(
                            thumbColor = OneUITheme.colors.accent,
                            activeTrackColor = OneUITheme.colors.accent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            draftScaleMode = if (draftScaleMode == WallpaperScaleMode.CROP) {
                                WallpaperScaleMode.FIT
                            } else {
                                WallpaperScaleMode.CROP
                            }
                        },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Crop, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (draftScaleMode == WallpaperScaleMode.CROP) "Mode: Crop" else "Mode: Fit",
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = { draftRotation = (draftRotation + 90) % 360 },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Rotate (${draftRotation}°)", color = Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}
