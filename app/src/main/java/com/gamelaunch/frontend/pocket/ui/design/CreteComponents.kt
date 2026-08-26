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
 * Generates a deterministic colour from a string hash.
 * Used for placeholder initial letter colour.
 */
private fun colorFromHash(text: String): Color {
    val hash = text.hashCode()
    val hue = (hash and 0x7FFFFFFF) % 360
    // Desaturated palette — 40% saturation, 60% lightness
    val h = hue / 360f
    val s = 0.4f
    val l = 0.6f
    // HSL to RGB
    val c = (1 - kotlin.math.abs(2 * l - 1)) * s
    val x = c * (1 - kotlin.math.abs((h * 6) % 2 - 1))
    val m = l - c / 2
    val (r, g, b) = when {
        h < 1f/6 -> Triple(c, x, 0f)
        h < 2f/6 -> Triple(x, c, 0f)
        h < 3f/6 -> Triple(0f, c, x)
        h < 4f/6 -> Triple(0f, x, c)
        h < 5f/6 -> Triple(x, 0f, c)
        else     -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

/**
 * Portrait cover card — 2:3 ratio, focus scale animation, platform badge.
 * When no artwork, shows large initial letter as background decoration.
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
            .width(if (focused) CreteDS.gameCardWidthFocus else CreteDS.gameCardWidth)
            .height(if (focused) CreteDS.gameCardHeightFocus else CreteDS.gameCardHeight)
            .scale(scale)
            // no alpha dim — all cards are full opacity; only focused gets border+scale
            .clip(RoundedCornerShape(CreteDS.radiusM))
            .border(1.5.dp, borderColor, RoundedCornerShape(CreteDS.radiusM))
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
            // Placeholder with large initial letter background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CreteDS.bgCardElevated)
            ) {
                // Large background initial letter
                val initial = title.firstOrNull()?.uppercaseChar()?.toString() ?: ""
                val letterColor = colorFromHash(title)
                Text(
                    text = initial,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Bold,
                    color = letterColor.copy(alpha = 0.46f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { translationY = -10f }
                )
                // Smaller title initials in front
                Text(
                    text = title.take(2).uppercase(),
                    style = CreteDS.typeGameTitle,
                    color = CreteDS.textSecondary,
                    modifier = Modifier.align(Alignment.Center)
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

/** Small platform badge — bottom-right corner of game cards. */
@Composable
fun CreteProviderBadge(
    platformId: String,
    modifier: Modifier = Modifier
) {
    val (label, tint) = when (platformId.lowercase()) {
        "steam"     -> "ST" to Color(0xFF1B9FEA)
        "gog"       -> "GO" to Color(0xFF8A44CB)
        "epic"      -> "EP" to Color(0xFF2D2D2D)
        "amazon"    -> "PG" to Color(0xFFFF9900)
        "android"   -> "AN" to Color(0xFF3DDC84)
        "moonlight" -> "ML" to Color(0xFF4D9FFF)
        "gfn"       -> "GN" to Color(0xFF76B900)
        else        -> platformId.take(2).uppercase() to CreteDS.textSecondary
    }
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xCC060E1C))
            .border(0.5.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
            letterSpacing = (-0.3).sp
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
 * Play button with solid red background (#C9482A).
 * Optional dropdown arrow for multi-provider.
 * Amber glow border on focus.
 */
@Composable
fun CretePlayButton(
    onClick: () -> Unit,
    onDropdownClick: (() -> Unit)? = null,
    focused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (focused) CreteDS.accent else Color.Transparent,
        animationSpec = tween(CreteDS.animFast),
        label = "playBorder"
    )

    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(CreteDS.radiusM))
            .background(CreteDS.accentRed)
            .border(2.dp, borderColor, RoundedCornerShape(CreteDS.radiusM)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play region
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = CreteDS.spaceL)
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
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Play",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        // Dropdown arrow — shown when multiple providers exist
        if (onDropdownClick != null) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.3f))
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
                    tint = Color.White.copy(alpha = 0.8f),
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
