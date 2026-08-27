package com.gamelaunch.frontend.pocket.providers.impl

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gamelaunch.frontend.pocket.data.SteamMetadataDao
import com.gamelaunch.frontend.pocket.domain.DiscoveredProviderGame
import com.gamelaunch.frontend.pocket.domain.LaunchContext
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.emulation.EmulatorRegistry
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderCapability
import com.gamelaunch.frontend.pocket.providers.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for emulated games (ROMs).
 *
 * Takes an EmulatorDefinition ID + rom absolute path from launchData,
 * builds the correct Intent using EmulatorRegistry.buildLaunchIntent(),
 * and launches the game via the emulator.
 *
 * launchData JSON format:
 * {
 *   "romPath": "/storage/.../game.iso",
 *   "emulatorId": "DOLPHIN",
 *   "system": "gc"
 * }
 */
@Singleton
class EmulatorProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val steamMetadataDao: SteamMetadataDao
) : GameProvider {

    override val id = ProviderId.EMULATOR

    override val capabilities = setOf(
        ProviderCapability.DIRECT_LAUNCH
    )

    /**
     * Returns true if at least one emulator is installed.
     */
    override suspend fun isAvailable(): Boolean {
        return EmulatorRegistry.findInstalled(context).isNotEmpty()
    }

    /**
     * Emulators don't discover games — RomScanner does that.
     * This provider only handles launching.
     */
    override suspend fun discoverGames(): List<DiscoveredProviderGame> = emptyList()

    /**
     * Launch the emulated game described by [target].
     *
     * Reads romPath from launchData, or falls back to steam_metadata.rom_abs_path
     * for the game's hostGameKey.
     */
    override suspend fun launch(target: LaunchTarget, launchContext: LaunchContext): Result<Unit> {
        val data = runCatching { JSONObject(target.launchData) }.getOrElse { JSONObject() }
        val emulatorId = data.optString("emulatorId").ifBlank { null }

        if (emulatorId == null) {
            Log.e(TAG, "Missing emulatorId in launchData for ${target.hostGameKey}")
            return Result.failure(IllegalStateException("Missing emulator ID in launch data"))
        }

        val emulatorDef = EmulatorRegistry.findById(emulatorId)
        if (emulatorDef == null) {
            Log.e(TAG, "Unknown emulator: $emulatorId")
            return Result.failure(IllegalStateException("Unknown emulator: $emulatorId"))
        }

        val installedPkg = EmulatorRegistry.findInstalledPackage(context, emulatorDef)
        if (installedPkg == null) {
            Log.e(TAG, "${emulatorDef.displayName} is not installed")
            return Result.failure(
                IllegalStateException("${emulatorDef.displayName} is not installed. Please install it to play this game.")
            )
        }

        // Get ROM path from launchData, or fall back to steam_metadata.rom_abs_path
        var romPath = data.optString("romPath").ifBlank { null }
        if (romPath == null) {
            // Look up from steam_metadata using the game's hostGameKey (which is the romPath like "emu:gc:luigis_mansion")
            val appId = target.hostGameKey
            val metadata = steamMetadataDao.getByAppId(appId)
            romPath = metadata?.romAbsPath
        }

        if (romPath == null) {
            Log.e(TAG, "No ROM path found for ${target.hostGameKey}")
            return Result.failure(IllegalStateException("ROM file path not found"))
        }

        Log.d(TAG, "Launching ${target.displayName} via ${emulatorDef.displayName}: $romPath")

        val intent = EmulatorRegistry.buildLaunchIntent(emulatorDef, romPath, context)
        if (intent == null) {
            Log.e(TAG, "Failed to build launch intent for ${emulatorDef.displayName}")
            return Result.failure(IllegalStateException("Failed to build launch intent"))
        }

        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Launched ${emulatorDef.displayName} successfully")
        }
    }

    companion object {
        private const val TAG = "EmulatorProvider"
    }
}
