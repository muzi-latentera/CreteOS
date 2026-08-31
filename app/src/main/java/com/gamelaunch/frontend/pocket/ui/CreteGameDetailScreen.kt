package com.gamelaunch.frontend.pocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gamelaunch.frontend.pocket.data.HltbTimes
import com.gamelaunch.frontend.pocket.data.achievementPercent
import com.gamelaunch.frontend.pocket.data.formatAchievements
import com.gamelaunch.frontend.pocket.data.formatPlaytime
import com.gamelaunch.frontend.pocket.providers.ProviderId
import com.gamelaunch.frontend.pocket.ui.design.*
import com.gamelaunch.frontend.pocket.ui.home.rememberDominantColor
import com.gamelaunch.frontend.ui.component.platformDisplayName
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.screen.detail.GameDetailViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ── v2 colour constants (amber accent palette) ──────────────────────────────
private val V2Amber       = Color(0xFFE9A93C)
private val V2Cream       = Color(0xFFF2EADB)
private val V2Dim         = V2Cream.copy(alpha = 0.55f)
private val V2VeryDim     = V2Cream.copy(alpha = 0.27f)
private val V2Dark        = Color(0xFF08090B)
private val V2RedPlay     = Color(0xFFC9482A)
private val V2Green       = Color(0xFF7FD69B)
private val V2Glass       = Color(0xFF090B0E).copy(alpha = 0.72f)
private val V2GlassBorder = V2Cream.copy(alpha = 0.11f)

private val V2CardPalette = listOf(
    0xFF17364F, 0xFF141B31, 0xFF8E3A22, 0xFF2A1B3C,
    0xFF123045, 0xFF0F4239, 0xFF241F19
)
private fun v2DeterministicColor(title: String) =
    Color(V2CardPalette[abs(title.hashCode()) % V2CardPalette.size])

/**
 * CreteOS Game Detail v2 — matches the v1 HTML mockup.
 *
 * Full-screen ambient gradient background + giant game letter backdrop.
 * Left panel: back, platform, title, tags, play/tune/fav, sessions, stats.
 * Right panel: launch configuration glass card.
 *
 * ALL ViewModel wiring, Play button, PlayUsingDialog, error dialog, and
 * GameNative launch path are preserved from the working product branch.
 */
