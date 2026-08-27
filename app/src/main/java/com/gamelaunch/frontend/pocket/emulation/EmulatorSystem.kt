package com.gamelaunch.frontend.pocket.emulation

/**
 * Represents emulated gaming systems with ES-DE compatible identifiers.
 *
 * Each system has:
 * - [id]: ES-DE compatible identifier (lowercase)
 * - [displayName]: Human-readable name for UI
 * - [fileExtensions]: Set of ROM file extensions (lowercase, no dot)
 */
enum class EmulatorSystem(
    val id: String,
    val displayName: String,
    val fileExtensions: Set<String>
) {
    NES(
        id = "nes",
        displayName = "Nintendo Entertainment System",
        fileExtensions = setOf("nes", "unf")
    ),
    SNES(
        id = "snes",
        displayName = "Super Nintendo",
        fileExtensions = setOf("sfc", "smc")
    ),
    GB(
        id = "gb",
        displayName = "Game Boy",
        fileExtensions = setOf("gb", "gbc")
    ),
    GBC(
        id = "gbc",
        displayName = "Game Boy Color",
        fileExtensions = setOf("gb", "gbc")
    ),
    GBA(
        id = "gba",
        displayName = "Game Boy Advance",
        fileExtensions = setOf("gba")
    ),
    N64(
        id = "n64",
        displayName = "Nintendo 64",
        fileExtensions = setOf("z64", "n64", "v64")
    ),
    DREAMCAST(
        id = "dreamcast",
        displayName = "Sega Dreamcast",
        fileExtensions = setOf("chd", "gdi", "cdi", "iso")
    ),
    SATURN(
        id = "saturn",
        displayName = "Sega Saturn",
        fileExtensions = setOf("iso", "chd", "cue", "bin")
    ),
    SWITCH(
        id = "switch",
        displayName = "Nintendo Switch",
        fileExtensions = setOf("nsp", "xci", "nca")
    ),
    WIIU(
        id = "wiiu",
        displayName = "Nintendo Wii U",
        fileExtensions = setOf("wud", "wux", "rpx", "wua")
    ),
    GAMECUBE(
        id = "gc",
        displayName = "Nintendo GameCube",
        fileExtensions = setOf("iso", "gcm", "rvz", "wbfs", "gcz", "wia")
    ),
    WII(
        id = "wii",
        displayName = "Nintendo Wii",
        fileExtensions = setOf("iso", "gcm", "rvz", "wbfs", "gcz", "wia")
    ),
    PS3(
        id = "ps3",
        displayName = "PlayStation 3",
        fileExtensions = setOf("pkg", "psn") // EXPERIMENTAL
    ),
    PS2(
        id = "ps2",
        displayName = "PlayStation 2",
        fileExtensions = setOf("iso", "bin", "img", "mdf", "chd")
    ),
    PS1(
        id = "psx",
        displayName = "PlayStation",
        fileExtensions = setOf("bin", "cue", "iso", "img", "pbp", "chd", "ecm")
    ),
    PSP(
        id = "psp",
        displayName = "PlayStation Portable",
        fileExtensions = setOf("iso", "cso", "pbp", "chd")
    ),
    N3DS(
        id = "3ds",
        displayName = "Nintendo 3DS",
        fileExtensions = setOf("3ds", "cia", "cxi")
    ),
    NDS(
        id = "nds",
        displayName = "Nintendo DS",
        fileExtensions = setOf("nds", "dsi")
    ),
    PSVITA(
        id = "psvita",
        displayName = "PlayStation Vita",
        fileExtensions = setOf("vpk") // EXPERIMENTAL
    ),
    GENERIC(
        id = "generic",
        displayName = "Generic",
        fileExtensions = emptySet()
    );

    companion object {
        /**
         * Find a system by its ES-DE compatible ID.
         */
        fun fromId(id: String): EmulatorSystem? =
            entries.find { it.id.equals(id, ignoreCase = true) }

        /**
         * Find systems that support a given file extension.
         */
        fun forExtension(extension: String): List<EmulatorSystem> {
            val ext = extension.lowercase().removePrefix(".")
            return entries.filter { it.fileExtensions.contains(ext) }
        }
    }
}
