package io.github.rocketkid300.monochromewatch.feature.player.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberScalingLazyListState
import io.github.rocketkid300.monochromewatch.feature.player.diagnostics.DiagnosticsReport
import io.github.rocketkid300.monochromewatch.feature.player.diagnostics.Reachability

/**
 * Read-only facts about this device. The app does not use WebView anywhere; the
 * WebView rows exist only so the real Watch 4 can answer the question.
 */
@Composable
fun DiagnosticsScreen(
    report: DiagnosticsReport,
    onRerun: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Text("Diagnostics", style = MaterialTheme.typography.title2) }
            item { Line("Device", "${report.device} (API ${report.sdkInt})") }

            val web = report.webView
            item { Line("FEATURE_WEBVIEW", if (web.featureFlag) "yes" else "no") }
            item {
                Line(
                    "WebView provider",
                    when {
                        web.error != null -> "error: ${web.error}"
                        web.providerPackage == null -> "none (WebView unavailable)"
                        else -> "${web.providerPackage} ${web.providerVersion.orEmpty()}".trim()
                    },
                )
            }

            item {
                Line(
                    "Endpoint",
                    report.endpoint.ifEmpty { "not set" },
                )
            }
            item {
                Line(
                    "Reachable",
                    when (val r = report.reachability) {
                        Reachability.NotConfigured -> "n/a"
                        Reachability.Checking -> "checking…"
                        is Reachability.Reached -> "HTTP ${r.httpStatus} in ${r.millis} ms"
                        is Reachability.Failed -> "no: ${r.reason}"
                    },
                )
            }

            item { Text("Audio decoders", style = MaterialTheme.typography.caption1) }
            report.decoders.forEach { d ->
                item { Line(d.label, d.decoder ?: "not available") }
            }

            item {
                Chip(
                    label = { Text("Run again") },
                    onClick = onRerun,
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

@Composable
private fun Line(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.caption1,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
