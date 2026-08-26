package com.gamelaunch.frontend.pocket.domain

import com.gamelaunch.frontend.pocket.providers.ProviderId

data class LaunchTarget(
    val id: Long = 0,
    val hostGameKey: String,
    val provider: ProviderId,
    /** Provider-specific game identifier, e.g. Steam AppID, shortcut ID, file path hash */
    val externalId: String,
    /** Source within provider, e.g. STEAM / GOG / EPIC for GameNative */
    val source: String = "",
    val displayName: String,
    /** Provider-specific serialised launch data (JSON) */
    val launchData: String = "{}",
    val isAvailable: Boolean = true,
    val isPreferred: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
