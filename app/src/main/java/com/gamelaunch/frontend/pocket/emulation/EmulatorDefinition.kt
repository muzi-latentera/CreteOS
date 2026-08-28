package com.gamelaunch.frontend.pocket.emulation

/**
 * Defines how ROM paths are passed to an emulator via Intent.
 */
enum class RomIntentType {
    /** ROM path as a String extra (most common) */
    FILE_PATH,
    /** Content URI (content://) as Intent data, or as a Uri extra when romIntentKey is set */
    CONTENT_URI,
    /** File URI set as Intent data (setData) */
    DATA_URI,
    /** ArrayList<String> of file paths */
    FILE_ARRAY_LIST
}

/**
 * Complete definition of an emulator's launch contract and capabilities.
 *
 * Used by [EmulatorRegistry] to build launch Intents and detect installed emulators.
 *
 * @property id Unique identifier for this emulator (uppercase, e.g., "RETROARCH")
 * @property displayName Human-readable name for UI
 * @property systems Set of [EmulatorSystem]s this emulator can run
 * @property packageCandidates Package names to try in order (first installed wins)
 * @property launchActivity Activity class name relative to package, or null for MAIN launcher
 * @property launchAction Custom Intent action, or null for ACTION_MAIN
 * @property romIntentKey Extra key for ROM path, or null if passed via Intent data
 * @property romIntentType How the ROM path should be formatted in the Intent
 * @property requiresSafUriGrant If true, must grant URI read permission before launch
 * @property supportsCustomDriver If true, emulator can use custom GPU drivers (e.g., Eden)
 * @property experimental If true, emulator support is experimental/unstable
 * @property exportMethod Human-readable note about migration/export method
 * @property extraKeys Additional Intent extras required (e.g., "LIBRETRO" for RetroArch core)
 * @property notes Developer notes about this emulator's launch contract
 */
data class EmulatorDefinition(
    val id: String,
    val displayName: String,
    val systems: Set<EmulatorSystem>,
    val packageCandidates: List<String>,
    val launchActivity: String? = null,
    val launchAction: String? = null,
    val launchCategory: String? = null,  // e.g. Intent.CATEGORY_LEANBACK_LAUNCHER for Dolphin
    val romIntentKey: String? = null,
    val romIntentType: RomIntentType,
    val requiresSafUriGrant: Boolean = false,
    val supportsCustomDriver: Boolean = false,
    val experimental: Boolean = false,
    val exportMethod: String = "",
    val extraKeys: Map<String, String> = emptyMap(),
    val notes: String = ""
) {
    /**
     * Returns true if this emulator supports the given system.
     */
    fun supportsSystem(system: EmulatorSystem): Boolean = systems.contains(system)

    /**
     * Returns the first package candidate — typically the preferred/primary package.
     */
    val primaryPackage: String
        get() = packageCandidates.first()
}
