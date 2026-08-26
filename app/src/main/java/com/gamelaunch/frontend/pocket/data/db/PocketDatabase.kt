package com.gamelaunch.frontend.pocket.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
 */
@Database(
    entities = [
        LaunchTargetEntity::class,
        GameLaunchPreferenceEntity::class,
        ManualGameLinkEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class PocketDatabase : RoomDatabase() {
    abstract fun launchTargetDao(): LaunchTargetDao
    abstract fun gameLaunchPreferenceDao(): GameLaunchPreferenceDao
    abstract fun manualGameLinkDao(): ManualGameLinkDao

    companion object {
        const val DATABASE_NAME = "creteos_pocket.db"

        fun create(context: Context): PocketDatabase =
            Room.databaseBuilder(context, PocketDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
