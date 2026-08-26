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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.pocket.ui.design.*
import com.gamelaunch.frontend.pocket.ui.home.rememberDominantColor
import com.gamelaunch.frontend.ui.component.platformDisplayName
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.screen.detail.GameDetailViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.min

// ══════════════════════════════════════════════════════════════════════════
// V1 DESIGN CONSTANTS (detail screen specific)
// ══════════════════════════════════════════════════════════════════════════

private val AmberAccent = Color(0xFFE9A93C)
private val CreamText = Color(0xFFF2E8D5)
private val DimCream = CreamText.copy(alpha = 0.55f)
private val VeryDimCream = CreamText.copy(alpha = 0.27f)
private val DarkBase = Color(0xFF0A0D10)
private val GreenFps = Color(0xFF7FD69B)
private val RedPlay = Color(0xFFC9482A)
private val TealHint = Color(0xFF2E7D96)

// Glass panel backgrounds
private val GlassPanel = Color(0xFF090B0E).copy(alpha = 0.72f)
private val GlassBorder = CreamText.copy(alpha = 0.11f)

// Session row styling
private val SessionRowBg = CreamText.copy(alpha = 0.045f)
private val SessionRowBorder = CreamText.copy(alpha = 0.06f)

// Chip styling
private val ChipBg = CreamText.copy(alpha = 0.08f)
private val ChipBorder = CreamText.copy(alpha = 0.13f)

// Deterministic colour palette
private val DetailCardPalette = listOf(
    0xFF17364F, 0xFF141B31, 0xFF8E3A22, 0xFF2A1B3C,
    0xFF123045, 0xFF0F4239, 0xFF241F19
)

private fun deterministicColor(title: String): Color =
    Color(DetailCardPalette[abs(title.hashCode()) % DetailCardPalette.size])

/**
 * CreteOS v1 Game Detail Screen — landscape full screen layout
 *
 * ┌─────────────────────────────────────────────────────┬──────────────────┐
 * │ LEFT PANEL (flex:1)                                 │ RIGHT PANEL 420dp│
 * │ [B] Back to library                                 │ Launch config    │
 * │ PLATFORM (amber mono)                               │ (glass panel)    │
 * │ GAME TITLE (76sp 800 weight)                        │                  │
 * │ [tag] [tag] [tag]                                   │ Provider: ...    │
 * │ [Play button] [Tune performance] [♡]                │ Launch path: ... │
 * │                                                     │ Last played: ... │
 * │ Recent sessions            More from shelf          │ Play count: ...  │
 * │ Today      ████ 43min 59fps  [card][card][card]     │ App ID: ...      │
 * │ Yesterday  ████ 1h12  60fps                         │                  │
 * │                                                     │ [hint box]       │
 * │ ─────────────────────────────────────────────────── │                  │
 * │ Playtime  Last Played  Avg FPS  Sessions            │                  │
 * └─────────────────────────────────────────────────────┴──────────────────┘
 */
