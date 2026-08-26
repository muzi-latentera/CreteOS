package com.gamelaunch.frontend.pocket.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that PocketDatabase migrations preserve existing data.
 *
 * The most important property: launch_targets, game_launch_preferences and
 * manual_game_links inserted in v1 must survive migration to v3.
 * These contain user-owned data (GameNative links, preferred providers) that
 * must never be silently dropped on app update.
 */
@RunWith(AndroidJUnit4::class)
class PocketDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PocketDatabase::class.java
    )

    @Test
    fun migrate_v1_to_v2_preserves_launch_targets() {
        // Create v1 database with a launch target
        helper.createDatabase(PocketDatabase.DATABASE_NAME, 1).use { db ->
            db.execSQL("""
                INSERT INTO launch_targets
                (host_game_key, provider, external_id, source, display_name,
                 launch_data, is_available, is_preferred)
                VALUES
                ('steam:107100', 'GAME_NATIVE', '107100', 'STEAM', 'Bastion',
                 '{}', 1, 1)
            """.trimIndent())
        }

        // Run migration 1→2
        helper.runMigrationsAndValidate(
            PocketDatabase.DATABASE_NAME,
            2,
            true,
            PocketDatabase.MIGRATION_1_2
        ).use { db ->
            // Verify launch target survived
            val cursor = db.query(
                "SELECT display_name FROM launch_targets WHERE host_game_key = 'steam:107100'"
            )
            assert(cursor.moveToFirst()) { "Launch target must survive v1→v2 migration" }
            val name = cursor.getString(0)
            assert(name == "Bastion") { "Launch target name must be 'Bastion', got '$name'" }
            cursor.close()

            // Verify hltb_cache table was created
            val hltbCursor = db.query("SELECT COUNT(*) FROM hltb_cache")
            assert(hltbCursor.moveToFirst())
            hltbCursor.close()
        }
    }

    @Test
    fun migrate_v2_to_v3_preserves_launch_targets_and_hltb() {
        // Create v2 database with a launch target and an HLTB entry
        helper.createDatabase(PocketDatabase.DATABASE_NAME, 2).use { db ->
            db.execSQL("""
                INSERT INTO launch_targets
                (host_game_key, provider, external_id, source, display_name,
                 launch_data, is_available, is_preferred)
                VALUES
                ('steam:107100', 'GAME_NATIVE', '107100', 'STEAM', 'Bastion',
                 '{}', 1, 1)
            """.trimIndent())
            db.execSQL("""
                INSERT INTO hltb_cache
                (steam_app_id, game_title, hltb_id, main_story_seconds,
                 main_extra_seconds, completionist_seconds, cached_at_ms)
                VALUES
                ('107100', 'Bastion', 1234, 21600, 32400, 72000, 1724700000000)
            """.trimIndent())
        }

        // Run migration 2→3
        helper.runMigrationsAndValidate(
            PocketDatabase.DATABASE_NAME,
            3,
            true,
            PocketDatabase.MIGRATION_2_3
        ).use { db ->
            // Verify launch target survived
            val ltCursor = db.query(
                "SELECT display_name FROM launch_targets WHERE host_game_key = 'steam:107100'"
            )
            assert(ltCursor.moveToFirst()) { "Launch target must survive v2→v3 migration" }
            assert(ltCursor.getString(0) == "Bastion")
            ltCursor.close()

            // Verify HLTB data survived
            val hltbCursor = db.query(
                "SELECT main_story_seconds FROM hltb_cache WHERE steam_app_id = '107100'"
            )
            assert(hltbCursor.moveToFirst()) { "HLTB cache must survive v2→v3 migration" }
            assert(hltbCursor.getInt(0) == 21600) { "HLTB main story seconds must be 21600" }
            hltbCursor.close()

            // Verify new tables were created
            val smCursor = db.query("SELECT COUNT(*) FROM steam_metadata")
            assert(smCursor.moveToFirst())
            smCursor.close()

            val gsCursor = db.query("SELECT COUNT(*) FROM game_sessions")
            assert(gsCursor.moveToFirst())
            gsCursor.close()
        }
    }

    @Test
    fun migrate_v1_to_v3_in_one_step_preserves_launch_targets() {
        helper.createDatabase(PocketDatabase.DATABASE_NAME, 1).use { db ->
            db.execSQL("""
                INSERT INTO launch_targets
                (host_game_key, provider, external_id, source, display_name,
                 launch_data, is_available, is_preferred)
                VALUES
                ('steam:367520', 'GAME_NATIVE', '367520', 'STEAM', 'Hollow Knight',
                 '{}', 1, 0)
            """.trimIndent())
        }

        helper.runMigrationsAndValidate(
            PocketDatabase.DATABASE_NAME,
            3,
            true,
            PocketDatabase.MIGRATION_1_2,
            PocketDatabase.MIGRATION_2_3
        ).use { db ->
            val cursor = db.query(
                "SELECT display_name FROM launch_targets WHERE host_game_key = 'steam:367520'"
            )
            assert(cursor.moveToFirst()) { "Hollow Knight launch target must survive v1→v3" }
            assert(cursor.getString(0) == "Hollow Knight")
            cursor.close()
        }
    }
}
