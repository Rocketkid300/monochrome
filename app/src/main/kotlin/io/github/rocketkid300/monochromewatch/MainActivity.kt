package io.github.rocketkid300.monochromewatch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.rocketkid300.monochromewatch.feature.player.PlayerViewModel
import io.github.rocketkid300.monochromewatch.feature.player.diagnostics.DiagnosticsViewModel
import io.github.rocketkid300.monochromewatch.feature.player.screens.DiagnosticsScreen
import io.github.rocketkid300.monochromewatch.feature.player.screens.BrowseScreen
import io.github.rocketkid300.monochromewatch.feature.player.screens.NowPlayingScreen
import io.github.rocketkid300.monochromewatch.feature.player.screens.SettingsScreen
import io.github.rocketkid300.monochromewatch.feature.player.screens.TextEntryScreen
import io.github.rocketkid300.monochromewatch.feature.player.ui.MonoWatchTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MonoWatchTheme {
                val navController = rememberNavController()
                val viewModel: PlayerViewModel = hiltViewModel()

                val tracks by viewModel.tracks.collectAsStateWithLifecycle()
                val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
                val settings by viewModel.settings.collectAsStateWithLifecycle()

                RequestNotificationPermission()

                NavHost(navController = navController, startDestination = Routes.BROWSE) {
                    composable(Routes.BROWSE) {
                        BrowseScreen(
                            tracks = tracks,
                            onTrackClick = { track ->
                                viewModel.play(track)
                                navController.navigate(Routes.NOW_PLAYING) { launchSingleTop = true }
                            },
                            onSearch = {
                                navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                            },
                            onOpenNowPlaying = {
                                navController.navigate(Routes.NOW_PLAYING) { launchSingleTop = true }
                            },
                            onOpenSettings = {
                                navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                            },
                        )
                    }

                    composable(Routes.NOW_PLAYING) {
                        NowPlayingScreen(
                            state = nowPlaying,
                            onPlayPause = viewModel::togglePlayPause,
                            onNext = viewModel::next,
                            onPrevious = viewModel::previous,
                            onSeek = viewModel::seekTo,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            useRemoteBackend = settings.useRemoteBackend,
                            baseUrl = settings.baseUrl,
                            showCovers = settings.showCovers,
                            onToggleCovers = viewModel::setShowCovers,
                            onToggleRemote = viewModel::setRemoteBackend,
                            onEditEndpoint = {
                                navController.navigate(Routes.ENDPOINT) { launchSingleTop = true }
                            },
                            onOpenDiagnostics = {
                                navController.navigate(Routes.DIAGNOSTICS) { launchSingleTop = true }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(Routes.SEARCH) {
                        TextEntryScreen(
                            title = "Search",
                            initial = "",
                            keyboardType = KeyboardType.Text,
                            error = null,
                            onSubmit = { query ->
                                if (query.isNotBlank()) viewModel.search(query.trim())
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(Routes.ENDPOINT) {
                        var error by remember { mutableStateOf<String?>(null) }
                        TextEntryScreen(
                            title = "HiFi endpoint",
                            initial = settings.baseUrl,
                            keyboardType = KeyboardType.Uri,
                            error = error,
                            onSubmit = { url ->
                                error = viewModel.setEndpoint(url)
                                if (error == null) navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(Routes.DIAGNOSTICS) {
                        val diagnostics: DiagnosticsViewModel = hiltViewModel()
                        val report by diagnostics.report.collectAsStateWithLifecycle()
                        DiagnosticsScreen(
                            report = report,
                            onRerun = diagnostics::run,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

private object Routes {
    const val BROWSE = "browse"
    const val NOW_PLAYING = "now_playing"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val SEARCH = "search"
    const val ENDPOINT = "endpoint"
}

/** Media3 posts the playback notification; API 33+ needs runtime permission for it. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Result ignored: playback still works, only the notification is affected. */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
