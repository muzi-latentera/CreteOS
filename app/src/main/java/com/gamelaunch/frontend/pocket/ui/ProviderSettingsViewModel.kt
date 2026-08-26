package com.gamelaunch.frontend.pocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.pocket.data.repository.LaunchTargetRepository
import com.gamelaunch.frontend.pocket.display.GamingDisplayManager
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderId
import com.gamelaunch.frontend.pocket.sync.ProviderSyncCoordinator
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
    val isScanning: Boolean = false,
    val lastSyncResult: String? = null
)

@HiltViewModel
class ProviderSettingsViewModel @Inject constructor(
    private val providers: Map<ProviderId, @JvmSuppressWildcards GameProvider>,
    private val launchTargetRepository: LaunchTargetRepository,
    private val gamingDisplayManager: GamingDisplayManager,
    private val providerSyncCoordinator: ProviderSyncCoordinator
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

    /**
     * Sync a single provider using the real ProviderSyncCoordinator.
     * This is what the Sync/Rescan button calls.
     */
    fun rescan(providerId: ProviderId) {
        val provider = providers[providerId] ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, lastSyncResult = null) }
            val result = runCatching {
                providerSyncCoordinator.syncProvider(providerId, provider)
            }.getOrElse { e ->
                ProviderSyncCoordinator.SyncResult(
                    providerId, 0, 0, 0, 0, listOf(e.message ?: "Error")
                )
            }
            val summary = buildSyncSummary(listOf(result))
            refreshStatuses()
            _uiState.update { it.copy(isScanning = false, lastSyncResult = summary) }
        }
    }

    /**
     * Sync all providers. Called by "Sync all" button.
     */
    fun rescanAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, lastSyncResult = null) }
            val results = runCatching {
                providerSyncCoordinator.syncAll()
            }.getOrElse { emptyList() }
            val summary = buildSyncSummary(results)
            refreshStatuses()
            _uiState.update { it.copy(isScanning = false, lastSyncResult = summary) }
        }
    }

    private fun buildSyncSummary(results: List<ProviderSyncCoordinator.SyncResult>): String {
        if (results.isEmpty()) return "No providers synced"
        val totalAdded   = results.sumOf { it.added }
        val totalStale   = results.sumOf { it.markedUnavailable }
        val totalErrors  = results.sumOf { it.errors.size }
        return buildString {
            if (totalAdded > 0) append("Added $totalAdded game${if (totalAdded == 1) "" else "s"}. ")
            if (totalStale > 0) append("$totalStale removed. ")
            if (totalErrors > 0) append("$totalErrors error${if (totalErrors == 1) "" else "s"}.")
            if (totalAdded == 0 && totalStale == 0 && totalErrors == 0) append("Library is up to date.")
        }.trim()
    }
}
