package com.gamelaunch.frontend.pocket.providers.impl

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gamelaunch.frontend.pocket.domain.DiscoveredProviderGame
import com.gamelaunch.frontend.pocket.domain.LaunchContext
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderCapability
import com.gamelaunch.frontend.pocket.providers.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for Moonlight — PC game streaming via Sunshine.
 *
 * Launches a specific PC/application session directly via Moonlight's
 * exported ShortcutTrampoline activity.
 *
 * Target: Sunshine on the PC + Moonlight on the Pocket FIT.
 *
 * Discovery: Phase 9 will enumerate Android launcher shortcuts published by Moonlight
 * (preferred path — no private data access needed). Manual linking is the fallback.
 *
 * NOTE: Verify ShortcutTrampoline activity name and intent extras against the current
 * Moonlight Android source before relying on this implementation.
 */
@Singleton
class MoonlightProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : GameProvider {

    override val id = ProviderId.MOONLIGHT

    override val capabilities = setOf(
        ProviderCapability.DIRECT_LAUNCH,
        ProviderCapability.ANDROID_SHORTCUT,
        ProviderCapability.STREAMING
    )

    override suspend fun isAvailable(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** Phase 9 will implement Android shortcut enumeration. */
    override suspend fun discoverGames(): List<DiscoveredProviderGame> = emptyList()

    override suspend fun launch(target: LaunchTarget, launchContext: LaunchContext): Result<Unit> {
        if (!isAvailable()) {
            return Result.failure(IllegalStateException("Moonlight is not installed"))
        }

        // Extract PC and app identifiers from stored launchData JSON
        val data = runCatching { JSONObject(target.launchData) }.getOrElse { JSONObject() }
        val pcName = data.optString("pcName").ifBlank { null }
        val appName = data.optString("appName").ifBlank { target.displayName }
        val appId = data.optString("appId").ifBlank { null }
        val uuid = data.optString("uuid").ifBlank { null }

        return runCatching {
            val intent = Intent().apply {
                setClassName(PACKAGE, SHORTCUT_ACTIVITY)
                pcName?.let { putExtra("PcName", it) }
                uuid?.let { putExtra("UUID", it) }
                appName.let { putExtra("AppName", it) }
                appId?.let { putExtra("AppId", it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }.getOrElse {
                Log.w(TAG, "ShortcutTrampoline unavailable, opening Moonlight")
                val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
                    ?: throw IllegalStateException("Cannot open Moonlight")
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
            }
            Log.d(TAG, "Launched Moonlight: appName=$appName pc=$pcName")
        }
    }

    companion object {
        const val PACKAGE = "com.limelight"
        /** Exported activity for direct game launch — verify against current Moonlight source */
        const val SHORTCUT_ACTIVITY = "com.limelight.ShortcutTrampoline"
        private const val TAG = "MoonlightProvider"
    }
}
