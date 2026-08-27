package com.gamelaunch.frontend.pocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.usecase.LaunchGameUseCase
import android.content.Context
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
    val steamMetadata: SteamMetadataEntity? = null
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

        if (game.platformId.lowercase() == "steam") {
            val appId = game.romPath.substringAfterLast(":")
            if (appId.isNotBlank()) {
                // Auto-create provider targets
                viewModelScope.launch { autoProvisionTargets(game, appId) }

                // Load / sync Steam metadata
                viewModelScope.launch {
                    val cached = steamMetadataDao.getByAppId(appId)
                    _uiState.update { it.copy(steamMetadata = cached) }

                    val now = System.currentTimeMillis()
                    val staleTtlMs = 7L * 24 * 60 * 60 * 1000  // 7 days

                    if (cached == null) {
                        steamMetadataSync.syncLibrary()
                        steamMetadataSync.seedGfnIds()
                    }

                    val needsAchievementSync = cached?.achievementsSyncedAtMs == null ||
                        (now - (cached.achievementsSyncedAtMs ?: 0L)) > staleTtlMs
                    if (needsAchievementSync) steamMetadataSync.syncAchievements(appId)

                    val afterSync = steamMetadataDao.getByAppId(appId)
                    if (afterSync?.developer == null) steamMetadataSync.fetchAppDetails(appId)

                    _uiState.update { it.copy(steamMetadata = steamMetadataDao.getByAppId(appId)) }
                }

                // TTB via IGDB (replaces HLTB direct call)
                viewModelScope.launch {
                    _uiState.update { it.copy(hltbLoading = true) }
                    // Seed pre-fetched data first (instant)
                    seedIgdbData(appId, game.title)
                    // Then check cache
                    val cached = hltbProvider.getCached(appId)
                    if (cached != null) {
                        _uiState.update { it.copy(hltbTimes = cached, hltbLoading = false) }
                    } else {
                        // Not in seed data — try live IGDB fetch
                        igdbSync.syncGame(appId, game.title)
                        val fresh = hltbProvider.getCached(appId) ?: HltbTimes.EMPTY
                        _uiState.update { it.copy(hltbTimes = fresh, hltbLoading = false) }
                    }
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

        // Priority 3: GeForce NOW — cloud, use real UUID if we have it
        if (isInstalled("com.nvidia.geforcenow")) {
            val gfnId = SteamMetadataSync.GFN_CATALOG[steamAppId]
                ?: steamMetadataDao.getByAppId(steamAppId)?.gfnGameId
            toUpsert += LaunchTarget(
                hostGameKey = game.romPath,
                provider    = ProviderId.GEFORCE_NOW,
                externalId  = gfnId ?: steamAppId,
                source      = "STEAM",
                displayName = "Play on GeForce NOW",
                launchData  = if (gfnId != null)
                    """{"gfnGameId":"$gfnId","steamAppId":"$steamAppId"}"""
                else
                    """{"steamAppId":"$steamAppId"}""",
                isPreferred = toUpsert.isEmpty() // Preferred only if nothing else available
            )
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
        )
    }

    fun showPlayUsing() = _uiState.update { it.copy(showPlayUsing = true) }

    fun dismissPlayUsing() = _uiState.update { it.copy(showPlayUsing = false) }

    fun launchWithTarget(game: Game, target: LaunchTarget) {
        _uiState.update { it.copy(showPlayUsing = false) }
        viewModelScope.launch {
            val result = unifiedLaunchCoordinator.launchSpecific(target)
            if (result.isSuccess) {
                gameSessionDao.startSession(
                    GameSessionEntity(
                        gameKey     = game.romPath,
                        startedAtMs = System.currentTimeMillis(),
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
