package com.gamelaunch.frontend.pocket.sync

import android.util.Log
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.pocket.data.db.entity.LaunchTargetEntity
import com.gamelaunch.frontend.pocket.data.repository.LaunchTargetRepository
import com.gamelaunch.frontend.pocket.domain.DiscoveredProviderGame
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates provider discovery and syncs results into both databases correctly:
 *
 * - eOr's GameRepository (via insertGame) for library visibility
 * - CreteOS PocketDatabase (via LaunchTargetRepository) for launch targets
 * - PcGameArtworkResolver for artwork (Steam CDN + eOr MediaRepository)
 *
 * This is the ONLY legitimate path for inserting provider-discovered games.
 * No code outside this class should write raw SQL to gamelauncher.db.
 */
@Singleton
class ProviderSyncCoordinator @Inject constructor(
    private val gameRepository: GameRepository,
    private val launchTargetRepository: LaunchTargetRepository,
    private val artworkResolver: PcGameArtworkResolver,
    private val providers: Map<ProviderId, @JvmSuppressWildcards GameProvider>
) {

    data class SyncResult(
        val provider: ProviderId,
        val discovered: Int,
        val added: Int,
        val updated: Int,
        val errors: List<String>
    )

    /**
     * Run discovery for all enabled providers and sync results.
     */
    suspend fun syncAll(): List<SyncResult> =
        providers.entries.mapNotNull { (id, provider) ->
            runCatching { syncProvider(id, provider) }
                .getOrElse { e ->
                    Log.e(TAG, "Sync failed for $id: ${e.message}", e)
                    SyncResult(id, 0, 0, 0, listOf(e.message ?: "Unknown error"))
                }
        }

    /**
     * Run discovery for a single provider.
     */
    suspend fun syncProvider(providerId: ProviderId, provider: GameProvider): SyncResult {
        if (!provider.isAvailable()) {
            Log.d(TAG, "$providerId not installed — marking unavailable")
            launchTargetRepository.markProviderUnavailable(providerId)
            return SyncResult(providerId, 0, 0, 0, emptyList())
        }

        val discovered = runCatching { provider.discoverGames() }.getOrElse {
            Log.w(TAG, "discoverGames() failed for $providerId: ${it.message}")
            return SyncResult(providerId, 0, 0, 0, listOf(it.message ?: "Discovery failed"))
        }

        Log.d(TAG, "$providerId discovered ${discovered.size} games")
        var added = 0; var updated = 0
        val errors = mutableListOf<String>()

        for (discovered in discovered) {
            runCatching {
                processDiscoveredGame(discovered)?.let { wasNew ->
                    if (wasNew) added++ else updated++
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to process ${discovered.displayName}: ${e.message}")
                errors += "${discovered.displayName}: ${e.message}"
            }
        }

        launchTargetRepository.markProviderAvailable(providerId)
        return SyncResult(providerId, discovered.size, added, updated, errors)
    }

    /**
     * Process one discovered game:
     * 1. Find or create the eOr Game row (via GameRepository — no raw SQL)
     * 2. Upsert a launch target in PocketDatabase
     * 3. Resolve artwork via PcGameArtworkResolver
     *
     * Returns true if a new Game row was inserted, false if existing.
     */
    private suspend fun processDiscoveredGame(discovered: DiscoveredProviderGame): Boolean {
        // Determine the romPath key (eOr canonical format)
        val hostKey = discovered.hostGameKey
            ?: buildHostKey(discovered.provider, discovered.externalId, discovered.source)

        // Check if eOr already has this game
        var existingGame = gameRepository.getGameByRomPath(hostKey)
        val wasNew: Boolean

        if (existingGame == null) {
            // Insert via GameRepository — correct path, Room handles it
            val game = Game(
                title      = discovered.displayName,
                romPath    = hostKey,
                romFilename = "${discovered.displayName}.${discovered.source.lowercase()}",
                platformId  = platformForProvider(discovered.provider)
            )
            val newId = gameRepository.insertGame(game)
            if (newId <= 0L) {
                // Already exists (race condition) — fetch it
                existingGame = gameRepository.getGameByRomPath(hostKey)
                    ?: return false
            }
            wasNew = newId > 0L
            val gameId = if (wasNew) newId else (existingGame?.id ?: return false)
            Log.d(TAG, "Inserted game '${discovered.displayName}' id=$gameId romPath=$hostKey")

            // Resolve artwork for new games immediately
            resolveArtwork(gameId, discovered)
        } else {
            wasNew = false
        }

        val gameId = existingGame?.id ?: gameRepository.getGameByRomPath(hostKey)?.id ?: return wasNew

        // Upsert launch target in PocketDatabase
        val target = LaunchTargetEntity(
            hostGameKey = hostKey,
            provider    = discovered.provider.name,
            externalId  = discovered.externalId,
            source      = discovered.source,
            displayName = discovered.displayName,
            launchData  = discovered.launchData,
            isAvailable = true,
            isPreferred = false
        )
        launchTargetRepository.upsertTarget(target.toDomain())
        Log.d(TAG, "Upserted launch target: ${discovered.provider} / ${discovered.externalId}")

        return wasNew
    }

    private suspend fun resolveArtwork(gameId: Long, discovered: DiscoveredProviderGame) {
        val steamAppId = when (discovered.provider) {
            ProviderId.GAME_NATIVE,
            ProviderId.GAME_HUB_LITE -> discovered.externalId.toIntOrNull()
            else -> null
        }
        if (steamAppId != null && discovered.source in STEAM_SOURCES) {
            // Set remote URLs immediately (fast) then trigger background download
            artworkResolver.setRemoteUrlsForSteamGame(gameId, steamAppId)
        }
        // Non-Steam and emulator artwork handled by eOr's existing scraper
    }

    private fun buildHostKey(provider: ProviderId, externalId: String, source: String): String =
        when (provider) {
            ProviderId.GAME_NATIVE,
            ProviderId.GAME_HUB_LITE -> if (source == "STEAM") "steam:$externalId"
                                         else "steam:$source:$externalId"
            ProviderId.WIN_NATIVE    -> "winnative:$externalId"
            ProviderId.WINLATOR      -> "winlator:$externalId"
            ProviderId.MOONLIGHT     -> "moonlight:$externalId"
            ProviderId.GEFORCE_NOW   -> "gfn:$externalId"
            ProviderId.ANDROID_SHORTCUT -> "shortcut:$externalId"
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

    // Helper to bridge from entity to domain (LaunchTargetRepository expects domain)
    private fun LaunchTargetEntity.toDomain() =
        com.gamelaunch.frontend.pocket.domain.LaunchTarget(
            id          = id,
            hostGameKey = hostGameKey,
            provider    = runCatching {
                com.gamelaunch.frontend.pocket.providers.ProviderId.valueOf(provider)
            }.getOrDefault(ProviderId.GAME_NATIVE),
            externalId  = externalId,
            source      = source,
            displayName = displayName,
            launchData  = launchData,
            isAvailable = isAvailable,
            isPreferred = isPreferred
        )

    companion object {
        private const val TAG = "ProviderSyncCoord"
        private val STEAM_SOURCES = setOf("STEAM", "GOG", "EPIC", "AMAZON", "CUSTOM_GAME")
    }
}
