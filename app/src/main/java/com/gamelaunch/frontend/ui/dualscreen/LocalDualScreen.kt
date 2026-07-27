package com.gamelaunch.frontend.ui.dualscreen

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True when eOr is running across two physical displays and the game artwork has been routed to the
 * second (artwork) screen. The interactive Activity UI reads this to drop its own full-screen
 * artwork/video backdrop — that layer now lives on the other screen — and let the menu take the
 * whole panel. Defaults to false, so single-screen devices render exactly as before.
 */
val LocalDualScreenActive = staticCompositionLocalOf { false }
