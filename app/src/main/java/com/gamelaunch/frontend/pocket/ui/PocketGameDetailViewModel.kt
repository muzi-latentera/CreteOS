package com.gamelaunch.frontend.pocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.usecase.LaunchGameUseCase
import com.gamelaunch.frontend.pocket.data.GameSessionDao
import com.gamelaunch.frontend.pocket.data.GameSessionEntity
import com.gamelaunch.frontend.pocket.data.HltbTimes
import com.gamelaunch.frontend.pocket.data.HowLongToBeatProvider
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
    private val launchTargetRepository: LaunchTargetRepository,
    private val unifiedLaunchCoordinator: UnifiedLaunchCoordinator,
    private val launchGameUseCase: LaunchGameUseCase,
    private val providers: Map<ProviderId, @JvmSuppressWildcards GameProvider>,
    private val hltbProvider: HowLongToBeatProvider,
    private val steamMetadataDao: SteamMetadataDao,
    private val steamMetadataSync: SteamMetadataSync,
    private val gameSessionDao: GameSessionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PocketLaunchUiState())
    val uiState: StateFlow<PocketLaunchUiState> = _uiState

    fun loadTargetsForGame(game: Game) {
        viewModelScope.launch {
            launchTargetRepository.getTargetsForGame(game.romPath).collectLatest { targets ->
                _uiState.update { it.copy(targets = targets) }
            }
        }

        if (game.platformId.lowercase() == "steam") {
            val appId = game.romPath.substringAfterLast(":")
            if (appId.isNotBlank()) {
                // Load Steam metadata from local cache immediately
                viewModelScope.launch {
                    val cached = steamMetadataDao.getByAppId(appId)
                    _uiState.update { it.copy(steamMetadata = cached) }

                    val now = System.currentTimeMillis()
                    val staleTtlMs = 7L * 24 * 60 * 60 * 1000  // 7 days

                    // Sync library if never synced
                    if (cached == null) {
                        steamMetadataSync.syncLibrary()
                    }

                    // Sync achievements if:
                    //  - never fetched (achievementsSyncedAtMs == null), OR
                    //  - last fetch was > 7 days ago
                    // This is checked independently of library sync so achievements
                    // are never stuck at 0/0 just because the record exists.
                    val needsAchievementSync = cached?.achievementsSyncedAtMs == null ||
                        (now - (cached.achievementsSyncedAtMs ?: 0L)) > staleTtlMs

                    if (needsAchievementSync) {
                        steamMetadataSync.syncAchievements(appId)
                    }

                    // Update UI with latest data
                    _uiState.update { it.copy(steamMetadata = steamMetadataDao.getByAppId(appId)) }
                }

                // HLTB
                viewModelScope.launch {
                    _uiState.update { it.copy(hltbLoading = true) }
                    val times = try { hltbProvider.getTimes(appId, game.title) }
                                catch (_: Exception) { HltbTimes.EMPTY }
                    _uiState.update { it.copy(hltbTimes = times, hltbLoading = false) }
                }
            }
        }
    }

    fun showPlayUsing() = _uiState.update { it.copy(showPlayUsing = true) }

    fun dismissPlayUsing() = _uiState.update { it.copy(showPlayUsing = false) }

    fun launchWithTarget(game: Game, target: LaunchTarget) {
        _uiState.update { it.copy(showPlayUsing = false) }
        viewModelScope.launch {
            val result = unifiedLaunchCoordinator.tryLaunch(game)
            if (result?.isSuccess == true) {
                // Record session start — end is recorded when CreteOS returns to foreground
                gameSessionDao.startSession(
                    GameSessionEntity(
                        gameKey     = game.romPath,
                        startedAtMs = System.currentTimeMillis(),
                        provider    = target.provider.name
                    )
                )
            }
            result?.onFailure { e -> _uiState.update { it.copy(launchError = e.message) } }
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