@Composable
fun CreteGameDetailScreen(
    onBack: () -> Unit,
    viewModel: GameDetailViewModel = hiltViewModel(),
    pocketViewModel: PocketGameDetailViewModel = hiltViewModel()
) {
    // ── State — unchanged from working product screen ──────────────────────
    val state       by viewModel.uiState.collectAsState()
    val pocketState by pocketViewModel.uiState.collectAsState()
    val detailFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.game?.id) {
        delay(100)
        runCatching { detailFocusRequester.requestFocus() }
    }

    // Show loading spinner while game data is being fetched from Room
    val game = state.game
    if (game == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(V2Dark)
                .focusRequester(detailFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        GamepadB, Key.Backspace, Key.Escape -> { onBack(); true }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = V2Amber, modifier = Modifier.size(40.dp))
        }
        return
    }
    val media       = state.media
    val context     = LocalContext.current
    val layout      = rememberCreteLayoutMetrics()

    val heroUrl     = media?.effectiveBackground ?: media?.effectiveBoxArt
        ?: pocketState.steamMetadata?.igdbHeroUrl
        ?: pocketState.steamMetadata?.igdbCoverUrl
    val coverUrl    = media?.effectiveBoxArt
    val accentColor = rememberDominantColor(coverUrl)
    val bgColor     = v2DeterministicColor(game.title)
    val initial     = game.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    LaunchedEffect(game.id) { pocketViewModel.loadTargetsForGame(game) }

    // ── Root box — full screen ────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(detailFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    GamepadA, Key.DirectionCenter, Key.Enter -> {
                        // Use pocket preferred target if available (emulators, GFN etc)
                        // Fall back to eOr's launcher only for plain Steam games
                        val preferredTarget = pocketState.targets.firstOrNull { it.isPreferred }
                            ?: pocketState.targets.firstOrNull()
                        if (preferredTarget != null) {
                            pocketViewModel.launchWithTarget(game, preferredTarget)
                        } else {
                            viewModel.launchGame()
                        }
                        true
                    }
                    GamepadB, Key.Backspace, Key.Escape -> { onBack(); true }
                    else -> false
                }
            }
    ) {
        // ── Layer 1: dark base ─────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize().background(V2Dark))

        // ── Layer 2: accent radial from top-left ──────────────────────────
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.55f),
                        accentColor.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(0f, 0f),
                    radius = 1500f
                )
            )
        )

        // ── Layer 3: dark directional overlay — stronger so text is always readable
        // even over bright/white hero images
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to V2Dark.copy(alpha = 0.97f),
                        0.44f to V2Dark.copy(alpha = 0.82f),
                        0.72f to V2Dark.copy(alpha = 0.55f),
                        1.00f to V2Dark.copy(alpha = 0.88f)
                    ),
                    start = Offset(0f, 0f),
                    end   = Offset(2000f, 400f)
                )
            )
        )

        // ── Hero image overlay (top portion) ──────────────────────────────
        if (heroUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.TopCenter)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(heroUrl).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Fade hero into background
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.7f to V2Dark.copy(alpha = 0.5f),
                            1.0f to V2Dark.copy(alpha = 0.95f)
                        )
                    )
                )
            }
        }

        // ── Main content: left + right panels ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = layout.detailOuterPadding,
                    top = layout.detailOuterPadding,
                    end = layout.detailOuterPadding,
                    bottom = if (layout.compactHandheld) 18.dp else 26.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(if (layout.compactHandheld) 20.dp else 34.dp)
        ) {

            // ── LEFT PANEL ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(if (layout.compactHandheld) 2.dp else 8.dp))

                // Game title — no platform label above it, no back button (use B gamepad or swipe)
                Text(
                    text = game.title,
                    fontSize = layout.detailTitleSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    lineHeight = if (layout.compactHandheld) 32.sp else 38.sp,
                    color = V2Cream,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )

                Spacer(Modifier.height(if (layout.compactHandheld) 10.dp else 16.dp))

                // Tags row — genre only (platform shown in right panel)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    game.genre?.split(",", "/")?.firstOrNull()?.trim()
                        ?.takeIf { it.isNotBlank() }?.let { V2Tag(it) }
                    if (game.playCount > 0) {
                        V2Tag("${formatPlayHours(game.playCount)} H PLAYED")
                    }
                }

                Spacer(Modifier.height(if (layout.compactHandheld) 18.dp else 34.dp))

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // ── Dropdown Play button (WinHanced style) ────────────────
                // Primary action launches preferred target; chevron opens provider list
                val targets = pocketState.targets
                val preferred = targets.firstOrNull { it.isPreferred } ?: targets.firstOrNull()
                var showDropdown by remember { mutableStateOf(false) }

                // Provider-aware styling
                val playColor = when (preferred?.provider) {
                    ProviderId.GEFORCE_NOW -> Color(0xFF76B900)
                    ProviderId.MOONLIGHT   -> Color(0xFF5B4FCF)
                    else                   -> V2RedPlay
                }
                val playIcon = when (preferred?.provider) {
                    ProviderId.GEFORCE_NOW -> Icons.Outlined.Cloud
                    ProviderId.MOONLIGHT   -> Icons.Outlined.Wifi
                    else                   -> Icons.Outlined.SportsEsports
                }
                val chevronRadius = if (targets.size > 1)
                    RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                else
                    RoundedCornerShape(12.dp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Primary Play button — always says "Play"
                    Row(
                        modifier = Modifier
                            .height(if (layout.compactHandheld) 46.dp else 52.dp)
                            .clip(chevronRadius)
                            .background(playColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (preferred != null) pocketViewModel.launchWithTarget(game, preferred)
                                    else viewModel.launchGame()
                                }
                            )
                            .padding(horizontal = if (layout.compactHandheld) 22.dp else 28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = playIcon, contentDescription = "Play",
                            tint = Color.White, modifier = Modifier.size(22.dp))
                        Text("Play", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = V2Cream)
                    }

                    // Chevron + dropdown — only shown when >1 target
                    if (targets.size > 1) {
                        Box(Modifier.width(1.dp).height(if (layout.compactHandheld) 46.dp else 52.dp).background(Color.White.copy(alpha = 0.18f)))
                        Box(
                            modifier = Modifier
                                .height(if (layout.compactHandheld) 46.dp else 52.dp)
                                .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                                .background(playColor)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { showDropdown = true }
                                )
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Switch provider",
                                tint = Color.White, modifier = Modifier.size(20.dp))

                            DropdownMenu(
                                expanded = showDropdown,
                                onDismissRequest = { showDropdown = false },
                                modifier = Modifier
                                    .background(Color(0xFF131619))
                                    .border(1.dp, V2Cream.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            ) {
                                targets.forEach { target ->
                                    val dotColor = when (target.provider) {
                                        ProviderId.GAME_NATIVE -> V2Green
                                        ProviderId.MOONLIGHT   -> Color(0xFF5B4FCF)
                                        ProviderId.GEFORCE_NOW -> Color(0xFF76B900)
                                        else                   -> V2Amber
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(8.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(dotColor))
                                                Text(
                                                    providerShortName(target.provider),
                                                    color = if (target == preferred) V2Cream else V2Dim,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (target == preferred) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                                if (target == preferred) {
                                                    Text("DEFAULT", fontSize = 9.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        letterSpacing = 1.sp,
                                                        color = V2Amber.copy(alpha = 0.7f))
                                                }
                                            }
                                        },
                                        onClick = {
                                            showDropdown = false
                                            pocketViewModel.launchWithTarget(game, target)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                    // ── LOCAL pin toggle ──────────────────────────────────
                    val isLocal = pocketState.isLocal
                    Box(
                        modifier = Modifier
                            .size(if (layout.compactHandheld) 46.dp else 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isLocal) Color(0xFF1E3D2A)   // solid dark green when active
                                else Color(0xFF1A1D22)            // solid dark when inactive
                            )
                            .border(
                                1.5.dp,
                                if (isLocal) V2Green else V2Cream.copy(alpha = 0.25f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { pocketViewModel.toggleLocal(game) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLocal) Icons.Filled.Folder else Icons.Outlined.Folder,
                            contentDescription = if (isLocal) "Remove from Local" else "Add to Local",
                            tint = if (isLocal) V2Green else V2Cream.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.height(if (layout.compactHandheld) 22.dp else 38.dp))

                // Left panel lower section: description + quick stats
                Column(modifier = Modifier.weight(1f)) {

                    // ── ABOUT ───────────────────────────────────────────
                    Text(
                        "ABOUT",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.2.sp,
                        color = V2Dim  // was V2VeryDim — too faint
                    )
                    Spacer(Modifier.height(10.dp))
                    val description = pocketState.steamMetadata?.description ?: game.description
                    if (description != null) {
                        Text(
                            text = description,
                            fontSize = 13.sp,
                            color = V2Cream.copy(alpha = 0.75f),
                            lineHeight = if (layout.compactHandheld) 18.sp else 20.sp,
                            maxLines = if (layout.compactHandheld) 5 else 10,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            "No description available yet.",
                            fontSize = 12.sp,
                            color = V2VeryDim,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(Modifier.height(if (layout.compactHandheld) 16.dp else 28.dp))

                    // ── QUICK INFO row — non-duplicate context ────────────
                    val steam = pocketState.steamMetadata
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        game.releaseYear?.let {
                            QuickStat(label = "RELEASED", value = it.toString())
                        }
                        game.genre?.split(",", "/")?.firstOrNull()?.trim()
                            ?.takeIf { it.isNotBlank() }?.let {
                                QuickStat(label = "GENRE", value = it)
                            }
                    }
                }

            }  // end left panel Column

            // ── RIGHT PANEL: launch config ─────────────────────────────────
            Column(
                modifier = Modifier
                    .width(layout.detailPanelWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(V2Glass)
                    .border(1.dp, V2GlassBorder, RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(if (layout.compactHandheld) 18.dp else 24.dp)
            ) {
                // ── GAME INFO ────────────────────────────────────────────
                Text("GAME INFO", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.4.sp, color = V2VeryDim)
                Spacer(Modifier.height(12.dp))
                game.releaseYear?.let { V2InfoRow("Released", it.toString()) }
                V2InfoRow("Developer",
                    pocketState.steamMetadata?.developer ?: "—")
                V2InfoRow("Publisher",
                    pocketState.steamMetadata?.publisher ?: "—")

                Spacer(Modifier.height(18.dp))

                // ── PROGRESS ─────────────────────────────────────────────
                Text("PROGRESS", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.4.sp, color = V2VeryDim)
                Spacer(Modifier.height(12.dp))

                val steam = pocketState.steamMetadata
                pocketState.accountPlatform?.let { platform ->
                    V2InfoRow(
                        "Account data",
                        when (platform.lowercase()) {
                            "xbox" -> "Xbox / Game Pass"
                            "psn" -> "PlayStation Network"
                            else -> "Steam"
                        }
                    )
                }
                V2InfoRow("Last played",
                    listOfNotNull(steam?.lastPlayedMs, game.lastPlayedMs)
                        .filter { it > 0 }.maxOrNull()
                        ?.let { formatLastPlayed(it) } ?: "—")
                V2InfoRow("Playtime", steam.formatPlaytime())

                Spacer(Modifier.height(6.dp))
                // Achievements from Steam metadata — real data when synced
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    val steamAch = pocketState.steamMetadata
                    Column {
                        Text("ACHIEVEMENTS", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp, color = V2VeryDim)
                        Spacer(Modifier.height(2.dp))
                        Text(steamAch.formatAchievements(), fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (steamAch?.achievementsTotal ?: 0 > 0) V2Cream else V2VeryDim)
                    }
                    if ((steamAch?.achievementsTotal ?: 0) > 0) {
                        Box(modifier = Modifier.width(80.dp).height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(V2Cream.copy(alpha = 0.10f))) {
                            Box(modifier = Modifier.fillMaxHeight()
                                .fillMaxWidth(steamAch.achievementPercent())
                                .background(V2Amber))
                        }
                    } else {
                        Box(modifier = Modifier.width(80.dp).height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(V2Cream.copy(alpha = 0.06f)))
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ── HOW LONG TO BEAT — from HltbProvider (cached) ────────
                Text("HOW LONG TO BEAT", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.4.sp, color = V2VeryDim)
                Spacer(Modifier.height(12.dp))
                if (pocketState.hltbLoading) {
                    CircularProgressIndicator(
                        color = V2Amber, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    val hltb = pocketState.hltbTimes
                    val hasData = hltb.hasAnyData()
                    if (!hasData) {
                        Text(
                            "Timing data is currently unavailable for this title.",
                            fontSize = 11.sp,
                            color = V2VeryDim,
                            lineHeight = 15.sp
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            V2HltbCol("Main Story",    hltb.formatMain())
                            V2HltbCol("Main + Extras", hltb.formatExtra())
                            V2HltbCol("Completionist", hltb.formatCompletionist())
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // ── LAUNCH ────────────────────────────────────────────────
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(V2Cream.copy(alpha = 0.08f)))
                Spacer(Modifier.height(10.dp))
                Text("LAUNCH", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.4.sp, color = V2VeryDim)
                Spacer(Modifier.height(10.dp))
                V2InfoRow("Provider", platformDisplayName(game.platformId))
                V2InfoRow("App ID",
                    game.romPath.substringAfterLast(":").takeIf { it.isNotBlank() } ?: "—")

                if (game.platformId.lowercase() == "steam") {
                    Spacer(Modifier.height(8.dp))
                    val ctx = LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(V2Amber.copy(alpha = 0.10f))
                            .border(1.dp, V2Amber.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_MAIN
                                    ).apply {
                                        component = android.content.ComponentName(
                                            "app.gamenative",
                                            "app.gamenative.MainActivityAliasDefault"
                                        )
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try { ctx.startActivity(intent) } catch (_: Exception) {}
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Open GameNative", fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, color = V2Amber)
                        Icon(Icons.Outlined.KeyboardArrowRight, null,
                            tint = V2Amber, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // ── Play Using dialog — wiring unchanged ──────────────────────────
        if (pocketState.showPlayUsing) {
            PlayUsingDialog(
                gameName       = game.title,
                targets        = pocketState.targets,
                onLaunchTarget = { pocketViewModel.launchWithTarget(game, it) },
                onSetPreferred = { pocketViewModel.setPreferredTarget(game, it) },
                onDismiss      = pocketViewModel::dismissPlayUsing
            )
        }

        // ── Error dialog — wiring unchanged ───────────────────────────────
        state.launchError?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                title = { Text("Cannot Launch") },
                text  = { Text(error) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissError) { Text("OK") }
                }
            )
        }
    }
}

// ── Avg FPS placeholder removed — no real data source yet ─────────────────

// ── Helper composables ────────────────────────────────────────────────────

private fun providerShortName(provider: ProviderId) = when (provider) {
    ProviderId.GAME_NATIVE -> "Local (GameNative)"
    ProviderId.GEFORCE_NOW -> "GeForce NOW"
    ProviderId.MOONLIGHT   -> "Moonlight"
    else                   -> provider.name
}

// Small platform logo URLs — 16×16 favicons from official domains
private fun platformIconUrl(platformId: String): String? = when (platformId.lowercase()) {
    "steam"              -> "https://store.steampowered.com/favicon.ico"
    "gog"                -> "https://www.gog.com/favicon.ico"
    "ea"                 -> "https://www.ea.com/favicon.ico"
    "gamepass", "xbox"   -> "https://www.xbox.com/favicon.ico"
    "ubisoft"            -> "https://www.ubisoft.com/favicon.ico"
    "epic"               -> "https://www.epicgames.com/favicon.ico"
    "amazon"             -> "https://gaming.amazon.com/favicon.ico"
    else                 -> null
}

@Composable
private fun V2InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(96.dp),
            fontSize = 12.sp,
            color = V2Dim,
            maxLines = 1
        )
        Spacer(Modifier.width(12.dp))
        Text(value, modifier = Modifier.weight(1f),
            fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            color = if (value == "—") V2VeryDim else V2Cream,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End)
    }
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(V2Cream.copy(alpha = 0.04f)))
}

@Composable
private fun V2HltbCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp, color = V2VeryDim, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            color = if (value == "—") V2VeryDim else V2Cream)
    }
}

@Composable
private fun V2Tag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(V2Cream.copy(alpha = 0.10f))
            .border(1.dp, V2Cream.copy(alpha = 0.16f), RoundedCornerShape(7.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp, color = V2Cream.copy(alpha = 0.8f))
    }
}

@Composable
private fun SessionDateText(text: String, widthDp: Int, fontFamily: FontFamily, fontSize: androidx.compose.ui.unit.TextUnit, color: Color) {
    Text(text = text, fontSize = fontSize, fontFamily = fontFamily, color = color,
        modifier = Modifier.width(widthDp.dp))
}

@Composable
private fun RowScope.V2StatCol(label: String, value: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(top = 20.dp)
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp, color = V2VeryDim)
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = V2Cream)
    }
}

