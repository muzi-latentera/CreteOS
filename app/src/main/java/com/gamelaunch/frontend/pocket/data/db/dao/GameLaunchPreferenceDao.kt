package com.gamelaunch.frontend.pocket.data.db.dao

import androidx.room.*
import com.gamelaunch.frontend.pocket.data.db.entity.GameLaunchPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameLaunchPreferenceDao {

    @Query("SELECT * FROM game_launch_preferences WHERE hostGameKey = :key")
    suspend fun getPreference(key: String): GameLaunchPreferenceEntity?

    @Query("SELECT * FROM game_launch_preferences WHERE hostGameKey = :key")
    fun observePreference(key: String): Flow<GameLaunchPreferenceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: GameLaunchPreferenceEntity)

    @Query("DELETE FROM game_launch_preferences WHERE hostGameKey = :key")
    suspend fun delete(key: String)
}
