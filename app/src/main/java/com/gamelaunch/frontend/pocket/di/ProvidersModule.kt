package com.gamelaunch.frontend.pocket.di

import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderId
import com.gamelaunch.frontend.pocket.providers.impl.GameHubLiteProvider
import com.gamelaunch.frontend.pocket.providers.impl.GameNativeProvider
import com.gamelaunch.frontend.pocket.providers.impl.GeForceNowProvider
import com.gamelaunch.frontend.pocket.providers.impl.MoonlightProvider
import com.gamelaunch.frontend.pocket.providers.impl.WinNativeProvider
import com.gamelaunch.frontend.pocket.providers.impl.WinlatorProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
abstract class ProvidersModule {

    @Binds @IntoMap @ProviderKey(ProviderId.GAME_NATIVE)
    abstract fun bindGameNative(impl: GameNativeProvider): GameProvider

    @Binds @IntoMap @ProviderKey(ProviderId.GAME_HUB_LITE)
    abstract fun bindGameHubLite(impl: GameHubLiteProvider): GameProvider

    @Binds @IntoMap @ProviderKey(ProviderId.WIN_NATIVE)
    abstract fun bindWinNative(impl: WinNativeProvider): GameProvider

    @Binds @IntoMap @ProviderKey(ProviderId.WINLATOR)
    abstract fun bindWinlator(impl: WinlatorProvider): GameProvider

    @Binds @IntoMap @ProviderKey(ProviderId.MOONLIGHT)
    abstract fun bindMoonlight(impl: MoonlightProvider): GameProvider

    @Binds @IntoMap @ProviderKey(ProviderId.GEFORCE_NOW)
    abstract fun bindGeForceNow(impl: GeForceNowProvider): GameProvider
}