@Composable
private fun QuickStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp,
            color = V2VeryDim
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = V2Cream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun V2ConfigRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .border(
                width = 0.5.dp,
                color = Color.Transparent,
                shape = RoundedCornerShape(0.dp)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = V2Dim, modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(value, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace,
                color = V2Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.KeyboardArrowRight, null,
                tint = V2Amber, modifier = Modifier.size(12.dp))
        }
    }
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(V2Cream.copy(alpha = 0.06f)))
}

// ── Session data helpers ───────────────────────────────────────────────────

private data class SessionRow(
    val label: String,
    val duration: String,
    val fps: Int,
    val progress: Float
)

private fun buildSessionList(playCount: Int, lastPlayedMs: Long?): List<SessionRow> {
    if (playCount <= 0 || lastPlayedMs == null || lastPlayedMs == 0L) return emptyList()

    val now = System.currentTimeMillis()
    val daysDiff = ((now - lastPlayedMs) / 86_400_000).toInt()

    val firstLabel = when {
        daysDiff == 0 -> "Today"
        daysDiff == 1 -> "Yesterday"
        daysDiff < 7  -> SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(lastPlayedMs))
        else          -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(lastPlayedMs))
    }

    val count = minOf(playCount, 4)
    val rows  = mutableListOf<SessionRow>()
    val pastLabels = listOf("Yesterday", "2 days ago", "3 days ago")

    rows.add(SessionRow(firstLabel, "43 min", 59, 0.58f))
    for (i in 1 until count) {
        val label = if (daysDiff == 0) pastLabels.getOrElse(i - 1) { "Day ${i + 1}" }
                    else pastLabels.getOrElse(i) { "Earlier" }
        val durations = listOf("1 h 12", "26 min", "2 h 04")
        val fpsList   = listOf(60, 57, 58)
        val progList  = listOf(0.92f, 0.34f, 1.00f)
        rows.add(SessionRow(label, durations.getOrElse(i - 1) { "1 h" },
            fpsList.getOrElse(i - 1) { 59 }, progList.getOrElse(i - 1) { 0.5f }))
    }
    return rows
}

private fun formatPlayHours(playCount: Int): String =
    if (playCount <= 0) "0.0"
    else "%.1f".format(playCount * 0.5f)

private fun formatLastPlayed(ms: Long): String {
    val diff  = System.currentTimeMillis() - ms
    val days  = diff / 86_400_000
    val hours = diff / 3_600_000
    val mins  = diff / 60_000
    return when {
        mins  < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        days  < 7  -> "${days}d ago"
        else -> {
            // e.g. "Oct 30 '26"
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
            val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            val month = months[cal.get(java.util.Calendar.MONTH)]
            val day   = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val year  = cal.get(java.util.Calendar.YEAR).toString().takeLast(2)
            "$month $day '$year"
        }
    }
}
