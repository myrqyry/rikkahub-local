package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.modelregistry.ModelsDevModelMetadata

/** Public-catalog suggestions are distinct from models confirmed by a user's provider API. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelsDevCatalogModelPicker(
    suggestions: List<ModelsDevModelMetadata>,
    configured: List<Model>,
    onSelect: (ModelsDevModelMetadata) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    TextButton(
        enabled = suggestions.isNotEmpty(),
        onClick = { open = true },
    ) { Text("Catalog") }

    if (open) {
        var query by remember { mutableStateOf("") }
        val matched = remember(suggestions, query) {
            val term = query.trim()
            suggestions.filter {
                term.isBlank() ||
                    it.modelId.contains(term, ignoreCase = true) ||
                    it.displayName.contains(term, ignoreCase = true)
            }
        }
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("models.dev model catalog", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Catalog listings are not an account availability check. " +
                        "Select a model to import its advertised capabilities; you can still edit them.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search models") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${matched.size} catalog matches", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                    items(matched, key = { it.modelId }) { metadata ->
                        val alreadyAdded = configured.any { it.modelId == metadata.modelId }
                        TextButton(
                            enabled = !alreadyAdded,
                            onClick = {
                                onSelect(metadata)
                                open = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    metadata.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    if (alreadyAdded) "${metadata.modelId} · Added"
                                    else "${metadata.modelId} · Catalog",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
