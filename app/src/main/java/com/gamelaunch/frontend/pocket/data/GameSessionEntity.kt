package com.gamelaunch.frontend.pocket.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── Entity ─────────────────────────────────────────────────────────────────

/**
 * A recorded CreteOS play session.
 *
 * Populated when a game is launched via CreteOS and then returned to.
 * Initially empty for all games — historical sessions cannot be reconstructed
 * from Steam or GameNative data. The beautiful session graph on the detail
 * screen will fill naturally over time as the user plays through CreteOS.
 *
 * gameKey = rom_path from eOr games table (e.g. "steam:107100")
 */
@Entity(
    tableName = "game_sessions",
    indices = [Index("game_key"), Index("started_at_ms")]
)
data class GameSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "game_key")
    val gameKey: String,                // mirrors Game.romPath

    @ColumnInfo(name = "started_at_ms")
    val startedAtMs: Long,

    @ColumnInfo(name = "ended_at_ms")
    val endedAtMs: Long? = null,        // null = session still in progress

    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int = 0,       // computed on close

    @ColumnInfo(name = "provider")
    val provider: String = "unknown"    // e.g. "GAME_NATIVE", "MOONLIGHT", "GFN"
)

// ── DAO ────────────────────────────────────────────────────────────────────

@Dao
interface GameSessionDao {

    /** Start a session — call when game is launched. Returns the session ID. */
    @Insert
    suspend fun startSession(session: GameSessionEntity): Long

    /** Close a session — call when user returns to CreteOS. */
    @Query("""
        UPDATE game_sessions
        SET ended_at_ms = :endedAtMs,
            duration_minutes = CAST((:endedAtMs - started_at_ms) / 60000 AS INTEGER)
        WHERE id = :sessionId
    """)
    suspend fun endSession(sessionId: Long, endedAtMs: Long)

    /** All completed sessions for a game, newest first. */
    @Query("""
        SELECT * FROM game_sessions
        WHERE game_key = :gameKey AND ended_at_ms IS NOT NULL
        ORDER BY started_at_ms DESC
        LIMIT :limit
    """)
    fun getSessionsForGame(gameKey: String, limit: Int = 10): Flow<List<GameSessionEntity>>

    /** Total minutes played for a game across all recorded sessions. */
    @Query("""
        SELECT COALESCE(SUM(duration_minutes), 0)
        FROM game_sessions
        WHERE game_key = :gameKey AND ended_at_ms IS NOT NULL
    """)
    suspend fun totalMinutesForGame(gameKey: String): Int

    /** Preserve play history when a legacy provider-namespaced key gains its real store identity. */
    @Query("UPDATE game_sessions SET game_key = :newKey WHERE game_key = :oldKey")
    suspend fun migrateGameKey(oldKey: String, newKey: String)

    /** Any session currently in progress (no end time). */
    @Query("""
        SELECT * FROM game_sessions
        WHERE ended_at_ms IS NULL
        ORDER BY started_at_ms DESC
        LIMIT 1
    """)
    suspend fun getActiveSession(): GameSessionEntity?
}
