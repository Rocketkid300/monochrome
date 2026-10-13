package io.github.rocketkid300.monochromewatch.core.data.catalog

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCatalogTest {

    private val catalog = DemoCatalog()

    @Test
    fun featured_returnsPlayableHttpTracks() = runTest {
        val tracks = catalog.featured()
        assertTrue("expected some demo tracks", tracks.isNotEmpty())
        assertTrue(tracks.all { it.streamUrl.startsWith("http") })
    }

    @Test
    fun search_matchesTitleIgnoringCase() = runTest {
        val results = catalog.search("song 3")
        assertEquals(1, results.size)
        assertEquals("sh-3", results.first().id)
    }

    @Test
    fun search_blankQuery_returnsEverything() = runTest {
        assertEquals(catalog.featured().size, catalog.search("   ").size)
    }

    @Test
    fun search_withNoMatch_returnsEmpty() = runTest {
        assertTrue(catalog.search("zzzz-no-such-track").isEmpty())
    }
}
