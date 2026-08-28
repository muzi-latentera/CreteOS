package com.gamelaunch.frontend.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorReleasePolicyTest {
    @Test
    fun `NetherSX2 release labelled dev is not a stable update`() {
        assertFalse(
            EmulatorReleasePolicy.isStableRelease(
                tag = "2.2n",
                name = "NetherSX2 v2.2n Net Dev Build",
            )
        )
    }

    @Test
    fun `normal numbered release is stable`() {
        assertTrue(EmulatorReleasePolicy.isStableRelease("2126.0", "Azahar 2126.0"))
    }

    @Test
    fun `Vita3K CI build tag is not compared to APK semantic version`() {
        assertFalse(EmulatorReleasePolicy.canComparePackage("org.vita3k.emulator"))
    }
}
