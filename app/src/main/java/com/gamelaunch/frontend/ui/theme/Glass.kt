package com.gamelaunch.frontend.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** Provided at the root by AppTheme; read anywhere in the tree to choose light vs. dark colours. */
val LocalDarkMode = compositionLocalOf { false }

// ── Light, playful liquid-glass + 3DS palette ───────────────────────────────
val LightBg   = Color(0xFFEDEFF4)   // very light cool grey base
val TileText  = Color(0xFF20242E)   // dark slate for labels on light/pastel tiles
val TileSub   = Color(0xFF5A6173)   // muted slate for subtitles
val BrandBlue = Color(0xFF3E7BFF)   // accent for selected chips / branding

// Cheerful 3DS-ish tile palette — assigned per tile so the grid reads colourful.
val TilePalette = listOf(
    Color(0xFF4FB7F5), // sky
    Color(0xFF7C8CFF), // periwinkle
    Color(0xFFB07BFF), // lavender
    Color(0xFFFF7AA8), // rose
    Color(0xFFFF9F66), // coral
    Color(0xFFFFC04D), // amber
    Color(0xFF3FD3A6), // mint
    Color(0xFF53CFE0), // teal
)
fun tileColor(index: Int): Color = TilePalette[index % TilePalette.size]

// "Back-ease" bezier — overshoots past the target then settles, for a natural little bounce.
val BounceEasing = CubicBezierEasing(0.34f, 1.8f, 0.45f, 1f)
const val BounceDurationMs = 420

/** Ambient background — light pastels in light mode, dark navy glows in dark mode. */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = LocalDarkMode.current
    val bg = if (dark) NavyBg else LightBg
    Box(
        modifier
            .fillMaxSize()
            .background(bg)
            .drawBehind {
                fun glow(color: Color, cx: Float, cy: Float, r: Float) = drawRect(
                    Brush.radialGradient(
                        colors = listOf(color, Color.Transparent),
                        center = Offset(size.width * cx, size.height * cy),
                        radius = size.minDimension * r
                    )
                )
                if (dark) {
                    glow(Color(0xFF3D6FFF).copy(alpha = 0.18f), 0.08f, 0.02f, 0.95f)
                    glow(Color(0xFF7B4FFF).copy(alpha = 0.15f), 0.98f, 0.08f, 1.05f)
                    glow(Color(0xFF00CFFF).copy(alpha = 0.10f), 0.55f, 1.05f, 1.05f)
                } else {
                    glow(Color(0xFF6FC4FF).copy(alpha = 0.22f), 0.08f, 0.02f, 0.95f)
                    glow(Color(0xFFB58CFF).copy(alpha = 0.20f), 0.98f, 0.08f, 1.05f)
                    glow(Color(0xFF59E0B8).copy(alpha = 0.16f), 0.55f, 1.05f, 1.05f)
                    glow(Color(0xFFFF9CC0).copy(alpha = 0.14f), 0.18f, 0.95f, 0.75f)
                }
            },
        content = content
    )
}

/**
 * Colourful glass tile: a solid colour fill with a soft top-to-bottom sheen, a thin bright edge
 * highlight and a soft floating shadow (colour-tinted when focused). In light mode unselected tiles
 * use a light pastel shade; in dark mode they use a darker shade of the same colour so the cards
 * match the dark theme. Focused tiles deepen to the full colour and lift, 3DS-style. The fill is
 * opaque so the shadow never bleeds through.
 */
@Composable
fun Modifier.glassTile(
    shape: Shape,
    color: Color,
    selected: Boolean = false
): Modifier {
    val dark = LocalDarkMode.current

    // Light: unselected tiles lighten toward white (pastel). Dark: tiles darken toward black so
    // they read as a darker shade of the same colour and sit naturally on the dark background.
    val base = if (dark) {
        if (selected) lerp(color, Color.Black, 0.15f) else lerp(color, Color.Black, 0.60f)
    } else {
        if (selected) color else lerp(color, Color.White, 0.5f)
    }
    val sheen        = lerp(base, Color.White, if (dark) 0.10f else 0.18f)
    val borderTop    = Color.White.copy(alpha = if (dark) 0.18f else 0.5f)
    val borderBottom = Color.White.copy(alpha = if (dark) 0.04f else 0.1f)
    val restShadow   = if (dark) Color(0xFF000820) else Color(0xFF2A3550)

    return this
        .shadow(
            elevation = if (selected) 18.dp else 7.dp,
            shape = shape,
            ambientColor = if (selected) color else restShadow,
            spotColor = if (selected) color else restShadow,
            clip = false
        )
        .clip(shape)
        .background(
            Brush.verticalGradient(listOf(sheen, base))
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(borderTop, borderBottom)),
            shape = shape
        )
}

/**
 * Neutral frosted chip for tabs and icon buttons. Frosted white in light mode, frosted dark in
 * dark mode; accent fill when selected.
 */
@Composable
fun Modifier.glassChip(
    shape: Shape,
    selected: Boolean = false,
    accent: Color = BrandBlue
): Modifier {
    val dark = LocalDarkMode.current
    // Opaque fills so the drop shadow stays behind the chip instead of bleeding through it.
    val unselectedGradient = if (dark)
        listOf(Color(0xFF1E2A4D), Color(0xFF161F3C))
    else
        listOf(Color(0xFFFFFFFF), Color(0xFFEDF0F7))
    val shadowColor = if (selected) accent else if (dark) Color(0xFF000820) else Color(0xFF2A3550)
    return this
        .shadow(
            elevation = if (selected) 10.dp else 3.dp,
            shape = shape,
            ambientColor = shadowColor,
            spotColor = shadowColor,
            clip = false
        )
        .clip(shape)
        .background(
            Brush.verticalGradient(
                if (selected) listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.78f))
                else unselectedGradient
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                if (dark) listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))
                else listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.3f))
            ),
            shape = shape
        )
}
