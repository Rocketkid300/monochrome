package io.github.rocketkid300.monochromewatch.feature.player.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberScalingLazyListState

@Composable
fun SettingsScreen(
    useRemoteBackend: Boolean,
    baseUrl: String,
    showCovers: Boolean,
    onToggleCovers: (Boolean) -> Unit,
    onToggleRemote: (Boolean) -> Unit,
    onEditEndpoint: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(text = "Settings", style = MaterialTheme.typography.title2)
            }
            item {
                Chip(
                    label = { Text("Remote backend") },
                    secondaryLabel = {
                        Text(if (useRemoteBackend) "On — your HiFi endpoint" else "Off — demo tracks")
                    },
                    onClick = { onToggleRemote(!useRemoteBackend) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    label = { Text("Album art") },
                    secondaryLabel = { Text(if (showCovers) "On (loads from image CDN)" else "Off (no cover requests)") },
                    onClick = { onToggleCovers(!showCovers) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    label = { Text("HiFi endpoint") },
                    secondaryLabel = { Text(baseUrl.ifBlank { "not set" }) },
                    onClick = onEditEndpoint,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    label = { Text("Diagnostics") },
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    label = { Text("Back") },
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
