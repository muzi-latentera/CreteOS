package com.gamelaunch.frontend.pocket.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── Entity ─────────────────────────────────────────────────────────────────

/**
 * Steam metadata sidecar — owned by CreteOS, keyed to Steam AppID.
 *
 * Deliberately separate from eOr's Game/GameMedia tables so we never touch
 * upstream schema. Populated by SteamMetadataSync using the user's Steam API key.
 *
 * play_count on Game = CreteOS launch count (sessions)
 * this.playtimeMinutes = real Steam total playtime
 */
@Entity(
    tableName = "steam_metadata",
    indices = [Index("steam_app_id", unique = true)]
)
data class SteamMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "steam_app_id")
    val steamAppId: String,

    // Playtime from Steam API (IPlayerService/GetOwnedGames)
    @ColumnInfo(name = "playtime_minutes")
    val playtimeMinutes: Int = 0,

    @ColumnInfo(name = "last_played_ms")
    val lastPlayedMs: Long? = null,

    // Achievements from ISteamUserStats/GetPlayerAchievements
    @ColumnInfo(name = "achievements_unlocked")
    val achievementsUnlocked: Int = 0,

    @ColumnInfo(name = "achievements_total")
    val achievementsTotal: Int = 0,

    // Game metadata — populated later from Steam store API or IGDB
    @ColumnInfo(name = "developer")
    val developer: String? = null,

    @ColumnInfo(name = "publisher")
    val publisher: String? = null,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "release_date")
    val releaseDate: String? = null,   // e.g. "Aug 16, 2011"

    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long = System.currentTimeMillis(),

    /** Null = achievements never fetched. Non-null = last successful fetch timestamp. */
    @ColumnInfo(name = "achievements_synced_at_ms")
    val achievementsSyncedAtMs: Long? = null
)

// ── DAO ────────────────────────────────────────────────────────────────────

@Dao
interface SteamMetadataDao {

    @Query("SELECT * FROM steam_metadata WHERE steam_app_id = :appId")
    suspend fun getByAppId(appId: String): SteamMetadataEntity?

    @Query("SELECT * FROM steam_metadata WHERE steam_app_id = :appId")
    fun observeByAppId(appId: String): Flow<SteamMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SteamMetadataEntity)

    @Query("SELECT COUNT(*) FROM steam_metadata")
    suspend fun count(): Int

    /** All synced entries, for bulk operations */
    @Query("SELECT * FROM steam_metadata ORDER BY playtime_minutes DESC")
    suspend fun getAll(): List<SteamMetadataEntity>
}

// ── Helpers ────────────────────────────────────────────────────────────────

/** Format Steam playtime minutes as a human-readable string. */
fun SteamMetadataEntity?.formatPlaytime(): String {
    val mins = this?.playtimeMinutes ?: return "—"
    if (mins <= 0) return "—"
    val h = mins / 60
    val m = mins % 60
    return when {
        h == 0   -> "${m}m"
        m == 0   -> "${h}h"
        else     -> "${h}h ${m}m"
    }
}

fun SteamMetadataEntity?.formatAchievements(): String {
    val e = this ?: return "—"
    if (e.achievementsTotal <= 0) return "—"
    return "${e.achievementsUnlocked} / ${e.achievementsTotal}"
}

fun SteamMetadataEntity?.achievementPercent(): Float {
    val e = this ?: return 0f
    if (e.achievementsTotal <= 0) return 0f
    return e.achievementsUnlocked.toFloat() / e.achievementsTotal.toFloat()
}
