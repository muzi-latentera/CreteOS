package com.gamelaunch.frontend.pocket.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.pocket.ui.design.*
import com.gamelaunch.frontend.pocket.ui.library.LibraryViewModel
import com.gamelaunch.frontend.ui.screen.home.HomeViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.ui.screen.settings.SettingsCategory

// ══════════════════════════════════════════════════════════════════════════
// CRETE HOME LAYOUT — unified WinHanced-style shell content
// ══════════════════════════════════════════════════════════════════════════

/**
 * The entire home-screen content area.
 *
 * Layout (fixed, always this structure):
 *
 *   "Recent Games"
 *   [game cover carousel — scrolls right]
 *
 *   [What's New]  [Library]  [Settings]   ← tab bar
 *   ─────                                 ← underline indicator
 *
 *   [large content tiles]                 ← fill remaining height, change per tab
 *
 * Tabs:
 *   What's New  → large landscape news/media cards
 *   Library     → large source tiles → tap to open full library grid
 *   Settings    → large settings tiles → tap to open settings screen
 */
@Composable
fun CreteHomeLayout(
    activeTab: ShellTab,
    onTabSelected: (ShellTab) -> Unit,
    homeViewModel: HomeViewModel,
    libraryViewModel: LibraryViewModel,
    onGameClick: (Long) -> Unit,
    onOpenLibrary: (LibraryFilter) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenDisplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state       by homeViewModel.uiState.collectAsState()
    val recentGames = remember(state.recentlyPlayed, state.games) {
        state.recentlyPlayed.takeIf { it.isNotEmpty() } ?: state.games.take(20)
    }
    var focusedIndex by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.padding(top = CreteDS.space3XL)) {

        // ── Recent Games label ─────────────────────────────────────────────
        Text(
            text = "Recent Games",
            style = CreteDS.typeGameTitle,
            color = CreteDS.textPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = CreteDS.spaceXXL, bottom = CreteDS.spaceM)
        )

        // ── Game cover carousel ────────────────────────────────────────────
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().height(CreteDS.gameCardHeight + 32.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = CreteDS.accent) }
        } else if (recentGames.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(CreteDS.spaceXXL),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "No games yet. Go to Settings → PC & Streaming to add games.",
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

        Spacer(Modifier.height(CreteDS.spaceXXL))

        // ── Tab bar ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            ShellTab.entries.forEach { tab ->
                val selected = tab == activeTab
                Column(
                    modifier = Modifier
                        .padding(horizontal = CreteDS.spaceXXL)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tab.label,
                        style = CreteDS.typeNavTab,
                        color = if (selected) CreteDS.textPrimary else CreteDS.textSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(2.5.dp)
                            .background(
                                if (selected) CreteDS.accent else Color.Transparent,
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }

        Spacer(Modifier.height(CreteDS.spaceL))

        // ── Large content tiles — fill remaining height ────────────────────
        Crossfade(
            targetState = activeTab,
            animationSpec = tween(CreteDS.animFast),
            label = "shellCards",
            modifier = Modifier.weight(1f)
        ) { tab ->
            when (tab) {
                ShellTab.WHATS_NEW -> WhatsNewSection()
                ShellTab.LIBRARY   -> LibrarySection(
                    libraryViewModel = libraryViewModel,
                    onOpenLibrary    = onOpenLibrary,
                    onGameClick      = onGameClick
                )
                ShellTab.SETTINGS  -> SettingsSection(
                    onOpenSettings  = onOpenSettings,
                    onOpenProviders = onOpenProviders,
                    onOpenDisplay   = onOpenDisplay
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// WHAT'S NEW — large landscape news cards
// ══════════════════════════════════════════════════════════════════════════

private data class NewsItem(
    val headline: String,
    val source: String,
    val timestampLabel: String,
    val thumbnailUrl: String? = null
)

// Sample items until real NewsProvider feeds are wired in
private val sampleNewsItems = listOf(
    NewsItem("The Best PC Games to Play This Weekend",           "r/pcgaming", "2h ago"),
    NewsItem("Steam Next Fest: Top 10 Most Wishlisted Demos",    "r/Games",    "5h ago"),
    NewsItem("Xbox Game Pass New Additions — August 2026",       "r/Games",    "1d ago"),
    NewsItem("Hollow Knight: Silksong Release Date Revealed",    "r/Games",    "2d ago"),
    NewsItem("Best Settings to Optimise Games on Handheld",      "r/pcgaming", "3d ago")
)

@Composable
private fun WhatsNewSection() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = CreteDS.spaceXXL, vertical = CreteDS.spaceS),
        horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceL),
        modifier = Modifier.fillMaxSize()
    ) {
        items(sampleNewsItems) { news ->
            LargeNewsCard(item = news)
        }
    }
}

@Composable
private fun LargeNewsCard(item: NewsItem) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
            .clip(RoundedCornerShape(CreteDS.radiusL))
            .background(Color(0x30FFFFFF))
            .border(0.5.dp, Color(0x44FFFFFF), RoundedCornerShape(CreteDS.radiusL))
    ) {
        if (item.thumbnailUrl != null) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Gradient placeholder — subtle variety per card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color(0xFF1A2840), Color(0xFF0A1628))
                        )
                    )
            )
        }

        // Bottom scrim + text
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xDD000000))
                    )
                )
                .padding(CreteDS.spaceL)
        ) {
            Text(
                text = item.source.uppercase(),
                style = CreteDS.typeChip,
                color = CreteDS.accent,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.headline,
                style = CreteDS.typeNavTab,
                color = CreteDS.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// LIBRARY — large source tiles → tap opens full grid
// ══════════════════════════════════════════════════════════════════════════

/**
 * Large library source tiles — same width as news cards (320dp).
 * Tapping passes the chosen filter so the full grid opens pre-filtered.
 */
@Composable
private fun LibrarySection(
    libraryViewModel: LibraryViewModel,
    onOpenLibrary: (LibraryFilter) -> Unit,
    onGameClick: (Long) -> Unit
) {
    val state by libraryViewModel.uiState.collectAsState()
    val totalCount = state.games.size

    // Pair<label, filter>  — icon drives the visual, filter drives navigation
    val sources = listOf(
        Triple("All Games",  Icons.Outlined.GridView,       LibraryFilter.ALL),
        Triple("Local",      Icons.Outlined.Computer,       LibraryFilter.LOCAL),
        Triple("Streaming",  Icons.Outlined.Stream,         LibraryFilter.STREAMING),
        Triple("Cloud",      Icons.Outlined.Cloud,          LibraryFilter.CLOUD),
        Triple("Retro",      Icons.Outlined.SportsEsports,  LibraryFilter.RETRO),
        Triple("Android",    Icons.Outlined.PhoneAndroid,   LibraryFilter.ANDROID)
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = CreteDS.spaceXXL, vertical = CreteDS.spaceS),
        horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceL),
        modifier = Modifier.fillMaxSize()
    ) {
        items(sources) { (label, icon, filter) ->
            LargeSourceTile(
                label  = label,
                icon   = icon,
                badge  = if (filter == LibraryFilter.ALL) "($totalCount)" else null,
                onClick = { onOpenLibrary(filter) }
            )
        }
    }
}

