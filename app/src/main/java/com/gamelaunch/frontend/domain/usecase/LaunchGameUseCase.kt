package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.FriendRepository
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.launcher.EmulatorLauncher
import com.gamelaunch.frontend.pocket.launch.UnifiedLaunchCoordinator
import com.gamelaunch.frontend.pocket.performance.AyaPerformanceModeManager
import javax.inject.Inject

class LaunchGameUseCase @Inject constructor(
    private val emulatorLauncher: EmulatorLauncher,
    private val gameRepository: GameRepository,
    private val friendRepository: FriendRepository,
    private val lockedModeRepository: LockedModeRepository,
    private val unifiedLaunchCoordinator: UnifiedLaunchCoordinator,
    private val ayaPerformanceModeManager: AyaPerformanceModeManager
) {
    suspend operator fun invoke(game: Game): Result<Unit> {
        if (lockedModeRepository.isLocked()) {
            val persisted = gameRepository.getGameById(game.id)
            if (persisted == null || !persisted.isAvailableInLockedMode) {
                return Result.failure(IllegalStateException("This game is unavailable in Locked Mode."))
            }
        }
        // Try pocket provider layer first; fall through to eOr's existing launcher
        // if no custom target is registered for this game.
        val coordinatedResult = unifiedLaunchCoordinator.tryLaunch(game)
        val result = if (coordinatedResult != null) {
            coordinatedResult
        } else {
            // eOr's fallback path launches an emulator or Android app on-device.
            ayaPerformanceModeManager.useGamingMode()
            emulatorLauncher.launch(game).also { fallbackResult ->
                if (fallbackResult.isFailure) ayaPerformanceModeManager.useEcoMode()
            }
        }
        if (result.isSuccess) {
            gameRepository.recordPlay(game.id)
            // Refresh the profile we share with friends (no-op when Friends is disabled).
            runCatching { friendRepository.publishMyProfile() }
        }
        return result
    }
}
