package com.gamelaunch.frontend.pocket

import com.gamelaunch.frontend.pocket.launch.LaunchPreferencePolicy
import com.gamelaunch.frontend.pocket.providers.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchPreferencePolicyTest {

    @Test
    fun `installed local game prefers GameNative`() {
        assertEquals(
            ProviderId.GAME_NATIVE,
            LaunchPreferencePolicy.chooseProvider(
                isLocallyInstalled = true,
                availableProviders = setOf(
                    ProviderId.GAME_NATIVE,
                    ProviderId.GEFORCE_NOW,
                    ProviderId.MOONLIGHT,
                ),
                hasVerifiedGfnLink = true,
            )
        )
    }

    @Test
    fun `non-local verified game prefers GeForce NOW and keeps Moonlight as fallback`() {
        assertEquals(
            ProviderId.GEFORCE_NOW,
            LaunchPreferencePolicy.chooseProvider(
                isLocallyInstalled = false,
                availableProviders = setOf(ProviderId.GEFORCE_NOW, ProviderId.MOONLIGHT),
                hasVerifiedGfnLink = true,
            )
        )
    }

    @Test
    fun `non-local unverified game prefers Moonlight over GFN library`() {
        assertEquals(
            ProviderId.MOONLIGHT,
            LaunchPreferencePolicy.chooseProvider(
                isLocallyInstalled = false,
                availableProviders = setOf(ProviderId.GEFORCE_NOW, ProviderId.MOONLIGHT),
                hasVerifiedGfnLink = false,
            )
        )
    }

    @Test
    fun `emulated game prefers emulator`() {
        assertEquals(
            ProviderId.EMULATOR,
            LaunchPreferencePolicy.chooseProvider(
                isLocallyInstalled = false,
                availableProviders = setOf(ProviderId.EMULATOR),
                hasVerifiedGfnLink = false,
            )
        )
    }
}
