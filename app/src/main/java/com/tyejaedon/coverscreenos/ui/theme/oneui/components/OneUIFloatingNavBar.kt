package com.tyejaedon.coverscreenos.ui.theme.oneui.components

import android.content.res.Configuration
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUITheme
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUIPressable
import kotlin.math.roundToInt

/**
 * Navigation destination metadata displayed inside [OneUIFloatingNavBar].
 *
 * @property route Unique navigation route key matched against `selectedRoute`.
 * @property title Short tab label rendered below the icon (ellipsized to 1 line).
 * @property icon Material/custom [ImageVector] shown in the tab slot.
 */
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
    // 0-based slot index of the currently active tab; defaults to the first tab if no route matches.
    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    // Total tab count clamped >= 1 so dividing the bar width never causes division by zero.
    val itemCount = items.size.coerceAtLeast(1)
    // True when hosted on a short cover display (<= 480dp tall) so the bar trims height and padding.
    val isCompactHeight = LocalConfiguration.current.screenHeightDp <= 480
    // Adaptive bar height (52dp compact cover vs 60dp standard phone).
    val barHeight = if (isCompactHeight) 52.dp else OneUITheme.dimensions.floatingNavHeight
    // Outer horizontal inset keeping the floating pill centered away from screen corners.
    val outerHorizontalPadding = if (isCompactHeight) 12.dp else 24.dp
    // Tab icon size scaled slightly down on compact displays to preserve vertical breathing room.
    val iconSize = if (isCompactHeight) 20.dp else 22.dp
    // Tab caption font size paired with iconSize so labels stay legible without wrapping.
    val labelSize = if (isCompactHeight) 10.sp else 11.sp

    Surface(
        shape = CircleShape,
        color = OneUITheme.colors.cardSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, if (OneUITheme.colors.isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)),
        shadowElevation = 4.dp,
        modifier = modifier
            .height(barHeight)
            .padding(horizontal = outerHorizontalPadding)
            .fillMaxWidth()
    ) {
        // BoxWithConstraints exposes measured inner width so each tab slot and the sliding pill share exact geometry.
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
            // Equal width allotted to each navigation tab slot inside the pill container.
            val itemWidth = maxWidth / itemCount
            // Destination horizontal X coordinate (in Dp) for the active tab's highlight pill.
            val targetOffset = itemWidth * selectedIndex

            // Smooth spring-animated X offset (in raw dp Float) gliding the highlight pill between tabs.
            val animatedOffset by animateFloatAsState(
                targetValue = targetOffset.value,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f),
                label = "navIndicatorOffset"
            )

            // Tactile Sliding Pill Indicator
            // Lambda-based offset defers pixel translation to the layout placement phase, avoiding recompositions per frame.
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
                    // True when this tab corresponds to the active destination.
                    val isSelected = index == selectedIndex
                    // Per-tab touch tracker linking press scale feedback (oneUIPressable) with tap clicks.
                    val interactionSource = remember { MutableInteractionSource() }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            // Suppress Material ripple (indication = null) in favor of One UI tactile press scaling.
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
                            modifier = Modifier.size(iconSize)
                        )
                        Text(
                            text = item.title,
                            fontSize = labelSize,
                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                            color = if (isSelected) OneUITheme.colors.accent else OneUITheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dual-mode `@Preview` verifying [OneUIFloatingNavBar] pill elevation, hairline border,
 * and active tab indicator across Light and OLED Dark themes.
 */
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