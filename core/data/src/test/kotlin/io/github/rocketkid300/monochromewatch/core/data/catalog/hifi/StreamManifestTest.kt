package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Fixtures are synthetic: shaped after what upstream's `extractStreamUrlFromManifest`
 * accepts (js/api.js), not captured from a live endpoint.
 */
class StreamManifestTest {

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

    @Test
    fun base64BtsJsonYieldsFirstUrl() {
        val manifest = b64("""{"mimeType":"audio/mp4","codecs":"mp4a.40.2","urls":["https://cdn.example/a.m4a"]}""")
        assertEquals(StreamSource.Direct("https://cdn.example/a.m4a"), StreamManifest.parse(manifest))
    }

    @Test
    fun rawJsonThatIsNotBase64StillWorks() {
        val raw = """{"urls":["https://cdn.example/raw.m4a"]}"""
        assertEquals(StreamSource.Direct("https://cdn.example/raw.m4a"), StreamManifest.parse(raw))
    }

    @Test
    fun keywordPriorityPrefersFlacThenLosslessThenHiResThenHigh() {
        val urls = listOf(
            "https://cdn.example/plain.m4a",
            "https://cdn.example/high/track.m4a",
            "https://cdn.example/hi-res/track.m4a",
            "https://cdn.example/lossless/track.m4a",
            "https://cdn.example/flac/track.flac",
        )
        assertEquals("https://cdn.example/flac/track.flac", StreamManifest.pickUrl(urls))
        assertEquals("https://cdn.example/lossless/track.m4a", StreamManifest.pickUrl(urls - urls[4]))
        assertEquals("https://cdn.example/hi-res/track.m4a", StreamManifest.pickUrl(urls - urls[4] - urls[3]))
        assertEquals("https://cdn.example/high/track.m4a", StreamManifest.pickUrl(urls - urls[4] - urls[3] - urls[2]))
    }

    @Test
    fun unmatchedUrlsKeepOriginalOrder() {
        val urls = listOf("https://a.example/1", "https://b.example/2")
        assertEquals("https://a.example/1", StreamManifest.pickUrl(urls))
    }

    @Test
    fun base64DashManifestIsReturnedAsText() {
        val mpd = """<?xml version="1.0"?><MPD type="static"><Period/></MPD>"""
        val result = StreamManifest.parse(b64(mpd))
        assertTrue(result is StreamSource.Dash)
        assertEquals(mpd, (result as StreamSource.Dash).mpdXml)
    }

    @Test
    fun nonJsonTextFallsBackToFirstUrlInText() {
        val result = StreamManifest.parse("see https://cdn.example/x.flac?token=abc%2F1 for the file")
        assertEquals(StreamSource.Direct("https://cdn.example/x.flac?token=abc%2F1"), result)
    }

    @Test
    fun onlyHttpSchemesAreEverReturned() {
        val manifest = b64("""{"urls":["file:///etc/passwd","javascript:alert(1)","ftp://x/y"]}""")
        assertNull(StreamManifest.parse(manifest))
        assertEquals("https://ok.example/a", StreamManifest.pickUrl(listOf("file:///x", "https://ok.example/a")))
    }

    @Test
    fun relativeUrlsAreDroppedByDesign() {
        // Documented limitation: an endpoint that returns "/media/1.m4a" gets no stream, because
        // there is no base to resolve it against and guessing one could leak requests.
        assertNull(StreamManifest.parse(b64("""{"urls":["/media/1.m4a","media/2.m4a","//cdn.example/3.m4a"]}""")))
        assertEquals("https://ok.example/a", StreamManifest.pickUrl(listOf("/rel", "https://ok.example/a")))
    }

    @Test
    fun emptyAndMalformedInputYieldNull() {
        assertNull(StreamManifest.parse(null))
        assertNull(StreamManifest.parse(""))
        assertNull(StreamManifest.parse("   "))
        assertNull(StreamManifest.parse(b64("""{"urls":[]}""")))
        assertNull(StreamManifest.parse(b64("""{"somethingElse":true}""")))
        assertNull(StreamManifest.parse("no links in here at all"))
    }

    @Test
    fun whitespaceInsideBase64IsTolerated() {
        val manifest = b64("""{"urls":["https://cdn.example/w.m4a"]}""").chunked(10).joinToString("\n")
        assertEquals(StreamSource.Direct("https://cdn.example/w.m4a"), StreamManifest.parse(manifest))
    }
}
