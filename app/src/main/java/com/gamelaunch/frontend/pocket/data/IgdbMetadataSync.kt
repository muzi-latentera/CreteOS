package com.gamelaunch.frontend.pocket.data

import android.util.Log
import com.gamelaunch.frontend.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches game metadata (time to beat, developer, publisher, summary) from IGDB
 * using Twitch OAuth client credentials (stored in BuildConfig / local.properties).
 *
 * Stores TTB results in [HltbCacheEntity] — same table the UI reads for HLTB display.
 * Updates [SteamMetadataEntity] with developer/publisher/description if not yet populated.
 *
 * Token is cached in-memory and refreshed automatically when expired.
 */
@Singleton
class IgdbMetadataSync @Inject constructor(
    private val hltbCacheDao: HltbCacheDao,
    private val steamMetadataDao: SteamMetadataDao,
    private val httpClient: OkHttpClient,
) {
    private val tokenMutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0L

    private val CLIENT_ID     get() = BuildConfig.IGDB_CLIENT_ID
    private val CLIENT_SECRET get() = BuildConfig.IGDB_CLIENT_SECRET
    private val JSON = "application/json".toMediaType()
    private val TEXT = "text/plain".toMediaType()

    companion object {
        private const val TAG = "IgdbMetadataSync"
        private const val TWITCH_TOKEN_URL = "https://id.twitch.tv/oauth2/token"
        private const val IGDB_BASE = "https://api.igdb.com/v4"
    }

    // ── Token management ──────────────────────────────────────────────────

    private suspend fun getToken(): String = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiresAt - 60_000) {
            return@withLock cachedToken!!
        }
        val url = "$TWITCH_TOKEN_URL?client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET&grant_type=client_credentials"
        val req = Request.Builder().url(url).post("".toRequestBody(TEXT)).build()
        val resp = httpClient.newCall(req).execute()
        val body = resp.body?.string() ?: throw IllegalStateException("Empty token response")
        val json = JSONObject(body)
        cachedToken = json.getString("access_token")
        tokenExpiresAt = now + json.getLong("expires_in") * 1000
        Log.d(TAG, "Got new IGDB token, expires in ${json.getLong("expires_in")}s")
        cachedToken!!
    }

    private suspend fun igdbPost(endpoint: String, query: String): JSONArray {
        val token = getToken()
        val req = Request.Builder()
            .url("$IGDB_BASE/$endpoint")
            .addHeader("Client-ID", CLIENT_ID)
            .addHeader("Authorization", "Bearer $token")
            .post(query.toRequestBody(TEXT))
            .build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) {
            Log.w(TAG, "IGDB $endpoint returned ${resp.code}: ${resp.body?.string()?.take(200)}")
            return JSONArray("[]")
        }
        return JSONArray(resp.body?.string() ?: "[]")
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Fetch TTB + metadata for a single Steam AppID.
     * Called from the detail screen on first open when data is missing.
     */
    suspend fun syncGame(steamAppId: String, gameTitle: String): Unit = withContext(Dispatchers.IO) {
        if (CLIENT_ID.isBlank() || CLIENT_SECRET.isBlank()) {
            Log.w(TAG, "IGDB credentials not configured — skipping sync")
            return@withContext
        }

        try {
            // 1. Find IGDB ID by Steam AppID
            val extGames = igdbPost("external_games",
                "fields game,uid; where uid=\"$steamAppId\"; limit 1;")
            if (extGames.length() == 0) {
                Log.d(TAG, "No IGDB match for AppID $steamAppId ($gameTitle)")
                return@withContext
            }
            val igdbId = extGames.getJSONObject(0).getInt("game")

            // 2. Fetch game details
            val games = igdbPost("games",
                "fields name,summary,genres.name,involved_companies.company.name," +
                "involved_companies.developer,involved_companies.publisher,first_release_date; " +
                "where id=$igdbId; limit 1;")
            val game = if (games.length() > 0) games.getJSONObject(0) else null

            // 3. Fetch TTB
            val ttbArr = igdbPost("game_time_to_beats",
                "fields game_id,hastily,normally,completely; where game_id=$igdbId; limit 1;")
            val ttb = if (ttbArr.length() > 0) ttbArr.getJSONObject(0) else null

            // 4. Store TTB in hltb_cache
            if (ttb != null) {
                val mainSec = ttb.optInt("hastily", 0)
                val plusSec = ttb.optInt("normally", 0)
                val compSec = ttb.optInt("completely", 0)
                if (mainSec > 0 || plusSec > 0) {
                    hltbCacheDao.upsert(HltbCacheEntity(
                        steamAppId           = steamAppId,
                        gameTitle            = gameTitle,
                        hltbId               = igdbId,
                        mainStorySeconds     = mainSec,
                        mainExtraSeconds     = plusSec,
                        completionistSeconds = compSec
                    ))
                    Log.d(TAG, "$gameTitle TTB: ${mainSec/3600}h / ${plusSec/3600}h / ${compSec/3600}h")
                }
            }

            // 5. Fetch cover art from IGDB
            val coversArr = igdbPost("covers",
                "fields image_id; where game=$igdbId; limit 1;")
            val igdbCoverUrl = if (coversArr.length() > 0) {
                val imageId = coversArr.getJSONObject(0).optString("image_id")
                if (imageId.isNotBlank())
                    "https://images.igdb.com/igdb/image/upload/t_cover_big_2x/$imageId.jpg"
                else null
            } else null

            // 6. Update steam_metadata
            if (game != null || igdbCoverUrl != null) {
                val existing = steamMetadataDao.getByAppId(steamAppId)
                if (existing != null) {
                    var developer: String? = null
                    var publisher: String? = null
                    val companies = game?.optJSONArray("involved_companies")
                    if (companies != null) {
                        for (i in 0 until companies.length()) {
                            val ic = companies.getJSONObject(i)
                            val name = ic.optJSONObject("company")?.optString("name") ?: continue
                            if (ic.optBoolean("developer") && developer == null) developer = name
                            if (ic.optBoolean("publisher") && publisher == null) publisher = name
                        }
                    }
                    val summary = game?.optString("summary")?.takeIf { it.isNotBlank() }

                    steamMetadataDao.upsert(existing.copy(
                        developer    = developer    ?: existing.developer,
                        publisher    = publisher    ?: existing.publisher,
                        description  = summary      ?: existing.description,
                        igdbCoverUrl = igdbCoverUrl ?: existing.igdbCoverUrl,
                        updatedAtMs  = System.currentTimeMillis()
                    ))
                    Log.d(TAG, "$gameTitle enriched: dev=$developer cover=${igdbCoverUrl?.takeLast(20)}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "IGDB sync failed for $steamAppId: ${e.message}")
        }
    }

    /**
     * Seed pre-fetched IGDB results from the Python batch run into hltb_cache.
     * This is called once on first launch with a hardcoded dataset,
     * so users get TTB data immediately without waiting for network.
     */
    suspend fun seedPreFetchedData(
        steamAppId: String,
        gameTitle: String,
        mainSeconds: Int,
        plusSeconds: Int,
        completionistSeconds: Int,
        developer: String?,
        publisher: String?,
        summary: String?,
        coverUrl: String? = null,
        heroUrl: String? = null,
    ): Unit = withContext(Dispatchers.IO) {
        if (mainSeconds > 0 || plusSeconds > 0) {
            hltbCacheDao.upsert(HltbCacheEntity(
                steamAppId           = steamAppId,
                gameTitle            = gameTitle,
                hltbId               = null,
                mainStorySeconds     = mainSeconds,
                mainExtraSeconds     = plusSeconds,
                completionistSeconds = completionistSeconds
            ))
        }
        val existing = steamMetadataDao.getByAppId(steamAppId) ?: return@withContext
        if (existing.developer == null || existing.description == null || existing.igdbCoverUrl == null) {
            steamMetadataDao.upsert(existing.copy(
                developer    = developer ?: existing.developer,
                publisher    = publisher ?: existing.publisher,
                description  = summary   ?: existing.description,
                igdbCoverUrl = coverUrl  ?: existing.igdbCoverUrl,
                igdbHeroUrl  = heroUrl   ?: existing.igdbHeroUrl,
                updatedAtMs  = System.currentTimeMillis()
            ))
        }
    }

    /**
     * Sync metadata for an emulated game by searching IGDB by title.
     * Only applies exact title matches to avoid false positives.
     *
     * @param romPath The canonical ROM path (e.g. "emu:gc:luigis_mansion")
     * @param title The clean game title to search
     * @param system The EmulatorSystem for platform hints
     */
    suspend fun syncEmulatedGame(
        romPath: String,
        title: String,
        system: com.gamelaunch.frontend.pocket.emulation.EmulatorSystem
    ): Unit = withContext(Dispatchers.IO) {
        if (CLIENT_ID.isBlank() || CLIENT_SECRET.isBlank()) {
            Log.w(TAG, "IGDB credentials not configured — skipping emulated game sync")
            return@withContext
        }

        try {
            Log.d(TAG, "Syncing emulated game: $title ($romPath)")

            // Search IGDB by title
            val searchQuery = """
                fields name,cover.image_id,artworks.image_id,screenshots.image_id,summary,
                       involved_companies.company.name,involved_companies.developer,involved_companies.publisher,
                       game_modes.name;
                search "$title";
                limit 5;
            """.trimIndent()

            val results = igdbPost("games", searchQuery)
            if (results.length() == 0) {
                Log.d(TAG, "No IGDB results for: $title")
                return@withContext
            }

            // Find best match by title similarity — prefer exact match
            var bestMatch: JSONObject? = null
            var bestScore = 0

            for (i in 0 until results.length()) {
                val game = results.getJSONObject(i)
                val gameName = game.optString("name", "")
                
                val score = when {
                    gameName.equals(title, ignoreCase = true) -> 100  // Exact match
                    gameName.lowercase().startsWith(title.lowercase()) -> 80  // Starts with
                    gameName.lowercase().contains(title.lowercase()) -> 60  // Contains
                    else -> {
                        // Compute simple similarity
                        val titleLower = title.lowercase()
                        val nameLower = gameName.lowercase()
                        val commonChars = titleLower.count { nameLower.contains(it) }
                        (commonChars * 100) / maxOf(title.length, 1)
                    }
                }

                if (score > bestScore) {
                    bestScore = score
                    bestMatch = game
                }
            }

            // Only use result if we have a good match (>= 80 score, i.e., exact or starts with)
            if (bestMatch == null || bestScore < 80) {
                Log.d(TAG, "No good IGDB match for '$title' (best score: $bestScore)")
                return@withContext
            }

            val gameName = bestMatch.optString("name", title)
            Log.d(TAG, "Found IGDB match: '$gameName' (score: $bestScore)")

            // Extract cover URL
            val coverId = bestMatch.optJSONObject("cover")?.optString("image_id")
            val coverUrl = if (!coverId.isNullOrBlank()) {
                "https://images.igdb.com/igdb/image/upload/t_cover_big_2x/$coverId.jpg"
            } else null

            // Extract hero URL from screenshots or artworks
            val heroUrl = run {
                val screenshots = bestMatch.optJSONArray("screenshots")
                if (screenshots != null && screenshots.length() > 0) {
                    val imageId = screenshots.getJSONObject(0).optString("image_id")
                    if (imageId.isNotBlank()) {
                        return@run "https://images.igdb.com/igdb/image/upload/t_1080p/$imageId.jpg"
                    }
                }
                val artworks = bestMatch.optJSONArray("artworks")
                if (artworks != null && artworks.length() > 0) {
                    val imageId = artworks.getJSONObject(0).optString("image_id")
                    if (imageId.isNotBlank()) {
                        return@run "https://images.igdb.com/igdb/image/upload/t_1080p/$imageId.jpg"
                    }
                }
                null
            }

            // Extract developer/publisher
            var developer: String? = null
            var publisher: String? = null
            val companies = bestMatch.optJSONArray("involved_companies")
            if (companies != null) {
                for (i in 0 until companies.length()) {
                    val ic = companies.getJSONObject(i)
                    val name = ic.optJSONObject("company")?.optString("name") ?: continue
                    if (ic.optBoolean("developer") && developer == null) developer = name
                    if (ic.optBoolean("publisher") && publisher == null) publisher = name
                }
            }

            val summary = bestMatch.optString("summary")?.takeIf { it.isNotBlank() }

            // Update steam_metadata for this emulated game
            val existing = steamMetadataDao.getByAppId(romPath)
            if (existing != null) {
                steamMetadataDao.upsert(existing.copy(
                    developer    = developer    ?: existing.developer,
                    publisher    = publisher    ?: existing.publisher,
                    description  = summary      ?: existing.description,
                    igdbCoverUrl = coverUrl     ?: existing.igdbCoverUrl,
                    igdbHeroUrl  = heroUrl      ?: existing.igdbHeroUrl,
                    updatedAtMs  = System.currentTimeMillis()
                ))
                Log.d(TAG, "Updated metadata for $title: dev=$developer cover=${coverUrl?.takeLast(30)}")
            } else {
                Log.w(TAG, "No steam_metadata record for $romPath — skipping update")
            }

        } catch (e: Exception) {
            Log.w(TAG, "IGDB sync failed for emulated game '$title': ${e.message}")
        }
    }
}
