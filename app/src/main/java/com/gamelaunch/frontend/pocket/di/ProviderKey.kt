package com.gamelaunch.frontend.pocket.di

import com.gamelaunch.frontend.pocket.providers.ProviderId
import dagger.MapKey

@MapKey
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ProviderKey(val value: ProviderId)
