package io.github.rocketkid300.monochromewatch.feature.player.playback

import android.content.Intent
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.rocketkid300.monochromewatch.core.data.catalog.hifi.HifiCatalog
import io.github.rocketkid300.monochromewatch.core.data.catalog.hifi.HifiUri
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hosts the ExoPlayer instance and its MediaSession so playback keeps running
 * when the watch UI is not in the foreground. The UI connects to this service
 * through a [androidx.media3.session.MediaController].
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var hifiCatalog: HifiCatalog

    private var mediaSession: MediaSession? = null

    /**
     * Search results only carry `hifi://track/<id>`. Just before ExoPlayer opens a track,
     * ask the user's HiFi endpoint (`/trackManifests/`, else `/track/`) for a fresh stream URL and swap it in.
     * Runs on ExoPlayer's loader thread, so blocking here is allowed.
     */
    private fun resolvingDataSourceFactory(): DataSource.Factory =
        ResolvingDataSource.Factory(DefaultDataSource.Factory(this)) { dataSpec ->
            val real = HifiUri.resolve(dataSpec.uri.toString()) { id -> runBlocking { hifiCatalog.resolvePlayable(id) } }
            if (real == null) dataSpec else dataSpec.withUri(Uri.parse(real))
        }

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingDataSourceFactory()))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
