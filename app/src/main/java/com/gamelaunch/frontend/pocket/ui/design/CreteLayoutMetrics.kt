package com.gamelaunch.frontend.pocket.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Window-based layout metrics for CreteOS' landscape UI.
 *
 * The Fold has considerably more logical height than a 1080p gaming handheld,
 * even though both are physically wide. Basing this on the current window keeps
 * the roomy Fold composition while making the Pocket FIT and mirrored XREAL view
 * feel like a console UI instead of a scaled-down tablet UI.
 */
@Immutable
data class CreteLayoutMetrics(
    val compactHandheld: Boolean,
    val screenWidth: Dp,
    val screenHeight: Dp,
    val horizontalPadding: Dp,
    val topClearance: Dp,
    val bottomNavHeight: Dp,
    val homeHeroTopPadding: Dp,
    val homeHeroHeight: Dp,
    val heroContentPadding: Dp,
    val heroTitleSize: TextUnit,
    val heroTitleLineHeight: TextUnit,
    val heroInfoWidth: Dp,
    val heroInfoPadding: Dp,
    val libraryMinCardWidth: Dp,
    val settingsRailWidth: Dp,
    val panelPadding: Dp,
    val detailOuterPadding: Dp,
    val detailPanelWidth: Dp,
    val detailTitleSize: TextUnit,
    val sourceCardHeight: Dp
)

@Composable
fun rememberCreteLayoutMetrics(): CreteLayoutMetrics {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp.dp
    val height = configuration.screenHeightDp.dp

    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        // Height is the limiting dimension for landscape handhelds. Width alone
        // would incorrectly classify an unfolded Fold as a compact device.
        val compact = configuration.screenHeightDp <= 650
        val bottomNav = if (compact) 56.dp else 64.dp
        val usableHeight = (configuration.screenHeightDp.dp - bottomNav)
        val heroHeight = if (compact) {
            (usableHeight * 0.55f).coerceIn(250.dp, 310.dp)
        } else {
            340.dp
        }

        CreteLayoutMetrics(
            compactHandheld = compact,
            screenWidth = width,
            screenHeight = height,
            horizontalPadding = if (compact) 24.dp else 32.dp,
            topClearance = if (compact) 56.dp else 68.dp,
            bottomNavHeight = bottomNav,
            homeHeroTopPadding = if (compact) 54.dp else 80.dp,
            homeHeroHeight = heroHeight,
            heroContentPadding = if (compact) 22.dp else 32.dp,
            heroTitleSize = if (compact) 31.sp else 40.sp,
            heroTitleLineHeight = if (compact) 33.sp else 42.sp,
            heroInfoWidth = if (compact) 252.dp else 296.dp,
            heroInfoPadding = if (compact) 14.dp else 20.dp,
            libraryMinCardWidth = if (compact) 112.dp else 152.dp,
            settingsRailWidth = if (compact) 224.dp else 260.dp,
            panelPadding = if (compact) 20.dp else 32.dp,
            detailOuterPadding = if (compact) 24.dp else 40.dp,
            detailPanelWidth = if (compact) 340.dp else 420.dp,
            detailTitleSize = if (compact) 30.sp else 36.sp,
            sourceCardHeight = if (compact) 176.dp else 210.dp
        )
    }
}
