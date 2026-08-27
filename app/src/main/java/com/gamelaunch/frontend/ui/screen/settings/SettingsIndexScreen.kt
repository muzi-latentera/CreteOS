package com.gamelaunch.frontend.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Settings landing screen: a scrollable, d-pad-navigable list of category rows. Tapping a row
 * drills into that category's own screen. This index is the app's findability mechanism as settings
 * grow — adding a feature is one new [SettingsCategory] entry plus its screen, never re-cramming an
 * existing tab. Route strings are mirrored by the matching objects in `Screen.kt`.
 */
enum class SettingsCategory(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    APPEARANCE(
        "settings_appearance", "Appearance",
        "Grid size, theme, colours, and background", Icons.Default.Palette
    ),
    HOME_LAYOUT(
        "settings_home_layout", "Home & Layout",
        "Home app, system order, hidden systems, dual screen", Icons.Default.Home
    ),
    GAMES(
        "settings_games", "Games & Library",
        "ROM folders, Android & Steam games, emulators", Icons.Default.VideogameAsset
    ),
    MEDIA(
        "settings_media", "Media & Artwork",
        "Media storage, ScreenScraper, artwork database", Icons.Default.PermMedia
    ),
    RETRO_ACHIEVEMENTS(
        "settings_retro_achievements", "RetroAchievements",
        "Log in and track achievements", Icons.Default.EmojiEvents
    ),
    SAVE_SYNC(
        "settings_save_sync", "Save Sync",
        "Sync saves across devices", Icons.Default.Sync
    ),
    FRIENDS(
        "settings_friends", "Friends",
        "Share activity and manage friends", Icons.Default.Group
    ),
    LOCKED_MODE(
        "settings_locked", "Locked Mode",
        "Restrict eOr to approved games and apps", Icons.Default.Lock
    ),
    PC_STREAMING(
        "provider_settings", "PC & Streaming",
        "GameNative, Moonlight, GeForce NOW — sync and manage", Icons.Default.VideogameAsset
    ),
}

@Composable
fun SettingsIndexScreen(
    onBack: (() -> Unit)?,
    onGoToLibrary: () -> Unit,
    onOpenCategory: (SettingsCategory) -> Unit,
    viewModel: SettingsViewModel,
) {
    val state by viewModel.uiState.collectAsState()

    // CreteOS-styled settings index — dark navy background, glass cards
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color(0xFF0E1E35),
                        androidx.compose.ui.graphics.Color(0xFF060E1C)
                    ),
                    radius = 1400f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(androidx.compose.ui.graphics.Color(0x28FFFFFF))
                                .border(0.5.dp, androidx.compose.ui.graphics.Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Back",
                                tint = androidx.compose.ui.graphics.Color(0xFF8899BB),
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer(rotationZ = 180f)
                            )
                        }
                    }
                    Text(
                        text = "Settings",
                        color = androidx.compose.ui.graphics.Color(0xFFEEF2FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }

                // First-launch finish button
                if (onBack == null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(androidx.compose.ui.graphics.Color(0xFF4D9FFF))
                            .clickable {
                                viewModel.saveCredentials()
                                viewModel.finishSetup()
                                onGoToLibrary()
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Go to Library",
                            color = androidx.compose.ui.graphics.Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Settings category rows — glass cards
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(SettingsCategory.entries) { category ->
                    CreteSettingsCategoryRow(
                        category = category,
                        onClick  = { onOpenCategory(category) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreteSettingsCategoryRow(
    category: SettingsCategory,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color(0x28FFFFFF))
            .border(0.5.dp, androidx.compose.ui.graphics.Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(androidx.compose.ui.graphics.Color(0x334D9FFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                category.icon,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color(0xFF4D9FFF),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                category.title,
                color = androidx.compose.ui.graphics.Color(0xFFEEF2FF),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                category.subtitle,
                color = androidx.compose.ui.graphics.Color(0xFF8899BB),
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color(0xFF8899BB),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SetupFinishAction(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(50))
            .background(gradientBrush)
            .dpadFocusable(shape = RoundedCornerShape(50), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            "Library",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
    }
}
