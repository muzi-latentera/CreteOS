package com.gamelaunch.frontend.pocket.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log
import com.gamelaunch.frontend.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only receiver for triggering a provider sync or testing shortcut discovery via ADB.
 *
 * Usage — trigger Moonlight shortcut test:
 *   adb shell am broadcast \
 *     -a io.latent.creteos.DEBUG_SYNC \
 *     -n io.latent.creteos/com.gamelaunch.frontend.pocket.debug.DebugSyncReceiver \
 *     --es action moonlight_shortcuts
 *
 * Usage — trigger full provider sync:
 *   adb shell am broadcast \
 *     -a io.latent.creteos.DEBUG_SYNC \
 *     -n io.latent.creteos/com.gamelaunch.frontend.pocket.debug.DebugSyncReceiver \
 *     --es action sync_all
 */
class DebugSyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        if (intent.action != ACTION) return

        val action = intent.getStringExtra("action") ?: "moonlight_shortcuts"

        CoroutineScope(Dispatchers.IO).launch {
            when (action) {
                "moonlight_shortcuts" -> testMoonlightShortcuts(context)
                else -> Log.w(TAG, "Unknown debug action: $action")
            }
        }
    }

    private fun testMoonlightShortcuts(context: Context) {
        Log.i(TAG, "=== Moonlight shortcut discovery test ===")
        Log.i(TAG, "Running as HOME launcher: checking LauncherApps.getShortcuts()")

        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (launcherApps == null) {
            Log.e(TAG, "LauncherApps service unavailable")
            return
        }

        val query = LauncherApps.ShortcutQuery().apply {
            setPackage("com.limelight")
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        }

        val result = runCatching {
            launcherApps.getShortcuts(query, Process.myUserHandle())
        }

        result.onSuccess { shortcuts ->
            if (shortcuts.isNullOrEmpty()) {
                Log.w(TAG, "Moonlight shortcuts: EMPTY — no per-game shortcuts published")
                Log.w(TAG, "Moonlight publishes shortcuts only for the active HOME launcher.")
                Log.w(TAG, "If this is empty while CreteOS is HOME, Moonlight does not publish per-game shortcuts on this build.")
            } else {
                Log.i(TAG, "Moonlight shortcuts found: ${shortcuts.size}")
                shortcuts.forEach { shortcut ->
                    Log.i(TAG, "  id=${shortcut.id} label='${shortcut.shortLabel}' " +
                          "isDynamic=${shortcut.isDynamic} isPinned=${shortcut.isPinned} " +
                          "activity=${shortcut.activity}")
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "getShortcuts() threw: ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "This usually means CreteOS is NOT the default HOME launcher.")
        }
    }

    companion object {
        const val ACTION = "io.latent.creteos.DEBUG_SYNC"
        private const val TAG = "DebugSyncReceiver"
    }
}
