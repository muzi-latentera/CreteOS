package com.gamelaunch.frontend.ui.theme.grid

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.model.GameSort
import com.gamelaunch.frontend.domain.model.sectionLabel
import com.gamelaunch.frontend.ui.component.boxArtAspectRatio
import com.gamelaunch.frontend.ui.perf.LocalReduceMotion
import com.gamelaunch.frontend.ui.theme.IceWhite
import com.gamelaunch.frontend.ui.theme.LocalDarkMode
import com.gamelaunch.frontend.ui.theme.TileText
import com.gamelaunch.frontend.ui.theme.glassChip
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

// How long a hold must be sustained before the section popup appears. Lite catches up more slowly,
// so it earns the popup sooner; the smooth full build waits longer so quick nudges don't flash it.
private const val HOLD_SHOW_MS_LITE = 250L
private const val HOLD_SHOW_MS_FULL = 500L
// Gaps larger than this between focus steps start a fresh hold (so separate presses don't accrue).
private const val MAX_GAP_MS = 400L
// Once the cursor has been still this long (and the grid has finished catching up), fade the popup.
private const val HIDE_MS = 450L

@Composable
fun GridHomeContent(
    games: List<Game>,
    onGameClick: (Long) -> Unit,
    columns: Int,
    mediaForGames: Map<Long, GameMedia> = emptyMap(),
    focusedGameIndex: Int = -1,
    onPageSizeChange: (Int) -> Unit = {},
    // Drives the fast-scroll section popup ("A", "★", "This Week"…) so the token matches the order
    // the games are actually in. Defaults to alphabetical.
    gameSort: GameSort = GameSort.ALPHABETICAL,
    // When set, every tile uses this fixed aspect ratio instead of its system's box shape — used by
    // mixed-system lists (Recently played) so the grid stays a uniform rectangle.
    uniformAspectRatio: Float? = null,
    modifier: Modifier = Modifier
) {
    if (games.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No games found")
        }
        return
    }

    val gridState = rememberLazyGridState()

    // Report a "page" (whole rows currently on screen × columns) up to the caller so L2/R2 can jump
    // the selection by a screenful at a time.
    LaunchedEffect(gridState, columns) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.size }
            .collect { visible ->
                val rows = (visible / columns).coerceAtLeast(1)
                onPageSizeChange(rows * columns)
            }
    }

    // Scroll so the controller-focused card is always visible. Anchor the focused card to the
    // second visible row (one row of context above it) instead of pinning it to the top row —
    // scrolling only kicks in once focus moves past the second row.
    LaunchedEffect(focusedGameIndex, columns) {
        if (focusedGameIndex in games.indices) {
            gridState.animateScrollToItem((focusedGameIndex - columns).coerceAtLeast(0))
        }
    }

    // ── Scroll section popup (+ blur) ────────────────────────────────────────────────────────
    // Once a hold has been sustained past the show delay, show a big "you are here" section token —
    // the letter/bucket the focused game sorts under — so the user can aim for a part of the list
    // while the cards behind it are still catching up. On the low-power (lite) build we also blur the
    // grid: the RK3568 can't repaint as fast as the cursor moves, so a hold-scroll otherwise shows
    // half-decoded, popping cards — the blur masks that and buys the grid time to settle. A single
    // nudge never raises it; it appears partway into a continuous hold (sooner on lite) and fades out
    // once the cursor stops and the grid catches up. Draw-only overlay: it deliberately does not
    // touch scroll mechanics or input handling (the source of the reverted double-move regression).
    val reduceMotion = LocalReduceMotion.current
    val showAfterMs = if (reduceMotion) HOLD_SHOW_MS_LITE else HOLD_SHOW_MS_FULL
    var scrolling by remember { mutableStateOf(false) }
    var lastChangeMs by remember { mutableLongStateOf(0L) }
    var holdStartMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(focusedGameIndex) {
        if (focusedGameIndex < 0) return@LaunchedEffect
        val now = SystemClock.uptimeMillis()
        // A big gap (or the first move) begins a fresh hold; small gaps continue the current one.
        if (now - lastChangeMs > MAX_GAP_MS) holdStartMs = now
        lastChangeMs = now
        // Reveal only once this hold has lasted long enough — so quick nudges stay hidden.
        if (now - holdStartMs >= showAfterMs) scrolling = true
        // This effect restarts on every move, so reaching past the delay means the cursor idled;
        // then wait for the grid's scroll to land before hiding.
        delay(HIDE_MS)
        if (gridState.isScrollInProgress) {
            snapshotFlow { gridState.isScrollInProgress }.first { !it }
        }
        scrolling = false
    }

    val blurRadius by animateDpAsState(
        targetValue   = if (reduceMotion && scrolling) 14.dp else 0.dp,
        animationSpec = tween(160),
        label = "gridScrollBlur"
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue   = if (scrolling) 1f else 0f,
        animationSpec = tween(140),
        label = "gridScrollIndicator"
    )

    // The entrance animation should fire once, when we load into a system — not every time a
    // card is recycled into view on scroll. Keep a short window open right after the games list
    // changes; cards composed during it animate, cards composed later appear instantly.
    var entranceWindowOpen by remember(games) { mutableStateOf(true) }
    LaunchedEffect(games) {
        entranceWindowOpen = true
        delay(900L)   // long enough for the staggered rise of the initially-visible cards
        entranceWindowOpen = false
    }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns               = GridCells.Fixed(columns),
            state                 = gridState,
            // Extra top padding so a focused top-row card (which scales 1.16× and bobs upward) clears
            // the header instead of being clipped under it.
            contentPadding        = PaddingValues(start = 8.dp, end = 8.dp, top = 30.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
            modifier              = Modifier
                .fillMaxSize()
                // Modifier.blur no-ops on API < 31; the effect only runs where RenderEffect exists.
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(blurRadius) else Modifier)
        ) {
            itemsIndexed(games, key = { _, g -> g.id }) { index, game ->
                GridGameCard(
                    game           = game,
                    index          = index,
                    media          = mediaForGames[game.id],
                    isFocused      = index == focusedGameIndex,
                    animateOnEntry = entranceWindowOpen,
                    aspectRatio    = uniformAspectRatio ?: boxArtAspectRatio(game.platformId),
                    // Pass the stable callback straight through — the card builds its own click
                    // lambda internally, so no per-item allocation happens here.
                    onGameClick    = onGameClick
                )
            }
        }

        // Section token, sitting crisply on top of the (blurred) grid while scrolling.
        if (indicatorAlpha > 0f) {
            ScrollSectionIndicator(
                label    = games.getOrNull(focusedGameIndex)?.sectionLabel(gameSort).orEmpty(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = indicatorAlpha }
            )
        }
    }
}

/** Big frosted "you are here" token (a letter like "S", "★", or a short bucket like "This Week"). */
@Composable
private fun ScrollSectionIndicator(
    label: String,
    modifier: Modifier = Modifier
) {
    if (label.isBlank()) return
    val dark = LocalDarkMode.current
    val primary = if (dark) IceWhite else TileText
    // Single letters/★ get the big splashy treatment; multi-word buckets shrink to fit on one line.
    val fontSize = if (label.length <= 2) 60.sp else 30.sp
    Box(
        modifier = modifier
            .glassChip(RoundedCornerShape(22.dp))
            .defaultMinSize(minWidth = 104.dp, minHeight = 104.dp)
            .padding(horizontal = 28.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            color      = primary,
            fontSize   = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines   = 1
        )
    }
}
