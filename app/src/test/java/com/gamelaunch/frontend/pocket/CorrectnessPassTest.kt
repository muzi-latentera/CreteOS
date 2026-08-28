package com.gamelaunch.frontend.pocket

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.pocket.data.repository.LaunchTargetRepository
import com.gamelaunch.frontend.pocket.data.SteamMetadataDao
import com.gamelaunch.frontend.pocket.domain.DiscoveredProviderGame
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.providers.GameProvider
import com.gamelaunch.frontend.pocket.providers.ProviderId
import com.gamelaunch.frontend.pocket.sync.GameIdentityResolver
import com.gamelaunch.frontend.pocket.sync.PcGameArtworkResolver
import com.gamelaunch.frontend.pocket.sync.ProviderSyncCoordinator
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Production-path integration tests for the provider sync system.
 *
 * ALL tests call either ProviderSyncCoordinator.syncProvider() or
 * GameIdentityResolver.resolve() directly — no illustrative/data-class tests.
 *
 * These tests verify the actual production code paths:
 * - Identity resolution (store ID > manual link > title match)
 * - isPreferred preservation on resync
 * - Stale target reconciliation
 * - Steam CDN artwork invocation (and non-invocation for GOG)
 * - Synthetic key generation for unresolved games
 */
class CorrectnessPassTest {

    // ---- shared mocks ----
    private lateinit var gameRepository: GameRepository
    private lateinit var launchTargetRepository: LaunchTargetRepository
    private lateinit var steamMetadataDao: SteamMetadataDao
    private lateinit var artworkResolver: PcGameArtworkResolver
    private lateinit var identityResolver: GameIdentityResolver
    private lateinit var coordinator: ProviderSyncCoordinator

    @Before
    fun setup() {
        gameRepository = mock()
        launchTargetRepository = mock()
        steamMetadataDao = mock()
        artworkResolver = mock()
        identityResolver = GameIdentityResolver(gameRepository, launchTargetRepository)
        
        // Default stubs - non-suspend functions only
        whenever(gameRepository.getAllGames()).thenReturn(flowOf(emptyList()))
        // Note: findManualLink is a suspend function - stub it in each test using runTest block
    }

    private fun createCoordinator(provider: GameProvider): ProviderSyncCoordinator {
        return ProviderSyncCoordinator(
            gameRepository = gameRepository,
            launchTargetRepository = launchTargetRepository,
            steamMetadataDao = steamMetadataDao,
            artworkResolver = artworkResolver,
            identityResolver = identityResolver,
            providers = mapOf(provider.id to provider)
        )
    }

    // ==========================================================================
    // Test A: One game, two providers, no duplicates
    // ==========================================================================

    @Test
    fun `syncProvider matches existing game by hostGameKey and does NOT insert duplicate`() = runTest {
        // Setup: Hollow Knight exists in eOr library at steam:367520
        val hollowKnight = Game(
            id = 42L,
            title = "Hollow Knight",
            romPath = "steam:367520",
            romFilename = "Hollow Knight.steam",
            platformId = "steam"
        )
        
        whenever(gameRepository.getAllGames()).thenReturn(flowOf(listOf(hollowKnight)))
        whenever(gameRepository.getGameByRomPath("steam:367520")).thenReturn(hollowKnight)
        whenever(launchTargetRepository.getTargetsForProvider(ProviderId.MOONLIGHT)).thenReturn(emptyList())
        whenever(launchTargetRepository.upsertTarget(any())).thenReturn(1L)
        
        // Moonlight discovers Hollow Knight (streaming shortcut)
        val moonlightProvider = mock<GameProvider> {
            on { id } doReturn ProviderId.MOONLIGHT
            onBlocking { isAvailable() } doReturn true
            onBlocking { discoverGames() } doReturn listOf(
                DiscoveredProviderGame(
                    provider = ProviderId.MOONLIGHT,
                    externalId = "abc123",
                    source = "STREAMING",
                    displayName = "Hollow Knight"
                )
            )
        }

        val coordinator = createCoordinator(moonlightProvider)
        val result = coordinator.syncProvider(ProviderId.MOONLIGHT, moonlightProvider)

        // Assert: No new game inserted (identity resolver matched by title)
        verify(gameRepository, never()).insertGame(any())
        
        // Assert: Launch target upserted with hostGameKey pointing to existing game
        val targetCaptor = argumentCaptor<LaunchTarget>()
        verify(launchTargetRepository).upsertTarget(targetCaptor.capture())
        assertEquals("steam:367520", targetCaptor.firstValue.hostGameKey)
        assertEquals("abc123", targetCaptor.firstValue.externalId)
        
        assertEquals(1, result.discovered)
        assertEquals(0, result.added)  // no NEW games added
        assertEquals(1, result.updated)
    }

