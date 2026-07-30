package com.gamelaunch.frontend.ui.perf

import androidx.compose.runtime.staticCompositionLocalOf
import com.gamelaunch.frontend.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's single "run lighter" signal. True whenever animations should be reduced and preview
 * video should be delayed — to keep browsing smooth on weak chipsets (e.g. the RG DS's RK3568).
 *
 * `reduced = BuildConfig.LOW_POWER || performanceModeSetting || dualScreenActive`. The lite build
 * bakes `LOW_POWER = true`, so it starts reduced from process launch (before [MainActivity] wires
 * the runtime signals); the full build starts false and is updated by MainActivity.
 */
@Singleton
class PerformanceState @Inject constructor() {
    private val _reduced = MutableStateFlow(BuildConfig.LOW_POWER)
    val reduced: StateFlow<Boolean> = _reduced.asStateFlow()

    fun set(reduced: Boolean) {
        _reduced.value = reduced
    }
}

/**
 * Read anywhere in the Compose tree to draw lighter: skip the focused card's idle animation and use
 * smaller shadows. Provided at the root by [MainActivity] from [PerformanceState.reduced]. Defaults
 * to false, so nothing changes unless a build/setting/device opts in.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }
