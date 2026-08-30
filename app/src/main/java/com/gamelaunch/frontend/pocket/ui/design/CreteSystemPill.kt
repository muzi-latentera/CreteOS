package com.gamelaunch.frontend.pocket.ui.design

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Floating system pill — top-right corner, like WinHanced.
 * Shows: clock | wifi | battery
 * No "CreteOS" wordmark. No full-width bar.
 */
@Composable
fun CreteSystemPill(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val layout = rememberCreteLayoutMetrics()
    var batteryPct  by remember { mutableIntStateOf(100) }
    var isCharging  by remember { mutableStateOf(false) }
    var wifiLevel   by remember { mutableIntStateOf(-1) }
    var clock       by remember { mutableStateOf(currentTime()) }
    // Battery + WiFi receiver
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                        batteryPct = (level * 100 / scale)
                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                     status == BatteryManager.BATTERY_STATUS_FULL
                    }
                    WifiManager.RSSI_CHANGED_ACTION,
                    WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                        val wm = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                        wifiLevel = wm?.let { WifiManager.calculateSignalLevel(it.connectionInfo.rssi, 4) } ?: -1
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter)
        // Clock tick
        val timer = Timer()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() { clock = currentTime() }
        }, 0L, 30_000L)
        onDispose {
            context.unregisterReceiver(receiver)
            timer.cancel()
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(CreteDS.radiusPill))
            .background(CreteDS.pillBg)
            .border(0.5.dp, CreteDS.border, RoundedCornerShape(CreteDS.radiusPill))
            .padding(horizontal = if (layout.compactHandheld) 12.dp else 14.dp, vertical = 0.dp)
            .height(if (layout.compactHandheld) 36.dp else CreteDS.pillHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (layout.compactHandheld) 8.dp else 10.dp)
    ) {
        // Clock
        Text(
            text = clock,
            style = CreteDS.typePillStatus,
            fontWeight = FontWeight.SemiBold
        )

        PillDivider()

        // WiFi
        Icon(
            imageVector = wifiIcon(wifiLevel),
            contentDescription = "WiFi",
            tint = CreteDS.textSecondary,
            modifier = Modifier.size(if (layout.compactHandheld) 15.dp else 16.dp)
        )

        // Battery
        Text(
            text = if (isCharging) "⚡${batteryPct}%" else "${batteryPct}%",
            style = CreteDS.typePillStatus,
            color = when {
                batteryPct <= 15 -> Color(0xFFFF5555)
                batteryPct <= 30 -> Color(0xFFFFAA44)
                else -> CreteDS.textPrimary
            }
        )
    }
}

@Composable
private fun PillDivider() {
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(16.dp)
            .background(CreteDS.border)
    )
}

private fun currentTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun wifiIcon(level: Int): ImageVector = when {
    level < 0 -> Icons.Outlined.WifiOff
    level == 0 -> Icons.Outlined.Wifi
    else -> Icons.Outlined.Wifi
}
