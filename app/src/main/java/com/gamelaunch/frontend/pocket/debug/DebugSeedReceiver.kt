package com.gamelaunch.frontend.pocket.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gamelaunch.frontend.pocket.data.db.PocketDatabase
import com.gamelaunch.frontend.pocket.data.IgdbSeedData
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

        // Handle DELETE_GAME action
        if (intent.action == ACTION_DELETE) {
            val appId = intent.getStringExtra("appId") ?: return
            val source = intent.getStringExtra("source") ?: "STEAM"
            val rawTitle = intent.getStringExtra("title")
            val title = rawTitle?.let { try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it } }
            val hostKey = if (source == "STEAM") "steam:$appId" else "steam:$source:$appId"
            CoroutineScope(Dispatchers.IO).launch {
                val eorDbFile = context.getDatabasePath("gamelauncher.db")
                if (eorDbFile.exists()) {
                    val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                        eorDbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
                    // Try romPath match first
                    var rows = db.delete("games", "rom_path = ?", arrayOf(hostKey))
                    // Also try alternate romPath formats
                    if (rows == 0) rows += db.delete("games", "rom_path LIKE ?", arrayOf("steam:%:$appId"))
                    if (rows == 0 && title != null) rows += db.delete("games", "title = ?", arrayOf(title))
                    db.close()
                    Log.i(TAG, "Deleted $rows game(s) with romPath=$hostKey title=$title")
                }
                val pocketDb = PocketDatabase.create(context)
                pocketDb.launchTargetDao().getTargetsForGameOnce(hostKey).forEach {
                    pocketDb.launchTargetDao().delete(it.id)
                }
            }
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

                    // 3. Insert artwork into game_media table
                    if (rowId > 0) {
                        val mediaValues = android.content.ContentValues().apply {
                            put("game_id", rowId)
                            put("scraper_timestamp_ms", now)
                        }

                        if (source == "STEAM") {
                            // Steam CDN URLs — portrait + hero
                            val boxArtUrl  = "https://cdn.akamai.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
                            val heroUrl    = "https://cdn.akamai.steamstatic.com/steam/apps/$appId/library_hero.jpg"
                            mediaValues.put("box_art_remote", boxArtUrl)
                            mediaValues.put("wheel_logo_remote", heroUrl)
                            mediaValues.put("screenshot_remote", heroUrl)
                        } else {
                            // Non-Steam: use IGDB cover + hero from seed data
                            val coverUrl = IgdbSeedData.coverUrlFor(appId)
                            val heroUrl  = IgdbSeedData.heroUrlFor(appId)
                            if (coverUrl != null) mediaValues.put("box_art_remote", coverUrl)
                            if (heroUrl  != null) mediaValues.put("screenshot_remote", heroUrl)
                        }

                        eorDb.insertWithOnConflict(
                            "game_media", null, mediaValues,
                            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                        )
                        Log.i(TAG, "eOr DB: inserted artwork for '$title'")
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

        // Handle SEED_ROM action — insert/update a ROM game with a clean title
        if (intent.action == ACTION_SEED_ROM) {
            val rawTitle = intent.getStringExtra("title") ?: return
            val title = try { java.net.URLDecoder.decode(rawTitle, "UTF-8") } catch (_: Exception) { rawTitle }
            val platformId = intent.getStringExtra("platformId") ?: return
            val romPath = intent.getStringExtra("romPath") ?: return
            val romFilename = romPath.substringAfterLast("/")
            CoroutineScope(Dispatchers.IO).launch {
                val eorDbFile = context.getDatabasePath("gamelauncher.db")
                if (!eorDbFile.exists()) { Log.w(TAG, "gamelauncher.db not found"); return@launch }
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    eorDbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
                val exists = db.rawQuery("SELECT id FROM games WHERE rom_path=?", arrayOf(romPath))
                    .use { it.count > 0 }
                if (exists) {
                    db.execSQL("UPDATE games SET title=? WHERE rom_path=?", arrayOf(title, romPath))
                } else {
                    val cv = android.content.ContentValues().apply {
                        put("title", title); put("rom_path", romPath); put("rom_filename", romFilename)
                        put("platform_id", platformId); put("date_added", System.currentTimeMillis())
                        put("is_favorite", 0); put("play_count", 0); put("is_scraped", 0)
                    }
                    db.insertWithOnConflict("games", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.close()
                Log.d(TAG, "SEED_ROM: $title [$platformId]")
            }
            return
        }
    }
        const val ACTION        = "io.latent.creteos.SEED_GAME"
        const val ACTION_DELETE   = "io.latent.creteos.DELETE_GAME"
        const val ACTION_SEED_ROM = "io.latent.creteos.SEED_ROM"
        private const val TAG   = "DebugSeedReceiver"
    }
}
