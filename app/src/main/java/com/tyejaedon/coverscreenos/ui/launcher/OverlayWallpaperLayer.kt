package com.tyejaedon.coverscreenos.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tyejaedon.coverscreenos.datastore.WallpaperMediaType
import com.tyejaedon.coverscreenos.datastore.WallpaperMediaLimits
import com.tyejaedon.coverscreenos.datastore.WallpaperScaleMode
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_ZOOM
import com.tyejaedon.coverscreenos.ui.launcher.WallpaperStaticFrame
import com.tyejaedon.coverscreenos.ui.launcher.WallpaperTransformFrame
import com.tyejaedon.coverscreenos.ui.theme.CoverOSDarkBackground
import com.tyejaedon.coverscreenos.ui.theme.CoverOSDarkSurfaceVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val WALLPAPER_LOG_TAG = "CoverWallpaper"
private const val MAX_WALLPAPER_DECODE_PIXELS = 6_000_000L

@Composable
internal fun CoverWallpaperLayer(
    wallpaperUri: String?,
    wallpaperScaleMode: WallpaperScaleMode,
    wallpaperCropX: Float,
    wallpaperCropY: Float,
    wallpaperZoom: Float,
    wallpaperRotation: Int,
    wallpaperDimAmount: Float,
    wallpaperBlurRadiusDp: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val containerSize = LocalWindowInfo.current.containerSize
    val uriValue = wallpaperUri?.trim().takeUnless { it.isNullOrEmpty() }
    val uri = remember(uriValue) { uriValue?.toUri() }
    val requestedWidth = max(containerSize.width, 1)
    val requestedHeight = max(containerSize.height, 1)
    val media by produceState<WallpaperFrame?>(
        null,
        uri,
        requestedWidth,
        requestedHeight,
        wallpaperRotation,
        wallpaperScaleMode
    ) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            try {
                decodeWallpaperFrame(
                    context,
                    uri,
                    requestedWidth,
                    requestedHeight,
                    MAX_WALLPAPER_ZOOM,
                    wallpaperRotation,
                    wallpaperScaleMode
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(WALLPAPER_LOG_TAG, "Wallpaper decode failed: ${error.message}", error)
                null
            }
        }
    }
    val frame = media
    if (frame == null || uri == null) {
        Box(
            modifier = modifier.background(
                Brush.verticalGradient(
                    colors = listOf(CoverOSDarkSurfaceVariant, CoverOSDarkBackground)
                )
            )
        )
    } else {
        WallpaperMediaContent(
            uri = uri,
            frame = frame,
            scaleMode = wallpaperScaleMode,
            cropX = wallpaperCropX,
            cropY = wallpaperCropY,
            zoom = wallpaperZoom,
            rotation = wallpaperRotation,
            dimAmount = wallpaperDimAmount,
            blurRadiusDp = wallpaperBlurRadiusDp,
            modifier = modifier
        )
    }
}

@Composable
internal fun WallpaperMediaContent(
    uri: Uri,
    frame: WallpaperFrame,
    scaleMode: WallpaperScaleMode,
    cropX: Float,
    cropY: Float,
    zoom: Float,
    rotation: Int,
    dimAmount: Float,
    blurRadiusDp: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackActive = rememberWallpaperPlaybackActive()
    val lowEffects = rememberLowEffectsMode()
    val shouldPlay by rememberUpdatedState(playbackActive && !lowEffects)
    val animatedDrawable by produceState<AnimatedImageDrawable?>(null, uri, frame.type, shouldPlay) {
        value = if (frame.type != WallpaperMediaType.GIF || !shouldPlay) null else withContext(Dispatchers.IO) {
            try {
                ImageDecoder.decodeDrawable(imageSource(context, uri)) as? AnimatedImageDrawable
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(WALLPAPER_LOG_TAG, "GIF decode failed: ${error.message}", error)
                null
            }
        }
    }
    var videoPlaybackFailed by remember(uri) { mutableStateOf(false) }

    DisposableEffect(animatedDrawable, playbackActive, lowEffects) {
        val drawable = animatedDrawable
        if (drawable != null) {
            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            if (playbackActive && !lowEffects) drawable.start() else drawable.stop()
        }
        onDispose { drawable?.stop() }
    }

    if (frame.type == WallpaperMediaType.VIDEO && shouldPlay && !videoPlaybackFailed) {
        WallpaperTransformFrame(
            sourceWidth = frame.width,
            sourceHeight = frame.height,
            scaleMode = scaleMode,
            cropX = cropX,
            cropY = cropY,
            zoom = zoom,
            rotation = rotation,
            dimAmount = dimAmount,
            blurRadiusDp = blurRadiusDp,
            modifier = modifier
        ) { frameModifier ->
            AndroidView(
                factory = { viewContext -> WallpaperVideoTextureView(viewContext) },
                update = { view ->
                    view.setPlayback(uri, shouldPlay) { error ->
                        Log.w(WALLPAPER_LOG_TAG, "Wallpaper video playback failed: ${error.message}")
                        videoPlaybackFailed = true
                    }
                },
                onRelease = { view -> view.release() },
                modifier = frameModifier
            )
        }
    } else if (frame.type == WallpaperMediaType.GIF && animatedDrawable != null && shouldPlay) {
        WallpaperTransformFrame(
            sourceWidth = frame.width,
            sourceHeight = frame.height,
            scaleMode = scaleMode,
            cropX = cropX,
            cropY = cropY,
            zoom = zoom,
            rotation = rotation,
            dimAmount = dimAmount,
            blurRadiusDp = blurRadiusDp,
            modifier = modifier
        ) { frameModifier ->
            AndroidView(
                factory = { viewContext -> ImageView(viewContext).apply { scaleType = ImageView.ScaleType.FIT_XY } },
                update = { view -> view.setImageDrawable(animatedDrawable) },
                modifier = frameModifier
            )
        }
    } else {
        WallpaperStaticFrame(
            bitmap = frame.bitmap,
            scaleMode = scaleMode,
            cropX = cropX,
            cropY = cropY,
            zoom = zoom,
            rotation = rotation,
            dimAmount = dimAmount,
            blurRadiusDp = blurRadiusDp,
            modifier = modifier
        )
    }
}

