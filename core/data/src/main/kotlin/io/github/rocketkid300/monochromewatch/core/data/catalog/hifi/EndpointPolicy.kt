package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

import java.net.URI

sealed interface EndpointCheck {
    data class Ok(val url: String) : EndpointCheck
    data class Rejected(val reason: String) : EndpointCheck
}

/**
 * Cleartext policy for the user-supplied HiFi endpoint.
 *
 * The manifest sets `usesCleartextTraffic` because Android cannot express "private
 * networks only" in XML. This enforces the narrower rule in code: `https://` is always
 * fine; `http://` only for hosts that are private by construction (loopback, RFC 1918,
 * link-local, Tailscale's 100.64/10 and `*.ts.net`, `.local`, single-label names).
 * The hostname rules are a heuristic: a single-label name could in principle resolve
 * publicly. Stream URLs the endpoint hands back are not re-checked.
 */
object EndpointPolicy {

    fun check(raw: String): EndpointCheck {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return EndpointCheck.Rejected("Enter your HiFi endpoint URL")

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return EndpointCheck.Rejected("Not a valid URL")
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (host.isNullOrEmpty() || (scheme != "http" && scheme != "https")) {
            return EndpointCheck.Rejected("Must look like https://host or http://host")
        }
        if (uri.userInfo != null) return EndpointCheck.Rejected("Remove the user:password@ part")
        if (scheme == "http" && !isPrivateHost(host)) {
            return EndpointCheck.Rejected("http:// is only allowed for LAN/Tailscale hosts; use https://")
        }
        return EndpointCheck.Ok(trimmed.trimEnd('/'))
    }

    /**
     * Same cleartext rule for URLs the endpoint hands back (signed manifest link, final
     * stream URL). Unlike [check] it does not require a bare base URL: paths, queries and
     * userinfo are fine, since signed links use them.
     */
    fun checkResolvedUrl(raw: String): EndpointCheck {
        val uri = try {
            URI(raw.trim())
        } catch (_: Exception) {
            return EndpointCheck.Rejected("Not a valid URL")
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (host.isNullOrEmpty() || (scheme != "http" && scheme != "https")) {
            return EndpointCheck.Rejected("Not an http(s) URL")
        }
        if (scheme == "http" && !isPrivateHost(host)) {
            return EndpointCheck.Rejected("Blocked cleartext URL to a public host ($host)")
        }
        return EndpointCheck.Ok(raw.trim())
    }

    internal fun isPrivateHost(host: String): Boolean {
        val h = host.removePrefix("[").removeSuffix("]")
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".ts.net") || h.endsWith(".localhost")) return true
        ipv4(h)?.let { (a, b) ->
            return a == 10 || a == 127 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 169 && b == 254) ||
                (a == 100 && b in 64..127)
        }
        if (h.contains(':')) { // IPv6 literal
            return h == "::1" || h.startsWith("fe80") || h.startsWith("fc") || h.startsWith("fd")
        }
        return !h.contains('.') // single-label (LAN / MagicDNS short name)
    }

    private fun ipv4(h: String): Pair<Int, Int>? {
        val parts = h.split('.')
        if (parts.size != 4) return null
        val nums = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return null }
        return nums[0] to nums[1]
    }
}
