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
         * VERIFIED GFN canonical URLs — confirmed by device navigation to correct game page.
         * Key = Steam AppID. Value = full canonical URL including game-id, asset-id, lang.
         *
         * Acceptance test: URL opens directly to the correct game's page in the GFN app on device.
         * DO NOT add entries based on HTTP redirect checks alone.
         */
        val GFN_VERIFIED: Map<String, String> = mapOf(
            // ── Device-verified ✓ ──────────────────────────────────────────────────────────
            "2358720" to "https://play.geforcenow.com/games?game-id=2683ca75-de39-41dc-a7ea-0ffb7ecbac95&lang=en_GB&asset-id=01_b42e33e0-bdad-4885-8c93-b92dc8f47a5d",  // Black Myth: Wukong ✓
            "870780"  to "https://play.geforcenow.com/games?game-id=5aed2b8b-912f-4e9e-b8e3-dcd8c8613679&lang=en_GB&asset-id=01_57efc2f9-306d-478f-becc-ef43367a7068",  // Control Ultimate Edition ✓
            "3321460" to "https://play.geforcenow.com/games?game-id=ac01742f-b3dd-4be3-86bc-79ac43e54e54&lang=en_GB&asset-id=01_95161de3-501c-4af1-8674-d32a31c2b977",  // Crimson Desert ✓
            "286690"  to "https://play.geforcenow.com/games?game-id=fbc605a5-0d02-4a5c-a7f7-0d9d6eca31e9&lang=en_GB&asset-id=01_69834672-8911-4064-9cee-85385a19916a",  // Metro 2033 Redux ✓
            "43110"   to "https://play.geforcenow.com/games?game-id=fbc605a5-0d02-4a5c-a7f7-0d9d6eca31e9&lang=en_GB&asset-id=01_69834672-8911-4064-9cee-85385a19916a",  // Metro 2033 ✓
            "883710"  to "https://play.geforcenow.com/games?game-id=58de244f-7510-4baf-86a1-172448fda8e6&lang=en_GB&asset-id=01_fc3c325e-290b-49d2-8c57-d082e0d4cd4a",  // Resident Evil 2 Remake ✓
            "3768760" to "https://play.geforcenow.com/games?game-id=ce2eca28-aa8c-4b4b-a3e2-5ad03a9f8ecd&lang=en_GB&asset-id=01_22df286c-76ed-4e85-94d1-bf3f3f0041e8",  // 007 First Light ✓
            "3357650" to "https://play.geforcenow.com/games?game-id=c7781673-a43b-4476-825b-a8ce0bcffe88&lang=en_GB&asset-id=01_883552ba-189a-4ea9-82b1-d842da11991b",  // Pragmata ✓
            "1808500" to "https://play.geforcenow.com/games?game-id=dfdbc357-7f61-45cc-bf64-ae7117da12d5&lang=en_GB&asset-id=01_6adbd882-66c7-40bc-b47d-784eb38fd170",  // ARC Raiders ✓
            "292030"  to "https://play.geforcenow.com/games?game-id=23346751-e1e5-40c6-8899-ec3fe6962e3a&lang=en_GB&asset-id=01_b6fede87-dc14-444f-a3b3-120a4202adf4",  // The Witcher 3 ✓
            "1620730" to "https://play.geforcenow.com/games?game-id=6f5df9e0-9a34-4769-aaa3-e48f14805b99&lang=en_GB&asset-id=01_f5c72618-bff0-48bb-b26d-01a0029b9de1",  // Hell is Us ✓
            "2050650" to "https://play.geforcenow.com/games?game-id=d63e33a8-2f5e-4b4f-b6f5-f79c6938ca6a&lang=en_GB&asset-id=01_86a551ec-ad3e-43e8-b2c8-505df9868019",  // Resident Evil 4 Remake ✓
            "1903340" to "https://play.geforcenow.com/games?game-id=037a263a-adbf-4705-8509-76447080de75&lang=en_GB&asset-id=01_fba9542a-ad36-4ee4-a48b-96067a9ff491",  // Clair Obscur ✓
            "1091500" to "https://play.geforcenow.com/games?game-id=e5fc8a96-2cda-49ef-bd13-513bdc68045b&lang=en_GB&asset-id=01_742eeb39-c372-4b14-b0ff-2b2e8f02ee97",  // Cyberpunk 2077 ✓ (UUID proven: Elden Ring accidentally launched this)
            "cp2077_gog" to "https://play.geforcenow.com/games?game-id=e5fc8a96-2cda-49ef-bd13-513bdc68045b&lang=en_GB&asset-id=01_742eeb39-c372-4b14-b0ff-2b2e8f02ee97",  // Cyberpunk 2077 GOG alias
            // Non-Steam aliases (GOG/GamePass/Ubisoft — GFN doesn't care which store, opens game page then user selects their version)
            "bg3_gog"       to "https://play.geforcenow.com/games?game-id=095ad0c3-2167-45f1-aa80-1eceacbdeebb&lang=en_GB&asset-id=01_a81c5653-1d0c-44fc-a6ea-3a3a290d4036",  // Baldur's Gate 3 (GOG)
            "swo_gp"        to "https://play.geforcenow.com/games?game-id=5e99f1b6-6db5-404e-bd80-f9d5c86b64d5&lang=en_GB&asset-id=01_a15ad360-1b1f-4fb6-9be7-50f7e7db526b",  // Star Wars Outlaws (GamePass)
            "indyjones_gp"  to "https://play.geforcenow.com/games?game-id=3d6f95b6-0aae-432f-ab4a-c31a01dc5de6&lang=en_GB&asset-id=01_27843f55-52e1-4a74-80ce-eb637ab5c406",  // Indiana Jones (GamePass)
            "dis2_gp"       to "https://play.geforcenow.com/games?game-id=62dc8b5b-74a8-4172-9e78-d08f126813a9&lang=en_GB&asset-id=01_3833bbd1-9655-4ad9-8527-caea5c9e4777",  // Dishonored 2 (GamePass)
            "fh6_gp"        to "https://play.geforcenow.com/games?game-id=326bf7cb-4bfc-4c8b-bfcb-4fdcafb4ef62&lang=en_GB&asset-id=01_7b3a959a-38b7-4ac8-b592-5ad73801f3a5",  // Forza Horizon 6 (GamePass)
            "bf6_ea"        to "https://play.geforcenow.com/games?game-id=cb4e2225-1c30-456b-ac8e-1424f3218329&lang=en_GB&asset-id=01_8f7b8453-2ee0-4651-aab2-06c359a84a88",  // Battlefield 6 (EA)
            "2138710" to "https://play.geforcenow.com/games?game-id=ea68304b-9448-4002-a00e-3b816721bf1c&lang=en_GB&asset-id=01_5453e247-4a92-46f2-815b-3e7a8cad2f18",  // Sifu ✓ — AppID 2138710 (NOT 2767120 which is a DLC)
            "1771300" to "https://play.geforcenow.com/games?game-id=8a9b10a6-b8f4-46f3-a479-e480c304d78c&lang=en_GB&asset-id=01_21338a23-06ed-44eb-b493-366a33b5bb9e",  // Kingdom Come: Deliverance II ✓ (Steam AppID 1771300 confirmed)
            // ── NOT verified — open GFN library for these until confirmed ────────────────
            // Sifu 2767120 — WRONG AppID (that's a DLC). Correct AppID 2138710 now in VERIFIED above
            // Cyberpunk 2077 1091500 — now VERIFIED above (UUID proven via Elden Ring test)
            // Elden Ring 1245620 — REMOVED (was wrongly using Cyberpunk's UUID e5fc8a96)
            // BG3 1086940, Star Wars Outlaws 2379780, Indiana Jones 2677660 — not in Steam library (GOG/GamePass)
            // KCD2 2358550 — WRONG AppID; correct 1771300 now in VERIFIED above
        )

        /** Legacy alias: Steam AppID → game-id only (for seedGfnIds compat) */
        val GFN_CATALOG: Map<String, String> = GFN_VERIFIED.mapValues { (_, url) ->
            Regex("game-id=([^&]+)").find(url)?.groupValues?.get(1) ?: ""
        }.filter { it.value.isNotBlank() }

        // Kept empty — asset-ids are now embedded in GFN_VERIFIED canonical URLs
        val GFN_ASSET_IDS: Map<String, String> = emptyMap()
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

            // Preserve all sidecar data (IGDB, HLTB, GFN link, ROM path and local state).
            // A Steam playtime refresh must never reset fields owned by another sync.
            val existing = steamMetadataDao.getByAppId(appId)
            steamMetadataDao.upsert(
                existing?.copy(
                    playtimeMinutes = mins,
                    lastPlayedMs    = lastMs,
                    updatedAtMs     = System.currentTimeMillis()
                ) ?: SteamMetadataEntity(
                    steamAppId           = appId,
                    playtimeMinutes      = mins,
                    lastPlayedMs         = lastMs,
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
