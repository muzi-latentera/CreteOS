package com.gamelaunch.frontend.pocket.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gamelaunch.frontend.pocket.ui.design.*
import com.gamelaunch.frontend.pocket.ui.home.rememberDominantColor
import com.gamelaunch.frontend.pocket.ui.home.DynamicBackground
import com.gamelaunch.frontend.pocket.ui.library.LibraryViewModel
import com.gamelaunch.frontend.ui.input.GamepadL1
import com.gamelaunch.frontend.ui.input.GamepadR1
import com.gamelaunch.frontend.ui.screen.home.HomeViewModel

/**
 * ShellTab enum — v1 navigation structure.
 * HOME, LIBRARY, SOURCES, SETTINGS (no WHATS_NEW)
 */
enum class ShellTab(val label: String) {
    HOME("Home"),
    LIBRARY("Library"),
    SOURCES("Sources"),
    SETTINGS("Settings")
}

/**
 * CreteOS Root Shell — v1 fixed landscape layout.
 *
 * ┌─────────┬───────────────────────────────────────────────┐
 * │         │  [72px top header bar]                        │
 * │  92px   ├───────────────────────────────────────────────┤
 * │ sidebar │                                               │
 * │         │  [content fills remaining height]             │
 * │         │                                               │
 * │         ├───────────────────────────────────────────────┤
 * │         │  [64px bottom hints bar]                      │
 * └─────────┴───────────────────────────────────────────────┘
 *
 * Sidebar: logo, nav icons (Home/Library/Sources), Settings + Power at bottom
 * Header: CRETE OS branding, breadcrumb, perf pill, WiFi, battery, clock
 * Hints: controller buttons (A/B/X/LB/RB), version string
 */
@Composable
fun CreteRootShell(
    onGameClick: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCreteSettings: () -> Unit = {},
    onOpenProviders: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenLibrary: (LibraryFilter) -> Unit = {},
    homeViewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel()
) {
    var activeTab by remember { mutableStateOf(ShellTab.HOME) }

    val homeState by homeViewModel.uiState.collectAsState()

    val focusedGame  = homeState.recentlyPlayed.firstOrNull() ?: homeState.games.firstOrNull()
    val focusedMedia = focusedGame?.let { homeState.mediaForGames[it.id] }
    val heroUrl      = focusedMedia?.effectiveBackground ?: focusedMedia?.effectiveBoxArt
    val accentColor  = rememberDominantColor(focusedMedia?.effectiveBoxArt)

    // LB/RB cycle through HOME→LIBRARY→SOURCES (not SETTINGS)
    val cyclableTabs = listOf(ShellTab.HOME, ShellTab.LIBRARY, ShellTab.SOURCES)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CreteDS.bgBase)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    GamepadL1 -> {
                        val currentIndex = cyclableTabs.indexOf(activeTab).takeIf { it >= 0 } ?: 0
                        val prev = (currentIndex - 1).coerceAtLeast(0)
                        activeTab = cyclableTabs[prev]
                        true
                    }
                    GamepadR1 -> {
                        val currentIndex = cyclableTabs.indexOf(activeTab).takeIf { it >= 0 } ?: 0
                        val next = (currentIndex + 1).coerceAtMost(cyclableTabs.lastIndex)
                        activeTab = cyclableTabs[next]
                        true
                    }
                    else -> false
                }
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Sidebar (92dp) ─────────────────────────────────────────────
            CreteSidebar(
                activeTab = activeTab,
                onTabSelected = { activeTab = it },
                onSettingsClick = { activeTab = ShellTab.SETTINGS },
                onPowerClick = { /* TODO: power menu */ }
            )

            // ── Content area ───────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Background: hero artwork on HOME, dark navy gradient on other tabs
                if (activeTab == ShellTab.HOME) {
                    DynamicBackground(
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (heroUrl != null) {
                            Crossfade(
                                targetState = heroUrl,
                                animationSpec = tween(CreteDS.animColour),
                                label = "shellHero"
                            ) { url ->
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(url).crossfade(true).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        // Scrim
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to CreteDS.bgBase.copy(alpha = if (heroUrl != null) 0.50f else 0.90f),
                                        0.40f to CreteDS.bgBase.copy(alpha = if (heroUrl != null) 0.65f else 0.92f),
                                        1.0f to CreteDS.bgBase.copy(alpha = 0.97f)
                                    )
                                )
                        )
                    }
                } else {
                    // Dark navy gradient for non-HOME tabs
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0C0F13), Color(0xFF0A0D10))
                                )
                            )
                    )
                }

                // Content layout: header + content + hints
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Top header bar (72dp) ──────────────────────────────
                    CreteHeaderBar(activeTab = activeTab)

                    // ── Content area ───────────────────────────────────────
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Crossfade(
                            targetState = activeTab,
                            animationSpec = tween(CreteDS.animFast),
                            label = "tabContent"
                        ) { tab ->
                            when (tab) {
                                ShellTab.HOME -> CreteHomeLayout(
                                    activeTab        = tab,
                                    onTabSelected    = { activeTab = it },
                                    homeViewModel    = homeViewModel,
                                    libraryViewModel = libraryViewModel,
                                    onGameClick      = onGameClick,
                                    onOpenLibrary    = onOpenLibrary,
                                    onOpenCreteSettings = onOpenCreteSettings,
                                    onOpenSettings   = onOpenSettings,
                                    onOpenProviders  = onOpenProviders,
                                    onOpenDisplay    = onOpenDisplay,
                                    modifier         = Modifier.fillMaxSize()
                                )
                                ShellTab.LIBRARY -> LibraryTabContent(
                                    libraryViewModel = libraryViewModel,
                                    onGameClick      = onGameClick
                                )
                                ShellTab.SOURCES -> SourcesPlaceholder()
                                ShellTab.SETTINGS -> SettingsTabContent(
                                    onOpenSettings   = onOpenSettings,
                                    onOpenProviders  = onOpenProviders,
                                    onOpenDisplay    = onOpenDisplay
                                )
                            }
                        }
                    }

                    // ── Bottom hints bar (64dp) ────────────────────────────
                    CreteHintsBar()
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// SIDEBAR — 92dp fixed, left edge
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun CreteSidebar(
    activeTab: ShellTab,
    onTabSelected: (ShellTab) -> Unit,
    onSettingsClick: () -> Unit,
    onPowerClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(92.dp)
            .fillMaxHeight()
            .background(Color(0xFF0B0E11))
            .border(
                width = 1.dp,
                color = Color(0x14F2E8D5),
                shape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp)
            )
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Logo box (46x46dp) ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF14181D))
                .border(
                    width = 1.dp,
                    color = CreteDS.accent.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(13.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "C",
                color = CreteDS.accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Nav icons ──────────────────────────────────────────────────────
        SidebarNavButton(
            icon = Icons.Filled.Home,
            label = "Home",
            selected = activeTab == ShellTab.HOME,
            onClick = { onTabSelected(ShellTab.HOME) }
        )
        Spacer(Modifier.height(8.dp))
        SidebarNavButton(
            icon = Icons.Outlined.ViewColumn,
            label = "Library",
            selected = activeTab == ShellTab.LIBRARY,
            onClick = { onTabSelected(ShellTab.LIBRARY) }
        )
        Spacer(Modifier.height(8.dp))
        SidebarNavButton(
            icon = Icons.Outlined.Layers,
            label = "Sources",
            selected = activeTab == ShellTab.SOURCES,
            onClick = { onTabSelected(ShellTab.SOURCES) }
        )

        Spacer(Modifier.weight(1f))

        // ── Bottom icons ───────────────────────────────────────────────────
        SidebarBottomButton(
            icon = Icons.Outlined.Settings,
            tint = CreteDS.textSecondary,
            onClick = onSettingsClick
        )
        Spacer(Modifier.height(12.dp))
        SidebarBottomButton(
            icon = Icons.Outlined.PowerSettingsNew,
            tint = Color(0xFFC9482A),
            onClick = onPowerClick
        )
    }
}

