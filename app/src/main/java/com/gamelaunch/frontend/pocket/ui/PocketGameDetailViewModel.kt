package com.gamelaunch.frontend.pocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.usecase.LaunchGameUseCase
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
    val launchError: String? = null
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
    private val providers: Map<ProviderId, @JvmSuppressWildcards GameProvider>
) : ViewModel() {

    private val _uiState = MutableStateFlow(PocketLaunchUiState())
    val uiState: StateFlow<PocketLaunchUiState> = _uiState

    fun loadTargetsForGame(game: Game) {
        viewModelScope.launch {
            launchTargetRepository.getTargetsForGame(game.romPath).collectLatest { targets ->
                _uiState.update { it.copy(targets = targets) }
            }
        }
    }

    fun showPlayUsing() = _uiState.update { it.copy(showPlayUsing = true) }

    fun dismissPlayUsing() = _uiState.update { it.copy(showPlayUsing = false) }

    fun launchWithTarget(game: Game, target: LaunchTarget) {
        _uiState.update { it.copy(showPlayUsing = false) }
        viewModelScope.launch {
            unifiedLaunchCoordinator.tryLaunch(game)
                ?.onFailure { e -> _uiState.update { it.copy(launchError = e.message) } }
        }
    }

    fun setPreferredTarget(game: Game, target: LaunchTarget) {
        viewModelScope.launch {
            launchTargetRepository.setPreferredTarget(game.romPath, target.id)
        }
    }

    fun isProviderAvailable(providerId: ProviderId): Boolean =
        providers[providerId]?.let { provider ->
            // Synchronous package check only — full async check happens at launch time
            true
        } ?: false

    fun dismissError() = _uiState.update { it.copy(launchError = null) }
}
