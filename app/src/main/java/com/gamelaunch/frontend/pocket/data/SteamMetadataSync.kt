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

        /**
         * GFN game-id → asset-id mapping.
         * Source: NVIDIA GFN share URLs (game-id + asset-id + lang).
         * The asset-id identifies the specific playable catalog asset/edition.
         */
        val GFN_ASSET_IDS: Map<String, String> = mapOf(
            "ea68304b-9448-4002-a00e-3b816721bf1c" to "01_5453e247-4a92-46f2-815b-3e7a8cad2f18", // Sifu
            "8e1750d0-8502-45ec-9d0a-a7e97c0ca7bf" to "01_d5cfa8c7-9cf4-47e9-931c-e6f455c32e4f", // AC Shadows
            "62dc8b5b-74a8-4172-9e78-d08f126813a9" to "01_3833bbd1-9655-4ad9-8527-caea5c9e4777", // Dishonored 2
            "326bf7cb-4bfc-4c8b-bfcb-4fdcafb4ef62" to "01_7b3a959a-38b7-4ac8-b592-5ad73801f3a5", // Forza Horizon 6
            "ce2eca28-aa8c-4b4b-a3e2-5ad03a9f8ecd" to "01_22df286c-76ed-4e85-94d1-bf3f3f0041e8", // 007 First Light
            "c7781673-a43b-4476-825b-a8ce0bcffe88" to "01_883552ba-189a-4ea9-82b1-d842da11991b", // Pragmata
            "cb4e2225-1c30-456b-ac8e-1424f3218329" to "01_8f7b8453-2ee0-4651-aab2-06c359a84a88", // Battlefield 6
            "58de244f-7510-4baf-86a1-172448fda8e6" to "01_fc3c325e-290b-49d2-8c57-d082e0d4cd4a", // RE2 Remake
            "5aed2b8b-912f-4e9e-b8e3-dcd8c8613679" to "01_57efc2f9-306d-478f-becc-ef43367a7068", // Control
            "5e99f1b6-6db5-404e-bd80-f9d5c86b64d5" to "01_a15ad360-1b1f-4fb6-9be7-50f7e7db526b", // Star Wars Outlaws
            "3d6f95b6-0aae-432f-ab4a-c31a01dc5de6" to "01_27843f55-52e1-4a74-80ce-eb637ab5c406", // Indiana Jones
            "037a263a-adbf-4705-8509-76447080de75" to "01_fba9542a-ad36-4ee4-a48b-96067a9ff491", // Clair Obscur
            "8a9b10a6-b8f4-46f3-a479-e480c304d78c" to "01_21338a23-06ed-44eb-b493-366a33b5bb9e", // KCD2
            "2683ca75-de39-41dc-a7ea-0ffb7ecbac95" to "01_b42e33e0-bdad-4885-8c93-b92dc8f47a5d", // Black Myth
            "fbc605a5-0d02-4a5c-a7f7-0d9d6eca31e9" to "01_69834672-8911-4064-9cee-85385a19916a", // Metro 2033
            "095ad0c3-2167-45f1-aa80-1eceacbdeebb" to "01_a81c5653-1d0c-44fc-a6ea-3a3a290d4036", // BG3
            "e5fc8a96-2cda-49ef-bd13-513bdc68045b" to "01_742eeb39-c372-4b14-b0ff-2b2e8f02ee97", // Cyberpunk
            "dfdbc357-7f61-45cc-bf64-ae7117da12d5" to "01_6adbd882-66c7-40bc-b47d-784eb38fd170", // ARC Raiders
            "23346751-e1e5-40c6-8899-ec3fe6962e3a" to "01_b6fede87-dc14-444f-a3b3-120a4202adf4", // Witcher 3
            "6f5df9e0-9a34-4769-aaa3-e48f14805b99" to "01_f5c72618-bff0-48bb-b26d-01a0029b9de1", // Hell is Us
            "d63e33a8-2f5e-4b4f-b6f5-f79c6938ca6a" to "01_86a551ec-ad3e-43e8-b2c8-505df9868019", // RE4 Remake
            "ac01742f-b3dd-4be3-86bc-79ac43e54e54" to "01_95161de3-501c-4af1-8674-d32a31c2b977", // Crimson Desert
            "3321460" to "ac01742f-b3dd-4be3-86bc-79ac43e54e54", // (appid alias)
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
