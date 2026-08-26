package com.gamelaunch.frontend.pocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.pocket.ui.design.*
import com.gamelaunch.frontend.pocket.ui.library.LibraryViewModel
import com.gamelaunch.frontend.ui.screen.home.HomeViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.ui.screen.settings.SettingsCategory

// ══════════════════════════════════════════════════════════════════════════
// HOME TAB
// ══════════════════════════════════════════════════════════════════════════

/**
 * Home tab content — WinHanced hierarchy:
 * - Recent Games label near top
 * - Horizontal cover carousel
 * - (Navigation tab bar lives in the shell, not here)
 *
 * Hero artwork is the full background handled by CreteRootShell.
 * This content just has the game list.
 */
@Composable
fun HomeTabContent(
    homeViewModel: HomeViewModel,
    onGameClick: (Long) -> Unit,
    onTabChange: (ShellTab) -> Unit
) {
    val state      by homeViewModel.uiState.collectAsState()
    val recentGames = remember(state.recentlyPlayed, state.games) {
        state.recentlyPlayed.takeIf { it.isNotEmpty() } ?: state.games.take(20)
    }
    var focusedIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = CreteDS.spaceXXL)
    ) {
        // "Recent Games" label
        Text(
            text = "Recent Games",
            style = CreteDS.typeGameTitle,
            color = CreteDS.textPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                start = CreteDS.spaceXXL,
                bottom = CreteDS.spaceM
            )
        )

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().height(CreteDS.gameCardHeight + 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CreteDS.accent)
            }
        } else if (recentGames.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(CreteDS.spaceXXL), contentAlignment = Alignment.CenterStart) {
                Text(
                    "No games yet. Go to Settings → PC & Streaming to add GameNative games.",
                    style = CreteDS.typeMeta
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = CreteDS.spaceXXL),
                horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceM)
            ) {
                itemsIndexed(recentGames) { index, game ->
                    CreteGameCard(
                        artworkUrl = state.mediaForGames[game.id]?.effectiveBoxArt,
                        title      = game.title,
                        platformId = game.platformId,
                        focused    = index == focusedIndex,
                        onClick    = {
                            focusedIndex = index
                            onGameClick(game.id)
                        }
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// LIBRARY TAB
// ══════════════════════════════════════════════════════════════════════════

enum class LibraryFilter(val label: String) {
    ALL("All Games"),
    LOCAL("Local"),
    STREAMING("Streaming"),
    CLOUD("Cloud"),
    RETRO("Retro"),
    ANDROID("Android")
}

/**
 * Library tab — games as the dominant grid.
 * Games deduplicated — one tile per game regardless of how many providers.
 * Filters are compact and controller-friendly.
 */
@Composable
fun LibraryTabContent(
    libraryViewModel: LibraryViewModel,
    onGameClick: (Long) -> Unit
) {
    val state      by libraryViewModel.uiState.collectAsState()
    var activeFilter by remember { mutableStateOf(LibraryFilter.ALL) }

    val filteredGames = remember(state.games, activeFilter) {
        when (activeFilter) {
            LibraryFilter.ALL       -> state.games
            LibraryFilter.LOCAL     -> state.games.filter { it.platformId in setOf("steam", "gog", "epic", "amazon") }
            LibraryFilter.STREAMING -> state.games.filter { it.platformId in setOf("moonlight", "gfn") }
            LibraryFilter.CLOUD     -> state.games.filter { it.platformId == "gfn" }
            LibraryFilter.RETRO     -> state.games.filter { it.platformId !in setOf("steam","gog","epic","amazon","moonlight","gfn","android") }
            LibraryFilter.ANDROID   -> state.games.filter { it.platformId == "android" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = CreteDS.spaceXL)
    ) {
        // Filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = CreteDS.spaceXXL),
            horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceS),
            modifier = Modifier.padding(bottom = CreteDS.spaceL)
        ) {
            items(LibraryFilter.entries) { filter ->
                LibraryFilterChip(
                    label    = filter.label,
                    selected = filter == activeFilter,
                    onClick  = { activeFilter = filter }
                )
            }
        }

        if (filteredGames.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(CreteDS.spaceXXL),
                contentAlignment = Alignment.Center
            ) {
                Text("No games", style = CreteDS.typeMeta)
            }
        } else {
            // Two-row horizontal grid — scrolls right
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = CreteDS.spaceXXL),
                horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceM),
                verticalArrangement = Arrangement.spacedBy(CreteDS.spaceM),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredGames) { game ->
                    CreteGameCard(
                        artworkUrl = state.mediaForGames[game.id]?.effectiveBoxArt,
                        title      = game.title,
                        platformId = game.platformId,
                        focused    = false,
                        onClick    = { onGameClick(game.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(CreteDS.radiusPill))
            .background(
                if (selected) CreteDS.accent.copy(alpha = 0.18f)
                else CreteDS.bgCard.copy(alpha = 0.6f)
            )
            .run {
                if (selected) this else this
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = CreteDS.spaceL, vertical = CreteDS.spaceS)
    ) {
        Text(
            text = label,
            style = CreteDS.typeChip,
            color = if (selected) CreteDS.accent else CreteDS.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// SETTINGS TAB
// ══════════════════════════════════════════════════════════════════════════

/**
 * Settings tab — two-column layout same as CreteSettingsScreen but inline.
 * Uses glass surfaces throughout.
 */
@Composable
fun SettingsTabContent(
    onOpenSettings: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenDisplay: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }

    val categories = listOf(
        SettingsCategoryItem("PC & Streaming",  "GameNative, Moonlight, GeForce NOW",   Icons.Outlined.Stream),
        SettingsCategoryItem("Libraries",        "Game sources and folders",             Icons.Outlined.LibraryBooks),
        SettingsCategoryItem("Display",          "XREAL, external display, resolution",  Icons.Outlined.Monitor),
        SettingsCategoryItem("Appearance",       "Theme, layout, backgrounds",           Icons.Outlined.Palette),
        SettingsCategoryItem("General",          "Emulators, metadata, achievements",    Icons.Outlined.Settings)
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CreteDS.spaceXXL, vertical = CreteDS.spaceXL),
        horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceXL)
    ) {
        // Left: category list
        LazyColumn(
            modifier = Modifier.width(260.dp),
            verticalArrangement = Arrangement.spacedBy(CreteDS.spaceS)
        ) {
            itemsIndexed(categories) { index, item ->
                GlassSettingsCard(
                    item     = item,
                    selected = index == selectedIndex,
                    onClick  = { selectedIndex = index }
                )
            }
        }

        // Right: contextual panel — glass surface
        GlassPanel(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (selectedIndex) {
                0 -> PcStreamingSettingsPanel(onOpenProviders = onOpenProviders, onOpenDisplay = onOpenDisplay)
                1 -> LibrariesSettingsPanel(onOpenSettings = onOpenSettings)
                2 -> DisplaySettingsPanel(onOpenDisplay = onOpenDisplay)
                3 -> AppearanceSettingsPanel(onOpenSettings = onOpenSettings)
                4 -> GeneralSettingsPanel(onOpenSettings = onOpenSettings)
            }
        }
    }
}

// ── Glass surface ──────────────────────────────────────────────────────────

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CreteDS.radiusL))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        Color(0x55131922),
                        Color(0x880C1018)
                    )
                )
            )
            .run {
                // Thin border
                this
            }
            .padding(CreteDS.spaceXXL),
        content = content
    )
}

