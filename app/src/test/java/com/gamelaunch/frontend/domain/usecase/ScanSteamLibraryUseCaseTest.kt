package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Files

class ScanSteamLibraryUseCaseTest {

    @Test
    fun `Steam wins duplicate title and unique Epic game keeps Epic platform`() = runTest {
        val exportDir = Files.createTempDirectory("crete-pc-export").toFile()
        try {
            exportDir.resolve("Hades.epic").writeText("41")
            exportDir.resolve("Hades.steam").writeText("1145360")
            exportDir.resolve("Alan Wake 2.epic").writeText("42")

            val gameRepository = mock<GameRepository>()
            val settingsRepository = mock<SettingsRepository>()
            whenever(settingsRepository.steamLibraryPath).thenReturn(flowOf(exportDir.absolutePath))
            whenever(gameRepository.getAllGames()).thenReturn(flowOf(emptyList()))
            whenever(gameRepository.insertGame(any())).thenReturn(1L)

            ScanSteamLibraryUseCase(gameRepository, settingsRepository).invoke().collect { }

            val games = argumentCaptor<Game>()
            verify(gameRepository, times(2)).insertGame(games.capture())
            val inserted = games.allValues

            assertTrue(inserted.any {
                it.title == "Hades" && it.romPath == "steam:1145360" && it.platformId == "steam"
            })
            assertTrue(inserted.any {
                it.title == "Alan Wake 2" && it.romPath == "steam:EPIC:42" && it.platformId == "epic"
            })
            assertFalse(inserted.any { it.romPath == "steam:EPIC:41" })
        } finally {
            exportDir.deleteRecursively()
        }
    }

    @Test
    fun `existing Steam title suppresses matching Epic export`() = runTest {
        val exportDir = Files.createTempDirectory("crete-pc-existing").toFile()
        try {
            exportDir.resolve("Control Ultimate Edition.epic").writeText("73")

            val gameRepository = mock<GameRepository>()
            val settingsRepository = mock<SettingsRepository>()
            whenever(settingsRepository.steamLibraryPath).thenReturn(flowOf(exportDir.absolutePath))
            whenever(gameRepository.getAllGames()).thenReturn(flowOf(listOf(
                Game(
                    id = 9,
                    title = "Control: Ultimate Edition",
                    romPath = "steam:870780",
                    romFilename = "Control Ultimate Edition",
                    platformId = "steam"
                )
            )))

            val finalProgress = ScanSteamLibraryUseCase(gameRepository, settingsRepository)
                .invoke().let { progress ->
                    var last = ScanProgress(0, 0)
                    progress.collect { last = it }
                    last
                }

            verify(gameRepository, times(0)).insertGame(any())
            assertEquals(0, finalProgress.added)
        } finally {
            exportDir.deleteRecursively()
        }
    }
}
