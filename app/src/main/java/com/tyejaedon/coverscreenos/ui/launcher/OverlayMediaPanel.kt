package com.tyejaedon.coverscreenos.ui

import com.tyejaedon.coverscreenos.ui.launcher.rememberPackageIconBitmap
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.models.CoverMediaCustomAction
import com.tyejaedon.coverscreenos.models.CoverMediaModel
import com.tyejaedon.coverscreenos.models.CoverMediaTransportAction
import com.tyejaedon.coverscreenos.models.CoverNotificationModel
import com.tyejaedon.coverscreenos.notifications.CoverNotificationListenerService
import com.tyejaedon.coverscreenos.ui.theme.oneui.CoverOneUIType
import com.tyejaedon.coverscreenos.ui.theme.oneui.CoverOneUITokens
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiElevatedPanel
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiIconSquircle
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiPanel
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiSlideDown
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiSlideUp
import java.util.Locale
import kotlinx.coroutines.delay

/** Fallback order of common music apps tried when no active media notification is present. */
private val PREFERRED_MEDIA_PACKAGES = listOf(
    "com.spotify.music",
    "com.apple.android.music",
    "com.google.android.apps.youtube.music",
    "com.pandora.android",
    "com.soundcloud.android",
    "com.amazon.mp3",
    "com.samsung.android.app.music"
)

/**
 * Responsive layout dimensions and transport icon sizes for [ExpandedNowPlayingPanel].
 */
internal data class ExpandedPlayerLayoutSpec(
    /** Fraction of available cover overlay width occupied by the expanded media player card. */
    val panelWidthFraction: Float,
    /** Fraction of available cover overlay height occupied by the expanded media player card. */
    val panelHeightFraction: Float,
    /** Inner horizontal content padding scaled to cover screen height. */
    val horizontalPadding: Dp,
    /** Inner vertical content padding scaled to cover screen height. */
    val verticalPadding: Dp,
    /** Vertical spacing between track header, metadata, and transport control rows. */
    val contentSpacing: Dp,
    /** Touch/visual square dimension for previous and next track buttons (>= 48.dp). */
    val sideControlSize: Dp,
    /** Larger touch/visual square dimension emphasizing the center play/pause button. */
    val primaryControlSize: Dp,
    /** Spacer height separating transport buttons from the bottom app-launch link. */
    val actionButtonTopSpacer: Dp,
    /** Vertical padding on the bottom "Open full media application" link. */
    val actionButtonVerticalPadding: Dp
)

/**
 * Resolves and launches a media player app from the cover overlay.
 *
 * Checks candidate packages in priority order: (1) the active media notification's source
 * package, (2) well-known music apps in [PREFERRED_MEDIA_PACKAGES], and (3) Android's default
 * [Intent.CATEGORY_APP_MUSIC] handler via `PackageManager.resolveActivity`.
 */
internal fun openMediaApplicationFromOverlay(
    context: Context,
    notifications: List<CoverNotificationModel>
): Boolean {
    val packageManager = context.packageManager
    // Package owning the active media notification, tried first before static fallback players.
    val mediaCandidateFromNotifications = notifications
        .asSequence()
        .filter { it.isMediaNotification }
        .map { it.packageName }
        .firstOrNull()

    // Deduplicated candidate package order combining active media notification source and preferred apps.
    val candidatePackages = buildList {
        mediaCandidateFromNotifications?.let { add(it) }
        addAll(PREFERRED_MEDIA_PACKAGES)
    }.distinct()

    candidatePackages.forEach { packageName ->
        val canLaunch = runCatching { packageManager.getLaunchIntentForPackage(packageName) != null }.getOrDefault(false)
        if (canLaunch) {
            val launched = CoverNotificationListenerService.launchNotificationSourceApp(context, packageName)
            if (launched) return true
        }
    }

    // Fall back to whichever app handles the standard Android music category intent.
    val categoryMusicIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_APP_MUSIC)
    }
    val fallbackPackage = runCatching {
        packageManager.resolveActivity(
            categoryMusicIntent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        )?.activityInfo?.packageName
    }.getOrNull()

    return fallbackPackage?.let { packageName ->
        CoverNotificationListenerService.launchNotificationSourceApp(context, packageName)
    } ?: false
}