// ── Settings category card ─────────────────────────────────────────────────

@Composable
private fun GlassSettingsCard(
    item: SettingsCategoryItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CreteDS.radiusM))
            .background(
                if (selected) Color(0x444D9FFF)
                else Color(0x33131922)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = CreteDS.spaceL, vertical = CreteDS.spaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceM)
    ) {
        androidx.compose.material3.Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (selected) CreteDS.accent else CreteDS.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = CreteDS.typeNavTab,
                color = if (selected) CreteDS.textPrimary else CreteDS.textSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(text = item.subtitle, style = CreteDS.typeMeta, color = CreteDS.textDisabled)
        }
    }
}

// ── Settings right panels ──────────────────────────────────────────────────

@Composable
private fun ColumnScope.PcStreamingSettingsPanel(
    onOpenProviders: () -> Unit,
    onOpenDisplay: () -> Unit
) {
    SettingsPanelTitle("PC & Streaming")
    SettingsPanelBody("Add GameNative games, manage Moonlight, and configure display settings.")
    Spacer(Modifier.height(CreteDS.spaceXL))
    SettingsPanelAction("Provider Settings & Sync", Icons.Outlined.Sync, onOpenProviders)
    SettingsPanelAction("Display Diagnostics", Icons.Outlined.Monitor, onOpenDisplay)
}

@Composable
private fun ColumnScope.LibrariesSettingsPanel(onOpenSettings: () -> Unit) {
    SettingsPanelTitle("Libraries")
    SettingsPanelBody("Configure ROM folders, emulators, and metadata scraping.")
    Spacer(Modifier.height(CreteDS.spaceXL))
    SettingsPanelAction("Games & Library", Icons.Outlined.VideogameAsset, onOpenSettings)
    SettingsPanelAction("Media & Artwork", Icons.Outlined.Image, onOpenSettings)
}

@Composable
private fun ColumnScope.DisplaySettingsPanel(onOpenDisplay: () -> Unit) {
    SettingsPanelTitle("Display")
    SettingsPanelBody("Detect and configure external displays including XREAL glasses.")
    Spacer(Modifier.height(CreteDS.spaceXL))
    SettingsPanelAction("Display Diagnostics", Icons.Outlined.Monitor, onOpenDisplay)
}

@Composable
private fun ColumnScope.AppearanceSettingsPanel(onOpenSettings: () -> Unit) {
    SettingsPanelTitle("Appearance")
    SettingsPanelBody("Theme, home layout, and visual customisation.")
    Spacer(Modifier.height(CreteDS.spaceXL))
    SettingsPanelAction("Appearance & Theme", Icons.Outlined.Palette, onOpenSettings)
    SettingsPanelAction("Home Layout", Icons.Outlined.Home, onOpenSettings)
}

@Composable
private fun ColumnScope.GeneralSettingsPanel(onOpenSettings: () -> Unit) {
    SettingsPanelTitle("General")
    SettingsPanelBody("Emulator configuration, RetroAchievements, friends, locked mode.")
    Spacer(Modifier.height(CreteDS.spaceXL))
    SettingsPanelAction("Advanced Settings", Icons.Outlined.Settings, onOpenSettings)
}

@Composable
private fun ColumnScope.SettingsPanelTitle(text: String) {
    Text(
        text = text,
        color = CreteDS.textPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        modifier = Modifier.padding(bottom = CreteDS.spaceS)
    )
}

@Composable
private fun ColumnScope.SettingsPanelBody(text: String) {
    Text(text = text, style = CreteDS.typeMeta)
}

@Composable
private fun ColumnScope.SettingsPanelAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CreteDS.radiusS))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = CreteDS.spaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceM)
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CreteDS.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = CreteDS.typeNavTab,
            color = CreteDS.textSecondary,
            modifier = Modifier.weight(1f)
        )
        androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = CreteDS.textDisabled,
            modifier = Modifier.size(16.dp)
        )
    }
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(CreteDS.border))
}
