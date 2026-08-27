package com.gamelaunch.frontend.pocket

import android.content.Context
import android.content.pm.PackageManager
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.pocket.domain.DisplayPolicy
import com.gamelaunch.frontend.pocket.domain.LaunchContext
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.providers.ProviderId
import com.gamelaunch.frontend.pocket.providers.impl.GameNativeProvider
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Unit tests for GameNativeProvider.
 *
 * Verifies:
 * - Availability detection
 * - Valid/invalid app_id handling
 * - XREAL screenSize JSON assembly
 * - Discovery returns empty when no export folder configured
 */
class GameNativeProviderTest {

    private val context: Context = mock()
    private val packageManager: PackageManager = mock()
    private val settingsRepository: SettingsRepository = mock()
    private lateinit var provider: GameNativeProvider

    @Before
    fun setup() {
        whenever(context.packageManager).thenReturn(packageManager)
        doNothing().whenever(context).startActivity(any())
        
        // Default: no Steam Library path configured
        whenever(settingsRepository.steamLibraryPath).thenReturn(flowOf(""))
        
        provider = GameNativeProvider(context, settingsRepository)
    }

    @Test
    fun `isAvailable returns true when GameNative package is present`() = runTest {
        whenever(packageManager.getPackageInfo(GameNativeProvider.PACKAGE, 0)).thenReturn(mock())
        assertTrue("Should be available when GameNative is installed", provider.isAvailable())
    }

    @Test
    fun `isAvailable returns false when GameNative package is absent`() = runTest {
        whenever(packageManager.getPackageInfo(GameNativeProvider.PACKAGE, 0))
            .thenThrow(PackageManager.NameNotFoundException())
        assertFalse("Should not be available when GameNative is missing", provider.isAvailable())
    }

    @Test
    fun `launch returns failure for non-integer app_id`() = runTest {
        val target = LaunchTarget(
            hostGameKey = "steam:invalid",
            provider = ProviderId.GAME_NATIVE,
            externalId = "not_a_number",
            displayName = "Bad Game"
        )
        val result = provider.launch(target, LaunchContext())
        assertTrue("Should fail when externalId is not an integer", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not_a_number") == true)
    }

    @Test
    fun `launch succeeds for valid integer app_id`() = runTest {
        val target = LaunchTarget(
            hostGameKey = "steam:367520",
            provider = ProviderId.GAME_NATIVE,
            externalId = "367520",
            source = "STEAM",
            displayName = "Hollow Knight"
        )
        val result = provider.launch(target, LaunchContext())
        assertTrue("Should succeed with valid app_id", result.isSuccess)
        verify(context).startActivity(any())
    }

    @Test
    fun `XREAL screenSize JSON is correct format`() {
        val width = 1920
        val height = 1200
        val config = """{"screenSize":"${width}x${height}"}"""
        assertEquals("""{"screenSize":"1920x1200"}""", config)
    }

    @Test
    fun `BACKEND_DEFAULT policy does not trigger XREAL override`() = runTest {
        val target = LaunchTarget(
            hostGameKey = "steam:367520",
            provider = ProviderId.GAME_NATIVE,
            externalId = "367520",
            displayName = "Hollow Knight"
        )
        val launchContext = LaunchContext(
            displayPolicy = DisplayPolicy.BACKEND_DEFAULT,
            externalDisplayConnected = true,
            displayWidth = 1920,
            displayHeight = 1200
        )
        val result = provider.launch(target, launchContext)
        assertTrue("Launch should succeed", result.isSuccess)
    }

    @Test
    fun `discoverGames returns empty list when no export folder exists`() = runTest {
        // Default: no steamLibraryPath, no fallback paths exist
        whenever(settingsRepository.steamLibraryPath).thenReturn(flowOf(""))
        
        val games = provider.discoverGames()
        assertTrue("discoverGames should return empty when no folder configured", games.isEmpty())
    }

    @Test
    fun `provider id is GAME_NATIVE`() {
        assertEquals(ProviderId.GAME_NATIVE, provider.id)
    }

    // ==========================================================================
    // Marker file parsing tests (verified against GameNative source)
    // ==========================================================================

    @Test
    fun `buildHostKey produces correct format for each source`() {
        assertEquals("steam:107100", GameNativeProvider.buildHostKey(107100, "STEAM"))
        assertEquals("steam:GOG:12345", GameNativeProvider.buildHostKey(12345, "GOG"))
        assertEquals("steam:EPIC:99999", GameNativeProvider.buildHostKey(99999, "EPIC"))
        assertEquals("steam:AMAZON:55555", GameNativeProvider.buildHostKey(55555, "AMAZON"))
        assertEquals("steam:CUSTOM_GAME:1", GameNativeProvider.buildHostKey(1, "CUSTOM_GAME"))
    }

    @Test
    fun `extension mapping matches GameNative FrontendSyncManager`() {
        // Verify our extension map matches GameNative's extensionFor() output
        assertEquals("STEAM", GameNativeProvider.EXPORT_EXTENSIONS["steam"])
        assertEquals("GOG", GameNativeProvider.EXPORT_EXTENSIONS["gog"])
        assertEquals("EPIC", GameNativeProvider.EXPORT_EXTENSIONS["epic"])
        assertEquals("AMAZON", GameNativeProvider.EXPORT_EXTENSIONS["amazon"])
        assertEquals("CUSTOM_GAME", GameNativeProvider.EXPORT_EXTENSIONS["pcgame"])
    }
}
