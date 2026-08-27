package com.gamelaunch.frontend.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.R

private val NearBlack = Color(0xFF08090B)
private val Amber     = Color(0xFFE9A93C)

/**
 * In-app loading screen shown while the app warms its first screen's artwork. Matches the
 * windowBackground splash_bg exactly — near-black with the CreteOS "C" mark centred — so there's
 * no visual transition between the OS window background and this composable.
 */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(NearBlack),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = Amber,
            modifier = Modifier.size(120.dp)
        )
        CircularProgressIndicator(
            color = Amber.copy(alpha = 0.5f),
            strokeWidth = 2.dp,
            modifier = Modifier.size(160.dp).align(Alignment.Center)
        )
    }
}
