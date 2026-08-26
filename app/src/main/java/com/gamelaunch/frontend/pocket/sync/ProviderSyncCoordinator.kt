package com.gamelaunch.frontend.pocket.sync

import android.util.Log
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.pocket.data.repository.LaunchTargetRepository
import com.gamelaunch.frontend.pocket.domain.DiscoveredProviderGame
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates provider discovery and correctly syncs results into both databases.
 *
 * Contract:
 * - eOr's GameRepository (insertGame) for library visibility — never raw SQL
 * - CreteOS PocketDatabase (LaunchTargetRepository) for launch targets
 * - PcGameArtworkResolver for Steam CDN artwork (Steam source only)
 * - GameIdentityResolver for matching discovered games to existing eOr games
 *
 * Stale-target reconciliation:
 *   After discovering games for a provider, any existing target for that provider
 *   that was NOT seen in this sync is marked unavailable (not deleted).
 *   Preferences are preserved throughout.
 *
 * isPreferred is NEVER reset by a sync. The user's choice persists.
 *
 * ## Performance
 *
 * Uses GameIdentityResolver.buildIndex() once per syncProvider() call,
 * then passes the index to each resolve() call. This avoids O(n²) database
 * queries for large provider discoveries (e.g., 300 Steam games).
 */
@Singleton
class ProviderSyncCoordinator @Inject constructor(
    private val gameRepository: GameRepository,
    private val launchTargetRepository: LaunchTargetRepository,
    private val artworkResolver: PcGameArtworkResolver,
    private val identityResolver: GameIdentityResolver,
    private val providers: Map<ProviderId, @JvmSuppressWildcards GameProvider>
) {

    data class SyncResult(
        val provider: ProviderId,
        val discovered: Int,
        val added: Int,
        val updated: Int,
        val markedUnavailable: Int,
        val errors: List<String>
    )

    suspend fun syncAll(): List<SyncResult> =
        providers.entries.mapNotNull { (id, provider) ->
            runCatching { syncProvider(id, provider) }
                .getOrElse { e ->
                    Log.e(TAG, "Sync failed for $id: ${e.message}", e)
                    SyncResult(id, 0, 0, 0, 0, listOf(e.message ?: "Unknown error"))
                }
        }

    suspend fun syncProvider(providerId: ProviderId, provider: GameProvider): SyncResult {
        if (!provider.isAvailable()) {
            Log.d(TAG, "$providerId not installed — marking unavailable")
            launchTargetRepository.markProviderUnavailable(providerId)
            return SyncResult(providerId, 0, 0, 0, 0, emptyList())
        }

        val discovered = runCatching { provider.discoverGames() }.getOrElse {
            Log.w(TAG, "discoverGames() failed for $providerId: ${it.message}")
            return SyncResult(providerId, 0, 0, 0, 0, listOf(it.message ?: "Discovery failed"))
        }

        Log.d(TAG, "$providerId discovered ${discovered.size} games")

        // Build library index ONCE for this sync — avoids O(n²) queries
        val libraryIndex = identityResolver.buildIndex()

        // Collect existing targets for this provider BEFORE processing — for reconciliation
        val existingTargets = launchTargetRepository
            .getTargetsForProvider(providerId)
            .associateBy { it.externalId }

        val seenExternalIds = mutableSetOf<String>()
        var added = 0; var updated = 0
        val errors = mutableListOf<String>()

        for (game in discovered) {
            runCatching {
                val wasNew = processDiscoveredGame(game, existingTargets[game.externalId], libraryIndex)
                seenExternalIds += game.externalId
                if (wasNew) added++ else updated++
            }.onFailure { e ->
                Log.w(TAG, "Failed to process ${game.displayName}: ${e.message}")
                errors += "${game.displayName}: ${e.message}"
            }
        }

        // Stale-target reconciliation: mark targets not seen in this sync as unavailable
        val stale = existingTargets.filterKeys { it !in seenExternalIds }
        stale.values.forEach { staleTarget ->
            runCatching {
                // Mark unavailable but preserve the row and any isPreferred flag
                launchTargetRepository.upsertTarget(staleTarget.copy(isAvailable = false))
                Log.d(TAG, "Marked stale: ${providerId}/${staleTarget.externalId}")
            }
        }

        return SyncResult(providerId, discovered.size, added, updated, stale.size, errors)
    }

    /**
     * Process one discovered game.
     *
     * Flow:
     * 1. Try to resolve an existing eOr host via GameIdentityResolver
     * 2. If resolved: attach launch target to that host
     * 3. If unresolved: create a synthetic eOr game row with a provider-namespaced key
     * 4. Upsert launch target — PRESERVING isPreferred if target already exists
     * 5. Resolve artwork for new games (Steam source only for CDN)
     *
     * Returns true if a new eOr Game row was inserted.
     */
    private suspend fun processDiscoveredGame(
        discovered: DiscoveredProviderGame,
        existingTarget: LaunchTarget?,
        libraryIndex: GameIdentityResolver.LibraryIndex
    ): Boolean {

        // 1+2. Try identity resolution
        val resolved = discovered.hostGameKey?.let { key ->
            // hostGameKey already provided by provider (e.g. GameNative with .steam files)
            val game = libraryIndex.byRomPath[key]
            game?.let { GameIdentityResolver.ResolvedIdentity(key, it.id, GameIdentityResolver.Confidence.EXACT_STORE_ID) }
        } ?: identityResolver.resolve(discovered, libraryIndex)

        val hostGameKey: String
        val hostGameId: Long
        val wasNew: Boolean

        if (resolved != null) {
            hostGameKey = resolved.hostGameKey
            hostGameId  = resolved.hostGameId
            wasNew      = false
        } else {
            // 3. Unresolved — create synthetic game row
            val syntheticKey = buildSyntheticKey(discovered)
            val existingGame = libraryIndex.byRomPath[syntheticKey]
                ?: gameRepository.getGameByRomPath(syntheticKey)

            if (existingGame != null) {
                hostGameKey = syntheticKey
                hostGameId  = existingGame.id
                wasNew      = false
            } else {
                val game = Game(
                    title       = discovered.displayName,
                    romPath     = syntheticKey,
                    romFilename = "${discovered.displayName}.${discovered.source.lowercase()}",
                    platformId  = platformForProvider(discovered.provider)
                )
                val newId = gameRepository.insertGame(game)
                if (newId <= 0L) {
                    // Race condition — fetch it
                    val refetched = gameRepository.getGameByRomPath(syntheticKey)
                        ?: return false
                    hostGameKey = syntheticKey
                    hostGameId  = refetched.id
                    wasNew      = false
                } else {
                    hostGameKey = syntheticKey
                    hostGameId  = newId
                    wasNew      = true
                    Log.d(TAG, "Synthetic game '${discovered.displayName}' id=$newId key=$syntheticKey")
                }
            }
        }

        // 4. Upsert launch target — PRESERVE isPreferred from existing target
        val currentPreferred = existingTarget?.isPreferred ?: false
        val target = LaunchTarget(
            id          = existingTarget?.id ?: 0,
            hostGameKey = hostGameKey,
            provider    = discovered.provider,
            externalId  = discovered.externalId,
            source      = discovered.source,
            displayName = discovered.displayName,
            launchData  = discovered.launchData,
            isAvailable = true,
            isPreferred = currentPreferred  // never reset by sync
        )
        launchTargetRepository.upsertTarget(target)
        Log.d(TAG, "Upserted target: ${discovered.provider}/${discovered.externalId} preferred=$currentPreferred")

        // 5. Artwork — Steam CDN only for source==STEAM
        if (wasNew) {
            resolveArtwork(hostGameId, discovered)
        }

        return wasNew
    }

    private suspend fun resolveArtwork(gameId: Long, discovered: DiscoveredProviderGame) {
        // Steam CDN ONLY for actual Steam AppIDs (source == "STEAM")
        // GOG/Epic/Amazon IDs are NOT Steam AppIDs — they would return wrong artwork
        if (discovered.source == "STEAM") {
            val steamAppId = discovered.externalId.toIntOrNull() ?: return
            artworkResolver.setRemoteUrlsForSteamGame(gameId, steamAppId)
        }
        // All other sources: eOr scraper handles artwork through its existing path
    }

    /**
     * Build a synthetic romPath for provider-only games (no eOr match).
     * Provider-namespaced to avoid collisions between providers.
     */
    private fun buildSyntheticKey(discovered: DiscoveredProviderGame): String = when (discovered.provider) {
        ProviderId.MOONLIGHT     -> "moonlight:${discovered.externalId}"
        ProviderId.GEFORCE_NOW   -> "gfn:${discovered.externalId}"
        ProviderId.WIN_NATIVE    -> "winnative:${discovered.externalId}"
        ProviderId.WINLATOR      -> "winlator:${discovered.externalId}"
        ProviderId.ANDROID_SHORTCUT -> "shortcut:${discovered.externalId}"
        // GameNative/GameHub with unknown source — shouldn't happen after identity resolver runs
        else -> "${discovered.provider.name.lowercase()}:${discovered.externalId}"
    }

    private fun platformForProvider(provider: ProviderId): String = when (provider) {
        ProviderId.GAME_NATIVE,
        ProviderId.GAME_HUB_LITE,
        ProviderId.WIN_NATIVE,
        ProviderId.WINLATOR      -> "steam"
        ProviderId.MOONLIGHT     -> "moonlight"
        ProviderId.GEFORCE_NOW   -> "gfn"
        ProviderId.ANDROID_SHORTCUT -> "android"
    }

    companion object {
        private const val TAG = "ProviderSyncCoord"
    }
}
