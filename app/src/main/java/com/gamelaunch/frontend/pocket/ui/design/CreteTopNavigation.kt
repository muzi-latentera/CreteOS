package com.gamelaunch.frontend.pocket.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Top navigation bar — WinHanced style.
 * - Centered tab labels with no icons
 * - LB / RB shoulder button hints flanking
 * - Thin accent underline on active tab
 * - No app wordmark
 */
@Composable
fun CreteTopNavigation(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val layout = rememberCreteLayoutMetrics()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (layout.compactHandheld) 46.dp else CreteDS.navBarHeight),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // LB hint
            ShoulderHint("LB")
            Spacer(Modifier.width(CreteDS.spaceL))

            // Tabs
            tabs.forEachIndexed { index, label ->
                CreteNavTab(
                    label    = label,
                    selected = index == selectedIndex,
                    onClick  = { onTabSelected(index) }
                )
                if (index < tabs.lastIndex) {
                    Spacer(Modifier.width(CreteDS.spaceXXL))
                }
            }

            Spacer(Modifier.width(CreteDS.spaceL))
            // RB hint
            ShoulderHint("RB")
        }
    }
}

@Composable
private fun CreteNavTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val textColor by animateColorAsState(
        targetValue = if (selected) CreteDS.textPrimary else CreteDS.textSecondary,
        animationSpec = tween(CreteDS.animFast),
        label = "tabColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Text(
            text = label,
            style = if (selected) CreteDS.typeNavTab else CreteDS.typeNavTabDim,
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        Spacer(Modifier.height(4.dp))
        // Active underline
        Box(
            modifier = Modifier
                .width(if (selected) 28.dp else 0.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) CreteDS.tabUnderline else Color.Transparent)
        )
    }
}

@Composable
private fun ShoulderHint(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(CreteDS.bgCardElevated)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = CreteDS.typeControllerHint,
            color = CreteDS.textSecondary,
            fontWeight = FontWeight.SemiBold
        )
    }
}
