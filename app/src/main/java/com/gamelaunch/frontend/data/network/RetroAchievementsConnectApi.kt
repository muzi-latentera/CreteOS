package com.gamelaunch.frontend.data.network

import com.gamelaunch.frontend.data.network.dto.RaLoginDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * RetroAchievements "Connect" API (dorequest.php) — the same endpoint emulators use.
 * Lets a user authenticate with their username + password and receive a long-lived token.
 * The absolute URL overrides the @Named("ra") Retrofit base (which points at /API/).
 *
 * A User-Agent header is mandatory and is injected by the @Named("ra") OkHttp client.
 */
interface RetroAchievementsConnectApi {

    /** Authenticate with a password. Returns a token, hardcore score and softcore score. */
    @GET("https://retroachievements.org/dorequest.php")
    suspend fun loginWithPassword(
        @Query("r") request: String = "login2",
        @Query("u") username: String,
        @Query("p") password: String
    ): Response<RaLoginDto>

    /** Re-validate using a previously issued token (no password needed) to refresh the score. */
    @GET("https://retroachievements.org/dorequest.php")
    suspend fun loginWithToken(
        @Query("r") request: String = "login2",
        @Query("u") username: String,
        @Query("t") token: String
    ): Response<RaLoginDto>
}
