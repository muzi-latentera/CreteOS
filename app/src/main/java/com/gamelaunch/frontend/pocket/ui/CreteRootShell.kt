package com.gamelaunch.frontend.pocket.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gamelaunch.frontend.pocket.ui.design.*
import com.gamelaunch.frontend.pocket.ui.home.rememberDominantColor
import com.gamelaunch.frontend.pocket.ui.home.DynamicBackground
import com.gamelaunch.frontend.pocket.ui.library.LibraryViewModel
import com.gamelaunch.frontend.ui.input.GamepadL1
import com.gamelaunch.frontend.ui.input.GamepadR1
import com.gamelaunch.frontend.ui.screen.home.HomeViewModel

enum class ShellTab(val label: String) {
    HOME("Home"),
    LIBRARY("Library"),
    SETTINGS("Settings")
}

/**
 * CreteOS Root Shell — single persistent layout.
 *
 * The system pill and tab navigation NEVER unmount.
 * Switching between Home / Library / Settings crossfades only the content area.
 * Game Detail, Provider Settings, Display Diagnostics etc. are still pushed routes.
 *
 * Layout:
 *
 * ┌─────────────────────────────────── [pill] ─┐
 * │   [hero artwork fills entire background]   │
 * │   [dark scrim + dynamic accent tint]       │
 * │                                            │
 * │  ┌──────────────────────────────────────┐  │
 * │  │  [tab content area — crossfades]     │  │
 * │  └──────────────────────────────────────┘  │
 * │                                            │
 * │  [LB] Home · Library · Settings [RB]       │
 * │  [controller hints]                        │
 * └────────────────────────────────────────────┘
 */
@Composable
fun CreteRootShell(
    onGameClick: (Long) -> Unit,
    onOpenSettings: () -> Unit,     // pushes to advanced settings route
    onOpenProviders: () -> Unit,
    onOpenDisplay: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel()
) {
    var activeTab by remember { mutableStateOf(ShellTab.HOME) }

    val homeState   by homeViewModel.uiState.collectAsState()

    // Derive focused game for background artwork — always from whatever's focused
    val focusedGame = homeState.recentlyPlayed.firstOrNull() ?: homeState.games.firstOrNull()
    val focusedMedia = focusedGame?.let { homeState.mediaForGames[it.id] }
    val heroUrl = focusedMedia?.effectiveBackground ?: focusedMedia?.effectiveBoxArt
    val accentColor = rememberDominantColor(focusedMedia?.effectiveBoxArt)

    DynamicBackground(
        accentColor = accentColor,
        modifier = Modifier
            .fillMaxSize()
            .background(CreteDS.bgBase)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    GamepadL1 -> {
                        activeTab = when (activeTab) {
                            ShellTab.HOME     -> ShellTab.HOME
                            ShellTab.LIBRARY  -> ShellTab.HOME
                            ShellTab.SETTINGS -> ShellTab.LIBRARY
                        }
                        true
                    }
                    GamepadR1 -> {
                        activeTab = when (activeTab) {
                            ShellTab.HOME     -> ShellTab.LIBRARY
                            ShellTab.LIBRARY  -> ShellTab.SETTINGS
                            ShellTab.SETTINGS -> ShellTab.SETTINGS
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        // ── Hero artwork fills the full background ──────────────────────────
        if (heroUrl != null) {
            Crossfade(
                targetState = heroUrl,
                animationSpec = tween(CreteDS.animColour),
                label = "shellHero"
            ) { url ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Dark scrim over artwork — lighter at top, darker at bottom
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.0f to CreteDS.bgBase.copy(alpha = 0.35f),
                        0.4f to CreteDS.bgBase.copy(alpha = 0.55f),
                        1.0f to CreteDS.bgBase.copy(alpha = 0.92f)
                    )
                )
        )

        // ── System pill — always mounted ────────────────────────────────────
        CreteSystemPill(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 16.dp)
        )

        // ── Content + nav ────────────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxSize()) {
            // Tab content crossfades
            Box(modifier = Modifier.weight(1f)) {
                Crossfade(
                    targetState = activeTab,
                    animationSpec = tween(CreteDS.animFast),
                    label = "tabContent"
                ) { tab ->
                    when (tab) {
                        ShellTab.HOME -> HomeTabContent(
                            homeViewModel = homeViewModel,
                            onGameClick   = onGameClick,
                            onTabChange   = { activeTab = it }
                        )
                        ShellTab.LIBRARY -> LibraryTabContent(
                            libraryViewModel = libraryViewModel,
                            onGameClick      = onGameClick
                        )
                        ShellTab.SETTINGS -> SettingsTabContent(
                            onOpenSettings  = onOpenSettings,
                            onOpenProviders = onOpenProviders,
                            onOpenDisplay   = onOpenDisplay
                        )
                    }
                }
            }

            // ── Persistent tab bar — always visible ────────────────────────
            CreteTopNavigation(
                tabs = ShellTab.entries.map { it.label },
                selectedIndex = activeTab.ordinal,
                onTabSelected = { index -> activeTab = ShellTab.entries[index] }
            )

            // Controller hints
            CreteBottomHints(
                hints = listOf(
                    "A" to "Select",
                    "B" to "Back",
                    "LB" to "Prev",
                    "RB" to "Next"
                )
            )
        }
    }
}
