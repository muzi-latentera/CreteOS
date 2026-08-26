package com.gamelaunch.frontend.pocket.di

import android.content.Context
import com.gamelaunch.frontend.pocket.data.db.PocketDatabase
import com.gamelaunch.frontend.pocket.data.db.dao.GameLaunchPreferenceDao
import com.gamelaunch.frontend.pocket.data.db.dao.LaunchTargetDao
import com.gamelaunch.frontend.pocket.data.db.dao.ManualGameLinkDao
import com.gamelaunch.frontend.pocket.display.GamingDisplayManager
import com.gamelaunch.frontend.pocket.sync.PcGameArtworkResolver
import com.gamelaunch.frontend.pocket.sync.ProviderSyncCoordinator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PocketModule {

    @Provides
    @Singleton
    fun providePocketDatabase(@ApplicationContext context: Context): PocketDatabase =
        PocketDatabase.create(context)

    @Provides
    fun provideLaunchTargetDao(db: PocketDatabase): LaunchTargetDao =
        db.launchTargetDao()

    @Provides
    fun provideGameLaunchPreferenceDao(db: PocketDatabase): GameLaunchPreferenceDao =
        db.gameLaunchPreferenceDao()

    @Provides
    fun provideManualGameLinkDao(db: PocketDatabase): ManualGameLinkDao =
        db.manualGameLinkDao()

    @Provides
    @Singleton
    fun provideGamingDisplayManager(@ApplicationContext context: Context): GamingDisplayManager =
        GamingDisplayManager(context)
}
