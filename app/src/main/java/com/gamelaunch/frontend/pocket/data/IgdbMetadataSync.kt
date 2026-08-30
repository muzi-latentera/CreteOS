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

internal data class IgdbTtbSeconds(
    val main: Int,
    val mainExtra: Int,
    val completionist: Int
)

/** IGDB averages can occasionally contradict their own main < extras < completion semantics. */
internal fun consistentIgdbTtb(hastily: Int, normally: Int, completely: Int): IgdbTtbSeconds {
    var main = hastily.coerceAtLeast(0)
    var mainExtra = normally.coerceAtLeast(0)
    val completionist = completely.coerceAtLeast(0)
    if ((mainExtra > 0 && main > mainExtra) || (completionist > 0 && main > completionist)) main = 0
    if (completionist > 0 && mainExtra > completionist) mainExtra = 0
    return IgdbTtbSeconds(main, mainExtra, completionist)
}

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
                val times = consistentIgdbTtb(
                    ttb.optInt("hastily", 0),
                    ttb.optInt("normally", 0),
                    ttb.optInt("completely", 0)
                )
                if (times.main > 0 || times.mainExtra > 0 || times.completionist > 0) {
                    hltbCacheDao.upsert(HltbCacheEntity(
                        steamAppId           = steamAppId,
                        gameTitle            = gameTitle,
                        hltbId               = igdbId,
                        mainStorySeconds     = times.main,
                        mainExtraSeconds     = times.mainExtra,
                        completionistSeconds = times.completionist
                    ))
                    Log.d(
                        TAG,
                        "$gameTitle TTB: ${times.main / 3600}h / ${times.mainExtra / 3600}h / " +
                            "${times.completionist / 3600}h"
                    )
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
     * Fetch metadata for a game that has no Steam AppID (Epic/GOG/Amazon imports, for example).
     * These stores expose provider-local IDs that IGDB cannot resolve through `external_games`, so
     * use a conservative exact-normalised title match instead. Results are stored under [gameKey]
     * in the same sidecar tables consumed by the detail screen and HLTB panel.
     */
    suspend fun syncGameByTitle(gameKey: String, gameTitle: String): Unit = withContext(Dispatchers.IO) {
        if (CLIENT_ID.isBlank() || CLIENT_SECRET.isBlank()) {
            Log.w(TAG, "IGDB credentials not configured — skipping title sync")
            return@withContext
        }

        try {
            val escapedTitle = gameTitle.replace("\\", "\\\\").replace("\"", "\\\"")
            val results = igdbPost(
                "games",
                """
                    fields id,name,cover.image_id,artworks.image_id,screenshots.image_id,summary,
                           involved_companies.company.name,involved_companies.developer,
                           involved_companies.publisher;
                    search "$escapedTitle";
                    limit 20;
                """.trimIndent()
            )
            val requestedTitle = normalizeTitle(gameTitle)
            val match = (0 until results.length())
                .map { results.getJSONObject(it) }
                .firstOrNull { normalizeTitle(it.optString("name")) == requestedTitle }
            if (match == null) {
                Log.d(TAG, "No exact IGDB title match for '$gameTitle'")
                return@withContext
            }

            val igdbId = match.optInt("id", 0)
            if (igdbId <= 0) return@withContext

            val ttbResults = igdbPost(
                "game_time_to_beats",
                "fields game_id,hastily,normally,completely; where game_id=$igdbId; limit 1;"
            )
            val ttb = ttbResults.optJSONObject(0)
            val times = consistentIgdbTtb(
                ttb?.optInt("hastily", 0) ?: 0,
                ttb?.optInt("normally", 0) ?: 0,
                ttb?.optInt("completely", 0) ?: 0
            )
            // Cache legitimate no-TTB results too, preventing a fresh network request on every
            // library emission for games where IGDB has metadata but no duration aggregate.
            hltbCacheDao.upsert(
                HltbCacheEntity(
                    steamAppId = gameKey,
                    gameTitle = gameTitle,
                    hltbId = igdbId,
                    mainStorySeconds = times.main,
                    mainExtraSeconds = times.mainExtra,
                    completionistSeconds = times.completionist
                )
            )

            val coverId = match.optJSONObject("cover")?.optString("image_id")
            val coverUrl = coverId?.takeIf { it.isNotBlank() }?.let {
                "https://images.igdb.com/igdb/image/upload/t_cover_big_2x/$it.jpg"
            }
            // Screenshots are reliably full-frame landscape images. IGDB's artwork collection
            // can also contain ultra-wide wordmarks/logos, which look broken when cropped into
            // the detail hero (Dishonored 2's 1920x273 monochrome logo was one such result).
            val heroId = match.optJSONArray("screenshots")?.optJSONObject(0)?.optString("image_id")
                ?.takeIf { it.isNotBlank() }
                ?: match.optJSONArray("artworks")?.optJSONObject(0)?.optString("image_id")
                    ?.takeIf { it.isNotBlank() }
            val heroUrl = heroId?.let {
                "https://images.igdb.com/igdb/image/upload/t_1080p/$it.jpg"
            }

            var developer: String? = null
            var publisher: String? = null
            match.optJSONArray("involved_companies")?.let { companies ->
                for (index in 0 until companies.length()) {
                    val involvement = companies.optJSONObject(index) ?: continue
                    val name = involvement.optJSONObject("company")?.optString("name")
                        ?.takeIf { it.isNotBlank() } ?: continue
                    if (involvement.optBoolean("developer") && developer == null) developer = name
                    if (involvement.optBoolean("publisher") && publisher == null) publisher = name
                }
            }
            val summary = match.optString("summary").takeIf { it.isNotBlank() }
            val existing = steamMetadataDao.getByAppId(gameKey)
                ?: SteamMetadataEntity(steamAppId = gameKey)
            steamMetadataDao.upsert(
                existing.copy(
                    developer = developer ?: existing.developer,
                    publisher = publisher ?: existing.publisher,
                    description = summary ?: existing.description,
                    igdbCoverUrl = coverUrl ?: existing.igdbCoverUrl,
                    igdbHeroUrl = heroUrl ?: existing.igdbHeroUrl,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
            Log.d(TAG, "$gameTitle title-enriched: IGDB=$igdbId cover=${coverUrl != null}")
        } catch (error: Exception) {
            Log.w(TAG, "IGDB title sync failed for '$gameTitle': ${error.message}")
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
     * Uses title similarity plus the emulated platform to avoid same-name ports and remakes.
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
                fields id,name,cover.image_id,artworks.image_id,screenshots.image_id,summary,platforms.name,platforms.abbreviation,
                       involved_companies.company.name,involved_companies.developer,involved_companies.publisher,
                       game_modes.name;
                search "$title";
                limit 20;
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
                
                val normalizedGameName = normalizeTitle(gameName)
                val normalizedSearchTitle = normalizeTitle(title)
                val titleScore = when {
                    normalizedGameName == normalizedSearchTitle -> 100
                    normalizedGameName.startsWith(normalizedSearchTitle) -> 80
                    normalizedGameName.contains(normalizedSearchTitle) -> 60
                    else -> {
                        val commonChars = normalizedSearchTitle.count { normalizedGameName.contains(it) }
                        (commonChars * 100) / maxOf(normalizedSearchTitle.length, 1)
                    }
                }
                val platforms = game.optJSONArray("platforms")
                val platformMatch = platforms?.let { resultPlatforms ->
                    (0 until resultPlatforms.length()).any { index ->
                        val platform = resultPlatforms.optJSONObject(index) ?: return@any false
                        platformMatches(
                            system,
                            platform.optString("name"),
                            platform.optString("abbreviation")
                        )
                    }
                } == true
                val score = titleScore + when {
                    platformMatch -> 30
                    platforms != null && platforms.length() > 0 -> -10
                    else -> 0
                }

                if (score > bestScore) {
                    bestScore = score
                    bestMatch = game
                }
            }

            // Require both an exact normalized title and the emulated platform. A looser prefix
            // match can select fan projects such as "New Super Mario Bros. Deluxe".
            if (bestMatch == null || bestScore < 120) {
                Log.d(TAG, "No good IGDB match for '$title' (best score: $bestScore)")
                return@withContext
            }

            val gameName = bestMatch.optString("name", title)
            val igdbId = bestMatch.optInt("id", 0)
            Log.d(TAG, "Found IGDB match: '$gameName' (score: $bestScore)")

            // ROMs do not have a Steam external ID, so use the platform-matched IGDB game ID
            // directly for the same time-to-beat data that Steam games receive.
            if (igdbId > 0) {
                val ttbResults = igdbPost(
                    "game_time_to_beats",
                    "fields game_id,hastily,normally,completely; where game_id=$igdbId; limit 1;"
                )
                val ttb = ttbResults.optJSONObject(0)
                val times = consistentIgdbTtb(
                    ttb?.optInt("hastily", 0) ?: 0,
                    ttb?.optInt("normally", 0) ?: 0,
                    ttb?.optInt("completely", 0) ?: 0
                )
                hltbCacheDao.upsert(
                    HltbCacheEntity(
                        steamAppId = romPath,
                        gameTitle = title,
                        hltbId = igdbId,
                        mainStorySeconds = times.main,
                        mainExtraSeconds = times.mainExtra,
                        completionistSeconds = times.completionist
                    )
                )
                Log.d(
                    TAG,
                    "$title TTB: ${times.main / 3600}h / ${times.mainExtra / 3600}h / " +
                        "${times.completionist / 3600}h"
                )
            }

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

    private fun normalizeTitle(value: String): String = java.text.Normalizer
        .normalize(value, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun platformMatches(
        system: com.gamelaunch.frontend.pocket.emulation.EmulatorSystem,
        name: String,
        abbreviation: String
    ): Boolean {
        val normalizedName = normalizeTitle(name)
        val normalizedAbbreviation = normalizeTitle(abbreviation)
        val aliases = when (system) {
            com.gamelaunch.frontend.pocket.emulation.EmulatorSystem.GAMECUBE ->
                listOf("nintendo gamecube", "gamecube", "gcn")
            com.gamelaunch.frontend.pocket.emulation.EmulatorSystem.WII -> listOf("nintendo wii", "wii")
            com.gamelaunch.frontend.pocket.emulation.EmulatorSystem.WIIU -> listOf("nintendo wii u", "wii u")
            com.gamelaunch.frontend.pocket.emulation.EmulatorSystem.SWITCH -> listOf("nintendo switch", "switch")
            com.gamelaunch.frontend.pocket.emulation.EmulatorSystem.GBA -> listOf("game boy advance", "gba")
            com.gamelaunch.frontend.pocket.emulation.EmulatorSystem.NDS -> listOf("nintendo ds", "nds")
            com.gamelaunch.frontend.pocket.emulation.EmulatorSystem.N3DS -> listOf("nintendo 3ds", "3ds")
            com.gamelaunch.frontend.pocket.emulation.EmulatorSystem.PS2 -> listOf("playstation 2", "ps2")
            com.gamelaunch.frontend.pocket.emulation.EmulatorSystem.PSP ->
                listOf("playstation portable", "psp")
            else -> listOf(system.displayName, system.id)
        }
        return aliases.any { alias ->
            val normalizedAlias = normalizeTitle(alias)
            normalizedName == normalizedAlias || normalizedAbbreviation == normalizedAlias
        }
    }
}
