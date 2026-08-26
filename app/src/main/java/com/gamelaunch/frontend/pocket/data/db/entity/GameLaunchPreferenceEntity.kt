package com.gamelaunch.frontend.pocket.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_launch_preferences")
data class GameLaunchPreferenceEntity(
    @PrimaryKey val hostGameKey: String,
    val preferredTargetId: Long? = null,
    /** DisplayPolicy.name() */
    val displayPolicy: String = "BACKEND_DEFAULT"
)