@Composable
fun CreteGameDetailScreen(
    onBack: () -> Unit,
    viewModel: GameDetailViewModel = hiltViewModel(),
    pocketViewModel: PocketGameDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pocketState by pocketViewModel.uiState.collectAsState()
    val game = state.game ?: return
    val media = state.media

    val coverUrl = media?.effectiveBoxArt
    val accentColor = rememberDominantColor(coverUrl) ?: deterministicColor(game.title)

    LaunchedEffect(game.id) { pocketViewModel.loadTargetsForGame(game) }

    // Full screen with gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    GamepadA, Key.Enter -> { viewModel.launchGame(); true }
                    GamepadB, Key.Backspace, Key.Escape -> { onBack(); true }
                    else -> false
                }
            }
    ) {
        // ── BACKGROUND LAYERS ──────────────────────────────────────────────

        // Layer 1: Radial gradient from accent colour (top-left)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.6f),
                            accentColor.copy(alpha = 0.2f),
                            DarkBase
                        ),
                        center = Offset(0f, 0f),
                        radius = 1400f
                    )
                )
        )

        // Layer 2: Dark gradient overlay — linear 100deg, 96%→70%→42%→86%
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to DarkBase.copy(alpha = 0.96f),
                            0.33f to DarkBase.copy(alpha = 0.70f),
                            0.66f to DarkBase.copy(alpha = 0.42f),
                            1.0f to DarkBase.copy(alpha = 0.86f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.MAX_VALUE, Float.MAX_VALUE * 0.17f) // ~100deg
                    )
                )
        )

        // Layer 3: Giant game letter — right side
        val initial = game.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        Text(
            text = initial,
            fontSize = 860.sp,
            fontWeight = FontWeight.ExtraBold,
            color = accentColor.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-100).dp, y = 260.dp)
        )

        // ── MAIN LAYOUT: LEFT PANEL + RIGHT PANEL ──────────────────────────

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 40.dp)
        ) {
            // ── LEFT PANEL (flex: 1) ───────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Back button row
                BackButtonRow(onBack = onBack)

                Spacer(Modifier.height(32.dp))

                // Platform label
                Text(
                    text = platformDisplayName(game.platformId).uppercase(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = AmberAccent,
                    letterSpacing = 2.86.sp // 0.26em
                )

                Spacer(Modifier.height(8.dp))

                // Game title
                Text(
                    text = game.title,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CreamText,
                    letterSpacing = (-2.28).sp, // -0.03em
                    lineHeight = 74.sp, // 0.98
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(16.dp))

                // Genre/tag chips
                GenreChipsRow(genre = game.genre)

                Spacer(Modifier.height(24.dp))

                // Action row: Play, Tune performance, Heart
                ActionButtonsRow(
                    onPlay = { viewModel.launchGame() },
                    onTunePerformance = { /* TODO */ },
                    onFavourite = { /* TODO */ },
                    showDropdown = pocketState.targets.size > 1,
                    onDropdown = { pocketViewModel.showPlayUsing() }
                )

                Spacer(Modifier.height(40.dp))

                // Recent sessions + More from shelf
                RecentSessionsAndShelfRow(
                    game = game,
                    media = media
                )

                Spacer(Modifier.weight(1f))

                // Stats row at bottom
                StatsRow(game = game)
            }

            Spacer(Modifier.width(32.dp))

            // ── RIGHT PANEL (420dp) ────────────────────────────────────────
            LaunchConfigPanel(
                game = game,
                modifier = Modifier.width(420.dp)
            )
        }

        // ── DIALOGS ────────────────────────────────────────────────────────

        // Play Using dialog
        if (pocketState.showPlayUsing) {
            PlayUsingDialog(
                gameName = game.title,
                targets = pocketState.targets,
                onLaunchTarget = { pocketViewModel.launchWithTarget(game, it) },
                onSetPreferred = { pocketViewModel.setPreferredTarget(game, it) },
                onDismiss = pocketViewModel::dismissPlayUsing
            )
        }

        state.launchError?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                title = { Text("Cannot Launch") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissError) { Text("OK") }
                }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// BACK BUTTON
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun BackButtonRow(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack
            )
    ) {
        // B button circle
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(RedPlay),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "B",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = "Back to library",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = DimCream
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// GENRE CHIPS
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun GenreChipsRow(genre: String?) {
    if (genre.isNullOrBlank()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        genre.split(",", "/", "|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(5)
            .forEach { tag ->
                DetailChip(text = tag)
            }
    }
}

@Composable
private fun DetailChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ChipBg)
            .border(1.dp, ChipBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = CreamText
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// ACTION BUTTONS ROW
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun ActionButtonsRow(
    onPlay: () -> Unit,
    onTunePerformance: () -> Unit,
    onFavourite: () -> Unit,
    showDropdown: Boolean,
    onDropdown: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play button — red with A badge and amber glow
        Row(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(RedPlay)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = if (showDropdown) onDropdown else onPlay
                )
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // A button circle badge
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = "PLAY",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }

        // Tune performance — outline button
        Row(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, CreamText.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTunePerformance
                )
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tune performance",
                fontSize = 14.sp,
                color = CreamText
            )
        }

        // Heart/favourite — square outline button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, CreamText.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFavourite
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.FavoriteBorder,
                contentDescription = "Favourite",
                tint = CreamText,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// RECENT SESSIONS + MORE FROM SHELF
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun RecentSessionsAndShelfRow(
    game: Game,
    media: GameMedia?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        // Recent sessions column
        Column(modifier = Modifier.weight(1f)) {
            // Section title
            Text(
                text = "RECENT SESSIONS",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = VeryDimCream,
                letterSpacing = 2.2.sp // 0.22em
            )

            Spacer(Modifier.height(12.dp))

            // Session rows
            val sessions = generatePlaceholderSessions(game)
            if (sessions.isEmpty()) {
                Text(
                    text = "No sessions recorded yet",
                    fontSize = 12.sp,
                    color = DimCream,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sessions.forEach { session ->
                        SessionRow(session = session)
                    }
                }
            }
        }

        // More from shelf column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "MORE FROM SHELF",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = VeryDimCream,
                letterSpacing = 2.2.sp
            )

            Spacer(Modifier.height(12.dp))

            // Placeholder cards — would show related games
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CreamText.copy(alpha = 0.05f))
                            .border(1.dp, CreamText.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}

// Session data class
private data class SessionInfo(
    val dateLabel: String,
    val durationMinutes: Int,
    val avgFps: Int,
    val barProgress: Float // 0f-1f for progress bar width
)

