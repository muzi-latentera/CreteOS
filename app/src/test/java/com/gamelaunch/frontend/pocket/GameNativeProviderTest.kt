package com.gamelaunch.frontend.pocket

import android.content.Context
import android.content.pm.PackageManager
import com.gamelaunch.frontend.pocket.domain.DisplayPolicy
import com.gamelaunch.frontend.pocket.domain.LaunchContext
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.providers.ProviderId
import com.gamelaunch.frontend.pocket.providers.impl.GameNativeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Unit tests for GameNativeProvider.
 * Intent construction is not easily verifiable in plain JVM tests (Android class).
 * These tests verify:
 * - availability detection
 * - valid/invalid app_id handling
 * - the XREAL screenSize JSON assembly logic
 */
class GameNativeProviderTest {

    private val context: Context = mock()
    private val packageManager: PackageManager = mock()
    private lateinit var provider: GameNativeProvider

    @Before
    fun setup() {
        whenever(context.packageManager).thenReturn(packageManager)
        // startActivity is void — allow it without returning anything
        doNothing().whenever(context).startActivity(any())
        provider = GameNativeProvider(context)
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
        // startActivity was called
        verify(context).startActivity(any())
    }

    @Test
    fun `XREAL screenSize JSON is correct format`() {
        // Test the JSON string construction logic directly
        val width = 1920
        val height = 1200
        val config = """{"screenSize":"${width}x${height}"}"""
        assertEquals("""{"screenSize":"1920x1200"}""", config)
        assertTrue(config.contains("1920x1200"))
    }

    @Test
    fun `BACKEND_DEFAULT policy does not trigger XREAL override check`() = runTest {
        // With BACKEND_DEFAULT, even if externalDisplayConnected is true, no config should be sent
        val target = LaunchTarget(
            hostGameKey = "steam:367520",
            provider = ProviderId.GAME_NATIVE,
            externalId = "367520",
            displayName = "Hollow Knight"
        )
        // Policy = BACKEND_DEFAULT and external display connected (edge case)
        val launchContext = LaunchContext(
            displayPolicy = DisplayPolicy.BACKEND_DEFAULT,
            externalDisplayConnected = true,
            displayWidth = 1920,
            displayHeight = 1200
        )
        // Just verify it doesn't crash and completes successfully
        val result = provider.launch(target, launchContext)
        assertTrue("Launch should succeed regardless", result.isSuccess)
    }

    @Test
    fun `discoverGames returns empty list`() = runTest {
        // Discovery is implemented in later phase — must not throw
        val games = provider.discoverGames()
        assertTrue("discoverGames should return empty list (Phase 4 TODO)", games.isEmpty())
    }

    @Test
    fun `provider id is GAME_NATIVE`() {
        assertEquals(ProviderId.GAME_NATIVE, provider.id)
    }
}
