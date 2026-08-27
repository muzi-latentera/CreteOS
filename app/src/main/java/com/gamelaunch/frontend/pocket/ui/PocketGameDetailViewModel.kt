package com.gamelaunch.frontend.pocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.usecase.LaunchGameUseCase
import android.content.Context
import com.gamelaunch.frontend.data.db.dao.GameDao
import com.gamelaunch.frontend.pocket.data.GameSessionDao
import com.gamelaunch.frontend.pocket.data.GameSessionEntity
import com.gamelaunch.frontend.pocket.data.HltbTimes
import com.gamelaunch.frontend.pocket.data.HowLongToBeatProvider
import com.gamelaunch.frontend.pocket.data.IgdbMetadataSync
import com.gamelaunch.frontend.pocket.data.IgdbSeedData
import com.gamelaunch.frontend.pocket.data.SteamMetadataDao
import com.gamelaunch.frontend.pocket.data.SteamMetadataEntity
import com.gamelaunch.frontend.pocket.data.SteamMetadataSync
import com.gamelaunch.frontend.pocket.data.repository.LaunchTargetRepository
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.emulation.EmulatorRegistry
import com.gamelaunch.frontend.pocket.emulation.EmulatorSystem
import com.gamelaunch.frontend.pocket.launch.UnifiedLaunchCoordinator
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PocketLaunchUiState(
    val targets: List<LaunchTarget> = emptyList(),
    val showPlayUsing: Boolean = false,
    val launchError: String? = null,
    val hltbTimes: HltbTimes = HltbTimes.EMPTY,
    val hltbLoading: Boolean = false,
    val steamMetadata: SteamMetadataEntity? = null,
    val isLocal: Boolean = false,
)

/**
 * Companion ViewModel for the game detail screen.
 * Provides pocket launch-target state without modifying [GameDetailViewModel].
 *
 * Inject this alongside GameDetailViewModel in the detail screen composable.
 */
