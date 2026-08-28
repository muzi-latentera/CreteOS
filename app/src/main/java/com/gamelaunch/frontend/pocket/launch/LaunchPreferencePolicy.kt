package com.gamelaunch.frontend.pocket.launch

import com.gamelaunch.frontend.pocket.providers.ProviderId

/** Automatic one-tap Play policy used only when the user has not chosen a target. */
object LaunchPreferencePolicy {
    fun chooseProvider(
        isLocallyInstalled: Boolean,
        availableProviders: Set<ProviderId>,
        hasVerifiedGfnLink: Boolean,
    ): ProviderId? = when {
        isLocallyInstalled && ProviderId.GAME_NATIVE in availableProviders ->
            ProviderId.GAME_NATIVE
        hasVerifiedGfnLink && ProviderId.GEFORCE_NOW in availableProviders ->
            ProviderId.GEFORCE_NOW
        ProviderId.MOONLIGHT in availableProviders ->
            ProviderId.MOONLIGHT
        ProviderId.GEFORCE_NOW in availableProviders ->
            ProviderId.GEFORCE_NOW
        ProviderId.GAME_NATIVE in availableProviders ->
            ProviderId.GAME_NATIVE
        ProviderId.EMULATOR in availableProviders ->
            ProviderId.EMULATOR
        else -> null
    }
}
