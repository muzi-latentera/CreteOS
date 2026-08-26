package com.gamelaunch.frontend.pocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.gamelaunch.frontend.pocket.ui.home.DynamicBackground
import com.gamelaunch.frontend.pocket.ui.home.rememberDominantColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

/**
 * CreteOS Game Detail v2 — WinHanced-inspired layout.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                [hero artwork — full width, ~55% height]         │
 * │                [ambient gradient from artwork colour]           │
 * ├─────────────────────────────────────────────────────────────────┤
 * │ [Play▾]  PLAY TIME  ACHIEVEMENTS  [divider]  MAIN STORY  ...  ⚙│
 * ├─────────────────────────────────────────────────────────────────┤
 * │  [cover]  TITLE bold                        Platform ● Steam   │
 * │           Description text                                     │
 * │           Release year · Developer                             │
 * │           [Action] [Indie] [RPG]                               │
 * └─────────────────────────────────────────────────────────────────┘
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

    // Load pocket targets when game available
    LaunchedEffect(game.id) { pocketViewModel.loadTargetsForGame(game) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CreteDS.bgBase)
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
        // Dynamic background tint from artwork
        DynamicBackground(accentColor = accentColor, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Hero artwork ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.52f)
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
                // Gradient into info panel
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.6f to CreteDS.bgBase.copy(alpha = 0.4f),
                                1.0f to CreteDS.bgBase
                            )
                        )
                )
                // Back button — top left
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = CreteDS.textPrimary
                    )
                }
            }

            // ── Info panel ─────────────────────────────────────────────────
            CreteGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CreteDS.spaceL)
                    .padding(bottom = CreteDS.spaceL),
                opacity = 0.93f
            ) {
                Column(modifier = Modifier.padding(CreteDS.spaceXL)) {

                    // Play button + stats row + settings gear
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceXL)
                    ) {
                        // Play button with dropdown if multiple targets
                        CretePlayButton(
                            onClick = { viewModel.launchGame() },
                            onDropdownClick = if (pocketState.targets.size > 1)
                                ({ pocketViewModel.showPlayUsing() }) else null,
                            focused = true
                        )

                        // Divider
                        CreteStatDivider()

                        // Sessions from CreteOS tracking (not actual duration yet)
                        val sessionsStr = if (game.playCount == 0) "—" else "${game.playCount}"
                        CreteStatItem(label = "Sessions", value = sessionsStr)

                        Spacer(Modifier.width(CreteDS.spaceXL))

                        // Last played
                        game.lastPlayedMs?.let { ms ->
                            CreteStatDivider()
                            Spacer(Modifier.width(CreteDS.spaceS))
                            CreteStatItem(
                                label = "Last Played",
                                value = formatLastPlayed(ms)
                            )
                            Spacer(Modifier.width(CreteDS.spaceXL))
                        }

                        // Achievements placeholder — Steam integration would fill this
                        CreteStatDivider()
                        Spacer(Modifier.width(CreteDS.spaceS))
                        CreteStatItem(label = "Achievements", value = "—")

                        Spacer(Modifier.weight(1f))

                        // Settings gear
                        IconButton(onClick = { /* game settings — phase 2 */ }) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Game settings",
                                tint = CreteDS.textSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(CreteDS.spaceXL))

                    // Cover + metadata row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceXL),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Small cover thumbnail
                        if (coverUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(coverUrl).crossfade(true).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(120.dp)
                                    .background(CreteDS.bgCardElevated, RoundedCornerShape(CreteDS.radiusM))
                            )
                        }

                        // Text metadata
                        Column(modifier = Modifier.weight(1f)) {
                            // Title + platform badge row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = game.title,
                                    style = CreteDS.typeGameTitle,
                                    fontWeight = FontWeight.Bold,
                                    color = CreteDS.textPrimary
                                )
                                // Provider badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = platformDisplayName(game.platformId),
                                        style = CreteDS.typeMeta,
                                        color = CreteDS.textSecondary
                                    )
                                    CreteProviderBadge(platformId = game.platformId)
                                }
                            }

                            Spacer(Modifier.height(CreteDS.spaceS))

                            // Description
                            game.description?.let { desc ->
                                Text(
                                    text = desc.take(220) + if (desc.length > 220) "…" else "",
                                    style = CreteDS.typeMeta,
                                    modifier = Modifier.padding(bottom = CreteDS.spaceS)
                                )
                            }

                            // Release year
                            game.releaseYear?.let { year ->
                                Text(
                                    text = "Released $year",
                                    style = CreteDS.typeMeta
                                )
                            }

                            Spacer(Modifier.height(CreteDS.spaceS))

                            // Genre chips
                            game.genre?.let { genre ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceS),
                                    modifier = Modifier.padding(top = CreteDS.spaceXs)
                                ) {
                                    genre.split(",", "/", "|")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                        .take(4)
                                        .forEach { tag ->
                                            CreteGenreChip(label = tag)
                                        }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(CreteDS.spaceL))

            // Bottom hints
            CreteBottomHints(
                hints = listOf(
                    "A" to "Play",
                    "B" to "Back",
                    "X" to "Options"
                )
            )
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

        // Error dialogs
        state.launchError?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                title = { Text("Cannot Launch") },
                text  = { Text(error) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissError) { Text("OK") }
                }
            )
        }   // end Column
        }   // end DynamicBackground
    }   // end outer Box
}

private fun formatLastPlayed(ms: Long): String {
    val diff   = System.currentTimeMillis() - ms
    val days   = diff / 86_400_000
    val hours  = diff / 3_600_000
    val mins   = diff / 60_000
    return when {
        mins < 60  -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7   -> "${days}d ago"
        else       -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    }
}