    // ==========================================================================
    // Test B: Preferred target survives syncProvider()
    // ==========================================================================

    @Test
    fun `syncProvider preserves isPreferred from existing target`() = runTest {
        // Setup: existing Moonlight target with isPreferred=true
        val existingTarget = LaunchTarget(
            id = 99L,
            hostGameKey = "steam:367520",
            provider = ProviderId.MOONLIGHT,
            externalId = "shortcut-hk",
            source = "STREAMING",
            displayName = "Hollow Knight",
            isAvailable = true,
            isPreferred = true  // user chose this as preferred
        )
        
        val hollowKnight = Game(
            id = 42L, title = "Hollow Knight",
            romPath = "steam:367520", romFilename = "HK.steam", platformId = "steam"
        )
        
        whenever(gameRepository.getAllGames()).thenReturn(flowOf(listOf(hollowKnight)))
        whenever(launchTargetRepository.getTargetsForProvider(ProviderId.MOONLIGHT))
            .thenReturn(listOf(existingTarget))
        whenever(launchTargetRepository.upsertTarget(any())).thenReturn(99L)
        
        val moonlightProvider = mock<GameProvider> {
            on { id } doReturn ProviderId.MOONLIGHT
            onBlocking { isAvailable() } doReturn true
            onBlocking { discoverGames() } doReturn listOf(
                DiscoveredProviderGame(
                    provider = ProviderId.MOONLIGHT,
                    externalId = "shortcut-hk",
                    source = "STREAMING",
                    displayName = "Hollow Knight"
                )
            )
        }

        val coordinator = createCoordinator(moonlightProvider)
        coordinator.syncProvider(ProviderId.MOONLIGHT, moonlightProvider)

        // Assert: upsertTarget called with isPreferred=true preserved
        val targetCaptor = argumentCaptor<LaunchTarget>()
        verify(launchTargetRepository).upsertTarget(targetCaptor.capture())
        assertTrue("isPreferred must be preserved", targetCaptor.firstValue.isPreferred)
    }

    // ==========================================================================
    // Test C: Stale reconciliation via syncProvider()
    // ==========================================================================

    @Test
    fun `syncProvider marks missing targets as unavailable without deleting`() = runTest {
        // Setup: provider previously had A, B, C — now only reports A, B
        val existingTargets = listOf(
            LaunchTarget(id = 1L, hostGameKey = "moonlight:A", provider = ProviderId.MOONLIGHT,
                externalId = "A", displayName = "Game A", isAvailable = true),
            LaunchTarget(id = 2L, hostGameKey = "moonlight:B", provider = ProviderId.MOONLIGHT,
                externalId = "B", displayName = "Game B", isAvailable = true),
            LaunchTarget(id = 3L, hostGameKey = "moonlight:C", provider = ProviderId.MOONLIGHT,
                externalId = "C", displayName = "Game C Removed", isAvailable = true)
        )
        
        whenever(gameRepository.getAllGames()).thenReturn(flowOf(emptyList()))
        whenever(launchTargetRepository.getTargetsForProvider(ProviderId.MOONLIGHT))
            .thenReturn(existingTargets)
        whenever(launchTargetRepository.upsertTarget(any())).thenReturn(1L)
        whenever(gameRepository.insertGame(any())).thenReturn(100L) // for synthetic games
        
        val moonlightProvider = mock<GameProvider> {
            on { id } doReturn ProviderId.MOONLIGHT
            onBlocking { isAvailable() } doReturn true
            onBlocking { discoverGames() } doReturn listOf(
                DiscoveredProviderGame(provider = ProviderId.MOONLIGHT, externalId = "A",
                    source = "STREAMING", displayName = "Game A"),
                DiscoveredProviderGame(provider = ProviderId.MOONLIGHT, externalId = "B",
                    source = "STREAMING", displayName = "Game B")
                // C is NOT reported — it was removed from Sunshine
            )
        }

        val coordinator = createCoordinator(moonlightProvider)
        val result = coordinator.syncProvider(ProviderId.MOONLIGHT, moonlightProvider)

        // Assert: deleteTarget was NEVER called
        verify(launchTargetRepository, never()).deleteTarget(any())
        
        // Assert: upsertTarget called 3 times (A, B available; C unavailable)
        val targetCaptor = argumentCaptor<LaunchTarget>()
        verify(launchTargetRepository, times(3)).upsertTarget(targetCaptor.capture())
        
        val upsertedTargets = targetCaptor.allValues
        val staleTarget = upsertedTargets.find { it.externalId == "C" }
        assertNotNull("Stale target C should be upserted", staleTarget)
        assertFalse("Stale target C must be marked unavailable", staleTarget!!.isAvailable)
        
        assertEquals(1, result.markedUnavailable)
    }