private fun generatePlaceholderSessions(game: Game): List<SessionInfo> {
    if (game.playCount <= 0 || game.lastPlayedMs == null || game.lastPlayedMs == 0L) {
        return emptyList()
    }

    val now = System.currentTimeMillis()
    val lastPlayed = game.lastPlayedMs
    val daysDiff = ((now - lastPlayed) / 86_400_000).toInt()

    // Format the most recent session date
    val recentLabel = when {
        daysDiff == 0 -> "Today"
        daysDiff == 1 -> "Yesterday"
        daysDiff < 7 -> {
            val cal = Calendar.getInstance().apply { timeInMillis = lastPlayed }
            SimpleDateFormat("EEE d", Locale.getDefault()).format(cal.time)
        }
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(lastPlayed))
    }

    // Generate 1-4 placeholder sessions
    val sessionCount = min(game.playCount, 4)
    val sessions = mutableListOf<SessionInfo>()

    // Most recent session (real date)
    sessions.add(
        SessionInfo(
            dateLabel = recentLabel,
            durationMinutes = (30..120).random(),
            avgFps = (55..60).random(),
            barProgress = (0.5f..1f).random()
        )
    )

    // Additional placeholder sessions
    val dayLabels = listOf("Yesterday", "Mon 24", "Sun 23", "Sat 22")
    for (i in 1 until sessionCount) {
        sessions.add(
            SessionInfo(
                dateLabel = dayLabels.getOrElse(i) { "Day $i" },
                durationMinutes = (20..140).random(),
                avgFps = (54..60).random(),
                barProgress = (0.3f..0.9f).random()
            )
        )
    }

    return sessions
}

private fun ClosedFloatingPointRange<Float>.random(): Float {
    return start + (Math.random() * (endInclusive - start)).toFloat()
}

@Composable
private fun SessionRow(session: SessionInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SessionRowBg)
            .border(1.dp, SessionRowBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date label (74dp)
        Text(
            text = session.dateLabel,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = DimCream,
            modifier = Modifier.width(74.dp)
        )

        // Progress bar (flex 1)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(DarkBase)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(session.barProgress)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AmberAccent)
            )
        }

        Spacer(Modifier.width(12.dp))

        // Duration (58dp right-aligned)
        Text(
            text = formatDuration(session.durationMinutes),
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            color = CreamText,
            modifier = Modifier.width(58.dp)
        )

        // FPS (52dp right-aligned, green)
        Text(
            text = "${session.avgFps}fps",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = GreenFps,
            modifier = Modifier.width(52.dp)
        )
    }
}

private fun formatDuration(minutes: Int): String {
    return if (minutes >= 60) {
        val h = minutes / 60
        val m = minutes % 60
        "${h}h${if (m > 0) "%02d".format(m) else ""}"
    } else {
        "${minutes}min"
    }
}

// ══════════════════════════════════════════════════════════════════════════
// STATS ROW
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun StatsRow(game: Game) {
    // Top border separator
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(CreamText.copy(alpha = 0.1f))
    )

    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Playtime
        StatColumn(
            label = "Playtime",
            value = if (game.playCount > 0) "${game.playCount * 30} min" else "—"
        )

        StatDivider()

        // Last Played
        StatColumn(
            label = "Last Played",
            value = formatLastPlayed(game.lastPlayedMs)
        )

        StatDivider()

        // Avg FPS
        StatColumn(
            label = "Avg FPS",
            value = "—"
        )

        StatDivider()

        // Sessions
        StatColumn(
            label = "Sessions",
            value = if (game.playCount > 0) game.playCount.toString() else "—"
        )
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = VeryDimCream,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = CreamText
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(CreamText.copy(alpha = 0.12f))
    )
}

// ══════════════════════════════════════════════════════════════════════════
// RIGHT PANEL — LAUNCH CONFIG
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun LaunchConfigPanel(
    game: Game,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassPanel)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(24.dp)
    ) {
        // Panel title
        Text(
            text = "LAUNCH CONFIG",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = VeryDimCream,
            letterSpacing = 2.4.sp // 0.24em
        )

        Spacer(Modifier.height(20.dp))

        // Config rows
        ConfigRow(label = "Provider", value = platformDisplayName(game.platformId))
        ConfigRow(label = "Launch path", value = game.romPath.take(40))
        ConfigRow(label = "Last played", value = formatLastPlayed(game.lastPlayedMs))
        ConfigRow(label = "Play count", value = game.playCount.toString())
        ConfigRow(label = "App ID", value = extractAppId(game.romPath))

        Spacer(Modifier.weight(1f))

        // Hint box at bottom
        HintBox()
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = DimCream
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = value,
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace,
                color = CreamText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
            )

            Icon(
                imageVector = Icons.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = AmberAccent,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    // Row separator
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(CreamText.copy(alpha = 0.06f))
    )
}

private fun extractAppId(romPath: String): String {
    // Extract app ID from paths like "steam:123456" or return "—"
    return when {
        romPath.startsWith("steam:") -> romPath.removePrefix("steam:")
        romPath.startsWith("package:") -> romPath.removePrefix("package:").take(20)
        else -> "—"
    }
}

@Composable
private fun HintBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TealHint.copy(alpha = 0.12f))
            .border(1.dp, TealHint.copy(alpha = 0.32f), RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Press Y to configure launch options and performance settings for this game.",
            fontSize = 12.sp,
            color = CreamText.copy(alpha = 0.8f),
            lineHeight = 18.sp
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// HELPER FUNCTIONS
// ══════════════════════════════════════════════════════════════════════════

private fun formatLastPlayed(ms: Long?): String {
    if (ms == null || ms == 0L) return "—"
    val diff = System.currentTimeMillis() - ms
    val days = diff / 86_400_000
    val hours = diff / 3_600_000
    val mins = diff / 60_000
    return when {
        mins < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    }
}
