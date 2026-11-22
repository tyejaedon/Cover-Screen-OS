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
// Cap static wallpaper decoding at 1.8 MP so large camera photos downsample safely without OOMs.
private const val MAX_WALLPAPER_DECODE_PIXELS = 1_800_000L

/**
 * Root wallpaper surface for the cover screen overlay.
 *
 * Resolves the target container dimensions from `LocalWindowInfo`, decodes a scaled
 * [WallpaperFrame] (or video poster frame) asynchronously on `Dispatchers.IO`, and falls back
 * to a dark vertical gradient when no wallpaper URI is configured or decoding fails.
 */
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
    // Cover window pixel dimensions used to target-size decoded bitmaps and video poster frames.
    val containerSize = LocalWindowInfo.current.containerSize
    val uriValue = wallpaperUri?.trim().takeUnless { it.isNullOrEmpty() }
    val uri = remember(uriValue) { uriValue?.toUri() }
    val requestedWidth = max(containerSize.width, 1)
    val requestedHeight = max(containerSize.height, 1)
    // Decode metadata and static/poster bitmap off the main thread whenever URI or framing inputs change.
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
    // Latest decoded static bitmap or video first-frame poster (null triggers the gradient fallback).
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

/**
 * Selects the appropriate rendering backend for a decoded [WallpaperFrame]:
 * - **Video**: Hardware-accelerated looping playback via [WallpaperVideoTextureView] (`TextureView`
 *   + `MediaPlayer` + `Surface`) inside `AndroidView`, guarded by lifecycle/interactive/power-save state.
 * - **GIF**: `ImageDecoder.decodeDrawable` (`AnimatedImageDrawable`) rendered inside a legacy `ImageView`.
 * - **Static / Paused / Fallback**: Renders the pre-decoded poster or still image via [WallpaperStaticFrame].
 */
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
    // True only while the cover screen is awake/interactive and the overlay lifecycle is active.
    val playbackActive = rememberWallpaperPlaybackActive()
    // True when Android Battery Saver is active, forcing motion wallpapers down to a static poster.
    val lowEffects = rememberLowEffectsMode()
    // Combined gate that pauses video/GIF motion when the screen sleeps, stops, or enters Battery Saver.
    val shouldPlay by rememberUpdatedState(playbackActive && !lowEffects)
    // Decoded looping GIF drawable; cleared to null when paused so memory isn't held while idle.
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
    // Sticky fallback flag: once MediaPlayer fails for a URI, show the static poster frame instead.
    var videoPlaybackFailed by remember(uri) { mutableStateOf(false) }

    // Synchronize AnimatedImageDrawable start/stop with visibility and power-saving state.
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
            // Host TextureView inside AndroidView so MediaPlayer frames receive the same Compose transforms/blur.
            AndroidView(
                factory = { viewContext -> WallpaperVideoTextureView(viewContext) },
                update = { view ->
                    view.setPlayback(uri, shouldPlay) { error ->
                        Log.w(WALLPAPER_LOG_TAG, "Wallpaper video playback failed: ${error.message}")
                        // Trigger recomposition into the static poster fallback if MediaPlayer fails.
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
            // FIT_XY matches the pre-computed aspect-scaled bounds supplied by WallpaperTransformFrame.
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

/**
 * Decoded wallpaper payload containing the ready-to-render [bitmap] (or first video poster frame),
 * detected [type], and upright source pixel dimensions ([width] × [height]).
 */
internal data class WallpaperFrame(
    val bitmap: Bitmap,
    val type: WallpaperMediaType,
    val width: Int,
    val height: Int
)

/**
 * Returns visually upright video dimensions by swapping [width] and [height] when the stream's
 * `METADATA_KEY_VIDEO_ROTATION` is 90° or 270°.
 */
internal fun uprightVideoDimensions(width: Int, height: Int, metadataRotation: Int): Pair<Int, Int> {
    val rotation = ((metadataRotation % 360) + 360) % 360
    return if (rotation == 90 || rotation == 270) height to width else width to height
}

/** Preview-specific alias forwarding to [decodeWallpaperFrame] with caller-supplied preview zoom. */
internal fun decodeWallpaperPreviewFrame(
    context: Context,
    uri: Uri,
    requestedWidth: Int,
    requestedHeight: Int,
    zoom: Float,
    rotation: Int,
    scaleMode: WallpaperScaleMode
): WallpaperFrame = decodeWallpaperFrame(context, uri, requestedWidth, requestedHeight, zoom, rotation, scaleMode)

/**
 * Decodes and caches a [WallpaperFrame] for [uri] scaled to fit the cover viewport while staying
 * within [WallpaperMediaLimits] and [MAX_WALLPAPER_DECODE_PIXELS].
 *
 * - **Video**: Uses `MediaMetadataRetriever` to validate duration/dimensions and extract a scaled
 *   sync keyframe poster at time `0` (`OPTION_CLOSEST_SYNC`).
 * - **Static image / GIF**: Uses `ImageDecoder.decodeBitmap` with `ALLOCATOR_SOFTWARE` and
 *   downsamples oversized sources to stay within the pixel budget.
 */
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
    // File size + mtime stamp so replacing a local wallpaper file invalidates the cached bitmap.
    val fileToken = if (uri.scheme == "file") {
        uri.path?.let { path ->
            File(path).let { file -> "${file.lastModified()}_${file.length()}" }
        }
    } else {
        null
    }
    // Composite key pairing URI, file edit token, framing parameters, and screen pixel size.
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
            // Extract frame 0 as a scaled poster bitmap shown while paused or in low-effects mode.
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
        // Downsample scale factor keeping decoded bitmaps under MAX_WALLPAPER_DECODE_PIXELS (1.8 MP).
        val pixelBudgetScale = sqrt(MAX_WALLPAPER_DECODE_PIXELS / sourcePixels.toFloat())
        // Use the tighter of viewport-zoom scale and pixel-budget scale so we never upscale or blow RAM.
        val decodeScale = min(targetScale, pixelBudgetScale).coerceAtMost(1f)
        // Software allocation allows safe Compose Canvas/blur rendering across secondary displays.
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

/** Reads the leading 4 KiB header bytes to sniff static image vs. GIF vs. video formats. */
private fun detectWallpaperType(context: Context, uri: Uri): WallpaperMediaType {
    val prefix = openWallpaperInputStream(context, uri)?.use { it.readNBytes(4096) }
        ?: error("Wallpaper file cannot be read")
    return WallpaperMediaLimits.detectMediaType(prefix)
}

/** Creates an [ImageDecoder.Source] supporting both local `file://` paths and `content://` URIs. */
private fun imageSource(context: Context, uri: Uri): ImageDecoder.Source =
    if (uri.scheme == "file") {
        ImageDecoder.createSource(File(uri.path ?: error("Wallpaper file path is missing")))
    } else {
        ImageDecoder.createSource(context.contentResolver, uri)
    }

/** Binds [retriever] to [uri], handling direct filesystem paths and `content://` providers. */
private fun setRetrieverDataSource(context: Context, retriever: MediaMetadataRetriever, uri: Uri) {
    if (uri.scheme == "file") retriever.setDataSource(uri.path ?: error("Video file path is missing"))
    else retriever.setDataSource(context, uri)
}

/** Opens a raw stream for [uri], dispatching between `File.inputStream()` and `ContentResolver`. */
private fun openWallpaperInputStream(context: Context, uri: Uri) =
    if (uri.scheme == "file") uri.path?.let { File(it).inputStream() }
    else context.contentResolver.openInputStream(uri)

/**
 * Hardware-accelerated `TextureView` hosting a muted, looping [MediaPlayer] bound to the view's
 * `SurfaceTexture` via an Android [Surface].
 *
 * `TextureView` (rather than `SurfaceView`) is required here so Compose `AndroidView` +
 * `WallpaperTransformFrame` can clip, rotate, scale, translate, and blur video frames within the
 * overlay's view hierarchy. MediaPlayer preparation runs asynchronously via `prepareAsync()`,
 * automatically starts/pauses on [setPlayback], releases native player/surface handles on
 * destruction or detachment, and reports playback errors back to Compose for poster fallback.
 */
private class WallpaperVideoTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    // Current video URI being played so changing wallpapers resets the player cleanly.
    private var sourceUri: Uri? = null
    // Desired play/pause state requested by Compose (synced once MediaPlayer finishes preparing).
    private var shouldPlay = false
    // Native Android media player decoding the muted looping wallpaper video.
    private var player: MediaPlayer? = null
    // Guards start()/pause() calls until onPrepared fires after prepareAsync().
    private var isPrepared = false
    // Android Surface wrapping TextureView's SurfaceTexture where MediaPlayer draws video frames.
    private var surface: Surface? = null
    // Notifies Compose when playback fails so the UI can fall back to the static poster frame.
    private var failureCallback: ((Throwable) -> Unit)? = null

    init {
        surfaceTextureListener = this
    }

    /** Updates the target video [uri] and desired [play] state, preparing or toggling playback. */
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
            // Wrap the TextureView's SurfaceTexture in a Surface so MediaPlayer renders into Compose's layer tree.
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

    /** Tears down all native MediaPlayer/Surface resources when `AndroidView.onRelease` runs. */
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

/**
 * Observes both Compose lifecycle (`Lifecycle.State.STARTED`) and device interactive state
 * (`PowerManager.isInteractive` via `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF`) so animated/video
 * wallpapers pause immediately when the screen turns off or the overlay stops.
 */
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

/**
 * Tracks Android Battery Saver state (`PowerManager.isPowerSaveMode` via
 * `ACTION_POWER_SAVE_MODE_CHANGED`) so video/GIF wallpapers degrade to static poster frames to
 * conserve power.
 */
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