@Composable
private fun LargeSourceTile(
    label: String,
    icon: ImageVector,
    badge: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)                          // matches news card width
            .clip(RoundedCornerShape(CreteDS.radiusL))
            .background(Color(0x30FFFFFF))
            .border(0.5.dp, Color(0x44FFFFFF), RoundedCornerShape(CreteDS.radiusL))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = CreteDS.spaceXXL, horizontal = CreteDS.spaceXL),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CreteDS.textSecondary.copy(alpha = 0.7f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(CreteDS.spaceL))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = CreteDS.typeGameTitle,
                color = CreteDS.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            if (badge != null) {
                Text(text = badge, style = CreteDS.typeMeta, color = CreteDS.textSecondary)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// SETTINGS — large section tiles → tap opens settings screens
// ══════════════════════════════════════════════════════════════════════════

private data class SettingsTile(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun SettingsSection(
    onOpenSettings: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenDisplay: () -> Unit
) {
    val tiles = listOf(
        SettingsTile("PC & Streaming",  "GameNative, Moonlight, GeForce NOW",    Icons.Outlined.Stream,         onOpenProviders),
        SettingsTile("Libraries",       "Game sources and ROM folders",          Icons.AutoMirrored.Outlined.LibraryBooks, onOpenSettings),
        SettingsTile("Display",         "XREAL, external display, resolution",   Icons.Outlined.Monitor,        onOpenDisplay),
        SettingsTile("Appearance",      "Theme, layout, backgrounds",            Icons.Outlined.Palette,        onOpenSettings),
        SettingsTile("General",         "Emulators, metadata, achievements",     Icons.Outlined.Settings,       onOpenSettings)
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = CreteDS.spaceXXL, vertical = CreteDS.spaceS),
        horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceL),
        modifier = Modifier.fillMaxSize()
    ) {
        items(tiles) { tile ->
            LargeSettingsTile(tile = tile)
        }
    }
}

