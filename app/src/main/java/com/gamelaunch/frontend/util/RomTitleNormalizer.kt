package com.gamelaunch.frontend.util

import java.text.Normalizer

/** Produces display titles from ROM dump filenames without changing the underlying file path. */
object RomTitleNormalizer {
    private val romExtension = Regex(
        "\\.(iso|gcm|rvz|wbfs|gcz|wia|nsp|xci|nca|chd|gdi|cdi|bin|cue|img|mdf|pbp|cso|3ds|cia|cxi|nds|dsi|vpk|gba|gb|gbc|nes|unf|sfc|smc|z64|n64|v64|wud|wux|rpx|wua|pkg|psn|zip|7z|rar)$",
        RegexOption.IGNORE_CASE
    )
    private val dumpTag = Regex(
        "\\s*\\((?:[^)]*\\b(?:USA|Europe|Japan|World|PAL|NTSC|US|EU|JP|Rev(?:ision)?|XenoPhobia|Decrypted|Trashed|Redump)\\b[^)]*|[A-Z][a-z]?(?:,[A-Z][a-z]?)+)\\)",
        RegexOption.IGNORE_CASE
    )

    fun fromFilename(filename: String): String {
        var title = filename.trim()
        while (romExtension.containsMatchIn(title)) {
            title = title.replace(romExtension, "")
        }
        title = title
            .replace(Regex("\\s*\\[[^]]*]"), "")
            .replace(dumpTag, "")
            .replace(Regex("\\s*\\([A-Z]{1,3}(?:,[A-Z]{1,3})*\\)"), "")
            .replace(Regex("\\s*\\.?nkit$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^\\d{3,8}\\s*[-.]+\\s*"), "")
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        val key = keyFor(title)
        return when {
            key.contains("legend of zelda tears of the kingdom") ->
                "The Legend of Zelda: Tears of the Kingdom"
            key.contains("windwaker hd") || key.contains("wind waker hd") ->
                "The Legend of Zelda: The Wind Waker HD"
            key.contains("legend of zelda the a link between worlds") ->
                "The Legend of Zelda: A Link Between Worlds"
            key.contains("pokemon mystery dungeon explorers of sky") ->
                "Pokémon Mystery Dungeon: Explorers of Sky"
            key.contains("pokemon firered version") -> "Pokémon FireRed Version"
            key.contains("metroid zero mission") -> "Metroid: Zero Mission"
            key.contains("devil may cry 3 dante s awakening") ->
                "Devil May Cry 3: Dante's Awakening – Special Edition"
            key.contains("midnight club l a remix") -> "Midnight Club: L.A. Remix"
            key.contains("new super mario bros") -> "New Super Mario Bros."
            key.contains("luigi s mansion") -> "Luigi's Mansion"
            else -> reorderTrailingArticle(title)
        }
    }

    /** The exact title produced by eOr's pre-normalization scanner. */
    fun legacyTitleFromFilename(filename: String): String =
        filename.substringBeforeLast('.', filename)
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?]"), "")
            .trim()

    /** Only scanner-generated titles are eligible for automatic repair; manual names are preserved. */
    fun shouldRepair(currentTitle: String, filename: String): Boolean =
        currentTitle.trim() == legacyTitleFromFilename(filename) &&
            currentTitle.trim() != fromFilename(filename)

    private fun reorderTrailingArticle(title: String): String {
        val match = Regex("^(.+),\\s*The\\s*[-:]\\s*(.+)$", RegexOption.IGNORE_CASE)
            .matchEntire(title)
            ?: return title
        return "The ${match.groupValues[1]}: ${match.groupValues[2]}"
    }

    private fun keyFor(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
