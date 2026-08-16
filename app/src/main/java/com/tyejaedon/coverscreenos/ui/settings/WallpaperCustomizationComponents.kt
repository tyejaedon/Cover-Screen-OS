package com.tyejaedon.coverscreenos.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.WallpaperScaleMode
import com.tyejaedon.coverscreenos.ui.WallpaperBitmapCache
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusMedium
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusSmall
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun WallpaperCustomizationCard(
    wallpaperUri: String?,
    wallpaperScaleMode: WallpaperScaleMode,
    dimAmount: Float,
    blurRadiusDp: Float,
    isWallpaperImportInProgress: Boolean,
    onChooseWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onScaleModeSelected: (WallpaperScaleMode) -> Unit,
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
            Text("Wallpaper", style = MaterialTheme.typography.titleMedium)
            Text(
                "Keep visuals clean: adjust readability so app labels stay clear in bright or busy photos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WallpaperPreviewCard(
                wallpaperUri = wallpaperUri,
                wallpaperScaleMode = wallpaperScaleMode,
                dimAmount = dimAmount,
                blurRadiusDp = blurRadiusDp,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
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

            WallpaperPresentationControls(
                wallpaperScaleMode = wallpaperScaleMode,
                dimAmount = dimAmount,
                blurRadiusDp = blurRadiusDp,
                onScaleModeSelected = onScaleModeSelected,
                onDimAmountPreviewChanged = onDimAmountPreviewChanged,
                onDimAmountCommit = onDimAmountCommit,
                onBlurRadiusPreviewChanged = onBlurRadiusPreviewChanged,
                onBlurRadiusCommit = onBlurRadiusCommit
            )
        }
    }
}

@Composable
private fun WallpaperPresentationControls(
    wallpaperScaleMode: WallpaperScaleMode,
    dimAmount: Float,
    blurRadiusDp: Float,
    onScaleModeSelected: (WallpaperScaleMode) -> Unit,
    onDimAmountPreviewChanged: (Float) -> Unit,
    onDimAmountCommit: () -> Unit,
    onBlurRadiusPreviewChanged: (Float) -> Unit,
    onBlurRadiusCommit: () -> Unit
) {
    Text("Fit mode", style = MaterialTheme.typography.labelLarge)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val modes = listOf(WallpaperScaleMode.CROP, WallpaperScaleMode.FIT)
        modes.forEach { mode ->
            val selected = wallpaperScaleMode == mode
            if (selected) {
                Button(
                    onClick = { onScaleModeSelected(mode) },
                    modifier = Modifier.coverMinimumTouchTarget()
                ) {
                    Text(if (mode == WallpaperScaleMode.CROP) "Crop" else "Fit")
                }
            } else {
                OutlinedButton(
                    onClick = { onScaleModeSelected(mode) },
                    modifier = Modifier.coverMinimumTouchTarget()
                ) {
                    Text(if (mode == WallpaperScaleMode.CROP) "Crop" else "Fit")
                }
            }
        }
    }

    Text(
        text = "Dim overlay: ${(dimAmount * 100f).roundToInt()}%",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Slider(
        value = dimAmount,
        onValueChange = onDimAmountPreviewChanged,
        onValueChangeFinished = onDimAmountCommit,
        valueRange = MIN_WALLPAPER_DIM_AMOUNT..MAX_WALLPAPER_DIM_AMOUNT,
        modifier = Modifier.fillMaxWidth()
    )

    Text(
        text = "Blur: ${blurRadiusDp.roundToInt()}dp",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Slider(
        value = blurRadiusDp,
        onValueChange = onBlurRadiusPreviewChanged,
        onValueChangeFinished = onBlurRadiusCommit,
        valueRange = MIN_WALLPAPER_BLUR_RADIUS_DP..MAX_WALLPAPER_BLUR_RADIUS_DP,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun WallpaperPreviewCard(
    wallpaperUri: String?,
    wallpaperScaleMode: WallpaperScaleMode,
    dimAmount: Float,
    blurRadiusDp: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val requestedWidthPx = remember(configuration.screenWidthDp, density) {
        max(with(density) { configuration.screenWidthDp.dp.roundToPx() }, 1)
    }
    val requestedHeightPx = remember(density) { max(with(density) { 152.dp.roundToPx() }, 1) }
    val wallpaperCacheVersionToken = remember(wallpaperUri) {
        wallpaperUri
            ?.takeUnless { it.isBlank() }
            ?.let { uriValue -> runCatching { resolvePreviewCacheVersionToken(uriValue.toUri()) }.getOrNull() }
    }
    val wallpaperBitmap by produceState<Bitmap?>(
        initialValue = null,
        wallpaperUri,
        wallpaperCacheVersionToken,
        context,
        requestedWidthPx,
        requestedHeightPx
    ) {
        value = if (wallpaperUri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    decodeSampledPreviewBitmap(
                        context = context,
                        uri = wallpaperUri.toUri(),
                        cacheVersionToken = wallpaperCacheVersionToken,
                        requestedWidthPx = requestedWidthPx,
                        requestedHeightPx = requestedHeightPx
                    )
                }.getOrNull()
            }
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(CoverOSCornerRadiusSmall),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Live cover preview",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (wallpaperScaleMode == WallpaperScaleMode.CROP) "Crop" else "Fit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .height(152.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background)
            ) {
        val bitmap = wallpaperBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Selected wallpaper preview",
                contentScale = if (wallpaperScaleMode == WallpaperScaleMode.CROP) {
                    ContentScale.Crop
                } else {
                    ContentScale.Fit
                },
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadiusDp.coerceAtLeast(0f).dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAmount.coerceIn(0f, 1f)))
            )

            // Simulated lock tile overlay to preview readability before applying.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "9:41",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = "Tue, Aug 16",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .width(62.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Using pure black launcher background",
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
            }
        }
    }
}

