package io.github.rocketkid300.monochromewatch.core.data.catalog

import io.github.rocketkid300.monochromewatch.core.data.catalog.hifi.HifiCatalog
import io.github.rocketkid300.monochromewatch.core.data.catalog.hifi.HifiException
import io.github.rocketkid300.monochromewatch.core.data.model.Track
import io.github.rocketkid300.monochromewatch.core.data.settings.SettingsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class CatalogRepositoryTest {

    private val demo = DemoCatalog()
    private val remote = mockk<HifiCatalog>()
    private val settings = mockk<SettingsStore>()
    private val repo = CatalogRepository(settings, demo, remote)

    private val hifiTrack = Track("1", "Remote", "Artist", streamUrl = "hifi://track/1")

    private fun remoteOn(on: Boolean) = every { settings.useRemoteBackend } returns on

    @Test
    fun remoteOffNeverTouchesTheEndpoint() = runTest {
        remoteOn(false)
        assertEquals(demo.search("a"), repo.search("a"))
        coVerify(exactly = 0) { remote.searchTracks(any()) }
    }

    @Test
    fun remoteOnReturnsHifiResults() = runTest {
        remoteOn(true)
        coEvery { remote.searchTracks("q") } returns listOf(hifiTrack)
        assertEquals(listOf(hifiTrack), repo.search("q"))
    }

    @Test
    fun unconfiguredOrUnreachableEndpointDegradesToDemo() = runTest {
        remoteOn(true)
        coEvery { remote.searchTracks(any()) } throws HifiException("No HiFi API endpoint configured")
        assertEquals(demo.search("q"), repo.search("q"))

        coEvery { remote.searchTracks(any()) } throws IOException("offline")
        assertEquals(demo.search("q"), repo.search("q"))
    }

    @Test
    fun cancellationIsRethrownNotSwallowed() = runTest {
        remoteOn(true)
        coEvery { remote.searchTracks(any()) } throws CancellationException("cancelled")
        assertThrows(CancellationException::class.java) { kotlinx.coroutines.runBlocking { repo.search("q") } }
    }

    @Test
    fun unexpectedErrorsAreNotHiddenAsDemoResults() = runTest {
        remoteOn(true)
        coEvery { remote.searchTracks(any()) } throws IllegalStateException("bug")
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { repo.search("q") } }
    }
}
