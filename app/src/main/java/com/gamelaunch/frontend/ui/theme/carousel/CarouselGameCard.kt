package com.gamelaunch.frontend.ui.theme.carousel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.component.boxArtAspectRatio
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.NeonPurple

@Composable
fun CarouselGameCard(
    game: Game,
    media: GameMedia?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue    = if (isSelected) 1.13f else 0.87f,
        animationSpec  = spring(dampingRatio = 0.65f, stiffness = 280f),
        label          = "card_scale"
    )
    val shape = RoundedCornerShape(12.dp)

    // Height derives from the system's real box shape so covers aren't cropped. But tall covers
    // (PSP UMD cases, Saturn, 3DO longboxes, Switch) would otherwise rise past the row and cover the
    // game title above the carousel — so cap the height at a standard portrait box and let those
    // narrow instead. Width then follows from the aspect so the art still isn't cropped.
    val aspect = boxArtAspectRatio(game.platformId)
    val baseWidth = 118.dp
    val maxCardHeight = baseWidth / 0.72f          // standard portrait box height — the tallest we allow
    val rawHeight = baseWidth / aspect
    val cardHeight = if (rawHeight > maxCardHeight) maxCardHeight else rawHeight
    val cardWidth  = if (rawHeight > maxCardHeight) maxCardHeight * aspect else baseWidth

    AsyncGameArtwork(
        localPath          = media?.boxArtLocalPath,
        remoteUrl          = media?.boxArtRemoteUrl,
        contentDescription = game.title,
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .scale(scale)
            .then(
                if (isSelected)
                    Modifier
                        .shadow(
                            elevation    = 28.dp,
                            shape        = shape,
                            spotColor    = ElectricBlue,
                            ambientColor = NeonPurple.copy(alpha = 0.5f)
                        )
                        .border(2.dp, ElectricBlue, shape)
                else
                    Modifier.shadow(8.dp, shape)
            )
            .clip(shape)
            .clickable(onClick = onClick),
        packageName        = if (game.platformId == "android") game.romFilename else null
    )
}
