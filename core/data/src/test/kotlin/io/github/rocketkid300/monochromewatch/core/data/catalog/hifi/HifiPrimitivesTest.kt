package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HifiPrimitivesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun hifiUriRoundTrips() {
        assertEquals("hifi://track/42", HifiUri.forTrack(42))
        assertEquals(42L, HifiUri.parseTrackId("hifi://track/42"))
    }

    @Test
    fun hifiUriRejectsForeignOrBrokenUris() {
        assertNull(HifiUri.parseTrackId("https://cdn.example/track/42"))
        assertNull(HifiUri.parseTrackId("hifi://track/abc"))
        assertNull(HifiUri.parseTrackId("hifi://album/42"))
        assertNull(HifiUri.parseTrackId(null))
    }

    @Test
    fun qualityAliasesMatchUpstreamTable() {
        assertEquals(HifiQuality.HI_RES_LOSSLESS, HifiQuality.parse("master"))
        assertEquals(HifiQuality.HI_RES_LOSSLESS, HifiQuality.parse("Hi-Res"))
        assertEquals(HifiQuality.LOSSLESS, HifiQuality.parse("hifi"))
        assertEquals(HifiQuality.HIGH, HifiQuality.parse(" sd "))
        assertEquals(HifiQuality.LOW, HifiQuality.parse("low_quality"))
        assertNull(HifiQuality.parse("DOLBY_ATMOS"))
        assertNull(HifiQuality.parse(""))
        assertNull(HifiQuality.parse(null))
    }

    @Test
    fun coverUrlsFollowUpstreamFormat() {
        assertEquals(
            "https://resources.tidal.com/images/ab/cd/ef/320x320.jpg",
            HifiImages.cover("ab-cd-ef"),
        )
        assertEquals(
            "https://resources.tidal.com/images/ab/cd/ef/750x750.jpg",
            HifiImages.cover("ab-cd-ef", 750),
        )
        assertEquals("https://x.example/a.jpg", HifiImages.cover("https://x.example/a.jpg"))
        assertNull(HifiImages.cover(null))
        assertNull(HifiImages.cover(" "))
    }

    @Test
    fun searchSectionFindsItemsAtAnyNestingDepth() {
        val flat = json.parseToJsonElement("""{"version":"2.7","data":{"items":[{"id":1},{"id":2}]}}""")
        val nested = json.parseToJsonElement("""{"data":{"tracks":{"items":[{"id":1}]},"albums":{"items":[]}}}""")
        val bare = json.parseToJsonElement("""[{"foo":1},{"items":[{"id":9}]}]""")
        val none = json.parseToJsonElement("""{"data":{"tracks":{"limit":0}}}""")

        assertEquals(2, SearchSection.items(flat).size)
        assertEquals(1, SearchSection.items(nested).size)
        assertEquals(1, SearchSection.items(bare).size)
        assertEquals(0, SearchSection.items(none).size)
        assertEquals(0, SearchSection.items(null).size)
    }
}