internal data class WallpaperFrame(
    val bitmap: Bitmap,
    val type: WallpaperMediaType,
    val width: Int,
    val height: Int
)

internal fun uprightVideoDimensions(width: Int, height: Int, metadataRotation: Int): Pair<Int, Int> {
    val rotation = ((metadataRotation % 360) + 360) % 360
    return if (rotation == 90 || rotation == 270) height to width else width to height
}

internal fun decodeWallpaperPreviewFrame(
    context: Context,
    uri: Uri,
    requestedWidth: Int,
    requestedHeight: Int,
    zoom: Float,
    rotation: Int,
    scaleMode: WallpaperScaleMode
): WallpaperFrame = decodeWallpaperFrame(context, uri, requestedWidth, requestedHeight, zoom, rotation, scaleMode)

internal fun decodeWallpaperFrame(
    context: Context,
    uri: Uri,
    requestedWidth: Int,
    requestedHeight: Int,
    zoom: Float,
    rotation: Int,
    scaleMode: WallpaperScaleMode
): WallpaperFrame {
    val type = detectWallpaperType(context, uri)
    val fileToken = if (uri.scheme == "file") {
        uri.path?.let { path ->
            File(path).let { file -> "${file.lastModified()}_${file.length()}" }
        }
    } else {
        null
    }
    val cacheKey = WallpaperBitmapCache.buildKey(
        uri.toString(),
        "$fileToken|$zoom|$rotation|$scaleMode",
        requestedWidth,
        requestedHeight
    )
    WallpaperBitmapCache.get(cacheKey)?.let { return it }
    if (type == WallpaperMediaType.VIDEO) {
        val retriever = MediaMetadataRetriever()
        return try {
            setRetrieverDataSource(context, retriever, uri)
            val encodedWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                ?: error("Video width is unavailable")
            val encodedHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                ?: error("Video height is unavailable")
            val metadataRotation =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val (width, height) = uprightVideoDimensions(encodedWidth, encodedHeight, metadataRotation)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: error("Video duration is unavailable")
            require(duration in 1..WallpaperMediaLimits.MAX_DURATION_MS &&
                WallpaperMediaLimits.hasValidDimensions(width, height, animated = true)) {
                "Video exceeds wallpaper limits"
            }
            val frameWidth = if (rotation % 180 == 0) requestedWidth else requestedHeight
            val frameHeight = if (rotation % 180 == 0) requestedHeight else requestedWidth
            val widthScale = frameWidth.toFloat() / width.coerceAtLeast(1)
            val heightScale = frameHeight.toFloat() / height.coerceAtLeast(1)
            val baseScale = if (scaleMode == WallpaperScaleMode.CROP) max(widthScale, heightScale) else min(widthScale, heightScale)
            val scale = (baseScale * zoom).coerceAtMost(1f)
            val poster = retriever.getScaledFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                max((width * scale).toInt(), 1),
                max((height * scale).toInt(), 1)
            ) ?: error("Video poster frame is unavailable")
            WallpaperFrame(poster, type, width, height).also { WallpaperBitmapCache.put(cacheKey, it) }
        } finally {
            retriever.release()
        }
    }

    val source = imageSource(context, uri)
    var sourceWidth = 0
    var sourceHeight = 0
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        sourceWidth = info.size.width
        sourceHeight = info.size.height
        require(WallpaperMediaLimits.hasValidDimensions(
            sourceWidth,
            sourceHeight,
            animated = type == WallpaperMediaType.GIF
        )) { "Wallpaper image exceeds its dimension or pixel limit" }
        val frameWidth = if (rotation % 180 == 0) requestedWidth else requestedHeight
        val frameHeight = if (rotation % 180 == 0) requestedHeight else requestedWidth
        val scaleX = frameWidth.toFloat() / sourceWidth
        val scaleY = frameHeight.toFloat() / sourceHeight
        val baseScale = if (scaleMode == WallpaperScaleMode.CROP) max(scaleX, scaleY) else min(scaleX, scaleY)
        val targetScale = (baseScale * zoom).coerceAtMost(1f)
        val sourcePixels = sourceWidth.toLong() * sourceHeight
        val pixelBudgetScale = sqrt(MAX_WALLPAPER_DECODE_PIXELS / sourcePixels.toFloat())
        val decodeScale = min(targetScale, pixelBudgetScale).coerceAtMost(1f)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        if (decodeScale < 1f) {
            decoder.setTargetSize(
                max((sourceWidth * decodeScale).toInt(), 1),
                max((sourceHeight * decodeScale).toInt(), 1)
            )
        }
    }
    return WallpaperFrame(bitmap, type, sourceWidth, sourceHeight).also {
        WallpaperBitmapCache.put(cacheKey, it)
    }
}

