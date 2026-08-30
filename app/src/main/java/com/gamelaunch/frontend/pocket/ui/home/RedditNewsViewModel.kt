package com.gamelaunch.frontend.pocket.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.data.network.RedditRssFetcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
                    // One combined request avoids Reddit rate-limiting the second subreddit. The
                    // feed parser restores each entry's real subreddit from its permalink.
                    val candidates = runCatching {
                        rssFetcher.fetchTopOfDay(subreddits, limit = FETCH_CANDIDATE_COUNT)
                    }.getOrDefault(emptyList())

                    val buckets = subreddits.map { sub ->
                        candidates.asSequence()
                            .filter { post -> post.subreddit.equals(sub, ignoreCase = true) }
                            .filterNot { post -> isMegathread(post.title) }
                            .distinctBy { post -> normaliseTitle(post.title) }
                            .take(POSTS_PER_SUBREDDIT)
                            .toList()
                    }

                    // Interleave posts from each subreddit so both r/Games and r/pcgaming appear
                    val interleaved = mutableListOf<RedditRssFetcher.RedditRssPost>()
                    val maxLen = buckets.maxOfOrNull { it.size } ?: 0
                    for (i in 0 until maxLen) {
                        buckets.forEach { list -> if (i < list.size) interleaved.add(list[i]) }
                    }

                    interleaved.map { post ->
                        NewsPost(
                            headline = post.title,
                            subreddit = "r/${post.subreddit}",
                            url = post.link,
                            thumbnailUrl = post.thumbnailUrl,
                            ageLabel = formatAge(post.publishedUtc)
                        )
                    }
                }
                _state.value = NewsUiState(posts = posts, isLoading = false)
            } catch (e: Exception) {
                _state.value = NewsUiState(isLoading = false, error = e.message)
            }
        }
    }

    private fun isMegathread(title: String): Boolean {
        val lower = title.lowercase()
        return lower.contains("megathread") ||
            lower.contains("mega thread") ||
            lower.contains("weekly thread") ||
            lower.contains("daily thread") ||
            lower.contains("monthly thread") ||
            lower.contains("weekly discussion") ||
            lower.contains("daily discussion") ||
            lower.contains("monthly discussion") ||
            lower.contains("what are you playing") ||
            lower.contains("what have you been playing") ||
            lower.contains("suggestion thread") ||
            lower.contains("recommendation thread") ||
            lower.contains("tech support thread") ||
            lower.contains("simple questions") ||
            lower.contains("free talk friday")
    }

    private fun normaliseTitle(title: String): String =
        title.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()

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

    private companion object {
        const val POSTS_PER_SUBREDDIT = 10
        const val FETCH_CANDIDATE_COUNT = 100
    }
}
