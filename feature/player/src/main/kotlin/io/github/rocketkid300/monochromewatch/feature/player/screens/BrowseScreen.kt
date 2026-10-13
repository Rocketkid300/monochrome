package io.github.rocketkid300.monochromewatch.feature.player.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberScalingLazyListState
import androidx.compose.ui.unit.dp
import io.github.rocketkid300.monochromewatch.core.data.model.Track

@Composable
fun BrowseScreen(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(text = "Mono Watch", style = MaterialTheme.typography.title2)
            }
            item {
                Chip(
                    label = { Text("Now playing") },
                    onClick = onOpenNowPlaying,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    label = { Text("Search") },
                    onClick = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    label = { Text("Settings") },
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(count = tracks.size) { index ->
                val track = tracks[index]
                Chip(
                    label = { Text(track.title.ifBlank { "Untitled" }) },
                    secondaryLabel = { Text(track.artist.ifBlank { "Unknown artist" }) },
                    onClick = { onTrackClick(track) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
