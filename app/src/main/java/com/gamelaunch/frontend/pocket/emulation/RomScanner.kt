package com.gamelaunch.frontend.pocket.emulation

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans a ROM root directory (via SAF URI) for games matching EmulatorSystem definitions.
 *
 * Directory structure expected:
 *   ROMs/
 *     gc/           ← matches EmulatorSystem.GAMECUBE.id
 *       Luigi's Mansion (USA).nkit.iso
 *     ps2/          ← matches EmulatorSystem.PS2.id  
 *       Shadow of the Colossus (USA).chd
 *     gba/          ← matches EmulatorSystem.GBA.id
 *       Pokemon Emerald (USA).gba
 *
 * Each ROM file is cleaned and converted to a RomEntry with:
 * - title: Clean game title (no region codes, version numbers, or extensions)
 * - system: The EmulatorSystem for this ROM
 * - romPath: Canonical key like "emu:gc:luigis_mansion"
 * - absolutePath: Full file path for emulator launch
 * - fileExtension: The ROM's file extension
 */
@Singleton
class RomScanner @Inject constructor() {

    data class RomEntry(
        val title: String,
        val system: EmulatorSystem,
        val romPath: String,
        val absolutePath: String,
        val fileExtension: String
    )

    /**
     * Scan the given SAF tree URI for ROMs.
     *
     * @param context Android context for SAF access
     * @param romRootUri SAF tree URI pointing to the ROM root (e.g. content://...document/ROMs)
     * @return List of discovered ROM entries
     */
    suspend fun scan(context: Context, romRootUri: Uri): List<RomEntry> = withContext(Dispatchers.IO) {
        val results = mutableListOf<RomEntry>()

        val rootDoc = DocumentFile.fromTreeUri(context, romRootUri)
        if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory) {
            Log.w(TAG, "ROM root not accessible: $romRootUri")
            return@withContext emptyList()
        }

        Log.d(TAG, "Scanning ROM root: ${rootDoc.name}")

        // Scan each subdirectory as a potential system folder
        for (systemDir in rootDoc.listFiles()) {
            if (!systemDir.isDirectory) continue

            val systemId = systemDir.name?.lowercase() ?: continue
            val system = EmulatorSystem.fromId(systemId)
            if (system == null) {
                Log.d(TAG, "Skipping unknown system folder: $systemId")
                continue
            }

            Log.d(TAG, "Scanning ${system.displayName} folder: ${systemDir.name}")

            // Scan ROM files in this system folder
            for (romFile in systemDir.listFiles()) {
                if (!romFile.isFile) continue

                val fileName = romFile.name ?: continue
                val extension = fileName.substringAfterLast('.', "").lowercase()

                if (!system.fileExtensions.contains(extension)) {
                    continue
                }

                val cleanTitle = cleanRomTitle(fileName)
                val sanitizedKey = sanitizeForKey(cleanTitle)
                val romPath = "emu:${system.id}:$sanitizedKey"

                // Get the absolute path from the URI
                val absolutePath = getAbsolutePath(context, romFile.uri)
                if (absolutePath == null) {
                    Log.w(TAG, "Could not resolve absolute path for: $fileName")
                    continue
                }

                results.add(
                    RomEntry(
                        title = cleanTitle,
                        system = system,
                        romPath = romPath,
                        absolutePath = absolutePath,
                        fileExtension = extension
                    )
                )

                Log.d(TAG, "Found ROM: $cleanTitle ($romPath)")
            }
        }

