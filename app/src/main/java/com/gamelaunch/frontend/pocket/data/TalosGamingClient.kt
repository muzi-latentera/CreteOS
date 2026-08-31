package com.gamelaunch.frontend.pocket.data

import com.gamelaunch.frontend.data.preferences.AppDataStore
import com.gamelaunch.frontend.domain.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

data class TalosGamingProfile(
    val platform: String,
    val displayName: String,
)

data class TalosGameStats(
    val platform: String,
    val externalGameId: String,
    val name: String,
    val iconUrl: String?,
    val totalPlaytimeMinutes: Int,
    val lastPlayedMs: Long?,
    val knownAchievementsUnlocked: Int = 0,
    val knownAchievementsTotal: Int = 0,
)

data class TalosAchievementSummary(
    val unlocked: Int,
    val total: Int,
)

data class TalosSnapshot(
    val profiles: List<TalosGamingProfile>,
    val games: List<TalosGameStats>,
)

data class TalosPairResult(
    val vpsUrl: String,
    val expiresInSeconds: Long,
)

@Singleton
class TalosGamingClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val dataStore: AppDataStore,
) {
    private val mutex = Mutex()
    private var snapshot: TalosSnapshot? = null
    private var snapshotFetchedAtMs: Long = 0L
    private val achievementCache = mutableMapOf<String, Pair<Long, TalosAchievementSummary>>()

    suspend fun configuredUrl(): String = dataStore.talosVpsUrl.first()

    suspend fun isConnected(): Boolean =
        dataStore.talosVpsUrl.first().isNotBlank() && dataStore.talosCreteosToken.first().isNotBlank()

    suspend fun pair(vpsUrl: String, code: String): Result<TalosPairResult> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = normalizeBaseUrl(vpsUrl)
            require(code.trim().length == 6) { "Enter the six-character Talos pairing code." }
            val body = JSONObject()
                .put("code", code.trim().uppercase())
                .put("clientName", "CreteOS")
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$baseUrl/api/pairing/creteos")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(apiError(text, response.code))
                val json = JSONObject(text)
                val token = json.optString("token")
                require(token.isNotBlank()) { "Talos did not return a CreteOS token." }
                dataStore.setTalosConnection(baseUrl, token)
                mutex.withLock {
                    snapshot = null
                    snapshotFetchedAtMs = 0L
                    achievementCache.clear()
                }
                TalosPairResult(baseUrl, json.optLong("expiresIn", 0L))
            }
        }
    }

    suspend fun disconnect() {
        dataStore.clearTalosConnection()
        mutex.withLock {
            snapshot = null
            snapshotFetchedAtMs = 0L
            achievementCache.clear()
        }
    }

    suspend fun refresh(force: Boolean = false): Result<TalosSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                val now = System.currentTimeMillis()
                snapshot?.takeIf { !force && now - snapshotFetchedAtMs < SNAPSHOT_TTL_MS }
                    ?.let { return@runCatching it }

                val connection = connectionOrThrow()
                val request = Request.Builder()
                    .url("${connection.first}/api/creteos/gaming/snapshot")
                    .header("Authorization", "Bearer ${connection.second}")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException(apiError(text, response.code))
                    parseSnapshot(JSONObject(text)).also {
                        snapshot = it
                        snapshotFetchedAtMs = now
                    }
                }
            }
        }
    }

    suspend fun statsFor(game: Game, steamAppId: String): TalosGameStats? {
        val games = refresh().getOrNull()?.games ?: return null
        return matchStats(game, steamAppId, games)
    }

    suspend fun achievementSummary(stats: TalosGameStats): Result<TalosAchievementSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = "${stats.platform}:${stats.externalGameId}"
                val now = System.currentTimeMillis()
                mutex.withLock {
                    achievementCache[key]?.takeIf { now - it.first < ACHIEVEMENT_TTL_MS }
                        ?.second
                        ?.let { return@runCatching it }
                }

                val connection = connectionOrThrow()
                val url = connection.first.toHttpUrl().newBuilder()
                    .addPathSegments("api/creteos/gaming/achievements")
                    .addQueryParameter("platform", stats.platform)
                    .addQueryParameter("gameId", stats.externalGameId)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer ${connection.second}")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException(apiError(text, response.code))
                    val achievements = JSONObject(text).optJSONArray("achievements")
                    var unlocked = 0
                    val total = achievements?.length() ?: 0
                    repeat(total) { index ->
                        if (achievements?.optJSONObject(index)?.optBoolean("unlocked", false) == true) unlocked++
                    }
                    TalosAchievementSummary(unlocked, total).also { result ->
                        mutex.withLock { achievementCache[key] = now to result }
                    }
                }
            }
        }

    private suspend fun connectionOrThrow(): Pair<String, String> {
        val url = dataStore.talosVpsUrl.first()
        val token = dataStore.talosCreteosToken.first()
        check(url.isNotBlank() && token.isNotBlank()) { "Talos is not connected in CreteOS Settings." }
        return url to token
    }

    private fun parseSnapshot(json: JSONObject): TalosSnapshot {
        val profilesJson = json.optJSONArray("profiles")
        val profiles = buildList {
            repeat(profilesJson?.length() ?: 0) { index ->
                profilesJson?.optJSONObject(index)?.let { item ->
                    add(TalosGamingProfile(item.optString("platform"), item.optString("display_name")))
                }
            }
        }
        val libraryJson = json.optJSONArray("library")
        val games = buildList {
            repeat(libraryJson?.length() ?: 0) { index ->
                libraryJson?.optJSONObject(index)?.let { item ->
                    add(
                        TalosGameStats(
                            platform = item.optString("platform"),
                            externalGameId = item.optString("external_game_id"),
                            name = item.optString("name"),
                            iconUrl = item.optString("icon_url").takeIf { it.isNotBlank() && it != "null" },
                            totalPlaytimeMinutes = item.optInt("total_playtime_minutes", 0),
                            lastPlayedMs = item.optString("last_played_at")
                                .takeIf { it.isNotBlank() && it != "null" }
                                ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
                            knownAchievementsUnlocked = item.optJSONObject("metadata")
                                ?.optInt("earned_achievements", 0) ?: 0,
                            knownAchievementsTotal = item.optJSONObject("metadata")
                                ?.optInt("total_achievements", 0) ?: 0,
                        )
                    )
                }
            }
        }
        return TalosSnapshot(profiles, games)
    }

    private fun normalizeBaseUrl(value: String): String {
        val url = value.trim().trimEnd('/')
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "Talos URL must start with https:// or http://"
        }
        return url
    }

    private fun apiError(body: String, status: Int): String = runCatching {
        val json = JSONObject(body)
        json.optString("message").ifBlank { json.optString("error") }.ifBlank { "Talos returned HTTP $status" }
    }.getOrDefault("Talos returned HTTP $status")

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val SNAPSHOT_TTL_MS = 4L * 60 * 60 * 1000
        private const val ACHIEVEMENT_TTL_MS = 6L * 60 * 60 * 1000

        internal fun matchStats(
            game: Game,
            steamAppId: String,
            games: List<TalosGameStats>,
        ): TalosGameStats? {
            val platformId = game.platformId.trim().lowercase()
            if (platformId == "steam") {
                return games.firstOrNull {
                    it.platform == "steam" && it.externalGameId == steamAppId
                }
            }

            val provider = when (platformId) {
                "xbox", "gamepass", "game_pass", "xbox_game_pass" -> "xbox"
                "psn", "playstation", "ps4", "ps5" -> "psn"
                else -> return null
            }
            val candidates = titleCandidates(game.title)
            return games.firstOrNull {
                it.platform == provider && titleCandidates(it.name).any(candidates::contains)
            }
        }

        internal fun titleCandidates(title: String): Set<String> {
            val normalized = Normalizer.normalize(title, Normalizer.Form.NFKD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase()
                .replace("&", "and")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
            if (normalized.isBlank()) return emptySet()
            val withoutArticle = normalized.removePrefix("the ")
            val withoutEdition = withoutArticle.replace(
                Regex(" (definitive|complete|enhanced|remastered|goty|game of the year) edition$| remastered$"),
                "",
            )
            return setOf(normalized, withoutArticle, withoutEdition).filterTo(linkedSetOf()) { it.isNotBlank() }
        }
    }
}
