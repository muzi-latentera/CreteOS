package com.gamelaunch.frontend.pocket.emulation

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import com.gamelaunch.frontend.pocket.data.SteamMetadataDao
import com.gamelaunch.frontend.pocket.data.SteamMetadataEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds emulated games into eOr's gamelauncher.db and creates steam_metadata entries
 * in PocketDatabase for IGDB metadata lookup.
 *
 * IMPORTANT: Do NOT merge ROMs with PC/Steam games even if titles match.
 * Each ROM is a separate game entry keyed by its unique romPath (e.g. "emu:gc:luigis_mansion").
 */
@Singleton
class EmulationSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val romScanner: RomScanner,
    private val steamMetadataDao: SteamMetadataDao
) {

    data class SeedResult(
        val gamesInserted: Int,
        val gamesSkipped: Int,
        val errors: List<String>
    )

    /**
     * Scan ROMs from the given URI and seed them into the database.
     *
     * @param romRootUri SAF tree URI for the ROM root directory
     * @return SeedResult with counts of inserted/skipped games
     */
    suspend fun scanAndSeed(romRootUri: Uri): SeedResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var inserted = 0
        var skipped = 0

        // Scan for ROMs
        val roms = romScanner.scan(context, romRootUri)
        if (roms.isEmpty()) {
            Log.w(TAG, "No ROMs found at $romRootUri")
            return@withContext SeedResult(0, 0, listOf("No ROMs found in the selected directory"))
        }

        Log.i(TAG, "Found ${roms.size} ROMs, seeding to database...")

        // Open eOr's database
        val eorDbFile = context.getDatabasePath("gamelauncher.db")
        if (!eorDbFile.exists()) {
            Log.e(TAG, "eOr gamelauncher.db not found at ${eorDbFile.path}")
            return@withContext SeedResult(0, 0, listOf("Game library database not found"))
        }

        val eorDb = runCatching {
            SQLiteDatabase.openDatabase(eorDbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        }.getOrElse { e ->
            Log.e(TAG, "Failed to open gamelauncher.db: ${e.message}")
            return@withContext SeedResult(0, 0, listOf("Failed to open game library: ${e.message}"))
        }

        try {
            val now = System.currentTimeMillis()

            for (rom in roms) {
                try {
                    // Check if game already exists by romPath
                    val cursor = eorDb.rawQuery(
                        "SELECT id, title FROM games WHERE rom_path = ?",
                        arrayOf(rom.romPath)
                    )
                    val exists = cursor.count > 0
                    var existingTitle: String? = null
                    var existingId: Long? = null
                    if (cursor.moveToFirst()) {
                        existingId = cursor.getLong(0)
                        existingTitle = cursor.getString(1)
                    }
                    cursor.close()

                    if (exists && existingId != null) {
                        // Game exists — check if title needs updating (re-clean may produce better title)
                        if (existingTitle != rom.title) {
                            eorDb.execSQL(
                                "UPDATE games SET title = ? WHERE id = ?",
                                arrayOf(rom.title, existingId)
                            )
                            Log.d(TAG, "Updated title: '$existingTitle' -> '${rom.title}'")
                        }
                        skipped++
                        continue
                    }

                    // Insert into games table (INSERT OR IGNORE pattern)
                    val values = ContentValues().apply {
                        put("title", rom.title)
                        put("rom_path", rom.romPath)
                        put("rom_filename", "${rom.title}.${rom.fileExtension}")
                        put("platform_id", rom.system.id)
                        put("is_favorite", 0)
                        put("play_count", 0)
                        put("date_added", now)
                        put("is_scraped", 0)
                        put("available_in_locked_mode", 1)
                    }

                    val rowId = eorDb.insertWithOnConflict(
                        "games", null, values,
                        SQLiteDatabase.CONFLICT_IGNORE
                    )

                    if (rowId > 0) {
                        inserted++
                        Log.d(TAG, "Inserted ROM: ${rom.title} (rowId=$rowId)")

                        // Insert placeholder into game_media for artwork
                        val mediaValues = ContentValues().apply {
                            put("game_id", rowId)
                            put("scraper_timestamp_ms", now)
                        }
                        eorDb.insertWithOnConflict(
                            "game_media", null, mediaValues,
                            SQLiteDatabase.CONFLICT_IGNORE
                        )
                    } else {
                        skipped++
                        Log.d(TAG, "ROM already exists (conflict): ${rom.title}")
                    }

                    // Create steam_metadata entry for IGDB lookup
                    // steam_app_id = romPath for emulated games
                    val existingMetadata = steamMetadataDao.getByAppId(rom.romPath)
                    if (existingMetadata == null) {
                        steamMetadataDao.upsert(
                            SteamMetadataEntity(
                                steamAppId = rom.romPath,
                                playtimeMinutes = 0,
                                romAbsPath = rom.absolutePath,
                                updatedAtMs = now
                            )
                        )
                        Log.d(TAG, "Created steam_metadata for ${rom.romPath}")
                    } else if (existingMetadata.romAbsPath != rom.absolutePath) {
                        // Update the absolute path if it changed
                        steamMetadataDao.upsert(
                            existingMetadata.copy(
                                romAbsPath = rom.absolutePath,
                                updatedAtMs = now
                            )
                        )
                    }

                } catch (e: Exception) {
                    val error = "Failed to seed ${rom.title}: ${e.message}"
                    Log.e(TAG, error, e)
                    errors.add(error)
                }
            }
        } finally {
            eorDb.close()
        }

        Log.i(TAG, "Seed complete: inserted=$inserted, skipped=$skipped, errors=${errors.size}")
        SeedResult(inserted, skipped, errors)
    }

    companion object {
        private const val TAG = "EmulationSeeder"
    }
}
