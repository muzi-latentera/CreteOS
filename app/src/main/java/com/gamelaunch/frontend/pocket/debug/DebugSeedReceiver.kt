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
        if (!com.gamelaunch.frontend.BuildConfig.DEBUG) {
            Log.w(TAG, "DebugSeedReceiver called in non-debug build — ignoring")
            return
        }
        if (intent.action != ACTION) return

        val appId = intent.getStringExtra("appId") ?: run {
            Log.e(TAG, "Missing appId extra"); return
        }
        val rawTitle       = intent.getStringExtra("title") ?: "Game $appId"
        // URL-decode if encoded (allows colons, apostrophes etc to survive adb --es)
        val title          = try { java.net.URLDecoder.decode(rawTitle, "UTF-8") } catch (_: Exception) { rawTitle }
        val source         = intent.getStringExtra("source") ?: "STEAM"
        val hostKey        = if (source == "STEAM") "steam:$appId" else "steam:$source:$appId"
        val playtimeMins   = intent.getLongExtra("playtimeMinutes", 0L)
        val lastPlayedEpoch= intent.getLongExtra("lastPlayedEpoch", 0L)
        // Convert Steam epoch seconds → milliseconds; 0 means never played
        val lastPlayedMs   = if (lastPlayedEpoch > 0L) lastPlayedEpoch * 1000L else null

        Log.i(TAG, "Seeding: title=$title appId=$appId playtime=${playtimeMins}m lastPlayed=$lastPlayedMs")

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
                val eorDbFile = context.getDatabasePath("gamelauncher.db")
                if (eorDbFile.exists()) {
                    val eorDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                        eorDbFile.path, null,
                        android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                    )
                    val now = System.currentTimeMillis()
                    // Map source → platform_id used by the UI for icon/colour
                    val platformId = when (source.uppercase()) {
                        "STEAM"    -> "steam"
                        "EA"       -> "ea"
                        "GAMEPASS", "XBOX" -> "gamepass"
                        "GOG"      -> "gog"
                        "UBISOFT"  -> "ubisoft"
                        "EPIC"     -> "epic"
                        "ANDROID"  -> "android"
                        "MOONLIGHT" -> "moonlight"
                        "GFN"      -> "gfn"
                        else       -> source.lowercase()
                    }
                    val values = android.content.ContentValues().apply {
                        put("title", title)
                        put("rom_path", hostKey)
                        put("rom_filename", "$title.$platformId")
                        put("platform_id", platformId)
                        put("is_favorite", 0)
                        put("play_count", 0) // launch count only — Steam playtime goes into SteamMetadataEntity
                        put("date_added", now)
                        put("is_scraped", 0)
                        put("available_in_locked_mode", 1)
                        if (lastPlayedMs != null) put("last_played_ms", lastPlayedMs)
                    }
                    val rowId = eorDb.insertWithOnConflict(
                        "games", null, values,
                        android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                    )
                    Log.i(TAG, "eOr DB: inserted game rowId=$rowId for '$title' (romPath=$hostKey)")

                    // 3. Insert Steam CDN artwork into game_media table
                    // Try portrait (library_600x900) as box art; hero (library_hero) as background
                    // Both are stored — Coil will load whichever succeeds at display time
                    if (source == "STEAM" && rowId > 0) {
                        val boxArtUrl  = "https://cdn.akamai.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
                        val heroUrl    = "https://cdn.akamai.steamstatic.com/steam/apps/$appId/library_hero.jpg"
                        val capsuleUrl = "https://cdn.akamai.steamstatic.com/steam/apps/$appId/capsule_616x353.jpg"
                        val mediaValues = android.content.ContentValues().apply {
                            put("game_id", rowId)
                            // box_art_remote: portrait preferred, hero as fallback stored in wheel_logo_remote
                            put("box_art_remote", boxArtUrl)
                            put("wheel_logo_remote", heroUrl)   // repurposed as portrait fallback
                            // screenshot_remote → effectiveBackground (hero for detail screen)
                            put("screenshot_remote", heroUrl)
                            put("scraper_timestamp_ms", now)
                        }
                        eorDb.insertWithOnConflict(
                            "game_media", null, mediaValues,
                            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                        )
                        Log.i(TAG, "eOr DB: inserted artwork for '$title' — cover=$boxArtUrl hero=$heroUrl")
                    }
                    eorDb.close()
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
