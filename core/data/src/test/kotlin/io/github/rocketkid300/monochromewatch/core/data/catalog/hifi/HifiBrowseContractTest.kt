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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * info / album / artist / artist+album search. ALL FIXTURES ARE SYNTHETIC: shaped after
 * upstream's parsing code (js/api.js getAlbum, getArtist, deduplicateAlbums) and typings
 * (js/HiFi.ts). They were NOT captured from a live server.
 */
class HifiBrowseContractTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    /** Answers by path; [handler] sees the full URL so tests can vary by query. */
    private class Fake(val handler: (HttpUrl) -> String?) : Interceptor {
        val requests = mutableListOf<HttpUrl>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val url = chain.request().url
            requests += url
            val body = handler(url)
            return Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (body != null) 200 else 404).message("x")
                .body((body ?: "{}").toResponseBody("application/json".toMediaType())).build()
        }
    }

    private fun catalog(fake: Fake) =
        HifiCatalog(OkHttpClient.Builder().addInterceptor(fake).build(), json) { "https://hifi.example" }

    @Test
    fun trackInfoFindsTheMatchingEntryInSingleOrListShapes() = runTest {
        val single = Fake { """{"data":{"id":5,"title":"Five","duration":10}}""" }
        assertEquals("Five", catalog(single).trackInfo(5).title)
        assertEquals("5", single.requests.single().queryParameter("id"))
        assertEquals("/info/", single.requests.single().encodedPath)

        val list = Fake { """{"data":[{"id":4,"title":"Four"},{"item":{"id":5,"title":"Five"}}]}""" }
        assertEquals("Five", catalog(list).trackInfo(5).title)
    }

    @Test
    fun albumFollowsPaginationWithOffsetAndLimit() = runTest {
        fun item(id: Int) = """{"item":{"id":$id,"title":"T$id","duration":1},"type":"track"}"""
        val fake = Fake { url ->
            when (url.queryParameter("offset")) {
                null -> """{"data":{"id":9,"title":"Alb","numberOfTracks":3,"items":[${item(1)},${item(2)}]}}"""
                "2" -> """{"data":{"items":[${item(3)}]}}"""
                else -> null
            }
        }
        val result = catalog(fake).album(9)

        assertEquals("Alb", result.album.title)
        assertEquals(listOf(1L, 2L, 3L), result.tracks.map { it.id })
        val page2 = fake.requests.last()
        assertEquals("2", page2.queryParameter("offset"))
        assertEquals("500", page2.queryParameter("limit"))
    }

    @Test
    fun albumStopsWhenEndpointIgnoresOffsetAndRepeatsPageOne() = runTest {
        val page = """{"data":{"id":9,"title":"A","numberOfTracks":5,"items":[
            {"item":{"id":1,"title":"a","duration":1}},{"item":{"id":2,"title":"b","duration":1}}]}}"""
        val fake = Fake { page }
        assertEquals(2, catalog(fake).album(9).tracks.size)
        assertEquals(2, fake.requests.size) // first page + one rejected repeat, no endless loop
    }

    @Test
    fun albumWithoutMetadataTakesItFromFirstTrackAndFillsArtistAndDate() = runTest {
        val body = """{"data":{"items":[{"item":{"id":1,"title":"a","duration":1,
            "artist":{"id":7,"name":"Ann"},"streamStartDate":"2020-03-04T00:00:00.000+0000",
            "album":{"id":9,"title":"From Track"}}}]}}"""
        val r = catalog(Fake { body }).album(9)
        assertEquals("From Track", r.album.title)
        assertEquals("Ann", r.album.artist?.name)
        assertEquals("2020-03-04", r.album.releaseDate)
    }

    @Test
    fun artistScansTreeFiltersByCreditDedupesAndSorts() = runTest {
        val primary = """{"data":{"artist":{"id":7,"name":"Ann","picture":"p-q","artistTypes":["ARTIST"]},
          "rows":[{"modules":[{"pagedList":{"items":[
            {"id":101,"title":"Old","numberOfTracks":10,"releaseDate":"2001-01-01","type":"ALBUM","artists":[{"id":7,"name":"Ann"}]},
            {"id":102,"title":"New","numberOfTracks":12,"releaseDate":"2020-01-01","type":"ALBUM","artist":{"id":7,"name":"Ann"}},
            {"id":103,"title":"Dup","numberOfTracks":9,"releaseDate":"2010-01-01","type":"ALBUM","explicit":false,"artist":{"id":7}},
            {"id":104,"title":"Dup","numberOfTracks":9,"releaseDate":"2010-01-01","type":"ALBUM","explicit":true,"artist":{"id":7}},
            {"id":105,"title":"Single","numberOfTracks":1,"releaseDate":"2022-01-01","type":"SINGLE","artist":{"id":7}},
            {"id":106,"title":"Not Hers","numberOfTracks":3,"releaseDate":"2023-01-01","type":"ALBUM","artist":{"id":99}}
          ]}}]}],
          "tracks":[
            {"id":1,"title":"Meh","duration":100,"popularity":10,"artist":{"id":7,"name":"Ann"}},
            {"id":2,"title":"Hit","duration":100,"popularity":90,"artist":{"id":7,"name":"Ann"}},
            {"id":3,"title":"Other's","duration":100,"popularity":99,"artist":{"id":99,"name":"Bob"}}
          ]}}"""
        val fake = Fake { primary }
        val r = catalog(fake).artist(7)

        assertEquals("Ann", r.artist.name)
        assertEquals("ARTIST", r.artist.type) // prepareArtist: artistTypes[0] -> type
        assertEquals(listOf("New", "Dup", "Old"), r.albums.map { it.title })
        assertTrue(r.albums.first { it.title == "Dup" }.explicit) // explicit wins dedupe
        assertEquals(listOf("Single"), r.epsAndSingles.map { it.title })
        assertEquals(listOf("Hit", "Meh"), r.topTracks.map { it.title })
        assertEquals(1, fake.requests.size) // albums found, so no fallback fetch
    }

    @Test
    fun artistFallsBackToDiscographyRouteWhenPrimaryHasNoAlbums() = runTest {
        val fake = Fake { url ->
            if (url.queryParameter("f") != null) {
                """{"albums":{"items":[{"id":1,"title":"Found Later","numberOfTracks":4,"artist":{"id":7}}]}}"""
            } else {
                """{"data":{"id":7,"name":"Ann"}}"""
            }
        }
        val r = catalog(fake).artist(7)

        assertEquals(listOf("Found Later"), r.albums.map { it.title })
        val fallback = fake.requests.last()
        assertEquals("7", fallback.queryParameter("f"))
        assertEquals("true", fallback.queryParameter("skip_tracks"))
        assertNull(fallback.queryParameter("id"))
    }

    @Test
    fun artistAndAlbumSearchUseScopedQueryParams() = runTest {
        val fake = Fake { """{"data":{"items":[{"id":1,"name":"Ann","title":"Alb","artists":[{"id":1,"name":"Ann"}]}]}}""" }
        val c = catalog(fake)
        assertEquals("Ann", c.searchArtists("ann").single().name)
        assertEquals("Alb", c.searchAlbums("alb").single().title)
        assertEquals("ann", fake.requests[0].queryParameter("a"))
        assertEquals("alb", fake.requests[1].queryParameter("al"))
    }

    @Test
    fun albumDedupeKeepsMoreTaggedWhenExplicitnessMatches() {
        val plain = HifiAlbumDto(1, "X", 5)
        val tagged = HifiAlbumDto(2, "X", 5, mediaMetadata = HifiMediaMetadata(listOf("LOSSLESS", "HIRES_LOSSLESS")))
        assertEquals(2L, listOf(plain, tagged).deduplicated().single().id)
        assertEquals(2L, listOf(tagged, plain).deduplicated().single().id)
        assertEquals(2, listOf(plain, HifiAlbumDto(3, "X", 6)).deduplicated().size) // different track count
    }

    // ---- /trackManifests/ (synthetic fixtures, shaped after upstream normalizeTrackManifestResponse) ----

    private fun b64(s: String) = java.util.Base64.getEncoder().encodeToString(s.toByteArray())

    @Test
    fun trackManifestsRouteIsTriedFirstAndSignedManifestUriIsFetched() = runTest {
        val bts = """{"urls":["https://cdn.example/flac/7.flac"]}"""
        val fake = Fake { url ->
            when (url.encodedPath) {
                "/trackManifests/" -> """{"data":{"data":{"id":"7","type":"trackManifests",
                    "attributes":{"uri":"https://signed.example/m/7","formats":["FLAC"]}}}}"""
                "/m/7" -> bts
                else -> null
            }
        }
        val source = catalog(fake).resolveStream(7, HifiQuality.LOSSLESS)

        assertEquals(StreamSource.Direct("https://cdn.example/flac/7.flac"), source)
        assertEquals(listOf("/trackManifests/", "/m/7"), fake.requests.map { it.encodedPath })
        val q = fake.requests.first()
        assertEquals("7", q.queryParameter("id"))
        assertEquals("LOSSLESS", q.queryParameter("quality"))
        assertEquals("false", q.queryParameter("adaptive"))
        assertEquals(listOf("FLAC"), q.queryParameterValues("formats"))
    }

    @Test
    fun signedManifestMayBeDash() = runTest {
        val mpd = """<MPD type="static"><Period/></MPD>"""
        val fake = Fake { url ->
            when (url.encodedPath) {
                "/trackManifests/" -> """{"data":{"attributes":{"uri":"https://signed.example/m/8"}}}"""
                "/m/8" -> mpd
                else -> null
            }
        }
        assertEquals(StreamSource.Dash(mpd), catalog(fake).resolveStream(8, HifiQuality.HI_RES_LOSSLESS))
        assertEquals(listOf("FLAC_HIRES"), fake.requests.first().queryParameterValues("formats"))
    }

    @Test
    fun missingManifestsRouteFallsBackToTrackAndIsRememberedPerEndpoint() = runTest {
        val body = """{"data":{"manifest":"${b64("""{"urls":["https://cdn.example/9.m4a"]}""")}"}}"""
        val fake = Fake { url -> if (url.encodedPath == "/track/") body else null } // everything else 404
        val c = catalog(fake)

        assertEquals(StreamSource.Direct("https://cdn.example/9.m4a"), c.resolveStream(9))
        assertEquals(listOf("/trackManifests/", "/track/"), fake.requests.map { it.encodedPath })

        fake.requests.clear()
        c.resolveStream(9)
        assertEquals(listOf("/track/"), fake.requests.map { it.encodedPath }) // 404 remembered, no retry
    }

    @Test
    fun brokenManifestsResponseFallsBackToTrackButIsNotRemembered() = runTest {
        val trackBody = """{"data":{"manifest":"${b64("""{"urls":["https://cdn.example/1.m4a"]}""")}"}}"""
        val fake = Fake { url ->
            when (url.encodedPath) {
                "/trackManifests/" -> """{"data":{"attributes":{}}}""" // no uri: malformed
                "/track/" -> trackBody
                else -> null
            }
        }
        val c = catalog(fake)
        c.resolveStream(1)
        fake.requests.clear()
        c.resolveStream(1)
        assertEquals(listOf("/trackManifests/", "/track/"), fake.requests.map { it.encodedPath })
    }

    @Test
    fun failedSignedManifestFetchDoesNotMarkRouteUnsupported() = runTest {
        val trackBody = """{"data":{"manifest":"${b64("""{"urls":["https://cdn.example/2.m4a"]}""")}"}}"""
        val fake = Fake { url ->
            when (url.encodedPath) {
                "/trackManifests/" -> """{"data":{"attributes":{"uri":"https://signed.example/gone"}}}"""
                "/track/" -> trackBody
                else -> null // /gone -> 404
            }
        }
        val c = catalog(fake)
        c.resolveStream(2)
        fake.requests.clear()
        c.resolveStream(2)
        assertEquals("/trackManifests/", fake.requests.first().encodedPath) // still tried
    }

    // ---- hardening: signed-URL policy and DASH step-down ----

    @Test
    fun cleartextSignedManifestLinkOnPublicHostIsNeverFetchedAndFallsBackToTrack() = runTest {
        val trackBody = """{"data":{"manifest":"${b64("""{"urls":["https://cdn.example/3.m4a"]}""")}"}}"""
        val fake = Fake { url ->
            when (url.encodedPath) {
                "/trackManifests/" -> """{"data":{"attributes":{"uri":"http://public.example/m/3"}}}"""
                "/track/" -> trackBody
                else -> null
            }
        }
        assertEquals(StreamSource.Direct("https://cdn.example/3.m4a"), catalog(fake).resolveStream(3))
        assertEquals(listOf("/trackManifests/", "/track/"), fake.requests.map { it.encodedPath }) // /m/3 never requested
    }

    @Test
    fun resolvePlayableStepsDownFromDashToTheFirstPlainUrl() = runTest {
        val mpd = """<MPD type="static"><Period/></MPD>"""
        val fake = Fake { url ->
            when (url.encodedPath) {
                "/trackManifests/" -> """{"data":{"attributes":{"uri":"https://signed.example/m/${url.queryParameter("quality")}"}}}"""
                "/m/HI_RES_LOSSLESS" -> mpd
                "/m/LOSSLESS" -> """{"urls":["https://cdn.example/flac/4.flac"]}"""
                else -> null
            }
        }
        val source = catalog(fake).resolvePlayable(4, HifiQuality.HI_RES_LOSSLESS)

        assertEquals(StreamSource.Direct("https://cdn.example/flac/4.flac"), source)
        val asked = fake.requests.filter { it.encodedPath == "/trackManifests/" }.map { it.queryParameter("quality") }
        assertEquals(listOf("HI_RES_LOSSLESS", "LOSSLESS"), asked)
    }

    @Test
    fun resolvePlayableFailsClearlyWhenEverythingIsDash() = runTest {
        val mpd = """<MPD type="static"><Period/></MPD>"""
        val fake = Fake { url ->
            when (url.encodedPath) {
                "/trackManifests/" -> """{"data":{"attributes":{"uri":"https://signed.example/m"}}}"""
                "/m" -> mpd
                else -> null
            }
        }
        val e = org.junit.Assert.assertThrows(HifiException::class.java) {
            kotlinx.coroutines.runBlocking { catalog(fake).resolvePlayable(5, HifiQuality.HIGH) }
        }
        assertTrue(e.message!!.contains("DASH"))
        // names every quality tried, best first, not just the last one
        assertTrue(e.message!!, e.message!!.indexOf("HIGH") in 0 until e.message!!.indexOf("LOW"))
    }

    @Test
    fun publicHostCleartextStreamUrlIsRefusedOnEveryPath() = runTest {
        val httpManifest = b64("""{"urls":["http://cdn.example/leak.m4a"]}""")
        // /track/ route
        val viaTrack = Fake { url -> if (url.encodedPath == "/track/") """{"data":{"manifest":"$httpManifest"}}""" else null }
        val e1 = org.junit.Assert.assertThrows(HifiException::class.java) {
            kotlinx.coroutines.runBlocking { catalog(viaTrack).resolveStream(1) }
        }
        assertTrue(e1.message!!, e1.message!!.contains("refused"))

        // legacy OriginalTrackUrl
        val legacy = Fake { url -> if (url.encodedPath == "/track/") """[{"id":1,"duration":1},{"OriginalTrackUrl":"http://cdn.example/x.flac"}]""" else null }
        org.junit.Assert.assertThrows(HifiException::class.java) {
            kotlinx.coroutines.runBlocking { catalog(legacy).resolveStream(1) }
        }

        // /trackManifests/ route: refused there, falls back to /track/, refused again; playable path fails too
        val viaManifests = Fake { url ->
            when (url.encodedPath) {
                "/trackManifests/" -> """{"data":{"attributes":{"uri":"https://signed.example/m"}}}"""
                "/m" -> """{"urls":["http://cdn.example/leak.m4a"]}"""
                "/track/" -> """{"data":{"manifest":"$httpManifest"}}"""
                else -> null
            }
        }
        org.junit.Assert.assertThrows(HifiException::class.java) {
            kotlinx.coroutines.runBlocking { catalog(viaManifests).resolvePlayable(1) }
        }

        // a private-network http URL is still fine
        val lan = Fake { url -> if (url.encodedPath == "/track/") """{"data":{"manifest":"${b64("""{"urls":["http://192.168.1.5:9000/ok.m4a"]}""")}"}}""" else null }
        assertEquals(StreamSource.Direct("http://192.168.1.5:9000/ok.m4a"), catalog(lan).resolvePlayable(1))
    }
}
