package com.gamelaunch.frontend.pocket.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {

    @Test
    fun `search ignores a leading article`() {
        assertTrue(matchesLibrarySearch("The Witcher 3: Wild Hunt", "Witcher"))
        assertTrue(matchesLibrarySearch("The Witcher 2: Assassins of Kings", "the witcher 2"))
    }

    @Test
    fun `search is incremental accent insensitive and token based`() {
        assertTrue(matchesLibrarySearch("Pokémon Mystery Dungeon", "poke myst"))
        assertTrue(matchesLibrarySearch("Cyberpunk 2077", "2077 cyber"))
        assertFalse(matchesLibrarySearch("The Witcher 3: Wild Hunt", "Witcher 2"))
    }

    @Test
    fun `empty search includes every title`() {
        assertTrue(matchesLibrarySearch("Hades", "   "))
    }
}
