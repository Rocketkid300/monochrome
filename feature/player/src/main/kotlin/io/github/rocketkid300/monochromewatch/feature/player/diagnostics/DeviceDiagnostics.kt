package io.github.rocketkid300.monochromewatch.feature.player.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodecList
import android.webkit.WebView
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rocketkid300.monochromewatch.core.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * What the app is allowed to assume about this device. The app never *uses* a
 * WebView; this only reports whether one exists, so the answer for the real
 * Galaxy Watch 4 comes from the device and not from guesswork.
 */
data class WebViewStatus(
    /** `PackageManager.FEATURE_WEBVIEW`: the platform claims to support WebView. */
    val featureFlag: Boolean,
    /** Package of the active WebView provider, or null if there is none. */
    val providerPackage: String?,
    val providerVersion: String?,
    /** Set if asking the platform threw. */
    val error: String? = null,
) {
    /** True only if a provider is actually installed and active. */
    val available: Boolean get() = providerPackage != null
}

sealed interface Reachability {
    data object NotConfigured : Reachability
    data object Checking : Reachability
    data class Reached(val httpStatus: Int, val millis: Long) : Reachability
    data class Failed(val reason: String) : Reachability
}

data class DecoderSupport(val label: String, val mimeType: String, val decoder: String?) {
    val supported: Boolean get() = decoder != null
}

data class DiagnosticsReport(
    val sdkInt: Int,
    val device: String,
    val webView: WebViewStatus,
    val endpoint: String,
    val reachability: Reachability,
    val decoders: List<DecoderSupport>,
)

class DeviceDiagnostics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val settings: SettingsStore,
) {

    /** Everything that does not need the network. */
    fun local(): DiagnosticsReport {
        val endpoint = settings.baseUrl.trim()
        return DiagnosticsReport(
            sdkInt = android.os.Build.VERSION.SDK_INT,
            device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            webView = webViewStatus(),
            endpoint = endpoint,
            reachability = if (endpoint.isEmpty()) Reachability.NotConfigured else Reachability.Checking,
            decoders = decoders(),
        )
    }

    /** Plain GET of the configured endpoint's base URL. Any HTTP status counts as "reachable". */
    suspend fun reachability(): Reachability {
        val endpoint = settings.baseUrl.trim()
        if (endpoint.isEmpty()) return Reachability.NotConfigured

        val request = try {
            Request.Builder().url(endpoint).get().build()
        } catch (e: IllegalArgumentException) {
            return Reachability.Failed("Not a valid URL")
        }
        val probe = client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()

        return withContext(Dispatchers.IO) {
            val start = System.nanoTime()
            try {
                probe.newCall(request).execute().use { response ->
                    Reachability.Reached(response.code, (System.nanoTime() - start) / 1_000_000)
                }
            } catch (e: IOException) {
                Reachability.Failed(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
            }
        }
    }

    /**
     * Asks the platform which WebView provider is active. Does not construct a
     * WebView (that throws when none is installed). Same call as
     * `WebViewCompat.getCurrentWebViewPackage`, which delegates to it on API 26+.
     */
    private fun webViewStatus(): WebViewStatus {
        val flag = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
        return try {
            val pkg = WebView.getCurrentWebViewPackage()
            WebViewStatus(flag, pkg?.packageName, pkg?.versionName)
        } catch (e: Exception) {
            WebViewStatus(flag, null, null, error = e.javaClass.simpleName)
        }
    }

    /** First non-encoder codec that handles each audio format a HiFi endpoint is likely to serve. */
    private fun decoders(): List<DecoderSupport> {
        val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        return FORMATS.map { (label, mime) ->
            val match = infos.firstOrNull { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
            DecoderSupport(label, mime, match?.name)
        }
    }

    private companion object {
        val FORMATS = listOf(
            "AAC" to "audio/mp4a-latm",
            "FLAC" to "audio/flac",
            "MP3" to "audio/mpeg",
            "Opus" to "audio/opus",
            "E-AC-3" to "audio/eac3",
        )
    }
}
