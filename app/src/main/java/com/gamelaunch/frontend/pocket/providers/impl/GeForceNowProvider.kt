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
        // Prefer pre-built canonical URL; fall back to constructing from parts
        val canonicalUrl = data.optString("canonicalGfnUrl").ifBlank { null }
        val gfnGameId    = data.optString("gfnGameId").ifBlank { null }

        return runCatching {
            val launchUrl = when {
                canonicalUrl != null -> {
                    // Use stored canonical URL, just swap utm_source
                    canonicalUrl
                        .replace("utm_source=shortcut", "utm_source=creteos")
                        .let { if (!it.contains("utm_source=")) "$it&utm_source=creteos&utm_campaign=launcher" else it }
                }
                gfnGameId != null -> {
                    // Fallback: construct from game-id alone
                    "$GFN_DEEP_LINK_BASE$gfnGameId&lang=en_GB&utm_source=creteos&utm_campaign=launcher"
                }
                else -> null
            }

            if (launchUrl != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(launchUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "Launched GFN: $launchUrl")
            } else {
                // No verified URL — open GFN library
                val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
                    ?: throw IllegalStateException("GeForce NOW is not installed")
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                Log.d(TAG, "Opened GFN library (no verified URL)")
            }
        }
    }

    companion object {
        const val PACKAGE = "com.nvidia.geforcenow"
        const val GFN_DEEP_LINK_BASE = "https://play.geforcenow.com/games?game-id="
        private const val TAG = "GeForceNowProvider"
    }
}
