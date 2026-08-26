package com.gamelaunch.frontend.pocket.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SignalWifi0Bar
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Height of the CreteOS status bar. */
val StatusBarHeight = 48.dp

/** Status bar background with subtle transparency. */
private val StatusBarBackground = Color(0xFF0D1117).copy(alpha = 0.85f)

/** Primary text/icon color for the status bar. */
private val StatusBarText = Color(0xFFE6EDF3)

/** Secondary/muted text color for the status bar. */
private val StatusBarTextMuted = Color(0xFF8B949E)

/**
 * CreteOS-style status bar with clock, WiFi signal, battery percentage, and power button.
 * Designed for landscape 1920×1080 handheld gaming devices.
 */
@Composable
fun CreteOSStatusBar(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPowerDialog by remember { mutableStateOf(false) }
    
    // Clock state - updates every minute
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTime = timeFormat.format(Date())
            // Calculate delay until next minute
            val now = System.currentTimeMillis()
            val delayMs = 60_000 - (now % 60_000)
            delay(delayMs)
        }
    }
    
    // Battery state
    var batteryLevel by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    
    DisposableEffect(context) {
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    batteryLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(batteryReceiver) }
    }
    
    // WiFi state
    var wifiSignalLevel by remember { mutableIntStateOf(-1) } // -1 = disconnected
    
    DisposableEffect(context) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val wifiInfo = wifiManager?.connectionInfo
                if (wifiInfo != null && wifiManager.isWifiEnabled) {
                    val rssi = wifiInfo.rssi
                    wifiSignalLevel = WifiManager.calculateSignalLevel(rssi, 5)
                } else {
                    wifiSignalLevel = -1
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        context.registerReceiver(wifiReceiver, filter)
        
        // Initial check
        val wifiInfo = wifiManager?.connectionInfo
        if (wifiInfo != null && wifiManager.isWifiEnabled) {
            wifiSignalLevel = WifiManager.calculateSignalLevel(wifiInfo.rssi, 5)
        }
        
        onDispose { context.unregisterReceiver(wifiReceiver) }
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(StatusBarHeight)
            .background(StatusBarBackground)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // CreteOS wordmark (left)
        Text(
            text = "CreteOS",
            color = StatusBarText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Status indicators (right)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clock
            Text(
                text = currentTime,
                color = StatusBarText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            
            // WiFi indicator
            Icon(
                imageVector = getWifiIcon(wifiSignalLevel),
                contentDescription = "WiFi",
                tint = if (wifiSignalLevel >= 0) StatusBarText else StatusBarTextMuted,
                modifier = Modifier.size(20.dp)
            )
            
            // Battery indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$batteryLevel%",
                    color = if (batteryLevel <= 20 && !isCharging) Color(0xFFFF6B6B) else StatusBarText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = getBatteryIcon(batteryLevel, isCharging),
                    contentDescription = "Battery",
                    tint = when {
                        isCharging -> Color(0xFF4ADE80)
                        batteryLevel <= 20 -> Color(0xFFFF6B6B)
                        else -> StatusBarText
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Power button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable { showPowerDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Power menu",
                    tint = StatusBarText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    
    if (showPowerDialog) {
        PowerDialog(
            onDismiss = { showPowerDialog = false },
            onSleep = {
                showPowerDialog = false
                // Send the device to sleep (screen off)
                // This doesn't require special permissions
                val intent = Intent(Intent.ACTION_SCREEN_OFF)
                context.sendBroadcast(intent)
            },
            onRestart = {
                showPowerDialog = false
                // Note: Actual reboot requires system-level permission
                // This will show a toast or system dialog
                try {
                    val intent = Intent("android.intent.action.REBOOT")
                    intent.putExtra("nowait", 1)
                    intent.putExtra("interval", 1)
                    intent.putExtra("window", 0)
                    context.sendBroadcast(intent)
                } catch (e: Exception) {
                    // Reboot requires privileged access
                }
            }
        )
    }
}

@Composable
private fun PowerDialog(
    onDismiss: () -> Unit,
    onSleep: () -> Unit,
    onRestart: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161B22),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE6EDF3),
        title = {
            Text(
                text = "Power",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text("What would you like to do?")
        },
        confirmButton = {
            TextButton(onClick = onRestart) {
                Text("Restart", color = Color(0xFFFF6B6B))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF8B949E))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onSleep) {
                    Text("Sleep", color = Color(0xFF58A6FF))
                }
            }
        }
    )
}

private fun getWifiIcon(signalLevel: Int): ImageVector = when (signalLevel) {
    -1 -> Icons.Default.SignalWifiOff
    0 -> Icons.Default.SignalWifi0Bar
    1, 2, 3 -> Icons.Default.SignalWifi4Bar // Use Wifi4Bar for all connected states
    else -> Icons.Default.SignalWifi4Bar
}

private fun getBatteryIcon(level: Int, isCharging: Boolean): ImageVector = when {
    isCharging -> Icons.Default.BatteryChargingFull
    level >= 95 -> Icons.Default.BatteryFull
    level >= 80 -> Icons.Default.Battery6Bar
    level >= 65 -> Icons.Default.Battery5Bar
    level >= 50 -> Icons.Default.Battery4Bar
    level >= 35 -> Icons.Default.Battery3Bar
    level >= 20 -> Icons.Default.Battery2Bar
    level >= 10 -> Icons.Default.Battery1Bar
    else -> Icons.Default.Battery0Bar
}
