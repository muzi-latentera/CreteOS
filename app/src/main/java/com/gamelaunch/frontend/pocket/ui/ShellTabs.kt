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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.platform.LocalUriHandler
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
import com.gamelaunch.frontend.pocket.data.SteamMetadataDao
import com.gamelaunch.frontend.pocket.data.SteamMetadataEntity
import com.gamelaunch.frontend.pocket.data.IgdbSeedData
import com.gamelaunch.frontend.pocket.data.formatPlaytime
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

private fun platformDisplayLabel(platformId: String) = when (platformId.lowercase()) {
    "steam"     -> "STEAM"
    "gog"       -> "GOG"
    "epic"      -> "EPIC"
    "ea"        -> "EA"
    "gamepass", "xbox" -> "GAME PASS"
    "ubisoft"   -> "UBISOFT"
    "amazon"    -> "PRIME"
    "android"   -> "ANDROID"
    "moonlight" -> "MOONLIGHT"
    "gfn"       -> "GFN"
    "local", "gamenative" -> "LOCAL"
    else        -> platformId.uppercase().take(8)
}

private fun platformPillColor(platformId: String) = when (platformId.lowercase()) {
    "steam"              -> Color(0xFF1B2838)
    "gog"                -> Color(0xFF5C2D91)
    "epic"               -> Color(0xFF313131)
    "ea"                 -> Color(0xFFE8620A)
    "gamepass", "xbox"   -> Color(0xFF107C10)
    "ubisoft"            -> Color(0xFF0070CC)
    "amazon"             -> Color(0xFF00A8E0)
    "moonlight"          -> Color(0xFF1A4A7A)
    "gfn"                -> Color(0xFF76B900)
    "local", "gamenative" -> Color(0xFF2D6A3F)  // green = locally installed
    else                 -> Color(0xFF2A2A2A)
}

// Favicon URLs for platform logos shown in pills
// Note: EA favicon is just an orange square — skip it so we fall back to the vector icon
private fun platformIconUrl(platformId: String): String? = when (platformId.lowercase()) {
    "steam"              -> "https://store.steampowered.com/favicon.ico"
    "gog"                -> "https://www.gog.com/favicon.ico"
    "gamepass", "xbox"   -> "https://www.xbox.com/favicon.ico"
    "epic"               -> "https://www.epicgames.com/favicon.ico"
    "amazon"             -> "https://gaming.amazon.com/favicon.ico"
    // EA and Ubisoft favicons are poor quality — use vector icons instead
    else                 -> null
}

