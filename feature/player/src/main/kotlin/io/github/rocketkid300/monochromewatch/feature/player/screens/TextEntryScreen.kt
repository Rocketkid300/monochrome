package io.github.rocketkid300.monochromewatch.feature.player.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberScalingLazyListState

/**
 * One text field for everything typed on the watch (search query, endpoint URL). Focus is
 * requested on entry so Wear's keyboard, which also offers voice and handwriting, comes up.
 */
@Composable
fun TextEntryScreen(
    title: String,
    initial: String,
    keyboardType: KeyboardType,
    error: String?,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberScalingLazyListState(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text(title, style = MaterialTheme.typography.title2) }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colors.surface, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colors.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSubmit(text) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            if (error != null) {
                item { Text(error, style = MaterialTheme.typography.caption1, color = MaterialTheme.colors.error) }
            }
            item {
                Chip(label = { Text("OK") }, onClick = { onSubmit(text) }, modifier = Modifier.fillMaxWidth())
            }
            item {
                Chip(label = { Text("Cancel") }, onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
