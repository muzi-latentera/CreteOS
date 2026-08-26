package com.gamelaunch.frontend.pocket.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.component.platformDisplayName
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.input.GamepadL1
import com.gamelaunch.frontend.ui.input.GamepadR1
import com.gamelaunch.frontend.ui.input.GamepadStart
import com.gamelaunch.frontend.ui.lockedmode.LockedModeViewModel
import com.gamelaunch.frontend.ui.screen.home.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The currently selected navigation tab. */
enum class CreteOSTab { HOME, LIBRARY, SETTINGS }

/** Accent colour for focused UI elements. */
private val AccentBlue = Color(0xFF58A6FF)

/** Muted text colour. */
private val MutedText = Color(0xFF8B949E)

/**
 * CreteOS Home Screen - WinHanced/SteamOS-style visual redesign.
 * 
 * Layout (landscape 1920×1080):
 * - Top: Status bar with clock, WiFi, battery, power
 * - Middle-top: Blurred hero artwork from focused game
 * - Middle: Game info (title, platform, last played) + Play button
 * - Bottom: Recent games horizontal carousel
 * - Footer: Tab bar (Library, Settings) + controller hints
 *
 * @param onGameClick Callback when a game is selected to view details.
 * @param onSettingsClick Callback when settings is navigated to.
 * @param onLibraryClick Callback when library tab is selected.
 * @param viewModel The shared HomeViewModel for game data.
 * @param lockedModeViewModel The locked mode ViewModel.
 */
@Composable
fun CreteOSHomeScreen(
    onGameClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onLibraryClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    lockedModeViewModel: LockedModeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Current focused game index in carousel
    var focusedGameIndex by remember { mutableIntStateOf(0) }
    
    // Get recent games from the state
    val recentGames = state.recentlyPlayed.takeIf { it.isNotEmpty() } ?: state.games.take(20)
    val focusedGame = recentGames.getOrNull(focusedGameIndex)
    val focusedMedia = focusedGame?.let { state.mediaForGames[it.id] }
    
    // Extract dominant colour from focused game's artwork
    val artworkPath = focusedMedia?.effectiveBoxArt
    val accentColor = rememberDominantColor(artworkPath)
    
    // Focus management for controller input
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { androidx.compose.runtime.mutableStateOf(false) }
    
    // Request focus on resume
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            var tries = 0
            while (!isFocused && tries++ < 15) {
                runCatching { focusRequester.requestFocus() }
                delay(80)
            }
        }
    }
    
    // Keep focused index in bounds
    LaunchedEffect(recentGames.size) {
        if (recentGames.isNotEmpty()) {
            focusedGameIndex = focusedGameIndex.coerceIn(0, recentGames.size - 1)
        }
    }
    
    DynamicBackground(
        accentColor = accentColor,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.hasFocus }
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (recentGames.isNotEmpty()) {
                                focusedGameIndex = (focusedGameIndex - 1).coerceAtLeast(0)
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (recentGames.isNotEmpty()) {
                                focusedGameIndex = (focusedGameIndex + 1).coerceAtMost(recentGames.size - 1)
                            }
                            true
                        }
                        GamepadA, Key.Enter -> {
                            focusedGame?.let { onGameClick(it.id) }
                            true
                        }
                        GamepadL1 -> {
                            onLibraryClick()
                            true
                        }
                        GamepadR1, GamepadStart -> {
                            onSettingsClick()
                            true
                        }
                        else -> false
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Status bar
                CreteOSStatusBar()
                
                // Loading state
                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                } else {
                    // Hero section with blurred artwork
                    // Use wide hero image (screenshot_remote) for background, fall back to box art
                    val heroArtPath = focusedMedia?.effectiveBackground ?: focusedMedia?.effectiveBoxArt
                    HeroSection(
                        game = focusedGame,
                        artworkPath = heroArtPath,
                        onPlayClick = { focusedGame?.let { onGameClick(it.id) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f)
                    )
                    
                    // Recent games section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f)
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = "Recent Games",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                        
                        GameCarousel(
                            games = recentGames,
                            mediaForGames = state.mediaForGames,
                            focusedIndex = focusedGameIndex,
                            onGameClick = onGameClick,
                            onFocusChanged = { focusedGameIndex = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Bottom tab bar
                    BottomTabBar(
                        selectedTab = CreteOSTab.HOME,
                        onLibraryClick = onLibraryClick,
                        onSettingsClick = onSettingsClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Hero section showing blurred background artwork, game info, and play button.
 */
@Composable
private fun HeroSection(
    game: Game?,
    artworkPath: String?,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Blurred background artwork — use the hero/screenshot URL, fall back to box art
        val heroUrl = artworkPath
        if (heroUrl != null) {
            Crossfade(
                targetState = heroUrl,
                label = "heroArtwork"
            ) { url ->
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 24.dp)
                )
            }
        }

        // Dark scrim — heavier at top, lighter at bottom where text is
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.5f),
                        0.6f to Color.Black.copy(alpha = 0.4f),
                        1f to Color.Black.copy(alpha = 0.85f)
                    )
                )
        )

        // Game info overlay pinned to bottom
        if (game != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.title,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = platformDisplayName(game.platformId),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        val lastPlayed = game.lastPlayedMs?.let { formatLastPlayed(it) }
                        if (lastPlayed != null) {
                            Text(
                                text = "Last played: $lastPlayed",
                                color = MutedText,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PLAY", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

/**
 * Bottom tab bar with Library, Settings, and controller hints.
 */
@Composable
private fun BottomTabBar(
    selectedTab: CreteOSTab,
    onLibraryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Tab buttons (left side)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabButton(
                icon = Icons.Default.Folder,
                label = "Library",
                isSelected = selectedTab == CreteOSTab.LIBRARY,
                onClick = onLibraryClick
            )
            TabButton(
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = selectedTab == CreteOSTab.SETTINGS,
                onClick = onSettingsClick
            )
        }

        // Controller hints (right side) — only show once
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ControllerHint(button = "LB", label = "Library")
            ControllerHint(button = "RB", label = "Settings")
            ControllerHint(button = "A", label = "Play")
        }
    }
}

/**
 * Individual tab button in the bottom bar.
 */
@Composable
private fun TabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) Color.White else MutedText,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = if (isSelected) Color.White else MutedText,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Controller hint pill showing button + action.
 */
@Composable
private fun ControllerHint(
    button: String,
    label: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Button indicator
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(MutedText)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = button,
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            color = MutedText,
            fontSize = 11.sp
        )
    }
}

/**
 * Format last played timestamp to human-readable string.
 */
private fun formatLastPlayed(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestampMs
    val diffMinutes = diffMs / 60_000
    val diffHours = diffMs / 3_600_000
    val diffDays = diffMs / 86_400_000
    
    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "${diffMinutes}m ago"
        diffHours < 24 -> "${diffHours}h ago"
        diffDays < 7 -> "${diffDays}d ago"
        diffDays < 30 -> "${diffDays / 7}w ago"
        else -> "${diffDays / 30}mo ago"
    }
}
