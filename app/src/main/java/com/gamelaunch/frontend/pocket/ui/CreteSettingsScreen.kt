package com.gamelaunch.frontend.pocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.pocket.ui.design.*
import com.gamelaunch.frontend.ui.screen.settings.SettingsCategory

/**
 * CreteOS Settings v2 — WinHanced two-column landscape layout.
 *
 * Left: vertical list of category cards
 * Right: contextual content for selected category
 *
 * Top nav carries over from home (What's New · Library · Settings tab active).
 */
@Composable
fun CreteSettingsScreen(
    onBack: () -> Unit,
    onOpenCategory: (SettingsCategory) -> Unit,
    onProviderSettings: () -> Unit,
    onDisplayDiagnostics: () -> Unit,
    onScanEmulationRoms: () -> Unit = {}
) {
    var selectedIndex by remember { mutableIntStateOf(0) }

    val categories = remember {
        listOf(
            SettingsCategoryItem(
                label = "Libraries",
                subtitle = "Game sources and import",
                icon = Icons.Outlined.LibraryBooks
            ),
            SettingsCategoryItem(
                label = "PC & Streaming",
                subtitle = "GameNative, Moonlight, GeForce NOW",
                icon = Icons.Outlined.Stream
            ),
            SettingsCategoryItem(
                label = "Appearance",
                subtitle = "Theme, layout, backgrounds",
                icon = Icons.Outlined.Palette
            ),
            SettingsCategoryItem(
                label = "Display",
                subtitle = "XREAL, external display, resolution",
                icon = Icons.Outlined.Monitor
            ),
            SettingsCategoryItem(
                label = "General",
                subtitle = "Emulators, metadata, RetroAchievements",
                icon = Icons.Outlined.Settings
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CreteDS.bgBase)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top nav — Settings tab active
            CreteTopNavigation(
                tabs = listOf("Home", "Library", "Settings"),
                selectedIndex = 2,
                onTabSelected = { index ->
                    when (index) {
                        0, 1 -> onBack()
                        else -> {}
                    }
                }
            )

            Spacer(Modifier.height(CreteDS.spaceXL))

            // Two-column layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = CreteDS.spaceXXL),
                horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceXL)
            ) {
                // Left: category list
                LazyColumn(
                    modifier = Modifier.width(280.dp),
                    verticalArrangement = Arrangement.spacedBy(CreteDS.spaceS)
                ) {
                    itemsIndexed(categories) { index, item ->
                        SettingsCategoryCard(
                            item     = item,
                            selected = index == selectedIndex,
                            onClick  = { selectedIndex = index }
                        )
                    }
                }

                // Right: contextual panel
                CreteGlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (selectedIndex) {
                        0 -> LibrariesPanel(
                            onOpenCategory = onOpenCategory,
                            onScanEmulationRoms = onScanEmulationRoms
                        )
                        1 -> PcStreamingPanel(
                            onProviderSettings   = onProviderSettings,
                            onDisplayDiagnostics = onDisplayDiagnostics
                        )
                        2 -> AppearancePanel(onOpenCategory = onOpenCategory)
                        3 -> DisplayPanel(onDisplayDiagnostics = onDisplayDiagnostics)
                        4 -> GeneralPanel(onOpenCategory = onOpenCategory)
                    }
                }
            }
        }

        // System pill
        CreteSystemPill(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 16.dp)
        )
    }
}

// ── Category card ──────────────────────────────────────────────────────────

@Composable
private fun SettingsCategoryCard(
    item: SettingsCategoryItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CreteDS.radiusM))
            .background(if (selected) CreteDS.bgCardElevated else CreteDS.bgCard)
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) CreteDS.borderFocused else CreteDS.border,
                shape = RoundedCornerShape(CreteDS.radiusM)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = CreteDS.spaceL, vertical = CreteDS.spaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CreteDS.spaceM)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (selected) CreteDS.accent else CreteDS.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = CreteDS.typeNavTab,
                color = if (selected) CreteDS.textPrimary else CreteDS.textSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = item.subtitle,
                style = CreteDS.typeMeta,
                color = CreteDS.textDisabled
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = CreteDS.accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ── Right panels ───────────────────────────────────────────────────────────

