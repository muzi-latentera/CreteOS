package com.gamelaunch.frontend.pocket.sync

import android.util.Log
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import com.gamelaunch.frontend.pocket.data.repository.LaunchTargetRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Removes emulator/front-end APKs that older Android scans added as playable games. */
@Singleton
class AndroidLibraryInfrastructureReconciler @Inject constructor(
    private val gameRepository: GameRepository,
    private val launchTargetRepository: LaunchTargetRepository,
    private val packageManagerHelper: PackageManagerHelper
) {
    suspend fun reconcile(games: List<Game>) {
        // The foreground Android scan may delete an excluded emulator row before this reconciler
        // observes it. Clean by canonical package key as well so its target rows cannot become
        // permanent orphans in the separate Pocket database.
        packageManagerHelper.emulatorPackages.forEach { packageName ->
            val hostGameKey = "$PACKAGE_PREFIX$packageName"
            launchTargetRepository.clearAutomaticPreference(hostGameKey)
            launchTargetRepository.getTargetsForGameOnce(hostGameKey).forEach { target ->
                launchTargetRepository.deleteTarget(target.id)
            }
        }

        games.asSequence()
            .filter { it.platformId.equals("android", ignoreCase = true) }
            .filter { it.romPath.startsWith(PACKAGE_PREFIX) }
            .filter { it.romPath.removePrefix(PACKAGE_PREFIX) in packageManagerHelper.emulatorPackages }
            .forEach { game ->
                gameRepository.deleteGame(game.id)
                Log.i(TAG, "Removed emulator frontend from library: ${game.title}")
            }
    }

    private companion object {
        const val TAG = "AndroidLibraryRepair"
        const val PACKAGE_PREFIX = "package:"
    }
}