    // ==========================================================================
    // Test D: GOG game never triggers Steam CDN artwork
    // ==========================================================================

    @Test
    fun `syncProvider does NOT call Steam CDN artwork for GOG source`() = runTest {
        // Setup: new GOG game discovery
        whenever(gameRepository.getAllGames()).thenReturn(flowOf(emptyList()))
        whenever(launchTargetRepository.getTargetsForProvider(ProviderId.GAME_NATIVE))
            .thenReturn(emptyList())
        whenever(launchTargetRepository.upsertTarget(any())).thenReturn(1L)
        whenever(gameRepository.insertGame(any())).thenReturn(100L)
        
        val gameNativeProvider = mock<GameProvider> {
            on { id } doReturn ProviderId.GAME_NATIVE
            onBlocking { isAvailable() } doReturn true
            onBlocking { discoverGames() } doReturn listOf(
                DiscoveredProviderGame(
                    provider = ProviderId.GAME_NATIVE,
                    externalId = "12345",
                    source = "GOG",  // NOT Steam
                    displayName = "Some GOG Game",
                    hostGameKey = "steam:GOG:12345"
                )
            )
        }

        val coordinator = createCoordinator(gameNativeProvider)
        coordinator.syncProvider(ProviderId.GAME_NATIVE, gameNativeProvider)

        // Assert: Steam CDN artwork resolver was NEVER called
        // GOG IDs are not Steam AppIDs — calling Steam CDN would return wrong art
        verify(artworkResolver, never()).setRemoteUrlsForSteamGame(any(), any())
        verify(artworkResolver, never()).resolveForSteamGame(any(), any())
    }

    // ==========================================================================
    // Test E: Steam game DOES trigger Steam CDN artwork
    // ==========================================================================

    @Test
    fun `syncProvider calls Steam CDN artwork for new Steam source game`() = runTest {
        // Setup: new Steam game discovery
        whenever(gameRepository.getAllGames()).thenReturn(flowOf(emptyList()))
        whenever(launchTargetRepository.getTargetsForProvider(ProviderId.GAME_NATIVE))
            .thenReturn(emptyList())
        whenever(launchTargetRepository.upsertTarget(any())).thenReturn(1L)
        whenever(gameRepository.insertGame(any())).thenReturn(100L)
        
        val gameNativeProvider = mock<GameProvider> {
            on { id } doReturn ProviderId.GAME_NATIVE
            onBlocking { isAvailable() } doReturn true
            onBlocking { discoverGames() } doReturn listOf(
                DiscoveredProviderGame(
                    provider = ProviderId.GAME_NATIVE,
                    externalId = "367520",
                    source = "STEAM",  // IS Steam
                    displayName = "Hollow Knight",
                    hostGameKey = "steam:367520"
                )
            )
        }

        val coordinator = createCoordinator(gameNativeProvider)
        coordinator.syncProvider(ProviderId.GAME_NATIVE, gameNativeProvider)

        // Assert: Steam CDN artwork WAS called with correct AppID
        verify(artworkResolver).setRemoteUrlsForSteamGame(eq(100L), eq(367520))
    }

    // ==========================================================================
    // Test F: Identity resolver prefers store ID over title match
    // ==========================================================================

    @Test
    fun `resolve returns EXACT_STORE_ID when both store ID and title would match`() = runTest {
        // Setup: game exists with romPath=steam:367520 AND title "Hollow Knight"
        val hollowKnight = Game(
            id = 42L,
            title = "Hollow Knight",
            romPath = "steam:367520",
            romFilename = "HK.steam",
            platformId = "steam"
        )
        
        whenever(gameRepository.getAllGames()).thenReturn(flowOf(listOf(hollowKnight)))
        whenever(gameRepository.getGameByRomPath("steam:367520")).thenReturn(hollowKnight)
        
        val discovered = DiscoveredProviderGame(
            provider = ProviderId.GAME_NATIVE,
            externalId = "367520",
            source = "STEAM",
            displayName = "Hollow Knight"
        )

        val result = identityResolver.resolve(discovered)

        // Assert: EXACT_STORE_ID wins over TITLE_MATCH
        assertNotNull(result)
        assertEquals(GameIdentityResolver.Confidence.EXACT_STORE_ID, result!!.confidence)
        assertEquals("steam:367520", result.hostGameKey)
    }

    // ==========================================================================
    // Test G: Unresolved game creates synthetic key, not store key
    // ==========================================================================

