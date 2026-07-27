package com.gamelaunch.frontend.platform.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.activity.ComponentActivity
import com.gamelaunch.frontend.ui.dualscreen.ArtworkBus
import com.gamelaunch.frontend.ui.dualscreen.ArtworkPresentation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detects a second physical display and drives eOr's dual-screen layout: the interactive menu on
 * the bottom panel, game artwork on the top panel (see [DualScreenDevices]).
 *
 * Owned by [ComponentActivity]. On single-screen devices it does nothing and [active] stays false,
 * so the app behaves exactly as before. It listens for hot-plug events (clamshell open/close, a
 * panel powering on/off) and re-evaluates on each.
 */
class DualScreenManager(
    private val activity: ComponentActivity,
    private val artworkBus: ArtworkBus,
    private val darkModeProvider: () -> Boolean
) {
    private val displayManager =
        activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private var presentation: ArtworkPresentation? = null

    private val _active = MutableStateFlow(false)
    /** True while artwork is being presented on a second screen. Drives `LocalDualScreenActive`. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private var enabled = true
    private var swap = false
    private var started = false

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    fun start() {
        if (started) return
        started = true
        displayManager.registerDisplayListener(listener, null)
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { displayManager.unregisterDisplayListener(listener) }
        dismiss()
        _active.value = false
    }

    /** Apply the latest persisted preferences (dual-screen on/off and the manual top/bottom swap). */
    fun setPreferences(enabled: Boolean, swap: Boolean) {
        if (this.enabled == enabled && this.swap == swap) return
        this.enabled = enabled
        this.swap = swap
        if (started) refresh()
    }

    /**
     * The display id the interactive Activity should run on. For the common
     * [DualScreenDevices.Layout.ARTWORK_ON_SECONDARY] case this is the default display (no move
     * needed). For [DualScreenDevices.Layout.MENU_ON_SECONDARY] (Thor) it's the secondary display,
     * so [com.gamelaunch.frontend.MainActivity] can relaunch itself there. Returns
     * [Display.DEFAULT_DISPLAY] when single-screen or disabled.
     */
    fun requiredMenuDisplayId(): Int {
        val secondary = secondaryDisplay() ?: return Display.DEFAULT_DISPLAY
        return when (effectiveLayout()) {
            DualScreenDevices.Layout.ARTWORK_ON_SECONDARY -> Display.DEFAULT_DISPLAY
            DualScreenDevices.Layout.MENU_ON_SECONDARY -> secondary.displayId
        }
    }

    private fun effectiveLayout(): DualScreenDevices.Layout =
        DualScreenDevices.layoutFor().let { if (swap) it.flipped() else it }

    /** The first powered-on display that isn't the default one, or null if there's only one screen. */
    private fun secondaryDisplay(): Display? =
        displayManager.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON
        }

    /** Re-evaluate which display (if any) should show artwork and (re)attach the Presentation. */
    fun refresh() {
        val secondary = if (enabled) secondaryDisplay() else null
        if (secondary == null) {
            dismiss()
            _active.value = false
            return
        }

        // Where the artwork goes:
        //  - ARTWORK_ON_SECONDARY (RG DS): artwork on the secondary (top) panel; menu Activity stays.
        //  - MENU_ON_SECONDARY (Thor): the Activity is relaunched onto the secondary (bottom) panel
        //    by MainActivity, so artwork goes on the default (top) display.
        val artworkDisplay = when (effectiveLayout()) {
            DualScreenDevices.Layout.ARTWORK_ON_SECONDARY -> secondary
            DualScreenDevices.Layout.MENU_ON_SECONDARY ->
                displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: secondary
        }

        showOn(artworkDisplay)
        _active.value = true
    }

    private fun showOn(display: Display) {
        val current = presentation
        if (current != null && current.display?.displayId == display.displayId && current.isShowing) {
            return
        }
        dismiss()
        val next = ArtworkPresentation(
            activity = activity,
            display = display,
            artworkBus = artworkBus,
            darkMode = darkModeProvider()
        )
        runCatching { next.show() }
            .onSuccess { presentation = next }
            .onFailure { Log.w(TAG, "Failed to show artwork presentation on display ${display.displayId}", it) }
    }

    fun dismiss() {
        presentation?.let { runCatching { it.dismiss() } }
        presentation = null
    }

    companion object {
        private const val TAG = "DualScreenManager"

        /**
         * The display id the Activity currently occupies. Used by MainActivity to decide whether a
         * MENU_ON_SECONDARY relaunch is needed.
         */
        fun currentDisplayId(activity: ComponentActivity): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display?.displayId ?: Display.DEFAULT_DISPLAY
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay?.displayId ?: Display.DEFAULT_DISPLAY
            }
    }
}
