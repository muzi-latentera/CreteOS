package com.gamelaunch.frontend.pocket.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Deep purple-tinted dark navy — matches WinHanced's ambient dark.
 * Slightly warmer/purple-er than pure #06080F.
 */
val CreteOSBackground = Color(0xFF080B14)

private const val ACCENT_ANIMATION_DURATION_MS = 600

/**
 * WinHanced-style dynamic background.
 *
 * Two ambient radial blobs react to the focused game's artwork colour:
 *  - Primary blob: top-left, stronger alpha (0.45 → 0.0)
 *  - Secondary blob: bottom-right, softer alpha (0.25 → 0.0)
 *
 * This gives the purple/green blob effect visible in WinHanced screenshots.
 */
@Composable
fun DynamicBackground(
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = ACCENT_ANIMATION_DURATION_MS),
        label = "accentColorAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CreteOSBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val blobRadius = maxOf(w, h) * 1.3f

            // Primary blob — top-left, full accent saturation
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedAccent.copy(alpha = 0.45f),
                        animatedAccent.copy(alpha = 0.22f),
                        animatedAccent.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(0f, 0f),
                    radius = blobRadius
                )
            )

            // Secondary blob — bottom-right, complementary depth
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedAccent.copy(alpha = 0.25f),
                        animatedAccent.copy(alpha = 0.10f),
                        animatedAccent.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    center = Offset(w, h),
                    radius = blobRadius * 0.85f
                )
            )
        }

        content()
    }
}

@Composable
fun Modifier.dynamicBackground(accentColor: Color): Modifier {
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = ACCENT_ANIMATION_DURATION_MS),
        label = "accentColorAnimation"
    )

    return this
        .background(CreteOSBackground)
        .background(
            Brush.radialGradient(
                colors = listOf(
                    animatedAccent.copy(alpha = 0.45f),
                    animatedAccent.copy(alpha = 0.22f),
                    animatedAccent.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset.Zero,
                radius = 2000f
            )
        )
}
