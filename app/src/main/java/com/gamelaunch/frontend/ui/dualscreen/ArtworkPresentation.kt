package com.gamelaunch.frontend.ui.dualscreen

import android.app.Presentation
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.component.VideoPlayer
import com.gamelaunch.frontend.ui.screen.home.SystemPreviewFan
import com.gamelaunch.frontend.ui.theme.AmbientBackground
import com.gamelaunch.frontend.ui.theme.AppTheme

/**
 * A [Presentation] that renders eOr's game artwork full-bleed on a second physical display (the
 * top panel of a dual-screen handheld). It is passive: [FLAG_NOT_FOCUSABLE] guarantees it never
 * steals key/controller focus from the interactive menu Activity on the other screen.
 *
 * The content is Compose hosted in a [ComposeView]. Because a Presentation is not an Activity, the
 * ComposeView's ViewTree owners must be wired up manually — we reuse the host [activity] as the
 * lifecycle / view-model-store / saved-state owner. State comes from [ArtworkBus].
 */
class ArtworkPresentation(
    private val activity: ComponentActivity,
    display: Display,
    private val artworkBus: ArtworkBus,
    private val darkMode: Boolean
) : Presentation(activity, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Passive display: black backdrop, never focusable so the menu Activity keeps input focus.
        window?.apply {
            setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            // Full-bleed: no title, cover the whole panel.
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }

        val composeView = ComposeView(context).apply {
            // Reuse the Activity as the owner of the Compose ViewTree (a Presentation has no owners
            // of its own). Without these, ComposeView throws when it composes.
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                AppTheme(darkMode = darkMode) {
                    ArtworkScreen(artworkBus)
                }
            }
        }
        setContentView(composeView)
    }
}

@Composable
private fun ArtworkScreen(artworkBus: ArtworkBus) {
    val state by artworkBus.state.collectAsState()

    // Continue the app's ambient gradient onto this screen so both panels read as one surface,
    // instead of a flat black fill.
    AmbientBackground(Modifier.fillMaxSize()) {
        when (state.mode) {
            ArtworkMode.GAME -> {
                val media = state.media
                if (state.shouldPlayVideo && media?.effectiveVideo != null) {
                    VideoPlayer(
                        videoPath = media.effectiveVideo,
                        shouldPlay = true,
                        isMuted = state.videoMuted,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncGameArtwork(
                        // Same priority order the single-screen background uses.
                        localPath = media?.screenshotLocalPath
                            ?: media?.backgroundLocalPath
                            ?: media?.boxArtLocalPath,
                        remoteUrl = media?.screenshotRemoteUrl
                            ?: media?.effectiveBackground
                            ?: media?.boxArtRemoteUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // The fanned box-art preview for the focused system — the same one the single-screen
            // menu shows, now given the whole top panel.
            ArtworkMode.SYSTEM_GRID -> SystemPreviewFan(
                previewArt = state.systemPreviewArt,
                focusedPlatformId = state.focusedPlatformId,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            )

            ArtworkMode.IDLE -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BrandPlaceholder()
            }
        }
    }
}

@Composable
private fun BrandPlaceholder() {
    Icon(
        imageVector = Icons.Default.SportsEsports,
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.25f),
        modifier = Modifier.padding(48.dp)
    )
}
