package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

/**
 * Search results do not carry a stream URL: the real contract resolves one per
 * track via `/track/?id=`, and those URLs expire. So a catalog [Track] from the
 * HiFi backend gets a stable placeholder URI, and the player resolves it to a
 * real URL just before it starts loading.
 *
 * Plain string handling on purpose (no `android.net.Uri`) so it is JVM-testable.
 */
object HifiUri {
    const val SCHEME = "hifi"
    private const val TRACK_PREFIX = "$SCHEME://track/"

    fun forTrack(id: Long): String = "$TRACK_PREFIX$id"

    /**
     * The pure core of the player's data-source resolver. Returns the real URL for a
     * `hifi://track/<id>` URI, or null if [uri] is not a hifi URI (leave it alone).
     * Failures surface as [java.io.IOException], which is what ExoPlayer expects from a
     * resolver; coroutine cancellation is passed through untouched.
     */
    fun resolve(uri: String, lookup: (Long) -> StreamSource): String? {
        if (!uri.startsWith("$SCHEME://")) return null
        val id = parseTrackId(uri) ?: throw java.io.IOException("Malformed hifi URI: $uri")
        val source = try {
            lookup(id)
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw java.io.IOException("Could not resolve stream for track $id", e)
        }
        return when (source) {
            is StreamSource.Direct -> when (val check = EndpointPolicy.checkResolvedUrl(source.url)) {
                is EndpointCheck.Ok -> check.url
                is EndpointCheck.Rejected -> throw java.io.IOException("Stream URL refused: ${check.reason}")
            }
            is StreamSource.Dash -> throw java.io.IOException("Hi-res DASH streams are not supported yet")
        }
    }

    fun parseTrackId(uri: String?): Long? =
        uri?.takeIf { it.startsWith(TRACK_PREFIX) }
            ?.removePrefix(TRACK_PREFIX)
            ?.toLongOrNull()
}

/** Album/artist art lives on a public image CDN addressed by UUID (upstream `getCoverUrl`). */
object HifiImages {
    private const val BASE = "https://resources.tidal.com/images"

    fun cover(idOrUrl: String?, size: Int = 320): String? {
        if (idOrUrl.isNullOrBlank()) return null
        if (idOrUrl.startsWith("http://") || idOrUrl.startsWith("https://")) return idOrUrl
        return "$BASE/${idOrUrl.replace('-', '/')}/${size}x$size.jpg"
    }
}
