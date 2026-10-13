package io.github.rocketkid300.monochromewatch.core.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small key/value store for watch-side settings. SharedPreferences keeps this
 * dependency-free (no DataStore/Flow needed for two toggles and one URL).
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** When true, [CatalogRepository] asks the remote backend instead of the demo list. */
    var useRemoteBackend: Boolean
        get() = prefs.getBoolean(KEY_REMOTE, false)
        set(value) = prefs.edit().putBoolean(KEY_REMOTE, value).apply()

    /** Base URL of the user's own HiFi-API endpoint used by the remote backend. */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE, value).apply()

    /** Fetch album art from the third-party image CDN. Off by default: no requests beyond your endpoint. */
    var showCovers: Boolean
        get() = prefs.getBoolean(KEY_COVERS, false)
        set(value) = prefs.edit().putBoolean(KEY_COVERS, value).apply()

    companion object {
        /** Deliberately empty: there is no default server. The user supplies their own HiFi endpoint. */
        const val DEFAULT_BASE_URL = ""
        private const val PREFS = "monowatch_settings"
        private const val KEY_REMOTE = "use_remote_backend"
        private const val KEY_BASE = "base_url"
        private const val KEY_COVERS = "show_covers"
    }
}