@Composable
private fun LargeSettingsTile(tile: SettingsTile) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)                          // matches news card width
            .clip(RoundedCornerShape(CreteDS.radiusL))
            .background(Color(0x30FFFFFF))
            .border(0.5.dp, Color(0x44FFFFFF), RoundedCornerShape(CreteDS.radiusL))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = tile.onClick
            )
            .padding(vertical = CreteDS.spaceXXL, horizontal = CreteDS.spaceXL),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = tile.icon,
            contentDescription = null,
            tint = CreteDS.textSecondary.copy(alpha = 0.7f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(CreteDS.spaceL))
        Text(
            text = tile.label,
            style = CreteDS.typeGameTitle,
            color = CreteDS.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = tile.subtitle,
            style = CreteDS.typeMeta,
            color = CreteDS.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// FULL LIBRARY GRID — pushed as a separate screen when source tile is tapped
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
 * Full library grid — shown as a pushed screen when a library source tile is tapped.
 * Uses a LazyRow of CreteGameCard tiles (same as the Recent Games carousel).
 * initialFilter pre-selects the filter pill matching what was tapped on the home screen.
 */
@Composable
fun LibraryTabContent(
    libraryViewModel: LibraryViewModel,
    onGameClick: (Long) -> Unit,
    initialFilter: LibraryFilter = LibraryFilter.ALL
) {
    val state        by libraryViewModel.uiState.collectAsState()
    var activeFilter by remember { mutableStateOf(initialFilter) }

    val providerPackages = setOf(
        "com.nvidia.geforcenow",
        "com.limelight",
        "com.nytimes.crossword",
        "app.gamenative",
        "gamehub.lite"
    )

    val filteredGames = remember(state.games, activeFilter) {
        val base = state.games.filter { game ->
            if (game.platformId == "android" && game.romPath.startsWith("package:")) {
                game.romPath.removePrefix("package:") !in providerPackages
            } else true
        }
        when (activeFilter) {
            LibraryFilter.ALL       -> base
            LibraryFilter.LOCAL     -> base.filter { it.platformId in setOf("steam", "gog", "epic", "amazon") }
            LibraryFilter.STREAMING -> base.filter { it.platformId in setOf("moonlight", "gfn") }
            LibraryFilter.CLOUD     -> base.filter { it.platformId == "gfn" }
            LibraryFilter.RETRO     -> base.filter { it.platformId !in setOf("steam","gog","epic","amazon","moonlight","gfn","android") }
            LibraryFilter.ANDROID   -> base.filter { it.platformId == "android" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Color(0xFF0E1E35), Color(0xFF060E1C)),
                    radius = 1400f
                )
            )
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
            ) { Text("No games", style = CreteDS.typeMeta) }
        } else {
            // Two-row horizontal scrolling grid of game cards — same tiles as Recent Games carousel
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = CreteDS.spaceXXL,
                    end = CreteDS.spaceXXL,
                    top = CreteDS.spaceS,
                    bottom = CreteDS.spaceS
                ),
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
private fun LibraryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(CreteDS.radiusPill))
            .background(if (selected) Color(0x554D9FFF) else Color(0x22FFFFFF))
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) CreteDS.accent.copy(alpha = 0.7f) else Color(0x40FFFFFF),
                shape = RoundedCornerShape(CreteDS.radiusPill)
            )
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
// SETTINGS TAB CONTENT — kept for backward compatibility & deep-link access
// ══════════════════════════════════════════════════════════════════════════

