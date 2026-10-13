package io.github.rocketkid300.monochromewatch.core.data.catalog

import io.github.rocketkid300.monochromewatch.core.data.catalog.hifi.HifiCatalog
import io.github.rocketkid300.monochromewatch.core.data.model.Track
import io.github.rocketkid300.monochromewatch.core.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes catalog calls to the demo list or the user's HiFi endpoint based on
 * the setting, and falls back to the demo list if the endpoint is unconfigured
 * or unreachable (so the app always has something to play).
 *
 * The HiFi contract has no "featured" route, so the home list is always the demo list.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val settings: SettingsStore,
    private val demo: DemoCatalog,
    private val remote: HifiCatalog,
) : MusicCatalog {

    override suspend fun featured(): List<Track> = demo.featured()

    override suspend fun search(query: String): List<Track> =
        if (settings.useRemoteBackend) remoteOrNull { remote.searchTracks(query) } ?: demo.search(query)
        else demo.search(query)

    /**
     * Returns null on a failure the caller can degrade from: [HifiException][io.github.rocketkid300.monochromewatch.core.data.catalog.hifi.HifiException]
     * (unconfigured / malformed), network errors, and HTTP errors. Cancellation and programming
     * errors are never swallowed.
     */
    private suspend fun remoteOrNull(block: suspend () -> List<Track>): List<Track>? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: io.github.rocketkid300.monochromewatch.core.data.catalog.hifi.HifiException) {
            null
        } catch (_: IOException) {
            null
        } catch (_: retrofit2.HttpException) {
            null
        }
}
