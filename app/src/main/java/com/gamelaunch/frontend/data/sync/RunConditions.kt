package com.gamelaunch.frontend.data.sync

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Evaluates the user's Save Sync run conditions (Wi-Fi only / while charging) for this moment. */
@Singleton
class RunConditions @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isOnWifi(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun isCharging(): Boolean {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    /** null when conditions are met; otherwise a short reason the engine can't run right now. */
    fun unmetReason(wifiOnly: Boolean, chargingOnly: Boolean): String? = when {
        wifiOnly && !isOnWifi()     -> "Waiting for Wi-Fi"
        chargingOnly && !isCharging() -> "Waiting until charging"
        else -> null
    }
}
