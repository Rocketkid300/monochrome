package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Port of the response-tree walking in upstream `LosslessAPI.getArtist`
 * (js/api.js). The `/artist/` route has no fixed shape, so upstream scans every
 * nested object and classifies it as album / track / video by its fields.
 * Videos are ignored here (a watch plays audio).
 */
internal object ArtistScan {

    class Found {
        val albums = LinkedHashMap<Long, JsonObject>()
        val tracks = LinkedHashMap<Long, JsonObject>()
    }

    /** Upstream `json.data || json`. */
    fun unwrap(root: JsonElement): JsonElement =
        (root as? JsonObject)?.get("data")?.takeIf { it !is JsonNull } ?: root

    fun scan(value: JsonElement?, found: Found = Found()): Found {
        scan(value, found, Collections.newSetFromMap(IdentityHashMap()))
        return found
    }

    private fun scan(value: JsonElement?, found: Found, visited: MutableSet<JsonElement>) {
        when (value) {
            is JsonArray -> value.forEach { scan(it, found, visited) }
            is JsonObject -> {
                if (!visited.add(value)) return
                val item = value["item"] as? JsonObject ?: value
                val type = item.string("type")?.lowercase().orEmpty()
                val id = item.id()
                if (id != null) {
                    val album = isAlbum(item, type)
                    val video = type.contains("video")
                    if (album || type == "album") found.albums[id] = item
                    if ((isTrack(item, type) || type == "track") && !album && !video) found.tracks[id] = item
                }
                value.values.forEach { scan(it, found, visited) }
            }
            else -> Unit
        }
    }

    private fun isAlbum(v: JsonObject, type: String) =
        "numberOfTracks" in v || "numberOfItems" in v || type == "album"

    private fun isTrack(v: JsonObject, type: String): Boolean {
        val duration = (v["duration"] as? JsonPrimitive)?.let { it.doubleOrNull ?: if (it.contentOrNull.isNullOrEmpty()) 0.0 else 1.0 }
        val trackNumber = v["trackNumber"]?.takeIf { it !is JsonNull }
        return (duration != null && duration != 0.0) || trackNumber != null || type == "track"
    }

    /** Upstream `matchesArtistId`: does this release/track credit [artistId]? */
    fun matchesArtist(item: JsonObject, artistId: Long, requestedId: Long): Boolean {
        val candidates = mutableListOf<Long>()
        fun add(e: JsonElement?) {
            (e as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.let(candidates::add)
        }
        add(item["artistId"])
        add(item["artist_id"])
        (item["artist"] as? JsonObject)?.let { add(it["id"]) }
        (item["artists"] as? JsonArray)?.forEach { add((it as? JsonObject)?.get("id")) }
        (item["artistRoles"] as? JsonArray)?.forEach { add(((it as? JsonObject)?.get("artist") as? JsonObject)?.get("id")) }
        if (item["artist"] is JsonPrimitive) add(item["artist"])
        return candidates.any { it == artistId || it == requestedId }
    }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.id(): Long? =
        (this["id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.takeIf { it != 0L }
}