private fun detectWallpaperType(context: Context, uri: Uri): WallpaperMediaType {
    val prefix = openWallpaperInputStream(context, uri)?.use { it.readNBytes(4096) }
        ?: error("Wallpaper file cannot be read")
    return WallpaperMediaLimits.detectMediaType(prefix)
}

private fun imageSource(context: Context, uri: Uri): ImageDecoder.Source =
    if (uri.scheme == "file") {
        ImageDecoder.createSource(File(uri.path ?: error("Wallpaper file path is missing")))
    } else {
        ImageDecoder.createSource(context.contentResolver, uri)
    }

private fun setRetrieverDataSource(context: Context, retriever: MediaMetadataRetriever, uri: Uri) {
    if (uri.scheme == "file") retriever.setDataSource(uri.path ?: error("Video file path is missing"))
    else retriever.setDataSource(context, uri)
}

private fun openWallpaperInputStream(context: Context, uri: Uri) =
    if (uri.scheme == "file") uri.path?.let { File(it).inputStream() }
    else context.contentResolver.openInputStream(uri)

private class WallpaperVideoTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var sourceUri: Uri? = null
    private var shouldPlay = false
    private var player: MediaPlayer? = null
    private var isPrepared = false
    private var surface: Surface? = null
    private var failureCallback: ((Throwable) -> Unit)? = null

    init {
        surfaceTextureListener = this
    }

    fun setPlayback(uri: Uri, play: Boolean, onFailure: (Throwable) -> Unit) {
        failureCallback = onFailure
        if (sourceUri != uri) {
            releasePlayer()
            sourceUri = uri
        }
        shouldPlay = play
        val current = player
        if (current != null && isPrepared) {
            try {
                if (play && !current.isPlaying) current.start()
                else if (!play && current.isPlaying) current.pause()
            } catch (error: IllegalStateException) {
                fail(error)
            }
        } else if (isAvailable) {
            preparePlayer()
        }
    }

    private fun preparePlayer() {
        val uri = sourceUri ?: return
        val texture = surfaceTexture ?: return
        if (player != null) return
        try {
            val output = Surface(texture)
            val mediaPlayer = MediaPlayer()
            surface = output
            player = mediaPlayer
            mediaPlayer.setSurface(output)
            mediaPlayer.setVolume(0f, 0f)
            mediaPlayer.isLooping = true
            mediaPlayer.setOnPreparedListener { prepared ->
                isPrepared = true
                try {
                    if (shouldPlay && !prepared.isPlaying) prepared.start()
                } catch (error: IllegalStateException) {
                    fail(error)
                }
            }
            mediaPlayer.setOnErrorListener { _, what, extra ->
                fail(IllegalStateException("MediaPlayer error=$what extra=$extra"))
                true
            }
            if (uri.scheme == "file") mediaPlayer.setDataSource(uri.path ?: error("Video path is missing"))
            else mediaPlayer.setDataSource(context, uri)
            mediaPlayer.prepareAsync()
        } catch (error: Exception) {
            fail(error)
        }
    }

    private fun fail(error: Throwable) {
        releasePlayer()
        failureCallback?.invoke(error)
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        isPrepared = false
        surface?.release()
        surface = null
    }

    fun release() {
        sourceUri = null
        releasePlayer()
        surfaceTextureListener = null
    }

    override fun onSurfaceTextureAvailable(texture: android.graphics.SurfaceTexture, width: Int, height: Int) =
        preparePlayer()

    override fun onSurfaceTextureSizeChanged(texture: android.graphics.SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(texture: android.graphics.SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(texture: android.graphics.SurfaceTexture) = Unit

    override fun onDetachedFromWindow() {
        releasePlayer()
        super.onDetachedFromWindow()
    }
}

@Composable
private fun rememberWallpaperPlaybackActive(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var active by remember { mutableStateOf(false) }
    DisposableEffect(context, lifecycleOwner, powerManager) {
        fun refresh() {
            active = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                (powerManager?.isInteractive ?: true)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) = refresh()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        val observer = LifecycleEventObserver { _, _ -> refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        refresh()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    return active
}

@Composable
private fun rememberLowEffectsMode(): Boolean {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var lowEffects by remember { mutableStateOf(powerManager?.isPowerSaveMode == true) }
    DisposableEffect(context, powerManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                lowEffects = powerManager?.isPowerSaveMode == true
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return lowEffects
}
