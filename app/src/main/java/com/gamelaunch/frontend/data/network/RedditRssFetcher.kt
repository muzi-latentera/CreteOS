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
        val link: String,
        val subreddit: String,
        val thumbnailUrl: String?,
        val publishedUtc: Long
    )

    fun fetch(subreddit: String, limit: Int = 10): List<RedditRssPost> {
        val url = "https://www.reddit.com/r/$subreddit/.rss?limit=$limit"
        android.util.Log.d("RedditRss", "Fetching: $url")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "CreteOS/1.0 (Android; gaming launcher)")
            .build()

        val response = client.newCall(request).execute()
        android.util.Log.d("RedditRss", "Response for $subreddit: ${response.code}")
        if (!response.isSuccessful) return emptyList()

        val posts = response.body?.byteStream()?.let { stream ->
            parseAtomFeed(stream, subreddit)
        } ?: emptyList()
        android.util.Log.d("RedditRss", "Parsed ${posts.size} posts from r/$subreddit")
        return posts
    }

    private fun parseAtomFeed(stream: InputStream, subreddit: String): List<RedditRssPost> {
        val posts = mutableListOf<RedditRssPost>()
        val parser = Xml.newPullParser()
        parser.setFeature(android.util.Xml.FEATURE_RELAXED, true)
        // Enable namespace processing so we can match media:thumbnail
        parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true)
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType

        var inEntry = false
        var title = ""
        var link = ""
        var thumbnail: String? = null
        var published = 0L

        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    val localName = parser.name ?: ""
                    val ns = parser.namespace ?: ""

                    when {
                        localName == "entry" -> {
                            inEntry = true
                            title = ""; link = ""; thumbnail = null; published = 0L
                        }
                        inEntry && localName == "title" -> {
                            title = parser.nextText().trim()
                        }
                        inEntry && localName == "link" -> {
                            val rel  = parser.getAttributeValue(null, "rel")
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            if ((rel == "alternate" || rel == null) && href.contains("reddit.com")) {
                                link = href
                            }
                        }
                        inEntry && localName == "thumbnail" -> {
                            // media:thumbnail — the url attribute
                            val url = parser.getAttributeValue(null, "url")
                                ?: parser.getAttributeValue("http://search.yahoo.com/mrss/", "url")
                            if (!url.isNullOrBlank() && url.startsWith("http")) {
                                thumbnail = url
                            }
                        }
                        inEntry && (localName == "published" || localName == "updated") && published == 0L -> {
                            runCatching {
                                val text = parser.nextText()
                                published = java.time.Instant.parse(text).epochSecond
                            }
                        }
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    if ((parser.name ?: "") == "entry" && inEntry) {
                        if (title.isNotBlank() && link.isNotBlank() && !title.startsWith("[")) {
                            posts.add(RedditRssPost(title, link, subreddit, thumbnail, published))
                        }
                        inEntry = false
                    }
                }
            }
            // Safe next — XmlPullParser can throw on malformed XML
            try { eventType = parser.next() } catch (e: Exception) { break }
        }

        android.util.Log.d("RedditRss", "r/$subreddit: parsed ${posts.size} posts, first=${posts.firstOrNull()?.title?.take(40)}")
        return posts
    }
}
