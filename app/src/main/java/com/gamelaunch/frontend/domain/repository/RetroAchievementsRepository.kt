package com.gamelaunch.frontend.domain.repository

import com.gamelaunch.frontend.domain.model.RaProfile
import com.gamelaunch.frontend.domain.model.RaRecentGame
import com.gamelaunch.frontend.domain.model.RaSession

interface RetroAchievementsRepository {
    /** Authenticate with username + password via the Connect API. Returns a reusable token. */
    suspend fun login(username: String, password: String): Result<RaSession>

    /** Re-validate with a stored token (no password) to refresh the score totals. */
    suspend fun refreshSession(username: String, token: String): Result<RaSession>

    suspend fun getUserProfile(username: String, apiKey: String): Result<RaProfile>
    suspend fun getRecentlyPlayed(username: String, apiKey: String, count: Int = 15): Result<List<RaRecentGame>>
}
