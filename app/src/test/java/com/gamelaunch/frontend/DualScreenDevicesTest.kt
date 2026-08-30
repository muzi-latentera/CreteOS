package com.gamelaunch.frontend

import com.gamelaunch.frontend.platform.display.DualScreenDevices
import com.gamelaunch.frontend.platform.display.DualScreenDevices.Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualScreenDevicesTest {

    @Test fun `AYN Thor puts the menu on the secondary display`() {
        assertEquals(
            Layout.MENU_ON_SECONDARY,
            DualScreenDevices.layoutFor(manufacturer = "AYN", model = "Thor", device = "thor")
        )
    }

    @Test fun `Anbernic RG DS puts artwork on the secondary display`() {
        assertEquals(
            Layout.ARTWORK_ON_SECONDARY,
            DualScreenDevices.layoutFor(manufacturer = "Anbernic", model = "RG DS", device = "rgds")
        )
    }

    @Test fun `known dual-screen handhelds enable presentation support`() {
        assertTrue(DualScreenDevices.isDualScreenHandheld("AYN", "Thor", "thor"))
        assertTrue(DualScreenDevices.isDualScreenHandheld("Anbernic", "RG DS", "rgds"))
    }

    @Test fun `Pocket FIT external display is not treated as a built-in second panel`() {
        assertFalse(
            DualScreenDevices.isDualScreenHandheld(
                manufacturer = "AYANEO",
                model = "Pocket FIT Elite",
                device = "PocketFITElite"
            )
        )
    }

    @Test fun `unknown device defaults to artwork-on-secondary (never relaunches)`() {
        assertEquals(
            Layout.ARTWORK_ON_SECONDARY,
            DualScreenDevices.layoutFor(manufacturer = "Google", model = "Pixel 8", device = "shiba")
        )
    }

    @Test fun `flipped inverts the layout`() {
        assertEquals(Layout.MENU_ON_SECONDARY, Layout.ARTWORK_ON_SECONDARY.flipped())
        assertEquals(Layout.ARTWORK_ON_SECONDARY, Layout.MENU_ON_SECONDARY.flipped())
    }
}
