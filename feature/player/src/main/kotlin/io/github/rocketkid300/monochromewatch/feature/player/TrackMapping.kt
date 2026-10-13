package io.github.rocketkid300.monochromewatch.feature.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.rocketkid300.monochromewatch.core.data.model.Track

/** Maps a catalog [Track] onto the Media3 playback model. */
fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(streamUrl)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .apply { artworkUrl?.let { setArtworkUri(android.net.Uri.parse(it)) } }
            .build(),
    )
    .build()
