package com.tyejaedon.coverscreenos.ui.launcher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.ui.theme.CoverOSBatteryChargingColor
import com.tyejaedon.coverscreenos.ui.theme.CoverOSDestructiveColor
import com.tyejaedon.coverscreenos.ui.theme.oneui.CoverOneUIType
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiChip
import kotlin.math.roundToInt

internal data class BatteryStatusSnapshot(
    val levelPercent: Int,
    val isCharging: Boolean
)

@Composable
internal fun rememberBatteryStatus(): BatteryStatusSnapshot? {
    val context = LocalContext.current
    var status by remember(context) { mutableStateOf(readBatteryStatus(context)) }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                status = resolveBatteryStatusSnapshot(intent)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        if (stickyIntent != null) {
            status = resolveBatteryStatusSnapshot(stickyIntent)
        }

        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return status
}

private fun readBatteryStatus(context: Context): BatteryStatusSnapshot? {
    val stickyIntent = context.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        Context.RECEIVER_NOT_EXPORTED
    )
    return resolveBatteryStatusSnapshot(stickyIntent)
}

private fun resolveBatteryStatusSnapshot(intent: Intent?): BatteryStatusSnapshot? {
    intent ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null

    val levelPercent = ((level.toFloat() / scale.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

    return BatteryStatusSnapshot(levelPercent = levelPercent, isCharging = isCharging)
}

@Composable
internal fun BatteryStatusPill(status: BatteryStatusSnapshot, modifier: Modifier = Modifier) {
    val levelFraction = (status.levelPercent / 100f).coerceIn(0f, 1f)
    val trackColor = Color.White.copy(alpha = 0.18f)
    val fillColor = when {
        status.levelPercent <= 15 -> CoverOSDestructiveColor
        status.isCharging -> CoverOSBatteryChargingColor
        else -> Color.White
    }

    Row(
        modifier = modifier
            .oneUiChip()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(trackColor)
                .padding(1.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(levelFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(fillColor)
            )
        }

        Text(
            text = if (status.isCharging) "${status.levelPercent}% CHG" else "${status.levelPercent}%",
            style = CoverOneUIType.chipLabel,
            color = Color.White,
            maxLines = 1
        )
    }
}
