package com.gamelaunch.frontend.pocket.data

import com.gamelaunch.frontend.domain.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TalosGamingClientTest {
    private val xboxStats = TalosGameStats(
        platform = "xbox",
        externalGameId = "123",
        name = "A Plague Tale: Requiem",
        iconUrl = null,
        totalPlaytimeMinutes = 0,
        lastPlayedMs = null,
    )

    @Test
    fun `normalizes punctuation and leading article for account matching`() {
        val local = TalosGamingClient.titleCandidates("The Witcher 3: Wild Hunt")
        val remote = TalosGamingClient.titleCandidates("Witcher 3 - Wild Hunt")

        assertTrue(local.any(remote::contains))
    }

    @Test
    fun `matches common edition suffix without collapsing sequel number`() {
        val local = TalosGamingClient.titleCandidates("Dishonored 2: Definitive Edition")
        val remote = TalosGamingClient.titleCandidates("Dishonored 2")

        assertTrue(local.any(remote::contains))
    }

    @Test
    fun `matches Game Pass games to Xbox history`() {
        val game = Game(
            title = "A Plague Tale Requiem",
            romPath = "steam:plague_tale_requiem_gp",
            romFilename = "",
            platformId = "gamepass",
        )

        assertEquals(xboxStats, TalosGamingClient.matchStats(game, "plague_tale_requiem_gp", listOf(xboxStats)))
    }

    @Test
    fun `does not attach Xbox history to Epic ownership`() {
        val game = Game(
            title = "A Plague Tale Requiem",
            romPath = "steam:plague_tale_requiem_epic",
            romFilename = "",
            platformId = "epic",
        )

        assertNull(TalosGamingClient.matchStats(game, "plague_tale_requiem_epic", listOf(xboxStats)))
    }
}
