package com.gamelaunch.frontend.pocket

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.pocket.data.repository.LaunchTargetRepository
import com.gamelaunch.frontend.pocket.display.GamingDisplayInfo
import com.gamelaunch.frontend.pocket.display.GamingDisplayManager
import com.gamelaunch.frontend.pocket.domain.DisplayPolicy
import com.gamelaunch.frontend.pocket.domain.LaunchContext
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.launch.UnifiedLaunchCoordinator
import com.gamelaunch.frontend.pocket.performance.AyaPerformanceModeManager
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class UnifiedLaunchCoordinatorTest {

    private val repository: LaunchTargetRepository = mock()
    private val displayManager: GamingDisplayManager = mock()
    private val gameNativeProvider: GameProvider = mock()
    private val performanceModeManager: AyaPerformanceModeManager = mock()

    private lateinit var coordinator: UnifiedLaunchCoordinator

    private val internalDisplay = GamingDisplayInfo.internal()
    private val xrealDisplay = GamingDisplayInfo(
        displayId = 1, width = 1920, height = 1200,
        refreshRate = 60f, isExternal = true, name = "XREAL One S"
    )

    private val testGame = Game(
        id = 1,
        title = "Hollow Knight",
        romPath = "steam:367520",
        romFilename = "Hollow Knight.steam",
        platformId = "steam"
    )

    private val availableTarget = LaunchTarget(
        id = 1L,
        hostGameKey = "steam:367520",
        provider = ProviderId.GAME_NATIVE,
        externalId = "367520",
        source = "STEAM",
        displayName = "Hollow Knight",
        isAvailable = true,
        isPreferred = true
    )

    @Before
    fun setup() {
        whenever(displayManager.activeDisplay).thenReturn(MutableStateFlow(internalDisplay))
        whenever(gameNativeProvider.id).thenReturn(ProviderId.GAME_NATIVE)
        coordinator = UnifiedLaunchCoordinator(
            launchTargetRepository = repository,
            gamingDisplayManager = displayManager,
            ayaPerformanceModeManager = performanceModeManager,
            providers = mapOf(ProviderId.GAME_NATIVE to gameNativeProvider)
        )
    }

    @Test
    fun `returns null when no targets registered — falls through to eOr`() = runTest {
        whenever(repository.getTargetsForGameOnce("steam:367520")).thenReturn(emptyList())

        val result = coordinator.tryLaunch(testGame)

        assertNull("Should return null so eOr handles the game", result)
    }

    @Test
    fun `launches preferred available target successfully`() = runTest {
        whenever(repository.getTargetsForGameOnce("steam:367520")).thenReturn(listOf(availableTarget))
        whenever(gameNativeProvider.isAvailable()).thenReturn(true)
        whenever(gameNativeProvider.launch(eq(availableTarget), any())).thenReturn(Result.success(Unit))

        val result = coordinator.tryLaunch(testGame)

        assertNotNull("Should return a result (not null)", result)
        assertTrue("Should succeed", result!!.isSuccess)
        verify(gameNativeProvider).launch(eq(availableTarget), any())
        verify(performanceModeManager).useGamingMode()
    }

    @Test
    fun `cloud launch stays in eco mode`() = runTest {
        val cloudProvider: GameProvider = mock()
        val cloudTarget = availableTarget.copy(provider = ProviderId.GEFORCE_NOW)
        whenever(cloudProvider.isAvailable()).thenReturn(true)
        whenever(cloudProvider.launch(eq(cloudTarget), any())).thenReturn(Result.success(Unit))
        coordinator = UnifiedLaunchCoordinator(
            launchTargetRepository = repository,
            gamingDisplayManager = displayManager,
            ayaPerformanceModeManager = performanceModeManager,
            providers = mapOf(ProviderId.GEFORCE_NOW to cloudProvider)
        )

        val result = coordinator.launchSpecific(cloudTarget)

        assertTrue(result.isSuccess)
        verify(performanceModeManager).useEcoMode()
        verify(performanceModeManager, never()).useGamingMode()
    }

    @Test
    fun `failed local launch restores eco mode`() = runTest {
        whenever(gameNativeProvider.isAvailable()).thenReturn(true)
        whenever(gameNativeProvider.launch(eq(availableTarget), any()))
            .thenReturn(Result.failure(IllegalStateException("launch failed")))

        val result = coordinator.launchSpecific(availableTarget)

        assertTrue(result.isFailure)
        inOrder(performanceModeManager) {
            verify(performanceModeManager).useGamingMode()
            verify(performanceModeManager).useEcoMode()
        }
    }

    @Test
    fun `returns null when all targets are unavailable`() = runTest {
        val unavailableTarget = availableTarget.copy(isAvailable = false)
        whenever(repository.getTargetsForGameOnce("steam:367520")).thenReturn(listOf(unavailableTarget))

        val result = coordinator.tryLaunch(testGame)

        assertNull("Should fall through to eOr when all targets unavailable", result)
    }

    @Test
    fun `returns failure and marks unavailable when provider not installed`() = runTest {
        whenever(repository.getTargetsForGameOnce("steam:367520")).thenReturn(listOf(availableTarget))
        whenever(gameNativeProvider.isAvailable()).thenReturn(false)

        val result = coordinator.tryLaunch(testGame)

        assertNotNull(result)
        assertTrue("Should fail when provider not installed", result!!.isFailure)
        verify(repository).markProviderUnavailable(ProviderId.GAME_NATIVE)
    }

    @Test
    fun `builds internal display context when no external display`() = runTest {
        whenever(repository.getTargetsForGameOnce("steam:367520")).thenReturn(listOf(availableTarget))
        whenever(gameNativeProvider.isAvailable()).thenReturn(true)
        val contextCaptor = argumentCaptor<LaunchContext>()
        whenever(gameNativeProvider.launch(eq(availableTarget), contextCaptor.capture()))
            .thenReturn(Result.success(Unit))

        coordinator.tryLaunch(testGame)

        val captured = contextCaptor.firstValue
        assertFalse(captured.externalDisplayConnected)
        assertEquals(1920, captured.displayWidth)
        assertEquals(1080, captured.displayHeight)
    }

    @Test
    fun `builds XREAL display context when external display connected`() = runTest {
        whenever(displayManager.activeDisplay).thenReturn(MutableStateFlow(xrealDisplay))
        coordinator = UnifiedLaunchCoordinator(
            launchTargetRepository = repository,
            gamingDisplayManager = displayManager,
            ayaPerformanceModeManager = performanceModeManager,
            providers = mapOf(ProviderId.GAME_NATIVE to gameNativeProvider)
        )

        whenever(repository.getTargetsForGameOnce("steam:367520")).thenReturn(listOf(availableTarget))
        whenever(gameNativeProvider.isAvailable()).thenReturn(true)
        val contextCaptor = argumentCaptor<LaunchContext>()
        whenever(gameNativeProvider.launch(eq(availableTarget), contextCaptor.capture()))
            .thenReturn(Result.success(Unit))

        coordinator.tryLaunch(testGame)

        val captured = contextCaptor.firstValue
        assertTrue(captured.externalDisplayConnected)
        assertEquals(1920, captured.displayWidth)
        assertEquals(1200, captured.displayHeight)
        assertEquals(DisplayPolicy.AUTO_MATCH_DISPLAY, captured.displayPolicy)
    }
}
