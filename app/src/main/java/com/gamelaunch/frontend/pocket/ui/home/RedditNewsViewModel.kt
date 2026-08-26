package com.gamelaunch.frontend.pocket.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.data.network.RedditRssFetcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

data class NewsPost(
    val headline: String,
    val subreddit: String,      // "r/Games"
    val url: String,            // Reddit post URL to open in browser
    val thumbnailUrl: String?,
    val ageLabel: String
)

data class NewsUiState(
    val posts: List<NewsPost> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class RedditNewsViewModel @Inject constructor(
    @Named("reddit") private val rssFetcher: RedditRssFetcher
) : ViewModel() {

    private val _state = MutableStateFlow(NewsUiState())
    val state: StateFlow<NewsUiState> = _state

    private val subreddits = listOf("Games", "pcgaming")

    init { fetchNews() }

    fun fetchNews() {
        viewModelScope.launch {
            _state.value = NewsUiState(isLoading = true)
            try {
                val posts = withContext(Dispatchers.IO) {
                    subreddits
                        .map { sub -> async { rssFetcher.fetch(sub, limit = 8) } }
                        .map { it.await() }
                        .flatten()
                        .sortedByDescending { it.publishedUtc }
                        .distinctBy { it.title }
                        .take(15)
                        .map { post ->
                            NewsPost(
                                headline     = post.title,
                                subreddit    = "r/${post.subreddit}",
                                url          = post.link,
                                thumbnailUrl = post.thumbnailUrl,
                                ageLabel     = formatAge(post.publishedUtc)
                            )
                        }
                }
                _state.value = NewsUiState(posts = posts, isLoading = false)
            } catch (e: Exception) {
                _state.value = NewsUiState(isLoading = false, error = e.message)
            }
        }
    }

    private fun formatAge(utcSeconds: Long): String {
        if (utcSeconds == 0L) return ""
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
