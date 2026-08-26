package com.gamelaunch.frontend.pocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.gamelaunch.frontend.pocket.ui.design.*
import com.gamelaunch.frontend.pocket.ui.home.rememberDominantColor
import com.gamelaunch.frontend.ui.component.platformDisplayName
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.screen.detail.GameDetailViewModel
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

    // Show loading spinner while game data is being fetched from Room
    val game = state.game
    if (game == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(V2Dark)
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

    val heroUrl     = media?.effectiveBackground ?: media?.effectiveBoxArt
    val coverUrl    = media?.effectiveBoxArt
    val accentColor = rememberDominantColor(coverUrl)
    val bgColor     = v2DeterministicColor(game.title)
    val initial     = game.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    LaunchedEffect(game.id) { pocketViewModel.loadTargetsForGame(game) }

    // ── Root box — full screen ────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    GamepadA, Key.Enter                 -> { viewModel.launchGame(); true }
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

        // ── Layer 3: dark directional overlay ─────────────────────────────
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to V2Dark.copy(alpha = 0.96f),
                        0.44f to V2Dark.copy(alpha = 0.70f),
                        0.72f to V2Dark.copy(alpha = 0.42f),
                        1.00f to V2Dark.copy(alpha = 0.86f)
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
                .padding(40.dp, 40.dp, 40.dp, 26.dp),
            horizontalArrangement = Arrangement.spacedBy(34.dp)
        ) {

            // ── LEFT PANEL ─────────────────────────────────────────────────
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Spacer(Modifier.height(8.dp))

                // Game title — no platform label above it, no back button (use B gamepad or swipe)
                Text(
                    text = game.title,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 38.sp,
                    color = V2Cream,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )

                Spacer(Modifier.height(16.dp))

                // Tags row — genre only (platform shown in right panel)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    game.genre?.split(",", "/")?.firstOrNull()?.trim()
                        ?.takeIf { it.isNotBlank() }?.let { V2Tag(it) }
                    if (game.playCount > 0) {
                        V2Tag("${formatPlayHours(game.playCount)} H PLAYED")
                    }
                }

                Spacer(Modifier.height(34.dp))

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play button — red, prominent
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(V2RedPlay)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (pocketState.targets.size > 1) pocketViewModel.showPlayUsing()
                                    else viewModel.launchGame()
                                }
                            )
                            .padding(horizontal = 30.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.28f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = V2Cream)
                        }
                        Text(
                            "Play",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = V2Cream
                        )
                    }

                    // Tune performance — outline
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, V2Cream.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 24.dp, vertical = 15.dp)
                    ) {
                        Text(
                            "Game details",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                            color = V2Cream.copy(alpha = 0.85f)
                        )
                    }

                    // Favourite icon
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, V2Cream.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favourite",
                            tint = V2Cream.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(38.dp))

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
                            lineHeight = 20.sp,
                            maxLines = 5,
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

                    Spacer(Modifier.height(28.dp))

                    // ── QUICK STATS row ──────────────────────────────────
                    // Shows non-duplicate info: playtime, release year, genre
                    val steam = pocketState.steamMetadata
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Playtime
                        if (steam != null && steam.playtimeMinutes > 0) {
                            QuickStat(
                                label = "PLAYTIME",
                                value = steam.formatPlaytime()
                            )
                        }
                        // Release year
                        game.releaseYear?.let {
                            QuickStat(label = "RELEASED", value = it.toString())
                        }
                        // Genre
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
                    .width(420.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(V2Glass)
                    .border(1.dp, V2GlassBorder, RoundedCornerShape(16.dp))
                    .padding(24.dp)
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
                V2InfoRow("Last played",
                    steam?.lastPlayedMs?.let { formatLastPlayed(it) }
                        ?: game.lastPlayedMs?.let { formatLastPlayed(it) }
                        ?: "—")
                V2InfoRow("Playtime", steam.formatPlaytime())
                V2InfoRow("Sessions",
                    if (game.playCount > 0) game.playCount.toString() else "—")
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
                    val hasData = hltb.formatMain() != "—"
                    if (!hasData) {
                        Text(
                            "Currently unavailable — HLTB blocks direct requests. Check howlongtobeat.com",
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

@Composable
private fun V2InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = V2Dim)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            color = if (value == "—") V2VeryDim else V2Cream,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
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
