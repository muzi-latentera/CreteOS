package com.gamelaunch.frontend.domain.model

private const val RA_MEDIA_BASE = "https://media.retroachievements.org"

data class RaProfile(
    val username: String,
    val avatarUrl: String,
    val totalPoints: Int,
    val softcorePoints: Int,
    val truePoints: Int,
    val rank: Int?,
    val status: String?
)

/** Result of a Connect API username/password (or token) login. */
data class RaSession(
    val username: String,
    val token: String,
    val points: Int,
    val softcorePoints: Int
) {
    /** Basic profile that can be shown without a Web API key. */
    fun toProfile(): RaProfile = RaProfile(
        username       = username,
        avatarUrl      = raAvatarUrl(username),
        totalPoints    = points,
        softcorePoints = softcorePoints,
        truePoints     = 0,
        rank           = null,
        status         = null
    )
}

data class RaRecentGame(
    val gameId: Int,
    val title: String,
    val consoleName: String,
    val iconUrl: String,
    val numAchievements: Int,
    val numEarned: Int,
    val numEarnedHardcore: Int,
    val scoreEarned: Int,
    val maxScore: Int,
    val lastPlayed: String?,
    val highestAwardKind: String?
) {
    val completionPercent: Float
        get() = if (numAchievements > 0) numEarned.toFloat() / numAchievements else 0f

    val isMastered: Boolean
        get() = highestAwardKind == "mastered" || highestAwardKind == "completed"
}

fun String.toRaMediaUrl(): String =
    if (startsWith("http")) this else "$RA_MEDIA_BASE$this"

fun raAvatarUrl(username: String): String =
    "$RA_MEDIA_BASE/UserPic/$username.png"
