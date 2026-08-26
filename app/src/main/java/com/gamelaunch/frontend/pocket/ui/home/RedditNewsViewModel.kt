package com.gamelaunch.frontend.pocket.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.data.network.RedditApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewsPost(
    val headline: String,
    val subreddit: String,         // e.g. "r/Games"
    val url: String,               // Reddit post URL to open in browser
    val thumbnailUrl: String?,     // nullable — some posts have no preview image
    val ageLabel: String
)

data class NewsUiState(
    val posts: List<NewsPost> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class RedditNewsViewModel @Inject constructor(
    private val redditApi: RedditApi
) : ViewModel() {

    private val _state = MutableStateFlow(NewsUiState())
    val state: StateFlow<NewsUiState> = _state

    private val subreddits = listOf("Games", "pcgaming")

    init {
        fetchNews()
    }

    fun fetchNews() {
        viewModelScope.launch {
            _state.value = NewsUiState(isLoading = true)
            try {
                // Fetch both subreddits concurrently
                val results = subreddits.map { sub ->
                    async {
                        try {
                            redditApi.getHotPosts(sub, limit = 8)
                                .data.children
                                .map { it.data }
                                .filter { !it.title.startsWith("[") }  // skip mod posts
                                .map { post ->
                                    NewsPost(
                                        headline     = post.title,
                                        subreddit    = "r/${post.subreddit}",
                                        url          = "https://reddit.com${post.permalink}",
                                        thumbnailUrl = bestThumbnail(post),
                                        ageLabel     = formatAge(post.createdUtc)
                                    )
                                }
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }.map { it.await() }

                val merged = results
                    .flatten()
                    .distinctBy { it.headline }
                    .take(15)

                _state.value = NewsUiState(posts = merged, isLoading = false)
            } catch (e: Exception) {
                _state.value = NewsUiState(isLoading = false, error = e.message)
            }
        }
    }

    /** Pick the best available thumbnail URL from a Reddit post. */
    private fun bestThumbnail(post: com.gamelaunch.frontend.data.network.RedditPost): String? {
        // Try preview image first (higher quality), fall back to thumbnail
        val previewUrl = post.preview?.images
            ?.firstOrNull()
            ?.resolutions
            ?.lastOrNull()   // last resolution = largest under source
            ?.url
            ?.replace("&amp;", "&")   // Reddit HTML-encodes preview URLs

        if (previewUrl != null) return previewUrl

        val thumb = post.thumbnail
        return if (thumb != null && thumb.startsWith("http")) thumb else null
    }

    private fun formatAge(utcSeconds: Long): String {
        val diffMs = System.currentTimeMillis() - (utcSeconds * 1000)
        val hours = diffMs / 3_600_000
        val days  = diffMs / 86_400_000
        return when {
            hours < 1  -> "${diffMs / 60_000}m ago"
            hours < 24 -> "${hours}h ago"
            else       -> "${days}d ago"
        }
    }
}
