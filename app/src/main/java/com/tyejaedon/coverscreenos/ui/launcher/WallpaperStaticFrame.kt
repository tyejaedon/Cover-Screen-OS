package com.tyejaedon.coverscreenos.ui.launcher

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.datastore.WallpaperScaleMode
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun WallpaperStaticFrame(
    bitmap: Bitmap,
    scaleMode: WallpaperScaleMode,
    cropX: Float,
    cropY: Float,
    zoom: Float,
    rotation: Int,
    dimAmount: Float,
    blurRadiusDp: Float,
    modifier: Modifier = Modifier
) {
    WallpaperTransformFrame(
        sourceWidth = bitmap.width,
        sourceHeight = bitmap.height,
        scaleMode = scaleMode,
        cropX = cropX,
        cropY = cropY,
        zoom = zoom,
        rotation = rotation,
        dimAmount = dimAmount,
        blurRadiusDp = blurRadiusDp,
        modifier = modifier
    ) { contentModifier ->
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = contentModifier
        )
    }
}

@Composable
internal fun WallpaperTransformFrame(
    sourceWidth: Int,
    sourceHeight: Int,
    scaleMode: WallpaperScaleMode,
    cropX: Float,
    cropY: Float,
    zoom: Float,
    rotation: Int,
    dimAmount: Float,
    blurRadiusDp: Float,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier.clipToBounds().background(Color.Black),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val rotatedWidth = if (rotation % 180 == 0) sourceWidth else sourceHeight
        val rotatedHeight = if (rotation % 180 == 0) sourceHeight else sourceWidth
        val widthScale = widthPx / rotatedWidth.coerceAtLeast(1)
        val heightScale = heightPx / rotatedHeight.coerceAtLeast(1)
        val baseScale = if (scaleMode == WallpaperScaleMode.CROP) max(widthScale, heightScale) else min(widthScale, heightScale)
        val scaledWidth = rotatedWidth * baseScale * zoom
        val scaledHeight = rotatedHeight * baseScale * zoom
        val offsetX = ((scaledWidth - widthPx) / 2f).coerceAtLeast(0f) * cropX.coerceIn(-1f, 1f)
        val offsetY = ((scaledHeight - heightPx) / 2f).coerceAtLeast(0f) * cropY.coerceIn(-1f, 1f)
        val unrotatedWidthPx = sourceWidth * baseScale
        val unrotatedHeightPx = sourceHeight * baseScale
        val transform = with(density) {
            Modifier
                .requiredSize(unrotatedWidthPx.toDp(), unrotatedHeightPx.toDp())
        }
            .graphicsLayer {
                rotationZ = rotation.toFloat()
                scaleX = zoom
                scaleY = zoom
                translationX = offsetX
                translationY = offsetY
            }
            .blur(
                blurRadiusDp.coerceIn(0f, 18f).dp,
                edgeTreatment = BlurredEdgeTreatment.Unbounded
            )
        content(transform)
        if (dimAmount > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = dimAmount.coerceIn(0f, 0.8f)))
            )
        }
    }
}
