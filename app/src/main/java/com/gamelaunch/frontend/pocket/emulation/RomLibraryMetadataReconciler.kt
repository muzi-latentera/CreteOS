package com.gamelaunch.frontend.pocket.emulation

import android.util.Log
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.pocket.data.IgdbMetadataSync
import com.gamelaunch.frontend.pocket.data.SteamMetadataDao
import com.gamelaunch.frontend.pocket.data.SteamMetadataEntity
import com.gamelaunch.frontend.util.RomTitleNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges CreteOS IGDB metadata into eOr's library models through their public repositories.
 * This deliberately avoids opening or replacing either Room database directly.
 */
@Singleton
class RomLibraryMetadataReconciler @Inject constructor(
    private val gameRepository: GameRepository,
    private val mediaRepository: MediaRepository,
    private val steamMetadataDao: SteamMetadataDao,
    private val igdbSync: IgdbMetadataSync
) {
    suspend fun reconcile(games: List<Game>) = withContext(Dispatchers.IO) {
        games.forEach { game ->
            val system = emulatorSystemFor(game) ?: return@forEach
            runCatching { reconcileGame(game, system) }
                .onFailure { error ->
                    Log.w(TAG, "Could not reconcile ROM metadata for ${game.romPath}", error)
                }
        }
    }

    private suspend fun reconcileGame(game: Game, system: EmulatorSystem) {
        val canonicalTitle = RomTitleNormalizer.fromFilename(game.romFilename)
        val titleNeedsRepair = RomTitleNormalizer.shouldRepair(game.title, game.romFilename)
        if (titleNeedsRepair) {
            gameRepository.renameGame(game.id, canonicalTitle)
        }

        var metadata = steamMetadataDao.getByAppId(game.romPath)
        if (metadata == null) {
            metadata = SteamMetadataEntity(
                steamAppId = game.romPath,
                playtimeMinutes = 0,
                romAbsPath = game.romPath,
                updatedAtMs = System.currentTimeMillis()
            )
            steamMetadataDao.upsert(metadata)
        } else if (metadata.romAbsPath.isNullOrBlank()) {
            metadata = metadata.copy(
                romAbsPath = game.romPath,
                updatedAtMs = System.currentTimeMillis()
            )
            steamMetadataDao.upsert(metadata)
        }

        // A repaired title gets one fresh, platform-aware lookup so an old ambiguous result (for
        // example the 3DS remake of Luigi's Mansion) is replaced with the correct platform entry.
        if (metadata.igdbCoverUrl.isNullOrBlank() || titleNeedsRepair) {
            igdbSync.syncEmulatedGame(game.romPath, canonicalTitle, system)
            metadata = steamMetadataDao.getByAppId(game.romPath) ?: metadata
        }

        if (game.description.isNullOrBlank()) {
            metadata.description
                ?.takeIf { it.isNotBlank() }
                ?.let { gameRepository.fillDescriptionIfMissing(game.id, it) }
        }

        if (!metadata.igdbCoverUrl.isNullOrBlank() || !metadata.igdbHeroUrl.isNullOrBlank()) {
            mediaRepository.upsertMedia(
                GameMedia(
                    gameId = game.id,
                    boxArtRemoteUrl = metadata.igdbCoverUrl,
                    screenshotRemoteUrl = metadata.igdbHeroUrl,
                    scraperTimestampMs = System.currentTimeMillis()
                )
            )
        }
    }

    private fun emulatorSystemFor(game: Game): EmulatorSystem? {
        val rawId = if (game.romPath.startsWith("emu:")) {
            game.romPath.split(':').getOrNull(1)
        } else {
            game.platformId
        } ?: return null
        val normalizedId = when (rawId.trim().lowercase()) {
            "ps1" -> "psx"
            "n3ds" -> "3ds"
            else -> rawId.trim()
        }
        return EmulatorSystem.fromId(normalizedId)
    }

    private companion object {
        const val TAG = "RomMetadataRepair"
    }
}
