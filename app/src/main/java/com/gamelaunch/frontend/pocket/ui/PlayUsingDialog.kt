package com.gamelaunch.frontend.pocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.pocket.domain.LaunchTarget

/**
 * "Play Using…" dialog shown when a game has multiple launch targets.
 * Uses eOr's existing Material3 styling — no visual redesign.
 */
@Composable
fun PlayUsingDialog(
    gameName: String,
    targets: List<LaunchTarget>,
    onLaunchTarget: (LaunchTarget) -> Unit,
    onSetPreferred: (LaunchTarget) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play using…") },
        text = {
            if (targets.isEmpty()) {
                Text("No additional launch methods configured for $gameName.")
            } else {
                LazyColumn {
                    items(targets) { target ->
                        LaunchTargetRow(
                            target = target,
                            onLaunch = { onLaunchTarget(target) },
                            onSetPreferred = { onSetPreferred(target) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun LaunchTargetRow(
    target: LaunchTarget,
    onLaunch: () -> Unit,
    onSetPreferred: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.provider.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (target.isPreferred) FontWeight.Bold else FontWeight.Normal
            )
            if (target.displayName.isNotBlank() && target.displayName != target.provider.displayName) {
                Text(
                    text = target.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!target.isAvailable) {
                Text(
                    text = "Not installed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        // Set as preferred
        IconButton(onClick = onSetPreferred) {
            Icon(
                imageVector = if (target.isPreferred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (target.isPreferred) "Preferred" else "Set as preferred",
                tint = if (target.isPreferred) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Launch button
        TextButton(
            onClick = onLaunch,
            enabled = target.isAvailable
        ) {
            Text("Play")
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
}
