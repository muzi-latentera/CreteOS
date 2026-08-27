package com.gamelaunch.frontend.pocket.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.pocket.data.IgdbMetadataSync
import com.gamelaunch.frontend.pocket.data.SteamMetadataDao
import com.gamelaunch.frontend.pocket.emulation.EmulationSeeder
import com.gamelaunch.frontend.pocket.emulation.EmulatorSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmulationSettingsUiState(
    val romRootPath: String = "",
    val isScanning: Boolean = false,
    val lastScanResult: String? = null,
    val error: String? = null
)

/**
 * ViewModel for emulation settings — handles ROM scanning and IGDB metadata sync.
 */
@HiltViewModel
class EmulationSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val emulationSeeder: EmulationSeeder,
    private val igdbSync: IgdbMetadataSync,
    private val steamMetadataDao: SteamMetadataDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmulationSettingsUiState())
    val uiState: StateFlow<EmulationSettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            val path = settingsRepository.romRootPath.first()
            _uiState.update { it.copy(romRootPath = path) }
        }
    }

    /**
     * Scan ROMs from the given SAF tree URI and seed them into the database.
     * Then triggers IGDB metadata sync for each new game.
     */
    fun scanRoms(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, lastScanResult = null, error = null) }

            try {
                val result = emulationSeeder.scanAndSeed(treeUri)

                if (result.errors.isNotEmpty()) {
                    _uiState.update { 
                        it.copy(
                            isScanning = false,
                            error = result.errors.first()
                        )
                    }
                    return@launch
                }

                val message = when {
                    result.gamesInserted > 0 && result.gamesSkipped > 0 ->
                        "Added ${result.gamesInserted} games (${result.gamesSkipped} already existed)"
                    result.gamesInserted > 0 ->
                        "Added ${result.gamesInserted} games"
                    result.gamesSkipped > 0 ->
                        "${result.gamesSkipped} games already in library"
                    else ->
                        "No ROMs found"
                }

                _uiState.update { it.copy(isScanning = false, lastScanResult = message) }

                // Trigger IGDB sync for newly added games in background
                if (result.gamesInserted > 0) {
                    syncIgdbMetadataForEmulatedGames()
                }

            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        error = "Scan failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Set the ROM root path and persist it.
     */
    fun setRomRootPath(path: String) {
        viewModelScope.launch {
            settingsRepository.setRomRootPath(path)
            _uiState.update { it.copy(romRootPath = path) }
        }
    }

    /**
     * Sync IGDB metadata for all emulated games that don't have cover art yet.
     */
    private suspend fun syncIgdbMetadataForEmulatedGames() {
        try {
            // Get all steam_metadata entries that look like emulated games (romPath starts with "emu:")
            val allMetadata = steamMetadataDao.getAll()
            val emulatedGames = allMetadata.filter { it.steamAppId.startsWith("emu:") }

            for (meta in emulatedGames) {
                // Skip if we already have cover art
                if (meta.igdbCoverUrl != null) continue

                // Parse romPath to get system and title
                val parts = meta.steamAppId.split(":")
                if (parts.size < 3) continue

                val systemId = parts[1]
                val system = EmulatorSystem.fromId(systemId) ?: continue

                // Convert key back to title (replace underscores with spaces, capitalize)
                val titleKey = parts[2]
                val title = titleKey
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }

                // Sync from IGDB
                igdbSync.syncEmulatedGame(meta.steamAppId, title, system)
            }
        } catch (e: Exception) {
            // Log but don't fail — this is background enrichment
            android.util.Log.w("EmulationSettingsVM", "IGDB sync failed: ${e.message}")
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearResult() {
        _uiState.update { it.copy(lastScanResult = null) }
    }
}