/**
 * Compact pill-shaped now-playing strip anchored above the dock on the lockscreen tile.
 *
 * Displays active track artwork (validated via [usableMediaArtwork]) or the source app's icon,
 * title/artist metadata, and current playback state; tapping opens [ExpandedNowPlayingPanel]
 * when media is active or falls back to [onOpenMediaApp] when idle.
 */
@Composable
internal fun CompactNowPlayingCard(
    mediaNotification: CoverNotificationModel?,
    onOpenExpanded: () -> Unit,
    onOpenMediaApp: (() -> Boolean)? = null,
    modifier: Modifier = Modifier
) {
    // Full-pill shape matching One UI compact media strip geometry.
    val containerShape = RoundedCornerShape(percent = 50)
    // True when an active media session is bound; enables tapping to expand controls vs launching a player.
    val hasMedia = mediaNotification != null
    val isActionEnabled = hasMedia || onOpenMediaApp != null
    // Validated album art bitmap, fallback source app icon, and live play/pause state flag.
    val artwork = usableMediaArtwork(mediaNotification?.media?.artworkBitmap)
    val iconBitmap = mediaNotification?.let { rememberPackageIconBitmap(it.packageName) }
    val isPlaying = mediaNotification?.media?.isPlaying == true

    Surface(
        modifier = modifier
            .height(48.dp)
            .oneUiPanel(shape = containerShape)
            .clickable(enabled = isActionEnabled) {
                if (hasMedia) {
                    onOpenExpanded()
                } else {
                    onOpenMediaApp?.invoke()
                }
            },
        color = Color.Transparent,
        shape = containerShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (artwork != null || iconBitmap != null) {
                Image(
                    bitmap = artwork?.asImageBitmap() ?: requireNotNull(iconBitmap),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier.size(32.dp).oneUiIconSquircle(10.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaNotification?.media?.title ?: mediaNotification?.title ?: "Open a media app",
                    style = CoverOneUIType.tileTitle,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val artist = mediaNotification?.media?.artist ?: mediaNotification?.previewText.orEmpty()
                if (artist.isNotBlank()) {
                    Text(
                        text = artist,
                        style = CoverOneUIType.tileSubtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (hasMedia) "Expand media player" else null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Modal expanded media player overlay rendered over the lockscreen tile.
 *
 * Uses [AnimatedVisibility] with One UI slide transitions and adapts sizing via
 * [BoxWithConstraints] + [expandedPlayerLayoutSpec]. Reads `MediaController` transport state and
 * capability flags (`isPlaying`, `canPlayPause`, `canSkipPrevious`, `canSkipNext`) from
 * [CoverNotificationModel.media] and forwards user taps as [CoverMediaTransportAction] commands.
 */
@Composable
fun ExpandedNowPlayingPanel(
    mediaNotification: CoverNotificationModel?,
    visible: Boolean,
    onDismiss: () -> Unit,
    onLaunchSourceApp: (CoverNotificationModel) -> Unit,
    onAction: (CoverNotificationModel, CoverMediaTransportAction) -> Unit,
    onOpenNotification: (CoverNotificationModel) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && mediaNotification != null,
        enter = oneUiSlideUp(),
        exit = oneUiSlideDown(),
        modifier = modifier.fillMaxSize()
    ) {
        if (mediaNotification == null) return@AnimatedVisibility

        // Mirror active MediaSession transport capabilities so unsupported actions render disabled.
        val media = mediaNotification.media
        val isPlaying = media?.isPlaying == true
        val canPlayPause = media?.canPlayPause == true
        val canSkipPrevious = media?.canSkipPrevious == true
        val canSkipNext = media?.canSkipNext == true
        // Non-recycled album art used as blurred/scrimmed panel background; null falls back to dark surface.
        val artworkBitmap = usableMediaArtwork(media?.artworkBitmap)

        // Cached source app icon shown beside the "Open app" header button.
        val sourceIcon =
            rememberPackageIconBitmap(
                mediaNotification.packageName
            )
        // Track title and artist/preview fallback displayed in the expanded player header.
        val titleText = media?.title ?: mediaNotification.title
        val subtitleText = media?.artist ?: mediaNotification.previewText.ifBlank { "Open app" }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            // Responsive player dimensions matched to the current cover window height.
            val layoutSpec = expandedPlayerLayoutSpec(maxHeight)
            val expandedShape = RoundedCornerShape(CoverOneUITokens.radiusHero)
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(layoutSpec.panelWidthFraction)
                    .fillMaxHeight(layoutSpec.panelHeightFraction)
                    .oneUiElevatedPanel(shape = expandedShape)
                    .clip(expandedShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                color = Color.Transparent,
                shape = expandedShape
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().testTag("media_artwork_background")) {
                        MediaArtworkBackground(artworkBitmap)
                    }
                    MediaPlayerContent(
                        mediaNotification = mediaNotification,
                        media = media,
                        sourceIcon = sourceIcon,
                        titleText = titleText,
                        subtitleText = subtitleText,
                        layoutSpec = layoutSpec,
                        isPlaying = isPlaying,
                        canPlayPause = canPlayPause,
                        canSkipPrevious = canSkipPrevious,
                        canSkipNext = canSkipNext,
                        onDismiss = onDismiss,
                        onLaunchSourceApp = onLaunchSourceApp,
                        onAction = onAction,
                        onOpenNotification = onOpenNotification
                    )
                }
            }
        }
    }
}

/**
 * Scrollable inner content of [ExpandedNowPlayingPanel], including header actions, track
 * metadata, transport controls (`PREVIOUS`, `PLAY_PAUSE`, `NEXT`), and full-app launch action.
 */
@Composable
private fun MediaPlayerContent(
    mediaNotification: CoverNotificationModel,
    media: CoverMediaModel?,
    sourceIcon: ImageBitmap?,
    titleText: String,
    subtitleText: String,
    layoutSpec: ExpandedPlayerLayoutSpec,
    isPlaying: Boolean,
    canPlayPause: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onDismiss: () -> Unit,
    onLaunchSourceApp: (CoverNotificationModel) -> Unit,
    onAction: (CoverNotificationModel, CoverMediaTransportAction) -> Unit,
    onOpenNotification: (CoverNotificationModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = layoutSpec.horizontalPadding, vertical = layoutSpec.verticalPadding),
        verticalArrangement = Arrangement.spacedBy(layoutSpec.contentSpacing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onLaunchSourceApp(mediaNotification) }
                    .heightIn(min = 32.dp)
                    .padding(horizontal = 2.dp)
            ) {
                if (sourceIcon != null) {
                    Image(bitmap = sourceIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(text = "Open app", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = titleText,
            style = CoverOneUIType.sectionTitle,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitleText,
            style = CoverOneUIType.tileSubtitle,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (media != null && media.durationMs > 0L && media.positionMs >= 0L) {
            MediaProgressSection(
                mediaKey = "${mediaNotification.notificationKey}:$titleText:${media.durationMs}",
                media = media,
                onSeekTo = { positionMs ->
                    onAction(mediaNotification, CoverMediaTransportAction.SeekTo(positionMs))
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaControlIconButton(
                icon = Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                enabled = canSkipPrevious,
                onClick = { onAction(mediaNotification, CoverMediaTransportAction.PREVIOUS) },
                modifier = Modifier.size(layoutSpec.sideControlSize)
            )
            MediaControlIconButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                enabled = canPlayPause,
                onClick = { onAction(mediaNotification, CoverMediaTransportAction.PLAY_PAUSE) },
                modifier = Modifier.size(layoutSpec.primaryControlSize)
            )
            MediaControlIconButton(
                icon = Icons.Filled.SkipNext,
                contentDescription = "Next",
                enabled = canSkipNext,
                onClick = { onAction(mediaNotification, CoverMediaTransportAction.NEXT) },
                modifier = Modifier.size(layoutSpec.sideControlSize)
            )
        }
        val customActions = media?.customActions.orEmpty()
        if (customActions.isNotEmpty()) {
            MediaCustomActionsRow(
                customActions = customActions,
                onAction = { transportAction ->
                    onAction(mediaNotification, transportAction)
                }
            )
        }
        Spacer(modifier = Modifier.height(layoutSpec.actionButtonTopSpacer))
        Text(
            text = "Open full media application",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .clickable { onOpenNotification(mediaNotification) }
                .heightIn(min = 48.dp)
                .padding(vertical = layoutSpec.actionButtonVerticalPadding)
                .align(Alignment.CenterHorizontally)
        )
    }
}

/**
 * Selects an [ExpandedPlayerLayoutSpec] scaled to the cover viewport [height] so controls keep
 * comfortable touch targets (`>= 48.dp`) on compact cover displays without vertical clipping.
 */
internal fun expandedPlayerLayoutSpec(height: Dp): ExpandedPlayerLayoutSpec = when {
    height <= 340.dp -> ExpandedPlayerLayoutSpec(
        panelWidthFraction = 0.97f,
        panelHeightFraction = 0.72f,
        horizontalPadding = 8.dp,
        verticalPadding = 4.dp,
        contentSpacing = 4.dp,
        sideControlSize = 48.dp,
        primaryControlSize = 48.dp,
        actionButtonTopSpacer = 2.dp,
        actionButtonVerticalPadding = 4.dp
    )
    height <= 420.dp -> ExpandedPlayerLayoutSpec(
        panelWidthFraction = 0.96f,
        panelHeightFraction = 0.78f,
        horizontalPadding = 10.dp,
        verticalPadding = 6.dp,
        contentSpacing = 4.dp,
        sideControlSize = 48.dp,
        primaryControlSize = 50.dp,
        actionButtonTopSpacer = 4.dp,
        actionButtonVerticalPadding = 6.dp
    )
    else -> ExpandedPlayerLayoutSpec(
        panelWidthFraction = 0.94f,
        panelHeightFraction = 0.84f,
        horizontalPadding = 14.dp,
        verticalPadding = 12.dp,
        contentSpacing = 8.dp,
        sideControlSize = 54.dp,
        primaryControlSize = 70.dp,
        actionButtonTopSpacer = 8.dp,
        actionButtonVerticalPadding = 10.dp
    )
}

/**
 * Full-bleed album artwork (or dark neutral fallback) layered under a vertical dark scrim so
 * foreground transport icons and track labels remain legible.
 */
@Composable
private fun MediaArtworkBackground(artwork: Bitmap?) {
    if (artwork != null) {
        Image(
            bitmap = artwork.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF202124)))
    }
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.65f), Color.Black.copy(alpha = 0.82f))
            )
        )
    )
}

