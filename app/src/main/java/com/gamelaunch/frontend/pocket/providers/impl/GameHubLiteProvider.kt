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
 * Provider for GameHub Lite — used as a secondary/compatibility backend.
 *
 * External direct-launch via gamehub.lite.LAUNCH_GAME is supported for Steam AppIDs.
 * GameHub Lite is expected to be more fragile than GameNative (changed maintainers,
 * patch-based project), so this provider gracefully falls back to opening the app
 * if direct launch is unavailable.
 *
 * NOTE: Verify current package name against installed build. The Producdevity/gamehub-lite
 * fork is the current active project as of 2026.
 */
@Singleton
class GameHubLiteProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : GameProvider {

    override val id = ProviderId.GAME_HUB_LITE

    override val capabilities = setOf(
        ProviderCapability.DIRECT_LAUNCH,
        ProviderCapability.LOCAL
    )

    override suspend fun isAvailable(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    override suspend fun discoverGames(): List<DiscoveredProviderGame> = emptyList()

    override suspend fun launch(target: LaunchTarget, launchContext: LaunchContext): Result<Unit> {
        if (!isAvailable()) {
            return Result.failure(IllegalStateException("GameHub Lite is not installed"))
        }

        val steamAppId = target.externalId
        return runCatching {
            val intent = Intent(LAUNCH_ACTION).apply {
                // Verified on gamehub.lite v5.1.8 — activity confirmed via aapt manifest dump
                setClassName(PACKAGE, LAUNCH_ACTIVITY)
                putExtra(EXTRA_STEAM_APP_ID, steamAppId)
                putExtra(EXTRA_AUTO_START, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }.getOrElse {
                Log.w(TAG, "Direct launch rejected, opening GameHub Lite library")
                val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
                    ?: throw IllegalStateException("Cannot open GameHub Lite")
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
            }
            Log.d(TAG, "Launched GameHub Lite steamAppId=$steamAppId")
        }
    }

    companion object {
        // Verified against gamehub.lite v5.1.8 on Android 17, 2026-08-26
        const val PACKAGE = "gamehub.lite"
        const val LAUNCH_ACTION = "gamehub.lite.LAUNCH_GAME"
        const val LAUNCH_ACTIVITY = "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity"
        const val EXTRA_STEAM_APP_ID = "steamAppId"
        const val EXTRA_AUTO_START = "autoStartGame"
        private const val TAG = "GameHubLiteProvider"
    }
}
