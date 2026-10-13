package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException

/** The pure core of PlaybackService's resolver: hifi://track/<id> -> real URL. */
class HifiUriResolveTest {

    @Test
    fun nonHifiUrisAreLeftAlone() {
        var called = false
        assertNull(HifiUri.resolve("https://cdn.example/a.m4a") { called = true; StreamSource.Direct("x") })
        assertTrue(!called)
    }

    @Test
    fun hifiUriIsReplacedByTheDirectUrlForThatId() {
        var seen = -1L
        val url = HifiUri.resolve(HifiUri.forTrack(42)) { seen = it; StreamSource.Direct("https://cdn.example/42.m4a") }
        assertEquals(42L, seen)
        assertEquals("https://cdn.example/42.m4a", url)
    }

    @Test
    fun resolvedUrlIsRecheckedAgainstTheCleartextPolicy() {
        assertEquals(
            "http://192.168.1.5:9000/t.m4a",
            HifiUri.resolve("hifi://track/1") { StreamSource.Direct("http://192.168.1.5:9000/t.m4a") },
        )
        val e = assertThrows(IOException::class.java) {
            HifiUri.resolve("hifi://track/1") { StreamSource.Direct("http://cdn.example/t.m4a") }
        }
        assertTrue(e.message!!.contains("refused"))
    }

    @Test
    fun lookupFailuresBecomeIoExceptionWithCause() {
        val boom = HifiException("no endpoint")
        val e = assertThrows(IOException::class.java) { HifiUri.resolve("hifi://track/7") { throw boom } }
        assertEquals(boom, e.cause)
    }

    @Test
    fun cancellationPassesThroughUnwrapped() {
        assertThrows(CancellationException::class.java) { HifiUri.resolve("hifi://track/7") { throw CancellationException("stop") } }
    }

    @Test
    fun dashAndMalformedHifiUrisFailAsIoException() {
        assertThrows(IOException::class.java) { HifiUri.resolve("hifi://track/7") { StreamSource.Dash("<MPD/>") } }
        assertThrows(IOException::class.java) { HifiUri.resolve("hifi://track/abc") { StreamSource.Direct("x") } }
        assertThrows(IOException::class.java) { HifiUri.resolve("hifi://album/7") { StreamSource.Direct("x") } }
    }
}
