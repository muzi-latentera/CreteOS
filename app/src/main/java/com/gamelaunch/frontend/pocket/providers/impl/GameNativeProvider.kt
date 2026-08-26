package com.gamelaunch.frontend.pocket.providers.impl

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.pocket.domain.DiscoveredProviderGame
import com.gamelaunch.frontend.pocket.domain.DisplayPolicy
import com.gamelaunch.frontend.pocket.domain.LaunchContext
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderCapability
import com.gamelaunch.frontend.pocket.providers.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for GameNative — the primary local PC-game runtime.
 *
 * ## Marker File Format — VERIFIED from GameNative 1.2.0 source (2026-08-26)
 *
 * FrontendSyncManager.kt uses file.writeText(appId.toString(), UTF-8):
 *
 * | Extension | Source (GameNative DAO) | Entity ID type | File content |
 * |-----------|-------------------------|----------------|--------------|
 * | .steam    | SteamAppDao.getInstalledGames() | SteamApp.id: Int | Int as String |
 * | .gog      | GOGGameDao.getInstalledGames() | GOGGame.id: String | Converted via toIntOrNull() ?: 0 |
 * | .epic     | EpicGameDao.getInstalledGames() | EpicGame.id: Int (auto-gen) | Int as String |
 * | .amazon   | AmazonGameDao.getInstalledGames() | AmazonGame.appId: Int (auto-gen) | Int as String |
 * | .pcgame   | SteamAppDao.getInstalledGames() | SteamApp.id: Int | Int as String |
 *
 * IMPORTANT: GOG IDs in GameNative's GOGGame entity are String (GOG's actual IDs).
 * However, FrontendSyncManager converts them to Int:
 *   gogGameDao.getInstalledGames().map { (it.id.toIntOrNull() ?: 0) to it.title }
 * If a GOG ID is non-numeric, it becomes "0" in the marker file. This is a GameNative
 * limitation — we just parse whatever Int is written.
 *
 * Epic and Amazon IDs are Room auto-generated integers, NOT the stores' native IDs.
 * The native IDs (catalogId, productId) are stored in separate columns.
 *
 * ## Folder Resolution
 *
 * Uses eOr's configured Steam Library path (Settings → Games → Steam Library Folder).
 * User must set the SAME folder in GameNative (Settings → Interface → Frontend Sync).
 * Falls back to common hardcoded paths only if eOr's setting is blank.
 *
 * This is File-based storage access, NOT SAF. The folder must be on accessible storage
 * (typically /sdcard or app-specific directories).
 */
@Singleton
class GameNativeProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : GameProvider {

    override val id = ProviderId.GAME_NATIVE

    override val capabilities = setOf(
        ProviderCapability.DIRECT_LAUNCH,
        ProviderCapability.TEMP_RESOLUTION_OVERRIDE,
        ProviderCapability.LOCAL
    )

