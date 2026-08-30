package com.gamelaunch.frontend.pocket.launch

import android.util.Log
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.pocket.data.repository.LaunchTargetRepository
import com.gamelaunch.frontend.pocket.display.GamingDisplayManager
import com.gamelaunch.frontend.pocket.domain.DisplayPolicy
import com.gamelaunch.frontend.pocket.domain.LaunchContext
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.performance.AyaPerformanceModeManager
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for the CreteOS provider launch layer.
 *
 * Called by [com.gamelaunch.frontend.domain.usecase.LaunchGameUseCase] before the normal
 * eOr [com.gamelaunch.frontend.launcher.EmulatorLauncher].
 *
 * If no custom launch target exists for a game, [tryLaunch] returns **null** so the caller
 * falls through to the existing emulator launch path — all existing eOr game behaviour is
 * preserved unchanged.
 */
@Singleton
class UnifiedLaunchCoordinator @Inject constructor(
    private val launchTargetRepository: LaunchTargetRepository,
    private val gamingDisplayManager: GamingDisplayManager,
    private val ayaPerformanceModeManager: AyaPerformanceModeManager,
    private val providers: Map<ProviderId, @JvmSuppressWildcards GameProvider>
) {

    init {
        // Keep display/USB state live for launches. Previously this only started after opening the
        // diagnostics screen, so a normal cold-start launch always used the internal profile.
        gamingDisplayManager.start()
    }

    /**
     * Attempt to launch [game] using the pocket provider layer.
     *
     * @return `Result.success` if a provider handled the launch.
     *         `null` if no custom target is registered (caller should fall through to eOr).
     *         `Result.failure` if a target exists but the launch attempt failed.
     */
    suspend fun tryLaunch(game: Game): Result<Unit>? {
        val hostKey = game.romPath
        val targets = launchTargetRepository.getTargetsForGameOnce(hostKey)

        if (targets.isEmpty()) {
            // No custom targets — let eOr handle it as before
            return null
        }

        val preferred = targets.firstOrNull { it.isPreferred && it.isAvailable }
            ?: targets.firstOrNull { it.isAvailable }

        if (preferred == null) {
            Log.w(TAG, "All launch targets unavailable for $hostKey — falling through to eOr")
            return null
        }

        return launchWithProvider(preferred)
    }

    /**
     * Launch [game] using a specific [target] — called from the "Play Using" picker
     * when the user explicitly selects a provider.
     */
    suspend fun launchSpecific(target: LaunchTarget): Result<Unit> =
        launchWithProvider(target)

    private suspend fun launchWithProvider(target: LaunchTarget): Result<Unit> {
        val provider = providers[target.provider]
        if (provider == null) {
            Log.e(TAG, "No provider registered for ${target.provider}")
            return Result.failure(
                IllegalStateException("Provider ${target.provider} not registered")
            )
        }
        if (!provider.isAvailable()) {
            launchTargetRepository.markProviderUnavailable(target.provider)
            return Result.failure(
                IllegalStateException(
                    "${target.provider.displayName} is not installed. " +
                    "Please install it to launch this game."
                )
            )
        }

        // Build launch context using current display state
        gamingDisplayManager.refresh()
        val display = gamingDisplayManager.activeDisplay.value
        val launchContext = LaunchContext(
            // Mirrored USB profiles use a negative synthetic ID and cannot be passed to Android's
            // ActivityOptions.setLaunchDisplayId(). Providers can still use their dimensions.
            destinationDisplayId = display.displayId.takeIf { display.isExternal && it >= 0 },
            displayWidth = display.width,
            displayHeight = display.height,
            refreshRate = display.refreshRate,
            externalDisplayConnected = display.isExternal,
            displayPolicy = DisplayPolicy.AUTO_MATCH_DISPLAY // default; per-game override in Phase 11
        )

        if (target.provider.runsLocally) {
            ayaPerformanceModeManager.useGamingMode()
        } else {
            ayaPerformanceModeManager.useEcoMode()
        }

        return provider.launch(target, launchContext).also { result ->
            // A failed launch leaves CreteOS in the foreground, where Eco is the intended profile.
            if (result.isFailure) ayaPerformanceModeManager.useEcoMode()
        }
    }

    companion object {
        private const val TAG = "UnifiedLaunchCoord"
    }
}
