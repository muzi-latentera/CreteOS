package com.gamelaunch.frontend.pocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.pocket.providers.ProviderId

/**
 * PC & Streaming provider settings screen.
 *
 * GameNative discovery status (verified 2026-08-26, v1.2.0):
 * - No public API to list installed games
 * - No Android shortcuts for installed games
 * - No shared storage export
 * - Direct launch works: LAUNCH_GAME intent with app_id is confirmed working
 *
 * The correct workflow for adding GameNative games is manual AppID entry.
 * Once the GameNative team adds a read-only installed-games API, Sync will
 * use it automatically. Until then, Add Game is the production path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsScreen(
    onBack: () -> Unit,
    onDisplayDiagnostics: () -> Unit = {},
    viewModel: ProviderSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    if (state.showAddGameDialog) {
        AddGameNativeGameDialog(
            onAdd   = { appId, title -> viewModel.addGameNativeGame(appId, title) },
            onDismiss = viewModel::dismissAddGame
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PC & Streaming") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {

            // ── GameNative ──────────────────────────────────────────────────────
            item {
                SectionHeader("GameNative")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "GameNative 1.2.0 does not expose an API to list installed games. " +
                            "Add games manually by Steam AppID — artwork and title are fetched automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = viewModel::showAddGame,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add GameNative Game")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Sync (for providers that do support discovery) ──────────────────
            item {
                SectionHeader("Sync")
                OutlinedButton(
                    onClick = { viewModel.rescanAll() },
                    enabled = !state.isScanning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Syncing…")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sync all providers")
                    }
                }
                state.lastSyncResult?.let { result ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Provider status ─────────────────────────────────────────────────
            item { SectionHeader("Providers") }

            items(state.providerStatuses) { status ->
                ProviderStatusRow(
                    status   = status,
                    onRescan = { viewModel.rescan(status.providerId) }
                )
            }

            // ── Display ─────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Active Display")
                state.activeDisplay?.let { display ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (display.isExternal) Icons.Default.Monitor
                                          else Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = if (display.isExternal) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "${display.width}×${display.height} @ ${display.refreshRate.toInt()} Hz",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${if (display.isExternal) "External" else "Internal"} — ${display.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                TextButton(onClick = onDisplayDiagnostics) {
                    Text("Display Diagnostics →")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ProviderStatusRow(
    status: ProviderStatus,
    onRescan: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (status.isInstalled) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (status.isInstalled) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(status.providerId.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (status.isInstalled) "${status.gameCount} games linked" else "Not installed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (status.isInstalled) {
            IconButton(onClick = onRescan) {
                Icon(Icons.Default.Refresh, contentDescription = "Sync")
            }
        }
    }
    HorizontalDivider()
}

data class ProviderStatus(
    val providerId: ProviderId,
    val isInstalled: Boolean,
    val gameCount: Int
)

data class ActiveDisplayInfo(
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val isExternal: Boolean,
    val name: String
)
