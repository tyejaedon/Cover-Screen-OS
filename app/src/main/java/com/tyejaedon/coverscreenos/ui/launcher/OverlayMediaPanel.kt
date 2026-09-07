package com.tyejaedon.coverscreenos.ui

import com.tyejaedon.coverscreenos.ui.launcher.rememberPackageIconBitmap
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.models.CoverNotificationModel
import com.tyejaedon.coverscreenos.services.notifications.CoverNotificationListenerService
import com.tyejaedon.coverscreenos.ui.theme.coverGlassSurface
import java.util.Locale

private const val COMPACT_CARD_MIN_ART_EDGE_PX = 180

private val PREFERRED_MEDIA_PACKAGES = listOf(
    "com.spotify.music",
    "com.apple.android.music",
    "com.google.android.apps.youtube.music",
    "com.pandora.android",
    "com.soundcloud.android",
    "com.amazon.mp3",
    "com.samsung.android.app.music"
)

private data class ExpandedPlayerLayoutSpec(
    val panelWidthFraction: Float,
    val panelHeightFraction: Float,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val contentSpacing: Dp,
    val showArtwork: Boolean,
    val artworkWidthFraction: Float,
    val artworkBottomPadding: Dp,
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
    val containerShape = RoundedCornerShape(16.dp)
    val hasMedia = mediaNotification != null
    val isActionEnabled = hasMedia || onOpenMediaApp != null

    Surface(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(containerShape)
            .coverGlassSurface(
                color = Color.White.copy(alpha = 0.12f),
                borderColor = Color.White.copy(alpha = 0.22f),
                shape = containerShape
            )
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
        Box(modifier = Modifier.fillMaxWidth()) {
            if (!hasMedia) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.15f))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Open a media app",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 1
                        )
                    }
                }
            } else {
                val iconBitmap =
                    rememberPackageIconBitmap(
                        mediaNotification.packageName
                    )
                val artworkBitmap = mediaNotification.media?.artworkBitmap
                val displayArtwork = remember(artworkBitmap) {
                    artworkBitmap?.takeIf { minOf(it.width, it.height) >= COMPACT_CARD_MIN_ART_EDGE_PX }
                }
                val titleText = mediaNotification.media?.title ?: mediaNotification.title
                val subtitleText = mediaNotification.media?.artist ?: mediaNotification.previewText.ifBlank { "Playing" }

                Box(modifier = Modifier.fillMaxWidth()) {
                    if (displayArtwork != null) {
                        Image(
                            bitmap = displayArtwork.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Low,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                                    )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF2C3E50).copy(alpha = 0.8f),
                                            Color(0xFF000000).copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        )
                    }

                    if (iconBitmap != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp)
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(2.dp)
                        ) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
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
        enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
            slideInVertically(animationSpec = tween(durationMillis = 260), initialOffsetY = { it / 3 }),
        exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
            slideOutVertically(animationSpec = tween(durationMillis = 220), targetOffsetY = { it / 3 }),
        modifier = modifier.fillMaxSize()
    ) {
        if (mediaNotification == null) return@AnimatedVisibility

        val actionLabels = remember(mediaNotification.media) {
            mediaNotification.media?.actionLabels.orEmpty().filter { it.isNotBlank() }.distinct()
        }
        val previousAction = remember(actionLabels) {
            findMediaActionLabel(actionLabels, "previous", "prev", "rewind", "back")
        }
        val playPauseAction = remember(mediaNotification.media, actionLabels) {
            mediaNotification.media?.playPauseActionLabel?.trim()?.takeUnless { it.isEmpty() }
                ?: findMediaActionLabel(actionLabels, "play", "pause", "resume")
                ?: actionLabels.firstOrNull()
        }
        val nextAction = remember(actionLabels) {
            findMediaActionLabel(actionLabels, "next", "skip", "forward")
        }
        val isPauseAction = remember(playPauseAction) { isPauseLikeActionLabel(playPauseAction) }
        val artworkBitmap = mediaNotification.media?.artworkBitmap

        val sourceIcon =
            rememberPackageIconBitmap(
                mediaNotification.packageName
            )
        val titleText = mediaNotification.media?.title ?: mediaNotification.title
        val subtitleText = mediaNotification.media?.artist ?: mediaNotification.previewText.ifBlank { "Open app" }
        val density = LocalDensity.current
        val containerHeightPx = LocalWindowInfo.current.containerSize.height
        val containerHeightDp = with(density) { containerHeightPx.toDp() }
        val layoutSpec = remember(containerHeightPx) {
            if (containerHeightDp <= 340.dp) {
                ExpandedPlayerLayoutSpec(
                    panelWidthFraction = 0.97f,
                    panelHeightFraction = 0.72f,
                    horizontalPadding = 8.dp,
                    verticalPadding = 6.dp,
                    contentSpacing = 5.dp,
                    showArtwork = false,
                    artworkWidthFraction = 0.48f,
                    artworkBottomPadding = 6.dp,
                    sideControlSize = 42.dp,
                    primaryControlSize = 52.dp,
                    actionButtonTopSpacer = 4.dp,
                    actionButtonVerticalPadding = 6.dp
                )
            } else if (containerHeightDp <= 420.dp) {
                ExpandedPlayerLayoutSpec(
                    panelWidthFraction = 0.96f,
                    panelHeightFraction = 0.78f,
                    horizontalPadding = 10.dp,
                    verticalPadding = 8.dp,
                    contentSpacing = 6.dp,
                    showArtwork = true,
                    artworkWidthFraction = 0.52f,
                    artworkBottomPadding = 8.dp,
                    sideControlSize = 46.dp,
                    primaryControlSize = 58.dp,
                    actionButtonTopSpacer = 6.dp,
                    actionButtonVerticalPadding = 8.dp
                )
            } else {
                ExpandedPlayerLayoutSpec(
                    panelWidthFraction = 0.94f,
                    panelHeightFraction = 0.84f,
                    horizontalPadding = 14.dp,
                    verticalPadding = 12.dp,
                    contentSpacing = 8.dp,
                    showArtwork = true,
                    artworkWidthFraction = 0.62f,
                    artworkBottomPadding = 12.dp,
                    sideControlSize = 54.dp,
                    primaryControlSize = 70.dp,
                    actionButtonTopSpacer = 8.dp,
                    actionButtonVerticalPadding = 10.dp
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(layoutSpec.panelWidthFraction)
                    .fillMaxHeight(layoutSpec.panelHeightFraction)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            if (sourceIcon != null) {
                                Image(
                                    bitmap = sourceIcon,
                                    contentDescription = "App Icon",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "Open app",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = onDismiss
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (layoutSpec.showArtwork && artworkBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = layoutSpec.artworkBottomPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = artworkBitmap.asImageBitmap(),
                                contentDescription = "Album Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth(layoutSpec.artworkWidthFraction)
                                    .aspectRatio(1f)
                            )
                        }
                    }

                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = subtitleText,
                        style = if (layoutSpec.showArtwork) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onOpenNotification(mediaNotification) }
                            .padding(vertical = layoutSpec.actionButtonVerticalPadding)
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

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
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
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

