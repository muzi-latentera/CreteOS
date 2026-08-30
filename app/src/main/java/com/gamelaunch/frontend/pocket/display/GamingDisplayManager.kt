package com.gamelaunch.frontend.pocket.display

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.hardware.usb.UsbManager
import android.util.Log
import android.view.Display
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects the active gaming display (internal KONKR panel or external XREAL/USB-C display)
 * and exposes resolution/refresh info to providers at launch time.
 *
 * This runs alongside eOr's existing [com.gamelaunch.frontend.platform.display.DualScreenManager]
 * without replacing it. DualScreenManager handles dual-screen handheld artwork presentation.
 * GamingDisplayManager handles the external PC-game display use case.
 *
 * Target hardware:
 *   KONKR Pocket FIT Elite — internal: 1920×1080
 *   XREAL 1S (attached via USB-C) — mirrored profile: 1920×1200 @ 90Hz
 */
@Singleton
class GamingDisplayManager @Inject constructor(
    private val context: Context
) {
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _activeDisplay = MutableStateFlow(GamingDisplayInfo.internal())
    val activeDisplay: StateFlow<GamingDisplayInfo> = _activeDisplay.asStateFlow()

    private var started = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED ||
                intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                refresh()
            }
        }
    }

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    fun start() {
        if (started) return
        started = true
        displayManager.registerDisplayListener(listener, null)
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { displayManager.unregisterDisplayListener(listener) }
        runCatching { context.unregisterReceiver(usbReceiver) }
    }

    /** Re-read both Android displays and USB display accessories immediately. */
    fun refresh() {
        val external = findExternalDisplay()
        val info = if (external != null) {
            val mode = external.mode
            GamingDisplayInfo(
                displayId = external.displayId,
                width = mode.physicalWidth,
                height = mode.physicalHeight,
                refreshRate = mode.refreshRate,
                isExternal = true,
                name = external.name ?: "External"
            ).also {
                Log.d(TAG, "External display detected: ${it.width}x${it.height} @ ${it.refreshRate}Hz '${it.name}'")
            }
        } else if (isXreal1SConnected()) {
            GamingDisplayInfo.xreal1SMirrored().also {
                Log.d(TAG, "XREAL 1S USB profile detected: ${it.width}x${it.height} @ ${it.refreshRate}Hz")
            }
        } else {
            GamingDisplayInfo.internal().also {
                Log.d(TAG, "Using internal display: ${it.width}x${it.height}")
            }
        }
        _activeDisplay.value = info
    }

    /**
     * Returns the first powered-on non-default display that looks like an external gaming display.
     * We deliberately do NOT match on name strings like "XREAL" because USB-C display names vary
     * by firmware and cable. Instead we use characteristics: non-default, powered on, resolution
     * consistent with an external monitor.
     */
    private fun findExternalDisplay(): Display? =
        displayManager.displays.firstOrNull { display ->
            display.displayId != Display.DEFAULT_DISPLAY &&
            display.state == Display.STATE_ON &&
            display.mode.physicalWidth >= MIN_EXTERNAL_WIDTH
        }

    /** All detected displays and their modes, for the diagnostics screen. */
    fun getAllDisplays(): List<GamingDisplayInfo> {
        val androidDisplays = displayManager.displays.map { display ->
            val mode = display.mode
            GamingDisplayInfo(
                displayId = display.displayId,
                width = mode.physicalWidth,
                height = mode.physicalHeight,
                refreshRate = mode.refreshRate,
                isExternal = display.displayId != Display.DEFAULT_DISPLAY,
                name = display.name ?: "Display ${display.displayId}"
            )
        }
        val hasAndroidExternal = androidDisplays.any { it.isExternal }
        return if (!hasAndroidExternal && isXreal1SConnected()) {
            androidDisplays + GamingDisplayInfo.xreal1SMirrored()
        } else {
            androidDisplays
        }
    }

    private fun isXreal1SConnected(): Boolean =
        usbManager.deviceList.values.any { device ->
            device.vendorId == XREAL_VENDOR_ID && device.productId == XREAL_1S_PRODUCT_ID
        }

    companion object {
        private const val TAG = "GamingDisplayManager"
        private const val MIN_EXTERNAL_WIDTH = 1200 // pixels — avoids treating small side-screens as external
        private const val XREAL_VENDOR_ID = 0x3318
        private const val XREAL_1S_PRODUCT_ID = 0x043E
    }
}

data class GamingDisplayInfo(
    val displayId: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val isExternal: Boolean,
    val name: String
) {
    companion object {
        private const val MIRRORED_USB_DISPLAY_ID = -2

        /** Default: KONKR Pocket FIT Elite internal display */
        fun internal() = GamingDisplayInfo(
            displayId = Display.DEFAULT_DISPLAY,
            width = 1920,
            height = 1080,
            refreshRate = 60f,
            isExternal = false,
            name = "KONKR Internal"
        )

        /**
         * Pocket FIT routes the XREAL 1S as a vendor-managed mirror instead of an Android
         * secondary display. The negative ID makes that explicit: it is a launch profile, not an
         * ID that may be passed to ActivityOptions.setLaunchDisplayId().
         */
        fun xreal1SMirrored() = GamingDisplayInfo(
            displayId = MIRRORED_USB_DISPLAY_ID,
            width = 1920,
            height = 1200,
            refreshRate = 90f,
            isExternal = true,
            name = "XREAL 1S (mirrored)"
        )
    }
}
