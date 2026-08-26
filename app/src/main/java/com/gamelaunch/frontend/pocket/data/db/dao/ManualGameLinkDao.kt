package com.gamelaunch.frontend.pocket.data.db.dao

import androidx.room.*
import com.gamelaunch.frontend.pocket.data.db.entity.ManualGameLinkEntity

@Dao
interface ManualGameLinkDao {

    @Query("SELECT * FROM manual_game_links WHERE providerName = :provider AND providerExternalId = :externalId LIMIT 1")
    suspend fun findLink(provider: String, externalId: String): ManualGameLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: ManualGameLinkEntity): Long

    @Query("DELETE FROM manual_game_links WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM manual_game_links")
    suspend fun getAll(): List<ManualGameLinkEntity>
}
