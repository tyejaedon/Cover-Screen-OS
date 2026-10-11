package com.tyejaedon.coverscreenos.ui

import com.tyejaedon.coverscreenos.ui.launcher.rememberPackageIconBitmap
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.tyejaedon.coverscreenos.models.CoverNotificationModel
import com.tyejaedon.coverscreenos.models.CoverMediaModel
import com.tyejaedon.coverscreenos.services.notifications.CoverNotificationListenerService
import com.tyejaedon.coverscreenos.ui.theme.oneui.CoverOneUIType
import com.tyejaedon.coverscreenos.ui.theme.oneui.CoverOneUITokens
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiElevatedPanel
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiIconSquircle
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiPanel
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiSlideDown
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiSlideUp
import java.util.Locale

private val PREFERRED_MEDIA_PACKAGES = listOf(
    "com.spotify.music",
    "com.apple.android.music",
    "com.google.android.apps.youtube.music",
    "com.pandora.android",
    "com.soundcloud.android",
    "com.amazon.mp3",
    "com.samsung.android.app.music"
)

internal data class ExpandedPlayerLayoutSpec(
    val panelWidthFraction: Float,
    val panelHeightFraction: Float,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val contentSpacing: Dp,
    val sideControlSize: Dp,
    val primaryControlSize: Dp,
    val actionButtonTopSpacer: Dp,
    val actionButtonVerticalPadding: Dp
)

