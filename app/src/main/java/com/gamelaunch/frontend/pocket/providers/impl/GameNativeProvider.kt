package com.gamelaunch.frontend.pocket.providers.impl

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gamelaunch.frontend.pocket.domain.DiscoveredProviderGame
import com.gamelaunch.frontend.pocket.domain.DisplayPolicy
import com.gamelaunch.frontend.pocket.domain.LaunchContext
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderCapability
import com.gamelaunch.frontend.pocket.providers.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for GameNative — the primary local PC-game runtime.
 *
 * DISCOVERY: GameNative 1.2.0 includes Frontend Sync (PR #1454, merged 2026-05-30).
 * The user configures an export directory in GameNative:
 *   Settings → Interface → Frontend Sync → pick a folder per store
 *
 * GameNative writes: <SanitizedTitle>.<ext>  (file content = numeric AppID string)
 * Extensions: .steam, .gog, .epic, .amazon, .pcgame
 *
 * SOURCE determines the romPath format — NOT the provider:
 *   .steam  → steam:<id>
 *   .gog    → steam:GOG:<id>
 *   .epic   → steam:EPIC:<id>
 *   .amazon → steam:AMAZON:<id>
 *   .pcgame → steam:CUSTOM_GAME:<id>
 *
 * LAUNCH: sends app.gamenative.LAUNCH_GAME with app_id and game_source.
 * We do NOT send container_config normally — GameNative uses its own saved settings.
 *
 * XREAL: when AUTO_MATCH_DISPLAY is active and an external display is detected,
 * a temporary {"screenSize":"WxH"} container_config override is sent.
 * This is in-memory only and does NOT overwrite GameNative's saved configuration.
 */
@Singleton
class GameNativeProvider @Inject constructor(
    @ApplicationContext private val context: Context
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
     * Searches common export directory locations for .steam/.gog/.epic/.amazon/.pcgame files.
     * Returns empty if no configured export directory is found.
     *
     * User setup required:
     * 1. In GameNative: Settings → Interface → Frontend Sync → pick export folder
     * 2. In CreteOS: Settings → Games → Steam Library Folder → same folder
     * Then tap "Rescan PC & Streaming Providers" in CreteOS.
     */
    override suspend fun discoverGames(): List<DiscoveredProviderGame> {
        val exportDir = resolveExportDirectory() ?: run {
            Log.d(TAG, "No frontend sync export directory found — configure GameNative Frontend Sync")
            return emptyList()
        }

        val results = mutableListOf<DiscoveredProviderGame>()

        exportDir.walkTopDown().maxDepth(2)
            .filter { it.isFile && it.extension.lowercase() in EXPORT_EXTENSIONS }
            .forEach { file ->
                runCatching {
                    val source = EXPORT_EXTENSIONS[file.extension.lowercase()] ?: return@forEach
                    val appId  = file.readText(Charsets.UTF_8).trim().toIntOrNull()
                        ?: return@forEach
                    val title  = file.nameWithoutExtension.trim()

                    // Source determines the hostGameKey format — NOT the provider
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
     * Find the GameNative frontend sync export directory.
     * Checks common paths where the user might have pointed GameNative's export folder.
     * Returns the first directory that actually contains export files, or null.
     */
    private fun resolveExportDirectory(): File? {
        val candidates = listOf(
            "/sdcard/ROMs",
            "/sdcard/ROMs/steam",
            "/sdcard/Games",
            "/sdcard/GameNative",
            "/sdcard/frontend"
        )
        return candidates.map { File(it) }.firstOrNull { dir ->
            dir.isDirectory && dir.walkTopDown().maxDepth(2)
                .any { it.isFile && it.extension.lowercase() in EXPORT_EXTENSIONS }
        }
    }

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
        // Verified against GameNative 1.2.0 (app.gamenative), 2026-08-26
        const val PACKAGE = "app.gamenative"
        const val LAUNCH_ACTION = "app.gamenative.LAUNCH_GAME"
        const val EXTRA_APP_ID = "app_id"
        const val EXTRA_GAME_SOURCE = "game_source"
        const val EXTRA_CONTAINER_CONFIG = "container_config"
        const val DEFAULT_SOURCE = "STEAM"

        // Matches FrontendSyncManager.extensionFor() in GameNative source (PR #1454)
        val EXPORT_EXTENSIONS = mapOf(
            "steam"   to "STEAM",
            "gog"     to "GOG",
            "epic"    to "EPIC",
            "amazon"  to "AMAZON",
            "pcgame"  to "CUSTOM_GAME"
        )

        // Source determines the eOr romPath format — provider identity is irrelevant
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
