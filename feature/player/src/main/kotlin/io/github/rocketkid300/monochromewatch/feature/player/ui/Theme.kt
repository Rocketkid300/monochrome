package io.github.rocketkid300.monochromewatch.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme

/** Thin wrapper so the app has one theme entry point. Wear defaults are already dark. */
@Composable
fun MonoWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