@Composable
private fun PlatformPill(platformId: String, label: String) {
    val iconUrl = platformIconUrl(platformId)
    // Try vector/drawable icon from PlatformVisuals for emulation systems and platforms without good favicons
    val vectorIconRes = com.gamelaunch.frontend.ui.component.platformIcon(platformId)
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(platformPillColor(platformId).copy(alpha = 0.85f))
            .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            // 1. Store platforms with good favicons — use the favicon
            iconUrl != null -> {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = label,
                    modifier = Modifier.size(12.dp),
                    contentScale = ContentScale.Fit
                )
            }
            // 2. Emulation systems and platforms with vector icons — use the drawable
            vectorIconRes != null -> {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = vectorIconRes),
                    contentDescription = label,
                    modifier = Modifier.size(12.dp),
                    tint = Color.Unspecified  // Preserve original icon colors
                )
            }
            // 3. Fallback: first 2 chars of label
            else -> {
                Text(
                    text = label.take(2),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

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
    fallbackUrl: String? = null,
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
    val platformLabel = platformDisplayLabel(platformId)

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
        // Background — layered artwork:
        // Layer 1: IGDB cover (always loads if available)
        // Layer 2: Steam portrait art on top (may 404 on some games — Coil shows nothing, layer 1 shows through)
        val effectiveUrl = artworkUrl ?: fallbackUrl
        if (effectiveUrl != null) {
            // Show IGDB cover as base layer first
            if (fallbackUrl != null && artworkUrl != null) {
                AsyncImage(
                    model = fallbackUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Then Steam art on top (transparent if 404)
            AsyncImage(
                model = effectiveUrl,
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

        // Bottom scrim + text — only shown when no artwork at all
        if (effectiveUrl == null) {
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
                Spacer(Modifier.height(4.dp))
                PlatformPill(platformId = platformId, label = platformLabel)
            }
        } else {
            // Artwork card: small platform pill bottom-left
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                PlatformPill(platformId = platformId, label = platformLabel)
            }
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
    newsViewModel: RedditNewsViewModel = hiltViewModel(),
    heroSteamViewModel: HeroSteamViewModel = hiltViewModel()
) {
    val steamMetadataDao = heroSteamViewModel.steamMetadataDao
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

    // Added this week — not used (replaced by Reddit news)
    // val addedThisWeekGames = ...

    // Hero game — the most recently played game (top of jumpBackIn)
    val heroGame = jumpBackInGames.firstOrNull()
    val heroMedia = heroGame?.let { libState.mediaForGames[it.id] }

    // Load Steam metadata for the hero game (developer, publisher, playtime)
    val heroAppId = heroGame?.romPath?.substringAfterLast(":")?.takeIf { it.isNotBlank() }
    val heroSteamMeta by produceState<SteamMetadataEntity?>(null, heroAppId) {
        value = heroAppId?.let { steamMetadataDao.getByAppId(it) }
    }

    // Focus states
    var heroFocused by remember { mutableStateOf(false) }
    var jumpBackInFocusIndex by remember { mutableIntStateOf(-1) }

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
                    steamMeta = heroSteamMeta,
                    focused = heroFocused,
                    onClick = { onGameClick(heroGame.id) },
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 80.dp, bottom = 12.dp)
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

        // ── REDDIT NEWS RAIL ───────────────────────────────────────────
        item {
            RedditNewsRail(newsViewModel = newsViewModel)
        }
    }
}

// ── HERO TILE ──────────────────────────────────────────────────────────────

@Composable
private fun HeroTile(
    game: Game,
    media: GameMedia?,
    steamMeta: SteamMetadataEntity?,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = deterministicColor(game.title)

    // Platform label for chips
    val platformLabel = platformDisplayLabel(game.platformId)

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

        // Background image — same source as the detail screen hero:
        // effectiveBackground → screenshotRemoteUrl → library_hero.jpg stored by DebugSeedReceiver
        // For Steam games also try the higher-res header.jpg directly
        val appId = game.romPath.substringAfterLast(":").takeIf { it.isNotBlank() }
        val heroImageUrl = when {
            // Prefer the background/screenshot URL (library_hero) that detail screen uses
            media?.effectiveBackground != null -> media.effectiveBackground
            // Steam fallback: header.jpg (460×215, crisp)
            appId != null && game.platformId.lowercase() == "steam" ->
                "https://cdn.akamai.steamstatic.com/steam/apps/$appId/header.jpg"
            media?.effectiveBoxArt != null -> media.effectiveBoxArt
            else -> null
        }
        if (heroImageUrl != null) {
            AsyncImage(
                model = heroImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.65f }
            )
        }

        // Left gradient overlay: 94%→76%→10% opacity dark
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(500.dp)
                .align(Alignment.CenterStart)
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f   to DarkBase.copy(alpha = 0.82f),
                            0.4f to DarkBase.copy(alpha = 0.60f),
                            1f   to DarkBase.copy(alpha = 0.05f)
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
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = CreamText,
                letterSpacing = (-0.5).sp,
                lineHeight = 42.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(16.dp))

                // Tag row — platform + provider only (playtime shown in right panel)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroChip(text = platformLabel)

                    // Provider chip
                    HeroChip(
                        text = when (game.platformId.lowercase()) {
                            "steam"     -> "GAME NATIVE"
                            "gfn"       -> "GEFORCE NOW"
                            "moonlight" -> "MOONLIGHT"
                            "android"   -> "ANDROID"
                            else        -> "READY"
                        },
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "GAME INFO",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    color = CreamText.copy(alpha = 0.42f)
                )
                ProfileRow(
                    label = "Developer",
                    value = steamMeta?.developer ?: "—"
                )
                ProfileRow(
                    label = "Publisher",
                    value = steamMeta?.publisher ?: "—"
                )
                ProfileRow(
                    label = "Playtime",
                    value = steamMeta.formatPlaytime()
                )
                ProfileRow(
                    label = "Last played",
                    value = formatLastPlayed(
                        // Use the most recent timestamp: eOr records CreteOS launches,
                        // Steam records last played on PC. Show whichever is newer.
                        listOfNotNull(steamMeta?.lastPlayedMs, game.lastPlayedMs)
                            .filter { it > 0 }
                            .maxOrNull()
                    )
                )
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
        hours < 1  -> "Just now"
        hours < 24 -> "${hours}h ago"
        days < 7   -> "${days}d ago"
        else -> {
            // Include year e.g. "Oct 30 '26"
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = lastPlayedMs }
            val month = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")[cal.get(java.util.Calendar.MONTH)]
            val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val year = cal.get(java.util.Calendar.YEAR).toString().takeLast(2)
            "$month $day '$year"
        }
    }
}

