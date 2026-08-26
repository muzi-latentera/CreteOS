package com.gamelaunch.frontend.data.network

import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

/**
 * Fetches Reddit Atom RSS feeds and parses them without a JSON API.
 * Reddit's .rss endpoint works without auth and without a special User-Agent.
 *
 * Returns a list of [RedditRssPost] items from the feed.
 */
class RedditRssFetcher(private val client: OkHttpClient) {

    data class RedditRssPost(
        val title: String,
        val link: String,           // full Reddit post URL
        val subreddit: String,
        val thumbnailUrl: String?,
        val publishedUtc: Long      // epoch seconds
    )

    fun fetch(subreddit: String, limit: Int = 10): List<RedditRssPost> {
        val url = "https://www.reddit.com/r/$subreddit/.rss?limit=$limit"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "CreteOS/1.0 (Android; gaming launcher)")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        return response.body?.byteStream()?.let { stream ->
            parseAtomFeed(stream, subreddit)
        } ?: emptyList()
    }

    private fun parseAtomFeed(stream: InputStream, subreddit: String): List<RedditRssPost> {
        val posts = mutableListOf<RedditRssPost>()
        val parser = Xml.newPullParser()
        parser.setFeature(android.util.Xml.FEATURE_RELAXED, true)
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType

        // Per-entry state
        var inEntry = false
        var title = ""
        var link = ""
        var thumbnail: String? = null
        var published = 0L

        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "entry" -> {
                            inEntry = true
                            title = ""; link = ""; thumbnail = null; published = 0L
                        }
                        "title" -> if (inEntry) {
                            title = parser.nextText().trim()
                        }
                        "link" -> if (inEntry) {
                            val rel = parser.getAttributeValue(null, "rel")
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            if (rel == "alternate" && href.contains("reddit.com")) {
                                link = href
                            }
                        }
                        "thumbnail" -> if (inEntry) {
                            // media:thumbnail url="…"
                            val url = parser.getAttributeValue(null, "url")
                            if (!url.isNullOrBlank() && url.startsWith("http")) {
                                thumbnail = url
                            }
                        }
                        "published", "updated" -> if (inEntry && published == 0L) {
                            runCatching {
                                val text = parser.nextText()
                                published = java.time.Instant.parse(text).epochSecond
                            }
                        }
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    if (parser.name == "entry" && inEntry && title.isNotBlank() && link.isNotBlank()) {
                        // Skip mod/weekly threads
                        if (!title.startsWith("[") && title.length > 10) {
                            posts.add(
                                RedditRssPost(
                                    title       = title,
                                    link        = link,
                                    subreddit   = subreddit,
                                    thumbnailUrl = thumbnail,
                                    publishedUtc = published
                                )
                            )
                        }
                        inEntry = false
                    }
                }
            }
            eventType = parser.next()
        }

        return posts
    }
}
