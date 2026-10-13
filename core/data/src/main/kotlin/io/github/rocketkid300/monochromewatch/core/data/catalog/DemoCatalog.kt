package io.github.rocketkid300.monochromewatch.core.data.catalog

import io.github.rocketkid300.monochromewatch.core.data.model.Track
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline demo catalog. Uses SoundHelix's freely available sample tracks so the
 * app is playable without any Monochrome backend, API keys or accounts.
 *
 * This is what runs by default. Turn on "Use remote backend" in the watch
 * settings to route through [CatalogRepository] instead.
 */
@Singleton
class DemoCatalog @Inject constructor() : MusicCatalog {

    override suspend fun featured(): List<Track> = TRACKS

    override suspend fun search(query: String): List<Track> {
        if (query.isBlank()) return TRACKS
        return TRACKS.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.orEmpty().contains(query, ignoreCase = true)
        }
    }

    companion object {
        private fun soundHelix(n: Int) =
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-$n.mp3"

        val TRACKS: List<Track> = listOf(
            Track(
                id = "sh-1",
                title = "SoundHelix Song 1",
                artist = "T. Schürger",
                album = "Demo Reel",
                streamUrl = soundHelix(1),
            ),
            Track(
                id = "sh-2",
                title = "SoundHelix Song 2",
                artist = "T. Schürger",
                album = "Demo Reel",
                streamUrl = soundHelix(2),
            ),
            Track(
                id = "sh-3",
                title = "SoundHelix Song 3",
                artist = "T. Schürger",
                album = "Demo Reel",
                streamUrl = soundHelix(3),
            ),
            Track(
                id = "sh-4",
                title = "SoundHelix Song 4",
                artist = "T. Schürger",
                album = "Demo Reel",
                streamUrl = soundHelix(4),
            ),
            Track(
                id = "sh-5",
                title = "SoundHelix Song 5",
                artist = "T. Schürger",
                album = "Demo Reel",
                streamUrl = soundHelix(5),
            ),
        )
    }
}
