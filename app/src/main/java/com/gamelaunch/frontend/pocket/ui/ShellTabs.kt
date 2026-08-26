package com.gamelaunch.frontend.pocket.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.pocket.ui.design.*
import com.gamelaunch.frontend.pocket.ui.home.RedditNewsViewModel
import com.gamelaunch.frontend.pocket.ui.library.LibraryViewModel
import com.gamelaunch.frontend.ui.screen.home.HomeViewModel
import kotlin.math.abs

// ══════════════════════════════════════════════════════════════════════════
// V1 DESIGN CONSTANTS
// ══════════════════════════════════════════════════════════════════════════

private val AmberAccent = Color(0xFFE9A93C)
private val CreamText = Color(0xFFF2E8D5)
private val DimCream = CreamText.copy(alpha = 0.55f)
private val VeryDimCream = CreamText.copy(alpha = 0.27f)
private val DarkBase = Color(0xFF0A0D10)
private val GreenSync = Color(0xFF4ADE80)
private val RedPlay = Color(0xFFC9482A)

// Deterministic colour palette for game cards without artwork
private val CardPalette = listOf(
    0xFF17364F, 0xFF141B31, 0xFF8E3A22, 0xFF2A1B3C,
    0xFF123045, 0xFF0F4239, 0xFF241F19
)

private fun deterministicColor(title: String): Color =
    Color(CardPalette[abs(title.hashCode()) % CardPalette.size])

// ══════════════════════════════════════════════════════════════════════════
// V1 GAME CARD — shared by home rails and library grid
// ══════════════════════════════════════════════════════════════════════════

/**
 * V1 design game card with giant letter backdrop, bottom scrim, focus glow.
 * Used in both home rail carousels and library grid.
 */
@Composable
fun V1GameCard(
    artworkUrl: String?,
    title: String,
    platformId: String,
    focused: Boolean,
    width: Dp = 152.dp,
    height: Dp = 203.dp,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(CreteDS.animFast),
        label = "cardScale"
    )

    val bgColor = deterministicColor(title)
    val initial = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    // Platform label
    val platformLabel = when (platformId.lowercase()) {
        "steam" -> "STEAM"
        "gog" -> "GOG"
        "epic" -> "EPIC"
        "amazon" -> "PRIME"
        "android" -> "ANDROID"
        "moonlight" -> "MOONLIGHT"
        "gfn" -> "GFN"
        else -> platformId.uppercase().take(6)
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (focused) {
                    Modifier
                        .drawBehind {
                            // Amber glow effect
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        AmberAccent.copy(alpha = 0.3f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width / 2, size.height / 2),
                                    radius = size.maxDimension * 0.7f
                                )
                            )
                        }
                        .border(3.dp, AmberAccent, RoundedCornerShape(12.dp))
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Background — artwork or gradient
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Gradient background from deterministic colour
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(bgColor, bgColor.copy(alpha = 0.6f)),
                            radius = 400f
                        )
                    )
            )
        }

        // Giant letter backdrop — right-aligned, partially clipped
        Text(
            text = initial,
            fontSize = 150.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.46f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 20.dp)
        )

        // Bottom scrim gradient 0%→86% dark
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            1f to DarkBase.copy(alpha = 0.86f)
                        )
                    )
                )
        )

        // Bottom text overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = CreamText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = platformLabel,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = DimCream,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// CRETE HOME LAYOUT — v1 hero tile + two rails
// ══════════════════════════════════════════════════════════════════════════

/**
 * HOME tab content — v1 design with:
 * 1. Large hero tile for the top game
 * 2. "Jump back in" rail — recently played games
 * 3. "Added this week" rail — newest additions
 */
