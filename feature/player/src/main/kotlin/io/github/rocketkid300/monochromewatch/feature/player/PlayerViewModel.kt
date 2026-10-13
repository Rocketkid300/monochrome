package io.github.rocketkid300.monochromewatch.feature.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rocketkid300.monochromewatch.core.data.catalog.MusicCatalog
import io.github.rocketkid300.monochromewatch.core.data.catalog.hifi.EndpointCheck
import io.github.rocketkid300.monochromewatch.core.data.catalog.hifi.EndpointPolicy
import io.github.rocketkid300.monochromewatch.core.data.model.Track
import io.github.rocketkid300.monochromewatch.core.data.settings.SettingsStore
import io.github.rocketkid300.monochromewatch.feature.player.playback.PlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the connection to [PlaybackService] and exposes catalog + playback
 * state to the Wear UI. Playback controls are forwarded to the MediaController.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalog: MusicCatalog,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _nowPlaying = MutableStateFlow(NowPlayingState())
    val nowPlaying: StateFlow<NowPlayingState> = _nowPlaying.asStateFlow()

    private val _settings = MutableStateFlow(
        SettingsState(
            useRemoteBackend = settingsStore.useRemoteBackend,
            baseUrl = settingsStore.baseUrl,
            showCovers = settingsStore.showCovers,
        ),
    )
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }
    }

    init {
        loadFeatured()
        connectToPlaybackService()

        // Keep the seek bar ticking while we are alive.
        viewModelScope.launch {
            while (isActive) {
                controller?.let { publish(it) }
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    fun refresh() = loadFeatured()

    /** Searches the active catalog (the user's HiFi endpoint when enabled, else the demo list). */
    fun search(query: String) {
        viewModelScope.launch {
            try {
                _tracks.value = catalog.search(query)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Leave the current list in place; the repository already degrades to the demo list.
            }
        }
    }

    private fun loadFeatured() {
        viewModelScope.launch {
            _tracks.value = catalog.featured()
        }
    }

    private fun connectToPlaybackService() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = connected
                connected.addListener(playerListener)
                publish(connected)
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun play(track: Track) {
        controller?.apply {
            setMediaItem(track.toMediaItem())
            prepare()
            play()
        }
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    /**
     * Saves the HiFi endpoint if [EndpointPolicy] accepts it. Returns an error message to show,
     * or null on success. Saving a valid endpoint also switches the remote backend on.
     */
    fun setEndpoint(raw: String): String? =
        when (val check = EndpointPolicy.check(raw)) {
            is EndpointCheck.Rejected -> check.reason
            is EndpointCheck.Ok -> {
                settingsStore.baseUrl = check.url
                settingsStore.useRemoteBackend = true
                _settings.update { it.copy(baseUrl = check.url, useRemoteBackend = true) }
                null
            }
        }

    fun setShowCovers(enabled: Boolean) {
        settingsStore.showCovers = enabled
        _settings.update { it.copy(showCovers = enabled) }
    }

    fun setRemoteBackend(enabled: Boolean) {
        settingsStore.useRemoteBackend = enabled
        _settings.update { it.copy(useRemoteBackend = enabled) }
        loadFeatured()
    }

    private fun publish(player: Player) {
        val metadata = player.currentMediaItem?.mediaMetadata
        _nowPlaying.value = NowPlayingState(
            isConnected = true,
            isPlaying = player.isPlaying,
            hasMedia = player.currentMediaItem != null,
            title = metadata?.title?.toString().orEmpty(),
            artist = metadata?.artist?.toString().orEmpty(),
            artworkUrl = metadata?.artworkUri?.toString(),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
        )
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controller?.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
        super.onCleared()
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 1000L
    }
}
