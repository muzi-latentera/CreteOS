package com.gamelaunch.frontend.domain.model

/** Ordering for the games shown inside a system. Persisted by name. */
enum class GameSort(val label: String) {
    FAVORITES("Favorites"),
    RECENTLY_PLAYED("Recently played"),
    RECENTLY_ADDED("Recently added"),
    ALPHABETICAL("Alphabetical");

    companion object {
        val DEFAULT = ALPHABETICAL
        fun fromName(name: String): GameSort = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * The section a game falls into under the given [sort] — a short "you are here" token for the
 * fast-scroll indicator. It always matches the active order: an alphabetical letter for title
 * order, a recency bucket for the time-based sorts, and a star for the favourites block. Adjacent
 * games in the sorted list share a token, so scrolling shows the section sliding past.
 */
fun Game.sectionLabel(sort: GameSort, now: Long = System.currentTimeMillis()): String = when (sort) {
    GameSort.ALPHABETICAL    -> alphaBucket(title)
    GameSort.FAVORITES       -> if (isFavorite) "★" else alphaBucket(title)
    GameSort.RECENTLY_PLAYED -> recencyBucket(lastPlayedMs, now, neverLabel = "Never")
    GameSort.RECENTLY_ADDED  -> recencyBucket(dateAdded, now, neverLabel = "Older")
}

private fun alphaBucket(title: String): String {
    val c = title.trimStart().firstOrNull() ?: '#'
    return if (c.isLetter()) c.uppercaseChar().toString() else "#"
}

private fun recencyBucket(ts: Long?, now: Long, neverLabel: String): String {
    if (ts == null || ts <= 0L) return neverLabel
    val days = (now - ts).coerceAtLeast(0L) / 86_400_000L   // ms per day
    return when {
        days < 1L   -> "Today"
        days < 7L   -> "This Week"
        days < 30L  -> "This Month"
        days < 365L -> "This Year"
        else        -> "Older"
    }
}

/** Apply a [GameSort] to a list of games. Ties fall back to title so the order is stable. */
fun List<Game>.sortedBy(sort: GameSort): List<Game> = when (sort) {
    GameSort.ALPHABETICAL    -> sortedBy { it.title.lowercase() }
    GameSort.RECENTLY_PLAYED -> sortedWith(
        compareByDescending<Game> { it.lastPlayedMs ?: Long.MIN_VALUE }.thenBy { it.title.lowercase() }
    )
    GameSort.RECENTLY_ADDED  -> sortedWith(
        compareByDescending<Game> { it.dateAdded }.thenBy { it.title.lowercase() }
    )
    GameSort.FAVORITES       -> sortedWith(
        compareByDescending<Game> { it.isFavorite }.thenBy { it.title.lowercase() }
    )
}