/**
 * Full settings screen — still used when navigating to the dedicated settings route.
 * Two-column glass layout.
 */
@Composable
fun SettingsTabContent(
    onOpenSettings: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenDisplay: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }

    val categories = listOf(
        SettingsCategoryItem("PC & Streaming", "GameNative, Moonlight, GeForce NOW",  Icons.Outlined.Stream),
        SettingsCategoryItem("Libraries",      "Game sources and folders",            Icons.AutoMirrored.Outlined.LibraryBooks),
        SettingsCategoryItem("Display",        "XREAL, external display, resolution", Icons.Outlined.Monitor),
        SettingsCategoryItem("Appearance",     "Theme, layout, backgrounds",          Icons.Outlined.Palette),
        SettingsCategoryItem("General",        "Emulators, metadata, achievements",   Icons.Outlined.Settings)
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CreteDS.spaceXXL, vertical = CreteDS.spaceXL),
        horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceXL)
    ) {
        LazyColumn(
            modifier = Modifier.width(260.dp),
            verticalArrangement = Arrangement.spacedBy(CreteDS.spaceS)
        ) {
            itemsIndexed(categories) { index, item ->
                GlassSettingsCard(item = item, selected = index == selectedIndex, onClick = { selectedIndex = index })
            }
        }

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

@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CreteDS.radiusL))
            .background(Color(0x28FFFFFF))
            .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(CreteDS.radiusL))
            .padding(CreteDS.spaceXXL),
        content = content
    )
}

@Composable
private fun GlassSettingsCard(item: SettingsCategoryItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CreteDS.radiusM))
            .background(if (selected) Color(0x554D9FFF) else Color(0x28FFFFFF))
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) CreteDS.accent.copy(alpha = 0.5f) else Color(0x33FFFFFF),
                shape = RoundedCornerShape(CreteDS.radiusM)
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = CreteDS.spaceL, vertical = CreteDS.spaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceM)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (selected) CreteDS.accent else CreteDS.textSecondary.copy(alpha = 0.6f),
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
private fun ColumnScope.PcStreamingSettingsPanel(onOpenProviders: () -> Unit, onOpenDisplay: () -> Unit) {
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
    Text(text = text, color = CreteDS.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp,
        modifier = Modifier.padding(bottom = CreteDS.spaceS))
}

@Composable
private fun ColumnScope.SettingsPanelBody(text: String) {
    Text(text = text, style = CreteDS.typeMeta)
}

@Composable
private fun ColumnScope.SettingsPanelAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CreteDS.radiusS))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = CreteDS.spaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceM)
    ) {
        Icon(imageVector = icon, contentDescription = null,
            tint = CreteDS.textSecondary.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
        Text(text = label, style = CreteDS.typeNavTab, color = CreteDS.textSecondary, modifier = Modifier.weight(1f))
        Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null,
            tint = CreteDS.textDisabled.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
    }
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x20FFFFFF)))
}

// ══════════════════════════════════════════════════════════════════════════
// LIBRARY SCREEN — full-screen pushed route wrapper for LibraryTabContent
// ══════════════════════════════════════════════════════════════════════════

/**
 * Standalone library screen — navigated to when a Library source tile is tapped.
 * Wraps LibraryTabContent and adds a back button.
 */
@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel,
    initialFilter: LibraryFilter = LibraryFilter.ALL,
    onGameClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    // No back pill — system back / gesture handles navigation.
    // Filter chips already show context (which filter is active).
    LibraryTabContent(
        libraryViewModel = libraryViewModel,
        onGameClick      = onGameClick,
        initialFilter    = initialFilter
    )
}
