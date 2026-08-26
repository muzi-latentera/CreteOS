package com.gamelaunch.frontend.pocket.sync

import android.util.Log
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.repository.MediaRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves and imports artwork for PC/streaming games added by CreteOS providers.
 *
 * Uses eOr's [MediaRepository] exclusively — never writes raw SQL to gamelauncher.db.
 *
 * Priority order:
 *   Steam game   → Steam CDN cover + hero → eOr scraper fallback
 *   Non-Steam PC → eOr scraper only
 *   Emulated     → eOr scraper (unchanged, not touched here)
 *   Android app  → eOr app icon (unchanged, not touched here)
 */
@Singleton
class PcGameArtworkResolver @Inject constructor(
    private val mediaRepository: MediaRepository
) {

    /**
     * Ensure [gameId] has artwork.
     * If it already has a box art path, does nothing.
     * For Steam games, fetches from Steam CDN and caches via eOr's download infrastructure.
     */
    suspend fun resolveForSteamGame(gameId: Long, steamAppId: Int) {
        val existing = mediaRepository.getMediaForGame(gameId)
        if (existing != null && existing.hasBoxArt) {
            Log.d(TAG, "Game $gameId already has artwork — skipping")
            return
        }

        val coverUrl = steamCoverUrl(steamAppId)
        val heroUrl  = steamHeroUrl(steamAppId)

        Log.d(TAG, "Fetching Steam artwork for appId=$steamAppId gameId=$gameId")

        // Download and cache box art through eOr's repository — stores in eOr's media directory
        // and writes the local path into game_media.box_art_local via MediaRepository
        val localCoverPath = runCatching {
            mediaRepository.downloadAndCacheBoxArt(gameId, coverUrl)
        }.getOrElse {
            Log.w(TAG, "Cover download failed for appId=$steamAppId: ${it.message}")
            null
        }

        // Download and cache hero/screenshot
        val localScreenshotPath = runCatching {
            mediaRepository.downloadAndCacheScreenshot(gameId, heroUrl)
        }.getOrElse {
            Log.w(TAG, "Hero download failed for appId=$steamAppId: ${it.message}")
            null
        }

        // If downloads succeeded, media rows are already updated by MediaRepository.
        // If downloads failed, write remote URLs so Coil can load directly until cached.
        if (localCoverPath == null && localScreenshotPath == null) {
            // Fallback: write remote URLs into GameMedia via upsertMedia
            val current = mediaRepository.getMediaForGame(gameId)
            val updated = (current ?: GameMedia(gameId = gameId)).copy(
                boxArtRemoteUrl       = coverUrl,
                screenshotRemoteUrl   = heroUrl
            )
            runCatching { mediaRepository.upsertMedia(updated) }
                .onSuccess { Log.d(TAG, "Wrote remote CDN URLs for appId=$steamAppId") }
                .onFailure { Log.w(TAG, "upsertMedia failed for appId=$steamAppId: ${it.message}") }
        } else {
            Log.i(TAG, "Artwork cached for appId=$steamAppId — cover=$localCoverPath")
        }
    }

    /**
     * Ensure artwork exists using remote URLs only (no download).
     * Used when the game was just inserted and we want immediate display before caching completes.
     */
    suspend fun setRemoteUrlsForSteamGame(gameId: Long, steamAppId: Int) {
        val existing = mediaRepository.getMediaForGame(gameId)
        if (existing != null && existing.hasBoxArt) return

        val media = (existing ?: GameMedia(gameId = gameId)).copy(
            boxArtRemoteUrl     = steamCoverUrl(steamAppId),
            screenshotRemoteUrl = steamHeroUrl(steamAppId)
        )
        runCatching { mediaRepository.upsertMedia(media) }
            .onFailure { Log.w(TAG, "setRemoteUrls failed for appId=$steamAppId: ${it.message}") }
    }

    companion object {
        private const val TAG = "PcGameArtworkResolver"

        fun steamCoverUrl(appId: Int)  = "https://cdn.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
        fun steamHeroUrl(appId: Int)   = "https://cdn.steamstatic.com/steam/apps/$appId/library_hero.jpg"
    }
}
