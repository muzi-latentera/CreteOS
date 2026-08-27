package com.gamelaunch.frontend.pocket.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Manual user-confirmed link between a provider game and an eOr host game key. */
@Entity(tableName = "manual_game_links")
data class ManualGameLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hostGameKey: String,
    val providerName: String,
    val providerExternalId: String,
    val createdAt: Long = System.currentTimeMillis()
)
