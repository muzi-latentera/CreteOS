package com.gamelaunch.frontend

import com.gamelaunch.frontend.platform.display.DualScreenDevices
import com.gamelaunch.frontend.platform.display.DualScreenDevices.Layout
import org.junit.Assert.assertEquals
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
