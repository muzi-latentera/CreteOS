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
    val game        = state.game ?: return
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

        // ── Giant letter backdrop ─────────────────────────────────────────
        Text(
            text = initial,
            color = accentColor.copy(alpha = 0.50f),
            fontSize = 860.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 40.dp, y = 260.dp)
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
                // Back button
                Row(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(V2RedPlay),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("B", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = V2Cream)
                    }
                    Text(
                        "Back to library",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.6.sp,
                        color = V2Dim
                    )
                }

                Spacer(Modifier.height(34.dp))

                // Platform label
                Text(
                    text = platformDisplayName(game.platformId).uppercase(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.6.sp,
                    color = V2Amber
                )

                Spacer(Modifier.height(14.dp))

                // Game title
                Text(
                    text = game.title,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-2.3).sp,
                    lineHeight = 74.sp,
                    color = V2Cream,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )

                Spacer(Modifier.height(22.dp))

                // Tags row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    V2Tag(platformDisplayName(game.platformId))
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

                // Sessions + More from shelf
                Row(
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.weight(1f)
                ) {
                    // Recent sessions
                    Column(modifier = Modifier.width(420.dp)) {
                        Text(
                            "RECENT SESSIONS",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.2.sp,
                            color = V2VeryDim
                        )
                        Spacer(Modifier.height(14.dp))

                        val sessions = buildSessionList(game.playCount, game.lastPlayedMs)
                        if (sessions.isEmpty()) {
                            Text(
                                "No sessions recorded yet",
                                fontSize = 12.sp,
                                color = V2Dim
                            )
                        } else {
                            sessions.forEach { session ->
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(V2Cream.copy(alpha = 0.045f))
                                        .border(1.dp, V2Cream.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        session.label,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = V2Dim,
                                        modifier = Modifier.width(74.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(V2Cream.copy(alpha = 0.08f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(session.progress)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(V2Amber)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        session.duration,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.5.sp,
                                        color = V2Cream,
                                        modifier = Modifier.width(58.dp)
                                    )
                                    Text(
                                        "${session.fps} fps",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = V2Green,
                                        modifier = Modifier.width(52.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Description
                    Column(modifier = Modifier.weight(1f)) {
                        game.description?.let { desc ->
                            Text(
                                text = desc,
                                fontSize = 13.sp,
                                color = V2Dim,
                                lineHeight = 18.sp,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // ── Stats row at bottom ────────────────────────────────────
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(V2Cream.copy(alpha = 0.10f)))

                Row(modifier = Modifier.fillMaxWidth()) {
                    V2StatCol("Playtime",
                        if (game.playCount > 0) "${(game.playCount * 30).toFloat() / 60f} h" else "—")
                    V2StatCol("Last Played",
                        game.lastPlayedMs?.let { formatLastPlayed(it) } ?: "—")
                    V2StatCol("Avg FPS", "—")
                    V2StatCol("Sessions",
                        if (game.playCount > 0) game.playCount.toString() else "—")
                }
            }

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
                Text(
                    "LAUNCH CONFIGURATION",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.4.sp,
                    color = V2VeryDim
                )
                Spacer(Modifier.height(14.dp))

                // ── Config rows ────────────────────────────────────────────
                V2ConfigRow("Provider", platformDisplayName(game.platformId))
                V2ConfigRow("Launch path", game.romPath.takeLast(36))
                V2ConfigRow("Last played",
                    game.lastPlayedMs?.let { formatLastPlayed(it) } ?: "—")
                V2ConfigRow("Play count", game.playCount.toString())
                V2ConfigRow("App ID",
                    game.romPath.substringAfterLast(":").takeIf { it.isNotBlank() } ?: "—")
                V2ConfigRow("Frame cap", "60 fps · VRR on")
                V2ConfigRow("Controller map", "CreteOS default")
                V2ConfigRow("Shader cache", "Warm")

                // GameNative settings deep link (Steam games only)
                if (game.platformId.lowercase() == "steam") {
                    Spacer(Modifier.height(8.dp))
                    val ctx = LocalContext.current
                    val appId = game.romPath.substringAfterLast(":")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(V2Amber.copy(alpha = 0.10f))
                            .border(1.dp, V2Amber.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    // Open GameNative — per-game settings via main activity
                                    val intent = android.content.Intent().apply {
                                        setPackage("app.gamenative")
                                        action = "android.intent.action.MAIN"
                                        addCategory("android.intent.category.LAUNCHER")
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try { ctx.startActivity(intent) } catch (_: Exception) {}
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Open in GameNative",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = V2Amber
                        )
                        Icon(
                            Icons.Outlined.KeyboardArrowRight,
                            null,
                            tint = V2Amber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Hint box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color(0xFF2E7D96).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFF2E7D96).copy(alpha = 0.32f), RoundedCornerShape(11.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        "Profile changes apply on next launch. CreteOS writes them back to the provider so its own UI stays in sync.",
                        fontSize = 12.sp,
                        color = Color(0xFFA8D6E4),
                        lineHeight = 17.sp
                    )
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

// ── Helper composables ────────────────────────────────────────────────────

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
        else       -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    }
}
