package com.gamelaunch.frontend.pocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.pocket.display.GamingDisplayInfo
import com.gamelaunch.frontend.pocket.display.GamingDisplayManager
import com.gamelaunch.frontend.pocket.ui.design.rememberCreteLayoutMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DisplayDiagnosticsViewModel @Inject constructor(
    private val gamingDisplayManager: GamingDisplayManager
) : ViewModel() {

    private val _displays = MutableStateFlow<List<GamingDisplayInfo>>(emptyList())
    val displays: StateFlow<List<GamingDisplayInfo>> = _displays

    private val _activeDisplay = MutableStateFlow(GamingDisplayInfo.internal())
    val activeDisplay: StateFlow<GamingDisplayInfo> = _activeDisplay

    init {
        gamingDisplayManager.start()
        viewModelScope.launch {
            gamingDisplayManager.activeDisplay.collectLatest { active ->
                _activeDisplay.value = active
                _displays.value = gamingDisplayManager.getAllDisplays()
            }
        }
    }

    fun refresh() {
        _displays.value = gamingDisplayManager.getAllDisplays()
    }
}

/**
 * Display diagnostics screen — shows all Android displays, their IDs, resolutions,
 * refresh rates and whether each is classified as internal or external.
 *
 * Purpose: verify GamingDisplayManager behaviour before Pocket FIT + XREAL hardware test.
 * Expected output with XREAL connected:
 *   Display 0 — Internal — 1920×1080 @ 144Hz
 *   USB profile — External — 1920×1200 @ 90Hz  ← XREAL 1S mirrored by Pocket FIT
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayDiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DisplayDiagnosticsViewModel = hiltViewModel()
) {
    val displays by viewModel.displays.collectAsState()
    val active   by viewModel.activeDisplay.collectAsState()
    val layout = rememberCreteLayoutMetrics()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Display Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::refresh) { Text("Refresh") }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(
                    horizontal = layout.horizontalPadding,
                    vertical = if (layout.compactHandheld) 12.dp else 20.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(if (layout.compactHandheld) 18.dp else 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                SectionLabel("Active Gaming Display")
                DisplayCard(info = active, isActive = true)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Connect XREAL glasses via USB-C, then tap Refresh. " +
                        "The Pocket FIT may expose it as an XREAL USB mirror profile at " +
                        "1920×1200 @ 90Hz instead of a numbered Android display.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                SectionLabel("All Detected Displays (${displays.size})")
                if (displays.isEmpty()) {
                    Text(
                        "No displays detected. Tap Refresh.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    displays.forEach { display ->
                        DisplayCard(info = display, isActive = display.displayId == active.displayId)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DisplayCard(info: GamingDisplayInfo, isActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (info.isExternal) Icons.Default.Monitor else Icons.Default.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (info.isExternal)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Display ID: ${info.displayId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${info.width} × ${info.height} @ ${info.refreshRate.toInt()} Hz",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (info.isExternal) "External" else "Internal",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (info.isExternal)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isActive) {
                Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
