package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

/**
 * Drives [HifiCatalog] through a fake endpoint (an OkHttp interceptor that
 * answers by URL path) and asserts on the requests it makes. This checks the
 * route/query contract ported from upstream `js/api.js` plus the response
 * handling. Bodies are synthetic, shaped after upstream's typings in
 * `js/HiFi.ts`; they are not captured from a real server.
 */
class HifiCatalogContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

    private class FakeEndpoint(private val routes: Map<String, String>) : Interceptor {
        val requests = mutableListOf<HttpUrl>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val url = chain.request().url
            requests += url
            val body = routes[url.encodedPath]
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(if (body != null) 200 else 404)
                .message(if (body != null) "OK" else "Not Found")
                .body((body ?: "{}").toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun catalog(endpoint: FakeEndpoint, base: String = "https://hifi.example") =
        HifiCatalog(OkHttpClient.Builder().addInterceptor(endpoint).build(), json) { base }

    private val searchBody = """
        {"version":"2.7","data":{"limit":25,"offset":0,"totalNumberOfItems":4,"items":[
          {"id":101,"title":"Song One","version":"Remastered","duration":215,
           "allowStreaming":true,"streamReady":true,"unknownField":"ignored",
           "artist":{"id":1,"name":"The Artist","picture":null},
           "artists":[{"id":1,"name":"The Artist"}],
           "album":{"id":9,"title":"The Album","cover":"aaaa-bbbb-cccc","vibrantColor":"#fff","videoCover":null}},
          {"id":102,"title":"Blocked","duration":100,"allowStreaming":false,"streamReady":true},
          {"id":"not-a-number","title":"Malformed"},
          {"id":103,"title":"Bare Minimum"}
        ]}}
    """.trimIndent()

    @Test
    fun searchHitsSearchRouteWithSParamAndMapsTracks() = runTest {
        val endpoint = FakeEndpoint(mapOf("/search/" to searchBody))

        val tracks = catalog(endpoint).searchTracks("  hello world ")

        val request = endpoint.requests.single()
        assertEquals("/search/", request.encodedPath)
        assertEquals("hello world", request.queryParameter("s"))
        assertEquals(listOf("101", "103"), tracks.map { it.id })

        val first = tracks.first()
        assertEquals("Song One (Remastered)", first.title)
        assertEquals("The Artist", first.artist)
        assertEquals("The Album", first.album)
        assertNull(first.artworkUrl) // covers are opt-in: no third-party image requests by default
        assertEquals("hifi://track/101", first.streamUrl)
        assertEquals(215_000L, first.durationMs)

        val bare = tracks.last()
        assertEquals("Bare Minimum", bare.title)
        assertEquals("Unknown artist", bare.artist)
        assertNull(bare.album)
        assertNull(bare.artworkUrl)
        assertNull(bare.durationMs)
    }

    @Test
    fun coversAreIncludedOnlyWhenEnabled() = runTest {
        val endpoint = FakeEndpoint(mapOf("/search/" to searchBody))
        val on = HifiCatalog(OkHttpClient.Builder().addInterceptor(endpoint).build(), json, { true }) { "https://hifi.example" }
        assertEquals(
            "https://resources.tidal.com/images/aaaa/bbbb/cccc/320x320.jpg",
            on.searchTracks("x").first().artworkUrl,
        )
    }

    @Test
    fun blankQueryMakesNoRequest() = runTest {
        val endpoint = FakeEndpoint(emptyMap())
        assertEquals(emptyList<Any>(), catalog(endpoint).searchTracks("   "))
        assertEquals(0, endpoint.requests.size)
    }

    @Test
    fun trackItemsWrappedInItemEnvelopeAreUnwrapped() = runTest {
        val body = """{"data":{"items":[{"type":"track","item":{"id":7,"title":"Wrapped","duration":60}}]}}"""
        val tracks = catalog(FakeEndpoint(mapOf("/search/" to body))).searchTracks("x")
        assertEquals(listOf("7"), tracks.map { it.id })
        assertEquals("Wrapped", tracks.single().title)
    }

    @Test
    fun resolveStreamHitsTrackRouteAndDecodesBtsManifest() = runTest {
        val manifest = b64("""{"mimeType":"audio/mp4","urls":["https://cdn.example/101.m4a"]}""")
        val body = """{"version":"2.7","data":{"trackId":101,"audioQuality":"HIGH",
            "manifestMimeType":"application/vnd.tidal.bts","manifest":"$manifest"}}"""
        val endpoint = FakeEndpoint(mapOf("/track/" to body))

        val source = catalog(endpoint).resolveStream(101, HifiQuality.HIGH)

        // /trackManifests/ is tried first (404 in this fake), then /track/ like upstream's getTrack.
        assertEquals(listOf("/trackManifests/", "/track/"), endpoint.requests.map { it.encodedPath })
        val request = endpoint.requests.last()
        assertEquals("101", request.queryParameter("id"))
        assertEquals("HIGH", request.queryParameter("quality"))
        assertEquals(StreamSource.Direct("https://cdn.example/101.m4a"), source)
    }

    @Test
    fun legacyArrayResponseUsesOriginalTrackUrlWhenPresent() = runTest {
        val manifest = b64("""{"urls":["https://cdn.example/from-manifest.m4a"]}""")
        val body = """[{"id":101,"duration":215},
            {"manifest":"$manifest","audioQuality":"LOSSLESS"},
            {"OriginalTrackUrl":"https://cdn.example/original.flac"}]"""

        val source = catalog(FakeEndpoint(mapOf("/track/" to body))).resolveStream(101)

        assertEquals(StreamSource.Direct("https://cdn.example/original.flac"), source)
    }

    @Test
    fun dashManifestIsSurfacedAsDash() = runTest {
        val mpd = """<MPD type="static"><Period/></MPD>"""
        val body = """{"data":{"manifest":"${b64(mpd)}","manifestMimeType":"application/dash+xml"}}"""

        val source = catalog(FakeEndpoint(mapOf("/track/" to body))).resolveStream(1, HifiQuality.HI_RES_LOSSLESS)

        assertEquals(StreamSource.Dash(mpd), source)
    }

    @Test
    fun resolveStreamFailsLoudlyOnUnusableResponses() = runTest {
        val noManifest = catalog(FakeEndpoint(mapOf("/track/" to """{"data":{"trackId":1}}""")))
        assertThrows(HifiException::class.java) { kotlinx.coroutines.runBlocking { noManifest.resolveStream(1) } }

        val emptyUrls = catalog(FakeEndpoint(mapOf("/track/" to """{"data":{"manifest":"${b64("""{"urls":[]}""")}"}}""")))
        assertThrows(HifiException::class.java) { kotlinx.coroutines.runBlocking { emptyUrls.resolveStream(1) } }
    }

    @Test
    fun endpointPathPrefixIsPreserved() = runTest {
        val endpoint = FakeEndpoint(mapOf("/api/v2/search/" to searchBody))
        val tracks = catalog(endpoint, base = "https://hifi.example/api/v2").searchTracks("x")
        assertEquals("/api/v2/search/", endpoint.requests.single().encodedPath)
        assertEquals(2, tracks.size)
    }

    @Test
    fun unconfiguredOrInvalidEndpointRefusesToMakeRequests() = runTest {
        val endpoint = FakeEndpoint(emptyMap())

        val blank = assertThrows(HifiException::class.java) {
            kotlinx.coroutines.runBlocking { catalog(endpoint, base = "  ").searchTracks("x") }
        }
        assertNotNull(blank.message)
        assertThrows(HifiException::class.java) {
            kotlinx.coroutines.runBlocking { catalog(endpoint, base = "ftp://hifi.example").searchTracks("x") }
        }
        assertEquals(0, endpoint.requests.size)
    }

    @Test
    fun httpErrorsPropagateInsteadOfBecomingEmptyResults() = runTest {
        val endpoint = FakeEndpoint(emptyMap()) // every route 404s
        assertThrows(retrofit2.HttpException::class.java) {
            kotlinx.coroutines.runBlocking { catalog(endpoint).searchTracks("x") }
        }
    }
}
