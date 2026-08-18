package com.tyejaedon.coverscreenos.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val WALLPAPER_PREVIEW_LOG_TAG = "WallpaperPreview"
private const val WALLPAPER_PREVIEW_RETRY_DELAY_MS = 600L
private const val WALLPAPER_PREVIEW_RETRY_MAX_ATTEMPTS = 6

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
            Text("Wallpaper customization", style = MaterialTheme.typography.titleMedium)
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
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val normalizedWallpaperUri = wallpaperUri
        ?.trim()
        .takeUnless { it.isNullOrEmpty() }
    var decodeRetryAttempt by remember(normalizedWallpaperUri) {
        mutableIntStateOf(0)
    }
    val requestedWidthPx = remember(containerSize.width) {
        max(containerSize.width, 1)
    }
    val requestedHeightPx = remember(density) { max(with(density) { 152.dp.roundToPx() }, 1) }
    val wallpaperCacheVersionToken = remember(normalizedWallpaperUri, decodeRetryAttempt) {
        normalizedWallpaperUri
            ?.takeUnless { it.isBlank() }
            ?.let { uriValue -> runCatching { resolvePreviewCacheVersionToken(uriValue.toUri()) }.getOrNull() }
    }
    val wallpaperBitmap by produceState<Bitmap?>(
        initialValue = null,
        normalizedWallpaperUri,
        wallpaperCacheVersionToken,
        decodeRetryAttempt,
        context,
        requestedWidthPx,
        requestedHeightPx
    ) {
        value = if (normalizedWallpaperUri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    decodeSampledPreviewBitmap(
                        context = context,
                        uri = normalizedWallpaperUri.toUri(),
                        cacheVersionToken = wallpaperCacheVersionToken,
                        requestedWidthPx = requestedWidthPx,
                        requestedHeightPx = requestedHeightPx
                    )
                }.onSuccess { decodedBitmap ->
                    if (decodedBitmap == null) {
                        Log.w(
                            WALLPAPER_PREVIEW_LOG_TAG,
                            "Preview decode returned null for URI=${previewUriDiagnostics(normalizedWallpaperUri.toUri())} attempt=$decodeRetryAttempt"
                        )
                    }
                }.getOrNull()
            }
        }
    }

    LaunchedEffect(normalizedWallpaperUri, wallpaperBitmap, decodeRetryAttempt) {
        if (normalizedWallpaperUri.isNullOrBlank()) return@LaunchedEffect
        if (wallpaperBitmap != null) return@LaunchedEffect
        if (decodeRetryAttempt >= WALLPAPER_PREVIEW_RETRY_MAX_ATTEMPTS) return@LaunchedEffect

        delay(WALLPAPER_PREVIEW_RETRY_DELAY_MS.milliseconds)
        decodeRetryAttempt += 1
    }

    val isCustomWallpaperConfigured = !normalizedWallpaperUri.isNullOrBlank()
    val hasPreviewDecodeFailure =
        isCustomWallpaperConfigured && wallpaperBitmap == null && decodeRetryAttempt >= WALLPAPER_PREVIEW_RETRY_MAX_ATTEMPTS
    val isPreviewLoading =
        isCustomWallpaperConfigured && wallpaperBitmap == null && decodeRetryAttempt < WALLPAPER_PREVIEW_RETRY_MAX_ATTEMPTS
    val previewFallbackMessage = when {
        !isCustomWallpaperConfigured -> "Using a pure black home screen background"
        hasPreviewDecodeFailure -> "Wallpaper preview failed. Pick a different image."
        else -> "Loading wallpaper preview... (${decodeRetryAttempt + 1}/$WALLPAPER_PREVIEW_RETRY_MAX_ATTEMPTS)"
    }
    val previewFallbackTextColor = if (hasPreviewDecodeFailure) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
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

                    if (isPreviewLoading) {
                        Text(
                            text = previewFallbackMessage,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Text(
                        text = previewFallbackMessage,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 12.dp),
                        color = previewFallbackTextColor,
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

    if (uri.scheme == "file") {
        return decodeSampledPreviewBitmapFromFileUri(
            context = context,
            uri = uri,
            cacheKey = cacheKey,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx
        )
    }

    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    openPreviewInputStream(context, uri)?.use { boundsStream ->
        BitmapFactory.decodeStream(boundsStream, null, boundsOptions)
    } ?: run {
        Log.w(
            WALLPAPER_PREVIEW_LOG_TAG,
            "Preview bounds stream unavailable uri=${previewUriDiagnostics(uri)}"
        )
        return decodePreviewBitmapWithImageDecoder(
            context = context,
            uri = uri,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx
        )
    }

    if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
        Log.w(
            WALLPAPER_PREVIEW_LOG_TAG,
            "Preview bounds invalid width=${boundsOptions.outWidth} height=${boundsOptions.outHeight} uri=${previewUriDiagnostics(uri)}"
        )
        return decodePreviewBitmapWithImageDecoder(
            context = context,
            uri = uri,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx
        )
    }

    val sampledOptions = BitmapFactory.Options().apply {
        inSampleSize = calculatePreviewInSampleSize(
            outWidth = boundsOptions.outWidth,
            outHeight = boundsOptions.outHeight,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx
        )
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    var decodedBitmap = openPreviewInputStream(context, uri)?.use { decodeStream ->
        BitmapFactory.decodeStream(decodeStream, null, sampledOptions)
    }

    if (decodedBitmap == null) {
        Log.w(
            WALLPAPER_PREVIEW_LOG_TAG,
            "BitmapFactory preview decode returned null sampleSize=${sampledOptions.inSampleSize} width=${boundsOptions.outWidth} height=${boundsOptions.outHeight} uri=${previewUriDiagnostics(uri)}"
        )
        decodedBitmap = decodePreviewBitmapWithImageDecoder(
            context = context,
            uri = uri,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx
        )
    }

    if (decodedBitmap != null) {
        WallpaperBitmapCache.put(cacheKey, decodedBitmap)
    }

    return decodedBitmap
}

private fun decodeSampledPreviewBitmapFromFileUri(
    context: android.content.Context,
    uri: Uri,
    cacheKey: String,
    requestedWidthPx: Int,
    requestedHeightPx: Int
): Bitmap? {
    val path = uri.path
    if (path.isNullOrBlank()) {
        Log.w(
            WALLPAPER_PREVIEW_LOG_TAG,
            "Preview file URI missing path uri=${previewUriDiagnostics(uri)}"
        )
        return null
    }

    val file = File(path)
    if (!file.exists() || !file.isFile || !file.canRead() || file.length() <= 0L) {
        Log.w(
            WALLPAPER_PREVIEW_LOG_TAG,
            "Preview file is unavailable uri=${previewUriDiagnostics(uri)}"
        )
        return null
    }

    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

    if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
        Log.w(
            WALLPAPER_PREVIEW_LOG_TAG,
            "Preview file bounds invalid width=${boundsOptions.outWidth} height=${boundsOptions.outHeight} uri=${previewUriDiagnostics(uri)}"
        )
        return decodePreviewBitmapWithImageDecoder(
            context = context,
            uri = uri,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx
        )
    }

    val sampleSize = calculatePreviewInSampleSize(
        outWidth = boundsOptions.outWidth,
        outHeight = boundsOptions.outHeight,
        requestedWidthPx = requestedWidthPx,
        requestedHeightPx = requestedHeightPx
    )
    val sampledOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    var decodedBitmap = BitmapFactory.decodeFile(file.absolutePath, sampledOptions)
    if (decodedBitmap == null) {
        Log.w(
            WALLPAPER_PREVIEW_LOG_TAG,
            "BitmapFactory preview file decode returned null sampleSize=$sampleSize width=${boundsOptions.outWidth} height=${boundsOptions.outHeight} uri=${previewUriDiagnostics(uri)}"
        )
        decodedBitmap = decodePreviewBitmapWithImageDecoder(
            context = context,
            uri = uri,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx
        )
    }

    if (decodedBitmap != null) {
        WallpaperBitmapCache.put(cacheKey, decodedBitmap)
    }

    return decodedBitmap
}

