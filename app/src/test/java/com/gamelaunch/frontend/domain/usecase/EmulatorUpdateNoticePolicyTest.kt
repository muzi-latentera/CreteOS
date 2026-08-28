package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.model.EmulatorUpdate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorUpdateNoticePolicyTest {
    private val updates = listOf(
        EmulatorUpdate("emu.b", "B", "1.0", "1.1", "https://example.com/b"),
        EmulatorUpdate("emu.a", "A", "2.0", "2.1", "https://example.com/a"),
    )

    @Test
    fun `dismissed update batch stays hidden after relaunch`() {
        val dismissed = EmulatorUpdateNoticePolicy.signature(updates)

        assertFalse(
            EmulatorUpdateNoticePolicy.shouldShow(
                notificationsEnabled = true,
                updates = updates.reversed(),
                dismissedSignature = dismissed,
            )
        )
    }

    @Test
    fun `new emulator version creates a new visible batch`() {
        val dismissed = EmulatorUpdateNoticePolicy.signature(updates)
        val newer = updates.map {
            if (it.packageName == "emu.a") it.copy(latestVersion = "2.2") else it
        }

        assertNotEquals(dismissed, EmulatorUpdateNoticePolicy.signature(newer))
        assertTrue(
            EmulatorUpdateNoticePolicy.shouldShow(
                notificationsEnabled = true,
                updates = newer,
                dismissedSignature = dismissed,
            )
        )
    }

    @Test
    fun `master notification switch hides banner`() {
        assertFalse(
            EmulatorUpdateNoticePolicy.shouldShow(
                notificationsEnabled = false,
                updates = updates,
                dismissedSignature = null,
            )
        )
    }
}
