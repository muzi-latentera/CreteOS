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

// Single tab bar — maps directly to WinHanced's What's New | Library | Settings
enum class ShellTab(val label: String) {
    WHATS_NEW("What's New"),
    LIBRARY("Library"),
    SETTINGS("Settings")
}

// Kept for backward compatibility with any callers that haven't been updated yet
// (will be removed in a follow-up once all usages are migrated)
@Deprecated("Use ShellTab directly", replaceWith = ReplaceWith("ShellTab"))
typealias ShellTabLegacy = ShellTab

/**
 * CreteOS Root Shell — single persistent layout, WinHanced architecture.
 *
 * The layout is fixed and never changes:
 *
 * ┌──────────────────────────────────── [pill] ─┐
 * │  [hero artwork fills entire background]     │
 * │  [dark scrim]                               │
 * │                                             │
 * │  "Recent Games"                             │
 * │  [game cover carousel — scrolls right]      │
 * │                                             │
 * │  [What's New]  [Library]  [Settings]        │  ← single tab bar
 * │  ────                                       │  ← underline indicator
 * │                                             │
 * │  [large content tiles — fill remaining]     │  ← changes per tab
 * └─────────────────────────────────────────────┘
 *
 * What's New  → large landscape news/media cards
 * Library     → large source tiles (All Games, Local, Streaming…) → pushes library grid
 * Settings    → large settings tiles (PC & Streaming, Libraries…) → pushes settings screen
 */
@Composable
fun CreteRootShell(
    onGameClick: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenLibrary: () -> Unit = {},      // navigates to full library grid screen
    homeViewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel()
) {
    var activeTab by remember { mutableStateOf(ShellTab.WHATS_NEW) }

    val homeState by homeViewModel.uiState.collectAsState()

    val focusedGame  = homeState.recentlyPlayed.firstOrNull() ?: homeState.games.firstOrNull()
    val focusedMedia = focusedGame?.let { homeState.mediaForGames[it.id] }
    val heroUrl      = focusedMedia?.effectiveBackground ?: focusedMedia?.effectiveBoxArt
    val accentColor  = rememberDominantColor(focusedMedia?.effectiveBoxArt)

    DynamicBackground(
        accentColor = accentColor,
        modifier = Modifier
            .fillMaxSize()
            .background(CreteDS.bgBase)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    GamepadL1 -> {
                        val prev = (activeTab.ordinal - 1).coerceAtLeast(0)
                        activeTab = ShellTab.entries[prev]
                        true
                    }
                    GamepadR1 -> {
                        val next = (activeTab.ordinal + 1).coerceAtMost(ShellTab.entries.lastIndex)
                        activeTab = ShellTab.entries[next]
                        true
                    }
                    else -> false
                }
            }
    ) {
        // ── Background: hero artwork with gradient scrim ─────────────────
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

        // Scrim — always shown (darker when no artwork to keep navy feel)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.0f to CreteDS.bgBase.copy(alpha = if (heroUrl != null) 0.50f else 0.90f),
                        0.40f to CreteDS.bgBase.copy(alpha = if (heroUrl != null) 0.65f else 0.92f),
                        1.0f to CreteDS.bgBase.copy(alpha = 0.97f)
                    )
                )
        )

        // ── System pill ──────────────────────────────────────────────────
        CreteSystemPill(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 16.dp)
        )

        // ── Single unified content layout ────────────────────────────────
        CreteHomeLayout(
            activeTab       = activeTab,
            onTabSelected   = { activeTab = it },
            homeViewModel   = homeViewModel,
            libraryViewModel = libraryViewModel,
            onGameClick     = onGameClick,
            onOpenLibrary   = onOpenLibrary,
            onOpenSettings  = onOpenSettings,
            onOpenProviders = onOpenProviders,
            onOpenDisplay   = onOpenDisplay,
            modifier        = Modifier.fillMaxSize()
        )
    }
}
