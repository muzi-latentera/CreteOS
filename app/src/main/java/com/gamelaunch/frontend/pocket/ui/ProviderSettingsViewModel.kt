package com.gamelaunch.frontend.pocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.pocket.data.repository.LaunchTargetRepository
import com.gamelaunch.frontend.pocket.display.GamingDisplayManager
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderSettingsUiState(
    val providerStatuses: List<ProviderStatus> = emptyList(),
    val activeDisplay: ActiveDisplayInfo? = null,
    val isScanning: Boolean = false
)

@HiltViewModel
class ProviderSettingsViewModel @Inject constructor(
    private val providers: Map<ProviderId, @JvmSuppressWildcards GameProvider>,
    private val launchTargetRepository: LaunchTargetRepository,
    private val gamingDisplayManager: GamingDisplayManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderSettingsUiState())
    val uiState: StateFlow<ProviderSettingsUiState> = _uiState

    init {
        observeDisplay()
        refreshStatuses()
    }

    private fun observeDisplay() {
        viewModelScope.launch {
            gamingDisplayManager.activeDisplay.collectLatest { display ->
                _uiState.update {
                    it.copy(
                        activeDisplay = ActiveDisplayInfo(
                            width = display.width,
                            height = display.height,
                            refreshRate = display.refreshRate,
                            isExternal = display.isExternal,
                            name = display.name
                        )
                    )
                }
            }
        }
    }

    private fun refreshStatuses() {
        viewModelScope.launch {
            val statuses = providers.entries.map { (id, provider) ->
                ProviderStatus(
                    providerId = id,
                    isInstalled = provider.isAvailable(),
                    gameCount = launchTargetRepository.countAvailableForProvider(id)
                )
            }.sortedBy { it.providerId.displayName }
            _uiState.update { it.copy(providerStatuses = statuses) }
        }
    }

    fun rescan(providerId: ProviderId) {
        viewModelScope.launch {
            val provider = providers[providerId] ?: return@launch
            _uiState.update { it.copy(isScanning = true) }
            runCatching {
                val discovered = provider.discoverGames()
                // TODO Phase 11+: feed discovered games through ProviderSyncCoordinator
            }
            refreshStatuses()
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    fun rescanAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            providers.values.forEach { provider ->
                runCatching { provider.discoverGames() }
            }
            refreshStatuses()
            _uiState.update { it.copy(isScanning = false) }
        }
    }
}