@HiltViewModel
class PocketGameDetailViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val launchTargetRepository: LaunchTargetRepository,
    private val unifiedLaunchCoordinator: UnifiedLaunchCoordinator,
    private val launchGameUseCase: LaunchGameUseCase,
    private val providers: Map<ProviderId, @JvmSuppressWildcards GameProvider>,
    private val hltbProvider: HowLongToBeatProvider,
    private val steamMetadataDao: SteamMetadataDao,
    private val steamMetadataSync: SteamMetadataSync,
    private val gameSessionDao: GameSessionDao,
    private val igdbSync: IgdbMetadataSync,
    private val gameDao: GameDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PocketLaunchUiState())
    val uiState: StateFlow<PocketLaunchUiState> = _uiState

    fun loadTargetsForGame(game: Game) {
        // Observe targets reactively
        viewModelScope.launch {
            launchTargetRepository.getTargetsForGame(game.romPath).collectLatest { targets ->
                _uiState.update { it.copy(targets = targets) }
            }
        }

        // The game key suffix (last segment of romPath) is used as the lookup ID
        // for both Steam AppIDs (107100) and non-Steam fake IDs (bf6_ea, acs_ubi etc)
        val appId = game.romPath.substringAfterLast(":")
        if (appId.isNotBlank()) {
            // Auto-provision launch targets (GFN/Moonlight/GameNative) for all games
            viewModelScope.launch { autoProvisionTargets(game, appId) }

            // IGDB seed — runs for ALL platforms (instant, from bundled IgdbSeedData)
            viewModelScope.launch {
                _uiState.update { it.copy(hltbLoading = true) }
                seedIgdbData(appId, game.title)

                // For Steam games, also sync live Steam metadata (playtime, achievements etc)
                if (game.platformId.lowercase() == "steam") {
                    val cached = steamMetadataDao.getByAppId(appId)
                    _uiState.update { it.copy(steamMetadata = cached) }

                    val now = System.currentTimeMillis()
                    val staleTtlMs = 7L * 24 * 60 * 60 * 1000

                    if (cached == null) {
                        steamMetadataSync.syncLibrary()
                        steamMetadataSync.seedGfnIds()
                    }

                    val needsAchievementSync = cached?.achievementsSyncedAtMs == null ||
                        (now - (cached.achievementsSyncedAtMs ?: 0L)) > staleTtlMs
                    if (needsAchievementSync) steamMetadataSync.syncAchievements(appId)

                    val afterSync = steamMetadataDao.getByAppId(appId)
                    if (afterSync?.developer == null) steamMetadataSync.fetchAppDetails(appId)
                }

                // For non-Steam games, create a stub steam_metadata record so IGDB data has somewhere to land
                if (game.platformId.lowercase() != "steam") {
                    if (steamMetadataDao.getByAppId(appId) == null) {
                        steamMetadataDao.upsert(SteamMetadataEntity(
                            steamAppId = appId,
                            playtimeMinutes = 0,
                            updatedAtMs = System.currentTimeMillis()
                        ))
                    }
                    // Re-run seed now the record exists
                    seedIgdbData(appId, game.title)
                }

                // Update UI with latest metadata
                val meta = steamMetadataDao.getByAppId(appId)
                _uiState.update { it.copy(steamMetadata = meta, isLocal = meta?.isLocal ?: false) }

                // For emulated games, trigger IGDB search by title if no cover art yet
                // Handles both emu: romPaths and eOr's native file path romPaths (detected via platform_id)
                val emulationSystems = setOf("gc","wii","wiiu","ps1","ps2","ps3","psp","psvita",
                    "gba","gb","gbc","nds","n3ds","switch","n64","dreamcast","saturn")
                val isEmulatedGame = game.romPath.startsWith("emu:") || 
                    game.platformId in emulationSystems
                if (isEmulatedGame) {
                    val system = when {
                        game.romPath.startsWith("emu:") -> {
                            val parts = game.romPath.split(":")
                            parts.getOrNull(1)?.let { EmulatorSystem.fromId(it) }
                        }
                        else -> EmulatorSystem.fromId(game.platformId)
                    }
                    if (system != null && meta?.igdbCoverUrl == null) {
                        igdbSync.syncEmulatedGame(game.romPath, game.title, system)
                        val updatedMeta = steamMetadataDao.getByAppId(game.romPath)
                        _uiState.update { it.copy(steamMetadata = updatedMeta) }
                    }
                }

                // TTB from cache (populated by seedIgdbData above)
                val ttb = hltbProvider.getCached(appId)
                if (ttb != null) {
                    _uiState.update { it.copy(hltbTimes = ttb, hltbLoading = false) }
                } else {
                    // Try live IGDB fetch as last resort
                    igdbSync.syncGame(appId, game.title)
                    val fresh = hltbProvider.getCached(appId) ?: HltbTimes.EMPTY
                    _uiState.update { it.copy(hltbTimes = fresh, hltbLoading = false) }
                }
            }
        }
    }

    /**
     * Auto-create LaunchTarget rows for every installed provider that supports this Steam game.
     * Uses REPLACE conflict strategy so re-running is safe and updates availability.
     *
     * Targets created:
     * - GAME_NATIVE  — if app.gamenative is installed
     * - GEFORCE_NOW  — if com.nvidia.geforcenow is installed (opens to game or library)
     * - MOONLIGHT    — if com.limelight is installed (opens to PC stream list)
     */
    private suspend fun autoProvisionTargets(game: Game, steamAppId: String) {
        val pm = context.packageManager
        fun isInstalled(pkg: String) = runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)

        val toUpsert = mutableListOf<LaunchTarget>()

        // Priority 1: GameNative — local PC, best experience
        if (isInstalled("app.gamenative")) {
            toUpsert += LaunchTarget(
                hostGameKey = game.romPath,
                provider    = ProviderId.GAME_NATIVE,
                externalId  = steamAppId,
                source      = "STEAM",
                displayName = "Play on PC (GameNative)",
                launchData  = """{"steamAppId":"$steamAppId"}""",
                isPreferred = true   // Default to local when available
            )
        }

        // Priority 2: Moonlight — streaming from local PC
        if (isInstalled("com.limelight")) {
            toUpsert += LaunchTarget(
                hostGameKey = game.romPath,
                provider    = ProviderId.MOONLIGHT,
                externalId  = steamAppId,
                source      = "STEAM",
                displayName = "Stream via Moonlight",
                launchData  = """{"steamAppId":"$steamAppId"}""",
                isPreferred = toUpsert.isEmpty() // Preferred only if GameNative not installed
            )
        }

        // Priority 3: GeForce NOW — only for verified canonical URLs
        // Does NOT touch GameNative or Moonlight targets
        if (isInstalled("com.nvidia.geforcenow")) {
            val canonicalUrl = SteamMetadataSync.GFN_VERIFIED[steamAppId]
            val gfnId = canonicalUrl?.let { Regex("game-id=([^&]+)").find(it)?.groupValues?.get(1) }

            // Check if existing GFN target needs upgrading to canonical URL
            val currentGfnTargets = launchTargetRepository.getTargetsForGameOnce(game.romPath)
                .filter { it.provider == ProviderId.GEFORCE_NOW }
            val alreadyHasCanonical = currentGfnTargets.any { it.launchData.contains("canonicalGfnUrl") }

            if (currentGfnTargets.isEmpty() || (!alreadyHasCanonical && canonicalUrl != null)) {
                toUpsert += LaunchTarget(
                    id          = currentGfnTargets.firstOrNull()?.id ?: 0L,
                    hostGameKey = game.romPath,
                    provider    = ProviderId.GEFORCE_NOW,
                    externalId  = gfnId ?: steamAppId,
                    source      = "STEAM",
                    displayName = if (canonicalUrl != null) "GeForce NOW" else "GeForce NOW (library)",
                    launchData  = if (canonicalUrl != null)
                        """{"canonicalGfnUrl":"${canonicalUrl.replace("\"","\\\"")}","steamAppId":"$steamAppId"}"""
                    else
                        """{"steamAppId":"$steamAppId"}""",
                    isPreferred = currentGfnTargets.firstOrNull()?.isPreferred ?: toUpsert.isEmpty()
                )
            }
        }

        // Priority 4: Emulators — for emulated games
        // Handles both emu: romPaths (our seeder) and eOr's native file path romPaths (detected via platform_id)
        val emulationSystemIds = setOf("gc","wii","wiiu","ps1","ps2","ps3","psp","psvita",
            "gba","gb","gbc","nds","n3ds","switch","n64","dreamcast","saturn")
        val isEmulatedGame = game.romPath.startsWith("emu:") || game.platformId in emulationSystemIds
        if (isEmulatedGame) {
            val system = when {
                game.romPath.startsWith("emu:") -> {
                    val parts = game.romPath.split(":")
                    if (parts.size >= 2) EmulatorSystem.fromId(parts[1]) else null
                }
                else -> EmulatorSystem.fromId(game.platformId)
            }
            if (system != null) {
                val emulators = EmulatorRegistry.forSystem(system)
                val existingTargets = launchTargetRepository.getTargetsForGameOnce(game.romPath)

                for (emulatorDef in emulators) {
                    val installedPkg = EmulatorRegistry.findInstalledPackage(context, emulatorDef) ?: continue
                    val existingTarget = existingTargets.find {
                        it.provider == ProviderId.EMULATOR && it.launchData.contains("\"emulatorId\":\"${emulatorDef.id}\"")
                    }
                    if (existingTarget != null) {
                        android.util.Log.d("AutoProvision", "emu target exists: pkg=$installedPkg emulator=${emulatorDef.id}")
                        continue
                    }

                    // For eOr native romPaths (file paths), romPath IS the absolute path
                    val romAbsPath: String? = when {
                        game.romPath.startsWith("emu:") -> {
                            // Look up from steam_metadata or scan filesystem
                            val metadata = steamMetadataDao.getByAppId(game.romPath)
                            var path = metadata?.romAbsPath
                            if (path == null) {
                                val parts = game.romPath.split(":")
                                val systemId = parts.getOrNull(1) ?: system.id
                                val romKey = parts.getOrNull(2) ?: ""
                                val basePaths = listOf(
                                    "/storage/emulated/0/CreteOS/Emulation/ROMs/$systemId",
                                    "/storage/emulated/0/ROMs/$systemId"
                                )
                                for (basePath in basePaths) {
                                    val dir = java.io.File(basePath)
                                    if (!dir.exists()) continue
                                    val match = dir.listFiles()?.firstOrNull { f ->
                                        val clean = f.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9]+"), "_")
                                        clean == romKey || clean.contains(romKey)
                                    }
                                    if (match != null) { path = match.absolutePath; break }
                                }
                            }
                            path
                        }
                        else -> {
                            // eOr native: romPath is already the absolute file path
                            if (java.io.File(game.romPath).exists()) game.romPath
                            else {
                                android.util.Log.w("AutoProvision", "ROM file not found: ${game.romPath}")
                                null
                            }
                        }
                    }

                    if (romAbsPath == null) {
                        android.util.Log.w("AutoProvision", "emu target skipped - no romAbsPath for ${game.romPath}")
                        continue
                    }

                    android.util.Log.d("AutoProvision", "emu target: pkg=$installedPkg romPath=$romAbsPath emulator=${emulatorDef.id}")

                    val launchDataJson = org.json.JSONObject().apply {
                        put("romPath", romAbsPath)
                        put("emulatorId", emulatorDef.id)
                        put("system", system.id)
                    }.toString()

                    toUpsert += LaunchTarget(
                        hostGameKey = game.romPath,
                        provider    = ProviderId.EMULATOR,
                        externalId  = emulatorDef.id,
                        source      = "EMULATOR",
                        displayName = "${system.displayName} • ${emulatorDef.displayName}",
                        launchData  = launchDataJson,
                        isPreferred = toUpsert.isEmpty() && existingTargets.isEmpty()
                    )
                }
            }
        }

        if (toUpsert.isNotEmpty()) {
            launchTargetRepository.upsertTargets(toUpsert)
        }
    }

    private suspend fun seedIgdbData(steamAppId: String, gameTitle: String) {
        val entry = IgdbSeedData.entries.find { it.steamAppId == steamAppId } ?: return
        igdbSync.seedPreFetchedData(
            steamAppId            = entry.steamAppId,
            gameTitle             = gameTitle,
            mainSeconds           = entry.mainSec,
            plusSeconds           = entry.plusSec,
            completionistSeconds  = entry.compSec,
            developer             = entry.developer,
            publisher             = entry.publisher,
            summary               = entry.summary,
            coverUrl              = entry.coverUrl,
            heroUrl               = entry.heroUrl,
        )
    }

    fun toggleLocal(game: Game) {
        val appId = game.romPath.substringAfterLast(":")
        if (appId.isBlank()) return
        viewModelScope.launch {
            val current = uiState.value.isLocal
            val new = !current
            // Ensure a metadata record exists first
            if (steamMetadataDao.getByAppId(appId) == null) {
                steamMetadataDao.upsert(SteamMetadataEntity(
                    steamAppId = appId,
                    playtimeMinutes = 0,
                    updatedAtMs = System.currentTimeMillis()
                ))
            }
            steamMetadataDao.setLocal(appId, new)
            _uiState.update { it.copy(isLocal = new) }
        }
    }

    fun showPlayUsing() = _uiState.update { it.copy(showPlayUsing = true) }

    fun dismissPlayUsing() = _uiState.update { it.copy(showPlayUsing = false) }

    fun launchWithTarget(game: Game, target: LaunchTarget) {
        _uiState.update { it.copy(showPlayUsing = false) }
        viewModelScope.launch {
            val result = unifiedLaunchCoordinator.launchSpecific(target)
            if (result.isSuccess) {
                val now = System.currentTimeMillis()
                // Update eOr's last_played_ms so hero tile updates
                gameDao.recordPlay(game.id, now)
                // Also update steam_metadata so detail screen shows current time
                val appId = game.romPath.substringAfterLast(":")
                if (appId.isNotBlank()) {
                    val meta = steamMetadataDao.getByAppId(appId)
                    if (meta != null) {
                        steamMetadataDao.upsert(meta.copy(
                            lastPlayedMs = now,
                            updatedAtMs  = now
                        ))
                    }
                }
                // Record CreteOS session
                gameSessionDao.startSession(
                    GameSessionEntity(
                        gameKey     = game.romPath,
                        startedAtMs = now,
                        provider    = target.provider.name
                    )
                )
            }
            result.onFailure { e -> _uiState.update { it.copy(launchError = e.message) } }
        }
    }

    /** Call from MainActivity.onResume — closes any open session when user returns to CreteOS. */
    fun endActiveSession() {
        viewModelScope.launch {
            val active = gameSessionDao.getActiveSession()
            if (active != null) {
                gameSessionDao.endSession(active.id, System.currentTimeMillis())
                // Schedule Steam playtime re-sync 10 minutes after returning from a game.
                // By then Steam has had time to record the session server-side.
                viewModelScope.launch {
                    kotlinx.coroutines.delay(10 * 60 * 1000L)  // 10 minutes
                    steamMetadataSync.syncLibrary()
                    android.util.Log.d("PocketVM", "Post-session Steam sync complete")
                }
            }
        }
    }

    fun setPreferredTarget(game: Game, target: LaunchTarget) {
        viewModelScope.launch {
            launchTargetRepository.setPreferredTarget(game.romPath, target.id)
        }
    }

    fun isProviderAvailable(providerId: ProviderId): Boolean =
        providers[providerId]?.let { _ -> true } ?: false

    fun dismissError() = _uiState.update { it.copy(launchError = null) }
}
