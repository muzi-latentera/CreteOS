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

/** CreteOS dark navy base background colour. */
val CreteOSBackground = Color(0xFF0D1117)

/** Duration for accent colour transition animation. */
private const val ACCENT_ANIMATION_DURATION_MS = 600

/**
 * Animated dynamic background for the CreteOS home screen.
 * 
 * Displays a dark navy base with an animated radial gradient accent that transitions
 * smoothly when the focused game changes. The accent colour is extracted from the
 * focused game's artwork via the Palette API.
 *
 * @param accentColor The dominant colour extracted from the current game's artwork.
 * @param modifier Modifier for the background container.
 * @param content Content to render on top of the dynamic background.
 */
@Composable
fun DynamicBackground(
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Animate accent colour changes over 600ms
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
        // Radial gradient overlay from top-left corner
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gradientRadius = maxOf(size.width, size.height) * 1.2f
            
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedAccent.copy(alpha = 0.30f),
                        animatedAccent.copy(alpha = 0.15f),
                        animatedAccent.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(0f, 0f), // Top-left corner
                    radius = gradientRadius
                )
            )
        }
        
        content()
    }
}

/**
 * Simplified dynamic background without content slot, for use as a background modifier.
 */
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
                    animatedAccent.copy(alpha = 0.30f),
                    animatedAccent.copy(alpha = 0.15f),
                    animatedAccent.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = Offset.Zero,
                radius = 2000f // Large enough for most screens
            )
        )
}
