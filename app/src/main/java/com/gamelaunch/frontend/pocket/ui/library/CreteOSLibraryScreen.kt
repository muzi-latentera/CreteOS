package com.gamelaunch.frontend.pocket.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.pocket.ui.home.CreteOSStatusBar
import com.gamelaunch.frontend.pocket.ui.home.CreteOSTab
import com.gamelaunch.frontend.pocket.ui.home.DefaultAccentColor
import com.gamelaunch.frontend.pocket.ui.home.DynamicBackground
import com.gamelaunch.frontend.pocket.ui.home.rememberDominantColor
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.input.GamepadL1
import com.gamelaunch.frontend.ui.input.GamepadR1
import com.gamelaunch.frontend.pocket.ui.home.GameCoverCard
import com.gamelaunch.frontend.ui.screen.home.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Platform filter options for the library. */
enum class LibraryFilter(val label: String, val platformIds: List<String>?) {
    ALL("All", null),
    GAMENATIVE("GameNative", listOf("steam", "gamenative")),
    WINLATOR("Winlator", listOf("winlator")),
    EMULATORS("Emulators", listOf("nes", "snes", "n64", "gb", "gbc", "gba", "nds", "3ds", "ps1", "ps2", "psp", "genesis", "dc", "saturn", "pce")),
    GEFORCE_NOW("GeForce NOW", listOf("geforce", "nvidia")),
    MOONLIGHT("Moonlight", listOf("moonlight")),
    ANDROID("Android", listOf("android"))
}

/** Accent colour for UI elements. */
private val AccentBlue = Color(0xFF58A6FF)
private val MutedText = Color(0xFF8B949E)

/**
 * CreteOS Library Screen - Full game grid with platform filter chips.
 *
 * Layout:
 * - Top: Status bar
 * - Filter chips: All / GameNative / Winlator / Emulators / GeForce NOW / Moonlight / Android
 * - Main: 2-row horizontal grid of game covers
 * - Bottom: Tab bar with controller hints
 */
