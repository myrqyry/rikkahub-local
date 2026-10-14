package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.tts.rvc.RvcFilterSettings
import me.rerere.tts.rvc.VoxaRvcClient
import me.rerere.tts.rvc.RvcEndpoint

@Composable
fun RvcFilterCard(
    settings: RvcFilterSettings,
    onChange: (RvcFilterSettings) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val client = remember { VoxaRvcClient() }
    var endpoint by rememberSaveable(settings.endpoint) { mutableStateOf(settings.endpoint) }
    var models by remember { mutableStateOf(emptyList<String>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(settings.enabled) }
    var transposeDraft by remember(settings.transpose) { mutableStateOf(settings.transpose.toFloat()) }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.rvc_filter_heading),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.rvc_filter_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.rvc_filter_hide else R.string.rvc_filter_configure))
                }
                Switch(
                    checked = settings.enabled,
                    enabled = runCatching { settings.copy(enabled = true).requireReady() }.isSuccess,
                    onCheckedChange = { onChange(settings.copy(enabled = it)) },
                )
            }
            if (expanded) {
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text(stringResource(R.string.rvc_filter_endpoint)) },
                supportingText = { Text(stringResource(R.string.rvc_filter_endpoint_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = {
                        val validated = runCatching { RvcEndpoint.parse(endpoint) }
                        validated.onFailure { status = it.message }
                        if (validated.isFailure) return@FilledTonalButton
                        val next = settings.copy(endpoint = endpoint)
                        onChange(next)
                        busy = true
                        status = null
                        scope.launch {
                            try {
                                val available = client.listModels(endpoint)
                                models = available
                                status = if (available.isEmpty()) "No RVC models offered by Voxa."
                                else "Voxa online · " + available.size + " voices"
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                models = emptyList()
                                status = e.message ?: "Unable to reach Voxa."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                ) { Text(stringResource(R.string.rvc_filter_connect)) }
                Text(
                    status ?: stringResource(R.string.rvc_filter_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status != null && models.isEmpty()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = settings.model.substringAfterLast('/').ifEmpty {
                        stringResource(R.string.rvc_filter_choose_model)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                androidx.compose.foundation.layout.Box {
                    FilledTonalButton(
                        enabled = models.isNotEmpty(),
                        onClick = { showModels = true },
                    ) { Text(stringResource(R.string.rvc_filter_voices)) }
                    DropdownMenu(
                        expanded = showModels,
                        onDismissRequest = { showModels = false },
                    ) {
                        models.forEach { id ->
                            DropdownMenuItem(
                                text = { Text(id.substringAfterLast('/') + "  ·  " + id.substringBeforeLast('/', "")) },
                                onClick = {
                                    showModels = false
                                    onChange(settings.copy(endpoint = endpoint, model = id))
                                },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.rvc_filter_transpose),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "%+d".format(settings.transpose),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = transposeDraft,
                onValueChange = { transposeDraft = it },
                onValueChangeFinished = {
                    onChange(settings.copy(transpose = transposeDraft.toInt().coerceIn(-12, 12)))
                },
                valueRange = -12f..12f,
                steps = 23,
            )
            Text(
                stringResource(R.string.rvc_filter_no_fallback),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
        }
    }
}
