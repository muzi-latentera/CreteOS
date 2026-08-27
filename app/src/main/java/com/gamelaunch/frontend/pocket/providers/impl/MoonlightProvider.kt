package com.gamelaunch.frontend.pocket.providers.impl

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
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
 * Verified against Moonlight 12.1 (versionCode 314) on Android 17, 2026-08-26:
 *   - Package: com.limelight
 *   - ShortcutTrampoline activity: com.limelight.ShortcutTrampoline (android:exported=true)
 *   - Moonlight publishes launcher shortcuts (confirmed via `dumpsys shortcut`)
 *
 * Discovery: enumerate Android launcher shortcuts published by Moonlight via LauncherApps.
 * Launch: ShortcutTrampoline with PC/app extras, or startShortcut() if shortcut ID is available.
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

    /**
     * Discover Moonlight games via Android launcher shortcuts.
     * Moonlight publishes one shortcut per paired PC/app combination.
     */
    override suspend fun discoverGames(): List<DiscoveredProviderGame> {
        if (!isAvailable()) return emptyList()

        return runCatching {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
                ?: return@runCatching emptyList()

            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(PACKAGE)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }

            val shortcuts = runCatching {
                launcherApps.getShortcuts(query, Process.myUserHandle())
            }.getOrNull() ?: return@runCatching emptyList()

            shortcuts.mapNotNull { shortcut ->
                val label = shortcut.shortLabel?.toString() ?: return@mapNotNull null
                val shortcutId = shortcut.id

                // Store ONLY the shortcut ID and label — do not fabricate PC name or UUIDs.
                // shortcut.activity is the Moonlight activity component, not a PC identifier.
                // startShortcut() only needs package + shortcutId; PcName/UUID are NOT needed for that path.
                val launchData = org.json.JSONObject().apply {
                    put("shortcutId", shortcutId)
                    put("appName", label)
                    // pcName, uuid, appId intentionally omitted — unknown from shortcut metadata
                    // User can supply these via manual linking if ShortcutTrampoline fallback is needed
                }.toString()

                DiscoveredProviderGame(
                    provider    = ProviderId.MOONLIGHT,
                    externalId  = shortcutId,
                    source      = "STREAMING",
                    displayName = label,
                    launchData  = launchData,
                    hostGameKey = null // requires manual or title-based matching
                )
            }
        }.getOrElse { e ->
            Log.w(TAG, "Shortcut discovery failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun launch(target: LaunchTarget, launchContext: LaunchContext): Result<Unit> {
        if (!isAvailable()) {
            return Result.failure(IllegalStateException("Moonlight is not installed"))
        }

        val data = runCatching { JSONObject(target.launchData) }.getOrElse { JSONObject() }
        val shortcutId = data.optString("shortcutId").ifBlank { null }

        // Primary path: startShortcut() — requires only package + shortcutId
        // This is reliable and does not require PC name/UUID
        if (shortcutId != null) {
            val shortcutResult = runCatching {
                val launcherApps = context.getSystemService(LauncherApps::class.java)
                launcherApps?.startShortcut(
                    PACKAGE,
                    shortcutId,
                    null,
                    null,
                    Process.myUserHandle()
                ) ?: throw IllegalStateException("LauncherApps service unavailable")
                Log.d(TAG, "Launched via startShortcut: $shortcutId")
            }
            if (shortcutResult.isSuccess) return Result.success(Unit)
            Log.w(TAG, "startShortcut failed: ${shortcutResult.exceptionOrNull()?.message}")
        }

        // Secondary path: ShortcutTrampoline intent — only when we have verified PC + app metadata
        // Do NOT fabricate missing fields.
        val appName = data.optString("appName").ifBlank { null }
        val appId   = data.optString("appId").ifBlank { null }
        val pcName  = data.optString("pcName").ifBlank { null }  // only if user provided via manual link
        val uuid    = data.optString("uuid").ifBlank { null }

        val hasSufficientMetadata = appName != null && (uuid != null || pcName != null)

        if (hasSufficientMetadata) {
            return runCatching {
                val intent = Intent().apply {
                    setClassName(PACKAGE, SHORTCUT_TRAMPOLINE)
                    appName?.let { putExtra("AppName", it) }
                    appId?.let { putExtra("AppId", it) }
                    pcName?.let { putExtra("PcName", it) }
                    uuid?.let { putExtra("UUID", it) }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "Launched via ShortcutTrampoline: appName=$appName")
            }
        }

        // Final fallback: open Moonlight's library — user selects the game
        Log.w(TAG, "Insufficient Moonlight metadata for direct launch — opening library")
        return runCatching {
            val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
                ?: throw IllegalStateException("Moonlight not found")
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        }
    }

    companion object {
        // Verified against Moonlight 12.1 (versionCode 314), 2026-08-26
        const val PACKAGE = "com.limelight"
        const val SHORTCUT_TRAMPOLINE = "com.limelight.ShortcutTrampoline"
        private const val TAG = "MoonlightProvider"
    }
}
