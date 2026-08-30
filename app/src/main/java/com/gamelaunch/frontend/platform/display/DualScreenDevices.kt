package com.gamelaunch.frontend.platform.display

import android.os.Build

/**
 * Maps a dual-screen handheld to how its two panels relate to Android's primary/secondary displays.
 *
 * Android exposes no API for a display's physical top/bottom position, and the target devices are
 * mirror images of each other, so we classify the known ones by [Build] identity. eOr always wants
 * the **interactive menu on the bottom panel** and **artwork on the top panel**.
 */
object DualScreenDevices {

    /**
     * True only for handhelds whose two panels are part of the device itself. A generic external
     * monitor or AR-glasses display must never be claimed by the artwork Presentation.
     */
    fun isDualScreenHandheld(
        manufacturer: String = Build.MANUFACTURER,
        model: String = Build.MODEL,
        device: String = Build.DEVICE
    ): Boolean {
        val m = model.lowercase()
        val d = device.lowercase()
        return m.contains("thor") || d.contains("thor") ||
            m.contains("rg ds") || m.contains("rgds") ||
            d.contains("rgds") || d.contains("rg_ds")
    }

    enum class Layout {
        /**
         * The bottom (menu) panel is already the primary display, so the Activity stays where it
         * launches and the artwork [android.app.Presentation] goes on the secondary (top) panel.
         * No Activity relaunch needed. This is the Anbernic RG DS, and the safe default.
         */
        ARTWORK_ON_SECONDARY,

        /**
         * The top panel is the primary display, so the bottom (menu) panel is the *secondary* one.
         * The interactive Activity must be relaunched onto the secondary display and the artwork
         * Presentation shown on the primary (top). This is the AYN Thor.
         */
        MENU_ON_SECONDARY;

        fun flipped(): Layout =
            if (this == ARTWORK_ON_SECONDARY) MENU_ON_SECONDARY else ARTWORK_ON_SECONDARY
    }

    /**
     * Classify a supported dual-screen handheld. Callers first gate this with
     * [isDualScreenHandheld], so the fallback is never used to claim an external display.
     */
    fun layoutFor(
        manufacturer: String = Build.MANUFACTURER,
        model: String = Build.MODEL,
        device: String = Build.DEVICE
    ): Layout {
        val m = model.lowercase()
        val d = device.lowercase()
        return when {
            // AYN Thor — top screen is primary; the menu belongs on the secondary (bottom) panel.
            m.contains("thor") || d.contains("thor") ->
                Layout.MENU_ON_SECONDARY

            // Anbernic RG DS — bottom (menu) is already primary; artwork goes on the secondary (top).
            m.contains("rg ds") || m.contains("rgds") || d.contains("rgds") ||
                d.contains("rg_ds") ->
                Layout.ARTWORK_ON_SECONDARY

            else -> Layout.ARTWORK_ON_SECONDARY
        }
    }
}
