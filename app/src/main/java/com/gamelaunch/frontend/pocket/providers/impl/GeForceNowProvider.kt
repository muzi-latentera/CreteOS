package com.gamelaunch.frontend.pocket.providers.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.gamelaunch.frontend.pocket.domain.DiscoveredProviderGame
import com.gamelaunch.frontend.pocket.domain.LaunchContext
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderCapability
import com.gamelaunch.frontend.pocket.providers.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for NVIDIA GeForce NOW — cloud game streaming.
 *
 * Launch strategy (priority order):
 * 1. Android launcher shortcut if GFN publishes per-game shortcuts
 * 2. Documented GFN web deep link: https://play.geforcenow.com/games?game-id=<GFN-ID>
 * 3. Open the GFN app directly
 *
 * NOTE: NVIDIA does not document a native Android app intent contract for per-game launch
 * equivalent to GameNative or Moonlight. The web deep link is the most reliable current approach.
 * This provider is deliberately conservative — we do not read GFN private data.
 *
 * GFN game IDs must be linked manually or obtained from the GFN catalog API.
 */
@Singleton
class GeForceNowProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : GameProvider {

    override val id = ProviderId.GEFORCE_NOW

    override val capabilities = setOf(
        ProviderCapability.ANDROID_SHORTCUT,
        ProviderCapability.CLOUD,
        ProviderCapability.STREAMING
    )

    override suspend fun isAvailable(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** GFN games are linked manually — no automatic discovery without private data access. */
    override suspend fun discoverGames(): List<DiscoveredProviderGame> = emptyList()

    override suspend fun launch(target: LaunchTarget, launchContext: LaunchContext): Result<Unit> {
        val data = runCatching { JSONObject(target.launchData) }.getOrElse { JSONObject() }
        val gfnGameId   = data.optString("gfnGameId").ifBlank { null }
        val steamAppId  = data.optString("steamAppId").ifBlank { null }

        return runCatching {
            when {
                gfnGameId != null -> {
                    // Use the officially documented GFN deep link format
                    // autoVerify=true in GFN manifest means this opens GFN app directly
                    val deepLink = "$GFN_DEEP_LINK_BASE$gfnGameId" +
                        "&utm_source=creteos&utm_campaign=launcher"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        // Don't force setPackage — let Android App Link resolution handle it
                        // GFN has autoVerify=true so it should claim play.geforcenow.com
                    }
                    context.startActivity(intent)
                    Log.d(TAG, "Launched GFN via deep link: $deepLink")
                }
                else -> {
                    // No GFN UUID — open GFN library directly
                    val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
                        ?: throw IllegalStateException("GeForce NOW is not installed")
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    Log.d(TAG, "Opened GFN library (no game UUID for ${target.displayName})")
                }
            }
        }
    }

    companion object {
        const val PACKAGE = "com.nvidia.geforcenow"
        const val GFN_DEEP_LINK_BASE = "https://play.geforcenow.com/games?game-id="
        private const val TAG = "GeForceNowProvider"
    }
}
