package com.tyejaedon.coverscreenos.ui.theme.oneui.components

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUITheme
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUIPressable
import kotlin.math.roundToInt

data class OneUINavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

/**
 * Signature Samsung One UI 8 Floating Bottom Navigation Bar.
 *
 * - Suspended 60dp pill-shaped container (CircleShape).
 * - Frosted glass translucency with hairline border.
 * - Tactile spring-animated sliding pill indicator (damping = 0.82, stiffness = 400).
 */
@Composable
fun OneUIFloatingNavBar(
    items: List<OneUINavItem>,
    selectedRoute: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    val itemCount = items.size.coerceAtLeast(1)

    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(40.dp)
    } else {
        Modifier
    }

    Surface(
        shape = CircleShape,
        color = OneUITheme.colors.cardSurface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, if (OneUITheme.colors.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)),
        shadowElevation = 0.dp,
        modifier = modifier
            .height(OneUITheme.dimensions.floatingNavHeight)
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .then(blurModifier)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
            val itemWidth = maxWidth / itemCount
            val targetOffset = itemWidth * selectedIndex

            val animatedOffset by animateFloatAsState(
                targetValue = targetOffset.value,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f),
                label = "navIndicatorOffset"
            )

            // Tactile Sliding Pill Indicator
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = (animatedOffset * density).roundToInt(), y = 0) }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(OneUITheme.colors.accent.copy(alpha = 0.16f))
            )

            // Items Row
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    val interactionSource = remember { MutableInteractionSource() }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .oneUIPressable(interactionSource = interactionSource)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                onItemSelected(item.route)
                            }
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (isSelected) OneUITheme.colors.accent else OneUITheme.colors.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = item.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                            color = if (isSelected) OneUITheme.colors.accent else OneUITheme.colors.textSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Light Mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "OLED Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun OneUIFloatingNavBarPreview() {
    OneUITheme {
        OneUIFloatingNavBar(
            items = listOf(
                OneUINavItem("dashboard", "Home", Icons.Filled.DashboardCustomize),
                OneUINavItem("customize", "Style", Icons.Filled.Palette),
                OneUINavItem("permissions", "Access", Icons.Filled.VerifiedUser),
                OneUINavItem("about", "Info", Icons.Filled.Info)
            ),
            selectedRoute = "dashboard",
            onItemSelected = {}
        )
    }
}
