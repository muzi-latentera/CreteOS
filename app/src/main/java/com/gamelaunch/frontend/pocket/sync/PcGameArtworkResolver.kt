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
 * IMPORTANT: Steam CDN URLs are only valid for actual Steam AppIDs.
 * Never use GOG/Epic/Amazon IDs as Steam CDN AppIDs — they are different namespaces.
 *
 * Cover and hero are resolved independently:
 * - Existing cover does NOT skip hero resolution
 * - Cover download failure falls back to remote URL independently
 * - Hero download failure falls back to remote URL independently
 */
@Singleton
class PcGameArtworkResolver @Inject constructor(
    private val mediaRepository: MediaRepository
) {

    /**
     * Immediately set remote Steam CDN URLs for [gameId] so Coil can display
     * artwork before any background download completes.
     * Only call this for actual Steam AppIDs (source == "STEAM").
     *
     * Cover and hero are resolved independently — existing cover does not
     * prevent a missing hero from being set, and vice versa.
     */
    suspend fun setRemoteUrlsForSteamGame(gameId: Long, steamAppId: Int) {
        val existing = mediaRepository.getMediaForGame(gameId)
            ?: GameMedia(gameId = gameId)

        val coverUrl = steamCoverUrl(steamAppId)
        val heroUrl  = steamHeroUrl(steamAppId)

        // Only update fields that are currently blank — don't overwrite locally-cached paths
        val needsCover = existing.boxArtLocalPath.isNullOrBlank() && existing.boxArtRemoteUrl.isNullOrBlank()
        val needsHero  = existing.screenshotLocalPath.isNullOrBlank() && existing.screenshotRemoteUrl.isNullOrBlank()

        if (!needsCover && !needsHero) {
            Log.d(TAG, "Game $gameId already has cover and hero — skipping remote URL set")
            return
        }

        val updated = existing.copy(
            boxArtRemoteUrl     = if (needsCover) coverUrl else existing.boxArtRemoteUrl,
            screenshotRemoteUrl = if (needsHero)  heroUrl  else existing.screenshotRemoteUrl
        )

        runCatching { mediaRepository.upsertMedia(updated) }
            .onSuccess { Log.d(TAG, "Set remote URLs for appId=$steamAppId cover=$needsCover hero=$needsHero") }
            .onFailure { Log.w(TAG, "upsertMedia failed for appId=$steamAppId: ${it.message}") }
    }

    /**
     * Download and cache Steam artwork for [gameId], then update the database.
     * Cover and hero are downloaded and cached independently.
     * Only call this for actual Steam AppIDs (source == "STEAM").
     */
    suspend fun resolveForSteamGame(gameId: Long, steamAppId: Int) {
        val existing = mediaRepository.getMediaForGame(gameId)
            ?: GameMedia(gameId = gameId)

        val needsCover = existing.boxArtLocalPath.isNullOrBlank()
        val needsHero  = existing.screenshotLocalPath.isNullOrBlank()

        // Cover: download independently
        if (needsCover) {
            val localCoverPath = runCatching {
                mediaRepository.downloadAndCacheBoxArt(gameId, steamCoverUrl(steamAppId))
            }.getOrElse {
                Log.w(TAG, "Cover download failed for appId=$steamAppId: ${it.message}")
                null
            }

            if (localCoverPath == null) {
                // Fallback to remote URL if download fails
                val updated = (mediaRepository.getMediaForGame(gameId) ?: GameMedia(gameId = gameId))
                    .copy(boxArtRemoteUrl = steamCoverUrl(steamAppId))
                runCatching { mediaRepository.upsertMedia(updated) }
            } else {
                Log.d(TAG, "Cached cover for appId=$steamAppId → $localCoverPath")
            }
        }

        // Hero: download independently (separate from cover)
        if (needsHero) {
            val localHeroPath = runCatching {
                mediaRepository.downloadAndCacheScreenshot(gameId, steamHeroUrl(steamAppId))
            }.getOrElse {
                Log.w(TAG, "Hero download failed for appId=$steamAppId: ${it.message}")
                null
            }

            if (localHeroPath == null) {
                // Fallback to remote URL if download fails
                val updated = (mediaRepository.getMediaForGame(gameId) ?: GameMedia(gameId = gameId))
                    .copy(screenshotRemoteUrl = steamHeroUrl(steamAppId))
                runCatching { mediaRepository.upsertMedia(updated) }
            } else {
                Log.d(TAG, "Cached hero for appId=$steamAppId → $localHeroPath")
            }
        }
    }

    companion object {
        private const val TAG = "PcGameArtworkResolver"

        fun steamCoverUrl(appId: Int) = "https://cdn.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
        fun steamHeroUrl(appId: Int)  = "https://cdn.steamstatic.com/steam/apps/$appId/library_hero.jpg"
    }
}
