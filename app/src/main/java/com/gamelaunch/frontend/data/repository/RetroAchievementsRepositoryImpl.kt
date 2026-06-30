package com.gamelaunch.frontend.data.repository

import com.gamelaunch.frontend.data.network.RetroAchievementsApi
import com.gamelaunch.frontend.data.network.RetroAchievementsConnectApi
import com.gamelaunch.frontend.data.network.dto.RaLoginDto
import com.gamelaunch.frontend.domain.model.RaProfile
import com.gamelaunch.frontend.domain.model.RaRecentGame
import com.gamelaunch.frontend.domain.model.RaSession
import com.gamelaunch.frontend.domain.model.raAvatarUrl
import com.gamelaunch.frontend.domain.model.toRaMediaUrl
import com.gamelaunch.frontend.domain.repository.RetroAchievementsRepository
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetroAchievementsRepositoryImpl @Inject constructor(
    private val api: RetroAchievementsApi,
    private val connectApi: RetroAchievementsConnectApi
) : RetroAchievementsRepository {

    override suspend fun login(username: String, password: String): Result<RaSession> =
        runCatching {
            connectApi.loginWithPassword(username = username, password = password)
                .toSession(username)
        }

    override suspend fun refreshSession(username: String, token: String): Result<RaSession> =
        runCatching {
            connectApi.loginWithToken(username = username, token = token)
                .toSession(username)
        }

    private fun Response<RaLoginDto>.toSession(fallbackUsername: String): RaSession {
        val dto = body() ?: error("Empty response (HTTP ${code()})")
        if (dto.success != true || dto.token.isNullOrBlank()) {
            error(dto.error ?: "Invalid username or password")
        }
        return RaSession(
            username       = dto.user ?: fallbackUsername,
            token          = dto.token,
            points         = dto.score ?: 0,
            softcorePoints = dto.softcoreScore ?: 0
        )
    }

    override suspend fun getUserProfile(username: String, apiKey: String): Result<RaProfile> =
        runCatching {
            val response = api.getUserSummary(username, apiKey, username)
            val dto = response.body() ?: error("Empty response (HTTP ${response.code()})")
            RaProfile(
                username      = dto.user ?: username,
                avatarUrl     = raAvatarUrl(dto.user ?: username),
                totalPoints   = dto.totalPoints ?: 0,
                softcorePoints = dto.softcorePoints ?: 0,
                truePoints    = dto.truePoints ?: 0,
                rank          = dto.rank,
                status        = dto.status
            )
        }

    override suspend fun getRecentlyPlayed(username: String, apiKey: String, count: Int): Result<List<RaRecentGame>> =
        runCatching {
            val response = api.getRecentlyPlayedGames(username, apiKey, username, count)
            val list = response.body() ?: error("Empty response (HTTP ${response.code()})")
            list.mapNotNull { dto ->
                val id = dto.gameId ?: return@mapNotNull null
                RaRecentGame(
                    gameId           = id,
                    title            = dto.title ?: "Unknown",
                    consoleName      = dto.consoleName ?: "",
                    iconUrl          = dto.imageIcon?.toRaMediaUrl() ?: "",
                    numAchievements  = dto.numAchievements ?: 0,
                    numEarned        = dto.numAwardedToUser ?: 0,
                    numEarnedHardcore = dto.numAwardedHardcore ?: 0,
                    scoreEarned      = dto.scoreAchieved ?: 0,
                    maxScore         = dto.maxPossible ?: 0,
                    lastPlayed       = dto.lastPlayed,
                    highestAwardKind = dto.highestAwardKind
                )
            }
        }
}