        Log.i(TAG, "Scan complete: found ${results.size} ROMs")
        results
    }

    /**
     * Clean a ROM filename to extract a human-readable title.
     *
     * Rules:
     * - Remove file extension
     * - Remove region codes in parentheses: (USA), (Europe), (En,Fr,De), etc.
     * - Remove revision/version codes in brackets: [v0], [vXXXXXX], [!]
     * - Remove title IDs in brackets: [0100F2C0115B6000]
     * - Remove nkit suffix
     * - Strip leading numbers like "4273 - " from DS ROM names (3-8 digits)
     * - Strip "The Legend of Zelda, The - " prefix for cleaner Zelda titles
     * - Strip leading "The " from titles
     * - Trim whitespace
     *
     * Examples:
     * - "Luigi's Mansion (USA).nkit.iso" → "Luigi's Mansion"
     * - "4273 - Pokemon Mystery Dungeon - Explorers of Sky (US)(XenoPhobia).nds" → "Pokemon Mystery Dungeon Explorers of Sky"
     * - "Super Mario Sunshine [v0].gcz" → "Super Mario Sunshine"
     * - "The Legend of Zelda Tears of the Kingdom [0100F2C0115B6000][v0].nsp" → "Tears of the Kingdom"
     * - "Legend of Zelda, The - A Link Between Worlds (Europe) (En,Fr,De,Es,It).3ds" → "Zelda A Link Between Worlds"
     * - "Devil May Cry 3 - Dante's Awakening (USA) (En,Ja) (Special Edition).iso" → "Devil May Cry 3 Dantes Awakening Special Edition"
     */
    private fun cleanRomTitle(filename: String): String {
        var title = filename

        // Remove all file extensions (handles .nkit.iso, .bin.ecm, etc)
        val extensionPatterns = listOf(
            "\\.nkit\\.iso$", "\\.nkit\\.gcz$", "\\.bin\\.ecm$",
            "\\.(iso|gcm|rvz|wbfs|gcz|wia|nsp|xci|nca|chd|gdi|cdi|bin|cue|img|mdf|pbp|cso|3ds|cia|cxi|nds|dsi|vpk|gba|gb|gbc|nes|unf|sfc|smc|z64|n64|v64|wud|wux|rpx|wua|pkg|psn)$"
        )
        for (pattern in extensionPatterns) {
            title = title.replace(Regex(pattern, RegexOption.IGNORE_CASE), "")
        }

        // Remove region codes in parentheses: (USA), (Europe), (En,Fr,De), (US), (J), etc.
        title = title.replace(Regex("\\s*\\([^)]*(?:USA|Europe|Japan|World|En|Fr|De|Es|It|Ja|Ko|Zh|PAL|NTSC|US|EU|JP|Rev|XenoPhobia|Decrypted|Trashed|Redump)[^)]*\\)", RegexOption.IGNORE_CASE), "")
        
        // Remove any remaining parenthetical region codes
        title = title.replace(Regex("\\s*\\([A-Z]{1,2}(?:,[A-Z]{1,2})*\\)"), "")

        // Keep "(Special Edition)" etc but strip region/group tags
        // Remove version/revision codes in brackets: [v0], [vXXXXXX], [!], [b], [titleID], etc.
        title = title.replace(Regex("\\s*\\[[^\\]]*\\]"), "")

        // Remove nkit suffix
        title = title.replace(Regex("\\s*\\.?nkit", RegexOption.IGNORE_CASE), "")

        // Strip leading numbers like "4273 - " or "12345678-" from scene ROM names (3-8 digits)
        title = title.replace(Regex("^\\d{3,8}\\s*[-.]+\\s*"), "")

        // Strip "Legend of Zelda, The - " or "Legend of Zelda The - " prefix for cleaner titles
        title = title.replace(Regex("^(?:The\\s+)?Legend\\s+of\\s+Zelda[,]?\\s*(?:The)?\\s*[-:]?\\s*", RegexOption.IGNORE_CASE), "Zelda ")

        // Strip leading "The " from remaining titles
        title = title.replace(Regex("^The\\s+", RegexOption.IGNORE_CASE), "")

        // Replace remaining hyphens with spaces for better readability (but preserve internal hyphens)
        // Only do this for spaced hyphens like " - "
        title = title.replace(" - ", " ")

        // Remove apostrophes for cleaner matching (they cause issues with IGDB searches)
        title = title.replace("'", "")

        // Clean up multiple spaces and trim
        title = title.replace(Regex("\\s+"), " ").trim()

        return title
    }

    /**
     * Sanitize a title for use as a romPath key.
     * Converts to lowercase, replaces spaces/special chars with underscores.
     */
    private fun sanitizeForKey(title: String): String {
        return title
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .replace(Regex("^_+|_+$"), "")  // trim leading/trailing underscores
            .replace(Regex("_+"), "_")       // collapse multiple underscores
    }

    /**
     * Attempt to get the absolute file path from a SAF URI.
     * This works for primary/external storage on most devices.
     */
    private fun getAbsolutePath(context: Context, uri: Uri): String? {
        // First try: Check if it's a file:// URI
        if (uri.scheme == "file") {
            return uri.path
        }

        // Second try: For content:// URIs, try to extract path from document ID
        if (uri.scheme == "content") {
            val docId = try {
                DocumentsContract.getDocumentId(uri)
            } catch (e: Exception) {
                null
            }

            if (docId != null) {
                // Handle primary storage: "primary:path/to/file"
                if (docId.startsWith("primary:")) {
                    val path = docId.removePrefix("primary:")
                    return "/storage/emulated/0/$path"
                }
                
                // Handle external SD card: "XXXX-XXXX:path/to/file"
                val colonIndex = docId.indexOf(':')
                if (colonIndex > 0) {
                    val storageId = docId.substring(0, colonIndex)
                    val path = docId.substring(colonIndex + 1)
                    
                    // Common external storage paths
                    val candidates = listOf(
                        "/storage/$storageId/$path",
                        "/mnt/media_rw/$storageId/$path"
                    )
                    for (candidate in candidates) {
                        if (java.io.File(candidate).exists()) {
                            return candidate
                        }
                    }
                    // Return first candidate even if not verified
                    return "/storage/$storageId/$path"
                }
            }
        }

        // Fallback: Try to get path from URI path segment
        return uri.path
    }

    companion object {
        private const val TAG = "RomScanner"
    }
}
