package com.gamelaunch.frontend.pocket.providers.impl

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gamelaunch.frontend.pocket.domain.DiscoveredProviderGame
import com.gamelaunch.frontend.pocket.domain.LaunchContext
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderCapability
import com.gamelaunch.frontend.pocket.providers.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for GameNative — the primary local PC-game runtime.
 *
 * Discovery: reads GameNative frontend-sync marker files (*.steam, *.gog, *.epic, etc.)
 * from the user-selected ROMs directory. Phase 4 will implement full discovery.
 *
 * Launch: sends app.gamenative.LAUNCH_GAME with app_id and game_source.
 * We do NOT send container_config so GameNative uses its own saved settings.
 *
 * XREAL override: Phase 5 (GamingDisplayManager) will inject a temporary screenSize into
 * LaunchContext when an external display is detected, and we add it only then.
 */
@Singleton
class GameNativeProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : GameProvider {

    override val id = ProviderId.GAME_NATIVE

    override val capabilities = setOf(
        ProviderCapability.DIRECT_LAUNCH,
        ProviderCapability.TEMP_RESOLUTION_OVERRIDE,
        ProviderCapability.LOCAL
    )

    override suspend fun isAvailable(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    /**
     * Phase 4 will scan GameNative frontend-sync export files.
     * Returning empty for now — games are currently seeded directly from eOr's Steam scanner.
     */
    override suspend fun discoverGames(): List<DiscoveredProviderGame> = emptyList()

    override suspend fun launch(target: LaunchTarget, launchContext: LaunchContext): Result<Unit> {
        val appId = target.externalId.toIntOrNull()
            ?: return Result.failure(
                IllegalArgumentException("Invalid GameNative app_id: '${target.externalId}'")
            )

        return runCatching {
            val intent = Intent(LAUNCH_ACTION).apply {
                setPackage(PACKAGE)
                putExtra(EXTRA_APP_ID, appId)
                putExtra(EXTRA_GAME_SOURCE, target.source.ifBlank { DEFAULT_SOURCE })
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // Temporary resolution override for external display (XREAL support).
                // Only applied when AUTO_MATCH_DISPLAY policy is active and display info is present.
                // This is a temporary in-memory override — it does NOT modify GameNative's saved config.
                if (launchContext.displayPolicy ==
                        com.gamelaunch.frontend.pocket.domain.DisplayPolicy.AUTO_MATCH_DISPLAY &&
                    launchContext.externalDisplayConnected) {
                    val screenSize = "${launchContext.displayWidth}x${launchContext.displayHeight}"
                    val config = """{"screenSize":"$screenSize"}"""
                    putExtra(EXTRA_CONTAINER_CONFIG, config)
                    Log.d(TAG, "Applying temporary screenSize override: $screenSize")
                }
            }
            context.startActivity(intent)
            Log.d(TAG, "Launched GameNative app_id=$appId source=${target.source}")
        }
    }

    companion object {
        const val PACKAGE = "app.gamenative"
        const val LAUNCH_ACTION = "app.gamenative.LAUNCH_GAME"
        const val EXTRA_APP_ID = "app_id"
        const val EXTRA_GAME_SOURCE = "game_source"
        const val EXTRA_CONTAINER_CONFIG = "container_config"
        const val DEFAULT_SOURCE = "STEAM"

        private const val TAG = "GameNativeProvider"
    }
}