// ── REDDIT NEWS RAIL ───────────────────────────────────────────────────────

@Composable
private fun RedditNewsRail(newsViewModel: RedditNewsViewModel) {
    val newsState by newsViewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val posts = newsState.posts

    if (posts.isEmpty()) return

    Column(modifier = Modifier.padding(top = 24.dp)) {
        // Rail header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WHAT'S NEW",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = CreamText,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(CreamText.copy(alpha = 0.2f), Color.Transparent)
                        )
                    )
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "${posts.size}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = DimCream
            )
        }

        // Horizontal scrolling news cards
        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            items(posts) { post ->
                RedditNewsCard(
                    title = post.headline,
                    subreddit = post.subreddit,
                    thumbnailUrl = post.thumbnailUrl,
                    onClick = {
                        try { uriHandler.openUri(post.url) } catch (_: Exception) {}
                    }
                )
            }
        }
    }
}

@Composable
private fun RedditNewsCard(
    title: String,
    subreddit: String,
    thumbnailUrl: String?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(300.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F1317))
            .border(1.dp, CreamText.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Thumbnail if available
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.3f }
            )
        }
        // Bottom gradient scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to Color(0xFF0F1317).copy(alpha = 0.7f),
                        1f to Color(0xFF0F1317).copy(alpha = 0.97f)
                    )
                )
        )
        // Content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(
                text = subreddit,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = AmberAccent,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = CreamText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )
        }
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
                    fallbackUrl = IgdbSeedData.coverUrlFor(
                        game.romPath.substringAfterLast(":").takeIf { it.isNotBlank() } ?: ""
                    ),
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
    OWNED("Owned"),
    STREAMING("Streaming"),
    CLOUD("Cloud"),
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
            LibraryFilter.ALL      -> base
            LibraryFilter.LOCAL    -> base.filter {
                val appId = it.romPath.substringAfterLast(":")
                appId in state.localAppIds
            }
            LibraryFilter.OWNED    -> base.filter { it.platformId in setOf("steam", "gog", "epic", "ea", "gamepass", "xbox", "ubisoft", "amazon") }
            LibraryFilter.STREAMING -> base.filter { it.platformId == "moonlight" }
            LibraryFilter.CLOUD    -> base.filter { it.platformId == "gfn" }
            LibraryFilter.ANDROID  -> base.filter { it.platformId == "android" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 68.dp) // clear system pill (≈56dp pill + 12dp gap)
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
                columns = GridCells.Adaptive(minSize = 152.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredGames, key = { it.id }) { game ->
                    V1GameCard(
                        artworkUrl = state.mediaForGames[game.id]?.effectiveBoxArt,
                        fallbackUrl = IgdbSeedData.coverUrlFor(
                            game.romPath.substringAfterLast(":").takeIf { it.isNotBlank() } ?: ""
                        ),
                        title = game.title,
                        platformId = game.platformId,
                        focused = game.id == focusedGameId,
                        width = 152.dp,
                        height = 203.dp,
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
            .padding(horizontal = CreteDS.spaceXXL)
            .padding(top = 68.dp, bottom = CreteDS.spaceXL), // clear system pill
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
            .background(
                if (selected) AmberAccent.copy(alpha = 0.10f)
                else Color.White.copy(alpha = 0.04f)
            )
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) AmberAccent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
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
            tint = if (selected) AmberAccent else CreteDS.textSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = CreteDS.typeNavTab,
                color = if (selected) CreamText else CreteDS.textSecondary,
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
