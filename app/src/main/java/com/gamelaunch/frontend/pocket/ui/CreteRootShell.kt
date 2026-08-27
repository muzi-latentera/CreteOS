package com.gamelaunch.frontend.pocket.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * ShellTab enum — v1 navigation structure.
 * HOME, LIBRARY, SOURCES, SETTINGS (no WHATS_NEW)
 */
enum class ShellTab(val label: String) {
    HOME("Home"),
    LIBRARY("Library"),
    SOURCES("Sources"),
    SETTINGS("Settings")
}

/**
 * CreteOS Root Shell — v1 fixed landscape layout.
 *
 * ┌─────────┬───────────────────────────────────────────────┐
 * │         │  [72px top header bar]                        │
 * │  92px   ├───────────────────────────────────────────────┤
 * │ sidebar │                                               │
 * │         │  [content fills remaining height]             │
 * │         │                                               │
 * │         ├───────────────────────────────────────────────┤
 * │         │  [64px bottom hints bar]                      │
 * └─────────┴───────────────────────────────────────────────┘
 *
 * Sidebar: logo, nav icons (Home/Library/Sources), Settings + Power at bottom
 * Header: CRETE OS branding, breadcrumb, perf pill, WiFi, battery, clock
 * Hints: controller buttons (A/B/X/LB/RB), version string
 */
@Composable
fun CreteRootShell(
    onGameClick: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCreteSettings: () -> Unit = {},
    onOpenProviders: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenLibrary: (LibraryFilter) -> Unit = {},
    homeViewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel()
) {
    var activeTab by remember { mutableStateOf(ShellTab.HOME) }

    val homeState by homeViewModel.uiState.collectAsState()
    val focusedGame  = homeState.recentlyPlayed.firstOrNull() ?: homeState.games.firstOrNull()
    val focusedMedia = focusedGame?.let { homeState.mediaForGames[it.id] }
    val heroUrl      = focusedMedia?.effectiveBackground ?: focusedMedia?.effectiveBoxArt
    val accentColor  = rememberDominantColor(focusedMedia?.effectiveBoxArt)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CreteDS.bgBase)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    GamepadL1 -> {
                        if (activeTab == ShellTab.LIBRARY) activeTab = ShellTab.HOME
                        true
                    }
                    GamepadR1 -> {
                        if (activeTab == ShellTab.HOME) activeTab = ShellTab.LIBRARY
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Background fills the whole screen
        if (activeTab == ShellTab.HOME) {
            DynamicBackground(
                accentColor = accentColor,
                modifier = Modifier.fillMaxSize()
            ) {
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
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(24.dp, BlurredEdgeTreatment.Unbounded)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to CreteDS.bgBase.copy(alpha = if (heroUrl != null) 0.50f else 0.90f),
                                0.40f to CreteDS.bgBase.copy(alpha = if (heroUrl != null) 0.65f else 0.92f),
                                1.0f to CreteDS.bgBase.copy(alpha = 0.97f)
                            )
                        )
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF0C0F13), Color(0xFF0A0D10))))
            )
        }

        // Content + bottom bar
        // Content + bottom bar, with system pill overlaid top-right
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Content area — fills all space above the bottom bar
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(
                    targetState = activeTab,
                    animationSpec = tween(CreteDS.animFast),
                    label = "tabContent"
                ) { tab ->
                    when (tab) {
                        ShellTab.HOME -> CreteHomeLayout(
                            activeTab        = tab,
                            onTabSelected    = { activeTab = it },
                            homeViewModel    = homeViewModel,
                            libraryViewModel = libraryViewModel,
                            onGameClick      = onGameClick,
                            onOpenLibrary    = onOpenLibrary,
                            onOpenCreteSettings = onOpenCreteSettings,
                            onOpenSettings   = onOpenSettings,
                            onOpenProviders  = onOpenProviders,
                            onOpenDisplay    = onOpenDisplay,
                            modifier         = Modifier.fillMaxSize()
                        )
                        ShellTab.LIBRARY -> LibraryTabContent(
                            libraryViewModel = libraryViewModel,
                            onGameClick      = onGameClick
                        )
                        ShellTab.SOURCES -> LibraryTabContent(
                            libraryViewModel = libraryViewModel,
                            onGameClick      = onGameClick
                        )
                        ShellTab.SETTINGS -> SettingsTabContent(
                            onOpenSettings   = onOpenSettings,
                            onOpenProviders  = onOpenProviders,
                            onOpenDisplay    = onOpenDisplay
                        )
                    }
                }
            }

            // ── Bottom nav bar ─────────────────────────────────────────────
            CreteBottomNavBar(
                activeTab = activeTab,
                onTabSelected = { activeTab = it },
                onSettingsClick = { activeTab = ShellTab.SETTINGS },
                onPowerClick = { /* TODO: power menu */ }
            )
        }   // end Column

        // System pill — floats top-right over all content
        CreteSystemPill(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 16.dp)
        )
    }   // end outer Box
    }   // end background Box
}

// ══════════════════════════════════════════════════════════════════════════
// BOTTOM NAV BAR — Home + Library on left, Settings + Power on right
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun CreteBottomNavBar(
    activeTab: ShellTab,
    onTabSelected: (ShellTab) -> Unit,
    onSettingsClick: () -> Unit,
    onPowerClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xFF0B0E11))
            .border(
                width = 1.dp,
                color = Color(0x14F2E8D5),
                shape = RoundedCornerShape(0.dp)
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Left: Home + Library ──────────────────────────────────────────
        BottomNavButton(
            icon = Icons.Filled.Home,
            label = "Home",
            selected = activeTab == ShellTab.HOME,
            onClick = { onTabSelected(ShellTab.HOME) }
        )
        Spacer(Modifier.width(8.dp))
        BottomNavButton(
            icon = Icons.Outlined.ViewColumn,
            label = "Library",
            selected = activeTab == ShellTab.LIBRARY,
            onClick = { onTabSelected(ShellTab.LIBRARY) }
        )

        Spacer(Modifier.weight(1f))

        // ── Right: Settings only — Power is in the system pill ────────────
        BottomNavButton(
            icon = Icons.Outlined.Settings,
            label = "Settings",
            selected = activeTab == ShellTab.SETTINGS,
            onClick = onSettingsClick
        )
    }
}

@Composable
private fun BottomNavButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    selectedColor: Color = CreteDS.accent,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) selectedColor else CreteDS.textSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (selected) selectedColor else CreteDS.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
