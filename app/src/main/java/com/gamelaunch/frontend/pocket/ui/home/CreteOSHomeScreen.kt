package com.gamelaunch.frontend.pocket.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.pocket.ui.design.*
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadL1
import com.gamelaunch.frontend.ui.input.GamepadR1
import com.gamelaunch.frontend.ui.screen.home.HomeViewModel

enum class HomeTab(val label: String) {
    HOME("Home"),
    LIBRARY("Library"),
    SETTINGS("Settings")
}

/**
 * CreteOS Home Screen v2 — WinHanced-inspired layout.
 *
 * - No app wordmark
 * - System pill top-right
 * - Hero artwork fills top ~55%
 * - Centered nav tabs with LB/RB hints
 * - Large game carousel at bottom
 * - Subtle controller hints strip
 */
@Composable
fun CreteOSHomeScreen(
    onGameClick: (Long) -> Unit,
    onLibraryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state        by viewModel.uiState.collectAsState()
    val listState    = rememberLazyListState()

    val recentGames = remember(state.recentlyPlayed, state.games) {
        state.recentlyPlayed.takeIf { it.isNotEmpty() } ?: state.games.take(20)
    }

    var focusedIndex by remember { mutableIntStateOf(0) }
    val focusedGame  = recentGames.getOrNull(focusedIndex)
    val focusedMedia = focusedGame?.let { state.mediaForGames[it.id] }
    val heroUrl      = focusedMedia?.effectiveBackground ?: focusedMedia?.effectiveBoxArt
    val accentColor  = rememberDominantColor(focusedMedia?.effectiveBoxArt)

    LaunchedEffect(focusedIndex) {
        if (recentGames.isNotEmpty()) {
            listState.animateScrollToItem(
                (focusedIndex - 1).coerceAtLeast(0)
            )
        }
    }

    DynamicBackground(
        accentColor = accentColor,
        modifier = Modifier
            .fillMaxSize()
            .background(CreteDS.bgBase)
    ) {
        // Hero artwork
        if (heroUrl != null) {
            Crossfade(
                targetState = heroUrl,
                animationSpec = tween(CreteDS.animColour),
                label = "hero"
            ) { url ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.62f)
                )
            }
        }

        // Bottom gradient — hero into dark content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.5f to CreteDS.bgBase.copy(alpha = 0.6f),
                        1.0f to CreteDS.bgBase
                    )
                )
        )

        // System pill
        CreteSystemPill(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 16.dp)
        )

        // Main content — nav + carousel + hints — anchored to bottom
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft  -> { if (recentGames.isNotEmpty()) focusedIndex = (focusedIndex - 1).coerceAtLeast(0); true }
                        Key.DirectionRight -> { if (recentGames.isNotEmpty()) focusedIndex = (focusedIndex + 1).coerceAtMost(recentGames.lastIndex); true }
                        GamepadA, Key.Enter -> { focusedGame?.let { onGameClick(it.id) }; true }
                        GamepadL1           -> { onLibraryClick(); true }
                        GamepadR1           -> { onSettingsClick(); true }
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.Bottom
        ) {
            CreteTopNavigation(
                tabs = HomeTab.entries.map { it.label },
                selectedIndex = 0,
                onTabSelected = { index ->
                    when (HomeTab.entries[index]) {
                        HomeTab.LIBRARY  -> onLibraryClick()
                        HomeTab.SETTINGS -> onSettingsClick()
                        HomeTab.HOME     -> {}
                    }
                }
            )

            Spacer(Modifier.height(CreteDS.spaceL))

            if (recentGames.isNotEmpty()) {
                HomeCarousel(
                    games        = recentGames,
                    mediaMap     = state.mediaForGames,
                    focusedIndex = focusedIndex,
                    listState    = listState,
                    onFocused    = { focusedIndex = it },
                    onSelected   = onGameClick
                )
            }

            Spacer(Modifier.height(CreteDS.spaceS))

            CreteBottomHints(
                hints = listOf(
                    "A" to "Play",
                    "B" to "Back",
                    "LB" to "Library",
                    "RB" to "Settings"
                )
            )
        }
    }
}

@Composable
private fun HomeCarousel(
    games: List<Game>,
    mediaMap: Map<Long, GameMedia>,
    focusedIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onFocused: (Int) -> Unit,
    onSelected: (Long) -> Unit
) {
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = CreteDS.spaceXXL),
        horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceM),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(games) { index, game ->
            val url = mediaMap[game.id]?.effectiveBoxArt
            CreteGameCard(
                artworkUrl = url,
                title      = game.title,
                platformId = game.platformId,
                focused    = index == focusedIndex,
                onClick    = { onFocused(index); onSelected(game.id) }
            )
        }
    }
}