/**
 * Guards against recycled or zero-dimension [Bitmap] instances before converting to `ImageBitmap`.
 */
internal fun usableMediaArtwork(artwork: Bitmap?): Bitmap? =
    artwork?.takeUnless { it.isRecycled || it.width <= 0 || it.height <= 0 }

/**
 * Press-scaled (`0.94f`) and haptic-enabled transport control button for [MediaPlayerContent].
 *
 * Dims icon alpha to `0.45f` and suppresses press animation when [enabled] is `false`.
 */
@Composable
private fun MediaControlIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Remembered interaction source tracking pointer press state for tactile button feedback.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    // Subtle press-down squeeze animation (0.94x) active only when the transport button is enabled.
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "mediaControlScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.45f),
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Interactive seekbar (when [CoverMediaModel.canSeek] is `true`) or read-only progress bar with
 * live-interpolated elapsed time and total track duration.
 */
@Composable
private fun MediaProgressSection(
    mediaKey: String,
    media: CoverMediaModel,
    onSeekTo: (Long) -> Unit
) {
    var nowElapsedMs by remember(
        mediaKey,
        media.positionMs,
        media.positionUpdateTimeMs,
        media.isPlaying
    ) {
        mutableLongStateOf(SystemClock.elapsedRealtime())
    }

    LaunchedEffect(
        mediaKey,
        media.isPlaying,
        media.positionMs,
        media.positionUpdateTimeMs,
        media.playbackSpeed,
        media.durationMs
    ) {
        if (!media.isPlaying || media.durationMs <= 0L || media.positionMs < 0L) return@LaunchedEffect
        while (true) {
            delay(500L)
            nowElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    val livePositionMs = computeLivePlaybackPositionMs(
        positionMs = media.positionMs,
        positionUpdateTimeMs = media.positionUpdateTimeMs,
        playbackSpeed = media.playbackSpeed,
        durationMs = media.durationMs,
        isPlaying = media.isPlaying,
        nowElapsedMs = nowElapsedMs
    )

    var isScrubbing by remember(mediaKey) { mutableStateOf(false) }
    var scrubPositionMs by remember(mediaKey) { mutableFloatStateOf(livePositionMs.toFloat()) }
    val displayedPositionMs = if (isScrubbing) {
        scrubPositionMs.toLong().coerceIn(0L, media.durationMs)
    } else {
        livePositionMs
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        if (media.canSeek) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 24.dp) {
                Slider(
                    value = displayedPositionMs.toFloat(),
                    onValueChange = { newValue ->
                        isScrubbing = true
                        scrubPositionMs = newValue
                    },
                    onValueChangeFinished = {
                        val targetMs = scrubPositionMs.toLong().coerceIn(0L, media.durationMs)
                        isScrubbing = false
                        onSeekTo(targetMs)
                    },
                    valueRange = 0f..media.durationMs.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.30f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                )
            }
        } else {
            val fraction = (displayedPositionMs.toFloat() / media.durationMs.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(Color.White)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatMediaDurationMs(displayedPositionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                text = formatMediaDurationMs(media.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Horizontally scrollable row of session/notification custom controls (such as shuffle, repeat,
 * or favorite) mirrored from the active media notification.
 */
@Composable
private fun MediaCustomActionsRow(
    customActions: List<CoverMediaCustomAction>,
    onAction: (CoverMediaTransportAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        customActions.forEach { customAction ->
            MediaCustomActionButton(
                customAction = customAction,
                onClick = { onAction(customAction.transportAction) }
            )
        }
    }
}

/**
 * Tactile 48.dp-minimum button for an extra media control ([CoverMediaCustomAction]), rendering
 * its source app icon tinted white or falling back to its text label when no icon is available.
 */
@Composable
private fun MediaCustomActionButton(
    customAction: CoverMediaCustomAction,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "mediaCustomActionScale"
    )
    val iconBitmap = usableMediaArtwork(customAction.iconBitmap)

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Icon(
                bitmap = iconBitmap.asImageBitmap(),
                contentDescription = customAction.label,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                text = customAction.label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Interpolates the current playback position from a `PlaybackState` snapshot using elapsed
 * monotonic time and playback speed, clamped to `[0, durationMs]` when duration is known.
 */
internal fun computeLivePlaybackPositionMs(
    positionMs: Long,
    positionUpdateTimeMs: Long,
    playbackSpeed: Float,
    durationMs: Long,
    isPlaying: Boolean,
    nowElapsedMs: Long = SystemClock.elapsedRealtime()
): Long {
    if (positionMs < 0L) return 0L
    val projected = if (!isPlaying || playbackSpeed <= 0f || positionUpdateTimeMs <= 0L) {
        positionMs
    } else {
        val elapsedMs = (nowElapsedMs - positionUpdateTimeMs).coerceAtLeast(0L)
        positionMs + (elapsedMs * playbackSpeed).toLong()
    }
    return if (durationMs > 0L) {
        projected.coerceIn(0L, durationMs)
    } else {
        projected.coerceAtLeast(0L)
    }
}

/** Formats a millisecond duration as `m:ss` or `h:mm:ss` for media progress labels. */
internal fun formatMediaDurationMs(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L)) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
