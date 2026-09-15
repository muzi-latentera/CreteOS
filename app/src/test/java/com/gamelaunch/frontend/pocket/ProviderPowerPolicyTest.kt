package com.gamelaunch.frontend.pocket

import com.gamelaunch.frontend.pocket.providers.ProviderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPowerPolicyTest {
    @Test
    fun `only cloud streaming providers avoid local gaming mode`() {
        assertFalse(ProviderId.MOONLIGHT.runsLocally)
        assertFalse(ProviderId.GEFORCE_NOW.runsLocally)
        assertFalse(ProviderId.ASOBI.runsLocally)

        ProviderId.entries
            .filterNot {
                it == ProviderId.MOONLIGHT || it == ProviderId.GEFORCE_NOW || it == ProviderId.ASOBI
            }
            .forEach { provider -> assertTrue("$provider should use Gaming mode", provider.runsLocally) }
    }
}
