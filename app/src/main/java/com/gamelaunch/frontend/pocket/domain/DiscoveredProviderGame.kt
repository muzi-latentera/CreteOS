package com.gamelaunch.frontend.pocket.domain

import com.gamelaunch.frontend.pocket.providers.ProviderId

/**
 * A game discovered during a provider scan, before it has been matched to an eOr host game.
 */
data class DiscoveredProviderGame(
    val provider: ProviderId,
    val externalId: String,
    val source: String = "",
    val displayName: String,
    val launchData: String = "{}",
    /**
     * Stable host-game key if we can auto-resolve it (e.g. romPath "steam:367520"),
     * null if the game needs manual matching or synthetic creation.
     */
    val hostGameKey: String? = null
)
