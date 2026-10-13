package io.github.rocketkid300.monochromewatch.core.data.catalog.hifi

/**
 * Audio quality tokens understood by a HiFi-API endpoint's `/track/?quality=`.
 *
 * Ported from Monochrome's `QUALITY_TOKENS` / `normalizeQualityToken`
 * (upstream `js/utils.js`). Dolby Atmos tokens are deliberately left out: a
 * watch speaker / Bluetooth earbud path has no use for them.
 */
enum class HifiQuality(val wire: String, val manifestFormat: String, private val aliases: Set<String>) {
    HI_RES_LOSSLESS(
        "HI_RES_LOSSLESS",
        "FLAC_HIRES",
        setOf(
            "HI_RES_LOSSLESS", "HIRES_LOSSLESS", "HIRESLOSSLESS", "HIFI_PLUS", "HI_RES_FLAC",
            "HI_RES", "HIRES", "MASTER", "MASTER_QUALITY", "MQA", "UHD", "ULTRAHD",
        ),
    ),
    LOSSLESS("LOSSLESS", "FLAC", setOf("LOSSLESS", "HIFI", "HD")),
    HIGH("HIGH", "AACLC", setOf("HIGH", "HIGH_QUALITY", "SD", "SD_HIGH", "SD_MEDIUM")),
    LOW("LOW", "HEAACV1", setOf("LOW", "LOW_QUALITY", "LD", "SD_LOW")),
    ;

    companion object {
        /** Mirrors upstream `sanitizeToken` + alias lookup. Returns null for unknown tokens. */
        fun parse(value: String?): HifiQuality? {
            if (value.isNullOrBlank()) return null
            val token = value.trim().uppercase().replace(Regex("[^A-Z0-9]+"), "_")
            return entries.firstOrNull { token in it.aliases }
        }
    }
}
