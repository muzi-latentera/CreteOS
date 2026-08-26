package com.gamelaunch.frontend.pocket.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs real Steam playtime and achievements into [SteamMetadataDao].
 *
 * Uses the same Steam Web API key already stored in Talos/CreteOS config.
 * This is the source of truth for:
 *   - Total playtime (minutes)
 *   - Last played timestamp
 *   - Achievement counts
 *
 * Does NOT touch eOr's play_count — that remains CreteOS launch count only.
 */
@Singleton
class SteamMetadataSync @Inject constructor(
    private val httpClient: OkHttpClient,
    private val steamMetadataDao: SteamMetadataDao
) {
    companion object {
        private const val TAG = "SteamMetadataSync"
        private const val STEAM_API_BASE = "https://api.steampowered.com"

        // Loaded from local.properties → BuildConfig, never stored in source
        private val STEAM_API_KEY get() = com.gamelaunch.frontend.BuildConfig.STEAM_API_KEY
        private val STEAM_ID      get() = com.gamelaunch.frontend.BuildConfig.STEAM_ID
    }

    /**
     * Sync playtime for all owned Steam games.
     * Upserts into steam_metadata without touching eOr tables.
     */
    suspend fun syncLibrary(): Int = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting Steam library sync")

        val url = "$STEAM_API_BASE/IPlayerService/GetOwnedGames/v1" +
            "?key=$STEAM_API_KEY&steamid=$STEAM_ID" +
            "&include_appinfo=1&include_played_free_games=1&format=json"

        val response = httpClient.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "CreteOS/1.0 (Android)")
                .build()
        ).execute()

        if (!response.isSuccessful) {
            Log.w(TAG, "Steam GetOwnedGames failed: HTTP ${response.code}")
            return@withContext 0
        }

        val body   = response.body?.string() ?: return@withContext 0
        val games  = JSONObject(body).optJSONObject("response")
                                     ?.optJSONArray("games")
                     ?: return@withContext 0

        var synced = 0
        for (i in 0 until games.length()) {
            val g      = games.getJSONObject(i)
            val appId  = g.optInt("appid", 0).toString()
            val mins   = g.optInt("playtime_forever", 0)
            val lastMs = g.optLong("rtime_last_played", 0L)
                .let { if (it > 0) it * 1000L else null }

            // Preserve existing achievement data if already fetched
            val existing = steamMetadataDao.getByAppId(appId)
            steamMetadataDao.upsert(
                SteamMetadataEntity(
                    steamAppId           = appId,
                    playtimeMinutes      = mins,
                    lastPlayedMs         = lastMs,
                    achievementsUnlocked = existing?.achievementsUnlocked ?: 0,
                    achievementsTotal    = existing?.achievementsTotal ?: 0,
                    developer            = existing?.developer,
                    publisher            = existing?.publisher,
                    description          = existing?.description,
                    releaseDate          = existing?.releaseDate,
                    updatedAtMs          = System.currentTimeMillis()
                )
            )
            synced++
        }

        Log.i(TAG, "Steam library sync complete: $synced games")
        synced
    }

    /**
     * Fetch achievements for a specific game.
     * Safe to call per-game on detail screen open (rate: ~1 req/game).
     */
    suspend fun syncAchievements(steamAppId: String): Unit = withContext(Dispatchers.IO) {
        // GetPlayerAchievements — returns achieved count for this user
        val playerUrl = "$STEAM_API_BASE/ISteamUserStats/GetPlayerAchievements/v1" +
            "?key=$STEAM_API_KEY&steamid=$STEAM_ID&appid=$steamAppId&format=json"

        try {
            val resp = httpClient.newCall(
                Request.Builder().url(playerUrl)
                    .header("User-Agent", "CreteOS/1.0 (Android)")
                    .build()
            ).execute()

            if (!resp.isSuccessful) {
                // 400 = game has no achievements, silently ignore
                if (resp.code != 400) {
                    Log.d(TAG, "Achievements for $steamAppId: HTTP ${resp.code}")
                }
                return@withContext
            }

            val body         = resp.body?.string() ?: return@withContext
            val achievements = JSONObject(body)
                .optJSONObject("playerstats")
                ?.optJSONArray("achievements")
                ?: return@withContext

            val total    = achievements.length()
            val unlocked = (0 until total).count {
                achievements.getJSONObject(it).optInt("achieved", 0) == 1
            }

            val existing = steamMetadataDao.getByAppId(steamAppId)
            if (existing != null) {
                steamMetadataDao.upsert(
                    existing.copy(
                        achievementsUnlocked    = unlocked,
                        achievementsTotal       = total,
                        achievementsSyncedAtMs  = System.currentTimeMillis(),
                        updatedAtMs             = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "$steamAppId achievements: $unlocked/$total")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Achievement sync failed for $steamAppId: ${e.message}")
        }
    }

    /**
     * Fetch developer, publisher, and short description from Steam Store API.
     * Free endpoint — no API key required.
     * Call once per game when detail screen opens and developer is still null.
     */
    suspend fun fetchAppDetails(steamAppId: String): Unit = withContext(Dispatchers.IO) {
        val url = "https://store.steampowered.com/api/appdetails?appids=$steamAppId&filters=basic"
        try {
            val resp = httpClient.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", "CreteOS/1.0 (Android)")
                    .build()
            ).execute()

            if (!resp.isSuccessful) {
                Log.w(TAG, "fetchAppDetails $steamAppId: HTTP ${resp.code}")
                return@withContext
            }

            val body   = resp.body?.string() ?: return@withContext
            val root   = JSONObject(body)
            val data   = root.optJSONObject(steamAppId)
                ?.takeIf { it.optBoolean("success", false) }
                ?.optJSONObject("data")
                ?: return@withContext

            val developers = data.optJSONArray("developers")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.joinToString(", ") }
            val publishers = data.optJSONArray("publishers")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.joinToString(", ") }
            val description = data.optString("short_description").takeIf { it.isNotBlank() }

            if (developers == null && publishers == null && description == null) return@withContext

            val existing = steamMetadataDao.getByAppId(steamAppId)
            if (existing != null) {
                steamMetadataDao.upsert(
                    existing.copy(
                        developer   = developers ?: existing.developer,
                        publisher   = publishers ?: existing.publisher,
                        description = description ?: existing.description,
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "$steamAppId details: dev='$developers' pub='$publishers'")
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAppDetails failed for $steamAppId: ${e.message}")
        }
    }
}
