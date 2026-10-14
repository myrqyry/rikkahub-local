package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.ColorwayCatalogEntry
import me.rerere.rikkahub.ui.theme.CustomTheme

/** Remote discovery is read-only until the user explicitly downloads one theme. */
@Composable
fun ColorwayBrowserDialog(
    entries: List<ColorwayCatalogEntry>,
    loading: Boolean,
    error: String?,
    downloadingPath: String?,
    downloadError: String?,
    preview: CustomTheme?,
    installedKeys: Set<String>,
    onRefresh: () -> Unit,
    onDownload: (ColorwayCatalogEntry) -> Unit,
    onInstall: () -> Unit,
    onBackToCatalog: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(entries, query) {
        entries.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.setting_theme_colorway_catalog),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.setting_theme_colorway_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (preview != null) {
                    Text(preview.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (preview.importedPalette?.dark == true)
                            stringResource(R.string.setting_theme_dark_palette)
                        else stringResource(R.string.setting_theme_light_palette),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        ThemeMiniature(preview.generateColorScheme(preview.importedPalette?.dark ?: true))
                    }
                    Text(
                        text = stringResource(R.string.setting_theme_other_mode_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onBackToCatalog) {
                            Text(stringResource(R.string.setting_theme_back_to_catalog))
                        }
                        FilledTonalButton(onClick = onInstall) {
                            Text(stringResource(R.string.setting_theme_install))
                        }
                    }
                } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.setting_theme_search_themes)) },
                    singleLine = true,
                )
                if (error != null || downloadError != null) {
                    Text(
                        text = downloadError ?: error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (loading) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else if (error != null) {
                    FilledTonalButton(onClick = onRefresh) {
                        Text(stringResource(R.string.setting_theme_retry_catalog))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.setting_theme_catalog_count, filtered.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        items(filtered, key = { it.id }) { entry ->
                            Surface(
                                onClick = { onDownload(entry) },
                                enabled = downloadingPath == null,
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        entry.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        when {
                                            downloadingPath == entry.id -> stringResource(R.string.setting_theme_downloading)
                                            entry.id in installedKeys -> stringResource(R.string.setting_theme_update)
                                            else -> stringResource(R.string.setting_theme_download)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = downloadingPath == null) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
                }
            }
        }
    }
}
