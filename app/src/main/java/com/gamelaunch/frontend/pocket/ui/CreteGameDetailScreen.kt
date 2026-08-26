package com.gamelaunch.frontend.pocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gamelaunch.frontend.pocket.ui.design.*
import com.gamelaunch.frontend.pocket.ui.home.DynamicBackground
import com.gamelaunch.frontend.pocket.ui.home.rememberDominantColor
import com.gamelaunch.frontend.ui.component.platformDisplayName
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.screen.detail.GameDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * CreteOS Game Detail — WinHanced layout.
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │   [hero artwork — full width, ~40% height, bleeds into gradient] │
 * ├──────────────────────────────────────────────────────────────────┤
 * │  [Play ▾]  TOTAL PLAY TIME  │  ACHIEVEMENTS  │  SESSIONS    ⚙  │
 * ├──────────────────────────────────────────────────────────────────┤
 * │  Game Title                                      Platform ● ST  │
 * │  Description of game text…                                      │
 * │  Release date: Jan 1, 2020   Developer: Studio                  │
 * │  [Action] [Indie] [RPG]                                         │
 * │                                                                  │
 * │  MAIN STORY   MAIN+EXTRAS   COMPLETIONIST                       │
 * │   3.3 hrs       11.7 hrs      34.8 hrs                          │
 * └──────────────────────────────────────────────────────────────────┘
 */
@Composable
fun CreteGameDetailScreen(
    onBack: () -> Unit,
    viewModel: GameDetailViewModel = hiltViewModel(),
    pocketViewModel: PocketGameDetailViewModel = hiltViewModel()
) {
    val state       by viewModel.uiState.collectAsState()
    val pocketState by pocketViewModel.uiState.collectAsState()
    val game        = state.game ?: return
    val media       = state.media
    val context     = LocalContext.current

    val heroUrl     = media?.effectiveBackground ?: media?.effectiveBoxArt
    val coverUrl    = media?.effectiveBoxArt
    val accentColor = rememberDominantColor(coverUrl)

    LaunchedEffect(game.id) { pocketViewModel.loadTargetsForGame(game) }

    DynamicBackground(
        accentColor = accentColor,
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
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Hero banner — full width, ~40% height ──────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.42f)
            ) {
                if (heroUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(heroUrl).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Soft bottom fade into ambient gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.6f to accentColor.copy(alpha = 0.12f),
                                1.0f to Color(0xFF080B14).copy(alpha = 0.97f)
                            )
                        )
                )
                // Back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 8.dp)
                ) {
                    Icon(Icons.Outlined.ArrowBack, "Back", tint = CreteDS.textPrimary)
                }
            }

            // ── Stats bar ─────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 12.dp)
            ) {
                CretePlayButton(
                    onClick = { viewModel.launchGame() },
                    onDropdownClick = if (pocketState.targets.size > 1)
                        ({ pocketViewModel.showPlayUsing() }) else null,
                    focused = true
                )

                Spacer(Modifier.width(20.dp))

                DetailStatBlock("TOTAL PLAY TIME",
                    if (game.playCount == 0) "0 Hours" else "${game.playCount} Sessions")
                DetailDivider()
                DetailStatBlock("ACHIEVEMENTS", "— / —")
                DetailDivider()
                DetailStatBlock("SESSIONS",
                    if (game.playCount == 0) "—" else "${game.playCount}")

                Spacer(Modifier.weight(1f))

                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Settings, "Options",
                        tint = CreteDS.textSecondary.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp))
                }
            }

            // Separator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(0.5.dp)
                    .background(Color(0x33FFFFFF))
            )

            Spacer(Modifier.height(20.dp))

            // ── Game info — title, description, metadata ──────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                // Title + platform
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = game.title,
                        color = CreteDS.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Text(
                            text = platformDisplayName(game.platformId),
                            style = CreteDS.typeMeta,
                            color = CreteDS.textSecondary
                        )
                        CreteProviderBadge(platformId = game.platformId)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Description
                game.description?.let { desc ->
                    Text(
                        text = desc,
                        color = CreteDS.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // Release date + developer on one line
                val metaParts = buildList {
                    game.releaseYear?.let { add("Release date: $it") }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString("   ·   "),
                        color = CreteDS.textSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Genre chips
                game.genre?.let { genre ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        genre.split(",", "/", "|")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .take(5)
                            .forEach { tag -> CreteGenreChip(label = tag) }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // ── How Long To Beat row ───────────────────────────────────
                // Placeholder values — wire to HLTB API in phase 2
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HltbBlock("MAIN STORY", "—")
                    DetailDivider()
                    HltbBlock("MAIN + EXTRAS", "—")
                    DetailDivider()
                    HltbBlock("COMPLETIONIST", "—")
                }
            }
        }

        // Play Using dialog
        if (pocketState.showPlayUsing) {
            PlayUsingDialog(
                gameName       = game.title,
                targets        = pocketState.targets,
                onLaunchTarget = { pocketViewModel.launchWithTarget(game, it) },
                onSetPreferred = { pocketViewModel.setPreferredTarget(game, it) },
                onDismiss      = pocketViewModel::dismissPlayUsing
            )
        }

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

// ── Shared detail stat blocks ──────────────────────────────────────────────

@Composable
private fun DetailStatBlock(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 14.dp)
    ) {
        Text(label, color = CreteDS.textSecondary, fontSize = 9.sp,
            fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = CreteDS.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun HltbBlock(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 20.dp)
    ) {
        Text(label, color = CreteDS.textSecondary, fontSize = 9.sp,
            fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = CreteDS.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun DetailDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(0.5.dp)
            .height(28.dp)
            .background(Color(0x44FFFFFF))
    )
}

private fun formatLastPlayed(ms: Long): String {
    val diff  = System.currentTimeMillis() - ms
    val days  = diff / 86_400_000
    val hours = diff / 3_600_000
    val mins  = diff / 60_000
    return when {
        mins < 60  -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7   -> "${days}d ago"
        else       -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    }
}
