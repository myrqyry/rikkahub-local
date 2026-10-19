package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.ComfyUiApi
import okhttp3.OkHttpClient
import org.koin.java.KoinJavaComponent

/** Only controls supported by our standard ComfyUI checkpoint workflow. */
@Composable
fun ColumnScope.ComfyUiProviderConfigure(
    provider: ProviderSetting.ComfyUi,
    onEdit: (ProviderSetting.ComfyUi) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val client = remember { KoinJavaComponent.getKoin().get<OkHttpClient>() }
    var checkpoints by remember(provider.baseUrl) { mutableStateOf<List<String>>(emptyList()) }
    var status by remember(provider.baseUrl) { mutableStateOf<String?>(null) }
    var loading by remember(provider.baseUrl) { mutableStateOf(false) }
    var expanded by remember(provider.baseUrl) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Enable ComfyUI", modifier = Modifier.weight(1f))
        Checkbox(checked = provider.enabled, onCheckedChange = { onEdit(provider.copy(enabled = it)) })
    }
    OutlinedTextField(
        value = provider.name, onValueChange = { onEdit(provider.copy(name = it)) },
        label = { Text("Provider name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = provider.baseUrl, onValueChange = { onEdit(provider.copy(baseUrl = it)) },
        label = { Text("ComfyUI server URL") }, singleLine = true,
        supportingText = {
            Text("The phone needs a reachable address. Phone localhost requires ADB reverse; desktop loopback is not accessible over Wi-Fi.")
        }, modifier = Modifier.fillMaxWidth(),
    )
    Button(enabled = !loading, onClick = {
        loading = true
        status = null
        scope.launch {
            runCatching {
                val api = ComfyUiApi(client, provider.baseUrl)
                api.checkConnection()
                api.checkpoints()
            }.onSuccess {
                checkpoints = it
                status = "Connected · " + it.size + " checkpoints"
                expanded = it.isNotEmpty()
            }.onFailure {
                checkpoints = emptyList()
                status = "Connection failed: " + (it.message ?: "unknown error")
            }
            loading = false
        }
    }) { Text(if (loading) "Checking…" else "Test connection · Load checkpoints") }
    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    OutlinedTextField(
        value = provider.checkpoint,
        onValueChange = { onEdit(provider.copy(checkpoint = it)) },
        label = { Text("Checkpoint (exact filename)") }, singleLine = true,
        supportingText = { Text("Requires the standard CheckpointLoaderSimple/CLIP/KSampler/VAE nodes.") },
        modifier = Modifier.fillMaxWidth(),
    )
    if (checkpoints.isNotEmpty()) {
        Button(onClick = { expanded = true }) {
            Text("Choose checkpoint (" + checkpoints.size + ")")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 400.dp)) {
            checkpoints.forEach { checkpoint ->
                DropdownMenuItem(text = { Text(checkpoint) }, onClick = {
                    onEdit(provider.copy(checkpoint = checkpoint))
                    expanded = false
                })
            }
        }
    }
    Text("Standard text-to-image", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IntParamField(
            label = "Steps", value = provider.steps, modifier = Modifier.weight(1f),
            onCommit = { it?.takeIf { value -> value in 1..150 }
                ?.let { value -> onEdit(provider.copy(steps = value)) } },
        )
        DoubleParamField(
            label = "CFG", value = provider.cfg.toDouble(), modifier = Modifier.weight(1f),
            onCommit = { it?.takeIf { value -> value in 0.0..30.0 }
                ?.let { value -> onEdit(provider.copy(cfg = value.toFloat())) } },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IntParamField(
            label = "Width", value = provider.width, modifier = Modifier.weight(1f),
            onCommit = { it?.takeIf { value -> value in 128..2048 && value % 8 == 0 }
                ?.let { value -> onEdit(provider.copy(width = value)) } },
        )
        IntParamField(
            label = "Height", value = provider.height, modifier = Modifier.weight(1f),
            onCommit = { it?.takeIf { value -> value in 128..2048 && value % 8 == 0 }
                ?.let { value -> onEdit(provider.copy(height = value)) } },
        )
    }
    var seedDraft by remember(provider.seed) { mutableStateOf(provider.seed.toString()) }
    OutlinedTextField(
        value = seedDraft,
        onValueChange = { value ->
            if (value.isBlank() || value == "-" ||
                value.toLongOrNull()?.let { it >= -1L } == true) {
                seedDraft = value
                value.toLongOrNull()?.let { onEdit(provider.copy(seed = it)) }
            }
        },
        label = { Text("Seed (-1 = random)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
    )
    OutlinedTextField(
        value = provider.sampler, onValueChange = { onEdit(provider.copy(sampler = it)) },
        label = { Text("Sampler") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
    )
    OutlinedTextField(
        value = provider.scheduler, onValueChange = { onEdit(provider.copy(scheduler = it)) },
        label = { Text("Scheduler") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
    )
    OutlinedTextField(
        value = provider.negativePrompt,
        onValueChange = { onEdit(provider.copy(negativePrompt = it)) },
        label = { Text("Negative prompt") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
    )
    Text(
        "Text-to-image only for now. No arbitrary workflows, LoRAs, image editing or previews. Cancelling removes queued work; running ComfyUI jobs may continue.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
