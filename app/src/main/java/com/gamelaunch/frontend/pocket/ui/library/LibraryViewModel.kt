package com.gamelaunch.frontend.pocket.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.pocket.data.SteamMetadataDao
import com.gamelaunch.frontend.pocket.emulation.RomLibraryMetadataReconciler
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
    val localAppIds: Set<String> = emptySet()
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val mediaRepository: MediaRepository,
    private val steamMetadataDao: SteamMetadataDao,
    private val romMetadataReconciler: RomLibraryMetadataReconciler
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                gameRepository.getAllGames(),
                mediaRepository.observeAllMedia(),
                steamMetadataDao.observeLocalAppIds()
            ) { games, mediaMap, localIds ->
                LibraryUiState(
                    games         = games,
                    mediaForGames = mediaMap,
                    isLoading     = false,
                    localAppIds   = localIds.toSet()
                )
            }.collectLatest { state ->
                _uiState.value = state
            }
        }

        // Repair scanner-generated ROM titles and promote existing/new IGDB metadata into the
        // eOr media repository that powers every library and home tile.
        viewModelScope.launch {
            gameRepository.getAllGames().collect { games ->
                romMetadataReconciler.reconcile(games)
            }
        }
    }

}
