package com.gamelaunch.frontend.pocket.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.component.platformIcon
import com.gamelaunch.frontend.ui.component.platformPadIcon

/** Cover card dimensions - 2:3 portrait aspect ratio for game covers. */
private val CoverWidth = 140.dp
private val CoverHeight = 210.dp
private val CoverCornerRadius = 8.dp

/** Focused card scale factor. */
private const val FocusedScale = 1.05f
private const val UnfocusedScale = 1.0f

/** Unfocused card dimming alpha. */
private const val UnfocusedAlpha = 0.85f
private const val FocusedAlpha = 1.0f

/** Placeholder background for games without artwork. */
private val PlaceholderBackground = Color(0xFF21262D)

/** Focus border color. */
private val FocusBorderColor = Color.White

/**
 * Horizontal carousel of game cover art with controller navigation support.
 * 
 * Features:
 * - Portrait 2:3 aspect ratio covers
 * - Platform badge overlay
 * - Focused card: scale 1.05, white border, full opacity
 * - Unfocused cards: dimmed (0.85 alpha)
 * - Smooth scroll to keep focused item centered
 *
 * @param games List of games to display.
 * @param mediaForGames Map of game IDs to their media assets.
 * @param focusedIndex Index of the currently focused game.
 * @param onGameClick Callback when a game is selected/clicked.
 * @param onFocusChanged Callback when focus moves to a different game.
 * @param modifier Modifier for the carousel container.
 */
@Composable
fun GameCarousel(
    games: List<Game>,
    mediaForGames: Map<Long, GameMedia>,
    focusedIndex: Int,
    onGameClick: (Long) -> Unit,
    onFocusChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // Scroll to keep focused item visible/centered
    LaunchedEffect(focusedIndex) {
        if (games.isNotEmpty() && focusedIndex in games.indices) {
            // Scroll to center the focused item
            listState.animateScrollToItem(
                index = focusedIndex,
                scrollOffset = -200 // Offset to better center the item
            )
        }
    }
    
    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
            val media = mediaForGames[game.id]
            val isFocused = index == focusedIndex
            
            GameCoverCard(
                game = game,
                media = media,
                isFocused = isFocused,
                onClick = { onGameClick(game.id) },
                modifier = Modifier.focusable()
            )
        }
    }
}

/**
 * Individual game cover card with focus animation and platform badge.
 */
@Composable
internal fun GameCoverCard(
    game: Game,
    media: GameMedia?,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) FocusedScale else UnfocusedScale,
        animationSpec = tween(durationMillis = 200),
        label = "coverScale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isFocused) FocusedAlpha else UnfocusedAlpha,
        animationSpec = tween(durationMillis = 200),
        label = "coverAlpha"
    )
    
    Box(
        modifier = modifier
            .width(CoverWidth)
            .height(CoverHeight)
            .scale(scale)
            .alpha(alpha)
            .shadow(
                elevation = if (isFocused) 16.dp else 4.dp,
                shape = RoundedCornerShape(CoverCornerRadius),
                ambientColor = if (isFocused) Color.White.copy(alpha = 0.3f) else Color.Black,
                spotColor = if (isFocused) Color.White.copy(alpha = 0.3f) else Color.Black
            )
            .clip(RoundedCornerShape(CoverCornerRadius))
            .background(PlaceholderBackground)
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 2.dp,
                        color = FocusBorderColor,
                        shape = RoundedCornerShape(CoverCornerRadius)
                    )
                } else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        val boxArt = media?.effectiveBoxArt
        
        if (boxArt != null) {
            // Load artwork via Coil
            AsyncGameArtwork(
                localPath = media.boxArtLocalPath,
                remoteUrl = media.boxArtRemoteUrl,
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Placeholder with game title
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PlaceholderBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = game.title,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        
        // Platform badge (bottom-right)
        PlatformBadge(
            platformId = game.platformId,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
        )
    }
}

/**
 * Small platform indicator badge shown on game covers.
 */
@Composable
fun PlatformBadge(
    platformId: String,
    modifier: Modifier = Modifier
) {
    val icon = platformIcon(platformId) ?: platformPadIcon(platformId)
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(4.dp)
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = platformId,
            tint = Color.Unspecified, // Use the icon's original colours
            modifier = Modifier.size(16.dp)
        )
    }
}
