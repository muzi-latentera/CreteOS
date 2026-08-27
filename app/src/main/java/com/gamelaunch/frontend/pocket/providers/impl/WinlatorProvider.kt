package com.gamelaunch.frontend.pocket.providers.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.gamelaunch.frontend.pocket.domain.DiscoveredProviderGame
import com.gamelaunch.frontend.pocket.domain.LaunchContext
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderCapability
import com.gamelaunch.frontend.pocket.providers.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for Winlator CMod (and compatible frontend-export-capable Winlator variants).
 *
 * Target: builds that export .desktop shortcut files to a shared storage frontend folder,
 * typically: Downloads/Winlator/Frontend/
 *
 * Discovery: indexes exported .desktop files (Phase 7).
 * Launch: opens the shortcut via Winlator's supported external mechanism.
 *
 * NOTE: Vanilla Winlator does not have a stable external direct-launch interface.
 * Only fork builds (CMod, etc.) with explicit frontend-export support are targeted.
 * Verify the current package name and launch contract against the installed build.
 */
@Singleton
class WinlatorProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : GameProvider {

    override val id = ProviderId.WINLATOR

    override val capabilities = setOf(
        ProviderCapability.FILE_SHORTCUT,
        ProviderCapability.LOCAL
    )

    override suspend fun isAvailable(): Boolean = PACKAGES.any { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    }

    /** Phase 7 will implement .desktop discovery from the Winlator frontend export folder. */
    override suspend fun discoverGames(): List<DiscoveredProviderGame> = emptyList()

    override suspend fun launch(target: LaunchTarget, launchContext: LaunchContext): Result<Unit> {
        val pkg = PACKAGES.firstOrNull { p ->
            runCatching { context.packageManager.getPackageInfo(p, 0); true }.getOrDefault(false)
        } ?: return Result.failure(IllegalStateException("Winlator CMod is not installed"))

        val shortcutPath = target.launchData.ifBlank { target.externalId }

        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(pkg)
                data = Uri.parse(shortcutPath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }.getOrElse {
                Log.w(TAG, "Direct shortcut launch failed, opening Winlator")
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                    ?: throw IllegalStateException("Cannot open Winlator")
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
            }
            Log.d(TAG, "Launched Winlator shortcut: $shortcutPath")
        }
    }

    companion object {
        // Known package IDs — CMod is the primary target; update in PROVIDERS.md when confirmed
        val PACKAGES = listOf("com.winlator", "net.qiujuer.winlator", "com.winlator.cmod")
        /** Default frontend export path used by Winlator CMod */
        const val DEFAULT_EXPORT_PATH = "Downloads/Winlator/Frontend"
        private const val TAG = "WinlatorProvider"
    }
}
