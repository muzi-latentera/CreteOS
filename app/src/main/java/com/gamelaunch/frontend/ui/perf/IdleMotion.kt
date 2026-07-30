package com.gamelaunch.frontend.ui.perf

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * The gentle "alive" idle for a focused card — a soft tilt, a slow vertical bob and a faint
 * breathing pulse, each on its own off-beat period so the motion drifts organically.
 *
 * [rememberIdleMotion] must be called **only for the focused card** (and skipped when reduced), so
 * the app runs at most one infinite animation instead of one per visible card — the previous
 * per-card `rememberInfiniteTransition` kept every tile's clock ticking every frame even though only
 * the focused tile used the values. Non-focused / reduced callers use [IdleMotion.None] (all zero).
 */
data class IdleMotion(val tilt: Float, val bob: Float, val breath: Float) {
    companion object {
        val None = IdleMotion(0f, 0f, 0f)
    }
}

@Composable
fun rememberIdleMotion(): IdleMotion {
    val idle = rememberInfiniteTransition(label = "idle")
    val tilt by idle.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(2300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idleTilt"
    )
    val bob by idle.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idleBob"
    )
    val breath by idle.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(2900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idleBreath"
    )
    return IdleMotion(tilt, bob, breath)
}
