package com.gamelaunch.frontend.pocket.data

import android.util.Log
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ── Room entity ────────────────────────────────────────────────────────────

@Entity(tableName = "hltb_cache", indices = [Index("steam_app_id", unique = true)])
data class HltbCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "steam_app_id") val steamAppId: String,
    @ColumnInfo(name = "game_title") val gameTitle: String,
    @ColumnInfo(name = "hltb_id") val hltbId: Int?,
    @ColumnInfo(name = "main_story_seconds") val mainStorySeconds: Int,
    @ColumnInfo(name = "main_extra_seconds") val mainExtraSeconds: Int,
    @ColumnInfo(name = "completionist_seconds") val completionistSeconds: Int,
    @ColumnInfo(name = "cached_at_ms") val cachedAtMs: Long = System.currentTimeMillis()
)

@Dao
interface HltbCacheDao {
    @Query("SELECT * FROM hltb_cache WHERE steam_app_id = :appId")
    suspend fun getByAppId(appId: String): HltbCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HltbCacheEntity)

    @Query("SELECT COUNT(*) FROM hltb_cache")
    suspend fun count(): Int
}

// ── Data model ─────────────────────────────────────────────────────────────

data class HltbTimes(
    val mainStoryHours: Float?,    // null = not found
    val mainExtraHours: Float?,
    val completionistHours: Float?
) {
    companion object {
        val EMPTY = HltbTimes(null, null, null)
    }

    fun formatMain()         = mainStoryHours?.let  { formatH(it) } ?: "—"
    fun formatExtra()        = mainExtraHours?.let  { formatH(it) } ?: "—"
    fun formatCompletionist()= completionistHours?.let { formatH(it) } ?: "—"

    private fun formatH(h: Float): String =
        if (h < 1f) "${(h * 60).toInt()}m"
        else if (h == h.toLong().toFloat()) "${h.toLong()}h"
        else "${h.toLong()}h ${((h % 1) * 60).toInt()}m"
}

// ── Provider ───────────────────────────────────────────────────────────────

