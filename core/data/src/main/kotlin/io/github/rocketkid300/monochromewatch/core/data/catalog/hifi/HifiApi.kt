package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import okhttp3.ResponseBody
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * The routes Monochrome's web client calls on a HiFi-API instance
 * (`LosslessAPI.fetchWithRetry` in upstream `js/api.js`):
 *
 *  - `/search/?s=<query>`           track search
 *  - `/track/?id=<id>&quality=<q>`  playback info incl. the stream manifest
 *
 * Responses are returned as raw [JsonElement]: upstream tolerates several
 * envelope shapes (`{version,data:{...}}`, bare objects, arrays of parts) and
 * we decode leniently in [HifiCatalog] rather than fail on the first surprise.
 *
 * Paths are relative to the user-configured instance base URL.
 */
interface HifiApi {

    @GET("search/")
    suspend fun searchTracks(@Query("s") query: String): JsonElement

    /** `?a=` artist search. */
    @GET("search/")
    suspend fun searchArtists(@Query("a") query: String): JsonElement

    /** `?al=` album search. */
    @GET("search/")
    suspend fun searchAlbums(@Query("al") query: String): JsonElement

    @GET("info/")
    suspend fun info(@Query("id") id: Long): JsonElement

    /** Tracks are paged with offset/limit (upstream uses limit=500 for follow-up pages). */
    @GET("album/")
    suspend fun album(
        @Query("id") id: Long,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null,
    ): JsonElement

    /** `?id=` is the artist profile; `?f=&skip_tracks=true` is the discography fallback. */
    @GET("artist/")
    suspend fun artist(
        @Query("id") id: Long? = null,
        @Query("f") discographyOf: Long? = null,
        @Query("skip_tracks") skipTracks: Boolean? = null,
    ): JsonElement

    /**
     * Newer route (upstream tries it before `/track/`). The response only carries
     * `attributes.uri`, a signed URL of the manifest, which is fetched with [fetchText].
     */
    @GET("trackManifests/")
    suspend fun trackManifests(
        @Query("id") id: Long,
        @Query("quality") quality: String,
        @Query("adaptive") adaptive: Boolean,
        @Query("formats") formats: List<String>,
    ): JsonElement

    /** GET an absolute URL (the signed manifest link returned by [trackManifests]). */
    @GET
    suspend fun fetchText(@Url url: String): ResponseBody

    @GET("track/")
    suspend fun track(
        @Query("id") id: Long,
        @Query("quality") quality: String,
    ): JsonElement
}
