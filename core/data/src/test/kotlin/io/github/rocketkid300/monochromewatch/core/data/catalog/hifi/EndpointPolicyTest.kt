package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {

    private fun ok(raw: String) = (EndpointPolicy.check(raw) as EndpointCheck.Ok).url
    private fun rejected(raw: String) = EndpointPolicy.check(raw) is EndpointCheck.Rejected

    @Test
    fun httpsIsAlwaysAllowedAndTrailingSlashTrimmed() {
        assertEquals("https://hifi.example.com", ok("  https://hifi.example.com/  "))
        assertEquals("https://hifi.example.com/api/v2", ok("https://hifi.example.com/api/v2/"))
    }

    @Test
    fun httpAllowedOnlyForPrivateHosts() {
        listOf(
            "http://localhost:3000", "http://127.0.0.1:8080", "http://10.1.2.3", "http://192.168.1.50:3000",
            "http://172.16.0.1", "http://172.31.255.255", "http://100.64.0.1", "http://100.101.2.3:3000",
            "http://169.254.1.1", "http://pi5", "http://pi5.local:3000", "http://pi5.tailf5f622.ts.net:3000",
            "http://[::1]:3000", "http://[fd7a:115c::1]",
        ).forEach { assertTrue(it, EndpointPolicy.check(it) is EndpointCheck.Ok) }
    }

    @Test
    fun httpRejectedForPublicHosts() {
        listOf(
            "http://example.com", "http://8.8.8.8", "http://172.32.0.1", "http://172.15.0.1",
            "http://100.63.0.1", "http://100.128.0.1", "http://192.169.1.1", "http://hifi.example.com:3000",
        ).forEach { assertTrue(it, rejected(it)) }
    }

    @Test
    fun resolvedUrlsGetTheSameCleartextRuleButMayCarrySignaturesAndPaths() {
        fun okUrl(u: String) = EndpointPolicy.checkResolvedUrl(u) is EndpointCheck.Ok
        assertTrue(okUrl("https://cdn.example/a/b.flac?token=abc&exp=1"))
        assertTrue(okUrl("http://192.168.1.5:3000/m/7?sig=x"))
        assertTrue(okUrl("http://100.101.2.3/track.m4a"))
        assertTrue(!okUrl("http://cdn.example/a.flac"))
        assertTrue(!okUrl("http://8.8.8.8/a"))
        assertTrue(!okUrl("ftp://cdn.example/a"))
        assertTrue(!okUrl("javascript:alert(1)"))
        assertTrue(!okUrl("not a url"))
    }

    @Test
    fun malformedOrOtherSchemesRejected() {
        listOf("", "   ", "hifi.example.com", "ftp://x.example", "file:///etc/passwd", "https://", "http://a b", "https://user:pw@host.example")
            .forEach { assertTrue("'$it'", rejected(it)) }
    }
}