@Composable
private fun SidebarNavButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected) Color(0x21E9A93C) else Color.Transparent
    val borderColor = if (selected) CreteDS.accent.copy(alpha = 0.5f) else Color.Transparent

    Box(modifier = Modifier.padding(start = 3.dp)) { // Offset for active bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Active indicator bar (3dp left of button)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(56.dp)
                    .background(
                        if (selected) CreteDS.accent else Color.Transparent,
                        RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)
                    )
            )

            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(width = 64.dp, height = 56.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(bgColor)
                    .then(
                        if (selected) Modifier.border(1.dp, borderColor, RoundedCornerShape(15.dp))
                        else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) CreteDS.accent else CreteDS.textSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun SidebarBottomButton(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// HEADER BAR — 72dp, top of content area
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun CreteHeaderBar(activeTab: ShellTab) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF0C0F13), Color(0xFF0A0D10))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0x12F2E8D5))
                ),
                shape = RoundedCornerShape(0.dp)
            )
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Left: CRETE OS branding ────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "CRETE",
                color = CreteDS.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.66.sp  // 0.14em
            )
            Text(
                text = "OS",
                color = CreteDS.accent,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.66.sp
            )
        }

        // Divider
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(Color(0x20F2E8D5))
        )
        Spacer(Modifier.width(16.dp))

        // Breadcrumb
        Text(
            text = activeTab.label.uppercase(),
            color = CreteDS.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.92.sp,  // 0.16em
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.weight(1f))

        // ── Right: perf pill + status icons ────────────────────────────────
        // Perf pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(Color(0x20FFFFFF))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Green dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFF4CAF50), RoundedCornerShape(3.dp))
            )
            Text(
                text = "58°C · 11.4W · 59 FPS",
                color = CreteDS.textMono,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.width(16.dp))

        // WiFi icon
        Icon(
            imageVector = Icons.Outlined.Wifi,
            contentDescription = "WiFi",
            tint = CreteDS.textSecondary,
            modifier = Modifier.size(18.dp)
        )

        Spacer(Modifier.width(12.dp))

        // Battery indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.BatteryFull,
                contentDescription = "Battery",
                tint = CreteDS.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "87%",
                color = CreteDS.textSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.width(16.dp))

        // Clock
        Text(
            text = "8:15 PM",
            color = CreteDS.textPrimary,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// HINTS BAR — 64dp, bottom of content area
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun CreteHintsBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xFF0A0D10))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x12F2E8D5), Color.Transparent)
                ),
                shape = RoundedCornerShape(0.dp)
            )
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Left: controller hint buttons ──────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControllerHint(label = "A", color = Color(0xFF4CAF50), text = "Select")
            ControllerHint(label = "B", color = Color(0xFFE53935), text = "Back")
            ControllerHint(label = "X", color = Color(0xFF2196F3), text = "Options")
            ControllerHint(label = "LB", color = CreteDS.textDisabled, text = null)
            ControllerHint(label = "RB", color = CreteDS.textDisabled, text = null)
        }

        Spacer(Modifier.weight(1f))

        // ── Right: version string ──────────────────────────────────────────
        Text(
            text = "CRETEOS 0.9 · ANDROID 16",
            color = CreteDS.textDisabled,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun ControllerHint(
    label: String,
    color: Color,
    text: String?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.2f))
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        if (text != null) {
            Text(
                text = text,
                color = CreteDS.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// SOURCES PLACEHOLDER
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun SourcesPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Sources — coming soon",
            color = CreteDS.textSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
