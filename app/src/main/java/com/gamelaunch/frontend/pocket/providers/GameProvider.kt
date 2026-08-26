package com.gamelaunch.frontend.pocket.providers

import com.gamelaunch.frontend.pocket.domain.DiscoveredProviderGame
import com.gamelaunch.frontend.pocket.domain.LaunchContext
import com.gamelaunch.frontend.pocket.domain.LaunchTarget

/**
 * Implemented by each backend integration (GameNative, GameHub Lite, WinNative, etc.).
 *
 * Providers are intentionally thin: they own only discovery and launch.
 * All configuration (Wine, Proton, Box64, drivers, container settings) stays inside the backend app.
 */
interface GameProvider {
    val id: ProviderId
    val capabilities: Set<ProviderCapability>

    /**
     * Returns true if the provider app is installed and usable.
     * Must not throw — return false on any error.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Scan for games this provider knows about.
     * Must be fast, non-destructive, and non-throwing — return empty list on any error.
     */
    suspend fun discoverGames(): List<DiscoveredProviderGame>

    /**
     * Launch the game described by [target] with [context].
     *
     * @return Result.success if the Activity start was attempted;
     *         Result.failure with a descriptive exception if the launch could not be attempted.
     */
    suspend fun launch(target: LaunchTarget, context: LaunchContext): Result<Unit>
}
