package com.gamelaunch.frontend.pocket.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.pocket.data.SteamMetadataDao
import com.gamelaunch.frontend.pocket.data.repository.LaunchTargetRepository
import com.gamelaunch.frontend.pocket.emulation.RomLibraryMetadataReconciler
import com.gamelaunch.frontend.pocket.providers.ProviderId
import com.gamelaunch.frontend.pocket.sync.PcLibraryMetadataReconciler
import com.gamelaunch.frontend.pocket.sync.AndroidLibraryInfrastructureReconciler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val games: List<Game> = emptyList(),
    val mediaForGames: Map<Long, GameMedia> = emptyMap(),
    val isLoading: Boolean = true,
    val localAppIds: Set<String> = emptySet(),
    val cloudGameKeys: Set<String> = emptySet(),
    val activeFilterName: String = "ALL",
    val filterInitialized: Boolean = false,
    val focusedGameId: Long? = null,
    val showSources: Boolean = false,
    val showSearch: Boolean = false,
    val searchText: String = "",
    val gridFirstVisibleItemIndex: Int = 0,
    val gridFirstVisibleItemScrollOffset: Int = 0
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val mediaRepository: MediaRepository,
    private val steamMetadataDao: SteamMetadataDao,
    private val launchTargetRepository: LaunchTargetRepository,
    private val romMetadataReconciler: RomLibraryMetadataReconciler,
    private val pcMetadataReconciler: PcLibraryMetadataReconciler,
    private val androidInfrastructureReconciler: AndroidLibraryInfrastructureReconciler
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                gameRepository.getAllGames(),
                mediaRepository.observeAllMedia(),
                steamMetadataDao.observeLocalAppIds(),
                // A generic "open GFN library" fallback does not establish that the individual
                // game is cloud-playable, so only direct provider links feed the Cloud chip.
                launchTargetRepository.observeDirectCloudHostGameKeys(ProviderId.GEFORCE_NOW)
            ) { games, mediaMap, localIds, cloudGameKeys ->
                LibrarySnapshot(games, mediaMap, localIds.toSet(), cloudGameKeys)
            }.collectLatest { snapshot ->
                _uiState.value = _uiState.value.copy(
                    games = snapshot.games,
                    mediaForGames = snapshot.mediaMap,
                    isLoading = false,
                    localAppIds = snapshot.localAppIds,
                    cloudGameKeys = snapshot.cloudGameKeys
                )
            }
        }

        // Repair scanner-generated ROM titles and promote existing/new IGDB metadata into the
        // eOr media repository that powers every library and home tile.
        viewModelScope.launch {
            gameRepository.getAllGames().collect { games ->
                androidInfrastructureReconciler.reconcile(games)
                romMetadataReconciler.reconcile(games)
                pcMetadataReconciler.reconcile(games)
            }
        }
    }

    fun initializeFilter(filterName: String) {
        if (_uiState.value.filterInitialized) return
        _uiState.value = _uiState.value.copy(
            activeFilterName = filterName,
            filterInitialized = true
        )
    }

    fun selectFilter(filterName: String) {
        _uiState.value = _uiState.value.copy(
            activeFilterName = filterName,
            filterInitialized = true,
            focusedGameId = null,
            gridFirstVisibleItemIndex = 0,
            gridFirstVisibleItemScrollOffset = 0
        )
    }

    fun setFocusedGame(gameId: Long?) {
        _uiState.value = _uiState.value.copy(focusedGameId = gameId)
    }

    fun setSourcesVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(
            showSources = visible,
            showSearch = if (visible) false else _uiState.value.showSearch
        )
    }

    fun setSearchVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(
            showSearch = visible,
            showSources = if (visible) false else _uiState.value.showSources,
            searchText = if (visible) _uiState.value.searchText else ""
        )
    }

    fun setSearchText(text: String) {
        _uiState.value = _uiState.value.copy(searchText = text)
    }

    fun setGridPosition(index: Int, offset: Int) {
        if (index == _uiState.value.gridFirstVisibleItemIndex &&
            offset == _uiState.value.gridFirstVisibleItemScrollOffset
        ) return
        _uiState.value = _uiState.value.copy(
            gridFirstVisibleItemIndex = index,
            gridFirstVisibleItemScrollOffset = offset
        )
    }

}

private data class LibrarySnapshot(
    val games: List<Game>,
    val mediaMap: Map<Long, GameMedia>,
    val localAppIds: Set<String>,
    val cloudGameKeys: Set<String>
)
