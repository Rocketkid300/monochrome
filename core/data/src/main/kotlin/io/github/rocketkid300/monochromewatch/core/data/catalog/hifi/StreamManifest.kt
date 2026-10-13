package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64

/** Something the media player can actually be pointed at. */
sealed interface StreamSource {
    /** A plain progressive/HLS URL (the common `application/vnd.tidal.bts` case). */
    data class Direct(val url: String) : StreamSource

    /**
     * An inline DASH manifest (hi-res tracks). Parsed so it can be recognised, never played: DASH
     * playback is intentionally unsupported, see [HifiCatalog.resolvePlayable].
     */
    data class Dash(val mpdXml: String) : StreamSource
}

/**
 * Kotlin port of Monochrome's `LosslessAPI.extractStreamUrlFromManifest`
 * (upstream `js/api.js`). The `manifest` field of a `/track/` response is
 * either base64 of a JSON `{"urls":[...]}` document or base64 of a DASH MPD.
 *
 * Differences from upstream, on purpose:
 *  - only `http`/`https` URLs are ever returned (upstream would hand any string to the player);
 *  - a DASH manifest is returned as text instead of a browser `blob:` URL.
 */
object StreamManifest {

    /** Upstream's preference order: earlier keyword wins, no match sorts last. */
    private val PRIORITY = listOf("flac", "lossless", "hi-res", "high")

    private val URL_IN_TEXT = Regex("""https?://[\w\-.~:?#\[@!${'$'}&'()*+,;=%/]+""")

    fun parse(manifest: String?): StreamSource? {
        if (manifest.isNullOrBlank()) return null

        val decoded = decodeBase64OrRaw(manifest)

        if (decoded.contains("<MPD")) return StreamSource.Dash(decoded)

        // Strict on purpose, like upstream's JSON.parse: anything that is not valid JSON
        // falls through to scraping the first URL out of the text.
        val root = runCatching { Json.parseToJsonElement(decoded) }.getOrNull()
            ?: return URL_IN_TEXT.find(decoded)?.value?.let(StreamSource::Direct)

        val urls = ((root as? JsonObject)?.get("urls") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            ?: return null

        return pickUrl(urls)?.let(StreamSource::Direct)
    }

    /** Upstream sorts by index of the first matching keyword; `sortedBy` is stable like modern JS sort. */
    internal fun pickUrl(urls: List<String>): String? =
        urls.filter(::isHttp)
            .sortedBy { url ->
                val lower = url.lowercase()
                PRIORITY.indexOfFirst { it in lower }.let { if (it == -1) 999 else it }
            }
            .firstOrNull()

    internal fun isHttp(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    /** `atob` strips ASCII whitespace and throws on garbage; on failure upstream uses the raw string. */
    private fun decodeBase64OrRaw(value: String): String =
        runCatching {
            String(Base64.getDecoder().decode(value.filterNot { it.isWhitespace() }), Charsets.UTF_8)
        }.getOrDefault(value)
}
