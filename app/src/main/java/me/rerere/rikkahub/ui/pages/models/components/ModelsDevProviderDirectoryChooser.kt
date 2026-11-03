package me.rerere.rikkahub.ui.pages.models.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.modelregistry.ModelsDevDirectorySnapshot
import me.rerere.rikkahub.data.modelregistry.ModelsDevProviderDirectory
import me.rerere.rikkahub.data.modelregistry.ModelsDevProviderEntry
import me.rerere.rikkahub.data.modelregistry.ModelsDevProviderPresentation
import me.rerere.rikkahub.data.modelregistry.ProviderDirectoryAction
import me.rerere.rikkahub.ui.icons.MingcuteIcons as HugeIcons

/**
 * Primary API provider picker. models.dev supplies names and ordering, while
 * RikkaHub's real adapters and existing provider records determine what is
 * selectable. This component never mutates credentials or provider settings.
 */
@Composable
internal fun ModelsDevProviderDirectoryChooser(
    configuredProviders: List<ProviderSetting>,
    onChoose: (ProviderDirectoryAction) -> Unit,
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = "Provider directory unavailable. Use a custom API endpoint or an existing setup."
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Browse models.dev",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Search the public provider directory. Existing API settings and keys are preserved.",
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
            loaded == null -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Loading provider directory", style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> {
                if (loaded.stale) {
                    Text(
                        "Offline · Showing the last cached provider directory",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val pinned = remember(loaded, search) {
                    ModelsDevProviderPresentation.pinned(loaded.providers, search)
                }
                val others = remember(loaded, search) {
                    ModelsDevProviderPresentation.other(loaded.providers, search)
                }
                Text(
                    "${pinned.size + others.size} providers · Directory entries aren't connection tests",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                ) {
                    if (pinned.isNotEmpty()) {
                        item("pinned") {
                            Text(
                                "Popular",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 5.dp, bottom = 5.dp),
                            )
                        }
                        items(pinned, key = { "pinned:${it.id}" }) { entry ->
                            ProviderDirectoryRow(
                                entry = entry,
                                action = ModelsDevProviderPresentation.resolve(
                                    entry, configuredProviders
                                ),
                                onChoose = onChoose,
                            )
                        }
                    }
                    if (others.isNotEmpty()) {
                        item("all") {
                            Column {
                                if (pinned.isNotEmpty()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                }
                                Text(
                                    "All providers",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(bottom = 5.dp),
                                )
                            }
                        }
                        items(others, key = { "catalog:${it.id}" }) { entry ->
                            ProviderDirectoryRow(
                                entry = entry,
                                action = ModelsDevProviderPresentation.resolve(
                                    entry, configuredProviders
                                ),
                                onChoose = onChoose,
                            )
                        }
                    }
                    if (pinned.isEmpty() && others.isEmpty()) {
                        item("empty") {
                            Text(
                                "No providers match your search.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderDirectoryRow(
    entry: ModelsDevProviderEntry,
    action: ProviderDirectoryAction,
    onChoose: (ProviderDirectoryAction) -> Unit,
) {
    val available = action != ProviderDirectoryAction.Unsupported
    val hasKey = when (val provider = (action as? ProviderDirectoryAction.OpenExisting)?.provider) {
        is ProviderSetting.OpenAI -> provider.apiKey.isNotBlank()
        is ProviderSetting.Claude -> provider.apiKey.isNotBlank()
        is ProviderSetting.Google -> provider.apiKey.isNotBlank() || provider.useServiceAccount
        else -> false
    }
    val hint = when (action) {
        is ProviderDirectoryAction.OpenExisting ->
            if (hasKey) "Open existing setup" else "Set up API key"
        is ProviderDirectoryAction.CreateDraft -> "Add provider · Starts disabled"
        ProviderDirectoryAction.Unsupported -> "Catalog only · Adapter unavailable"
    }
    val modelCount = if (entry.modelCount > 0) {
        "${entry.modelCount} model${if (entry.modelCount == 1) "" else "s"} · "
    } else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = available) { onChoose(action) }
            .heightIn(min = 58.dp)
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (available) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modelCount + hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (available) {
            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
