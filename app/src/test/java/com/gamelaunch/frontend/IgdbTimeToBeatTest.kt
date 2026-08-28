package com.gamelaunch.frontend

import com.gamelaunch.frontend.pocket.data.IgdbTtbSeconds
import com.gamelaunch.frontend.pocket.data.consistentIgdbTtb
import org.junit.Assert.assertEquals
import org.junit.Test

class IgdbTimeToBeatTest {

    @Test
    fun `keeps a consistent set of IGDB averages`() {
        assertEquals(
            IgdbTtbSeconds(main = 55, mainExtra = 116, completionist = 176),
            consistentIgdbTtb(hastily = 55, normally = 116, completely = 176)
        )
    }

    @Test
    fun `hides contradictory averages instead of mislabelling them`() {
        assertEquals(
            IgdbTtbSeconds(main = 0, mainExtra = 5, completionist = 12),
            consistentIgdbTtb(hastily = 20, normally = 5, completely = 12)
        )
        assertEquals(
            IgdbTtbSeconds(main = 4, mainExtra = 0, completionist = 8),
            consistentIgdbTtb(hastily = 4, normally = 10, completely = 8)
        )
    }
}
