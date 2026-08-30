package com.gamelaunch.frontend.pocket.ui

import android.content.Intent
import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.pocket.data.SteamMetadataDao
import com.gamelaunch.frontend.pocket.data.SteamMetadataEntity
import com.gamelaunch.frontend.pocket.data.SteamMetadataSync
import com.gamelaunch.frontend.pocket.data.IgdbSeedData
import com.gamelaunch.frontend.pocket.data.formatPlaytime
import com.gamelaunch.frontend.pocket.ui.design.*
import com.gamelaunch.frontend.pocket.ui.home.RedditNewsViewModel
import com.gamelaunch.frontend.pocket.ui.library.LibraryViewModel
import com.gamelaunch.frontend.ui.screen.home.HomeViewModel
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.input.GamepadL2
import com.gamelaunch.frontend.ui.input.GamepadR2
import com.gamelaunch.frontend.ui.input.GamepadX
import com.gamelaunch.frontend.ui.input.GamepadY
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.Normalizer
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
private val GameFocusBorderWidth = 1.dp

internal fun Context.launchInstalledPackage(packageName: String) {
    packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }
}

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
    "gamepass", "xbox"   -> Color(0xFF1A1A1A)  // dark neutral — Xbox green box looks weird
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
    val vectorIconRes = com.gamelaunch.frontend.ui.component.platformIcon(platformId)

    // Per-platform icon fill — what fraction of the fixed 24dp container the icon occupies
    val iconSize = when (platformId.lowercase()) {
        "gc"                   -> 24.dp
        "switch"               -> 23.dp
        "ps2", "ps3"           -> 23.dp
        "wiiu"                 -> 23.dp
        "3ds", "n3ds"          -> 23.dp
        "gba", "nds",
        "psp", "psvita"        -> 22.dp
        else                   -> 16.dp
    }
    // PS2 disc logo looks better rotated 90°
    val rotation = when (platformId.lowercase()) {
        "ps2" -> 90f
        else  -> 0f
    }

    // Fixed container — icon size varies but the box stays the same
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(platformPillColor(platformId).copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            // 1. Store platforms with good favicons — use the favicon
            iconUrl != null -> {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = label,
                    modifier = Modifier.size(
                        if (platformId.lowercase() in setOf("gamepass","xbox")) 13.dp else 16.dp
                    ),
                    contentScale = ContentScale.Fit
                )
            }
            // 2. Emulation systems and platforms with vector/PNG icons
            vectorIconRes != null -> {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = vectorIconRes),
                    contentDescription = label,
                    contentScale = ContentScale.Inside,
                    modifier = Modifier
                        .size(iconSize)
                        .then(if (rotation != 0f) Modifier.rotate(rotation) else Modifier),
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
    val distinctFallbackUrl = fallbackUrl?.takeUnless { it == artworkUrl }
    var primaryArtworkLoaded by remember(artworkUrl) { mutableStateOf(false) }
    var fallbackArtworkLoaded by remember(distinctFallbackUrl) { mutableStateOf(false) }

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
                        .border(GameFocusBorderWidth, AmberAccent, RoundedCornerShape(12.dp))
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
        // Always start with a readable title background. A non-null path may still point at a
        // removed scraper file, so artwork only suppresses the title after Coil loads it.
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
        if (distinctFallbackUrl != null) {
            AsyncImage(
                model = distinctFallbackUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { fallbackArtworkLoaded = true },
                onError = { fallbackArtworkLoaded = false }
            )
        }
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { primaryArtworkLoaded = true },
                onError = { primaryArtworkLoaded = false }
            )
        }

        val artworkVisible = primaryArtworkLoaded || fallbackArtworkLoaded

        // Bottom scrim + text — only shown when no artwork at all
        if (!artworkVisible) {
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
    val layout = rememberCreteLayoutMetrics()

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

    // Controller focus: hero first, then the Jump Back In rail.
    val railGames = remember(jumpBackInGames) { jumpBackInGames.drop(1) }
    val pocketFocusState by homeViewModel.pocketFocusState.collectAsState()
    val homeFocusRow = pocketFocusState.row
    val jumpBackInFocusIndex = pocketFocusState.railIndex
    val homeFocusRequester = remember { FocusRequester() }
    val homeListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var homeHasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { homeFocusRequester.requestFocus() }
    }

    // Navigation keeps the Home destination composed while detail is on top. Re-acquire its
    // controller focus every time it resumes, otherwise focus remains attached to the removed
    // detail destination and the remembered selection appears frozen after pressing B.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            var tries = 0
            do {
                runCatching { homeFocusRequester.requestFocus() }
                delay(80)
            } while (!homeHasFocus && tries++ < 14)
        }
    }
    LaunchedEffect(railGames.size) {
        if (railGames.isEmpty()) homeViewModel.setPocketFocusRow(0)
        homeViewModel.setPocketRailFocusIndex(
            jumpBackInFocusIndex.coerceIn(0, (railGames.size - 1).coerceAtLeast(0))
        )
    }

    LazyColumn(
        state = homeListState,
        modifier = modifier
            .fillMaxSize()
            .focusRequester(homeFocusRequester)
            .onFocusChanged { homeHasFocus = it.hasFocus }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        homeViewModel.setPocketFocusRow(0)
                        scope.launch { homeListState.animateScrollToItem(0) }
                        true
                    }
                    Key.DirectionDown -> {
                        if (railGames.isNotEmpty()) {
                            homeViewModel.setPocketFocusRow(1)
                            scope.launch { homeListState.animateScrollToItem(1) }
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (homeFocusRow == 1) {
                            homeViewModel.setPocketRailFocusIndex(
                                (jumpBackInFocusIndex - 1).coerceAtLeast(0)
                            )
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (homeFocusRow == 1) {
                            homeViewModel.setPocketRailFocusIndex(
                                (jumpBackInFocusIndex + 1)
                                    .coerceAtMost((railGames.size - 1).coerceAtLeast(0))
                            )
                        }
                        true
                    }
                    GamepadA, Key.DirectionCenter, Key.Enter -> {
                        if (homeFocusRow == 0) {
                            heroGame?.let { onGameClick(it.id) }
                        } else {
                            railGames.getOrNull(jumpBackInFocusIndex)?.let { onGameClick(it.id) }
                        }
                        true
                    }
                    else -> false
                }
            },
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // ── HERO TILE ──────────────────────────────────────────────────────
        item {
            if (heroGame != null) {
                HeroTile(
                    game = heroGame,
                    media = heroMedia,
                    steamMeta = heroSteamMeta,
                    focused = homeFocusRow == 0,
                    onClick = { onGameClick(heroGame.id) },
                    modifier = Modifier.padding(
                        start = layout.horizontalPadding,
                        end = layout.horizontalPadding,
                        top = layout.homeHeroTopPadding,
                        bottom = if (layout.compactHandheld) 8.dp else 12.dp
                    )
                )
            } else {
                // Empty state hero
                Box(
                    modifier = Modifier
                        .padding(
                            horizontal = layout.horizontalPadding,
                            vertical = if (layout.compactHandheld) 16.dp else 24.dp
                        )
                        .fillMaxWidth()
                        .height(layout.homeHeroHeight)
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
                    games = railGames, // Skip hero game
                    mediaForGames = libState.mediaForGames,
                    rowFocused = homeFocusRow == 1,
                    focusedIndex = jumpBackInFocusIndex,
                    onFocusChange = {
                        homeViewModel.setPocketFocusRow(1)
                        homeViewModel.setPocketRailFocusIndex(it)
                    },
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
    val layout = rememberCreteLayoutMetrics()
    val bgColor = deterministicColor(game.title)

    // Platform label for chips
    val platformLabel = platformDisplayLabel(game.platformId)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(layout.homeHeroHeight)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (focused) {
                    Modifier.border(GameFocusBorderWidth, AmberAccent, RoundedCornerShape(20.dp))
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
                .width(if (layout.compactHandheld) 420.dp else 500.dp)
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
                .padding(
                    start = layout.heroContentPadding,
                    top = layout.heroContentPadding,
                    bottom = layout.heroContentPadding
                )
                .widthIn(max = if (layout.compactHandheld) 360.dp else 420.dp)
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

            Spacer(Modifier.height(if (layout.compactHandheld) 7.dp else 12.dp))

            // Game title
            Text(
                text = game.title,
                fontSize = layout.heroTitleSize,
                fontWeight = FontWeight.Bold,
                color = CreamText,
                letterSpacing = (-0.5).sp,
                lineHeight = layout.heroTitleLineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(if (layout.compactHandheld) 10.dp else 16.dp))

                // Identity row: the store owns the game; LOCAL is availability. Launch providers
                // such as GeForce NOW and Moonlight belong in Play Using, not ownership badges.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroChip(text = platformLabel)

                    if (steamMeta?.isLocal == true &&
                        game.platformId.lowercase() !in setOf("local", "gamenative")
                    ) {
                        HeroChip(
                            text = "LOCAL",
                            textColor = GreenSync,
                            borderColor = GreenSync.copy(alpha = 0.3f)
                        )
                    }
                }

            Spacer(Modifier.height(if (layout.compactHandheld) 14.dp else 24.dp))

            // Button row
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Red PLAY button
                Row(
                    modifier = Modifier
                        .height(if (layout.compactHandheld) 40.dp else 44.dp)
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

        // Roomy layouts can afford the profile panel. On a handheld it competes
        // with the title and artwork; the same information remains on game detail.
        if (!layout.compactHandheld) Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = layout.heroContentPadding)
                .width(layout.heroInfoWidth)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0A0D10).copy(alpha = 0.66f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .padding(layout.heroInfoPadding)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(if (layout.compactHandheld) 8.dp else 14.dp)
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(84.dp),
            fontSize = 13.sp,
            color = DimCream,
            maxLines = 1
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = CreamText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
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
    val layout = rememberCreteLayoutMetrics()
    val newsState by newsViewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val posts = newsState.posts

    if (posts.isEmpty()) return

    Column(modifier = Modifier.padding(top = if (layout.compactHandheld) 16.dp else 24.dp)) {
        // Rail header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = layout.horizontalPadding, vertical = 8.dp),
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
            contentPadding = PaddingValues(horizontal = layout.horizontalPadding),
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
    val layout = rememberCreteLayoutMetrics()
    Box(
        modifier = Modifier
            .width(if (layout.compactHandheld) 260.dp else 300.dp)
            .height(if (layout.compactHandheld) 140.dp else 160.dp)
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
    rowFocused: Boolean,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onGameClick: (Long) -> Unit
) {
    if (games.isEmpty()) return
    val layout = rememberCreteLayoutMetrics()
    val railState = rememberLazyListState()

    LaunchedEffect(focusedIndex, games.size) {
        if (focusedIndex in games.indices) railState.animateScrollToItem(focusedIndex)
    }

    Column(
        modifier = Modifier.padding(top = if (layout.compactHandheld) 14.dp else 24.dp)
    ) {
        // Title row: label + fading line + count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = layout.horizontalPadding, vertical = 8.dp),
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
            state = railState,
            contentPadding = PaddingValues(horizontal = layout.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(if (layout.compactHandheld) 10.dp else 12.dp),
            modifier = Modifier.padding(top = if (layout.compactHandheld) 4.dp else 8.dp)
        ) {
            itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
                val media = mediaForGames[game.id]
                val seedCover = IgdbSeedData.coverUrlFor(
                    game.romPath.substringAfterLast(":").takeIf { it.isNotBlank() } ?: ""
                )
                V1GameCard(
                    artworkUrl = media?.effectiveBoxArt,
                    fallbackUrl = media?.boxArtRemoteUrl?.takeUnless { it == media.effectiveBoxArt }
                        ?: seedCover,
                    title = game.title,
                    platformId = game.platformId,
                    focused = rowFocused && index == focusedIndex,
                    width = layout.libraryMinCardWidth,
                    height = layout.libraryMinCardWidth * (203f / 152f),
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

internal fun matchesLibrarySearch(title: String, query: String): Boolean {
    val normalizedQuery = normalizeLibrarySearchText(query)
    if (normalizedQuery.isBlank()) return true
    val titleWords = normalizeLibrarySearchText(title).split(' ').filter { it.isNotBlank() }
    return normalizedQuery.split(' ').filter { it.isNotBlank() }.all { queryWord ->
        titleWords.any { titleWord -> titleWord.contains(queryWord) }
    }
}

private fun normalizeLibrarySearchText(value: String): String = Normalizer
    .normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()
    .replace(Regex("^(the|a|an)\\s+"), "")

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
    val activeFilter = runCatching { LibraryFilter.valueOf(state.activeFilterName) }
        .getOrDefault(initialFilter)
    val focusedGameId = state.focusedGameId
    val showSources = state.showSources
    val showSearch = state.showSearch
    val searchText = state.searchText
    var debouncedSearch by remember { mutableStateOf(searchText) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val layout = rememberCreteLayoutMetrics()
    var sourceFocusIndex by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = state.gridFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = state.gridFirstVisibleItemScrollOffset
    )

    LaunchedEffect(libraryViewModel, initialFilter) {
        libraryViewModel.initializeFilter(initialFilter.name)
    }
    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().collect { (index, offset) ->
            libraryViewModel.setGridPosition(index, offset)
        }
    }

    LaunchedEffect(searchText) {
        delay(250)
        debouncedSearch = searchText
    }
    LaunchedEffect(showSearch) {
        if (showSearch) {
            delay(100)
            searchFocusRequester.requestFocus()
        }
    }

    val providerPackages = setOf(
        "com.nvidia.geforcenow", "com.limelight", "com.nytimes.crossword",
        "app.gamenative", "gamehub.lite"
    )

    val filteredGames = remember(state.games, state.localAppIds, state.cloudGameKeys, activeFilter, debouncedSearch) {
        val base = state.games.filter { game ->
            if (game.platformId == "android" && game.romPath.startsWith("package:")) {
                game.romPath.removePrefix("package:") !in providerPackages
            } else true
        }
        val platformFiltered = when (activeFilter) {
            LibraryFilter.ALL      -> base
            LibraryFilter.LOCAL    -> base.filter {
                val appId = it.romPath.substringAfterLast(":")
                appId in state.localAppIds
            }
            LibraryFilter.OWNED    -> base.filter { it.platformId in setOf("steam", "gog", "epic", "ea", "gamepass", "xbox", "ubisoft", "amazon") }
            LibraryFilter.STREAMING -> base.filter { it.platformId == "moonlight" }
            LibraryFilter.CLOUD    -> base.filter {
                it.platformId == "gfn" ||
                    it.romPath in state.cloudGameKeys ||
                    SteamMetadataSync.GFN_VERIFIED.containsKey(it.romPath.substringAfterLast(":"))
            }
            LibraryFilter.ANDROID  -> base.filter { it.platformId == "android" }
        }
        platformFiltered.filter { matchesLibrarySearch(it.title, debouncedSearch) }
    }

    val controllerFocusRequester = remember { FocusRequester() }
    val controllerScope = rememberCoroutineScope()
    val gridGap = if (layout.compactHandheld) 10.dp else 12.dp
    val availableGridWidth = layout.screenWidth.value - (layout.horizontalPadding.value * 2f)
    val controllerColumns = (
        (availableGridWidth + gridGap.value) /
            (layout.libraryMinCardWidth.value + gridGap.value)
        ).toInt().coerceAtLeast(1)
    val gridCardWidth = (
        (availableGridWidth - gridGap.value * (controllerColumns - 1)) / controllerColumns
        ).dp
    val gridCardHeight = gridCardWidth * (203f / 152f)

    LaunchedEffect(filteredGames, focusedGameId, showSources) {
        if (!showSources && filteredGames.isNotEmpty() &&
            filteredGames.none { it.id == focusedGameId }) {
            libraryViewModel.setFocusedGame(filteredGames.first().id)
        }
    }
    LaunchedEffect(showSearch, showSources) {
        if (!showSearch) {
            delay(80)
            runCatching { controllerFocusRequester.requestFocus() }
        }
    }

    fun moveLibraryFocus(delta: Int): Boolean {
        if (filteredGames.isEmpty() || showSources) return false
        val current = filteredGames.indexOfFirst { it.id == focusedGameId }.coerceAtLeast(0)
        val next = (current + delta).coerceIn(0, filteredGames.lastIndex)
        libraryViewModel.setFocusedGame(filteredGames[next].id)
        controllerScope.launch { gridState.animateScrollToItem(next) }
        return true
    }

    fun cycleLibraryFilter(delta: Int) {
        val filters = LibraryFilter.entries
        val index = filters.indexOf(activeFilter).coerceAtLeast(0)
        libraryViewModel.selectFilter(filters[(index + delta + filters.size) % filters.size].name)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = layout.topClearance)
            .focusRequester(controllerFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

                if (showSearch) {
                    return@onKeyEvent when (event.key) {
                        GamepadB, GamepadX, Key.Escape -> {
                            libraryViewModel.setSearchVisible(false)
                            true
                        }
                        else -> false
                    }
                }

                if (showSources) {
                    return@onKeyEvent when (event.key) {
                        Key.DirectionLeft -> {
                            sourceFocusIndex = (sourceFocusIndex - 1).coerceAtLeast(0); true
                        }
                        Key.DirectionRight -> {
                            sourceFocusIndex = (sourceFocusIndex + 1).coerceAtMost(CRETE_SOURCES.lastIndex); true
                        }
                        Key.DirectionUp -> {
                            sourceFocusIndex = (sourceFocusIndex - 3).coerceAtLeast(0); true
                        }
                        Key.DirectionDown -> {
                            sourceFocusIndex = (sourceFocusIndex + 3).coerceAtMost(CRETE_SOURCES.lastIndex); true
                        }
                        GamepadA, Key.DirectionCenter, Key.Enter -> {
                            CRETE_SOURCES.getOrNull(sourceFocusIndex)?.packageName
                                ?.let(context::launchInstalledPackage)
                            true
                        }
                        GamepadB, GamepadY, Key.Escape -> {
                            libraryViewModel.setSourcesVisible(false); true
                        }
                        else -> false
                    }
                }

                when (event.key) {
                    Key.DirectionLeft -> moveLibraryFocus(-1)
                    Key.DirectionRight -> moveLibraryFocus(1)
                    Key.DirectionUp -> moveLibraryFocus(-controllerColumns)
                    Key.DirectionDown -> moveLibraryFocus(controllerColumns)
                    GamepadA, Key.DirectionCenter, Key.Enter -> {
                        filteredGames.firstOrNull { it.id == focusedGameId }
                            ?.let { onGameClick(it.id) }
                        true
                    }
                    GamepadL2 -> { cycleLibraryFilter(-1); true }
                    GamepadR2 -> { cycleLibraryFilter(1); true }
                    GamepadX -> { libraryViewModel.setSearchVisible(true); true }
                    GamepadY -> { libraryViewModel.setSourcesVisible(!showSources); true }
                    GamepadB, Key.Escape -> if (showSources) {
                        libraryViewModel.setSourcesVisible(false)
                        true
                    } else false
                    else -> false
                }
            }
    ) {
        // Filter chips row + count + sort
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = layout.horizontalPadding, vertical = if (layout.compactHandheld) 5.dp else 8.dp),
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
                        onClick = { libraryViewModel.selectFilter(filter.name) }
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
                        onClick = { libraryViewModel.setSourcesVisible(!showSources) }
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

            // Search toggle button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (showSearch) AmberAccent.copy(alpha = 0.16f)
                        else CreamText.copy(alpha = 0.05f)
                    )
                    .border(
                        1.dp,
                        if (showSearch) AmberAccent.copy(alpha = 0.5f)
                        else CreamText.copy(alpha = 0.10f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            libraryViewModel.setSearchVisible(!showSearch)
                            if (showSearch) debouncedSearch = ""
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = if (showSearch) "Close search" else "Search library",
                    tint = if (showSearch) AmberAccent else DimCream,
                    modifier = Modifier.size(18.dp)
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

        if (showSearch) {
            OutlinedTextField(
                value = searchText,
                onValueChange = libraryViewModel::setSearchText,
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = DimCream)
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = {
                            libraryViewModel.setSearchText("")
                            debouncedSearch = ""
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = DimCream)
                        }
                    }
                },
                placeholder = { Text("Search games…", color = DimCream) },
                textStyle = androidx.compose.ui.text.TextStyle(color = CreamText, fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = layout.horizontalPadding, vertical = 4.dp)
                    .focusRequester(searchFocusRequester)
            )
        }

        Spacer(Modifier.height(if (showSearch) 8.dp else if (layout.compactHandheld) 8.dp else 16.dp))

        // Sources view or game grid
        if (showSources) {
            SourcesGridView(focusedIndex = sourceFocusIndex)
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
                columns = GridCells.Fixed(controllerColumns),
                state = gridState,
                contentPadding = PaddingValues(horizontal = layout.horizontalPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(gridGap),
                verticalArrangement = Arrangement.spacedBy(gridGap),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredGames, key = { it.id }) { game ->
                    val media = state.mediaForGames[game.id]
                    val seedCover = IgdbSeedData.coverUrlFor(
                        game.romPath.substringAfterLast(":").takeIf { it.isNotBlank() } ?: ""
                    )
                    V1GameCard(
                        artworkUrl = media?.effectiveBoxArt,
                        fallbackUrl = media?.boxArtRemoteUrl?.takeUnless { it == media.effectiveBoxArt }
                            ?: seedCover,
                        title = game.title,
                        platformId = game.platformId,
                        focused = game.id == focusedGameId,
                        width = gridCardWidth,
                        height = gridCardHeight,
                        onClick = {
                            libraryViewModel.setFocusedGame(game.id)
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
    val enabled: Boolean = true,
    val packageName: String? = null
)

private val CRETE_SOURCES = listOf(
    SourceCard("GameNative", "Windows runtime", "N", 0xFF2E7D96, "Steam library", "Handles PC games via GameNative bridge.", packageName = "app.gamenative"),
    SourceCard("Moonlight", "PC streaming", "M", 0xFF1A4A7A, "PC games", "Stream games from your gaming PC.", packageName = "com.limelight"),
    SourceCard("GeForce NOW", "Cloud streaming", "G", 0xFF76B900, "Cloud library", "NVIDIA cloud gaming service.", packageName = "com.nvidia.geforcenow"),
    SourceCard("RetroArch", "Emulator frontend", "R", 0xFFC9482A, "ROMs", "Multi-system emulation via RetroArch.", packageName = "com.retroarch.aarch64"),
    SourceCard("Android", "Native apps", "A", 0xFF3DDC84, "Android games", "Games installed directly on device.", packageName = "com.ayaneo.gamelauncher"),
    SourceCard("GameHub", "Windows runtime", "H", 0xFF3E6FB8, "PC games", "Alternative Windows compatibility layer.")
)

@Composable
private fun SourcesGridView(focusedIndex: Int) {
    val layout = rememberCreteLayoutMetrics()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = layout.horizontalPadding)
    ) {
        Text(
            text = "Everything CreteOS can see",
            fontSize = if (layout.compactHandheld) 18.sp else 22.sp,
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
        Spacer(Modifier.height(if (layout.compactHandheld) 12.dp else 20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(if (layout.compactHandheld) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (layout.compactHandheld) 12.dp else 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(CRETE_SOURCES) { index, source ->
                SourceCardItem(source = source, focused = index == focusedIndex)
            }
        }
    }
}

@Composable
private fun SourceCardItem(source: SourceCard, focused: Boolean) {
    var enabled by remember { mutableStateOf(source.enabled) }
    val context = LocalContext.current
    val layout = rememberCreteLayoutMetrics()

    Column(
        modifier = Modifier
            .height(layout.sourceCardHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F1317))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) AmberAccent else CreamText.copy(alpha = 0.09f),
                RoundedCornerShape(14.dp)
            )
            .padding(if (layout.compactHandheld) 14.dp else 18.dp)
    ) {
        // Header: icon + name + kind + toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon box
            Box(
                modifier = Modifier
                    .size(if (layout.compactHandheld) 36.dp else 42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(source.bgColor).copy(alpha = 0.9f))
                    .then(
                        source.packageName?.let { packageName ->
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { context.launchInstalledPackage(packageName) }
                        } ?: Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = source.initial,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                if (source.packageName != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = "Open ${source.name}",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .size(10.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    fontSize = if (layout.compactHandheld) 14.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CreamText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = source.kind.uppercase(),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp,
                    color = DimCream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

        Spacer(Modifier.height(if (layout.compactHandheld) 9.dp else 14.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(CreamText.copy(alpha = 0.08f)))
        Spacer(Modifier.height(if (layout.compactHandheld) 9.dp else 14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = source.count,
                fontSize = if (layout.compactHandheld) 15.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = CreamText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "READY",
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                color = DimCream.copy(alpha = 0.65f),
                maxLines = 1
            )
        }
        Spacer(Modifier.height(if (layout.compactHandheld) 5.dp else 8.dp))
        Text(
            text = source.note,
            fontSize = 12.sp,
            color = DimCream,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
    val context = LocalContext.current
    val settingsFocusRequester = remember { FocusRequester() }
    val layout = rememberCreteLayoutMetrics()

    val categories = listOf(
        SettingsCategoryItem("PC & Streaming", "GameNative, Moonlight, GeForce NOW", Icons.Outlined.Stream),
        SettingsCategoryItem("Libraries", "Game sources and folders", Icons.AutoMirrored.Outlined.LibraryBooks),
        SettingsCategoryItem("Display", "XREAL, external display, resolution", Icons.Outlined.Monitor),
        SettingsCategoryItem("Appearance", "Theme, layout, backgrounds", Icons.Outlined.Palette),
        SettingsCategoryItem("General", "Emulators, metadata, achievements", Icons.Outlined.Settings)
    )

    fun activateSelectedCategory() {
        when (selectedIndex) {
            0 -> onOpenProviders()
            1 -> onOpenSettings()
            2 -> onOpenDisplay()
            3, 4 -> onOpenSettings()
            5 -> context.launchInstalledPackage("com.ayaneo.home")
        }
    }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { settingsFocusRequester.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = layout.horizontalPadding)
            .padding(
                top = layout.topClearance,
                bottom = if (layout.compactHandheld) CreteDS.spaceL else CreteDS.spaceXL
            )
            .focusRequester(settingsFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                        true
                    }
                    Key.DirectionDown -> {
                        selectedIndex = (selectedIndex + 1).coerceAtMost(categories.size)
                        true
                    }
                    Key.DirectionRight, GamepadA, Key.DirectionCenter, Key.Enter -> {
                        activateSelectedCategory()
                        true
                    }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(if (layout.compactHandheld) CreteDS.spaceL else CreteDS.spaceXL)
    ) {
        LazyColumn(
            modifier = Modifier
                .width(layout.settingsRailWidth)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(CreteDS.spaceS)
        ) {
            itemsIndexed(categories) { index, item ->
                GlassSettingsCard(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { selectedIndex = index }
                )
            }
            item {
                GlassSettingsCard(
                    item = SettingsCategoryItem("AYAHome", "AYANEO home screen", Icons.Outlined.Home),
                    selected = selectedIndex == categories.size,
                    onClick = {
                        selectedIndex = categories.size
                        context.launchInstalledPackage("com.ayaneo.home")
                    }
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
                5 -> {
                    SettingsPanelTitle("AYAHome")
                    SettingsPanelBody("Press A to open the AYANEO home screen.")
                }
            }
        }
    }
}

@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val layout = rememberCreteLayoutMetrics()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CreteDS.radiusL))
            .background(Color(0x28FFFFFF))
            .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(CreteDS.radiusL))
            .padding(layout.panelPadding),
        content = content
    )
}

@Composable
private fun GlassSettingsCard(item: SettingsCategoryItem, selected: Boolean, onClick: () -> Unit) {
    val layout = rememberCreteLayoutMetrics()
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
            .padding(
                horizontal = CreteDS.spaceL,
                vertical = if (layout.compactHandheld) CreteDS.spaceS else CreteDS.spaceM
            ),
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