private fun decodeSampledPreviewBitmap(
    context: android.content.Context,
    uri: Uri,
    cacheVersionToken: String? = null,
    requestedWidthPx: Int,
    requestedHeightPx: Int
): Bitmap? {
    val cacheKey = WallpaperBitmapCache.buildKey(
        uri = uri.toString(),
        versionToken = cacheVersionToken ?: resolvePreviewCacheVersionToken(uri),
        requestedWidthPx = requestedWidthPx,
        requestedHeightPx = requestedHeightPx
    )
    WallpaperBitmapCache.get(cacheKey)?.let { cachedBitmap ->
        return cachedBitmap
    }

    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    openPreviewInputStream(context, uri)?.use { boundsStream ->
        BitmapFactory.decodeStream(boundsStream, null, boundsOptions)
    } ?: return null

    val sampledOptions = BitmapFactory.Options().apply {
        inSampleSize = calculatePreviewInSampleSize(
            outWidth = boundsOptions.outWidth,
            outHeight = boundsOptions.outHeight,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx
        )
        inPreferredConfig = Bitmap.Config.RGB_565
    }

    val decodedBitmap = openPreviewInputStream(context, uri)?.use { decodeStream ->
        BitmapFactory.decodeStream(decodeStream, null, sampledOptions)
    }

    if (decodedBitmap != null) {
        WallpaperBitmapCache.put(cacheKey, decodedBitmap)
    }

    return decodedBitmap
}

private fun resolvePreviewCacheVersionToken(uri: Uri): String? {
    if (uri.scheme != "file") return null
    val path = uri.path ?: return null
    val file = File(path)
    if (!file.exists() || !file.isFile) return null
    return "${file.lastModified()}_${file.length()}"
}

private fun calculatePreviewInSampleSize(
    outWidth: Int,
    outHeight: Int,
    requestedWidthPx: Int,
    requestedHeightPx: Int
): Int {
    if (outWidth <= 0 || outHeight <= 0) return 1

    var inSampleSize = 1
    val halfWidth = outWidth / 2
    val halfHeight = outHeight / 2

    while ((halfWidth / inSampleSize) >= requestedWidthPx && (halfHeight / inSampleSize) >= requestedHeightPx) {
        inSampleSize *= 2
    }
    return inSampleSize.coerceAtLeast(1)
}

private fun openPreviewInputStream(context: android.content.Context, uri: Uri): InputStream? {
    return when (uri.scheme) {
        "file" -> uri.path?.let { path -> runCatching { File(path).inputStream() }.getOrNull() }
        else -> runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }
}

