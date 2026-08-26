package com.gamelaunch.frontend.pocket.domain

/**
 * Runtime context passed to a [com.gamelaunch.frontend.pocket.providers.GameProvider] at launch time.
 * Providers may ignore fields they do not support.
 */
data class LaunchContext(
    val destinationDisplayId: Int? = null,
    val displayWidth: Int = 1920,
    val displayHeight: Int = 1080,
    val refreshRate: Float? = null,
    val externalDisplayConnected: Boolean = false,
    val displayPolicy: DisplayPolicy = DisplayPolicy.BACKEND_DEFAULT
)

enum class DisplayPolicy {
    /** Use whatever settings the backend already has saved. Never override. */
    BACKEND_DEFAULT,

    /**
     * For providers that safely support a temporary resolution override:
     * match the currently detected gaming display resolution automatically.
     * Internal Pocket FIT: 1920×1080. XREAL attached: 1920×1200.
     */
    AUTO_MATCH_DISPLAY,

    /** Use a custom user-specified resolution (stored in per-game preferences). */
    CUSTOM
}
