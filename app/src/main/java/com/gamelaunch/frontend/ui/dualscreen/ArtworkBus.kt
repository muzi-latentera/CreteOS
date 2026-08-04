package com.gamelaunch.frontend.ui.dualscreen

import com.gamelaunch.frontend.domain.model.GameMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the artwork (top) screen is currently showing. */
enum class ArtworkMode {
    /** No game context yet (onboarding, splash, non-Games tab) — show branding. */
    IDLE,
    /** Browsing the system grid — show the focused system's preview art. */
    SYSTEM_GRID,
    /** Inside a system — show the selected game's screenshot / video / box art. */
    GAME
}

/** Immutable snapshot the [ArtworkPresentation] renders on the second screen. */
data class ArtworkUiState(
    val mode: ArtworkMode = ArtworkMode.IDLE,
    val media: GameMedia? = null,
    val shouldPlayVideo: Boolean = false,
    val videoMuted: Boolean = true,
    val systemPreviewArt: List<String> = emptyList(),
    /** The focused system id in SYSTEM_GRID mode, so the preview fan uses the right box aspect. */
    val focusedPlatformId: String? = null,
    val title: String? = null
)

/**
 * A process-wide hand-off channel from the interactive UI (bottom screen) to the artwork
 * [ArtworkPresentation] (top screen). [HomeViewModel] publishes a derived snapshot whenever the
 * selection/media changes; the Presentation observes [state]. Kept as a plain @Singleton (not a
 * ViewModel) so it survives independently of any NavBackStackEntry-scoped ViewModel and can be
 * read from the Activity that owns the Presentation.
 */
@Singleton
class ArtworkBus @Inject constructor() {
    private val _state = MutableStateFlow(ArtworkUiState())
    val state: StateFlow<ArtworkUiState> = _state.asStateFlow()

    fun publish(state: ArtworkUiState) {
        _state.value = state
    }
}
