package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.rocketkid300.monochromewatch.core.data.model.Track
import io.github.rocketkid300.monochromewatch.core.data.settings.SettingsStore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

class HifiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Talks to a HiFi-API-compatible endpoint **that the user supplies**, using the
 * same routes and response handling as Monochrome's web client.
 *
 * Deliberately NOT ported from upstream:
 *  - The direct-to-TIDAL access path in `HiFi.ts`. This class only ever talks
 *    to the endpoint the user configures.
 *  - Upstream's list of community API instances. There is no default endpoint
 *    here; an unconfigured app refuses to make requests.
 *
 * Network/HTTP failures propagate as-is (`IOException`, `retrofit2.HttpException`)
 * and coroutine cancellation is never swallowed; callers decide how to degrade.
 */
@Singleton
class HifiCatalog(
    private val client: OkHttpClient,
    private val json: Json,
    /** Cover art comes from a third-party CDN, so it is off unless the user opts in. */
    private val showCovers: () -> Boolean = { false },
    private val baseUrl: () -> String,
) {
    @Inject
    constructor(client: OkHttpClient, json: Json, settings: SettingsStore) :
        this(client, json, { settings.showCovers }, { settings.baseUrl })

    private companion object {
        const val MAX_ALBUM_TRACKS = 10_000
        const val TOP_TRACKS = 15
        val UNSUPPORTED_CODES = setOf(404, 405, 501)
    }

    private class Cached(val baseUrl: String, val api: HifiApi)

    @Volatile
    private var cached: Cached? = null

    /** Base URL whose endpoint answered "no such route" for /trackManifests/. */
    @Volatile
    private var manifestsUnsupportedFor: String? = null

    suspend fun searchTracks(query: String): List<Track> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        return SearchSection.items(api().searchTracks(q), key = "tracks")
            .mapNotNull(::decodeTrack)
            .filter { it.isStreamable }
            .map { it.toTrack(withCover = showCovers()) }
    }

    suspend fun searchAlbums(query: String): List<HifiAlbumDto> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return SearchSection.items(api().searchAlbums(q), key = "albums")
            .mapNotNull { decode(HifiAlbumDto.serializer(), it) }
            .map { it.prepared() }
    }

    suspend fun searchArtists(query: String): List<HifiArtistDto> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return SearchSection.items(api().searchArtists(q), key = "artists")
            .mapNotNull { decode(HifiArtistDto.serializer(), it) }
            .map { it.prepared() }
    }

    /** Upstream `getTrackMetadata`: `/info/?id=`, data may be one object or a list. */
    suspend fun trackInfo(id: Long): HifiTrackDto {
        val data = ArtistScan.unwrap(api().info(id))
        val items = (data as? JsonArray) ?: listOf(data)
        val found = items.firstOrNull { e ->
            val o = e as? JsonObject
            o?.idLong() == id || ((o?.get("item") as? JsonObject)?.idLong() == id)
        } ?: throw HifiException("Track metadata not found for $id")
        val payload = (found as JsonObject)["item"] as? JsonObject ?: found
        return decode(HifiTrackDto.serializer(), payload)
            ?: throw HifiException("Malformed /info response for $id")
    }

    /** Upstream `getAlbum`: metadata + paged track list (loop-guarded like upstream). */
    suspend fun album(id: Long): HifiAlbumResult {
        val data = ArtistScan.unwrap(api().album(id)) as? JsonObject
        var albumJson: JsonObject? = data?.takeIf { "numberOfTracks" in it || "title" in it }
        val firstItems = (data?.get("items") as? JsonArray).orEmpty()
        if (albumJson == null) {
            albumJson = (firstItems.firstOrNull().unwrapItem()?.get("album")) as? JsonObject
        }
        var album = albumJson?.let { decode(HifiAlbumDto.serializer(), it) }?.prepared()
            ?: throw HifiException("Album not found: $id")

        var tracks = firstItems.mapNotNull { e -> e.unwrapItem()?.let { decode(HifiTrackDto.serializer(), it) } }

        tracks.firstOrNull()?.let { first ->
            if (album.artist == null) album = album.copy(artist = first.artist)
            if (album.releaseDate == null) {
                val date = first.album?.releaseDate ?: first.streamStartDate?.substringBefore('T')
                album = album.copy(releaseDate = date)
            }
        }

        var offset = tracks.size
        while (tracks.size < album.numberOfTracks && tracks.size < MAX_ALBUM_TRACKS) {
            val next = nextAlbumPage(id, offset)
            if (next.isEmpty()) break
            // Upstream's guard: an endpoint that ignores offset returns page one again.
            if (tracks.isNotEmpty() && next.first().id == tracks.first().id) break
            tracks = tracks + next
            offset += next.size
        }
        return HifiAlbumResult(album, tracks)
    }

    private suspend fun nextAlbumPage(id: Long, offset: Int): List<HifiTrackDto> {
        val data = ArtistScan.unwrap(api().album(id, offset = offset, limit = 500))
        val items = (data as? JsonObject)?.get("items") as? JsonArray
            ?: (data as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonObject)?.get("items") as? JsonArray }
            ?: return emptyList()
        return items.mapNotNull { e -> e.unwrapItem()?.let { decode(HifiTrackDto.serializer(), it) } }
    }

    /**
     * Upstream `getArtist`: scan the whole `/artist/?id=` response for albums and
     * tracks, fall back to `/artist/?f=&skip_tracks=true` when no album turned up,
     * keep only items crediting this artist, dedupe, sort newest-first.
     */
    suspend fun artist(id: Long): HifiArtistResult {
        val primary = ArtistScan.unwrap(api().artist(id = id))
        val rawArtist = ((primary as? JsonObject)?.get("artist") as? JsonObject)
            ?: (primary as? JsonArray)?.firstOrNull() as? JsonObject
            ?: primary as? JsonObject
            ?: throw HifiException("Primary artist details not found for $id")
        val artist = decode(HifiArtistDto.serializer(), rawArtist)?.prepared()
            ?: throw HifiException("Malformed /artist response for $id")

        val found = ArtistScan.scan(primary)
        if (found.albums.isEmpty()) {
            // Upstream swallows a failed fallback fetch (a warning only); so do we, except cancellation.
            try {
                ArtistScan.scan(api().artist(discographyOf = id, skipTracks = true), found)
            } catch (e: java.io.IOException) {
                // fall through with whatever the primary response had
            } catch (e: retrofit2.HttpException) {
                // same
            }
        }

        val releases = found.albums.values
            .filter { ArtistScan.matchesArtist(it, artist.id, id) }
            .mapNotNull { decode(HifiAlbumDto.serializer(), it)?.prepared() }
            .deduplicated()
            .sortedByDescending { it.releaseDate.orEmpty() }
        val (eps, albums) = releases.partition { it.type == "EP" || it.type == "SINGLE" }

        val topTracks = found.tracks.values
            .filter { ArtistScan.matchesArtist(it, artist.id, id) }
            .mapNotNull { decode(HifiTrackDto.serializer(), it) }
            .sortedByDescending { it.popularity }
            .take(TOP_TRACKS)

        return HifiArtistResult(artist, albums, eps, topTracks)
    }

    private fun JsonElement?.unwrapItem(): JsonObject? =
        (this as? JsonObject)?.let { (it["item"] as? JsonObject) ?: it }

    private fun JsonObject.idLong(): Long? =
        (this["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, element: JsonElement): T? =
        try {
            json.decodeFromJsonElement(serializer, element)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    /**
     * Resolves a fresh stream for [trackId]. URLs expire, so call this right before playback.
     *
     * Same order as upstream `getTrack`: `/trackManifests/` first, then `/track/`. If an endpoint
     * answers 404/405/501 on the newer route it is remembered and skipped from then on.
     */
    suspend fun resolveStream(trackId: Long, quality: HifiQuality = HifiQuality.HIGH): StreamSource {
        val base = normalize(baseUrl())
        if (manifestsUnsupportedFor != base) {
            try {
                return viaTrackManifests(trackId, quality)
            } catch (e: retrofit2.HttpException) {
                if (e.code() in UNSUPPORTED_CODES) manifestsUnsupportedFor = base
            } catch (_: HifiException) {
                // malformed / empty manifest: fall back to /track/ like upstream
            } catch (_: java.io.IOException) {
                // network blip on the manifest hop: try the older route too
            }
        }
        return viaTrackRoute(trackId, quality)
    }

    /**
     * Like [resolveStream] but guarantees something ExoPlayer can open.
     *
     * DASH playback is intentionally NOT supported (design decision, not a TODO): the watch's
     * output is a Bluetooth codec or a mono speaker, so hi-res DASH adds bandwidth and battery
     * cost with no audible gain, and DASH segment URLs would widen the surface the stream
     * policy must police. So when a quality only yields DASH, the quality steps down
     * (HI_RES -> LOSSLESS -> HIGH -> LOW) until a plain URL turns up, and fails naming every
     * quality tried if none does. Network/HTTP errors are not retried at lower qualities.
     */
    suspend fun resolvePlayable(trackId: Long, quality: HifiQuality = HifiQuality.HIGH): StreamSource.Direct {
        val attempts = mutableListOf<String>()
        var cause: HifiException? = null
        // entries are declared best-first, so this walks quality downwards from the requested one
        for (q in HifiQuality.entries.dropWhile { it != quality }) {
            try {
                when (val source = resolveStream(trackId, q)) {
                    is StreamSource.Direct -> return source
                    is StreamSource.Dash -> attempts += "${q.wire}: DASH manifest (unsupported)"
                }
            } catch (e: HifiException) {
                attempts += "${q.wire}: ${e.message}"
                cause = e
            }
        }
        throw HifiException("No playable stream for $trackId. Tried " + attempts.joinToString("; "), cause)
    }

    private suspend fun viaTrackManifests(trackId: Long, quality: HifiQuality): StreamSource {
        val root = api().trackManifests(trackId, quality.wire, adaptive = false, formats = listOf(quality.manifestFormat))
        // Upstream: apiResponse.data?.data ?? apiResponse.data ?? apiResponse
        val outer = (root as? JsonObject)?.get("data") as? JsonObject
        val resource = (outer?.get("data") as? JsonObject) ?: outer ?: root as? JsonObject
        val uri = (((resource?.get("attributes") as? JsonObject)?.get("uri")) as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it.isString }?.content
            ?.takeIf(StreamManifest::isHttp)
            ?: throw HifiException("Malformed trackManifests response for $trackId")
        (EndpointPolicy.checkResolvedUrl(uri) as? EndpointCheck.Rejected)?.let {
            throw HifiException("Signed manifest link refused: ${it.reason}")
        }

        // A failure on this second hop says nothing about whether the route exists, so it must
        // not be an HttpException that resolveStream would read as "route unsupported".
        val text = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                api().fetchText(uri).use { it.string() }
            }
        } catch (e: retrofit2.HttpException) {
            throw HifiException("Signed manifest fetch failed: HTTP ${e.code()}", e)
        }
        return StreamManifest.parse(text)?.let(::enforce)
            ?: throw HifiException("No playable stream in manifest for $trackId")
    }

    private suspend fun viaTrackRoute(trackId: Long, quality: HifiQuality): StreamSource {
        val info = playbackInfo(api().track(trackId, quality.wire))
            ?: throw HifiException("Malformed /track response for $trackId")

        info.originalTrackUrl?.takeIf(StreamManifest::isHttp)?.let { return enforce(StreamSource.Direct(it)) }

        return StreamManifest.parse(info.manifest)?.let(::enforce)
            ?: throw HifiException("No playable stream in /track response for $trackId")
    }

    /**
     * The final stream URL is policy-checked here as well as in [HifiUri.resolve], so no caller of
     * this class can be handed a cleartext link to a public host.
     */
    private fun enforce(source: StreamSource): StreamSource {
        if (source is StreamSource.Direct) {
            (EndpointPolicy.checkResolvedUrl(source.url) as? EndpointCheck.Rejected)?.let {
                throw HifiException("Stream URL refused: ${it.reason}")
            }
        }
        return source
    }

    private fun decodeTrack(element: JsonElement): HifiTrackDto? {
        // Albums/playlists wrap tracks as {item, type}; upstream unwraps with `i.item || i`.
        val payload = (element as? JsonObject)?.get("item") as? JsonObject ?: element
        return try {
            json.decodeFromJsonElement(HifiTrackDto.serializer(), payload)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Upstream's `parseTrackLookup`: the info lives in `response.data`, in the
     * bare object, or (legacy) spread over an array where one entry carries
     * `manifest` and another `OriginalTrackUrl`.
     */
    private fun playbackInfo(root: JsonElement): HifiPlaybackInfo? {
        val candidates: List<JsonObject> = when (root) {
            is JsonArray -> root.filterIsInstance<JsonObject>()
            is JsonObject -> listOfNotNull((root["data"] as? JsonObject) ?: root)
            else -> emptyList()
        }
        val withManifest = candidates.firstOrNull { "manifest" in it }
        val withUrl = candidates.firstOrNull { "OriginalTrackUrl" in it }
        val base = withManifest ?: withUrl ?: return null

        return try {
            val info = json.decodeFromJsonElement(HifiPlaybackInfo.serializer(), base)
            if (info.originalTrackUrl == null && withUrl != null && withUrl !== base) {
                info.copy(originalTrackUrl = json.decodeFromJsonElement(HifiPlaybackInfo.serializer(), withUrl).originalTrackUrl)
            } else {
                info
            }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun api(): HifiApi {
        val base = normalize(baseUrl())
        cached?.takeIf { it.baseUrl == base }?.let { return it.api }

        val api = try {
            Retrofit.Builder()
                .baseUrl(base)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(HifiApi::class.java)
        } catch (e: IllegalArgumentException) {
            throw HifiException("Invalid HiFi API endpoint: $base", e)
        }
        cached = Cached(base, api)
        return api
    }

    private fun normalize(raw: String): String =
        when (val check = EndpointPolicy.check(raw)) {
            is EndpointCheck.Ok -> check.url + "/"
            is EndpointCheck.Rejected -> throw HifiException(check.reason)
        }
}
