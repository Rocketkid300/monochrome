package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

import kotlinx.serialization.Serializable

/* Album / artist wire types (upstream js/HiFi.ts: TidalAlbum, TidalArtistProfile). */

@Serializable
data class HifiMediaMetadata(val tags: List<String> = emptyList())

@Serializable
data class HifiAlbumDto(
    val id: Long,
    val title: String? = null,
    val numberOfTracks: Int = 0,
    val releaseDate: String? = null,
    val cover: String? = null,
    val explicit: Boolean = false,
    /** "ALBUM", "EP" or "SINGLE". */
    val type: String? = null,
    val artist: HifiArtistRef? = null,
    val artists: List<HifiArtistRef> = emptyList(),
    val mediaMetadata: HifiMediaMetadata? = null,
)

@Serializable
data class HifiArtistDto(
    val id: Long,
    val name: String? = null,
    val picture: String? = null,
    val type: String? = null,
    val artistTypes: List<String> = emptyList(),
)

/** Upstream `prepareAlbum`: promote `artists[0]` when `artist` is missing. */
fun HifiAlbumDto.prepared(): HifiAlbumDto =
    if (artist == null && artists.isNotEmpty()) copy(artist = artists.first()) else this

/** Upstream `prepareArtist`: promote `artistTypes[0]` when `type` is missing. */
fun HifiArtistDto.prepared(): HifiArtistDto =
    if (type == null && artistTypes.isNotEmpty()) copy(type = artistTypes.first()) else this

/** Upstream `deduplicateAlbums`: key (title, trackCount); prefer explicit, then more media tags. */
fun List<HifiAlbumDto>.deduplicated(): List<HifiAlbumDto> {
    val unique = LinkedHashMap<Pair<String?, Int>, HifiAlbumDto>()
    for (album in this) {
        val key = album.title to album.numberOfTracks
        val existing = unique[key]
        when {
            existing == null -> unique[key] = album
            album.explicit && !existing.explicit -> unique[key] = album
            !album.explicit && existing.explicit -> Unit
            (album.mediaMetadata?.tags?.size ?: 0) > (existing.mediaMetadata?.tags?.size ?: 0) -> unique[key] = album
        }
    }
    return unique.values.toList()
}

data class HifiAlbumResult(val album: HifiAlbumDto, val tracks: List<HifiTrackDto>)

data class HifiArtistResult(
    val artist: HifiArtistDto,
    /** Full-length releases, newest first. */
    val albums: List<HifiAlbumDto>,
    /** EPs and singles, newest first. */
    val epsAndSingles: List<HifiAlbumDto>,
    /** Up to 15 tracks by popularity. */
    val topTracks: List<HifiTrackDto>,
)