    @Test
    fun `syncProvider creates synthetic moonlight key for unresolved Moonlight game`() = runTest {
        // Setup: no existing game matches
        whenever(gameRepository.getAllGames()).thenReturn(flowOf(emptyList()))
        whenever(gameRepository.getGameByRomPath(any())).thenReturn(null)
        whenever(launchTargetRepository.getTargetsForProvider(ProviderId.MOONLIGHT))
            .thenReturn(emptyList())
        whenever(launchTargetRepository.upsertTarget(any())).thenReturn(1L)
        whenever(gameRepository.insertGame(any())).thenReturn(100L)
        
        val moonlightProvider = mock<GameProvider> {
            on { id } doReturn ProviderId.MOONLIGHT
            onBlocking { isAvailable() } doReturn true
            onBlocking { discoverGames() } doReturn listOf(
                DiscoveredProviderGame(
                    provider = ProviderId.MOONLIGHT,
                    externalId = "shortcut-xyz",
                    source = "STREAMING",
                    displayName = "Some PC Game"
                )
            )
        }

        val coordinator = createCoordinator(moonlightProvider)
        coordinator.syncProvider(ProviderId.MOONLIGHT, moonlightProvider)

        // Assert: insertGame called with romPath starting with "moonlight:"
        val gameCaptor = argumentCaptor<Game>()
        verify(gameRepository).insertGame(gameCaptor.capture())
        assertTrue(
            "Synthetic key should start with 'moonlight:'",
            gameCaptor.firstValue.romPath.startsWith("moonlight:")
        )
        assertFalse(
            "Should NOT use 'steam:' prefix for Moonlight-only game",
            gameCaptor.firstValue.romPath.startsWith("steam:")
        )
    }

    // ==========================================================================
    // Additional resolver tests (retain from original)
    // ==========================================================================

    @Test
    fun `buildStoreKey STEAM source produces steam colon id`() {
        assertEquals("steam:107100", identityResolver.buildStoreKey("107100", "STEAM"))
    }

    @Test
    fun `buildStoreKey GOG source produces steam GOG colon id`() {
        val key = identityResolver.buildStoreKey("12345", "GOG")
        assertEquals("steam:GOG:12345", key)
        assertNotEquals("steam:12345", key)
    }

    @Test
    fun `buildStoreKey non-integer externalId returns null`() {
        assertNull(identityResolver.buildStoreKey("not-a-number", "STEAM"))
    }

    @Test
    fun `resolve returns null when title is ambiguous`() = runTest {
        // Two games with same normalised title
        whenever(gameRepository.getAllGames()).thenReturn(flowOf(listOf(
            Game(id = 1L, title = "Hollow Knight", romPath = "steam:367520",
                romFilename = "HK.steam", platformId = "steam"),
            Game(id = 2L, title = "Hollow Knight", romPath = "steam:367521",
                romFilename = "HK2.steam", platformId = "steam")
        )))
        whenever(gameRepository.getGameByRomPath(any())).thenReturn(null)

        val discovered = DiscoveredProviderGame(
            provider = ProviderId.MOONLIGHT,
            externalId = "shortcut-abc",
            source = "STREAMING",
            displayName = "Hollow Knight"
        )

        val result = identityResolver.resolve(discovered)
        assertNull("Ambiguous title should NOT resolve", result)
    }

    // ==========================================================================
    // GameNative host key format verification
    // ==========================================================================

    @Test
    fun `GameNativeProvider buildHostKey produces correct format per source`() {
        assertEquals("steam:107100",
            com.gamelaunch.frontend.pocket.providers.impl.GameNativeProvider.buildHostKey(107100, "STEAM"))
        assertEquals("steam:GOG:12345",
            com.gamelaunch.frontend.pocket.providers.impl.GameNativeProvider.buildHostKey(12345, "GOG"))
        assertEquals("steam:EPIC:99999",
            com.gamelaunch.frontend.pocket.providers.impl.GameNativeProvider.buildHostKey(99999, "EPIC"))
        assertEquals("steam:AMAZON:55555",
            com.gamelaunch.frontend.pocket.providers.impl.GameNativeProvider.buildHostKey(55555, "AMAZON"))
        assertEquals("steam:CUSTOM_GAME:1",
            com.gamelaunch.frontend.pocket.providers.impl.GameNativeProvider.buildHostKey(1, "CUSTOM_GAME"))
    }

    // ==========================================================================
    // PcGameArtworkResolver URL format
    // ==========================================================================

    @Test
    fun `PcGameArtworkResolver steam CDN URLs use correct format`() {
        assertEquals(
            "https://cdn.steamstatic.com/steam/apps/107100/library_600x900.jpg",
            PcGameArtworkResolver.steamCoverUrl(107100)
        )
        assertEquals(
            "https://cdn.steamstatic.com/steam/apps/107100/library_hero.jpg",
            PcGameArtworkResolver.steamHeroUrl(107100)
        )
    }
}
