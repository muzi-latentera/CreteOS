package com.gamelaunch.frontend.pocket.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gamelaunch.frontend.pocket.data.db.PocketDatabase
import com.gamelaunch.frontend.pocket.data.db.entity.LaunchTargetEntity
import com.gamelaunch.frontend.pocket.providers.ProviderId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug broadcast receiver for seeding games via ADB.
 * Seeds both eOr's gamelauncher.db (games table) AND our PocketDatabase (launch_targets).
 *
 * Usage:
 *   adb shell am broadcast \
 *     -a io.latent.creteos.SEED_GAME \
 *     -n io.latent.creteos/com.gamelaunch.frontend.pocket.debug.DebugSeedReceiver \
 *     --es appId 107100 \
 *     --es title "Bastion" \
 *     --es source STEAM
 */
class DebugSeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val appId = intent.getStringExtra("appId") ?: run {
            Log.e(TAG, "Missing appId extra"); return
        }
        val title = intent.getStringExtra("title") ?: "Game $appId"
        val source = intent.getStringExtra("source") ?: "STEAM"
        val hostKey = if (source == "STEAM") "steam:$appId" else "steam:$source:$appId"

        Log.i(TAG, "Seeding: title=$title appId=$appId source=$source hostKey=$hostKey")

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                // 1. Seed into PocketDatabase launch targets
                val pocketDb = PocketDatabase.create(context)
                val entity = LaunchTargetEntity(
                    hostGameKey = hostKey,
                    provider = ProviderId.GAME_NATIVE.name,
                    externalId = appId,
                    source = source,
                    displayName = title,
                    launchData = "{}",
                    isAvailable = true,
                    isPreferred = true
                )
                val targetId = pocketDb.launchTargetDao().upsert(entity)
                Log.i(TAG, "Pocket DB: seeded launch target id=$targetId for '$title'")

                // 2. Seed into eOr's gamelauncher.db games table using raw SQL
                // (We don't have eOr's Hilt graph here, so we open the DB directly via SQLite)
                val eorDbFile = context.getDatabasePath("gamelauncher.db")
                if (eorDbFile.exists()) {
                    val eorDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                        eorDbFile.path, null,
                        android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                    )
                    val now = System.currentTimeMillis()
                    val values = android.content.ContentValues().apply {
                        put("title", title)
                        put("rom_path", hostKey)
                        put("rom_filename", "$title.steam")
                        put("platform_id", "steam")
                        put("is_favorite", 0)
                        put("play_count", 0)
                        put("date_added", now)
                        put("is_scraped", 0)
                        put("available_in_locked_mode", 1)
                    }
                    val rowId = eorDb.insertWithOnConflict(
                        "games", null, values,
                        android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
                    )
                    eorDb.close()
                    Log.i(TAG, "eOr DB: inserted game rowId=$rowId for '$title' (romPath=$hostKey)")
                } else {
                    Log.w(TAG, "eOr gamelauncher.db not found at ${eorDbFile.path}")
                }
            }.onFailure { e ->
                Log.e(TAG, "Seed failed: ${e.message}", e)
            }
        }
    }

    companion object {
        const val ACTION = "io.latent.creteos.SEED_GAME"
        private const val TAG = "DebugSeedReceiver"
    }
}
