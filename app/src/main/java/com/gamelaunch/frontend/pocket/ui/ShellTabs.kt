package com.gamelaunch.frontend.pocket.ui

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
// HOME TAB
// ══════════════════════════════════════════════════════════════════════════

// Sub-tabs shown below the game carousel on Home
private enum class HomeSection(val label: String) {
    WHATS_NEW("What's New"),
    LIBRARY("Library")
}

/**
 * Home tab content — WinHanced hierarchy:
 *
 * ┌────────────────────────────────────────────────┐
 * │  Recent Games                                  │  ← label
 * │  [cover] [cover] [cover] [cover] → scrolls    │  ← carousel
 * │                                                │
 * │  [What's New]  [Library]                       │  ← sub-tabs
 * │                                                │
 * │  [large tile] [large tile] [large tile] →      │  ← content tiles
 * └────────────────────────────────────────────────┘
 *
 * What's New = large landscape news cards (image + category + headline)
 * Library    = large square source tiles (icon + label) → tap goes to Library tab
 */
@Composable
fun HomeTabContent(
    homeViewModel: HomeViewModel,
    onGameClick: (Long) -> Unit,
    onTabChange: (ShellTab) -> Unit
) {
    val state       by homeViewModel.uiState.collectAsState()
    val recentGames = remember(state.recentlyPlayed, state.games) {
        state.recentlyPlayed.takeIf { it.isNotEmpty() } ?: state.games.take(20)
    }
    var focusedIndex  by remember { mutableIntStateOf(0) }
    var activeSection by remember { mutableStateOf(HomeSection.WHATS_NEW) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = CreteDS.space3XL)
    ) {
        // ── Recent Games label ─────────────────────────────────────────
        Text(
            text = "Recent Games",
            style = CreteDS.typeGameTitle,
            color = CreteDS.textPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = CreteDS.spaceXXL, bottom = CreteDS.spaceM)
        )

        // ── Game carousel ──────────────────────────────────────────────
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

        Spacer(Modifier.height(CreteDS.spaceXXL))

        // ── Section sub-tabs ─────────────────────────────────────────────
        Row(
            modifier = Modifier.padding(start = CreteDS.spaceXXL),
            horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceXXL)
        ) {
            HomeSection.entries.forEach { section ->
                val selected = section == activeSection
                Column(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { activeSection = section },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = section.label,
                        style = if (selected) CreteDS.typeNavTab else CreteDS.typeNavTabDim,
                        color = if (selected) CreteDS.textPrimary else CreteDS.textSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(4.dp))
                    // Underline indicator
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(2.dp)
                            .background(
                                if (selected) CreteDS.accent
                                else Color.Transparent,
                                RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        }

        Spacer(Modifier.height(CreteDS.spaceL))

        // ── Large content tiles — fill the remaining height ──────────────
        Box(modifier = Modifier.weight(1f)) {
            when (activeSection) {
                HomeSection.WHATS_NEW -> NewsSection()
                HomeSection.LIBRARY   -> LibrarySourceSection(
                    onNavigateToLibrary = { onTabChange(ShellTab.LIBRARY) }
                )
            }
        }
    }
}

// ── Library source section — large square tiles ───────────────────────────

/**
 * Full-height row of large library source tiles — WinHanced Library tab style.
 * Each tile is a tall frosted-glass card with an icon + label.
 * Tapping any tile navigates to the Library tab.
 */
@Composable
private fun LibrarySourceSection(onNavigateToLibrary: () -> Unit) {
    val sources = listOf(
        Triple("All Games",  Icons.Outlined.GridView,        null),
        Triple("Local",      Icons.Outlined.Computer,        null),
        Triple("Streaming",  Icons.Outlined.Stream,          null),
        Triple("Cloud",      Icons.Outlined.Cloud,           null),
        Triple("Retro",      Icons.Outlined.SportsEsports,   null),
        Triple("Android",    Icons.Outlined.PhoneAndroid,    null)
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = CreteDS.spaceXXL, vertical = CreteDS.spaceS),
        horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceL),
        modifier = Modifier.fillMaxSize()
    ) {
        items(sources) { (label, icon, _) ->
            LargeSourceTile(
                label = label,
                icon  = icon,
                onClick = onNavigateToLibrary
            )
        }
    }
}