internal fun openMediaApplicationFromOverlay(
    context: Context,
    notifications: List<CoverNotificationModel>
): Boolean {
    val packageManager = context.packageManager
    val mediaCandidateFromNotifications = notifications
        .asSequence()
        .filter { it.isMediaNotification }
        .map { it.packageName }
        .firstOrNull()

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

@Composable
internal fun CompactNowPlayingCard(
    mediaNotification: CoverNotificationModel?,
    onOpenExpanded: () -> Unit,
    onOpenMediaApp: (() -> Boolean)? = null,
    modifier: Modifier = Modifier
) {
    val containerShape = RoundedCornerShape(percent = 50)
    val hasMedia = mediaNotification != null
    val isActionEnabled = hasMedia || onOpenMediaApp != null
    val artwork = usableMediaArtwork(mediaNotification?.media?.artworkBitmap)
    val iconBitmap = mediaNotification?.let { rememberPackageIconBitmap(it.packageName) }
    val playPauseAction = resolveMediaPlayPauseAction(mediaNotification?.media)

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
                imageVector = if (isPauseLikeActionLabel(playPauseAction)) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (hasMedia) "Expand media player" else null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ExpandedNowPlayingPanel(
    mediaNotification: CoverNotificationModel?,
    visible: Boolean,
    onDismiss: () -> Unit,
    onLaunchSourceApp: (CoverNotificationModel) -> Unit,
    onAction: (CoverNotificationModel, String?) -> Unit,
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

        val actionLabels = remember(mediaNotification.media) {
            mediaNotification.media?.actionLabels.orEmpty().filter { it.isNotBlank() }.distinct()
        }
        val previousAction = remember(actionLabels) {
            findMediaActionLabel(actionLabels, "previous", "prev", "rewind", "back")
        }
        val playPauseAction = resolveMediaPlayPauseAction(mediaNotification.media)
        val nextAction = remember(actionLabels) {
            findMediaActionLabel(actionLabels, "next", "skip", "forward")
        }
        val isPauseAction = remember(playPauseAction) { isPauseLikeActionLabel(playPauseAction) }
        val artworkBitmap = usableMediaArtwork(mediaNotification.media?.artworkBitmap)

        val sourceIcon =
            rememberPackageIconBitmap(
                mediaNotification.packageName
            )
        val titleText = mediaNotification.media?.title ?: mediaNotification.title
        val subtitleText = mediaNotification.media?.artist ?: mediaNotification.previewText.ifBlank { "Open app" }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
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
                        sourceIcon = sourceIcon,
                        titleText = titleText,
                        subtitleText = subtitleText,
                        layoutSpec = layoutSpec,
                        previousAction = previousAction,
                        playPauseAction = playPauseAction,
                        nextAction = nextAction,
                        isPauseAction = isPauseAction,
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

@Composable
private fun MediaPlayerContent(
    mediaNotification: CoverNotificationModel,
    sourceIcon: ImageBitmap?,
    titleText: String,
    subtitleText: String,
    layoutSpec: ExpandedPlayerLayoutSpec,
    previousAction: String?,
    playPauseAction: String?,
    nextAction: String?,
    isPauseAction: Boolean,
    onDismiss: () -> Unit,
    onLaunchSourceApp: (CoverNotificationModel) -> Unit,
    onAction: (CoverNotificationModel, String?) -> Unit,
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
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 2.dp)
            ) {
                if (sourceIcon != null) {
                    Image(bitmap = sourceIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(text = "Open app", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
            IconButton(onClick = onDismiss) {
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
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaControlIconButton(
                icon = Icons.Filled.SkipPrevious,
                contentDescription = previousAction ?: "Previous",
                enabled = previousAction != null,
                onClick = { previousAction?.let { onAction(mediaNotification, it) } },
                modifier = Modifier.size(layoutSpec.sideControlSize)
            )
            MediaControlIconButton(
                icon = if (isPauseAction) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = playPauseAction ?: "Play/Pause",
                enabled = playPauseAction != null,
                onClick = { playPauseAction?.let { onAction(mediaNotification, it) } },
                modifier = Modifier.size(layoutSpec.primaryControlSize)
            )
            MediaControlIconButton(
                icon = Icons.Filled.SkipNext,
                contentDescription = nextAction ?: "Next",
                enabled = nextAction != null,
                onClick = { nextAction?.let { onAction(mediaNotification, it) } },
                modifier = Modifier.size(layoutSpec.sideControlSize)
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

internal fun expandedPlayerLayoutSpec(height: Dp): ExpandedPlayerLayoutSpec = when {
    height <= 340.dp -> ExpandedPlayerLayoutSpec(
        panelWidthFraction = 0.97f,
        panelHeightFraction = 0.72f,
        horizontalPadding = 8.dp,
        verticalPadding = 6.dp,
        contentSpacing = 5.dp,
        sideControlSize = 48.dp,
        primaryControlSize = 52.dp,
        actionButtonTopSpacer = 4.dp,
        actionButtonVerticalPadding = 6.dp
    )
    height <= 420.dp -> ExpandedPlayerLayoutSpec(
        panelWidthFraction = 0.96f,
        panelHeightFraction = 0.78f,
        horizontalPadding = 10.dp,
        verticalPadding = 8.dp,
        contentSpacing = 6.dp,
        sideControlSize = 48.dp,
        primaryControlSize = 58.dp,
        actionButtonTopSpacer = 6.dp,
        actionButtonVerticalPadding = 8.dp
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

internal fun usableMediaArtwork(artwork: Bitmap?): Bitmap? =
    artwork?.takeUnless { it.isRecycled || it.width <= 0 || it.height <= 0 }

internal fun resolveMediaPlayPauseAction(media: CoverMediaModel?): String? =
    media?.playPauseActionLabel?.trim()?.takeIf { it.isNotEmpty() }
        ?: findMediaActionLabel(media?.actionLabels.orEmpty(), "play", "pause", "resume")

@Composable
private fun MediaControlIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
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

private fun findMediaActionLabel(actionLabels: List<String>, vararg keywords: String): String? {
    if (actionLabels.isEmpty()) return null
    val normalizedKeywords = keywords.map { it.lowercase(Locale.getDefault()) }
    return actionLabels.firstOrNull { label ->
        val normalizedLabel = label.lowercase(Locale.getDefault())
        normalizedKeywords.any { keyword -> normalizedLabel.contains(keyword) }
    }
}

private fun isPauseLikeActionLabel(actionLabel: String?): Boolean {
    val normalized = actionLabel?.trim()?.lowercase(Locale.getDefault()).orEmpty()
    return normalized.contains("pause") || normalized.contains("stop")
}
