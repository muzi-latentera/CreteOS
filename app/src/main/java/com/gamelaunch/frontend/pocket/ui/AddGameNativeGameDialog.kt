package com.gamelaunch.frontend.pocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Dialog for manually adding a GameNative game to CreteOS by Steam AppID.
 *
 * Why manual: GameNative 1.2.0 has no public API to list installed games.
 * No shortcuts, no shared storage export, no content provider.
 * Direct launch works perfectly — we just need the AppID.
 *
 * Artwork and title are resolved automatically from Steam CDN once the AppID is confirmed.
 */
@Composable
fun AddGameNativeGameDialog(
    onAdd: (appId: Int, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    var appIdText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add GameNative Game") },
        text = {
            Column {
                Text(
                    "Enter the Steam AppID for a game installed in GameNative. " +
                    "Artwork and title will be fetched automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = appIdText,
                    onValueChange = { appIdText = it.filter { c -> c.isDigit() }; error = null },
                    label = { Text("Steam AppID") },
                    placeholder = { Text("e.g. 367520 for Hollow Knight") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Title (optional — auto-filled from Steam CDN)") },
                    placeholder = { Text("Hollow Knight") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Find AppIDs at store.steampowered.com — the number in the URL.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val appId = appIdText.toIntOrNull()
                when {
                    appIdText.isBlank() -> error = "AppID required"
                    appId == null       -> error = "Must be a number"
                    appId <= 0          -> error = "Must be a positive number"
                    else -> {
                        val title = titleText.ifBlank { "Game $appId" }
                        onAdd(appId, title)
                    }
                }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
