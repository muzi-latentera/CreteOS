package com.gamelaunch.frontend.pocket.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gamelaunch.frontend.pocket.data.GameSessionDao
import com.gamelaunch.frontend.pocket.data.GameSessionEntity
import com.gamelaunch.frontend.pocket.data.HltbCacheDao
import com.gamelaunch.frontend.pocket.data.HltbCacheEntity
import com.gamelaunch.frontend.pocket.data.SteamMetadataDao
import com.gamelaunch.frontend.pocket.data.SteamMetadataEntity
import com.gamelaunch.frontend.pocket.data.db.dao.GameLaunchPreferenceDao
import com.gamelaunch.frontend.pocket.data.db.dao.LaunchTargetDao
import com.gamelaunch.frontend.pocket.data.db.dao.ManualGameLinkDao
import com.gamelaunch.frontend.pocket.data.db.entity.GameLaunchPreferenceEntity
import com.gamelaunch.frontend.pocket.data.db.entity.LaunchTargetEntity
import com.gamelaunch.frontend.pocket.data.db.entity.ManualGameLinkEntity

/**
 * Separate Room database for CreteOS pocket layer.
 * Intentionally kept separate from eOr's AppDatabase so upstream merges don't touch our schema.
 * File: creteos_pocket.db
 *
 * Version history:
 *   1 → original (launch_targets, game_launch_preferences, manual_game_links)
 *   2 → added hltb_cache
 *   3 → added steam_metadata, game_sessions
 *   9 → include provider source in launch-target identity (Steam/Epic IDs may overlap)
 *
 * NEVER use fallbackToDestructiveMigration — the launch_targets and preferences
 * tables contain user-owned data (GameNative links, preferred providers) that
 * must survive app updates.
 */
@Database(
    entities = [
        LaunchTargetEntity::class,
        GameLaunchPreferenceEntity::class,
        ManualGameLinkEntity::class,
        HltbCacheEntity::class,
        SteamMetadataEntity::class,
        GameSessionEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class PocketDatabase : RoomDatabase() {

    abstract fun launchTargetDao(): LaunchTargetDao
    abstract fun gameLaunchPreferenceDao(): GameLaunchPreferenceDao
    abstract fun manualGameLinkDao(): ManualGameLinkDao
    abstract fun hltbCacheDao(): HltbCacheDao
    abstract fun steamMetadataDao(): SteamMetadataDao
    abstract fun gameSessionDao(): GameSessionDao

    companion object {
        const val DATABASE_NAME = "creteos_pocket.db"

        /** v1 → v2: add hltb_cache table */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS hltb_cache (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        steam_app_id TEXT NOT NULL,
                        game_title TEXT NOT NULL,
                        hltb_id INTEGER,
                        main_story_seconds INTEGER NOT NULL DEFAULT 0,
                        main_extra_seconds INTEGER NOT NULL DEFAULT 0,
                        completionist_seconds INTEGER NOT NULL DEFAULT 0,
                        cached_at_ms INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_hltb_cache_steam_app_id ON hltb_cache(steam_app_id)"
                )
            }
        }

        /** v2 → v3: add steam_metadata and game_sessions tables */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS steam_metadata (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        steam_app_id TEXT NOT NULL,
                        playtime_minutes INTEGER NOT NULL DEFAULT 0,
                        last_played_ms INTEGER,
                        achievements_unlocked INTEGER NOT NULL DEFAULT 0,
                        achievements_total INTEGER NOT NULL DEFAULT 0,
                        achievements_synced_at_ms INTEGER,
                        developer TEXT,
                        publisher TEXT,
                        description TEXT,
                        release_date TEXT,
                        updated_at_ms INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_steam_metadata_steam_app_id ON steam_metadata(steam_app_id)"
                )

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS game_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        game_key TEXT NOT NULL,
                        started_at_ms INTEGER NOT NULL,
                        ended_at_ms INTEGER,
                        duration_minutes INTEGER NOT NULL DEFAULT 0,
                        provider TEXT NOT NULL DEFAULT 'unknown'
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_game_sessions_game_key ON game_sessions(game_key)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_game_sessions_started_at ON game_sessions(started_at_ms)"
                )
            }
        }

        /** Also handle the case where a fresh install starts at v1 and needs to reach v3 */
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
            }
        }

        /** v3→v4: add gfn_game_id to steam_metadata for deep-link support */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE steam_metadata ADD COLUMN gfn_game_id TEXT")
            }
        }

        /** v4→v5: add igdb_cover_url for fallback artwork */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE steam_metadata ADD COLUMN igdb_cover_url TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE steam_metadata ADD COLUMN igdb_hero_url TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE steam_metadata ADD COLUMN is_local INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v7→v8: add rom_abs_path to steam_metadata for emulator launch */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE steam_metadata ADD COLUMN rom_abs_path TEXT")
            }
        }

        /** v8→v9: GameNative numeric IDs are only unique within a source/store. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_launch_targets_hostGameKey_provider_externalId")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_launch_targets_hostGameKey_provider_source_externalId " +
                        "ON launch_targets(hostGameKey, provider, source, externalId)"
                )
            }
        }

        fun create(context: Context): PocketDatabase =
            Room.databaseBuilder(context, PocketDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build()
    }
}