@Composable
fun CreteHomeLayout(
    activeTab: ShellTab,
    onTabSelected: (ShellTab) -> Unit,
    homeViewModel: HomeViewModel,
    libraryViewModel: LibraryViewModel,
    onGameClick: (Long) -> Unit,
    onOpenLibrary: (LibraryFilter) -> Unit,
    onOpenCreteSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenDisplay: () -> Unit,
    modifier: Modifier = Modifier,
    newsViewModel: RedditNewsViewModel = hiltViewModel()
) {
    val libState by libraryViewModel.uiState.collectAsState()

    // Provider apps to exclude
    val providerPackages = remember {
        setOf(
            "com.nvidia.geforcenow", "com.limelight", "com.nytimes.crossword",
            "app.gamenative", "gamehub.lite"
        )
    }

    fun Game.isProviderApp() =
        platformId == "android" && romPath.startsWith("package:") &&
                romPath.removePrefix("package:") in providerPackages

    // All games excluding providers
    val allGames = remember(libState.games) {
        libState.games.filter { !it.isProviderApp() }
    }

    // Jump back in — games with lastPlayedMs set, sorted by last played (most recent first)
    val jumpBackInGames = remember(allGames) {
        allGames
            .filter { it.lastPlayedMs != null && it.lastPlayedMs > 0 }
            .sortedByDescending { it.lastPlayedMs }
    }

    // Added this week — newest additions by dateAdded, limit 9
    val addedThisWeekGames = remember(allGames) {
        allGames
            .sortedByDescending { it.dateAdded }
            .take(9)
    }

    // Hero game — the most recently played game (top of jumpBackIn)
    val heroGame = jumpBackInGames.firstOrNull()
    val heroMedia = heroGame?.let { libState.mediaForGames[it.id] }

    // Focus states
    var heroFocused by remember { mutableStateOf(false) }
    var jumpBackInFocusIndex by remember { mutableIntStateOf(-1) }
    var addedThisWeekFocusIndex by remember { mutableIntStateOf(-1) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // ── HERO TILE ──────────────────────────────────────────────────────
        item {
            if (heroGame != null) {
                HeroTile(
                    game = heroGame,
                    media = heroMedia,
                    focused = heroFocused,
                    onClick = { onGameClick(heroGame.id) },
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp)
                )
            } else {
                // Empty state hero
                Box(
                    modifier = Modifier
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0F1317))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.SportsEsports,
                            contentDescription = null,
                            tint = DimCream,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No games played yet",
                            color = DimCream,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Add games via Settings → PC & Streaming",
                            color = VeryDimCream,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // ── JUMP BACK IN RAIL ──────────────────────────────────────────────
        if (jumpBackInGames.isNotEmpty()) {
            item {
                GameRail(
                    title = "Jump back in",
                    games = jumpBackInGames.drop(1), // Skip hero game
                    mediaForGames = libState.mediaForGames,
                    focusedIndex = jumpBackInFocusIndex,
                    onFocusChange = { jumpBackInFocusIndex = it },
                    onGameClick = onGameClick
                )
            }
        }

        // ── ADDED THIS WEEK RAIL ───────────────────────────────────────────
        if (addedThisWeekGames.isNotEmpty()) {
            item {
                GameRail(
                    title = "Added this week",
                    games = addedThisWeekGames,
                    mediaForGames = libState.mediaForGames,
                    focusedIndex = addedThisWeekFocusIndex,
                    onFocusChange = { addedThisWeekFocusIndex = it },
                    onGameClick = onGameClick
                )
            }
        }
    }
}

// ── HERO TILE ──────────────────────────────────────────────────────────────