    override suspend fun isAvailable(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    /**
     * Discover games from GameNative's Frontend Sync export files.
     *
     * File format: `<SanitizedTitle>.<ext>` where file content is the numeric AppID.
     * All marker files contain integer AppIDs as UTF-8 strings.
     *
     * User setup required:
     * 1. In CreteOS: Settings → Games → Steam Library Folder → pick a folder
     * 2. In GameNative: Settings → Interface → Frontend Sync → pick the SAME folder
     * 3. Tap "Rescan PC & Streaming Providers" in CreteOS
     */
    override suspend fun discoverGames(): List<DiscoveredProviderGame> {
        val exportDir = resolveExportDirectory() ?: run {
            Log.d(TAG, "No export directory found — configure Steam Library Folder in Settings")
            return emptyList()
        }

        val results = mutableListOf<DiscoveredProviderGame>()

        exportDir.walkTopDown().maxDepth(2)
            .filter { it.isFile && it.extension.lowercase() in EXPORT_EXTENSIONS }
            .forEach { file ->
                runCatching {
                    val source = EXPORT_EXTENSIONS[file.extension.lowercase()] ?: return@forEach
                    
                    // All marker files contain integer AppIDs (verified from GameNative source)
                    val fileContent = file.readText(Charsets.UTF_8).trim()
                    val appId = fileContent.toIntOrNull()
                    if (appId == null || appId <= 0) {
                        Log.w(TAG, "Invalid AppID in ${file.name}: '$fileContent'")
                        return@forEach
                    }
                    
                    val title = file.nameWithoutExtension.trim()
                    val hostKey = buildHostKey(appId, source)

                    results += DiscoveredProviderGame(
                        provider    = ProviderId.GAME_NATIVE,
                        externalId  = appId.toString(),
                        source      = source,
                        displayName = title,
                        launchData  = "{}",
                        hostGameKey = hostKey
                    )
                }.onFailure { e ->
                    Log.w(TAG, "Failed to parse export file ${file.name}: ${e.message}")
                }
            }

        Log.i(TAG, "GameNative frontend sync: found ${results.size} games in ${exportDir.absolutePath}")
        return results
    }

    /**
     * Resolve the export directory for GameNative Frontend Sync.
     *
     * Priority:
     * 1. eOr's configured Steam Library path (Settings → Games → Steam Library Folder)
     * 2. Fallback to common hardcoded paths if eOr setting is blank
     *
     * Returns the first directory that contains export files, or null.
     */
    private suspend fun resolveExportDirectory(): File? {
        // 1. Check eOr's configured Steam Library path first
        val configuredPath = settingsRepository.steamLibraryPath.firstOrNull()
        if (!configuredPath.isNullOrBlank()) {
            val configuredDir = File(configuredPath)
            if (configuredDir.isDirectory && hasExportFiles(configuredDir)) {
                Log.d(TAG, "Using configured Steam Library path: $configuredPath")
                return configuredDir
            }
            // Configured but no export files — log for debugging but continue to fallbacks
            if (configuredDir.isDirectory) {
                Log.d(TAG, "Configured path $configuredPath has no export files — user may need to run Frontend Sync in GameNative")
            }
        }

        // 2. Fallback to common hardcoded paths
        val fallbacks = listOf(
            "/sdcard/ROMs",
            "/sdcard/ROMs/steam",
            "/sdcard/Games",
            "/sdcard/GameNative",
            "/sdcard/frontend"
        )
        
        return fallbacks.map { File(it) }.firstOrNull { dir ->
            dir.isDirectory && hasExportFiles(dir)
        }
    }

    private fun hasExportFiles(dir: File): Boolean =
        dir.walkTopDown().maxDepth(2).any { it.isFile && it.extension.lowercase() in EXPORT_EXTENSIONS }

    override suspend fun launch(target: LaunchTarget, launchContext: LaunchContext): Result<Unit> {
        val appId = target.externalId.toIntOrNull()
            ?: return Result.failure(
                IllegalArgumentException("Invalid GameNative app_id: '${target.externalId}'")
            )

        return runCatching {
            val intent = Intent(LAUNCH_ACTION).apply {
                setPackage(PACKAGE)
                putExtra(EXTRA_APP_ID, appId)
                putExtra(EXTRA_GAME_SOURCE, target.source.ifBlank { DEFAULT_SOURCE })
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // Temporary XREAL resolution override — in-memory only, never persists
                if (launchContext.displayPolicy == DisplayPolicy.AUTO_MATCH_DISPLAY &&
                    launchContext.externalDisplayConnected) {
                    val screenSize = "${launchContext.displayWidth}x${launchContext.displayHeight}"
                    putExtra(EXTRA_CONTAINER_CONFIG, """{"screenSize":"$screenSize"}""")
                    Log.d(TAG, "Temporary screenSize override: $screenSize")
                }
            }
            context.startActivity(intent)
            Log.d(TAG, "Launched GameNative app_id=$appId source=${target.source}")
        }
    }

    companion object {
        const val PACKAGE = "app.gamenative"
        const val LAUNCH_ACTION = "app.gamenative.LAUNCH_GAME"
        const val EXTRA_APP_ID = "app_id"
        const val EXTRA_GAME_SOURCE = "game_source"
        const val EXTRA_CONTAINER_CONFIG = "container_config"
        const val DEFAULT_SOURCE = "STEAM"

        /**
         * Extension to source mapping — matches FrontendSyncManager.extensionFor()
         */
        val EXPORT_EXTENSIONS = mapOf(
            "steam"   to "STEAM",
            "gog"     to "GOG",
            "epic"    to "EPIC",
            "amazon"  to "AMAZON",
            "pcgame"  to "CUSTOM_GAME"
        )

        /**
         * Build the eOr romPath key from AppID and source.
         *
         * SOURCE determines the format — provider identity is irrelevant:
         *   STEAM       → steam:<id>
         *   GOG         → steam:GOG:<id>
         *   EPIC        → steam:EPIC:<id>
         *   AMAZON      → steam:AMAZON:<id>
         *   CUSTOM_GAME → steam:CUSTOM_GAME:<id>
         */
        fun buildHostKey(appId: Int, source: String): String = when (source) {
            "STEAM"       -> "steam:$appId"
            "GOG"         -> "steam:GOG:$appId"
            "EPIC"        -> "steam:EPIC:$appId"
            "AMAZON"      -> "steam:AMAZON:$appId"
            "CUSTOM_GAME" -> "steam:CUSTOM_GAME:$appId"
            else          -> "steam:$appId"
        }

        private const val TAG = "GameNativeProvider"
    }
}
