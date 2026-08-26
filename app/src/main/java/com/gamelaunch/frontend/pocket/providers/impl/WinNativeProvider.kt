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
 * Provider for WinNative — Windows-on-Android x86 runtime.
 *
 * Discovery: imports .desktop shortcut files exported by WinNative's frontend-sync feature.
 * The user selects the export folder once via Storage Access Framework (Phase 6).
 *
 * Launch: sends the .desktop file path/URI to WinNative's supported external launch activity.
 *
 * NOTE: Verify current package name and launch activity against the installed WinNative build.
 * We do NOT read WinNative's private database or container configuration.
 */
@Singleton
class WinNativeProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : GameProvider {

    override val id = ProviderId.WIN_NATIVE

    override val capabilities = setOf(
        ProviderCapability.FILE_SHORTCUT,
        ProviderCapability.LOCAL
    )

    override suspend fun isAvailable(): Boolean = PACKAGES.any { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    }

    /** Phase 6 will implement .desktop file discovery via SAF. */
    override suspend fun discoverGames(): List<DiscoveredProviderGame> = emptyList()

    override suspend fun launch(target: LaunchTarget, launchContext: LaunchContext): Result<Unit> {
        val pkg = PACKAGES.firstOrNull { p ->
            runCatching { context.packageManager.getPackageInfo(p, 0); true }.getOrDefault(false)
        } ?: return Result.failure(IllegalStateException("WinNative is not installed"))

        // launchData contains the .desktop file path or URI string
        val shortcutPath = target.launchData.ifBlank { target.externalId }

        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(pkg)
                data = Uri.parse(shortcutPath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }.getOrElse {
                Log.w(TAG, "Direct shortcut launch failed, opening WinNative")
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                    ?: throw IllegalStateException("Cannot open WinNative")
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
            }
            Log.d(TAG, "Launched WinNative shortcut: $shortcutPath")
        }
    }

    companion object {
        // Verify against current WinNative package — update in PROVIDERS.md when confirmed
        val PACKAGES = listOf("app.winnative", "com.winnative.android")
        private const val TAG = "WinNativeProvider"
    }
}