@Composable
private fun LargeSourceTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(220.dp)
            .clip(RoundedCornerShape(CreteDS.radiusL))
            .background(Color(0x30FFFFFF))           // frosted glass
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
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CreteDS.textSecondary.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(CreteDS.spaceL))
        Text(
            text = label,
            style = CreteDS.typeGameTitle,
            color = CreteDS.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── News section — large landscape cards ──────────────────────────────────

/**
 * Full-height row of large landscape news cards — WinHanced What's New style.
 * Each card fills the available height with an image, category label and headline
 * overlaid at the bottom behind a gradient scrim.
 */
@Composable
private fun NewsSection() {
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
        // Thumbnail fills the whole card when available
        if (item.thumbnailUrl != null) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Gradient placeholder — unique-ish colour per item
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

private data class NewsItem(
    val headline: String,
    val source: String,
    val timestampLabel: String,
    val thumbnailUrl: String? = null
)

// Sample items until real NewsProvider feeds are wired in
private val sampleNewsItems = listOf(
    NewsItem("The Best PC Games to Play This Weekend", "r/pcgaming", "2h ago"),
    NewsItem("Steam Next Fest: Top 10 Most Wishlisted Demos", "r/Games", "5h ago"),
    NewsItem("Xbox Game Pass New Additions — August 2026", "r/Games", "1d ago"),
    NewsItem("Hollow Knight: Silksong Release Date Revealed", "r/Games", "2d ago"),
    NewsItem("Best Settings to Optimise Games on Handheld", "r/pcgaming", "3d ago")
)

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
 * Excludes provider apps (GeForce NOW, Moonlight, etc.) from display.
 */
@Composable
fun LibraryTabContent(
    libraryViewModel: LibraryViewModel,
    onGameClick: (Long) -> Unit
) {
    val state      by libraryViewModel.uiState.collectAsState()
    var activeFilter by remember { mutableStateOf(LibraryFilter.ALL) }

    // Provider packages to exclude from library display
    val providerPackages = setOf(
        "com.nvidia.geforcenow",
        "com.limelight",
        "com.nytimes.crossword",
        "app.gamenative",
        "gamehub.lite"
    )

    val filteredGames = remember(state.games, activeFilter) {
        // First, exclude Android provider/launcher shortcuts
        val base = state.games.filter { game ->
            if (game.platformId == "android" && game.romPath.startsWith("package:")) {
                val pkg = game.romPath.removePrefix("package:")
                pkg !in providerPackages
            } else {
                true
            }
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
            // Dark base under the grid so games read better
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                Color(0x88060C18),
                                Color(0xCC060C18)
                            )
                        )
                    )
            ) {
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
                if (selected) Color(0x554D9FFF)   // accent tint when selected
                else Color(0x22FFFFFF)             // light smoky-glass when unselected
            )
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) CreteDS.accent.copy(alpha = 0.7f)
                        else Color(0x40FFFFFF),    // very subtle white border
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
        SettingsCategoryItem("Libraries",        "Game sources and folders",             Icons.AutoMirrored.Outlined.LibraryBooks),
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
                // frosted glass - light smoke over the dark background
                Color(0x28FFFFFF)
            )
            .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(CreteDS.radiusL))
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
                if (selected) Color(0x554D9FFF)   // accent tint when selected
                else Color(0x28FFFFFF)             // light frosted glass when unselected
            )
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) CreteDS.accent.copy(alpha = 0.5f)
                        else Color(0x33FFFFFF),
                shape = RoundedCornerShape(CreteDS.radiusM)
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
            // softer: textSecondary at reduced alpha when unselected, accent when selected
            tint = if (selected) CreteDS.accent
                   else CreteDS.textSecondary.copy(alpha = 0.6f),
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
            tint = CreteDS.textSecondary.copy(alpha = 0.55f),   // softer grey
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
            tint = CreteDS.textDisabled.copy(alpha = 0.5f),     // even softer chevron
            modifier = Modifier.size(16.dp)
        )
    }
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x20FFFFFF)))
}
