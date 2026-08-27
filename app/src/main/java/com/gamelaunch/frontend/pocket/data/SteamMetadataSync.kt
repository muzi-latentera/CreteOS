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

        /**
         * Known GFN UUID → Steam AppID mappings.
         * Source: manually extracted from play.geforcenow.com game detail URLs.
         * Key = Steam AppID, Value = GFN UUID for deep linking.
         */
        val GFN_CATALOG: Map<String, String> = mapOf(
            "1903340" to "037a263a-adbf-4705-8509-76447080de75", // Clair Obscur: Expedition 33
            "2358720" to "2683ca75-de39-41dc-a7ea-0ffb7ecbac95", // Black Myth: Wukong
            "870780"  to "5aed2b8b-912f-4e9e-b8e3-dcd8c8613679", // Control Ultimate Edition
            "3321460" to "ac01742f-b3dd-4be3-86bc-79ac43e54e54", // Crimson Desert
            "286690"  to "fbc605a5-0d02-4a5c-a7f7-0d9d6eca31e9", // Metro 2033 Redux
            "43110"   to "fbc605a5-0d02-4a5c-a7f7-0d9d6eca31e9", // Metro 2033
            "883710"  to "58de244f-7510-4baf-86a1-172448fda8e6", // Resident Evil 2 Remake
            "3768760" to "ce2eca28-aa8c-4b4b-a3e2-5ad03a9f8ecd", // 007 First Light
            "3357650" to "c7781673-a43b-4476-825b-a8ce0bcffe88", // Pragmata
            "1808500" to "dfdbc357-7f61-45cc-bf64-ae7117da12d5", // ARC Raiders
            "292030"  to "23346751-e1e5-40c6-8899-ec3fe6962e3a", // The Witcher 3
            "1620730" to "6f5df9e0-9a34-4769-aaa3-e48f14805b99", // Hell is Us
            "2050650" to "d63e33a8-2f5e-4b4f-b6f5-f79c6938ca6a", // Resident Evil 4 Remake
            "2358550" to "8a9b10a6-b8f4-46f3-a479-e480c304d78c", // Kingdom Come: Deliverance 2
            "1091500" to "e5fc8a96-2cda-49ef-bd13-513bdc68045b", // Cyberpunk 2077
            "1086940" to "095ad0c3-2167-45f1-aa80-1eceacbdeebb", // Baldur's Gate 3
            "2379780" to "5e99f1b6-6db5-404e-bd80-f9d5c86b64d5", // Star Wars Outlaws
            "2677660" to "3d6f95b6-0aae-432f-ab4a-c31a01dc5de6", // Indiana Jones and the Great Circle
            "2767120" to "ea68304b-9448-4002-a00e-3b816721bf1c", // Sifu
            "1245620" to "e5fc8a96-2cda-49ef-bd13-513bdc68045b", // Elden Ring (shares GFN with CP2077? check)
            // Additional from user-provided URLs
            "228980"  to "095ad0c3-2167-45f1-aa80-1eceacbdeebb", // BG3 alternate appid
        )
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
        val url = "https://store.steampowered.com/api/appdetails?appids=$steamAppId"
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

            // Use detailed_description (richer), strip HTML tags
            val rawDesc = data.optString("detailed_description").takeIf { it.isNotBlank() }
                ?: data.optString("short_description").takeIf { it.isNotBlank() }
            val description = rawDesc?.let { raw ->
                raw.replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s{2,}"), " ")
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
                    .trim()
                    .takeIf { it.length > 20 }
            }

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

    /**
     * Write known GFN UUIDs from [GFN_CATALOG] into steam_metadata rows.
     * Idempotent — safe to call on every cold start.
     */
    suspend fun seedGfnIds(): Unit = withContext(Dispatchers.IO) {
        var seeded = 0
        for ((appId, gfnId) in GFN_CATALOG) {
            try {
                steamMetadataDao.setGfnId(appId, gfnId)
                seeded++
            } catch (_: Exception) { /* row may not exist yet — will be set after library sync */ }
        }
        Log.d(TAG, "Seeded $seeded GFN IDs")
    }
}
