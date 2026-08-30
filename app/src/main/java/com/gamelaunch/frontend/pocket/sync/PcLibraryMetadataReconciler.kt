package com.gamelaunch.frontend.pocket.sync

import android.util.Log
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.pocket.data.HowLongToBeatProvider
import com.gamelaunch.frontend.pocket.data.GameSessionDao
import com.gamelaunch.frontend.pocket.data.IgdbSeedData
import com.gamelaunch.frontend.pocket.data.IgdbMetadataSync
import com.gamelaunch.frontend.pocket.data.SteamMetadataDao
import com.gamelaunch.frontend.pocket.data.SteamMetadataEntity
import com.gamelaunch.frontend.pocket.data.SteamMetadataSync
import com.gamelaunch.frontend.pocket.data.repository.LaunchTargetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enriches imported non-Steam PC games and promotes their IGDB artwork into eOr's public media
 * repository. Provider-local Epic/GOG IDs are not Steam AppIDs, so matching is title-based and is
 * deliberately restricted to exact normalised IGDB titles by [IgdbMetadataSync].
 */
@Singleton
class PcLibraryMetadataReconciler @Inject constructor(
    private val gameRepository: GameRepository,
    private val mediaRepository: MediaRepository,
    private val steamMetadataDao: SteamMetadataDao,
    private val launchTargetRepository: LaunchTargetRepository,
    private val gameSessionDao: GameSessionDao,
    private val hltbProvider: HowLongToBeatProvider,
    private val igdbSync: IgdbMetadataSync
) {
    suspend fun reconcile(games: List<Game>, forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        games.forEach { originalGame ->
            val game = reconcileOwnership(originalGame)
            if (supportsTitleMetadata(game)) {
                runCatching { reconcileGame(game, forceRefresh) }
                    .onFailure { error ->
                        Log.w(TAG, "Could not reconcile PC metadata for ${game.romPath}", error)
                    }
            }
        }
    }

    private suspend fun reconcileOwnership(game: Game): Game {
        val metadataKey = game.romPath.substringAfterLast(":").takeIf { it.isNotBlank() }
            ?: return game
        val ownershipPlatform = SteamMetadataSync.OWNERSHIP_PLATFORM_OVERRIDES[metadataKey]
            ?: return game
        val ownershipSource = sourceForPlatform(ownershipPlatform)
        val canonicalHostKey = "steam:$ownershipSource:$metadataKey"
        val oldHostKey = game.romPath
        val corrected = game.copy(
            platformId = ownershipPlatform,
            romPath = canonicalHostKey,
            romFilename = "${game.title}.$ownershipPlatform"
        )

        if (corrected != game) gameRepository.updateGame(corrected)

        if (oldHostKey != canonicalHostKey) {
            val savedPreferredTargetId = launchTargetRepository.getSavedPreferredTargetId(oldHostKey)
            launchTargetRepository.getTargetsForGameOnce(oldHostKey).forEach { target ->
                launchTargetRepository.upsertTarget(
                    target.copy(hostGameKey = canonicalHostKey, source = ownershipSource)
                )
            }
            launchTargetRepository.clearAutomaticPreference(oldHostKey)
            if (savedPreferredTargetId != null) {
                launchTargetRepository.setPreferredTarget(canonicalHostKey, savedPreferredTargetId)
            }
            gameSessionDao.migrateGameKey(oldHostKey, canonicalHostKey)
        } else {
            launchTargetRepository.getTargetsForGameOnce(canonicalHostKey)
                .filterNot { it.source.equals(ownershipSource, ignoreCase = true) }
                .forEach { target ->
                    launchTargetRepository.upsertTarget(target.copy(source = ownershipSource))
                }
        }

        if (corrected != game || oldHostKey != canonicalHostKey) {
            Log.i(TAG, "Corrected ${game.title} ownership to $ownershipPlatform ($canonicalHostKey)")
        }
        return corrected
    }

    private fun sourceForPlatform(platformId: String): String = when (platformId.lowercase()) {
        "gamepass", "xbox" -> "GAMEPASS"
        else -> platformId.uppercase()
    }

    private suspend fun reconcileGame(game: Game, forceRefresh: Boolean) {
        val metadataKey = game.romPath.substringAfterLast(":").takeIf { it.isNotBlank() } ?: return
        var metadata = steamMetadataDao.getByAppId(metadataKey)
        if (metadata == null) {
            metadata = SteamMetadataEntity(steamAppId = metadataKey)
            steamMetadataDao.upsert(metadata)
        }

        val needsTtb = hltbProvider.needsRefresh(metadataKey)
        if (forceRefresh || metadata.igdbCoverUrl.isNullOrBlank() || needsTtb) {
            igdbSync.syncGameByTitle(metadataKey, game.title)
            metadata = steamMetadataDao.getByAppId(metadataKey) ?: metadata
        }

        metadata.description
            ?.takeIf { game.description.isNullOrBlank() && it.isNotBlank() }
            ?.let { gameRepository.fillDescriptionIfMissing(game.id, it) }

        val curatedHeroUrl = IgdbSeedData.heroUrlFor(metadataKey)
        if (!curatedHeroUrl.isNullOrBlank() && curatedHeroUrl != metadata.igdbHeroUrl) {
            metadata = metadata.copy(
                igdbHeroUrl = curatedHeroUrl,
                updatedAtMs = System.currentTimeMillis()
            )
            steamMetadataDao.upsert(metadata)
        }
        if (!metadata.igdbCoverUrl.isNullOrBlank() ||
            !curatedHeroUrl.isNullOrBlank() ||
            !metadata.igdbHeroUrl.isNullOrBlank()
        ) {
            mediaRepository.upsertMedia(
                GameMedia(
                    gameId = game.id,
                    boxArtRemoteUrl = metadata.igdbCoverUrl,
                    // Bundled entries are manually reviewed and take priority over arbitrary
                    // first-result IGDB artwork. This also repairs older cached bad heroes.
                    screenshotRemoteUrl = curatedHeroUrl ?: metadata.igdbHeroUrl,
                    scraperTimestampMs = System.currentTimeMillis()
                )
            )
        }
    }

    private fun supportsTitleMetadata(game: Game): Boolean =
        game.platformId.trim().lowercase() in TITLE_MATCHED_PC_PLATFORMS &&
            game.romPath.startsWith("steam:")

    private companion object {
        const val TAG = "PcMetadataRepair"
        val TITLE_MATCHED_PC_PLATFORMS = setOf(
            "epic", "gog", "amazon", "ea", "gamepass", "xbox", "ubisoft", "gfn"
        )
    }
}
