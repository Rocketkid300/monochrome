package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

import io.github.rocketkid300.monochromewatch.core.data.model.Track
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire types for a HiFi-API endpoint, taken from Monochrome's own typings
 * (upstream `js/HiFi.ts`: TidalTrack, TidalArtistRef, TidalTrackAlbumRef,
 * PlaybackInfo in `js/container-classes.ts`). Only the fields a watch needs
 * are declared; everything is optional so an endpoint that omits one does not
 * take the whole result list down.
 */

@Serializable
data class HifiArtistRef(
    val id: Long = 0,
    val name: String? = null,
    val picture: String? = null,
)

@Serializable
data class HifiAlbumRef(
    val id: Long = 0,
    val title: String? = null,
    /** Cover image UUID, see [HifiImages.cover]. */
    val cover: String? = null,
    val releaseDate: String? = null,
)

@Serializable
data class HifiTrackDto(
    val id: Long,
    val title: String? = null,
    /** e.g. "Remastered"; Monochrome shows it next to the title. */
    val version: String? = null,
    /** Seconds. */
    val duration: Long = 0,
    val allowStreaming: Boolean = true,
    val streamReady: Boolean = true,
    val explicit: Boolean = false,
    val popularity: Double = 0.0,
    val streamStartDate: String? = null,
    val artist: HifiArtistRef? = null,
    val artists: List<HifiArtistRef> = emptyList(),
    val album: HifiAlbumRef? = null,
)

/** The `data` object of a `/track/?id=&quality=` response. */
@Serializable
data class HifiPlaybackInfo(
    val trackId: Long = 0,
    val audioQuality: String? = null,
    val manifestMimeType: String? = null,
    /** base64 of a BTS JSON document or a DASH MPD, see [StreamManifest]. */
    val manifest: String? = null,
    val bitDepth: Int? = null,
    val sampleRate: Int? = null,
    /** Older endpoints return a ready-to-play URL instead of a manifest. */
    @SerialName("OriginalTrackUrl") val originalTrackUrl: String? = null,
)

val HifiTrackDto.isStreamable: Boolean get() = allowStreaming && streamReady

fun HifiTrackDto.toTrack(withCover: Boolean = false): Track = Track(
    id = id.toString(),
    title = listOfNotNull(title?.takeIf { it.isNotBlank() }, version?.takeIf { it.isNotBlank() }?.let { "($it)" })
        .joinToString(" ")
        .ifBlank { "Unknown title" },
    artist = (artist ?: artists.firstOrNull())?.name?.takeIf { it.isNotBlank() } ?: "Unknown artist",
    album = album?.title,
    artworkUrl = if (withCover) HifiImages.cover(album?.cover) else null,
    streamUrl = HifiUri.forTrack(id),
    durationMs = duration.takeIf { it > 0 }?.times(1000),
)