@Composable
private fun HeroTile(
    game: Game,
    media: GameMedia?,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = deterministicColor(game.title)
    val initial = game.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    // Format play time
    val playTimeHours = if (game.playCount > 0) {
        String.format("%.1f", game.playCount * 0.5) // Rough estimate
    } else "0.0"

    // Platform label
    val platformLabel = when (game.platformId.lowercase()) {
        "steam" -> "STEAM"
        "gog" -> "GOG"
        "epic" -> "EPIC"
        "amazon" -> "PRIME"
        "android" -> "ANDROID"
        "moonlight" -> "MOONLIGHT"
        "gfn" -> "GFN"
        else -> game.platformId.uppercase().take(8)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (focused) {
                    Modifier.border(3.dp, AmberAccent, RoundedCornerShape(20.dp))
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Background — radial gradient from accent colour
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(bgColor, bgColor.copy(alpha = 0.4f), DarkBase),
                        center = Offset(0.7f, 0.3f),
                        radius = 1200f
                    )
                )
        )

        // Artwork background if available
        if (media?.effectiveBoxArt != null) {
            AsyncImage(
                model = media.effectiveBoxArt,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.4f }
            )
        }

        // Giant letter — right side
        Text(
            text = initial,
            fontSize = 460.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.42f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 60.dp, y = 40.dp)
        )

        // Left gradient overlay: 94%→76%→10% opacity dark
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(500.dp)
                .align(Alignment.CenterStart)
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to DarkBase.copy(alpha = 0.94f),
                            0.5f to DarkBase.copy(alpha = 0.76f),
                            1f to DarkBase.copy(alpha = 0.10f)
                        )
                    )
                )
        )

        // Left content
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp, top = 32.dp, bottom = 32.dp)
                .widthIn(max = 420.dp)
        ) {
            // Subtitle
            Text(
                text = "CONTINUE WHERE YOU STOPPED",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = AmberAccent,
                letterSpacing = 2.8.sp
            )

            Spacer(Modifier.height(12.dp))

            // Game title
            Text(
                text = game.title,
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                color = CreamText,
                letterSpacing = (-1.1).sp,
                lineHeight = 56.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(16.dp))

            // Tag row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Platform chip
                HeroChip(text = platformLabel)

                // Play time chip
                HeroChip(text = "$playTimeHours H PLAYED")

                // Save synced chip (green)
                HeroChip(
                    text = "SAVE SYNCED",
                    textColor = GreenSync,
                    borderColor = GreenSync.copy(alpha = 0.3f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Button row
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Red PLAY button
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(RedPlay)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick
                        )
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "PLAY",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.width(4.dp))
                    // A button indicator
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Game details outline button
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, CreamText.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Game details",
                        color = CreamText,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Right side — frosted glass panel
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp)
                .width(296.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0A0D10).copy(alpha = 0.66f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProfileRow(label = "Runtime", value = "ProtonGE 9-7")
                ProfileRow(label = "Profile", value = "Performance")
                ProfileRow(label = "Controls", value = "Xbox Layout")
                ProfileRow(label = "Last session", value = formatLastPlayed(game.lastPlayedMs))
            }
        }
    }
}

@Composable
private fun HeroChip(
    text: String,
    textColor: Color = CreamText,
    borderColor: Color = CreamText.copy(alpha = 0.2f)
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = DimCream
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = CreamText
        )
    }
}

