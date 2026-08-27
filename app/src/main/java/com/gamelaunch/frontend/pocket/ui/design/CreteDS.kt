package com.gamelaunch.frontend.pocket.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * CreteDS — Design System tokens for CreteOS UI v1.
 *
 * Single source of truth. All pocket UI components read from here.
 * Near-black base, amber accent, cream text palette.
 *
 * Do NOT reference Material colorScheme in pocket UI components directly.
 * Use these tokens so the look stays consistent across screens.
 */
object CreteDS {

    // ── Colour palette ─────────────────────────────────────────────────────

    /** Deepest background — near-black, under everything */
    val bgBase        = Color(0xFF08090B)

    /** Sidebar panel background */
    val bgSidebar     = Color(0xFF0B0E11)

    /** Card/panel fill — source/settings cards */
    val bgCard        = Color(0xFF0F1317)

    /** Elevated card — game card placeholder, slightly lighter */
    val bgCardElevated = Color(0xFF14181D)

    /** Top-right system pill background */
    val pillBg        = Color(0xDD0F1317)   // 87% opacity

    // ── Panel backgrounds — opaque glass effect ────────────────────────────
    /** Main panel background — 80% opacity */
    val panelBg       = Color(0xCC0B0E11)
    /** Card on panel — 87% opacity */
    val panelBgCard   = Color(0xDD0F1317)
    /** Slightly lighter panel — 73% opacity */
    val panelBgLight  = Color(0xBB14181D)

    /** Focus/selected accent — amber, used sparingly */
    val accent        = Color(0xFFE9A93C)

    /** Red accent — play button */
    val accentRed     = Color(0xFFC9482A)

    /** Focus ring/border on selected element — amber */
    val focusBorder   = Color(0xFFE9A93C)

    /** Navigation underline on active tab */
    val tabUnderline  = Color(0xFFE9A93C)

    /** Primary text — cream */
    val textPrimary   = Color(0xFFF2EADB)

    /** Secondary / muted text — cream at 55% */
    val textSecondary = Color(0x8CF2EADB)

    /** Disabled / very muted text — cream at 27% */
    val textDisabled  = Color(0x45F2EADB)

    /** Monospace data colour — cyan-tinted */
    val textMono      = Color(0xFF9FD3E2)

    /** Thin border on cards/pills — subtle */
    val border        = Color(0x14F2E8D5)

    /** Brighter border on focused card */
    val borderFocused = Color(0x33F2E8D5)

    /** Scrim over hero artwork */
    val heroScrim     = Color(0x99000000)

    /** Genre/tag chip background */
    val chipBg        = Color(0xFF14181D)

    /** Play button background — red */
    val playBg        = Color(0xFFC9482A)

    /** Play button focused — slightly lighter red */
    val playBgFocused = Color(0xFFD95A3D)

    // Steam platform colour
    val steamTint     = Color(0xFF1B2838)

    // ── Spacing ────────────────────────────────────────────────────────────

    val spaceXs   = 4.dp
    val spaceS    = 8.dp
    val spaceM    = 12.dp
    val spaceL    = 16.dp
    val spaceXL   = 24.dp
    val spaceXXL  = 32.dp
    val space3XL  = 48.dp

    // ── Corner radii ───────────────────────────────────────────────────────

    val radiusS    = 6.dp
    val radiusM    = 10.dp
    val radiusL    = 16.dp
    val radiusXL   = 24.dp
    val radiusPill = 100.dp   // system pill

    // ── Component sizes ────────────────────────────────────────────────────

    val gameCardWidth       = 140.dp
    val gameCardHeight      = 210.dp   // 2:3 portrait
    val gameCardWidthFocus  = 152.dp
    val gameCardHeightFocus = 228.dp

    val pillHeight          = 40.dp
    val navBarHeight        = 52.dp
    val bottomHintsHeight   = 40.dp

    val providerBadgeSize   = 18.dp
    val statDividerWidth    = 1.dp

    // ── Opacity ────────────────────────────────────────────────────────────

    val alphaCard     = 0.85f
    val alphaDim      = 0.6f      // unfocused card dimming
    val alphaDisabled = 0.38f

    // ── Animation durations (ms) ───────────────────────────────────────────

    const val animFast   = 150
    const val animNormal = 250
    const val animSlow   = 400
    const val animColour = 600   // background accent crossfade

    // ── Typography (used directly, not via MaterialTheme) ──────────────────

    val typeGameTitle = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp,
        color = textPrimary
    )

    val typeStatLabel = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        color = textSecondary
    )

    val typeStatValue = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        color = textPrimary
    )

    val typeNavTab = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.2.sp,
        color = textPrimary
    )

    val typeNavTabDim = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        letterSpacing = 0.2.sp,
        color = textSecondary
    )

    val typeMeta = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = textSecondary
    )

    val typeChip = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp,
        color = textSecondary
    )

    val typePillStatus = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = textPrimary
    )

    val typeControllerHint = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.1.sp,
        color = textSecondary
    )
}
