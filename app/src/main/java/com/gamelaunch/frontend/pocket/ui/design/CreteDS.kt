package com.gamelaunch.frontend.pocket.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * CreteDS — Design System tokens for CreteOS UI v2.
 *
 * Single source of truth. All pocket UI components read from here.
 * Inspired by WinHanced: dark navy base, frosted glass surfaces,
 * artwork-reactive ambient colour, thin outline icons, restrained accents.
 *
 * Do NOT reference Material colorScheme in pocket UI components directly.
 * Use these tokens so the look stays consistent across screens.
 */
object CreteDS {

    // ── Colour palette ─────────────────────────────────────────────────────

    /** Deepest background — under everything */
    val bgBase        = Color(0xFF06080F)

    /** Surface behind cards and panels */
    val bgSurface     = Color(0xFF0C1018)

    /** Card/panel fill */
    val bgCard        = Color(0xFF131922)

    /** Elevated card — slightly lighter */
    val bgCardElevated = Color(0xFF1A2435)

    /** Top-right system pill background */
    val pillBg        = Color(0xDD131922)   // 87% opacity - more opaque

    // ── Panel backgrounds — opaque glass effect ────────────────────────────
    /** Main panel background — 80% opacity */
    val panelBg       = Color(0xCC0C1018)
    /** Card on panel — 87% opacity */
    val panelBgCard   = Color(0xDD0F1520)
    /** Slightly lighter panel — 73% opacity */
    val panelBgLight  = Color(0xBB131922)

    /** Focus/selected accent — bright blue, used sparingly */
    val accent        = Color(0xFF4D9FFF)

    /** Focus ring/border on selected element */
    val focusBorder   = Color(0xFF4D9FFF)

    /** Navigation underline on active tab */
    val tabUnderline  = Color(0xFF4D9FFF)

    /** Primary text */
    val textPrimary   = Color(0xFFEEF2FF)

    /** Secondary / muted text */
    val textSecondary = Color(0xFF8899BB)

    /** Disabled / very muted text */
    val textDisabled  = Color(0xFF4A5568)

    /** Thin border on cards/pills */
    val border        = Color(0xFF1E2D45)

    /** Brighter border on focused card */
    val borderFocused = Color(0xFF3A5A88)

    /** Scrim over hero artwork */
    val heroScrim     = Color(0x99000000)

    /** Genre/tag chip background */
    val chipBg        = Color(0xFF1A2840)

    /** Play button background */
    val playBg        = Color(0xFF1C3060)

    /** Play button focused */
    val playBgFocused = Color(0xFF2A4C99)

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
