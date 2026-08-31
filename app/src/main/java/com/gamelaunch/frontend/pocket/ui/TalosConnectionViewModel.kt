package com.gamelaunch.frontend.pocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.pocket.data.TalosGamingClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TalosConnectionUiState(
    val url: String = "",
    val connected: Boolean = false,
    val busy: Boolean = false,
    val message: String = "",
)

@HiltViewModel
class TalosConnectionViewModel @Inject constructor(
    private val client: TalosGamingClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TalosConnectionUiState())
    val uiState: StateFlow<TalosConnectionUiState> = _uiState

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                url = client.configuredUrl(),
                connected = client.isConnected(),
            )
        }
    }

    fun pair(url: String, code: String) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, message = "Connecting…")
            client.pair(url, code).fold(
                onSuccess = {
                    val snapshot = client.refresh(force = true)
                    _uiState.value = _uiState.value.copy(
                        url = it.vpsUrl,
                        connected = true,
                        busy = false,
                        message = snapshot.fold(
                            onSuccess = { data -> "Connected · ${data.games.size} account games synced" },
                            onFailure = { error -> "Connected · sync failed: ${error.message}" },
                        ),
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(busy = false, message = it.message ?: "Connection failed")
                },
            )
        }
    }

    fun sync() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, message = "Refreshing Talos data…")
            client.refresh(force = true).fold(
                onSuccess = { data ->
                    val accounts = data.profiles.joinToString { "${it.platform}: ${it.displayName}" }
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        connected = true,
                        message = "${data.games.size} games · $accounts",
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(busy = false, message = it.message ?: "Sync failed")
                },
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            client.disconnect()
            _uiState.value = TalosConnectionUiState(message = "Talos disconnected")
        }
    }
}
