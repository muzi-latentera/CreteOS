package com.gamelaunch.frontend.pocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.pocket.providers.ProviderId

/**
 * PC & Streaming providers settings screen.
 * Uses eOr's existing Material3 / Compose conventions — no visual redesign.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsScreen(
    onBack: () -> Unit,
    viewModel: ProviderSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PC & Streaming") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Refresh, contentDescription = "Back") // placeholder
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
            // Display diagnostics section
            item {
                SectionHeader("Active Display")
                state.activeDisplay?.let { display ->
                    Text(
                        text = buildString {
                            append(if (display.isExternal) "External" else "Internal")
                            append(": ${display.width}×${display.height}")
                            display.refreshRate?.let { append(" @ ${it.toInt()}Hz") }
                            append(" — ${display.name}")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } ?: Text(
                    text = "Display info unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item { SectionHeader("Providers") }

            items(state.providerStatuses) { status ->
                ProviderStatusRow(
                    status = status,
                    onRescan = { viewModel.rescan(status.providerId) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { viewModel.rescanAll() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rescan all providers")
                }
                Spacer(modifier = Modifier.height(24.dp))
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
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
    HorizontalDivider()
}

@Composable
private fun ProviderStatusRow(
    status: ProviderStatus,
    onRescan: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (status.isInstalled) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (status.isInstalled) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
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
                Icon(Icons.Default.Refresh, contentDescription = "Rescan")
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
    val refreshRate: Float?,
    val isExternal: Boolean,
    val name: String
)