@Composable
fun CreteOSLibraryScreen(
    onGameClick: (Long) -> Unit,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Filter state
    var selectedFilter by remember { mutableStateOf(LibraryFilter.ALL) }
    var filterFocusIndex by remember { mutableIntStateOf(0) }
    
    // Grid focus state
    var gridFocusIndex by remember { mutableIntStateOf(0) }
    var isFilterFocused by remember { mutableStateOf(true) }
    
    // Filter games based on selected filter
    val filteredGames = remember(state.games, selectedFilter) {
        val platformIds = selectedFilter.platformIds
        if (platformIds == null) {
            state.games
        } else {
            state.games.filter { game ->
                platformIds.any { platformId ->
                    game.platformId.contains(platformId, ignoreCase = true)
                }
            }
        }
    }
    
    // Get accent color from focused game
    val focusedGame = filteredGames.getOrNull(gridFocusIndex)
    val focusedMedia = focusedGame?.let { state.mediaForGames[it.id] }
    val accentColor = rememberDominantColor(focusedMedia?.effectiveBoxArt)
    
    // Focus management
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            var tries = 0
            while (!isFocused && tries++ < 15) {
                runCatching { focusRequester.requestFocus() }
                delay(80)
            }
        }
    }
    
    // Keep focus in bounds
    LaunchedEffect(filteredGames.size) {
        if (filteredGames.isNotEmpty()) {
            gridFocusIndex = gridFocusIndex.coerceIn(0, filteredGames.size - 1)
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
                        Key.DirectionUp -> {
                            if (!isFilterFocused && gridFocusIndex >= 7) {
                                // Move up a row in grid (7 columns assumption)
                                gridFocusIndex = (gridFocusIndex - 7).coerceAtLeast(0)
                            } else if (!isFilterFocused) {
                                // Move to filter chips
                                isFilterFocused = true
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            if (isFilterFocused) {
                                // Move to grid
                                isFilterFocused = false
                            } else if (filteredGames.isNotEmpty()) {
                                // Move down a row in grid
                                gridFocusIndex = (gridFocusIndex + 7).coerceAtMost(filteredGames.size - 1)
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            if (isFilterFocused) {
                                filterFocusIndex = (filterFocusIndex - 1).coerceAtLeast(0)
                            } else if (filteredGames.isNotEmpty()) {
                                gridFocusIndex = (gridFocusIndex - 1).coerceAtLeast(0)
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (isFilterFocused) {
                                filterFocusIndex = (filterFocusIndex + 1).coerceAtMost(LibraryFilter.entries.size - 1)
                            } else if (filteredGames.isNotEmpty()) {
                                gridFocusIndex = (gridFocusIndex + 1).coerceAtMost(filteredGames.size - 1)
                            }
                            true
                        }
                        GamepadA, Key.Enter -> {
                            if (isFilterFocused) {
                                selectedFilter = LibraryFilter.entries[filterFocusIndex]
                                gridFocusIndex = 0
                                isFilterFocused = false
                            } else {
                                filteredGames.getOrNull(gridFocusIndex)?.let { onGameClick(it.id) }
                            }
                            true
                        }
                        GamepadB -> {
                            if (!isFilterFocused) {
                                isFilterFocused = true
                            } else {
                                onBack()
                            }
                            true
                        }
                        GamepadL1 -> {
                            onHomeClick()
                            true
                        }
                        GamepadR1 -> {
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
                
                // Back button and title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Library",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${filteredGames.size} games",
                        color = MutedText,
                        fontSize = 14.sp
                    )
                }
                
                // Filter chips
                FilterChipsRow(
                    filters = LibraryFilter.entries.toList(),
                    selectedFilter = selectedFilter,
                    focusedIndex = filterFocusIndex,
                    isFocused = isFilterFocused,
                    onFilterSelected = { filter ->
                        selectedFilter = filter
                        gridFocusIndex = 0
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
                
                // Game grid
                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                } else if (filteredGames.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MutedText,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No games found",
                                color = MutedText,
                                fontSize = 16.sp
                            )
                        }
                    }
                } else {
                    LibraryGameGrid(
                        games = filteredGames,
                        mediaForGames = state.mediaForGames,
                        focusedIndex = gridFocusIndex,
                        isFocused = !isFilterFocused,
                        onGameClick = onGameClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
                
                // Bottom tab bar
                LibraryBottomBar(
                    onHomeClick = onHomeClick,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Horizontal scrolling filter chips.
 */
@Composable
private fun FilterChipsRow(
    filters: List<LibraryFilter>,
    selectedFilter: LibraryFilter,
    focusedIndex: Int,
    isFocused: Boolean,
    onFilterSelected: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    LaunchedEffect(focusedIndex) {
        // Scroll to keep focused chip visible
        val chipWidth = 120 // Approximate chip width
        val targetScroll = (focusedIndex * chipWidth - 200).coerceAtLeast(0)
        scrollState.animateScrollTo(targetScroll)
    }
    
    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        filters.forEachIndexed { index, filter ->
            val isSelected = filter == selectedFilter
            val showFocus = isFocused && index == focusedIndex
            
            val backgroundColor by animateColorAsState(
                targetValue = when {
                    isSelected -> AccentBlue
                    showFocus -> Color.White.copy(alpha = 0.2f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                animationSpec = tween(200),
                label = "chipBg"
            )
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .clickable { onFilterSelected(filter) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = filter.label,
                    color = if (isSelected) Color.White else MutedText,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * 2-row horizontal grid of game covers.
 */
@Composable
private fun LibraryGameGrid(
    games: List<Game>,
    mediaForGames: Map<Long, GameMedia>,
    focusedIndex: Int,
    isFocused: Boolean,
    onGameClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    
    // Scroll to keep focused item visible
    LaunchedEffect(focusedIndex) {
        if (games.isNotEmpty() && focusedIndex in games.indices) {
            gridState.animateScrollToItem(focusedIndex)
        }
    }
    
    LazyHorizontalGrid(
        rows = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
            val media = mediaForGames[game.id]
            val isItemFocused = isFocused && index == focusedIndex
            
            LibraryGameCard(
                game = game,
                media = media,
                isFocused = isItemFocused,
                onClick = { onGameClick(game.id) }
            )
        }
    }
}

/**
 * Individual game card in the library grid.
 */
@Composable
private fun LibraryGameCard(
    game: Game,
    media: GameMedia?,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    GameCoverCard(
        game = game,
        media = media,
        isFocused = isFocused,
        onClick = onClick,
        modifier = Modifier
            .width(120.dp)
            .height(180.dp)
    )
}

/**
 * Bottom tab bar for the library screen.
 */
@Composable
private fun LibraryBottomBar(
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BottomTabItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = false,
                onClick = onHomeClick
            )
            BottomTabItem(
                icon = Icons.Default.Folder,
                label = "Library",
                isSelected = true,
                onClick = {}
            )
            BottomTabItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = false,
                onClick = onSettingsClick
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ControllerHintPill(button = "LB", label = "Home")
            ControllerHintPill(button = "RB", label = "Settings")
        }
    }
}

@Composable
private fun BottomTabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
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

@Composable
private fun ControllerHintPill(
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
