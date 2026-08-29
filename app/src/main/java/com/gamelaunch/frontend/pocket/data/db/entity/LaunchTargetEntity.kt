package com.gamelaunch.frontend.pocket.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "launch_targets",
    indices = [
        Index(value = ["hostGameKey", "provider", "source", "externalId"], unique = true),
        Index(value = ["hostGameKey"])
    ]
)
data class LaunchTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hostGameKey: String,
    /** ProviderId.name() — stored as String to survive enum additions without migration */
    val provider: String,
    val externalId: String,
    val source: String = "",
    val displayName: String,
    /** Provider-specific launch data as JSON */
    val launchData: String = "{}",
    val isAvailable: Boolean = true,
    val isPreferred: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
