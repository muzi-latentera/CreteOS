package com.gamelaunch.frontend.pocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.pocket.providers.ProviderId

/**
 * PC & Streaming provider settings screen.
 *
 * Contains:
 * - GameNative Frontend Sync setup instructions
 * - Sync/Rescan button that calls real ProviderSyncCoordinator
 * - Provider status list (installed/not installed, game count)
 * - Active display info
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsScreen(
    onBack: () -> Unit,
    onDisplayDiagnostics: () -> Unit = {},
    viewModel: ProviderSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

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

            // ── GameNative Setup Guide ──────────────────────────────────────────
            item {
                SectionHeader("GameNative Setup")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "To sync your GameNative library automatically:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        SetupStep(1, "In GameNative: Settings → Interface → Frontend Sync")
                        SetupStep(2, "Pick an export folder (e.g. /sdcard/ROMs)")
                        SetupStep(3, "In CreteOS: Settings → Games → Steam Library Folder → same folder")
                        SetupStep(4, "Tap Sync below — installed games appear automatically")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Note: GameNative 1.2.0 has no API to query its installed games directly. " +
                            "Frontend Sync (marker files) is the only production import path.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Sync button ─────────────────────────────────────────────────────
            item {
                Button(
                    onClick = { viewModel.rescanAll() },
                    enabled = !state.isScanning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
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
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Provider status ─────────────────────────────────────────────────
            item { SectionHeader("Providers (${state.providerStatuses.size})") }

            items(state.providerStatuses) { status ->
                ProviderStatusRow(
                    status = status,
                    onRescan = { viewModel.rescan(status.providerId) }
                )
            }

            // ── Active display ──────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Active Gaming Display")
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
private fun SetupStep(number: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "$number. ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(text, style = MaterialTheme.typography.bodySmall)
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
            imageVector = when {
                status.isInstalled  -> Icons.Default.CheckCircle
                else                -> Icons.Default.Error
            },
            contentDescription = null,
            tint = when {
                status.isInstalled  -> MaterialTheme.colorScheme.primary
                else                -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status.providerId.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = if (status.isInstalled) "${status.gameCount} games linked"
                       else "Not installed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (status.isInstalled) {
            IconButton(onClick = onRescan) {
                Icon(Icons.Default.Refresh, contentDescription = "Sync ${status.providerId.displayName}")
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