@Singleton
class HowLongToBeatProvider @Inject constructor(
    private val httpClient: OkHttpClient,   // plain singleton OkHttpClient
    private val hltbCacheDao: HltbCacheDao
) {
    companion object {
        private const val TAG = "HltbProvider"
        private const val BASE = "https://howlongtobeat.com"
        private const val API  = "$BASE/api/search/site"
        // Cache is valid for 30 days
        private const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }

    /**
     * Get HLTB times for a Steam game.
     * Returns cached result if fresh, otherwise fetches from HLTB.
     */
    /** Returns cached TTB times if they exist and haven't expired, null otherwise. */
    suspend fun getCached(steamAppId: String): HltbTimes? = withContext(Dispatchers.IO) {
        val cached = hltbCacheDao.getByAppId(steamAppId) ?: return@withContext null
        if (System.currentTimeMillis() - cached.cachedAtMs > CACHE_TTL_MS) return@withContext null
        val times = cached.toHltbTimes()
        if (times.mainStoryHours == null && times.mainExtraHours == null) return@withContext null
        times
    }

    suspend fun getTimes(steamAppId: String, gameTitle: String): HltbTimes =
        withContext(Dispatchers.IO) {
            // Check cache first
            val cached = hltbCacheDao.getByAppId(steamAppId)
            if (cached != null && System.currentTimeMillis() - cached.cachedAtMs < CACHE_TTL_MS) {
                return@withContext cached.toHltbTimes()
            }

            // Fetch from HLTB
            // Distinguish network/HTTP failure (don't cache) from genuine no-match (do cache)
            val result = try {
                searchHltb(gameTitle)
            } catch (e: Exception) {
                // Network failure, 403, timeout — do NOT cache, will retry on next open
                android.util.Log.w(TAG, "HLTB network error for '$gameTitle': ${e.message}")
                return@withContext HltbTimes.EMPTY
            }

            if (result != null) {
                // Confident exact match — cache for 30 days
                hltbCacheDao.upsert(
                    HltbCacheEntity(
                        steamAppId           = steamAppId,
                        gameTitle            = gameTitle,
                        hltbId               = result.hltbId,
                        mainStorySeconds     = result.mainStorySeconds,
                        mainExtraSeconds     = result.mainExtraSeconds,
                        completionistSeconds = result.completionistSeconds
                    )
                )
                result.toHltbTimes()
            } else {
                // Search succeeded but no exact title match — cache this so we don't re-search
                // (This is distinct from a failure — the search ran and found nothing confident)
                hltbCacheDao.upsert(
                    HltbCacheEntity(
                        steamAppId = steamAppId, gameTitle = gameTitle,
                        hltbId = null, mainStorySeconds = 0,
                        mainExtraSeconds = 0, completionistSeconds = 0
                    )
                )
                HltbTimes.EMPTY
            }
        }

    /**
     * Batch prefetch HLTB data for a list of (appId, title) pairs.
     * Skips already-cached entries. Rate-limited to ~1 req/sec.
     */
    suspend fun batchPrefetch(games: List<Pair<String, String>>) =
        withContext(Dispatchers.IO) {
            val pending = games.filter { (appId, _) ->
                val cached = hltbCacheDao.getByAppId(appId)
                cached == null || System.currentTimeMillis() - cached.cachedAtMs > CACHE_TTL_MS
            }

            Log.i(TAG, "Batch prefetch: ${pending.size} games to fetch (${games.size - pending.size} cached)")

            pending.forEachIndexed { index, (appId, title) ->
                try {
                    val result = searchHltb(title)
                    hltbCacheDao.upsert(
                        HltbCacheEntity(
                            steamAppId           = appId,
                            gameTitle            = title,
                            hltbId               = result?.hltbId,
                            mainStorySeconds     = result?.mainStorySeconds ?: 0,
                            mainExtraSeconds     = result?.mainExtraSeconds ?: 0,
                            completionistSeconds = result?.completionistSeconds ?: 0
                        )
                    )
                    Log.d(TAG, "[$index/${pending.size}] $title → main=${result?.mainStorySeconds?.div(3600)}h")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch HLTB for $title: ${e.message}")
                }
                // Rate limit: 1 request per second to avoid 429
                if (index < pending.lastIndex) delay(1100)
            }

            Log.i(TAG, "Batch prefetch complete")
        }

    // ── Internal search ────────────────────────────────────────────────────

    private data class HltbResult(
        val hltbId: Int,
        val mainStorySeconds: Int,
        val mainExtraSeconds: Int,
        val completionistSeconds: Int
    )

    private fun HltbResult.toHltbTimes() = HltbTimes(
        if (mainStorySeconds > 0) mainStorySeconds / 3600f else null,
        if (mainExtraSeconds > 0) mainExtraSeconds / 3600f else null,
        if (completionistSeconds > 0) completionistSeconds / 3600f else null
    )

    private fun searchHltb(title: String): HltbResult? {
        val body = buildSearchPayload(title)
        val request = Request.Builder()
            .url(API)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36")
            .header("Referer", BASE)
            .header("Origin", BASE)
            .header("Accept", "application/json, text/plain, */*")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "HLTB search failed for '$title': HTTP ${response.code}")
            return null
        }

        val json = JSONObject(response.body?.string() ?: return null)
        val data = json.optJSONArray("data") ?: return null
        if (data.length() == 0) return null

        // Pick best match — ONLY accept exact normalized title match.
        // Never fall back to "first result" — that silently attaches the wrong game's times.
        // e.g. searching "Resident Evil" must not match "Resident Evil 2".
        val normalised = normaliseTitle(title)
        var exactMatch: JSONObject? = null

        for (i in 0 until minOf(data.length(), 8)) {
            val entry = data.getJSONObject(i)
            val entryTitle = normaliseTitle(entry.optString("game_name", ""))
            if (entryTitle == normalised) {
                exactMatch = entry
                break
            }
        }

        if (exactMatch == null) {
            android.util.Log.d(TAG, "No confident HLTB match for '$title' (normalised: '$normalised')")
            return null  // unmatched — do NOT use first result
        }

        return exactMatch.let {
            HltbResult(
                hltbId               = it.optInt("game_id", 0),
                mainStorySeconds     = it.optInt("comp_main", 0),
                mainExtraSeconds     = it.optInt("comp_plus", 0),
                completionistSeconds = it.optInt("comp_100", 0)
            )
        }
    }

    /** Normalise a title for comparison: lowercase, strip punctuation, collapse spaces. */
    private fun normaliseTitle(s: String): String =
        s.lowercase()
            .replace(Regex("[:™®©]"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun buildSearchPayload(title: String): String {
        val terms = JSONObject().apply {
            put("searchType", "games")
            put("searchTerms", org.json.JSONArray().apply {
                title.split(" ").forEach { put(it) }
            })
            put("searchPage", 1)
            put("size", 5)
            put("searchOptions", JSONObject().apply {
                put("games", JSONObject().apply {
                    put("userId", 0)
                    put("platform", "")
                    put("sortCategory", "popular")
                    put("rangeCategory", "main")
                    put("rangeTime", JSONObject().apply { put("min", JSONObject.NULL); put("max", JSONObject.NULL) })
                    put("gameplay", JSONObject().apply {
                        put("perspective", ""); put("flow", ""); put("genre", ""); put("subGenre", "")
                    })
                    put("rangeYear", JSONObject().apply { put("min", ""); put("max", "") })
                    put("modifier", "")
                })
                put("users", JSONObject().apply { put("sortCategory", "postcount") })
                put("lists", JSONObject().apply { put("sortCategory", "follows") })
                put("filter", ""); put("sort", 0); put("randomizer", 0)
            })
            put("useCache", true)
        }
        return terms.toString()
    }
}

private fun HltbCacheEntity.toHltbTimes() = HltbTimes(
    if (mainStorySeconds > 0) mainStorySeconds / 3600f else null,
    if (mainExtraSeconds > 0) mainExtraSeconds / 3600f else null,
    if (completionistSeconds > 0) completionistSeconds / 3600f else null
)
