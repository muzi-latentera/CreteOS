package com.gamelaunch.frontend.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ── Reddit JSON API ────────────────────────────────────────────────────────

interface RedditApi {
    @GET("r/{subreddit}/hot.json")
    suspend fun getHotPosts(
        @Path("subreddit") subreddit: String,
        @Query("limit") limit: Int = 10,
        @Query("raw_json") rawJson: Int = 1
    ): RedditListingResponse
}

// ── DTOs ──────────────────────────────────────────────────────────────────

data class RedditListingResponse(
    val data: RedditListingData
)

data class RedditListingData(
    val children: List<RedditPostWrapper>
)

data class RedditPostWrapper(
    val data: RedditPost
)

data class RedditPost(
    val title: String,
    val url: String,                          // link or reddit post URL
    val permalink: String,                    // always a reddit path: /r/Games/comments/…
    val subreddit: String,
    @SerializedName("created_utc") val createdUtc: Long,
    val preview: RedditPreview?,
    val thumbnail: String?,                   // small thumbnail, sometimes "self"/"default"
    @SerializedName("is_self") val isSelf: Boolean
)

data class RedditPreview(
    val images: List<RedditImage>?
)

data class RedditImage(
    val source: RedditImageSource?,
    val resolutions: List<RedditImageSource>?
)

data class RedditImageSource(
    val url: String,                          // HTML-encoded — needs unescaping
    val width: Int,
    val height: Int
)
