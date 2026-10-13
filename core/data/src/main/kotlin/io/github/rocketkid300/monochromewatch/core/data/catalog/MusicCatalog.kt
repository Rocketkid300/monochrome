package io.github.rocketkid300.monochromewatch.core.data.catalog

import io.github.rocketkid300.monochromewatch.core.data.model.Track

/**
 * Source of tracks for the watch UI. Implemented by [CatalogRepository],
 * which routes to either the bundled demo list or a Monochrome backend.
 */
interface MusicCatalog {
    suspend fun featured(): List<Track>
    suspend fun search(query: String): List<Track>
}
