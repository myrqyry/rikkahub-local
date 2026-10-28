package me.rerere.rikkahub.ui.pages.models.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.modelregistry.ModelsDevDirectorySnapshot
import me.rerere.rikkahub.data.modelregistry.ModelsDevProviderDirectory
import me.rerere.rikkahub.data.modelregistry.ModelsDevProviderEntry

/** Catalog browsing is opt-in and cannot alter previously configured provider entries. */
@Composable
internal fun ModelsDevProviderDirectoryChooser(
    configuredProviders: List<ProviderSetting>,
    onChoose: (ModelsDevProviderEntry) -> Unit,
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<ModelsDevDirectorySnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var retryCount by remember { mutableStateOf(0) }

    LaunchedEffect(retryCount) {
        snapshot = null
        error = null
        try {
            snapshot = withContext(Dispatchers.IO) {
                ModelsDevProviderDirectory.load(
                    File(context.cacheDir, "models-dev-provider-directory.json")
                )
            }
        } catch (_: Exception) {
            error = "Provider directory unavailable. Custom API setup still works."
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "models.dev provider directory",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Public catalog, not a connection test. Only OpenAI-compatible endpoints " +
                "can be added from here; other providers need their own adapter. " +
                "New connections start disabled with no API key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search providers") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val loaded = snapshot
        when {
            error != null -> {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retryCount++ }) { Text("Retry") }
            }
            loaded == null -> CircularProgressIndicator()
            else -> {
                if (loaded.stale) {
                    Text(
                        "Offline: showing an older cached directory.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val filtered = remember(loaded, search) {
                    loaded.providers.filter { entry ->
                        entry.name.contains(search.trim(), ignoreCase = true) ||
                            entry.id.contains(search.trim(), ignoreCase = true)
                    }
                }
                Text(
                    "${filtered.size} providers · ${filtered.count { it.canAddAsOpenAiCompatible }} compatible",
                    style = MaterialTheme.typography.labelMedium,
                )
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(filtered, key = { it.id }) { entry ->
                        val configured = configuredProviders.any {
                            it is ProviderSetting.OpenAI && entry.baseUrl != null &&
                                it.baseUrl.trimEnd('/').equals(
                                    entry.baseUrl.trimEnd('/'), ignoreCase = true
                                )
                        }
                        val subtitle = when {
                            configured -> "Already configured"
                            entry.canAddAsOpenAiCompatible ->
                                "${entry.modelCount} indexed models · Add disabled, then configure"
                            else -> "Catalog only · Requires a supported provider adapter"
                        }
                        AddChoiceRow(
                            title = entry.name,
                            subtitle = subtitle,
                            enabled = entry.canAddAsOpenAiCompatible && !configured,
                            onClick = { onChoose(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddChoiceRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
