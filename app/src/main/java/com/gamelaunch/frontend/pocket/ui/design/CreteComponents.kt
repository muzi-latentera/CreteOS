package com.gamelaunch.frontend.pocket.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.providers.ProviderId

// ── Bottom controller hints ────────────────────────────────────────────────

/**
 * Subtle bottom strip of controller hints — WinHanced style.
 * Monochrome, small, right-aligned, unobtrusive.
 */
@Composable
fun CreteBottomHints(
    hints: List<Pair<String, String>>,   // button label → action label
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CreteDS.bottomHintsHeight)
            .padding(horizontal = CreteDS.spaceXL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        hints.forEachIndexed { index, (button, action) ->
            ControllerHint(button = button, label = action)
            if (index < hints.lastIndex) {
                Spacer(Modifier.width(CreteDS.spaceXL))
            }
        }
    }
}

@Composable
private fun ControllerHint(button: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Button circle/square indicator
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(CreteDS.bgCardElevated)
                .border(0.5.dp, CreteDS.border, RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                text = button,
                style = CreteDS.typeControllerHint,
                color = CreteDS.textSecondary,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            style = CreteDS.typeControllerHint,
            color = CreteDS.textSecondary
        )
    }
}

// ── Glass card surface ─────────────────────────────────────────────────────

/**
 * Frosted glass-style card. Thin border, subtle bg, rounded.
 * Used for the game info panel in the detail screen.
 * @param opacity Background opacity (0.0-1.0), defaults to 0.80 (80%)
 */
@Composable
fun CreteGlassCard(
    modifier: Modifier = Modifier,
    opacity: Float = 0.80f,
    content: @Composable BoxScope.() -> Unit
) {
    val bgColor = Color(0xFF0A1220).copy(alpha = opacity)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CreteDS.radiusL))
            .background(bgColor)
            .border(
                0.5.dp,
                Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(CreteDS.radiusL)
            ),
        content = content
    )
}

// ── Game cover card ────────────────────────────────────────────────────────

/**
 * Portrait cover card — 2:3 ratio, focus scale animation, platform badge.
 * Used in carousel and library grid.
 */
@Composable
fun CreteGameCard(
    artworkUrl: String?,
    title: String,
    platformId: String,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(CreteDS.animFast),
        label = "cardScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) CreteDS.focusBorder else Color.Transparent,
        animationSpec = tween(CreteDS.animFast),
        label = "cardBorder"
    )

    Box(
        modifier = modifier
            .width(CreteDS.gameCardWidth)    // constant 140dp — no layout shift ever
            .height(CreteDS.gameCardHeight)  // constant 210dp
            .clip(RoundedCornerShape(CreteDS.radiusM))   // clip first so artwork is rounded
            .border(1.5.dp, borderColor, RoundedCornerShape(CreteDS.radiusM))
            .graphicsLayer {                              // scale is draw-only, doesn't affect layout
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Artwork
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CreteDS.bgCardElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.take(2).uppercase(),
                    style = CreteDS.typeGameTitle,
                    color = CreteDS.textSecondary
                )
            }
        }

        // Platform badge — bottom right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
        ) {
            CreteProviderBadge(platformId = platformId)
        }
    }
}

// ── Provider badge ─────────────────────────────────────────────────────────

/** Small platform icon badge — Steam, Android, etc. */
@Composable
fun CreteProviderBadge(
    platformId: String,
    modifier: Modifier = Modifier
) {
    val label = when (platformId.lowercase()) {
        "steam", "gog", "epic", "amazon" -> "S"
        "android"   -> "A"
        "moonlight" -> "M"
        "gfn"       -> "G"
        else        -> platformId.take(1).uppercase()
    }
    Box(
        modifier = modifier
            .size(CreteDS.providerBadgeSize)
            .clip(RoundedCornerShape(3.dp))
            .background(CreteDS.bgBase.copy(alpha = 0.75f))
            .border(0.5.dp, CreteDS.border, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = CreteDS.textSecondary
        )
    }
}

// ── Stat item ──────────────────────────────────────────────────────────────

/** One stat column in the stats row — label over value. */
@Composable
fun CreteStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            style = CreteDS.typeStatLabel
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = CreteDS.typeStatValue
        )
    }
}

/** Thin vertical divider between stat items. */
@Composable
fun CreteStatDivider() {
    Box(
        modifier = Modifier
            .width(CreteDS.statDividerWidth)
            .height(32.dp)
            .background(CreteDS.border)
    )
}

// ── Play button ────────────────────────────────────────────────────────────

/**
 * WinHanced-style Play button with optional dropdown arrow for multi-provider.
 * Bordered rectangle, not a filled capsule.
 */
@Composable
fun CretePlayButton(
    onClick: () -> Unit,
    onDropdownClick: (() -> Unit)? = null,
    focused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (focused) CreteDS.playBgFocused else CreteDS.playBg,
        animationSpec = tween(CreteDS.animFast),
        label = "playBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) CreteDS.accent else CreteDS.borderFocused,
        animationSpec = tween(CreteDS.animFast),
        label = "playBorder"
    )

    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(CreteDS.radiusM))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(CreteDS.radiusM))
    ) {
        // Play region
        Row(
            modifier = Modifier
                .padding(horizontal = CreteDS.spaceL, vertical = CreteDS.spaceS)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = CreteDS.textPrimary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Play",
                style = CreteDS.typeNavTab,
                color = CreteDS.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Dropdown arrow — shown when multiple providers exist
        if (onDropdownClick != null) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(borderColor)
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = CreteDS.spaceS)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDropdownClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Choose launch method",
                    tint = CreteDS.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Genre chip ─────────────────────────────────────────────────────────────

@Composable
fun CreteGenreChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(CreteDS.radiusS))
            .background(CreteDS.chipBg)
            .border(0.5.dp, CreteDS.border, RoundedCornerShape(CreteDS.radiusS))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = CreteDS.typeChip,
            color = CreteDS.textSecondary
        )
    }
}
