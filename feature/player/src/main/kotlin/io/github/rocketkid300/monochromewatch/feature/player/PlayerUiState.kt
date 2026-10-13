package io.github.rocketkid300.monochromewatch.feature.player

import io.github.rocketkid300.monochromewatch.core.data.settings.SettingsStore

/** Everything the Now Playing screen needs, snapshotted from the MediaController. */
data class NowPlayingState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val hasMedia: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
)

/** Watch-side settings mirrored into Compose state. */
data class SettingsState(
    val useRemoteBackend: Boolean = false,
    val baseUrl: String = SettingsStore.DEFAULT_BASE_URL,
    val showCovers: Boolean = false,
)