private fun formatLastPlayed(lastPlayedMs: Long?): String {
    if (lastPlayedMs == null || lastPlayedMs == 0L) return "Never"
    val now = System.currentTimeMillis()
    val diff = now - lastPlayedMs
    val hours = diff / (1000 * 60 * 60)
    val days = hours / 24
    return when {
        hours < 1 -> "Just now"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}

// ── GAME RAIL ──────────────────────────────────────────────────────────────

@Composable
private fun GameRail(
    title: String,
    games: List<Game>,
    mediaForGames: Map<Long, GameMedia>,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onGameClick: (Long) -> Unit
) {
    if (games.isEmpty()) return

    Column(
        modifier = Modifier.padding(top = 24.dp)
    ) {
        // Title row: label + fading line + count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = CreamText,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.width(16.dp))

            // Fading line
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                CreamText.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Spacer(Modifier.width(16.dp))

            // Count
            Text(
                text = "${games.size}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = DimCream
            )
        }

        // Game cards row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
                V1GameCard(
                    artworkUrl = mediaForGames[game.id]?.effectiveBoxArt,
                    title = game.title,
                    platformId = game.platformId,
                    focused = index == focusedIndex,
                    onClick = {
                        onFocusChange(index)
                        onGameClick(game.id)
                    }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// LIBRARY TAB — v1 filter chips + game grid
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
 * Library tab content — v1 design with styled filter chips and game grid.
 */
@Composable
fun LibraryTabContent(
    libraryViewModel: LibraryViewModel,
    onGameClick: (Long) -> Unit,
    initialFilter: LibraryFilter = LibraryFilter.ALL
) {
    val state by libraryViewModel.uiState.collectAsState()
    var activeFilter by remember { mutableStateOf(initialFilter) }
    var focusedGameId by remember { mutableStateOf<Long?>(null) }
    var showSources by remember { mutableStateOf(false) }

    val providerPackages = setOf(
        "com.nvidia.geforcenow", "com.limelight", "com.nytimes.crossword",
        "app.gamenative", "gamehub.lite"
    )

    val filteredGames = remember(state.games, activeFilter) {
        val base = state.games.filter { game ->
            if (game.platformId == "android" && game.romPath.startsWith("package:")) {
                game.romPath.removePrefix("package:") !in providerPackages
            } else true
        }
        when (activeFilter) {
            LibraryFilter.ALL -> base
            LibraryFilter.LOCAL -> base.filter { it.platformId in setOf("steam", "gog", "epic", "amazon") }
            LibraryFilter.STREAMING -> base.filter { it.platformId in setOf("moonlight", "gfn") }
            LibraryFilter.CLOUD -> base.filter { it.platformId == "gfn" }
            LibraryFilter.RETRO -> base.filter {
                it.platformId !in setOf("steam", "gog", "epic", "amazon", "moonlight", "gfn", "android")
            }
            LibraryFilter.ANDROID -> base.filter { it.platformId == "android" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        // Filter chips row + count + sort
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(LibraryFilter.entries) { filter ->
                    V1FilterChip(
                        label = filter.label,
                        selected = filter == activeFilter,
                        onClick = { activeFilter = filter }
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // Game count
            Text(
                text = "${filteredGames.size} games",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = DimCream
            )

            Spacer(Modifier.width(12.dp))

            // Sources toggle button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (showSources) AmberAccent.copy(alpha = 0.16f)
                        else CreamText.copy(alpha = 0.05f)
                    )
                    .border(
                        1.dp,
                        if (showSources) AmberAccent.copy(alpha = 0.5f) else CreamText.copy(alpha = 0.10f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showSources = !showSources }
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Layers,
                    contentDescription = "Sources",
                    tint = if (showSources) AmberAccent else DimCream,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Sources",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (showSources) AmberAccent else DimCream
                )
            }

            Spacer(Modifier.width(8.dp))

            // Sort button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CreamText.copy(alpha = 0.05f))
                    .border(1.dp, CreamText.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* TODO: Sort menu */ }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                    contentDescription = "Sort",
                    tint = DimCream,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Sources view or game grid
        if (showSources) {
            SourcesGridView()
        } else if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AmberAccent)
            }
        } else if (filteredGames.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.SportsEsports,
                        contentDescription = null,
                        tint = DimCream,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No games in this category",
                        color = DimCream,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredGames, key = { it.id }) { game ->
                    // Aspect ratio 3:4 — card height = width * 4/3
                    V1GameCard(
                        artworkUrl = state.mediaForGames[game.id]?.effectiveBoxArt,
                        title = game.title,
                        platformId = game.platformId,
                        focused = game.id == focusedGameId,
                        width = 140.dp,
                        height = 187.dp, // 140 * 4/3 ≈ 187
                        onClick = {
                            focusedGameId = game.id
                            onGameClick(game.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun V1FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected) AmberAccent.copy(alpha = 0.16f) else CreamText.copy(alpha = 0.05f)
    val borderColor = if (selected) AmberAccent else CreamText.copy(alpha = 0.10f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(100.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) AmberAccent else DimCream
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// LIBRARY SCREEN — standalone route wrapper
// ══════════════════════════════════════════════════════════════════════════

/**
 * Standalone library screen — navigated to when a Library source tile is tapped.
 */

// ══════════════════════════════════════════════════════════════════════════
// SOURCES GRID VIEW — shown when Sources toggle is active in Library
// ══════════════════════════════════════════════════════════════════════════

private data class SourceCard(
    val name: String,
    val kind: String,
    val initial: String,
    val bgColor: Long,
    val count: String,
    val note: String,
    val enabled: Boolean = true
)

@Composable
private fun SourcesGridView() {
    val sources = listOf(
        SourceCard("Game Native",  "Windows runtime",    "N", 0xFF2E7D96, "Steam library", "Handles PC games via GameNative bridge."),
        SourceCard("Moonlight",    "PC streaming",       "M", 0xFF1A4A7A, "PC games",      "Stream games from your gaming PC."),
        SourceCard("GeForce NOW",  "Cloud streaming",    "G", 0xFF76B900, "Cloud library", "NVIDIA cloud gaming service."),
        SourceCard("RetroArch",    "Emulator frontend",  "R", 0xFFC9482A, "ROMs",          "Multi-system emulation via RetroArch."),
        SourceCard("Android",      "Native apps",        "A", 0xFF3DDC84, "Android games", "Games installed directly on device."),
        SourceCard("GameHub",      "Windows runtime",    "H", 0xFF3E6FB8, "PC games",      "Alternative Windows compatibility layer.")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
    ) {
        Text(
            text = "Everything CreteOS can see",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = CreamText
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Runtimes, emulators and folders indexed into one library. Toggle a source off and its titles leave the shelves.",
            fontSize = 13.sp,
            color = DimCream,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(sources) { source ->
                SourceCardItem(source = source)
            }
        }
    }
}

@Composable
private fun SourceCardItem(source: SourceCard) {
    var enabled by remember { mutableStateOf(source.enabled) }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F1317))
            .border(1.dp, CreamText.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
            .padding(18.dp)
    ) {
        // Header: icon + name + kind + toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon box
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(source.bgColor).copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = source.initial,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = source.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CreamText)
                Text(
                    text = source.kind.uppercase(),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp,
                    color = DimCream
                )
            }
            // Toggle
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (enabled) AmberAccent.copy(alpha = 0.55f) else CreamText.copy(alpha = 0.08f))
                    .border(1.dp, CreamText.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { enabled = !enabled }
            ) {
                Box(
                    modifier = Modifier
                        .padding(3.dp)
                        .size(18.dp)
                        .align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(CreamText.copy(alpha = 0.08f)))
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(text = source.count, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = CreamText)
            Text(
                text = "SCANNED RECENTLY",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = DimCream.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(text = source.note, fontSize = 12.sp, color = DimCream, lineHeight = 16.sp)
    }
}

@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel,
    initialFilter: LibraryFilter = LibraryFilter.ALL,
    onGameClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    LibraryTabContent(
        libraryViewModel = libraryViewModel,
        onGameClick = onGameClick,
        initialFilter = initialFilter
    )
}

// ══════════════════════════════════════════════════════════════════════════
// SETTINGS TAB CONTENT — kept for backward compatibility
// ══════════════════════════════════════════════════════════════════════════

// Uses SettingsCategoryItem from CreteSettingsScreen.kt — no local redeclaration

/**
 * Full settings screen — two-column glass layout.
 */
@Composable
fun SettingsTabContent(
    onOpenSettings: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenDisplay: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }

    val categories = listOf(
        SettingsCategoryItem("PC & Streaming", "GameNative, Moonlight, GeForce NOW", Icons.Outlined.Stream),
        SettingsCategoryItem("Libraries", "Game sources and folders", Icons.AutoMirrored.Outlined.LibraryBooks),
        SettingsCategoryItem("Display", "XREAL, external display, resolution", Icons.Outlined.Monitor),
        SettingsCategoryItem("Appearance", "Theme, layout, backgrounds", Icons.Outlined.Palette),
        SettingsCategoryItem("General", "Emulators, metadata, achievements", Icons.Outlined.Settings)
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
                GlassSettingsCard(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { selectedIndex = index }
                )
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CreteDS.textSecondary.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = CreteDS.typeNavTab,
            color = CreteDS.textSecondary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = CreteDS.textDisabled.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x20FFFFFF)))
}