private fun decodePreviewBitmapWithImageDecoder(
    context: android.content.Context,
    uri: Uri,
    requestedWidthPx: Int,
    requestedHeightPx: Int
): Bitmap? {
    val source = when (uri.scheme) {
        "file" -> {
            val path = uri.path ?: return null
            val file = File(path)
            if (!file.exists() || !file.isFile || !file.canRead()) {
                return null
            }
            ImageDecoder.createSource(file)
        }

        else -> ImageDecoder.createSource(context.contentResolver, uri)
    }

    return runCatching {
        ImageDecoder.decodeBitmap(source) { decoder, imageInfo, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val sampleSize = calculateImageDecoderSampleSize(
                sourceWidth = imageInfo.size.width,
                sourceHeight = imageInfo.size.height,
                requestedWidthPx = requestedWidthPx,
                requestedHeightPx = requestedHeightPx
            )
            if (sampleSize > 1) {
                decoder.setTargetSampleSize(sampleSize)
            }
        }
    }.onFailure { error ->
        Log.w(
            WALLPAPER_PREVIEW_LOG_TAG,
            "ImageDecoder preview decode failed uri=${previewUriDiagnostics(uri)} error=${error.message}"
        )
    }.getOrNull()
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

private fun calculateImageDecoderSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    requestedWidthPx: Int,
    requestedHeightPx: Int
): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0) return 1

    val widthRatio = sourceWidth.toFloat() / requestedWidthPx.coerceAtLeast(1)
    val heightRatio = sourceHeight.toFloat() / requestedHeightPx.coerceAtLeast(1)
    return max(widthRatio, heightRatio).toInt().coerceAtLeast(1)
}

private fun previewUriDiagnostics(uri: Uri): String {
    val scheme = uri.scheme ?: "<none>"
    if (scheme != "file") {
        return "uri=$uri scheme=$scheme"
    }

    val path = uri.path
    if (path.isNullOrBlank()) {
        return "uri=$uri scheme=file path=<empty>"
    }

    val file = File(path)
    return "uri=$uri scheme=file exists=${file.exists()} isFile=${file.isFile} canRead=${file.canRead()} length=${file.length()}"
}

private fun openPreviewInputStream(context: android.content.Context, uri: Uri): InputStream? {
    return when (uri.scheme) {
        "file" -> uri.path?.let { path -> runCatching { File(path).inputStream() }.getOrNull() }
        else -> runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?: runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")
                    ?.let { descriptor ->
                        ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                    }
            }.getOrNull()
    }
}

