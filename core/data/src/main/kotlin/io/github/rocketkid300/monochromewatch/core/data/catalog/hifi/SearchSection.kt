package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Port of `LosslessAPI.findSearchSection` (upstream `js/api.js`). HiFi
 * endpoints disagree about how deeply they nest a search bucket
 * (`{data:{items}}`, `{data:{tracks:{items}}}`, ...), so upstream walks the
 * tree and returns the first object that has an `items` array.
 */
internal object SearchSection {

    fun items(response: JsonElement?, key: String = "tracks"): List<JsonElement> =
        (find(response, key)?.get("items") as? JsonArray) ?: emptyList()

    private fun find(source: JsonElement?, key: String): JsonObject? {
        when (source) {
            is JsonArray -> source.forEach { element -> find(element, key)?.let { return it } }
            is JsonObject -> {
                if (source["items"] is JsonArray) return source
                source[key]?.let { find(it, key) }?.let { return it }
                source.values.forEach { value -> find(value, key)?.let { return it } }
            }
            else -> Unit
        }
        return null
    }
}
