package com.gamelaunch.frontend.pocket.data.db.dao

import androidx.room.*
import com.gamelaunch.frontend.pocket.data.db.entity.LaunchTargetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LaunchTargetDao {

    @Query("SELECT * FROM launch_targets WHERE hostGameKey = :key ORDER BY isPreferred DESC, createdAt ASC")
    fun getTargetsForGame(key: String): Flow<List<LaunchTargetEntity>>

    @Query("SELECT * FROM launch_targets WHERE hostGameKey = :key ORDER BY isPreferred DESC, createdAt ASC")
    suspend fun getTargetsForGameOnce(key: String): List<LaunchTargetEntity>

    @Query("SELECT * FROM launch_targets WHERE hostGameKey = :key AND isPreferred = 1 LIMIT 1")
    suspend fun getPreferredTarget(key: String): LaunchTargetEntity?

    @Query("SELECT * FROM launch_targets WHERE provider = :provider")
    suspend fun getTargetsForProvider(provider: String): List<LaunchTargetEntity>

    @Query("SELECT DISTINCT hostGameKey FROM launch_targets WHERE provider = :provider AND isAvailable = 1")
    fun observeAvailableHostGameKeys(provider: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(target: LaunchTargetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(targets: List<LaunchTargetEntity>)

    @Query("UPDATE launch_targets SET isPreferred = 0 WHERE hostGameKey = :key")
    suspend fun clearPreferred(key: String)

    @Query("UPDATE launch_targets SET isPreferred = 1, updatedAt = :now WHERE id = :targetId")
    suspend fun setPreferred(targetId: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE launch_targets SET isAvailable = :available, updatedAt = :now WHERE provider = :provider")
    suspend fun setProviderAvailability(provider: String, available: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM launch_targets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """DELETE FROM launch_targets
           WHERE hostGameKey = :key
             AND (provider != :provider OR externalId NOT IN (:allowedExternalIds))"""
    )
    suspend fun deleteTargetsExcept(
        key: String,
        provider: String,
        allowedExternalIds: List<String>
    ): Int

    @Query("SELECT COUNT(*) FROM launch_targets WHERE provider = :provider AND isAvailable = 1")
    suspend fun countAvailableForProvider(provider: String): Int
}