@Composable
private fun LibrariesPanel(
    onOpenCategory: (SettingsCategory) -> Unit,
    onScanEmulationRoms: () -> Unit = {}
) {
    PanelColumn {
        PanelTitle("Libraries")
        PanelBody("Configure where CreteOS looks for games.")
        Spacer(Modifier.height(CreteDS.spaceXL))
        listOf(
            SettingsCategory.GAMES to "Games & Library",
            SettingsCategory.MEDIA to "Media & Artwork",
            SettingsCategory.RETRO_ACHIEVEMENTS to "RetroAchievements",
            SettingsCategory.SAVE_SYNC to "Save Sync"
        ).forEach { (cat, label) ->
            PanelRow(label = label, onClick = { onOpenCategory(cat) })
        }

        Spacer(Modifier.height(CreteDS.spaceXL))
        
        // Emulation section
        Text(
            text = "Emulation",
            style = CreteDS.typeNavTab,
            color = CreteDS.textPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = CreteDS.spaceS)
        )
        Text(
            text = "Scan ROMs from your emulation folder to add them to the library.",
            style = CreteDS.typeMeta,
            modifier = Modifier.padding(bottom = CreteDS.spaceM)
        )
        PanelRow(label = "Scan ROMs", onClick = onScanEmulationRoms)
    }
}

@Composable
private fun PcStreamingPanel(
    onProviderSettings: () -> Unit,
    onDisplayDiagnostics: () -> Unit
) {
    PanelColumn {
        PanelTitle("PC & Streaming")
        PanelBody(
            "GameNative, Moonlight, GeForce NOW and other backends. " +
            "Add games and manage launch preferences."
        )
        Spacer(Modifier.height(CreteDS.spaceXL))
        PanelRow("Provider Settings & Sync", onClick = onProviderSettings)
        PanelRow("Display Diagnostics (XREAL)", onClick = onDisplayDiagnostics)
    }
}

@Composable
private fun AppearancePanel(onOpenCategory: (SettingsCategory) -> Unit) {
    PanelColumn {
        PanelTitle("Appearance")
        PanelBody("Theme, home layout and visual options.")
        Spacer(Modifier.height(CreteDS.spaceXL))
        PanelRow("Theme & Colours") { onOpenCategory(SettingsCategory.APPEARANCE) }
        PanelRow("Home Layout") { onOpenCategory(SettingsCategory.HOME_LAYOUT) }
    }
}

@Composable
private fun DisplayPanel(onDisplayDiagnostics: () -> Unit) {
    PanelColumn {
        PanelTitle("Display")
        PanelBody(
            "External display detection for XREAL and other USB-C displays. " +
            "View connected displays and verify resolution detection."
        )
        Spacer(Modifier.height(CreteDS.spaceXL))
        PanelRow("Display Diagnostics", onClick = onDisplayDiagnostics)
    }
}

@Composable
private fun GeneralPanel(onOpenCategory: (SettingsCategory) -> Unit) {
    PanelColumn {
        PanelTitle("General")
        Spacer(Modifier.height(CreteDS.spaceXL))
        PanelRow("Emulator Config") { onOpenCategory(SettingsCategory.GAMES) }
        PanelRow("Friends") { onOpenCategory(SettingsCategory.FRIENDS) }
        PanelRow("Locked Mode") { onOpenCategory(SettingsCategory.LOCKED_MODE) }
    }
}

// ── Panel helpers ───────────────────────────────────────────────────────────

@Composable
private fun PanelColumn(content: @Composable ColumnScope.() -> Unit) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(CreteDS.spaceXXL),
        content = content
    )
}

@Composable
private fun PanelTitle(text: String) {
    Text(
        text = text,
        style = CreteDS.typeGameTitle,
        color = CreteDS.textPrimary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = CreteDS.spaceS)
    )
}

@Composable
private fun PanelBody(text: String) {
    Text(text = text, style = CreteDS.typeMeta)
}

@Composable
private fun PanelRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CreteDS.radiusS))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = CreteDS.spaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = CreteDS.typeNavTab, color = CreteDS.textSecondary)
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = CreteDS.textDisabled,
            modifier = Modifier.size(16.dp)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(CreteDS.border)
    )
}

data class SettingsCategoryItem(
    val label: String,
    val subtitle: String,
    val icon: ImageVector
)
