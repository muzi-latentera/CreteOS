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
 * Provider for PlayStation Cloud streaming via the Asobi app (com.asobi.droid).
 *
 * Launch strategy:
 * - Sends asobi.launch_cloud_id intent extra with the PSN concept ID.
 *   Asobi looks up the game in its catalog and starts streaming directly.
 *
 * Game IDs are candidate PSN concept IDs sourced from the PSN catalogue. They must be verified
 * by a real launch through CreteOS on the target device before this provider is accepted.
 * platformId = "psn" so Talos auto-matches playtime and trophy data.
 */
@Singleton
class ASOBIProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : GameProvider {

    override val id = ProviderId.ASOBI

    override val capabilities = setOf(
        ProviderCapability.CLOUD,
        ProviderCapability.STREAMING
    )

    override suspend fun isAvailable(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    override suspend fun discoverGames(): List<DiscoveredProviderGame> =
        ASOBISeedData.GAMES.map { game ->
            DiscoveredProviderGame(
                provider    = ProviderId.ASOBI,
                externalId  = game.conceptId,
                source      = "PSN",
                displayName = game.title,
                launchData  = JSONObject()
                    .put("conceptId", game.conceptId)
                    .put("platform", game.platform)
                    .toString(),
                hostGameKey = null
            )
        }

    override suspend fun launch(target: LaunchTarget, launchContext: LaunchContext): Result<Unit> {
        val data = runCatching { JSONObject(target.launchData) }.getOrElse { JSONObject() }
        val conceptId = data.optString("conceptId").ifBlank { target.externalId }

        return runCatching {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setPackage(PACKAGE)
                putExtra(EXTRA_LAUNCH_CLOUD_ID, conceptId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            Log.d(TAG, "Launched Asobi PS Cloud: conceptId=$conceptId game=${target.displayName}")
        }
    }

    companion object {
        const val PACKAGE = "com.asobi.droid"
        const val EXTRA_LAUNCH_CLOUD_ID = "asobi.launch_cloud_id"
        private const val TAG = "ASOBIProvider"
    }
}
