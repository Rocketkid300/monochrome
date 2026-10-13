package io.github.rocketkid300.monochromewatch.core.data.model

/**
 * A playable track, independent of where it came from (demo list or a
 * Monochrome/HiFi backend). [streamUrl] must be a URL the watch can fetch
 * directly with the media player.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val streamUrl: String,
    val durationMs: Long? = null,
)
