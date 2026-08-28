package com.gamelaunch.frontend

import com.gamelaunch.frontend.util.RomTitleNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomTitleNormalizerTest {

    @Test
    fun `normalizes the current ROM library filenames`() {
        val expected = mapOf(
            "The Legend of Zelda Tears of the Kingdom [0100F2C0115B6000][v0].nsp" to
                "The Legend of Zelda: Tears of the Kingdom",
            "windwaker_hd_us.wua" to "The Legend of Zelda: The Wind Waker HD",
            "Luigi's Mansion (USA).nkit.iso" to "Luigi's Mansion",
            "Devil May Cry 3 - Dante's Awakening (USA) (Special Edition).iso" to
                "Devil May Cry 3: Dante's Awakening – Special Edition",
            "Midnight Club - L.A. Remix (USA).iso" to "Midnight Club: L.A. Remix",
            "Legend of Zelda, The - A Link Between Worlds (Europe) (En,Fr,De,Es,It).3ds" to
                "The Legend of Zelda: A Link Between Worlds",
            "New Super Mario Bros. (USA).nds" to "New Super Mario Bros.",
            "4273 - Pokemon Mystery Dungeon - Explorers of Sky (US)(XenoPhobia).nds" to
                "Pokémon Mystery Dungeon: Explorers of Sky",
            "Metroid - Zero Mission (USA).gba" to "Metroid: Zero Mission",
            "Pokemon - FireRed Version (USA, Europe) (Rev 1).gba" to
                "Pokémon FireRed Version"
        )

        expected.forEach { (filename, title) ->
            assertEquals(filename, title, RomTitleNormalizer.fromFilename(filename))
        }
    }

    @Test
    fun `repair is limited to the old scanner generated title`() {
        val filename = "4273 - Pokemon Mystery Dungeon - Explorers of Sky (US)(XenoPhobia).nds"

        assertTrue(
            RomTitleNormalizer.shouldRepair(
                "4273 - Pokemon Mystery Dungeon - Explorers of Sky",
                filename
            )
        )
        assertFalse(RomTitleNormalizer.shouldRepair("My Mystery Dungeon", filename))
        assertFalse(
            RomTitleNormalizer.shouldRepair(
                "Pokémon Mystery Dungeon: Explorers of Sky",
                filename
            )
        )
    }
}
